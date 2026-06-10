/* SuperEnchanter - Author: Scrulius (GitHub) */
package dev.scrulius.superenchanter.gui.anvil;

import dev.scrulius.superenchanter.SuperEnchanterPlugin;
import dev.scrulius.superenchanter.config.PluginConfig;
import dev.scrulius.superenchanter.economy.Cost;
import dev.scrulius.superenchanter.economy.CostService;
import dev.scrulius.superenchanter.gui.AbstractCustomGUI;
import dev.scrulius.superenchanter.gui.anvil.AnvilLogic.AnvilResult;
import dev.scrulius.superenchanter.gui.enchanting.EnchantingLogic;
import dev.scrulius.superenchanter.gui.enchanting.EnchantingLogic.AnalyzedEnchant;
import dev.scrulius.superenchanter.util.AuditLog;
import dev.scrulius.superenchanter.util.ItemBuilder;
import dev.scrulius.superenchanter.util.MiniMessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A clean, guided custom anvil GUI sharing the plugin's GUI language with the
 * transfer menu: two labelled inputs on the sides, the outcome in the centre, one
 * dedicated action button below it.
 * <pre>
 * Row0:  ·  ·  ·  ·  ·  ·  ·  ·  ·
 * Row1:  ·  Il ·  ·  ·  ·  ·  Sl ·     Il/Sl = item / sacrifice labels
 * Row2:  ·  IN ·  ·  ·  ·  ·  SA ·     IN=item input · SA=sacrifice input
 * Row3:  ·  ·  ·  ·  RP ·  ·  ·  ·     RP = result preview
 * Row4:  ·  ·  ·  ·  FO ·  ·  ·  ·     FO = forge button
 * Row5:  I  ·  ·  ·  ·  ·  ·  ·  C     I=info · C=close
 * </pre>
 * The result preview is the single source of status — it shows the merged item
 * (valid), a barrier with the conflicting enchantments (incompatible) or a
 * placeholder (incomplete) — so there is no separate indicator. Clicking the
 * result does nothing; the dedicated Forge button performs the operation.
 */
public class AnvilGUI extends AbstractCustomGUI {

    // ── Slot constants ────────────────────────────────────────────────────
    private static final int SLOT_ITEM_LABEL = 10;
    private static final int SLOT_INPUT = 19;
    private static final int SLOT_SAC_LABEL = 16;
    private static final int SLOT_SACRIFICE = 25;
    private static final int SLOT_RESULT = 31;
    private static final int SLOT_FORGE = 40;
    private static final int SLOT_INFO = 45;
    private static final int SLOT_CLOSE = 53;

    private static final Set<Integer> INPUT_SLOTS = Set.of(SLOT_INPUT, SLOT_SACRIFICE);

    /** Clean dark frame so the inputs, result and forge button stand out. */
    private static final ItemStack FRAME = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE)
            .name(" ").build();

    // ── Instance state ────────────────────────────────────────────────────
    private final PluginConfig config;

    /** Cached EcoEnchants gate, rebuilt only when the target item changes. */
    private ItemStack gateItem;
    private AnvilEnchantGate cachedGate = AnvilEnchantGate.ALLOW_ALL;

    public AnvilGUI(@NotNull SuperEnchanterPlugin plugin, @NotNull Player player) {
        super(plugin, player, plugin.getMessages().parsed("anvil.gui-title"));
        this.config = plugin.getPluginConfig();
        fillDecoration();
    }

    @Override
    @NotNull
    protected Set<Integer> getInputSlots() {
        return INPUT_SLOTS;
    }

    @Override
    @NotNull
    protected dev.scrulius.superenchanter.config.SoundEffect getOpenSound() {
        return config.getGuiOpenAnvilSound();
    }

    @Override
    protected void fillDecoration() {
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, FRAME);
        }

        inventory.setItem(SLOT_ITEM_LABEL, new ItemBuilder(Material.CYAN_STAINED_GLASS_PANE)
                .name(msg.raw("anvil.item-label-name"))
                .lore(msg.rawList("anvil.item-label-lore")).build());
        inventory.setItem(SLOT_SAC_LABEL, new ItemBuilder(Material.ORANGE_STAINED_GLASS_PANE)
                .name(msg.raw("anvil.sacrifice-label-name"))
                .lore(msg.rawList("anvil.sacrifice-label-lore")).build());

        inventory.setItem(SLOT_INFO, new ItemBuilder(Material.PAPER)
                .name(msg.raw("anvil.info-name"))
                .lore(msg.rawList("anvil.info-lore")).build());
        inventory.setItem(SLOT_CLOSE, new ItemBuilder(Material.BARRIER)
                .name(msg.raw("anvil.close-name"))
                .lore(msg.raw("anvil.close-lore")).build());

        inventory.setItem(SLOT_INPUT, null);
        inventory.setItem(SLOT_SACRIFICE, null);

        showEmptyResult();
    }

    @Override
    protected void onSlotClick(@NotNull Player player, int slot, @NotNull InventoryClickEvent event) {
        switch (slot) {
            case SLOT_CLOSE -> Bukkit.getScheduler().runTask(plugin, () -> player.closeInventory());
            case SLOT_FORGE -> attemptForge(player);
            default -> {
                // Labels, result preview and decoration are display-only.
            }
        }
    }

    @Override
    protected void updatePreview() {
        ItemStack target = inventory.getItem(SLOT_INPUT);
        ItemStack sacrifice = inventory.getItem(SLOT_SACRIFICE);

        // Curse-removal mode: a Sello Purificador in the sacrifice slot strips curses
        // off the target instead of the normal enchantment merge.
        if (isPurifierSacrifice(sacrifice)) {
            showPurifierPreview(target);
            return;
        }

        // Block merging two different items that only share a base material.
        if (target != null && !target.getType().isAir()
                && sacrifice != null && !sacrifice.getType().isAir()
                && !isMergeableSacrifice(target, sacrifice)) {
            showIncompatibleItems();
            return;
        }

        AnvilResult result = AnvilLogic.calculateResult(target, sacrifice, config,
                gateFor(target), this::ecoConflicts);

        if (result.isValid()) {
            showValidResult(result, sacrifice);
        } else if (!result.conflicts().isEmpty()) {
            showConflictResult(result);
        } else {
            showEmptyResult();
        }
    }

    // ── Forging action ────────────────────────────────────────────────────

    private void attemptForge(@NotNull Player player) {
        if (isOnCooldown()) {
            player.sendActionBar(msg.parsed("anvil.cooldown"));
            return;
        }

        ItemStack target = inventory.getItem(SLOT_INPUT);
        ItemStack sacrifice = inventory.getItem(SLOT_SACRIFICE);

        // Curse-removal mode takes priority over the normal merge.
        if (isPurifierSacrifice(sacrifice)) {
            attemptPurify(player, target, sacrifice);
            return;
        }

        // Re-validate identity (the forge button is clickable regardless of preview state).
        if (target != null && !target.getType().isAir()
                && sacrifice != null && !sacrifice.getType().isAir()
                && !isMergeableSacrifice(target, sacrifice)) {
            config.getErrorSound().play(player);
            return;
        }

        AnvilResult result = AnvilLogic.calculateResult(target, sacrifice, config,
                gateFor(target), this::ecoConflicts);

        if (!result.isValid() || result.resultItem() == null) {
            config.getErrorSound().play(player);
            return;
        }

        Cost cost = plugin.getCostService().effectiveCost(player,
                AnvilLogic.determineCost(sacrifice, result, config));

        if (!plugin.getCostService().deduct(player, cost)) {
            config.getErrorSound().play(player);
            sendInsufficientFundsMessage(player, cost);
            return;
        }

        applyCooldown();

        ItemStack forgedItem = result.resultItem();

        if (config.isChainForgingEnabled()) {
            inventory.setItem(SLOT_INPUT, forgedItem);
            inventory.setItem(SLOT_SACRIFICE, null);
        } else {
            inventory.setItem(SLOT_INPUT, null);
            inventory.setItem(SLOT_SACRIFICE, null);

            if (player.getItemOnCursor().getType().isAir()) {
                player.setItemOnCursor(forgedItem);
            } else {
                Map<Integer, ItemStack> overflow = player.getInventory().addItem(forgedItem);
                overflow.values().forEach(leftover ->
                        player.getWorld().dropItemNaturally(player.getLocation(), leftover));
            }
        }

        config.getAnvilSuccessSound().play(player);
        player.sendActionBar(msg.parsed("anvil.forge-success",
                Map.of("{cost}", cost.displayText())));

        final AuditLog audit = plugin.getAuditLog();
        audit.record(player, "FORGE", "", cost,
                audit.snap("result", forgedItem),
                audit.snap("sacrifice", sacrifice));

        updatePreview();
        persistInputItems();
    }

    private void sendInsufficientFundsMessage(@NotNull Player player, @NotNull Cost cost) {
        player.sendActionBar(msg.parsed(
                CostService.insufficientKey("anvil", cost.type()),
                Map.of("{cost}", cost.displayText())));
    }

    // ── Result-preview rendering (single source of status) ─────────────────

    private void showValidResult(@NotNull AnvilResult result, @NotNull ItemStack sacrifice) {
        Cost cost = plugin.getCostService().effectiveCost(player,
                AnvilLogic.determineCost(sacrifice, result, config));
        ItemStack resultItem = result.resultItem();
        if (resultItem == null) {
            return;
        }

        List<String> extra = new ArrayList<>();
        extra.add("");
        extra.add(msg.format("anvil.result-cost", Map.of("{cost}", cost.displayText())));

        if (!result.conflicts().isEmpty()) {
            extra.add("");
            extra.add(msg.raw("anvil.result-conflicts-header"));
            for (Enchantment conflict : result.conflicts()) {
                extra.add(msg.format("anvil.result-conflict-entry",
                        Map.of("{enchantment}", plugin.getEcoHook().displayNameOrFallback(conflict))));
            }
        }

        // Curses-travel warning: the player must see which curses the sacrifice
        // will stick onto the result BEFORE forging.
        if (config.isTransferCursesTravel()) {
            final List<Enchantment> riders = cursesOn(sacrifice);
            if (!riders.isEmpty()) {
                extra.add("");
                extra.add(msg.raw("anvil.curse-rider-header"));
                for (Enchantment curse : riders) {
                    extra.add(msg.format("anvil.purify-entry",
                            Map.of("{enchantment}", plugin.getEcoHook().displayNameOrFallback(curse))));
                }
            }
        }

        ItemStack preview = resultItem.clone();
        appendLore(preview, extra);
        inventory.setItem(SLOT_RESULT, preview);

        setForgeButton(true, cost.displayText());
    }

    private void showConflictResult(@NotNull AnvilResult result) {
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(msg.raw("anvil.incompatible-lore-header"));
        lore.add(msg.raw("anvil.incompatible-lore-subheader"));
        lore.add("");
        for (Enchantment conflict : result.conflicts()) {
            lore.add(msg.format("anvil.result-conflict-entry",
                    Map.of("{enchantment}", plugin.getEcoHook().displayNameOrFallback(conflict))));
        }

        inventory.setItem(SLOT_RESULT, new ItemBuilder(Material.BARRIER)
                .name(msg.raw("anvil.incompatible-name"))
                .lore(lore)
                .build());

        setForgeButton(false, null);
    }

    private void showEmptyResult() {
        inventory.setItem(SLOT_RESULT, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE)
                .name(msg.raw("anvil.empty-result-name"))
                .lore(msg.rawList("anvil.empty-result-lore"))
                .build());

        setForgeButton(false, null);
    }

    // ── Forge button ──────────────────────────────────────────────────────

    private void setForgeButton(boolean enabled, String costText) {
        if (enabled) {
            inventory.setItem(SLOT_FORGE, new ItemBuilder(Material.ANVIL)
                    .name(msg.raw("anvil.forge-ready-name"))
                    .lore(msg.formatList("anvil.forge-ready-lore",
                            Map.of("{cost}", costText != null ? costText : "")))
                    .glow()
                    .build());
        } else {
            inventory.setItem(SLOT_FORGE, new ItemBuilder(Material.GRAY_DYE)
                    .name(msg.raw("anvil.forge-disabled-name"))
                    .lore(msg.rawList("anvil.forge-disabled-lore"))
                    .build());
        }
    }

    // ── Curse removal (Sello Purificador) ─────────────────────────────────

    /**
     * Whether the sacrifice is a legal merge source for the target: an enchanted book
     * (applies to anything) OR an item of the <em>same identity</em> as the target
     * (same material + CustomModelData + MythicMobs id). This blocks merging two
     * different items that merely share a base material — e.g. a "diamond sword" with a
     * custom "concentrated diamond sword" — which vanilla never allowed.
     */
    private boolean isMergeableSacrifice(ItemStack target, ItemStack sacrifice) {
        if (sacrifice == null || sacrifice.getType().isAir()) {
            return false;
        }
        if (sacrifice.getType() == Material.ENCHANTED_BOOK) {
            return true;
        }
        if (target == null || target.getType().isAir()) {
            return false;
        }
        return sameIdentity(target, sacrifice);
    }

    /** Same base material + same CustomModelData + same MythicMobs id. */
    @SuppressWarnings("UnstableApiUsage")
    private boolean sameIdentity(@NotNull ItemStack a, @NotNull ItemStack b) {
        if (a.getType() != b.getType()) {
            return false;
        }
        if (!java.util.Objects.equals(
                a.getData(io.papermc.paper.datacomponent.DataComponentTypes.CUSTOM_MODEL_DATA),
                b.getData(io.papermc.paper.datacomponent.DataComponentTypes.CUSTOM_MODEL_DATA))) {
            return false;
        }
        return java.util.Objects.equals(plugin.getMythicMobsHook().getItemId(a),
                plugin.getMythicMobsHook().getItemId(b));
    }

    private void showIncompatibleItems() {
        inventory.setItem(SLOT_RESULT, new ItemBuilder(Material.BARRIER)
                .name(msg.raw("anvil.different-items-name"))
                .lore(msg.rawList("anvil.different-items-lore"))
                .build());
        setForgeButton(false, null);
    }

    /** @return whether the sacrifice item is a configured Sello Purificador. */
    private boolean isPurifierSacrifice(ItemStack sacrifice) {
        if (!config.isCurseRemovalEnabled() || sacrifice == null || sacrifice.getType().isAir()) {
            return false;
        }
        return config.isCurseRemovalSeal(plugin.getMythicMobsHook().getItemId(sacrifice));
    }

    /** The curse-type enchantments currently on an item. */
    private @NotNull List<Enchantment> cursesOn(ItemStack item) {
        final List<Enchantment> curses = new ArrayList<>();
        if (item == null || item.getType().isAir()) {
            return curses;
        }
        for (Enchantment ench : dev.scrulius.superenchanter.util.EnchantmentHelper
                .getEnchantments(item).keySet()) {
            if (plugin.getEcoHook().isCurse(ench)) {
                curses.add(ench);
            }
        }
        return curses;
    }

    /** Returns a copy of the item with every curse removed. */
    private @NotNull ItemStack stripCurses(@NotNull ItemStack item, @NotNull List<Enchantment> curses) {
        ItemStack result = item.clone();
        for (Enchantment curse : curses) {
            result = dev.scrulius.superenchanter.util.EnchantmentHelper.removeEnchantment(result, curse);
        }
        return result;
    }

    private void showPurifierPreview(ItemStack target) {
        final List<Enchantment> curses = cursesOn(target);
        if (curses.isEmpty()) {
            showEmptyResult(); // nothing to purify
            return;
        }
        ItemStack purified = stripCurses(target, curses);
        com.willfp.eco.core.display.Display.display(purified, player);
        ItemStack preview = purified.clone();

        List<String> extra = new ArrayList<>();
        extra.add("");
        extra.add(msg.raw("anvil.purify-header"));
        for (Enchantment curse : curses) {
            extra.add(msg.format("anvil.purify-entry",
                    Map.of("{enchantment}", plugin.getEcoHook().displayNameOrFallback(curse))));
        }
        appendLore(preview, extra);
        inventory.setItem(SLOT_RESULT, preview);
        setForgeButton(true, msg.raw("anvil.purify-free"));
    }

    private void attemptPurify(@NotNull Player player, ItemStack target, ItemStack sacrifice) {
        final List<Enchantment> curses = cursesOn(target);
        if (target == null || target.getType().isAir() || curses.isEmpty()) {
            config.getErrorSound().play(player);
            return;
        }
        applyCooldown();

        final ItemStack purified = stripCurses(target, curses);

        // Consume one Sello Purificador.
        final int left = sacrifice.getAmount() - 1;
        if (left <= 0) {
            inventory.setItem(SLOT_SACRIFICE, null);
        } else {
            sacrifice.setAmount(left);
            inventory.setItem(SLOT_SACRIFICE, sacrifice);
        }
        inventory.setItem(SLOT_INPUT, null);

        com.willfp.eco.core.display.Display.display(purified, player);
        if (player.getItemOnCursor().getType().isAir()) {
            player.setItemOnCursor(purified);
        } else {
            player.getInventory().addItem(purified).values().forEach(leftover ->
                    player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        }

        config.getAnvilSuccessSound().play(player);
        player.sendActionBar(msg.parsed("anvil.purify-success",
                Map.of("{count}", String.valueOf(curses.size()))));
        final AuditLog auditPurify = plugin.getAuditLog();
        auditPurify.record(player, "PURIFY", "curses=" + curses.size(), Cost.xp(0),
                auditPurify.snap("item", purified));

        updatePreview();
        persistInputItems();
    }

    // ── EcoEnchants gate ──────────────────────────────────────────────────

    /**
     * Builds (and caches) the {@link AnvilEnchantGate} for the current target by
     * running the same {@code EnchantingLogic.analyze} the enchanting table and
     * transfer menu use, so the anvil obeys EcoEnchants targets / conflicts /
     * required / type-limit / blacklist and per-enchant level caps — not just
     * vanilla rules. Rebuilt only when the target item changes.
     */
    @NotNull
    private AnvilEnchantGate gateFor(ItemStack target) {
        if (target == null || target.getType().isAir()) {
            gateItem = null;
            return AnvilEnchantGate.ALLOW_ALL;
        }
        if (gateItem != null && target.equals(gateItem)) {
            return cachedGate;
        }

        final Map<Enchantment, Integer> caps = new HashMap<>();
        for (AnalyzedEnchant a : EnchantingLogic.analyze(target, plugin)) {
            final int cap = switch (a.reason()) {
                // Allowed: cap at the (possibly overridden) max level.
                case NONE -> cappedMax(a.enchantment(), a.maxLevel());
                // Already maxed on the target: no further gain allowed.
                case MAXED -> a.currentLevel();
                // Conflict / missing-required / type-limit → rejected.
                default -> 0;
            };
            caps.put(a.enchantment(), cap);
        }

        gateItem = target.clone();
        // Enchants absent from the analysis don't target this item or are
        // blacklisted → rejected (0). Exception: with curses-travel ON, a cursed
        // sacrifice must carry its curses into the result — rejecting them here
        // would let the anvil silently launder a curse away (undoing the
        // grindstone's curse-rider rule).
        final boolean cursesTravel = config.isTransferCursesTravel();
        cachedGate = ench -> {
            final Integer cap = caps.get(ench);
            if (cap != null) {
                return cap;
            }
            if (cursesTravel && plugin.getEcoHook().isCurse(ench)) {
                return dev.scrulius.superenchanter.util.EnchantmentHelper.getMaxLevel(ench);
            }
            return 0;
        };
        return cachedGate;
    }

    /**
     * Pairwise EcoEnchants conflict test (declared {@code conflicts} sets or
     * {@code conflictsAll}) fed into the merge so two NEW enchantments arriving
     * from the same sacrifice can't slip past the per-target gate. Reads the
     * precomputed {@link dev.scrulius.superenchanter.gui.enchanting.EnchantmentIndex},
     * so it's O(1) per pair.
     */
    private boolean ecoConflicts(@NotNull Enchantment a, @NotNull Enchantment b) {
        final var index = plugin.getEnchantmentIndex();
        final var ea = index.get(a);
        final var eb = index.get(b);
        return (ea != null && (ea.conflictsAll() || ea.conflicts().contains(b)))
                || (eb != null && (eb.conflictsAll() || eb.conflicts().contains(a)));
    }

    /** The per-enchant max level, applying any configured max-level override. */
    private int cappedMax(@NotNull Enchantment ench, int rawMax) {
        final PluginConfig.EnchantmentOverride ov = config.getEnchantmentOverride(ench);
        return ov.maxLevel() != null ? Math.max(1, Math.min(rawMax, ov.maxLevel())) : rawMax;
    }

    /** Appends MiniMessage lore lines to an existing (already-built) item. */
    private void appendLore(@NotNull ItemStack item, @NotNull List<String> extra) {
        final ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        final List<Component> lore = new ArrayList<>();
        final List<Component> existing = meta.lore();
        if (existing != null) {
            lore.addAll(existing);
        }
        for (String line : extra) {
            lore.add(MiniMessageUtil.parse(line));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
    }
}
