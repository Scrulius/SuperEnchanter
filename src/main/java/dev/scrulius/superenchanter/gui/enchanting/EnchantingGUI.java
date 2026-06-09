/* SuperEnchanter - Author: Scrulius (GitHub) */
package dev.scrulius.superenchanter.gui.enchanting;

import dev.scrulius.superenchanter.SuperEnchanterPlugin;
import dev.scrulius.superenchanter.config.PluginConfig;
import dev.scrulius.superenchanter.gui.AbstractCustomGUI;
import dev.scrulius.superenchanter.gui.enchanting.EnchantingLogic.AnalyzedEnchant;
import dev.scrulius.superenchanter.gui.enchanting.EnchantingLogic.CategoryOffer;
import dev.scrulius.superenchanter.gui.enchanting.EnchantingLogic.LevelEnchantmentOffer;
import dev.scrulius.superenchanter.util.EnchantmentHelper;
import dev.scrulius.superenchanter.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A 54-slot custom enchanting GUI with a 3-tier flow:
 * <ol>
 *   <li><b>Categories</b> — the EcoEnchants types applicable to the input item.</li>
 *   <li><b>Enchantments</b> — the enchantments within the chosen category.</li>
 *   <li><b>Levels</b> — the levels of the chosen enchantment, with cost/power/reagent.</li>
 * </ol>
 * The close button doubles as a "back" button, walking one tier up at a time.
 */
public final class EnchantingGUI extends AbstractCustomGUI {

    // ── Slot constants ──────────────────────────────────────────────────────
    /** Player-head panel with the viewer's Magia bonuses (top-centre; only when Magia is active). */
    private static final int SLOT_STATS = 4;
    private static final int SLOT_INPUT = 19;
    private static final int SLOT_TABLE_DECO = 28;
    /** Decorative label that points at the potentiator slot (only when chance is enabled). */
    private static final int SLOT_BOOSTER_LABEL = 36;
    /** Input slot for an optional MythicMobs potentiator (only when chance is enabled). */
    private static final int SLOT_BOOSTER = 37;
    private static final int SLOT_POWER = 48;
    private static final int SLOT_CLOSE = 49; // also acts as back button
    private static final int SLOT_GUIDE = 50;
    private static final int SLOT_PREV_PAGE = 46;
    private static final int SLOT_NEXT_PAGE = 52;

    /** Ordered list of slots used to display offers (2 rows of 5). */
    private static final List<Integer> OFFER_SLOTS = List.of(
            21, 22, 23, 24, 25,   // row 2
            30, 31, 32, 33, 34    // row 3
    );
    private static final Set<Integer> OFFER_SLOTS_SET = Set.copyOf(OFFER_SLOTS);

    /** Decorative panes are read-only, so build them once and reuse the references. */
    private static final ItemStack FILLER_PANE = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE)
            .name("<#2D2D3D> ").build();
    private static final ItemStack EMPTY_OFFER_PANE = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE)
            .name("<#4A4A5E> ").build();

    // ── State ───────────────────────────────────────────────────────────────
    private final Block tableBlock;
    /** Whether probabilistic enchanting (and thus the potentiator slot) is active. */
    private final boolean boosterEnabled;
    /** Offers shown per page, clamped to the number of available offer slots. */
    private final int pageSize;
    /** Power breakdown computed when the menu opened (blocks don't change mid-session). */
    private final BookshelfScanner.ScanResult powerScan;
    private final int bookshelfPower;
    private int currentPage;

    /** Navigation state: category null = tier 0; enchantment null = tier 1; else tier 2. */
    private String selectedCategory = null;
    private Enchantment selectedEnchantment = null;

    /** Cached one-shot analysis of the current input item (see EnchantingLogic#analyze). */
    private List<AnalyzedEnchant> analysis = new ArrayList<>();
    private ItemStack analyzedItem = null;

    private List<CategoryOffer> categoryOffers = new ArrayList<>();
    private List<AnalyzedEnchant> enchantOffers = new ArrayList<>();
    private List<LevelEnchantmentOffer> levelOffers = new ArrayList<>();

    // ── Constructor ───────────────────────────────────────────────────────
    public EnchantingGUI(@NotNull SuperEnchanterPlugin plugin,
                         @NotNull Player player,
                         @NotNull Block tableBlock) {
        super(plugin, player, plugin.getMessages().parsed("enchanting.gui-title"));
        this.tableBlock = tableBlock;
        this.boosterEnabled = plugin.getPluginConfig().isSuccessChanceEnabled();
        this.pageSize = Math.max(1,
                Math.min(plugin.getPluginConfig().getEnchantsPerPage(), OFFER_SLOTS.size()));
        this.powerScan = BookshelfScanner.scan(tableBlock, plugin);
        this.bookshelfPower = powerScan.total();
        this.currentPage = 0;
        fillDecoration();
    }

    // ── AbstractCustomGUI implementation ────────────────────────────────────

    @Override
    protected @NotNull Set<Integer> getInputSlots() {
        // The potentiator slot only accepts items when probabilistic enchanting is on.
        return boosterEnabled ? Set.of(SLOT_INPUT, SLOT_BOOSTER) : Set.of(SLOT_INPUT);
    }

    @Override
    protected void fillDecoration() {
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, FILLER_PANE);
        }

        inventory.setItem(SLOT_INPUT, new ItemStack(Material.AIR));

        inventory.setItem(SLOT_TABLE_DECO, new ItemBuilder(Material.ENCHANTING_TABLE)
                .name(msg.raw("enchanting.table-deco-name"))
                .lore(msg.rawList("enchanting.table-deco-lore"))
                .build());

        if (boosterEnabled) {
            // Label points right at the (empty) potentiator input slot.
            inventory.setItem(SLOT_BOOSTER_LABEL, new ItemBuilder(Material.AMETHYST_SHARD)
                    .name(msg.raw("enchanting.booster-label-name"))
                    .lore(msg.rawList("enchanting.booster-label-lore"))
                    .build());
            inventory.setItem(SLOT_BOOSTER, new ItemStack(Material.AIR));
        }

        inventory.setItem(SLOT_POWER, buildPowerIcon());
        renderCloseOrBackButton();

        inventory.setItem(SLOT_GUIDE, new ItemBuilder(Material.BOOK)
                .name(msg.raw("enchanting.guide-name"))
                .lore(msg.rawList("enchanting.guide-lore"))
                .build());

        refreshStatsHead();
        clearOfferSlots();
    }

    /**
     * Renders (or refreshes) the player-head stats panel summarising every Magia bonus —
     * level, success/discount/refund, mana and the armor XP boost — in one place, so the
     * offer icons stay clean (costs/chances already show the bonuses applied). Only shown
     * when Magia is active; otherwise the slot stays decorative.
     */
    private void refreshStatsHead() {
        final var magia = plugin.getMagiaService();
        if (magia == null || !magia.isEnabled()) {
            inventory.setItem(SLOT_STATS, FILLER_PANE);
            return;
        }
        final List<String> lore = msg.formatList("enchant-icons.stats-head-lore", Map.of(
                "{level}", String.valueOf(magia.level(player)),
                "{success}", String.valueOf(magia.successBonus(player)),
                "{discount}", String.valueOf(magia.discountPercent(player)),
                "{refund}", String.valueOf(magia.refundChance(player)),
                "{mana}", String.valueOf(magia.manaBonus(player)),
                "{xp}", String.valueOf(magia.xpBonusPercent(player))));
        inventory.setItem(SLOT_STATS, new ItemBuilder(Material.PLAYER_HEAD)
                .name(msg.raw("enchant-icons.stats-head-name"))
                .lore(lore)
                .skullOwner(player)
                .build());
    }

    @Override
    protected void updatePreview() {
        final ItemStack inputItem = inventory.getItem(SLOT_INPUT);

        if (inputItem == null || inputItem.getType() == Material.AIR) {
            selectedCategory = null;
            selectedEnchantment = null;
            analysis = new ArrayList<>();
            analyzedItem = null;
            categoryOffers = new ArrayList<>();
            enchantOffers = new ArrayList<>();
            levelOffers = new ArrayList<>();
            currentPage = 0;
            clearOfferSlots();
            renderCloseOrBackButton();
            clearPaginationButtons();
            return;
        }

        // Re-scan only when the item actually changed (placed / enchanted / swapped).
        if (analyzedItem == null || !inputItem.equals(analyzedItem)) {
            analysis = EnchantingLogic.analyze(inputItem, plugin);
            analyzedItem = inputItem.clone();
            // Drop any selection that is no longer valid for the new item.
            if (selectedEnchantment != null
                    && analysis.stream().noneMatch(a -> a.enchantment().equals(selectedEnchantment))) {
                selectedEnchantment = null;
            }
            if (selectedCategory != null
                    && analysis.stream().noneMatch(a -> a.typeId().equalsIgnoreCase(selectedCategory))) {
                selectedCategory = null;
            }
        }

        if (selectedEnchantment != null) {
            levelOffers = new ArrayList<>(EnchantingLogic.getEnchantmentLevels(
                    inputItem, selectedEnchantment, bookshelfPower, plugin));
        } else if (selectedCategory != null) {
            enchantOffers = EnchantingLogic.enchantsInCategory(analysis, selectedCategory);
        } else {
            categoryOffers = EnchantingLogic.getCategories(analysis);
        }

        currentPage = 0;
        renderPage();
        renderCloseOrBackButton();
    }

    @Override
    protected void onSlotClick(@NotNull Player player, int slot,
                               @NotNull InventoryClickEvent event) {
        final PluginConfig config = plugin.getPluginConfig();

        // ── Close / Back button ────────────────────────────────────────
        if (slot == SLOT_CLOSE) {
            if (selectedEnchantment != null) {
                selectedEnchantment = null;
                plugin.getPluginConfig().getButtonClickSound().play(player);
                updatePreview();
            } else if (selectedCategory != null) {
                selectedCategory = null;
                plugin.getPluginConfig().getButtonClickSound().play(player);
                updatePreview();
            } else {
                Bukkit.getScheduler().runTask(plugin, () -> player.closeInventory());
            }
            return;
        }

        // ── Pagination ──────────────────────────────────────────────────
        if (slot == SLOT_PREV_PAGE) {
            if (currentPage > 0) {
                currentPage--;
                renderPage();
            }
            return;
        }
        if (slot == SLOT_NEXT_PAGE) {
            if (hasNextPage()) {
                currentPage++;
                renderPage();
            }
            return;
        }

        // ── Offer click — depends on the current tier ───────────────────
        if (OFFER_SLOTS_SET.contains(slot)) {
            if (selectedEnchantment != null) {
                handleLevelClick(player, slot, config);
            } else if (selectedCategory != null) {
                handleEnchantClick(player, slot);
            } else {
                handleCategoryClick(player, slot);
            }
        }
    }

    // ── Click handlers ──────────────────────────────────────────────────────

    private int offerIndexOf(int slot) {
        final int slotIndex = OFFER_SLOTS.indexOf(slot);
        if (slotIndex < 0 || slotIndex >= pageSize) {
            return -1;
        }
        return currentPage * pageSize + slotIndex;
    }

    private void handleCategoryClick(@NotNull Player player, int slot) {
        final int index = offerIndexOf(slot);
        if (index < 0 || index >= categoryOffers.size()) {
            return;
        }
        selectedCategory = categoryOffers.get(index).typeId();
        plugin.getPluginConfig().getButtonClickSound().play(player);
        updatePreview();
    }

    private void handleEnchantClick(@NotNull Player player, int slot) {
        final int index = offerIndexOf(slot);
        if (index < 0 || index >= enchantOffers.size()) {
            return;
        }
        final AnalyzedEnchant offer = enchantOffers.get(index);
        // Blocked enchantments (conflict / missing requirement / maxed / type limit)
        // cannot be opened — the icon already shows why.
        if (!offer.isAvailable()) {
            plugin.getPluginConfig().getErrorSound().play(player);
            player.sendActionBar(msg.parsed("enchanting.enchant-blocked"));
            return;
        }
        selectedEnchantment = offer.enchantment();
        plugin.getPluginConfig().getButtonClickSound().play(player);
        updatePreview();
    }

    /**
     * Per-step ("ladder") enchant: clicking a target level climbs ONE level at a time
     * from the item's current level, rolling success-chance on each rung. Each rung
     * charges only its own step cost + reagent (consumed even on a failed roll). On a
     * failed rung the climb stops, KEEPING every level already gained — so you can never
     * lose more than one rung, and retrying only pays the levels you still lack
     * (cumulative cost skips owned levels). A success booster, if present, covers the
     * whole climb and is consumed once.
     */
    private void handleLevelClick(@NotNull Player player, int slot, @NotNull PluginConfig config) {
        final int index = offerIndexOf(slot);
        if (index < 0 || index >= levelOffers.size()) {
            return;
        }
        final LevelEnchantmentOffer targetOffer = levelOffers.get(index);
        if (targetOffer.alreadyApplied()) {
            config.getErrorSound().play(player);
            return;
        }
        if (isOnCooldown()) {
            player.sendActionBar(msg.parsed("enchanting.cooldown-actionbar"));
            return;
        }
        ItemStack working = inventory.getItem(SLOT_INPUT);
        if (working == null || working.getType() == Material.AIR) {
            return;
        }

        final Enchantment enchantment = targetOffer.enchantment();
        final int targetLevel = targetOffer.level();
        final int startLevel = EnchantmentHelper.getEnchantments(working).getOrDefault(enchantment, 0);
        if (targetLevel <= startLevel) {
            config.getErrorSound().play(player);
            return;
        }

        // ── Magia (skill loop) — gating + success bonus + XP grant ──
        final var magia = plugin.getMagiaService();
        final boolean magiaOn = magia != null && magia.isEnabled();
        if (magiaOn && !magia.canEnchant(player, targetOffer.rarityId())) {
            config.getErrorSound().play(player);
            player.sendActionBar(msg.parsed("enchanting.magia-locked",
                    Map.of("{level}", String.valueOf(magia.requiredLevel(targetOffer.rarityId())))));
            return;
        }

        final org.bukkit.Location effectLoc = tableBlock.getLocation().add(0.5, 1.5, 0.5);
        final int boosterPercent = currentBoosterPercent(targetOffer.rarityId());
        final int magiaBonus = magiaOn ? magia.successBonus(player) : 0;
        final int effChance = EnchantFormulas.effectiveChance(
                config.getBaseSuccessChance(targetOffer.rarityId()), boosterPercent + magiaBonus);
        final var costType = targetOffer.cost().type();

        int reachedLevel = startLevel;
        int levelsGained = 0;
        int totalCharged = 0;
        boolean rollFailed = false;
        boolean charged = false;
        String resourceStop = null;            // "cost" / "power" / "reagent" when a rung blocks
        LevelEnchantmentOffer blockingStep = null;
        Enchantment appliedCurse = null;       // curse rolled per gained level (at most one)
        boolean refunded = false;              // Reembolso Arcano refunded the failed rung's cost
        dev.scrulius.superenchanter.economy.Cost refundedCost = null;

        for (int lvl = startLevel + 1; lvl <= targetLevel; lvl++) {
            final LevelEnchantmentOffer step = levelOffers.get(lvl - 1); // offer for level lvl
            if (bookshelfPower < step.requiredPower()) {
                resourceStop = "power"; blockingStep = step; break;
            }
            var stepCost = plugin.getCostService().effectiveCost(player, step.stepCost());
            if (magiaOn) stepCost = magia.applyDiscount(player, stepCost); // Carril 2
            if (!plugin.getCostService().canAfford(player, stepCost)) {
                resourceStop = "cost"; blockingStep = step; break;
            }
            if (!EnchantingLogic.hasReagent(player, step.reagent())) {
                resourceStop = "reagent"; blockingStep = step; break;
            }
            if (!plugin.getCostService().deduct(player, stepCost)) {
                resourceStop = "cost"; blockingStep = step; break;
            }
            EnchantingLogic.consumeReagent(player, step.reagent());
            totalCharged += stepCost.intAmount();
            if (!charged) {
                applyCooldown();
                charged = true;
            }

            final boolean ok = !config.isSuccessChanceEnabled()
                    || java.util.concurrent.ThreadLocalRandom.current().nextInt(100) < effChance;
            if (!ok) {
                rollFailed = true;
                // Sub-ability "Reembolso Arcano": chance (scales with Magia) to refund
                // this failed rung's cost — softens the success-chance sink.
                if (magiaOn && magia.refundChance(player)
                        > java.util.concurrent.ThreadLocalRandom.current().nextInt(100)) {
                    plugin.getCostService().refund(player, stepCost);
                    totalCharged -= stepCost.intAmount();
                    refunded = true;
                    refundedCost = stepCost;
                }
                break; // keep what we have; this rung's cost + reagent is the loss
            }
            working = EnchantingLogic.applyEnchantment(working, enchantment, lvl);
            reachedLevel = lvl;
            levelsGained++;

            // Curse roll PER gained level (so climbing in one click or step-by-step is
            // equally risky), but at most one curse per operation.
            if (appliedCurse == null) {
                appliedCurse = maybeApplyCurse(working, targetOffer.rarityId());
                if (appliedCurse != null) {
                    working = EnchantingLogic.applyEnchantment(working, appliedCurse, 1);
                }
            }
        }

        // One booster covers the whole climb (a guarantee seal guarantees every rung).
        if (boosterPercent > 0 && charged && (levelsGained > 0 || !config.isBoostersConsumedOnSuccessOnly())) {
            consumeBooster();
        }

        // Nothing happened (the very first rung blocked on resources) → explain, no cooldown.
        if (!charged && resourceStop != null) {
            config.getErrorSound().play(player);
            sendResourceError(player, resourceStop, blockingStep);
            return;
        }

        com.willfp.eco.core.display.Display.display(working, player);
        inventory.setItem(SLOT_INPUT, working);

        // Magia XP for the operation (per level gained + a slice on a failed rung). Granted
        // BEFORE feedback so the action bar can report how much XP was earned ({magia}).
        final double magiaXp = (magiaOn && charged)
                ? magia.grantXp(player, targetOffer.rarityId(), levelsGained, rollFailed)
                : 0.0;
        // The operation may have raised the Magia level (new bonuses) → refresh the panel.
        if (magiaXp > 0) {
            refreshStatsHead();
        }
        final String magiaInfo = magiaXp > 0
                ? msg.format("enchanting.magia-xp-suffix",
                        Map.of("{xp}", String.valueOf((long) Math.round(magiaXp))))
                : "";
        final String refundInfo = (refunded && refundedCost != null)
                ? msg.format("enchanting.refund-suffix", Map.of("{cost}", refundedCost.displayText()))
                : "";

        // ── Feedback ────────────────────────────────────────────────────────────
        if (levelsGained == 0) {
            // First rung failed the RNG roll. Reembolso Arcano changes the message
            // (you didn't lose the cost) when it kicked in.
            config.getEnchantFailSound().play(player);
            config.getEnchantFailParticle().spawn(player.getWorld(), effectLoc);
            player.sendActionBar(msg.parsed(
                    refunded ? "enchanting.enchant-fail-refund" : "enchanting.enchant-fail",
                    Map.of("{enchantment}", levelName(targetOffer, startLevel + 1),
                            "{chance}", String.valueOf(effChance),
                            "{cost}", refundedCost != null ? refundedCost.displayText() : "",
                            "{magia}", magiaInfo)));
        } else if (appliedCurse != null) {
            config.getEnchantFailSound().play(player);
            config.getEnchantFailParticle().spawn(player.getWorld(), effectLoc);
            player.sendActionBar(msg.parsed("enchanting.cursed",
                    Map.of("{enchantment}", levelName(targetOffer, reachedLevel),
                            "{curse}", plugin.getEcoHook().displayNameOrFallback(appliedCurse),
                            "{magia}", magiaInfo)));
        } else if (rollFailed || resourceStop != null) {
            // Partial climb: reached an intermediate level, then a rung failed / ran out.
            config.getEnchantSuccessSound().play(player);
            config.getEnchantSuccessParticle().spawn(player.getWorld(), effectLoc);
            player.sendActionBar(msg.parsed("enchanting.enchant-partial",
                    Map.of("{enchantment}", levelName(targetOffer, reachedLevel),
                            "{next}", EnchantFormulas.toRoman(reachedLevel + 1),
                            "{refund}", refundInfo,
                            "{magia}", magiaInfo)));
        } else {
            config.getEnchantSuccessSound().play(player);
            config.getEnchantSuccessParticle().spawn(player.getWorld(), effectLoc);
            player.sendActionBar(msg.parsed("enchanting.enchant-success",
                    Map.of("{enchantment}", levelName(targetOffer, reachedLevel),
                            "{magia}", magiaInfo)));
        }

        plugin.getAuditLog().record(player,
                levelsGained == 0 ? "ENCHANT-X" : (appliedCurse != null ? "ENCHANT-CURSE" : "ENCHANT"),
                dev.scrulius.superenchanter.util.AuditLog.describe(working)
                        + " " + enchantment.getKey().getKey() + " " + startLevel + "->" + reachedLevel
                        + (appliedCurse != null ? " CURSE:" + appliedCurse.getKey().getKey() : ""),
                new dev.scrulius.superenchanter.economy.Cost(costType, totalCharged));

        updatePreview();
        persistInputItems();
    }

    /** Display name with its roman level suffix (omitted for single-level enchantments). */
    private String levelName(@NotNull LevelEnchantmentOffer offer, int level) {
        return offer.displayName() + (offer.maxLevel() > 1 ? " " + EnchantFormulas.toRoman(level) : "");
    }

    /** Sends the specific "can't afford / not enough power / reagent" actionbar for a blocked rung. */
    private void sendResourceError(@NotNull Player player, @NotNull String reason,
                                   @NotNull LevelEnchantmentOffer step) {
        switch (reason) {
            case "power" -> player.sendActionBar(msg.parsed("enchanting.not-enough-power",
                    Map.of("{required}", String.valueOf(step.requiredPower()),
                            "{current}", String.valueOf(bookshelfPower))));
            case "reagent" -> player.sendActionBar(msg.parsed("enchanting.not-enough-reagent",
                    EnchantingLogic.reagentPlaceholders(step.reagent())));
            default -> {
                var c = plugin.getCostService().effectiveCost(player, step.stepCost());
                final var m = plugin.getMagiaService();
                if (m != null && m.isEnabled()) c = m.applyDiscount(player, c); // Carril 2
                player.sendActionBar(msg.parsed("enchanting.not-enough-xp",
                        Map.of("{cost}", c.displayText(),
                                "{balance}", plugin.getCostService().balanceText(player, c.type()))));
            }
        }
    }

    /**
     * Returns the potentiator percentage the item in the booster slot contributes for
     * an enchantment of the given rarity. A rarity-targeted orb only contributes to
     * its matching rarity, so the same orb can guarantee one rarity and do nothing
     * for another.
     *
     * @param enchantRarity the rarity id of the enchantment being attempted
     * @return the contributed percent, or {@code 0} when off / empty / wrong rarity
     */
    private int currentBoosterPercent(@org.jetbrains.annotations.Nullable String enchantRarity) {
        if (!boosterEnabled) {
            return 0;
        }
        final ItemStack booster = inventory.getItem(SLOT_BOOSTER);
        if (booster == null || booster.getType() == Material.AIR) {
            return 0;
        }
        final String mythicId = plugin.getMythicMobsHook().getItemId(booster);
        return plugin.getPluginConfig().getBoosterPercent(mythicId, enchantRarity);
    }

    /**
     * When a seal sits in the slot but doesn't contribute for {@code enchantRarity}
     * (it's bound to a different, specific rarity), returns that seal's target rarity
     * id so the icon can explain the mismatch. Returns {@code null} when there's no
     * seal, it isn't a configured seal, or it's a universal seal (which always applies,
     * so a 0% there means the feature is off rather than a rarity mismatch).
     */
    @org.jetbrains.annotations.Nullable
    private String sealMismatchRarityId(@org.jetbrains.annotations.Nullable String enchantRarity) {
        if (!boosterEnabled) {
            return null;
        }
        final ItemStack booster = inventory.getItem(SLOT_BOOSTER);
        if (booster == null || booster.getType() == Material.AIR) {
            return null;
        }
        final String mythicId = plugin.getMythicMobsHook().getItemId(booster);
        final PluginConfig.Booster b = plugin.getPluginConfig().getBooster(mythicId);
        if (b == null || b.rarity() == null) {
            return null; // not a seal, or universal seal (no specific rarity to name)
        }
        return b.percentFor(enchantRarity) > 0 ? null : b.rarity();
    }

    /**
     * Rolls for a curse on a successful enchant. Returns the curse to apply, or {@code null}
     * if the feature is off, the roll missed, or no curse targets the item. By design there
     * is NO prevention — the curse is an unavoidable gamble; the cure is the Sello Purificador
     * in the anvil.
     *
     * @param enchanted the freshly enchanted item (the curse must target it)
     * @param rarityId  the rarity of the enchant just applied (per-rarity curse chance)
     * @return the curse to apply, or {@code null} if none
     */
    private org.bukkit.enchantments.Enchantment maybeApplyCurse(
            @NotNull ItemStack enchanted,
            @org.jetbrains.annotations.Nullable String rarityId) {
        final PluginConfig config = plugin.getPluginConfig();
        if (!config.isCurseChanceEnabled()) {
            return null;
        }
        final double chance = config.getCurseChance(rarityId);
        if (chance <= 0
                || java.util.concurrent.ThreadLocalRandom.current().nextDouble(100.0) >= chance) {
            return null; // feature on but the roll missed
        }
        return plugin.getEcoHook().randomApplicableCurse(
                enchanted, config.getExcludedCurseKeys());
    }

    /** Consumes one potentiator from the booster slot, clearing it when it runs out. */
    private void consumeBooster() {
        final ItemStack booster = inventory.getItem(SLOT_BOOSTER);
        if (booster == null || booster.getType() == Material.AIR) {
            return;
        }
        final int left = booster.getAmount() - 1;
        if (left <= 0) {
            inventory.setItem(SLOT_BOOSTER, new ItemStack(Material.AIR));
        } else {
            booster.setAmount(left);
            inventory.setItem(SLOT_BOOSTER, booster);
        }
    }

    // ── Rendering helpers ───────────────────────────────────────────────────

    private int currentTotal() {
        if (selectedEnchantment != null) {
            return levelOffers.size();
        }
        if (selectedCategory != null) {
            return enchantOffers.size();
        }
        return categoryOffers.size();
    }

    private void renderPage() {
        final int total = currentTotal();
        final int start = currentPage * pageSize;
        final int end = Math.min(start + pageSize, total);

        for (int i = 0; i < OFFER_SLOTS.size(); i++) {
            final int offerIndex = start + i;
            final int guiSlot = OFFER_SLOTS.get(i);

            if (offerIndex < end) {
                if (selectedEnchantment != null) {
                    final LevelEnchantmentOffer lvlOffer = levelOffers.get(offerIndex);
                    final int bp = currentBoosterPercent(lvlOffer.rarityId());
                    inventory.setItem(guiSlot,
                            EnchantingLogic.createLevelOfferIcon(lvlOffer, player, bp,
                                    bp > 0 ? null : sealMismatchRarityId(lvlOffer.rarityId()), plugin));
                } else if (selectedCategory != null) {
                    inventory.setItem(guiSlot,
                            EnchantingLogic.createEnchantIcon(enchantOffers.get(offerIndex), player, plugin));
                } else {
                    inventory.setItem(guiSlot,
                            EnchantingLogic.createCategoryIcon(categoryOffers.get(offerIndex), plugin));
                }
            } else {
                inventory.setItem(guiSlot, EMPTY_OFFER_PANE);
            }
        }

        renderPaginationButtons();
    }

    private void renderCloseOrBackButton() {
        final boolean atRoot = selectedCategory == null && selectedEnchantment == null;
        if (atRoot) {
            inventory.setItem(SLOT_CLOSE, new ItemBuilder(Material.BARRIER)
                    .name(msg.raw("enchanting.close-name"))
                    .build());
        } else {
            inventory.setItem(SLOT_CLOSE, new ItemBuilder(Material.ARROW)
                    .name(msg.raw("enchanting.back-name"))
                    .lore(msg.rawList("enchanting.back-lore"))
                    .build());
        }
    }

    private void renderPaginationButtons() {
        final int total = currentTotal();
        final int totalPages = Math.max(1, (int) Math.ceil((double) total / pageSize));

        if (currentPage > 0) {
            inventory.setItem(SLOT_PREV_PAGE, new ItemBuilder(Material.ARROW)
                    .name(msg.raw("enchanting.prev-page-name"))
                    .lore(msg.format("enchanting.prev-page-lore",
                            Map.of("{current}", String.valueOf(currentPage),
                                    "{total}", String.valueOf(totalPages))))
                    .build());
        } else {
            inventory.setItem(SLOT_PREV_PAGE, FILLER_PANE);
        }

        if (hasNextPage()) {
            inventory.setItem(SLOT_NEXT_PAGE, new ItemBuilder(Material.ARROW)
                    .name(msg.raw("enchanting.next-page-name"))
                    .lore(msg.format("enchanting.next-page-lore",
                            Map.of("{current}", String.valueOf(currentPage + 2),
                                    "{total}", String.valueOf(totalPages))))
                    .build());
        } else {
            inventory.setItem(SLOT_NEXT_PAGE, FILLER_PANE);
        }
    }

    private void clearOfferSlots() {
        for (int slot : OFFER_SLOTS) {
            inventory.setItem(slot, EMPTY_OFFER_PANE);
        }
    }

    private void clearPaginationButtons() {
        inventory.setItem(SLOT_PREV_PAGE, FILLER_PANE);
        inventory.setItem(SLOT_NEXT_PAGE, FILLER_PANE);
    }

    @NotNull
    private ItemStack buildPowerIcon() {
        return new ItemBuilder(Material.CHISELED_BOOKSHELF)
                .name(msg.raw("enchanting.power-name"))
                .lore(msg.formatList("enchanting.power-lore",
                        Map.of("{current}", String.valueOf(powerScan.total()),
                                "{vanilla}", String.valueOf(powerScan.vanilla()),
                                "{library}", String.valueOf(powerScan.library()),
                                "{max}", String.valueOf(powerScan.max()))))
                .build();
    }

    private boolean hasNextPage() {
        return (currentPage + 1) * pageSize < currentTotal();
    }
}
