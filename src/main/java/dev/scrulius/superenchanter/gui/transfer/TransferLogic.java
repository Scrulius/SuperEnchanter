/* SuperEnchanter - Author: Scrulius (GitHub) */
package dev.scrulius.superenchanter.gui.transfer;

import dev.scrulius.superenchanter.SuperEnchanterPlugin;
import dev.scrulius.superenchanter.config.MessagesConfig;
import dev.scrulius.superenchanter.config.PluginConfig;
import dev.scrulius.superenchanter.economy.Cost;
import dev.scrulius.superenchanter.gui.enchanting.EnchantFormulas;
import dev.scrulius.superenchanter.gui.enchanting.EnchantingLogic;
import dev.scrulius.superenchanter.gui.enchanting.EnchantingLogic.AnalyzedEnchant;
import dev.scrulius.superenchanter.util.EnchantmentHelper;
import dev.scrulius.superenchanter.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Logic for the enchantment-transfer (repurposed grindstone) feature: salvaging
 * <b>one</b> enchantment from a donor item onto a target, consuming the donor.
 * <p>
 * All validation is delegated to {@link EnchantingLogic#analyze(ItemStack,
 * SuperEnchanterPlugin)} run against the <em>target</em> item, so transfer obeys
 * exactly the same rules as the enchanting table — targets, conflicts, required
 * enchantments, type limits and the blacklist — without duplicating any of that
 * logic. A donor enchantment is transferable only when the target could legally
 * receive it and the donor's level actually beats what the target already has.
 */
public final class TransferLogic {

    private TransferLogic() {
        // Utility class
    }

    /** Why a donor enchantment cannot be transferred ({@code NONE} = transferable). */
    public enum TransferBlock {
        NONE, NOT_APPLICABLE, CONFLICT, MISSING_REQUIRED, TYPE_LIMIT, ALREADY_OWNED
    }

    /**
     * One donor enchantment evaluated against the target.
     *
     * @param enchantment the enchantment carried by the donor
     * @param displayName its display name
     * @param donorLevel  the level on the donor
     * @param targetLevel the level the target currently has (0 if absent)
     * @param resultLevel the level the target would end up with ({@code max(donor, target)})
     * @param rarityId    the EcoEnchants rarity id (drives cost), or {@code null}
     * @param cost        the transfer cost (only meaningful when transferable)
     * @param block       why it is blocked, or {@link TransferBlock#NONE}
     * @param blockDetail names of conflicting / required enchantments, when relevant
     */
    public record TransferOffer(
            @NotNull Enchantment enchantment,
            @NotNull String displayName,
            int donorLevel,
            int targetLevel,
            int resultLevel,
            @Nullable String rarityId,
            @NotNull Cost cost,
            @NotNull TransferBlock block,
            @NotNull List<String> blockDetail
    ) {
        public boolean isTransferable() {
            return block == TransferBlock.NONE;
        }
    }

    /**
     * Evaluates every enchantment on the donor against the target, returning one
     * {@link TransferOffer} each (transferable ones first, then by name).
     *
     * @param donor  the item to salvage from (consumed on transfer)
     * @param target the item that would receive the enchantment
     * @param plugin the plugin instance
     * @return the offers, or an empty list if either item is missing / the donor
     *         carries no enchantments
     */
    @NotNull
    public static List<TransferOffer> computeOffers(@Nullable ItemStack donor,
                                                    @Nullable ItemStack target,
                                                    @NotNull SuperEnchanterPlugin plugin) {
        if (donor == null || donor.getType().isAir() || target == null || target.getType().isAir()) {
            return List.of();
        }
        final Map<Enchantment, Integer> donorEnchants = EnchantmentHelper.getEnchantments(donor);
        if (donorEnchants.isEmpty()) {
            return List.of();
        }

        final PluginConfig config = plugin.getPluginConfig();
        final boolean materialOk = !config.isTransferRequireSameMaterial()
                || donor.getType() == target.getType();

        // One registry scan against the TARGET gives us every relevant enchant's
        // block reason (conflicts/required/type-limit/maxed) and current level.
        final Map<Enchantment, AnalyzedEnchant> byEnch = new HashMap<>();
        for (AnalyzedEnchant a : EnchantingLogic.analyze(target, plugin)) {
            byEnch.put(a.enchantment(), a);
        }

        final List<TransferOffer> out = new ArrayList<>(donorEnchants.size());
        for (Map.Entry<Enchantment, Integer> entry : donorEnchants.entrySet()) {
            final Enchantment ench = entry.getKey();
            final int donorLevel = entry.getValue();
            final AnalyzedEnchant a = byEnch.get(ench);

            // Name / rarity: from the analysis when present, else resolved directly.
            String name = a != null ? a.displayName() : plugin.getEcoHook().getDisplayName(ench, 0);
            if (name.isEmpty()) {
                name = EnchantmentHelper.prettyName(ench);
            }
            final String rarityId = a != null ? a.rarityId() : plugin.getEcoHook().getRarityId(ench);

            TransferBlock block;
            List<String> detail = List.of();
            int targetLevel = a != null ? a.currentLevel() : 0;

            if (!materialOk || a == null) {
                // Donor enchant not valid for this target type (or blacklisted, which
                // analyze() filters out → absent here), or a material mismatch.
                block = TransferBlock.NOT_APPLICABLE;
                targetLevel = 0;
            } else {
                block = switch (a.reason()) {
                    case CONFLICT -> { detail = a.reasonDetail(); yield TransferBlock.CONFLICT; }
                    case MISSING_REQUIRED -> { detail = a.reasonDetail(); yield TransferBlock.MISSING_REQUIRED; }
                    case TYPE_LIMIT -> TransferBlock.TYPE_LIMIT;
                    case MAXED -> TransferBlock.ALREADY_OWNED;
                    // NONE: legal to receive — but only useful if the donor beats it.
                    case NONE -> donorLevel <= targetLevel
                            ? TransferBlock.ALREADY_OWNED
                            : TransferBlock.NONE;
                };
            }

            final int resultLevel = Math.max(targetLevel, donorLevel);
            final Cost cost = block == TransferBlock.NONE
                    ? transferCost(rarityId, resultLevel, config)
                    : new Cost(config.getTransferCostType(), 0);

            out.add(new TransferOffer(ench, name, donorLevel, targetLevel, resultLevel,
                    rarityId, cost, block, detail));
        }

        out.sort(Comparator
                .comparingInt((TransferOffer o) -> o.isTransferable() ? 0 : 1)
                .thenComparing(TransferOffer::displayName, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(out);
    }

    /**
     * Computes the cost of transferring an enchantment that ends at {@code level}
     * on the target, reusing the same capped curve as the enchanting table.
     */
    @NotNull
    public static Cost transferCost(@Nullable String rarityId, int level, @NotNull PluginConfig config) {
        final double rarityMult = config.isTransferUseRarityMultiplier()
                ? config.getRarityCostMultiplier(rarityId)
                : 1.0;
        // Same geometric curve as the table, no finishing premium (transfer salvages
        // an existing enchantment rather than forging the final rung).
        final int amount = EnchantFormulas.geometricCost(
                config.getTransferBaseCost(), config.getTransferLevelGrowth(),
                level, rarityMult, 1.0, config.getTransferMaxCost());
        return new Cost(config.getTransferCostType(), amount);
    }

    /**
     * Applies a transferred enchantment to the target at the result level. The
     * builder in {@link EnchantmentHelper} overwrites any lower existing level.
     */
    @NotNull
    public static ItemStack applyTransfer(@NotNull ItemStack target,
                                          @NotNull Enchantment ench, int resultLevel) {
        return EnchantmentHelper.applyEnchantment(target, ench, resultLevel);
    }

    /**
     * Offers for <b>extraction</b> (no target placed): every enchantment on the
     * donor can be pulled into a book, since a book stores any enchantment with no
     * target/conflict rules. The donor is still consumed on extraction. Cost reuses
     * the transfer curve at the donor's own level.
     *
     * @param donor  the item to salvage from (consumed on extraction)
     * @param plugin the plugin instance
     * @return one transferable offer per donor enchantment, or empty if none
     */
    @NotNull
    public static List<TransferOffer> computeExtractOffers(@Nullable ItemStack donor,
                                                           @NotNull SuperEnchanterPlugin plugin) {
        if (donor == null || donor.getType().isAir()) {
            return List.of();
        }
        final Map<Enchantment, Integer> donorEnchants = EnchantmentHelper.getEnchantments(donor);
        if (donorEnchants.isEmpty()) {
            return List.of();
        }
        final PluginConfig config = plugin.getPluginConfig();
        final List<TransferOffer> out = new ArrayList<>(donorEnchants.size());
        for (Map.Entry<Enchantment, Integer> entry : donorEnchants.entrySet()) {
            final Enchantment ench = entry.getKey();
            // Don't let curses be salvaged to a book — they'd be a dead-end (the table
            // and anvil both refuse to apply them). Curses only enter via the table roll.
            if (plugin.getEcoHook().isCurse(ench)) {
                continue;
            }
            final int level = entry.getValue();
            final String name = plugin.getEcoHook().displayNameOrFallback(ench);
            final String rarityId = plugin.getEcoHook().getRarityId(ench);
            // Extraction makes a tradeable book → costs more than a plain move.
            final Cost base = transferCost(rarityId, level, config);
            final Cost cost = new Cost(base.type(),
                    base.amount() * config.getTransferExtractMultiplier());
            // No target → targetLevel 0, result = the donor's own level, always allowed.
            out.add(new TransferOffer(ench, name, level, 0, level, rarityId, cost,
                    TransferBlock.NONE, List.of()));
        }
        out.sort(Comparator.comparing(TransferOffer::displayName, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(out);
    }

    /** Builds an enchanted book holding the extracted enchantment at the given level. */
    @NotNull
    public static ItemStack extractToBook(@NotNull Enchantment ench, int level) {
        return EnchantmentHelper.applyEnchantment(new ItemStack(Material.ENCHANTED_BOOK), ench, level);
    }

    /** Builds ONE enchanted book holding every selected offer at its result level. */
    @NotNull
    public static ItemStack extractToBook(@NotNull List<TransferOffer> offers) {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        for (TransferOffer o : offers) {
            book = EnchantmentHelper.applyEnchantment(book, o.enchantment(), o.resultLevel());
        }
        return book;
    }

    // ── Icon rendering ──────────────────────────────────────────────────────

    /**
     * Builds the offer icon for the enchant list: green book when transferable
     * (glowing while selected), gray dye with the reason when blocked.
     */
    @NotNull
    public static ItemStack createOfferIcon(@NotNull TransferOffer offer,
                                            boolean selected,
                                            boolean extract,
                                            @NotNull Player player,
                                            @NotNull SuperEnchanterPlugin plugin) {
        final MessagesConfig msg = plugin.getMessages();

        if (!offer.isTransferable()) {
            final List<String> lore = new ArrayList<>();
            final String joined = String.join(", ", offer.blockDetail());
            switch (offer.block()) {
                case CONFLICT -> lore.add(msg.format("transfer.blocked-conflict",
                        Map.of("{enchantments}", joined)));
                case MISSING_REQUIRED -> lore.add(msg.format("transfer.blocked-required",
                        Map.of("{enchantments}", joined)));
                case TYPE_LIMIT -> lore.add(msg.raw("transfer.blocked-type-limit"));
                case ALREADY_OWNED -> lore.add(msg.raw("transfer.blocked-already-owned"));
                default -> lore.add(msg.raw("transfer.blocked-not-applicable"));
            }
            return new ItemBuilder(Material.GRAY_DYE)
                    .name(msg.format("transfer.offer-blocked-name",
                            Map.of("{name}", offer.displayName(),
                                    "{level}", EnchantingLogic.toRoman(offer.donorLevel()))))
                    .lore(lore)
                    .build();
        }

        final Cost cost = plugin.getCostService().effectiveCost(player, offer.cost());
        final boolean canAfford = plugin.getCostService().canAfford(player, cost);
        final List<String> lore = new ArrayList<>();
        if (extract) {
            // No target: just show the level being extracted (if it has one).
            if (offer.resultLevel() > 1) {
                lore.add(msg.format("transfer.offer-extract-level",
                        Map.of("{result}", EnchantingLogic.toRoman(offer.resultLevel()))));
            }
        } else if (offer.resultLevel() > 1 || offer.donorLevel() > 1 || offer.targetLevel() > 0) {
            // Transfer: show the level movement (donor → result).
            lore.add(msg.format("transfer.offer-level", Map.of(
                    "{donor}", EnchantingLogic.toRoman(offer.donorLevel()),
                    "{target}", offer.targetLevel() > 0 ? EnchantingLogic.toRoman(offer.targetLevel()) : "0",
                    "{result}", EnchantingLogic.toRoman(offer.resultLevel()))));
        }
        lore.add(msg.format(canAfford ? "transfer.offer-cost-ok" : "transfer.offer-cost-fail",
                Map.of("{cost}", cost.displayText(),
                        "{balance}", plugin.getCostService().balanceText(player, cost.type()))));
        lore.add("");
        lore.add(msg.raw(selected ? "transfer.offer-selected" : "transfer.offer-select"));

        final ItemBuilder builder = new ItemBuilder(Material.ENCHANTED_BOOK)
                .name(msg.format("transfer.offer-name",
                        Map.of("{name}", offer.displayName(),
                                "{level}", EnchantingLogic.toRoman(offer.resultLevel()))))
                .lore(lore);
        if (selected) {
            builder.glow();
        }
        return builder.build();
    }
}
