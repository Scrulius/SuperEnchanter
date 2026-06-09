/* SuperEnchanter - Author: Scrulius (GitHub) */
package dev.scrulius.superenchanter.gui.enchanting;

import dev.scrulius.superenchanter.config.MessagesConfig;
import dev.scrulius.superenchanter.config.PluginConfig;
import dev.scrulius.superenchanter.economy.Cost;
import dev.scrulius.superenchanter.integration.EcoEnchantsHook;
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
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Encapsulates all enchanting-table logic: compatible enchantment discovery,
 * next-step offer computation, enchantment application, and icon rendering.
 * <p>
 * The table is strictly <b>sequential</b>: the only purchasable upgrade for an
 * enchantment is its <em>next</em> level ({@link #nextStep}), and a click on the
 * enchantment icon attempts exactly that rung. There is no level-selection tier
 * and no cumulative jump — the ladder is climbed (and risked) one rung at a time.
 */
public final class EnchantingLogic {

    /** Segments in every text progress bar shown in the GUI icons. */
    public static final int BAR_SEGMENTS = 10;
    /** MiniMessage tag for the filled run of the success-chance bar. */
    private static final String BAR_SUCCESS_TAG = "<gradient:#43E97B:#38D9A9>";
    /** MiniMessage tag for the empty run of every bar (dark metallic). */
    public static final String BAR_EMPTY_TAG = "<#3A3F46>";

    private EnchantingLogic() {
        // Utility class
    }

    /** Why an enchantment cannot currently be applied ({@code NONE} = available). */
    public enum BlockReason { NONE, MAXED, CONFLICT, MISSING_REQUIRED, TYPE_LIMIT }

    /**
     * A relevant enchantment for an item (correct target, not blacklisted) plus
     * its current status, so the menu can show available and blocked ones alike
     * — and explain <em>why</em> the blocked ones are blocked.
     *
     * @param reasonDetail names of the enchantments behind a CONFLICT / MISSING_REQUIRED reason
     */
    public record AnalyzedEnchant(
            @NotNull Enchantment enchantment,
            @NotNull String displayName,
            @NotNull String typeId,
            @NotNull String categoryName,
            @Nullable String rarityId,
            int currentLevel,
            int maxLevel,
            @NotNull BlockReason reason,
            @NotNull List<String> reasonDetail
    ) {
        public boolean isAvailable() {
            return reason == BlockReason.NONE;
        }
    }

    /**
     * A selectable category (EcoEnchants type) in the first tier of the menu.
     *
     * @param typeId      the EcoEnchants type id (e.g. {@code curse})
     * @param displayName the prettified category name
     * @param count       how many relevant enchantments fall under this category
     */
    public record CategoryOffer(
            @NotNull String typeId,
            @NotNull String displayName,
            int count
    ) {}

    /**
     * The single purchasable rung of an enchantment: its NEXT level. XP-only by
     * design — there is no reagent and no cumulative cost; each attempt charges
     * exactly this step.
     *
     * @param level         the level this rung grants (current + 1)
     * @param stepCost      what this one attempt charges (consumed even on failure)
     * @param requiredPower the bookshelf power this rung demands
     */
    public record NextStep(int level, @NotNull Cost stepCost, int requiredPower) {}

    /**
     * Returns every enchantment relevant to the item (correct target, not
     * blacklisted), each annotated with its category and a {@link BlockReason}. The
     * GUI caches this per item so that navigating categories never triggers
     * another scan.
     * <p>
     * The item-independent metadata (type, rarity, name, max level, required/conflict
     * sets, type limit) comes from the precomputed {@link EnchantmentIndex} built once
     * at startup, so this only does the genuinely item-dependent work: the target
     * check plus the present-set classification.
     * </p>
     */
    @NotNull
    public static List<AnalyzedEnchant> analyze(
            @NotNull ItemStack item,
            @NotNull dev.scrulius.superenchanter.SuperEnchanterPlugin plugin) {

        final EcoEnchantsHook eco = plugin.getEcoHook();
        final EnchantmentIndex index = plugin.getEnchantmentIndex();
        final Map<Enchantment, Integer> current = EnchantmentHelper.getEnchantments(item);
        final Set<Enchantment> present = current.keySet();
        final List<AnalyzedEnchant> result = new ArrayList<>();

        for (EnchantmentIndex.Entry entry : index.candidates()) {
            final Enchantment ench = entry.enchantment();
            if (!eco.appliesToItem(ench, item)) {
                continue;
            }

            final int currentLevel = current.getOrDefault(ench, 0);
            final int maxLevel = entry.maxLevel();

            // Only adding a NEW enchant can be blocked by required/conflict/type-limit;
            // upgrading an already-present one is always allowed (until maxed). Lists are
            // computed lazily in precedence order; the pure classifyBlock decides the reason.
            final boolean isNew = currentLevel == 0;
            final List<Enchantment> missing = isNew ? missingRequired(entry, present) : List.of();
            final List<Enchantment> conflicts =
                    (isNew && missing.isEmpty()) ? conflictingPresent(entry, present, index) : List.of();
            final boolean typeLimit = isNew && missing.isEmpty() && conflicts.isEmpty()
                    && typeLimitReached(entry, present, index);

            final BlockReason reason = classifyBlock(
                    currentLevel, maxLevel, !missing.isEmpty(), !conflicts.isEmpty(), typeLimit);
            final List<String> detail = switch (reason) {
                case MISSING_REQUIRED -> names(missing, eco);
                case CONFLICT -> names(conflicts, eco);
                default -> List.of();
            };

            result.add(new AnalyzedEnchant(ench, entry.displayName(), entry.typeId(),
                    entry.categoryName(), entry.rarityId(),
                    currentLevel, maxLevel, reason, detail));
        }
        return result;
    }

    /**
     * Pure precedence rule for why an enchantment is (un)available on an item — the
     * Bukkit/EcoEnchants-free core of {@link #analyze}, extracted so the ordering can be
     * unit-tested without a server. Precedence: an owned enchant at max level is
     * {@code MAXED}; otherwise only a <em>new</em> enchant ({@code currentLevel == 0}) can be
     * blocked, in order MISSING_REQUIRED → CONFLICT → TYPE_LIMIT; everything else is
     * {@code NONE} (available, including upgrading an owned-but-not-maxed enchant).
     *
     * @param currentLevel      the level currently on the item (0 = not present)
     * @param maxLevel          the enchantment's maximum level
     * @param missingRequired   whether a required prerequisite enchant is absent
     * @param conflictPresent   whether a conflicting enchant is present
     * @param typeLimitReached  whether the item already holds the max of this type
     * @return the {@link BlockReason}
     */
    @NotNull
    public static BlockReason classifyBlock(int currentLevel, int maxLevel,
                                            boolean missingRequired, boolean conflictPresent,
                                            boolean typeLimitReached) {
        if (currentLevel > 0 && currentLevel >= maxLevel) {
            return BlockReason.MAXED;
        }
        if (currentLevel == 0) {
            if (missingRequired) {
                return BlockReason.MISSING_REQUIRED;
            }
            if (conflictPresent) {
                return BlockReason.CONFLICT;
            }
            if (typeLimitReached) {
                return BlockReason.TYPE_LIMIT;
            }
        }
        return BlockReason.NONE;
    }

    /**
     * Localised display name for a category (EcoEnchants type id): uses
     * {@code messages.yml → category-names.<typeId>} when present, otherwise a
     * title-cased version of the id.
     */
    @NotNull
    static String categoryDisplayName(
            @NotNull dev.scrulius.superenchanter.SuperEnchanterPlugin plugin, @NotNull String typeId) {
        final String key = "category-names." + typeId.toLowerCase(java.util.Locale.ROOT);
        return plugin.getMessages().hasKey(key) ? plugin.getMessages().raw(key) : prettyId(typeId);
    }

    /**
     * Localised display name for an EcoEnchants rarity id: uses
     * {@code messages.yml → rarity-names.<id>} when present, otherwise title-cased.
     * Returns an empty string for a {@code null} rarity (vanilla-only enchantment).
     */
    @NotNull
    private static String rarityDisplayName(
            @NotNull dev.scrulius.superenchanter.SuperEnchanterPlugin plugin, @Nullable String rarityId) {
        if (rarityId == null || rarityId.isBlank()) {
            return "";
        }
        final String key = "rarity-names." + rarityId.toLowerCase(java.util.Locale.ROOT);
        return plugin.getMessages().hasKey(key) ? plugin.getMessages().raw(key) : prettyId(rarityId);
    }

    /**
     * Canonical category order in the menu: the 5 rarities ascending, then spell
     * (habilidades) and curse last. Types not listed fall after these (then alphabetical),
     * so a new/unknown type never breaks the layout.
     */
    private static final List<String> CATEGORY_ORDER =
            List.of("comun", "raro", "epico", "legendario", "divino", "spell", "curse");

    /** Position of a type id in {@link #CATEGORY_ORDER}, or a large value if unlisted. */
    private static int categoryRank(@NotNull String typeId) {
        final int i = CATEGORY_ORDER.indexOf(typeId.toLowerCase(java.util.Locale.ROOT));
        return i < 0 ? Integer.MAX_VALUE : i;
    }

    /** Groups an analysis into categories (EcoEnchants types), sorted by rarity then name. */
    @NotNull
    public static List<CategoryOffer> getCategories(@NotNull List<AnalyzedEnchant> analysis) {
        final Map<String, CategoryOffer> byType = new java.util.LinkedHashMap<>();
        for (AnalyzedEnchant e : analysis) {
            byType.merge(e.typeId(),
                    new CategoryOffer(e.typeId(), e.categoryName(), 1),
                    (a, b) -> new CategoryOffer(a.typeId(), a.displayName(), a.count() + 1));
        }
        final List<CategoryOffer> categories = new ArrayList<>(byType.values());
        categories.sort(Comparator.comparingInt((CategoryOffer c) -> categoryRank(c.typeId()))
                .thenComparing(CategoryOffer::displayName, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(categories);
    }

    /** Filters an analysis to one category, available first, then owned, then by name. */
    @NotNull
    public static List<AnalyzedEnchant> enchantsInCategory(@NotNull List<AnalyzedEnchant> analysis,
                                                           @NotNull String typeId) {
        final List<AnalyzedEnchant> out = new ArrayList<>();
        for (AnalyzedEnchant e : analysis) {
            if (e.typeId().equalsIgnoreCase(typeId)) {
                out.add(e);
            }
        }
        out.sort(Comparator
                .comparingInt((AnalyzedEnchant e) -> e.isAvailable() ? 0 : 1)
                .thenComparingInt(e -> e.currentLevel() > 0 ? 0 : 1)
                .thenComparing(AnalyzedEnchant::displayName, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(out);
    }

    // ── Block-reason helpers ────────────────────────────────────────────────

    private static List<Enchantment> missingRequired(@NotNull EnchantmentIndex.Entry entry,
                                                     @NotNull Set<Enchantment> present) {
        if (entry.required().isEmpty()) {
            return List.of();
        }
        final List<Enchantment> missing = new ArrayList<>();
        for (Enchantment r : entry.required()) {
            if (!present.contains(r)) {
                missing.add(r);
            }
        }
        return missing;
    }

    private static List<Enchantment> conflictingPresent(@NotNull EnchantmentIndex.Entry entry,
                                                        @NotNull Set<Enchantment> present,
                                                        @NotNull EnchantmentIndex index) {
        if (present.isEmpty()) {
            return List.of();
        }
        final Enchantment ench = entry.enchantment();
        final List<Enchantment> out = new ArrayList<>();
        for (Enchantment other : present) {
            if (other.equals(ench)) {
                continue;
            }
            final EnchantmentIndex.Entry oe = index.get(other);
            final boolean conflict = entry.conflictsAll()
                    || (oe != null && oe.conflictsAll())
                    || entry.conflicts().contains(other)
                    || (oe != null && oe.conflicts().contains(ench))
                    || ench.conflictsWith(other) || other.conflictsWith(ench);
            if (conflict) {
                out.add(other);
            }
        }
        return out;
    }

    private static boolean typeLimitReached(@NotNull EnchantmentIndex.Entry entry,
                                            @NotNull Set<Enchantment> present,
                                            @NotNull EnchantmentIndex index) {
        final int limit = entry.typeLimit();
        if (limit == Integer.MAX_VALUE) {
            return false;
        }
        final String typeId = entry.typeId();
        int count = 0;
        for (Enchantment other : present) {
            if (other.equals(entry.enchantment())) {
                continue;
            }
            final EnchantmentIndex.Entry oe = index.get(other);
            if (oe != null && typeId.equalsIgnoreCase(oe.typeId())) {
                count++;
            }
        }
        return count >= limit;
    }

    private static List<String> names(@NotNull List<Enchantment> enchantments,
                                      @NotNull EcoEnchantsHook eco) {
        final List<String> out = new ArrayList<>(enchantments.size());
        for (Enchantment e : enchantments) {
            String n = eco.getDisplayName(e, 0);
            out.add(n.isEmpty() ? EnchantmentHelper.prettyName(e) : n);
        }
        return out;
    }

    /**
     * The maximum level actually offered for an enchantment: the registry max,
     * optionally capped (never raised) by a per-enchant override.
     */
    public static int cappedMaxLevel(@NotNull AnalyzedEnchant offer,
                                     @NotNull dev.scrulius.superenchanter.SuperEnchanterPlugin plugin) {
        final PluginConfig.EnchantmentOverride override =
                plugin.getPluginConfig().getEnchantmentOverride(offer.enchantment());
        return override.maxLevel() != null
                ? Math.max(1, Math.min(offer.maxLevel(), override.maxLevel()))
                : offer.maxLevel();
    }

    /**
     * Computes the single purchasable rung for an enchantment: its next level, the
     * step cost and the bookshelf power it demands. Returns {@code null} when the
     * enchantment is already at its (override-capped) maximum.
     * <p>
     * Cost is per-step only — the sequential ladder means each click pays one rung;
     * there is no cumulative jump to charge for.
     */
    @Nullable
    public static NextStep nextStep(@NotNull AnalyzedEnchant offer,
                                    @NotNull dev.scrulius.superenchanter.SuperEnchanterPlugin plugin) {
        final PluginConfig config = plugin.getPluginConfig();
        final PluginConfig.EnchantmentOverride override =
                config.getEnchantmentOverride(offer.enchantment());

        final int maxLevel = cappedMaxLevel(offer, plugin);
        final int level = offer.currentLevel() + 1;
        if (level > maxLevel) {
            return null;
        }

        // Rarity drives cost and required power — magnitude, not level fraction.
        final String rarityId = offer.rarityId();
        final var costType = config.getEnchantingCostType(rarityId);

        final int amount;
        if (override.xpCost() != null) {
            amount = Math.max(1, override.xpCost());
        } else {
            final int curve = EnchantFormulas.xpCostForLevel(config.getBaseXPCost(),
                    config.getLevelXPMultiplier(), level, config.getCostExponent(),
                    config.getRarityCostMultiplier(rarityId), config.getMaxXpCost());
            amount = Math.max(1, (int) Math.round(curve * override.costMultiplier()));
        }

        final PluginConfig.RarityPower rarityPower = config.getRarityPower(rarityId);
        final int requiredPower = override.requiredPower() != null
                ? Math.max(0, override.requiredPower())
                : EnchantFormulas.requiredPowerForLevel(
                        rarityPower.floor(), rarityPower.step(), level, config.getMaxBookshelfPower());

        return new NextStep(level, new Cost(costType, amount), requiredPower);
    }

    @NotNull
    public static ItemStack applyEnchantment(@NotNull ItemStack item,
                                             @NotNull Enchantment enchant,
                                             int newLevel) {
        return EnchantmentHelper.applyEnchantment(item, enchant, newLevel);
    }

    @NotNull
    public static ItemStack createCategoryIcon(@NotNull CategoryOffer category,
                                               @NotNull dev.scrulius.superenchanter.SuperEnchanterPlugin plugin) {
        MessagesConfig msg = plugin.getMessages();
        return new ItemBuilder(plugin.getPluginConfig().getCategoryIcon(category.typeId()))
                .name(msg.format("enchanting.category-name", Map.of("{name}", category.displayName())))
                .lore(msg.formatList("enchanting.category-lore",
                        Map.of("{count}", String.valueOf(category.count()))))
                .glow()
                .build();
    }

    /**
     * Appends an enchantment's declared usage-requirement lines (e.g. "Requiere Combate XV",
     * from a {@code has_skill_level} gate's {@code not-met-lines}) to its icon description
     * block, so the player sees the requirement <em>before</em> enchanting. No-op when the
     * enchantment declares none.
     */
    private static void appendRequirementLines(@NotNull List<String> description,
                                               @NotNull org.bukkit.enchantments.Enchantment enchantment,
                                               @NotNull dev.scrulius.superenchanter.SuperEnchanterPlugin plugin) {
        final List<String> requirements = plugin.getEcoHook().getRequirementLines(enchantment);
        if (requirements.isEmpty()) {
            return;
        }
        if (!description.isEmpty()) {
            description.add("");
        }
        description.addAll(requirements);
    }

    /**
     * Renders the icon of one enchantment in the (single) enchantment tier. The icon
     * IS the attempt button: it shows everything the click will roll — success bar,
     * curse chance, downgrade risk, step cost and required power — and clicking it
     * attempts exactly one rung. Variants: maxed (gold), blocked (gray + reason),
     * Magia-locked (barrier), unaffordable (gray + checks) and available (green).
     *
     * @param offer the analyzed enchantment
     * @param step  the next purchasable rung, or {@code null} when maxed by override
     */
    @NotNull
    public static ItemStack createEnchantIcon(@NotNull AnalyzedEnchant offer,
                                              @Nullable NextStep step,
                                              @NotNull Player player,
                                              int bookshelfPower,
                                              @NotNull dev.scrulius.superenchanter.SuperEnchanterPlugin plugin) {
        final MessagesConfig msg = plugin.getMessages();
        final PluginConfig config = plugin.getPluginConfig();
        final var magia = plugin.getMagiaService();

        List<String> description = new ArrayList<>(
                plugin.getEcoHook().getDescription(offer.enchantment(), Math.max(1, offer.currentLevel()), player));
        if (description.isEmpty()) {
            final String descKey = "enchantment-descriptions." + offer.enchantment().getKey().getKey();
            if (msg.hasKey(descKey)) {
                description.addAll(msg.rawList(descKey));
            }
        }
        appendRequirementLines(description, offer.enchantment(), plugin);

        // ── Maxed: owned at max level, or capped by a per-enchant override ──
        if (offer.reason() == BlockReason.MAXED || (offer.isAvailable() && step == null)) {
            final String roman = offer.maxLevel() > 1 ? " " + toRoman(offer.currentLevel()) : "";
            final List<String> lore = new ArrayList<>();
            if (!description.isEmpty()) {
                lore.addAll(description);
                lore.add("");
            }
            lore.addAll(msg.rawList("enchant-icons.maxed-lore"));
            return new ItemBuilder(Material.ENCHANTED_BOOK)
                    .name(msg.format("enchant-icons.maxed-name", Map.of("{name}", offer.displayName() + roman)))
                    .lore(lore)
                    .glow()
                    .build();
        }

        // ── Blocked — explain why ───────────────────────────────────────
        if (!offer.isAvailable()) {
            final List<String> lore = new ArrayList<>();
            if (!description.isEmpty()) {
                lore.addAll(description);
                lore.add("");
            }
            final String joined = String.join(", ", offer.reasonDetail());
            switch (offer.reason()) {
                case CONFLICT -> lore.add(msg.format("enchant-icons.blocked-conflict",
                        Map.of("{enchantments}", joined)));
                case MISSING_REQUIRED -> lore.add(msg.format("enchant-icons.blocked-required",
                        Map.of("{enchantments}", joined)));
                case TYPE_LIMIT -> lore.add(msg.raw("enchant-icons.blocked-type-limit"));
                default -> { }
            }
            return new ItemBuilder(Material.GRAY_DYE)
                    .name(msg.format("enchant-icons.blocked-name", Map.of("{name}", offer.displayName())))
                    .lore(lore)
                    .build();
        }

        final String nextName = offer.displayName()
                + (offer.maxLevel() > 1 ? " " + toRoman(step.level()) : "");

        // ── Magia hard-gate (carril 5): barrier with the required level up front ──
        if (magia != null && magia.isEnabled() && !magia.canEnchant(player, offer.rarityId())) {
            final List<String> lockedLore = msg.formatList("enchant-icons.locked-lore",
                    Map.of("{required}", String.valueOf(magia.requiredLevel(offer.rarityId())),
                            "{level}", String.valueOf(magia.level(player))));
            return new ItemBuilder(Material.BARRIER)
                    .name(msg.format("enchant-icons.locked-name", Map.of("{name}", nextName)))
                    .lore(lockedLore)
                    .build();
        }

        // Discounted cost actually shown/charged for this player (permission + Magia).
        Cost cost = plugin.getCostService().effectiveCost(player, step.stepCost());
        if (magia != null && magia.isEnabled()) {
            cost = magia.applyDiscount(player, cost);
        }
        final boolean canAfford = plugin.getCostService().canAfford(player, cost);
        final boolean hasEnoughPower = bookshelfPower >= step.requiredPower();

        final List<String> lore = new ArrayList<>();
        if (!description.isEmpty()) {
            lore.addAll(description);
            lore.add("");
        }
        final String rarity = rarityDisplayName(plugin, offer.rarityId());
        if (!rarity.isEmpty()) {
            lore.add(msg.format("enchant-icons.lore-rarity", Map.of("{rarity}", rarity)));
        }
        if (offer.maxLevel() > 1) {
            lore.add(msg.format("enchant-icons.lore-level", Map.of(
                    "{current}", offer.currentLevel() > 0 ? toRoman(offer.currentLevel()) : "0",
                    "{max}", toRoman(cappedMaxLevel(offer, plugin)))));
        }
        lore.add("");
        appendRiskLore(lore, offer, player, plugin);

        if (canAfford && hasEnoughPower) {
            lore.add(msg.format("enchant-icons.available-lore-cost",
                    Map.of("{cost}", cost.displayText())));
            lore.add(msg.format("enchant-icons.available-lore-power",
                    Map.of("{power}", String.valueOf(step.requiredPower()))));
            lore.add("");
            lore.add(msg.format("enchant-icons.available-lore-button", Map.of("{name}", nextName)));

            return new ItemBuilder(Material.ENCHANTED_BOOK)
                    .name(msg.format("enchant-icons.available-name", Map.of("{name}", nextName)))
                    .lore(lore)
                    .glow()
                    .build();
        }

        // ── Unaffordable / underpowered: gray icon with explicit ✔/✘ checks ──
        if (!canAfford) {
            lore.add(msg.format("enchant-icons.unavailable-xp-fail",
                    Map.of("{cost}", cost.displayText())));
            lore.add(msg.format("enchant-icons.unavailable-xp-fail-current",
                    Map.of("{balance}", plugin.getCostService().balanceText(player, cost.type()))));
        } else {
            lore.add(msg.raw("enchant-icons.unavailable-xp-ok"));
        }
        if (!hasEnoughPower) {
            lore.add(msg.format("enchant-icons.unavailable-power-fail",
                    Map.of("{required}", String.valueOf(step.requiredPower()))));
        } else {
            lore.add(msg.raw("enchant-icons.unavailable-power-ok"));
        }
        lore.add("");
        lore.add(msg.raw("enchant-icons.unavailable-footer"));

        return new ItemBuilder(Material.GRAY_DYE)
                .name(msg.format("enchant-icons.unavailable-name", Map.of("{name}", nextName)))
                .lore(lore)
                .build();
    }

    /**
     * Appends the transparent-risk block of an attempt: the success-chance bar
     * (base + Magia bonus — the value MUST match the roll in the GUI), the exact
     * curse probability and, when there is a level to lose, the downgrade risk.
     */
    private static void appendRiskLore(@NotNull List<String> lore,
                                       @NotNull AnalyzedEnchant offer,
                                       @NotNull Player player,
                                       @NotNull dev.scrulius.superenchanter.SuperEnchanterPlugin plugin) {
        final PluginConfig config = plugin.getPluginConfig();
        final MessagesConfig msg = plugin.getMessages();
        final var magia = plugin.getMagiaService();

        if (config.isSuccessChanceEnabled()) {
            final int magiaBonus = (magia != null && magia.isEnabled()) ? magia.successBonus(player) : 0;
            final int effective = EnchantFormulas.effectiveChance(
                    config.getBaseSuccessChance(offer.rarityId()), magiaBonus);
            lore.add(msg.format("enchant-icons.lore-chance", Map.of(
                    "{bar}", EnchantFormulas.progressBar(effective, 100, BAR_SEGMENTS,
                            BAR_SUCCESS_TAG, BAR_EMPTY_TAG),
                    "{chance}", String.valueOf(effective))));
        }

        if (config.isCurseChanceEnabled()) {
            final double curse = config.getCurseChance(offer.rarityId());
            if (curse > 0) {
                lore.add(msg.format("enchant-icons.lore-curse",
                        Map.of("{chance}", formatPercent(curse))));
            }
        }

        if (config.isSuccessChanceEnabled() && config.isDowngradeEnabled()
                && offer.currentLevel() >= 1) {
            final int downgrade = config.getDowngradeChance(offer.rarityId());
            if (downgrade > 0) {
                final int fallback = offer.currentLevel() - 1;
                if (fallback <= 0) {
                    lore.add(msg.format("enchant-icons.lore-downgrade-lose",
                            Map.of("{chance}", String.valueOf(downgrade))));
                } else {
                    lore.add(msg.format("enchant-icons.lore-downgrade", Map.of(
                            "{chance}", String.valueOf(downgrade),
                            "{level}", toRoman(fallback))));
                }
            }
        }
    }

    /** Formats a percent that may carry decimals: {@code 3.0 → "3"}, {@code 0.5 → "0.5"}. */
    @NotNull
    public static String formatPercent(double value) {
        if (value == Math.floor(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(Math.round(value * 10.0) / 10.0);
    }

    @NotNull
    public static String toRoman(int number) {
        return EnchantFormulas.toRoman(number);
    }

    /**
     * Title-cases a snake_case / lowercase id (e.g. {@code wither_skeleton} or
     * {@code curse}) into a display string ({@code Wither Skeleton} / {@code Curse}).
     */
    @NotNull
    private static String prettyId(@NotNull String raw) {
        final String[] words = raw.split("_");
        final StringBuilder sb = new StringBuilder(raw.length());
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                sb.append(word.substring(1).toLowerCase());
            }
        }
        return sb.toString();
    }
}
