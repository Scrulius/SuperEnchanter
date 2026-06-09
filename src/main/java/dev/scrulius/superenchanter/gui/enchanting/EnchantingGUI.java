/* SuperEnchanter - Author: Scrulius (GitHub) */
package dev.scrulius.superenchanter.gui.enchanting;

import dev.scrulius.superenchanter.SuperEnchanterPlugin;
import dev.scrulius.superenchanter.config.PluginConfig;
import dev.scrulius.superenchanter.economy.Cost;
import dev.scrulius.superenchanter.gui.AbstractCustomGUI;
import dev.scrulius.superenchanter.gui.enchanting.EnchantingLogic.AnalyzedEnchant;
import dev.scrulius.superenchanter.gui.enchanting.EnchantingLogic.CategoryOffer;
import dev.scrulius.superenchanter.gui.enchanting.EnchantingLogic.NextStep;
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
import java.util.concurrent.ThreadLocalRandom;

/**
 * A 54-slot custom enchanting GUI with a 2-tier flow:
 * <ol>
 *   <li><b>Categories</b> — the EcoEnchants types applicable to the input item.</li>
 *   <li><b>Enchantments</b> — each icon shows the NEXT level, its exact success
 *       chance (progress bar), curse probability and downgrade risk; clicking it
 *       attempts that single rung.</li>
 * </ol>
 * Progression is strictly sequential (I → II → ... — no level jumping), the only
 * resource consumed is XP, and a failed attempt may additionally downgrade the
 * enchantment one level (the hardcore penalty). The close button doubles as a
 * "back" button at tier 2. (The rarity-seal slot was removed with the seals.)
 */
public final class EnchantingGUI extends AbstractCustomGUI {

    // ── Slot constants ──────────────────────────────────────────────────────
    /** Player-head panel with the viewer's Magia bonuses (top-centre; only when Magia is active). */
    private static final int SLOT_STATS = 4;
    /** Enchanting-table icon, to the LEFT of the input. */
    private static final int SLOT_TABLE_DECO = 19;
    /** Item-to-enchant input (col 2); the table icon sits to its left. */
    private static final int SLOT_INPUT = 20;
    private static final int SLOT_POWER = 48;
    private static final int SLOT_CLOSE = 49; // also acts as back button
    private static final int SLOT_GUIDE = 50;
    private static final int SLOT_PREV_PAGE = 46;
    private static final int SLOT_NEXT_PAGE = 52;

    /** Ordered list of slots used to display offers (2 rows of 4, shifted right). */
    private static final List<Integer> OFFER_SLOTS = List.of(
            22, 23, 24, 25,   // row 2
            31, 32, 33, 34    // row 3
    );
    private static final Set<Integer> OFFER_SLOTS_SET = Set.copyOf(OFFER_SLOTS);

    /** MiniMessage tags for the Magia-level and bookshelf-power progress bars. */
    private static final String BAR_MAGIA_TAG = "<gradient:#C084FC:#7C3AED>";
    private static final String BAR_POWER_TAG = "<gradient:#64DFDF:#38D9A9>";

    /** Decorative panes are read-only, so build them once and reuse the references. */
    private static final ItemStack FILLER_PANE = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE)
            .name("<#2D2D3D> ").build();
    private static final ItemStack EMPTY_OFFER_PANE = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE)
            .name("<#4A4A5E> ").build();

    // ── State ───────────────────────────────────────────────────────────────
    private final Block tableBlock;
    /** Offers shown per page, clamped to the number of available offer slots. */
    private final int pageSize;
    /** Power breakdown computed when the menu opened (blocks don't change mid-session). */
    private final BookshelfScanner.ScanResult powerScan;
    private final int bookshelfPower;
    private int currentPage;

    /** Navigation state: category null = tier 1 (categories); else tier 2 (enchantments). */
    private String selectedCategory = null;

    /** Cached one-shot analysis of the current input item (see EnchantingLogic#analyze). */
    private List<AnalyzedEnchant> analysis = new ArrayList<>();
    private ItemStack analyzedItem = null;

    private List<CategoryOffer> categoryOffers = new ArrayList<>();
    private List<AnalyzedEnchant> enchantOffers = new ArrayList<>();

    // ── Constructor ───────────────────────────────────────────────────────
    public EnchantingGUI(@NotNull SuperEnchanterPlugin plugin,
                         @NotNull Player player,
                         @NotNull Block tableBlock) {
        super(plugin, player, plugin.getMessages().parsed("enchanting.gui-title"));
        this.tableBlock = tableBlock;
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
        return Set.of(SLOT_INPUT);
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
     * a level progress bar, success/discount/refund and the armor XP boost — in one
     * place, so the offer icons stay clean (costs/chances already show the bonuses
     * applied). Only shown when Magia is active; otherwise the slot stays decorative.
     */
    private void refreshStatsHead() {
        final var magia = plugin.getMagiaService();
        if (magia == null || !magia.isEnabled()) {
            inventory.setItem(SLOT_STATS, FILLER_PANE);
            return;
        }
        final int level = magia.level(player);
        final int maxLevel = magia.maxLevel();
        final List<String> lore = msg.formatList("enchant-icons.stats-head-lore", Map.of(
                "{bar}", EnchantFormulas.progressBar(level, maxLevel,
                        EnchantingLogic.BAR_SEGMENTS, BAR_MAGIA_TAG, EnchantingLogic.BAR_EMPTY_TAG),
                "{level}", String.valueOf(level),
                "{max}", String.valueOf(maxLevel),
                "{success}", String.valueOf(magia.successBonus(player)),
                "{discount}", String.valueOf(magia.discountPercent(player)),
                "{refund}", String.valueOf(magia.refundChance(player)),
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
            analysis = new ArrayList<>();
            analyzedItem = null;
            categoryOffers = new ArrayList<>();
            enchantOffers = new ArrayList<>();
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
            if (selectedCategory != null
                    && analysis.stream().noneMatch(a -> a.typeId().equalsIgnoreCase(selectedCategory))) {
                selectedCategory = null;
            }
        }

        if (selectedCategory != null) {
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
        // ── Close / Back button ────────────────────────────────────────
        if (slot == SLOT_CLOSE) {
            if (selectedCategory != null) {
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
            if (selectedCategory != null) {
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

    /**
     * Attempts ONE rung of the clicked enchantment — the icon already showed the
     * exact chance, cost, curse % and downgrade risk for this attempt. Sequential
     * by design: the only purchasable level is the next one.
     * <p>
     * Outcome tree: the cost is charged up front (consumed even on failure). A
     * successful roll applies the level and then rolls the curse chance. A failed
     * roll rolls Reembolso Arcano (Magia refund) AND, when there was a level to
     * lose, the downgrade penalty (one level down; level I drops to nothing).
     */
    private void handleEnchantClick(@NotNull Player player, int slot) {
        final PluginConfig config = plugin.getPluginConfig();
        final int index = offerIndexOf(slot);
        if (index < 0 || index >= enchantOffers.size()) {
            return;
        }
        final AnalyzedEnchant offer = enchantOffers.get(index);
        if (!offer.isAvailable()) {
            config.getErrorSound().play(player);
            player.sendActionBar(msg.parsed("enchanting.enchant-blocked"));
            return;
        }
        ItemStack working = inventory.getItem(SLOT_INPUT);
        if (working == null || working.getType() == Material.AIR) {
            return;
        }
        // Stale-click guard: the icon was rendered for offer.currentLevel(); if the
        // item changed under us, re-render instead of acting on outdated numbers.
        final Enchantment enchantment = offer.enchantment();
        final int currentLevel = EnchantmentHelper.getEnchantments(working)
                .getOrDefault(enchantment, 0);
        if (currentLevel != offer.currentLevel()) {
            updatePreview();
            return;
        }

        final NextStep step = EnchantingLogic.nextStep(offer, plugin);
        if (step == null) {
            config.getErrorSound().play(player);
            player.sendActionBar(msg.parsed("enchanting.maxed-actionbar"));
            return;
        }
        if (isOnCooldown()) {
            player.sendActionBar(msg.parsed("enchanting.cooldown-actionbar"));
            return;
        }

        // ── Magia (skill loop) — gating + success bonus + XP grant ──
        final var magia = plugin.getMagiaService();
        final boolean magiaOn = magia != null && magia.isEnabled();
        if (magiaOn && !magia.canEnchant(player, offer.rarityId())) {
            config.getErrorSound().play(player);
            player.sendActionBar(msg.parsed("enchanting.magia-locked",
                    Map.of("{level}", String.valueOf(magia.requiredLevel(offer.rarityId())))));
            return;
        }

        // ── Resource checks: bookshelf power, then the XP cost ──
        if (bookshelfPower < step.requiredPower()) {
            config.getErrorSound().play(player);
            player.sendActionBar(msg.parsed("enchanting.not-enough-power",
                    Map.of("{required}", String.valueOf(step.requiredPower()),
                            "{current}", String.valueOf(bookshelfPower))));
            return;
        }
        Cost cost = plugin.getCostService().effectiveCost(player, step.stepCost());
        if (magiaOn) {
            cost = magia.applyDiscount(player, cost);
        }
        if (!plugin.getCostService().canAfford(player, cost)
                || !plugin.getCostService().deduct(player, cost)) {
            config.getErrorSound().play(player);
            player.sendActionBar(msg.parsed("enchanting.not-enough-xp",
                    Map.of("{cost}", cost.displayText(),
                            "{balance}", plugin.getCostService().balanceText(player, cost.type()))));
            return;
        }
        applyCooldown();

        // ── The roll ──
        final int magiaBonus = magiaOn ? magia.successBonus(player) : 0;
        final int effChance = EnchantFormulas.effectiveChance(
                config.getBaseSuccessChance(offer.rarityId()), magiaBonus);
        final boolean ok = !config.isSuccessChanceEnabled()
                || ThreadLocalRandom.current().nextInt(100) < effChance;

        Enchantment appliedCurse = null;
        boolean refunded = false;
        boolean downgraded = false;
        int newLevel = currentLevel;
        int charged = cost.intAmount();

        if (ok) {
            working = EnchantingLogic.applyEnchantment(working, enchantment, step.level());
            newLevel = step.level();
            appliedCurse = maybeApplyCurse(working, offer.rarityId());
            if (appliedCurse != null) {
                working = EnchantingLogic.applyEnchantment(working, appliedCurse, 1);
            }
        } else {
            // Reembolso Arcano (Magia): chance to recover this attempt's cost.
            if (magiaOn && magia.refundChance(player) > ThreadLocalRandom.current().nextInt(100)) {
                plugin.getCostService().refund(player, cost);
                charged = 0;
                refunded = true;
            }
            // Downgrade penalty: only when there is a level to lose; level I drops
            // to nothing (the enchantment is removed outright).
            if (config.isDowngradeEnabled() && currentLevel >= 1
                    && ThreadLocalRandom.current().nextInt(100)
                            < config.getDowngradeChance(offer.rarityId())) {
                downgraded = true;
                newLevel = currentLevel - 1;
                working = newLevel <= 0
                        ? EnchantmentHelper.removeEnchantment(working, enchantment)
                        : EnchantingLogic.applyEnchantment(working, enchantment, newLevel);
            }
        }

        com.willfp.eco.core.display.Display.display(working, player);
        inventory.setItem(SLOT_INPUT, working);

        // Magia XP (per level gained + a slice on failure). Granted BEFORE feedback
        // so the action bar can report how much XP was earned ({magia}).
        final double magiaXp = magiaOn
                ? magia.grantXp(player, offer.rarityId(), ok ? 1 : 0, !ok)
                : 0.0;
        if (magiaXp > 0) {
            refreshStatsHead(); // the attempt may have raised the Magia level
        }
        final String magiaInfo = magiaXp > 0
                ? msg.format("enchanting.magia-xp-suffix",
                        Map.of("{xp}", String.valueOf((long) Math.round(magiaXp))))
                : "";
        final String refundInfo = refunded
                ? msg.format("enchanting.refund-suffix", Map.of("{cost}", cost.displayText()))
                : "";
        final String attemptName = levelName(offer, step.level());

        // ── Feedback ────────────────────────────────────────────────────────
        final org.bukkit.Location effectLoc = tableBlock.getLocation().add(0.5, 1.5, 0.5);
        if (ok && appliedCurse != null) {
            config.getEnchantFailSound().play(player);
            config.getEnchantFailParticle().spawn(player.getWorld(), effectLoc);
            player.sendActionBar(msg.parsed("enchanting.cursed",
                    Map.of("{enchantment}", attemptName,
                            "{curse}", plugin.getEcoHook().displayNameOrFallback(appliedCurse),
                            "{magia}", magiaInfo)));
        } else if (ok) {
            config.getEnchantSuccessSound().play(player);
            config.getEnchantSuccessParticle().spawn(player.getWorld(), effectLoc);
            player.sendActionBar(msg.parsed("enchanting.enchant-success",
                    Map.of("{enchantment}", attemptName, "{magia}", magiaInfo)));
        } else if (downgraded) {
            config.getEnchantDowngradeSound().play(player);
            config.getEnchantFailParticle().spawn(player.getWorld(), effectLoc);
            final String key = newLevel <= 0
                    ? "enchanting.enchant-downgrade-lost" : "enchanting.enchant-downgrade";
            player.sendActionBar(msg.parsed(key,
                    Map.of("{enchantment}", attemptName,
                            "{level}", levelName(offer, newLevel),
                            "{chance}", String.valueOf(effChance),
                            "{refund}", refundInfo,
                            "{magia}", magiaInfo)));
        } else {
            config.getEnchantFailSound().play(player);
            config.getEnchantFailParticle().spawn(player.getWorld(), effectLoc);
            player.sendActionBar(msg.parsed(
                    refunded ? "enchanting.enchant-fail-refund" : "enchanting.enchant-fail",
                    Map.of("{enchantment}", attemptName,
                            "{chance}", String.valueOf(effChance),
                            "{cost}", cost.displayText(),
                            "{magia}", magiaInfo)));
        }

        // ── Audit ───────────────────────────────────────────────────────────
        final var audit = plugin.getAuditLog();
        final String action = ok
                ? (appliedCurse != null ? "ENCHANT-CURSE" : "ENCHANT")
                : (downgraded ? "ENCHANT-DOWN" : "ENCHANT-X");
        final String auditSummary = enchantment.getKey().getKey()
                + " " + currentLevel + " -> " + newLevel
                + (appliedCurse != null ? "  +curse:" + appliedCurse.getKey().getKey() : "");
        audit.record(player, action, auditSummary,
                new Cost(cost.type(), charged), audit.snap("item", working));

        updatePreview();
        persistInputItems();
    }

    /** Display name with its roman level suffix (omitted for single-level enchantments). */
    private String levelName(@NotNull AnalyzedEnchant offer, int level) {
        return offer.displayName()
                + (offer.maxLevel() > 1 && level > 0 ? " " + EnchantFormulas.toRoman(level) : "");
    }

    /**
     * Rolls for a curse on a successful enchant. Returns the curse to apply, or {@code null}
     * if the feature is off, the roll missed, or no curse targets the item. By design there
     * is NO prevention — the curse is an unavoidable gamble; the cure is the Sello Purificador
     * in the anvil. The exact probability is shown in the icon before clicking.
     *
     * @param enchanted the freshly enchanted item (the curse must target it)
     * @param rarityId  the rarity of the enchant just applied (per-rarity curse chance)
     * @return the curse to apply, or {@code null} if none
     */
    private Enchantment maybeApplyCurse(@NotNull ItemStack enchanted,
                                        @org.jetbrains.annotations.Nullable String rarityId) {
        final PluginConfig config = plugin.getPluginConfig();
        if (!config.isCurseChanceEnabled()) {
            return null;
        }
        final double chance = config.getCurseChance(rarityId);
        if (chance <= 0 || ThreadLocalRandom.current().nextDouble(100.0) >= chance) {
            return null; // feature on but the roll missed
        }
        return plugin.getEcoHook().randomApplicableCurse(
                enchanted, config.getExcludedCurseKeys());
    }

    // ── Rendering helpers ───────────────────────────────────────────────────

    private int currentTotal() {
        return selectedCategory != null ? enchantOffers.size() : categoryOffers.size();
    }

    private void renderPage() {
        final int total = currentTotal();
        final int start = currentPage * pageSize;
        final int end = Math.min(start + pageSize, total);

        for (int i = 0; i < OFFER_SLOTS.size(); i++) {
            final int offerIndex = start + i;
            final int guiSlot = OFFER_SLOTS.get(i);

            if (offerIndex < end) {
                if (selectedCategory != null) {
                    final AnalyzedEnchant offer = enchantOffers.get(offerIndex);
                    inventory.setItem(guiSlot, EnchantingLogic.createEnchantIcon(
                            offer, EnchantingLogic.nextStep(offer, plugin),
                            player, bookshelfPower, plugin));
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
        if (selectedCategory == null) {
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
                .lore(msg.formatList("enchanting.power-lore", Map.of(
                        "{bar}", EnchantFormulas.progressBar(powerScan.total(), powerScan.max(),
                                EnchantingLogic.BAR_SEGMENTS, BAR_POWER_TAG, EnchantingLogic.BAR_EMPTY_TAG),
                        "{current}", String.valueOf(powerScan.total()),
                        "{vanilla}", String.valueOf(powerScan.vanilla()),
                        "{library}", String.valueOf(powerScan.library()),
                        "{max}", String.valueOf(powerScan.max()))))
                .build();
    }

    private boolean hasNextPage() {
        return (currentPage + 1) * pageSize < currentTotal();
    }
}
