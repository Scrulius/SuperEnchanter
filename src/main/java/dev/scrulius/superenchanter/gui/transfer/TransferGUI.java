/* SuperEnchanter - Author: Scrulius (GitHub) */
package dev.scrulius.superenchanter.gui.transfer;

import dev.scrulius.superenchanter.SuperEnchanterPlugin;
import dev.scrulius.superenchanter.economy.Cost;
import dev.scrulius.superenchanter.economy.CostService;
import dev.scrulius.superenchanter.gui.AbstractCustomGUI;
import dev.scrulius.superenchanter.gui.enchanting.EnchantingLogic;
import dev.scrulius.superenchanter.gui.transfer.TransferLogic.TransferOffer;
import dev.scrulius.superenchanter.util.AuditLog;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Enchantment-transfer GUI (a repurposed grindstone). Kept deliberately simple:
 * two labelled inputs on the sides, the donor's enchantments in a single row, and
 * one clear Transfer button in the middle.
 * <pre>
 * Row0:  ·  ·  ·  ·  ·  ·  ·  ·  ·
 * Row1:  ·  Dl ·  ·  ·  ·  ·  Tl ·     Dl/Tl = donor/target labels
 * Row2:  ·  DO ·  ·  ·  ·  ·  TA ·     DO=donor input · TA=target input
 * Row3:  ·  E  E  E  E  E  E  E  ·     E = donor's transferable enchantments
 * Row4:  ·  ·  ·  ◀  X  ▶  ·  ·  ·     X = transfer button · ◀▶ = pages
 * Row5:  I  ·  ·  ·  ·  ·  ·  ·  C     I=info · C=close
 * </pre>
 * Clicking an enchantment <em>toggles</em> its selection (it glows); the dedicated
 * Transfer button performs the destructive move on <b>every selected</b> enchantment at
 * once, so a misclick never consumes the donor. The donor is consumed entirely in a
 * single operation — you get ONE shot to grab whichever enchants you want; everything
 * left on the donor is destroyed with it.
 */
public final class TransferGUI extends AbstractCustomGUI {

    // ── Slot constants ────────────────────────────────────────────────────
    private static final int SLOT_DONOR_LABEL = 10;
    private static final int SLOT_DONOR = 19;
    private static final int SLOT_TARGET_LABEL = 16;
    private static final int SLOT_TARGET = 25;
    private static final int SLOT_PREV_PAGE = 39;
    private static final int SLOT_TRANSFER = 40;
    private static final int SLOT_NEXT_PAGE = 41;
    private static final int SLOT_INFO = 45;
    private static final int SLOT_CLOSE = 53;

    /** Enchant list: one row of 7, paginated past that. */
    private static final List<Integer> OFFER_SLOTS = List.of(28, 29, 30, 31, 32, 33, 34);
    private static final Set<Integer> OFFER_SLOTS_SET = Set.copyOf(OFFER_SLOTS);
    private static final int PAGE_SIZE = OFFER_SLOTS.size();
    /** Centre of the row — shows the "place items" hint when the list is empty. */
    private static final int SLOT_LIST_HINT = 31;

    private static final Set<Integer> INPUT_SLOTS = Set.of(SLOT_DONOR, SLOT_TARGET);

    /** Clean dark frame so the coloured zones and books stand out. */
    private static final ItemStack FRAME = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE)
            .name(" ").build();

    // ── State ───────────────────────────────────────────────────────────────
    private final Block grindstoneBlock;
    private List<TransferOffer> offers = new ArrayList<>();
    /** Enchantments the player has selected to transfer/extract in one operation. */
    private final Set<Enchantment> selected = new LinkedHashSet<>();
    private int currentPage = 0;
    /** True when there is no target → the action extracts the enchant to a book. */
    private boolean extractMode = false;

    public TransferGUI(@NotNull SuperEnchanterPlugin plugin, @NotNull Player player,
                       @NotNull Block grindstoneBlock) {
        super(plugin, player, plugin.getMessages().parsed("transfer.gui-title"));
        this.grindstoneBlock = grindstoneBlock;
        fillDecoration();
    }

    @Override
    protected @NotNull Set<Integer> getInputSlots() {
        return INPUT_SLOTS;
    }

    @Override
    protected void fillDecoration() {
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, FRAME);
        }

        inventory.setItem(SLOT_DONOR_LABEL, new ItemBuilder(Material.RED_STAINED_GLASS_PANE)
                .name(msg.raw("transfer.donor-name"))
                .lore(msg.rawList("transfer.donor-lore")).build());
        inventory.setItem(SLOT_TARGET_LABEL, new ItemBuilder(Material.LIME_STAINED_GLASS_PANE)
                .name(msg.raw("transfer.target-name"))
                .lore(msg.rawList("transfer.target-lore")).build());

        inventory.setItem(SLOT_INFO, new ItemBuilder(Material.PAPER)
                .name(msg.raw("transfer.info-name"))
                .lore(msg.rawList("transfer.info-lore")).build());
        inventory.setItem(SLOT_CLOSE, new ItemBuilder(Material.BARRIER)
                .name(msg.raw("transfer.close-name"))
                .lore(msg.raw("transfer.close-lore")).build());

        inventory.setItem(SLOT_DONOR, null);
        inventory.setItem(SLOT_TARGET, null);

        renderOffers();
        renderActionButton();
    }

    @Override
    protected void updatePreview() {
        final ItemStack donor = inventory.getItem(SLOT_DONOR);
        final ItemStack target = inventory.getItem(SLOT_TARGET);
        final boolean targetIsBook = target != null && target.getType() == Material.BOOK;
        final boolean hasTarget = target != null && !target.getType().isAir();

        if (targetIsBook && plugin.getPluginConfig().isTransferAllowExtract()) {
            // A plain BOOK in the target slot → extract mode (book is consumed).
            offers = TransferLogic.computeExtractOffers(donor, plugin);
            extractMode = true;
        } else if (hasTarget) {
            // Another item present → transfer mode (validated against the target).
            offers = TransferLogic.computeOffers(donor, target, plugin);
            extractMode = false;
        } else {
            // Empty target → nothing to do (place a target item, or a book to extract).
            offers = List.of();
            extractMode = false;
        }
        currentPage = 0;

        // Drop any selections no longer offered as transferable.
        selected.removeIf(ench -> offers.stream()
                .noneMatch(o -> o.enchantment().equals(ench) && o.isTransferable()));

        renderOffers();
        renderActionButton();
    }

    @Override
    protected void onSlotClick(@NotNull Player player, int slot, @NotNull InventoryClickEvent event) {
        if (slot == SLOT_CLOSE) {
            Bukkit.getScheduler().runTask(plugin, () -> player.closeInventory());
            return;
        }
        if (slot == SLOT_TRANSFER) {
            if (extractMode) {
                attemptExtract(player);
            } else {
                attemptTransfer(player);
            }
            return;
        }
        if (slot == SLOT_PREV_PAGE) {
            if (currentPage > 0) {
                currentPage--;
                plugin.getPluginConfig().getButtonClickSound().play(player);
                renderOffers();
            }
            return;
        }
        if (slot == SLOT_NEXT_PAGE) {
            if (hasNextPage()) {
                currentPage++;
                plugin.getPluginConfig().getButtonClickSound().play(player);
                renderOffers();
            }
            return;
        }
        if (OFFER_SLOTS_SET.contains(slot)) {
            handleOfferClick(player, slot);
        }
    }

    // ── Selection ─────────────────────────────────────────────────────────

    private void handleOfferClick(@NotNull Player player, int slot) {
        final int index = offerIndexOf(slot);
        if (index < 0 || index >= offers.size()) {
            return;
        }
        final TransferOffer offer = offers.get(index);
        if (!offer.isTransferable()) {
            plugin.getPluginConfig().getErrorSound().play(player);
            player.sendActionBar(msg.parsed("transfer.cannot-select"));
            return;
        }
        // Toggle this enchantment in/out of the multi-selection.
        if (!selected.remove(offer.enchantment())) {
            selected.add(offer.enchantment());
        }
        plugin.getPluginConfig().getButtonClickSound().play(player);
        renderOffers();
        renderActionButton();
    }

    // ── Transfer action ───────────────────────────────────────────────────

    private void attemptTransfer(@NotNull Player player) {
        if (isOnCooldown()) {
            player.sendActionBar(msg.parsed("transfer.cooldown"));
            return;
        }
        final List<TransferOffer> sel = selectedOffers();
        if (sel.isEmpty()) {
            plugin.getPluginConfig().getErrorSound().play(player);
            player.sendActionBar(msg.parsed("transfer.select-first"));
            return;
        }

        final ItemStack donor = inventory.getItem(SLOT_DONOR);
        final ItemStack target = inventory.getItem(SLOT_TARGET);
        if (donor == null || donor.getType().isAir() || target == null || target.getType().isAir()) {
            return;
        }

        final Cost cost = plugin.getCostService().effectiveCost(player, totalCost(sel));
        if (!plugin.getCostService().canAfford(player, cost)) {
            plugin.getPluginConfig().getErrorSound().play(player);
            player.sendActionBar(msg.parsed(
                    CostService.insufficientKey("transfer", cost.type()),
                    Map.of("{cost}", cost.displayText(),
                            "{balance}", plugin.getCostService().balanceText(player, cost.type()))));
            return;
        }
        if (!plugin.getCostService().deduct(player, cost)) {
            plugin.getPluginConfig().getErrorSound().play(player);
            return;
        }

        applyCooldown();

        // Move EVERY selected enchant onto the target; the donor is consumed entirely.
        ItemStack enchanted = target;
        final StringBuilder moved = new StringBuilder();
        for (TransferOffer o : sel) {
            enchanted = TransferLogic.applyTransfer(enchanted, o.enchantment(), o.resultLevel());
            moved.append(' ').append(o.enchantment().getKey().getKey()).append('=').append(o.resultLevel());
        }
        com.willfp.eco.core.display.Display.display(enchanted, player);
        inventory.setItem(SLOT_TARGET, enchanted);
        inventory.setItem(SLOT_DONOR, null);
        selected.clear();

        plugin.getPluginConfig().getTransferSuccessSound().play(player);
        plugin.getPluginConfig().getEnchantSuccessParticle().spawn(player.getWorld(),
                grindstoneBlock.getLocation().add(0.5, 1.0, 0.5));
        player.sendActionBar(msg.parsed("transfer.success",
                Map.of("{count}", String.valueOf(sel.size()))));

        final AuditLog audit = plugin.getAuditLog();
        audit.record(player, "TRANSFER", moved.toString().trim(), cost,
                audit.snap("donor", donor),
                audit.snap("result", enchanted));

        updatePreview();
        persistInputItems();
    }

    private void attemptExtract(@NotNull Player player) {
        if (isOnCooldown()) {
            player.sendActionBar(msg.parsed("transfer.cooldown"));
            return;
        }
        final List<TransferOffer> sel = selectedOffers();
        if (sel.isEmpty()) {
            plugin.getPluginConfig().getErrorSound().play(player);
            player.sendActionBar(msg.parsed("transfer.select-first"));
            return;
        }

        final ItemStack donor = inventory.getItem(SLOT_DONOR);
        final ItemStack bookStack = inventory.getItem(SLOT_TARGET);
        // Extraction requires a donor and a plain BOOK in the target slot (consumed).
        if (donor == null || donor.getType().isAir()
                || bookStack == null || bookStack.getType() != Material.BOOK) {
            return;
        }

        final Cost cost = plugin.getCostService().effectiveCost(player, totalCost(sel));
        if (!plugin.getCostService().canAfford(player, cost)) {
            plugin.getPluginConfig().getErrorSound().play(player);
            player.sendActionBar(msg.parsed(
                    CostService.insufficientKey("transfer", cost.type()),
                    Map.of("{cost}", cost.displayText(),
                            "{balance}", plugin.getCostService().balanceText(player, cost.type()))));
            return;
        }
        if (!plugin.getCostService().deduct(player, cost)) {
            plugin.getPluginConfig().getErrorSound().play(player);
            return;
        }

        applyCooldown();

        // Build ONE enchanted book holding every selected enchant; consume one plain book
        // and the donor entirely.
        final ItemStack book = TransferLogic.extractToBook(sel);
        com.willfp.eco.core.display.Display.display(book, player);
        final StringBuilder pulled = new StringBuilder();
        for (TransferOffer o : sel) {
            pulled.append(' ').append(o.enchantment().getKey().getKey()).append('=').append(o.resultLevel());
        }
        inventory.setItem(SLOT_DONOR, null);
        if (bookStack.getAmount() <= 1) {
            inventory.setItem(SLOT_TARGET, book);          // the slot's book becomes the enchanted book
        } else {
            bookStack.setAmount(bookStack.getAmount() - 1); // keep the rest of the stack
            inventory.setItem(SLOT_TARGET, bookStack);
            giveOrDrop(player, book);                       // the enchanted book goes to the player
        }
        selected.clear();

        plugin.getPluginConfig().getExtractSuccessSound().play(player);
        plugin.getPluginConfig().getEnchantSuccessParticle().spawn(player.getWorld(),
                grindstoneBlock.getLocation().add(0.5, 1.0, 0.5));
        player.sendActionBar(msg.parsed("transfer.extract-success",
                Map.of("{count}", String.valueOf(sel.size()))));

        final AuditLog auditEx = plugin.getAuditLog();
        auditEx.record(player, "EXTRACT", pulled.toString().trim(), cost,
                auditEx.snap("donor", donor),
                auditEx.snap("book", book));

        updatePreview();
        persistInputItems();
    }

    /** Puts an item on the cursor, or in the inventory / on the floor if the cursor is busy. */
    private void giveOrDrop(@NotNull Player player, @NotNull ItemStack item) {
        if (player.getItemOnCursor().getType().isAir()) {
            player.setItemOnCursor(item);
        } else {
            player.getInventory().addItem(item).values().forEach(leftover ->
                    player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        }
    }

    /** The currently selected, still-transferable offers (in offer order). */
    private List<TransferOffer> selectedOffers() {
        final List<TransferOffer> out = new ArrayList<>();
        for (TransferOffer o : offers) {
            if (o.isTransferable() && selected.contains(o.enchantment())) {
                out.add(o);
            }
        }
        return out;
    }

    /** Sum of the selected offers' costs (all share the transfer currency). */
    private Cost totalCost(@NotNull List<TransferOffer> sel) {
        int total = 0;
        for (TransferOffer o : sel) {
            total += o.cost().intAmount();
        }
        final var type = sel.isEmpty()
                ? plugin.getPluginConfig().getTransferCostType()
                : sel.get(0).cost().type();
        return new Cost(type, total);
    }

    // ── Rendering ─────────────────────────────────────────────────────────

    private int offerIndexOf(int slot) {
        final int slotIndex = OFFER_SLOTS.indexOf(slot);
        if (slotIndex < 0) {
            return -1;
        }
        return currentPage * PAGE_SIZE + slotIndex;
    }

    private boolean hasNextPage() {
        return (currentPage + 1) * PAGE_SIZE < offers.size();
    }

    private void renderOffers() {
        // Empty state: dark row with a single centred hint, no pagination.
        if (offers.isEmpty()) {
            for (int slot : OFFER_SLOTS) {
                inventory.setItem(slot, FRAME);
            }
            inventory.setItem(SLOT_LIST_HINT, new ItemBuilder(Material.ENCHANTED_BOOK)
                    .name(msg.raw("transfer.list-empty-name"))
                    .lore(msg.rawList("transfer.list-empty-lore")).build());
            inventory.setItem(SLOT_PREV_PAGE, FRAME);
            inventory.setItem(SLOT_NEXT_PAGE, FRAME);
            return;
        }

        final int start = currentPage * PAGE_SIZE;
        for (int i = 0; i < OFFER_SLOTS.size(); i++) {
            final int slot = OFFER_SLOTS.get(i);
            final int index = start + i;
            if (index < offers.size()) {
                final TransferOffer offer = offers.get(index);
                final boolean isSelected = offer.isTransferable() && selected.contains(offer.enchantment());
                inventory.setItem(slot,
                        TransferLogic.createOfferIcon(offer, isSelected, extractMode, player, plugin));
            } else {
                inventory.setItem(slot, FRAME);
            }
        }
        renderPaginationButtons();
    }

    private void renderPaginationButtons() {
        final int totalPages = Math.max(1, (int) Math.ceil((double) offers.size() / PAGE_SIZE));

        if (currentPage > 0) {
            inventory.setItem(SLOT_PREV_PAGE, new ItemBuilder(Material.ARROW)
                    .name(msg.raw("enchanting.prev-page-name"))
                    .lore(msg.format("enchanting.prev-page-lore",
                            Map.of("{current}", String.valueOf(currentPage),
                                    "{total}", String.valueOf(totalPages))))
                    .build());
        } else {
            inventory.setItem(SLOT_PREV_PAGE, FRAME);
        }

        if (hasNextPage()) {
            inventory.setItem(SLOT_NEXT_PAGE, new ItemBuilder(Material.ARROW)
                    .name(msg.raw("enchanting.next-page-name"))
                    .lore(msg.format("enchanting.next-page-lore",
                            Map.of("{current}", String.valueOf(currentPage + 2),
                                    "{total}", String.valueOf(totalPages))))
                    .build());
        } else {
            inventory.setItem(SLOT_NEXT_PAGE, FRAME);
        }
    }

    private void renderActionButton() {
        final List<TransferOffer> sel = selectedOffers();
        final Cost cost = sel.isEmpty() ? null
                : plugin.getCostService().effectiveCost(player, totalCost(sel));
        if (!sel.isEmpty() && plugin.getCostService().canAfford(player, cost)) {
            final String nameKey = extractMode ? "transfer.extract-button-ready-name"
                    : "transfer.button-ready-name";
            final String loreKey = extractMode ? "transfer.extract-button-ready-lore"
                    : "transfer.button-ready-lore";
            inventory.setItem(SLOT_TRANSFER,
                    new ItemBuilder(extractMode ? Material.WRITABLE_BOOK : Material.ANVIL)
                            .name(msg.raw(nameKey))
                            .lore(msg.formatList(loreKey,
                                    Map.of("{count}", String.valueOf(sel.size()),
                                            "{cost}", cost.displayText())))
                            .glow()
                            .build());
        } else {
            inventory.setItem(SLOT_TRANSFER, new ItemBuilder(Material.GRAY_DYE)
                    .name(msg.raw("transfer.button-disabled-name"))
                    .lore(msg.rawList("transfer.button-disabled-lore"))
                    .build());
        }
    }
}
