/* SuperEnchanter - Author: Scrulius (GitHub) */
package dev.scrulius.superenchanter.gui;

import dev.scrulius.superenchanter.SuperEnchanterPlugin;
import dev.scrulius.superenchanter.config.MessagesConfig;
import dev.scrulius.superenchanter.util.PendingItemStore;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Abstract base class for all custom GUI menus in SuperEnchanter.
 * <p>
 * Implements {@link InventoryHolder} so that GUIs can be identified by checking
 * {@code event.getInventory().getHolder(false) instanceof AbstractCustomGUI}
 * rather than fragile title-string matching.
 * </p>
 * <p>
 * Provides comprehensive anti-dupe protection by:
 * <ul>
 *   <li>Cancelling all events first, then selectively re-applying only safe operations</li>
 *   <li>Blocking shift-click, number-key, double-click, drop, and offhand-swap interactions</li>
 *   <li>Cloning items before any slot manipulation</li>
 *   <li>Forcing {@code player.updateInventory()} after every interaction</li>
 *   <li>Integrating with {@link dev.scrulius.superenchanter.util.CooldownManager}</li>
 * </ul>
 */
public abstract class AbstractCustomGUI implements InventoryHolder {

    /** Thread-safe registry of all currently open custom GUIs, keyed by player UUID. */
    private static final Map<UUID, AbstractCustomGUI> ACTIVE_GUIS = new ConcurrentHashMap<>();

    protected final SuperEnchanterPlugin plugin;
    protected final Player player;
    protected final Inventory inventory;
    protected final MessagesConfig msg;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * Constructs a new custom GUI for the specified player.
     *
     * @param plugin the owning plugin instance
     * @param player the player who will view this GUI
     * @param title  the inventory title rendered as an Adventure {@link Component}
     */
    protected AbstractCustomGUI(@NotNull SuperEnchanterPlugin plugin,
                                 @NotNull Player player,
                                 @NotNull Component title) {
        this.plugin = plugin;
        this.player = player;
        this.msg = plugin.getMessages();
        this.inventory = Bukkit.createInventory(this, 54, title);
    }

    // ────────────────────────────────────────────────────────────
    //  Abstract Template Methods
    // ────────────────────────────────────────────────────────────

    /**
     * Returns the set of slot indices where players are allowed to place or take items.
     *
     * @return an unmodifiable set of valid input slot indices (0-53)
     */
    protected abstract @NotNull Set<Integer> getInputSlots();

    /**
     * Called when the player clicks a non-input slot in the top inventory.
     * Subclasses use this to handle action buttons, result slots, navigation, etc.
     *
     * @param player the clicking player
     * @param slot   the raw slot index clicked
     * @param event  the original click event (already cancelled)
     */
    protected abstract void onSlotClick(@NotNull Player player, int slot,
                                         @NotNull InventoryClickEvent event);

    /**
     * Updates the visual preview or result display based on the current input slot contents.
     * Called automatically after any input slot modification.
     */
    protected abstract void updatePreview();

    /**
     * Fills the inventory with decorative items (glass panes, borders, labels).
     * Called once during construction.
     */
    protected abstract void fillDecoration();

    // ────────────────────────────────────────────────────────────
    //  Concrete Methods
    // ────────────────────────────────────────────────────────────

    /**
     * Opens this GUI for the player and plays the open sound.
     */
    public void open() {
        ACTIVE_GUIS.put(player.getUniqueId(), this);
        player.openInventory(inventory);
        plugin.getPluginConfig().getGuiOpenSound().play(player);
    }

    /**
     * Core click handler with comprehensive anti-dupe protection.
     * <p>
     * The strategy is "deny by default, then re-allow only the vanilla-safe
     * interactions":
     * <ul>
     *   <li>Every click starts cancelled.</li>
     *   <li>Dangerous click types (shift, number key, double-click, drop,
     *       offhand swap, creative) are <b>always</b> blocked, everywhere.</li>
     *   <li>Plain left/right clicks in the player's own inventory are re-allowed.</li>
     *   <li>Plain left/right clicks on a designated input slot are re-allowed so
     *       that <b>vanilla</b> performs the pickup/place/swap. We never move
     *       items by hand — manual cursor manipulation is the classic source of
     *       ghost-item and dupe desyncs, so we let the server do it correctly.</li>
     *   <li>Every other top-inventory slot is a plugin-controlled button and is
     *       routed to {@link #onSlotClick} while staying cancelled.</li>
     * </ul>
     *
     * @param event the inventory click event
     */
    public void handleClick(@NotNull InventoryClickEvent event) {
        // Deny by default — we re-allow only vanilla-safe interactions below.
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player clicker)) {
            return;
        }

        ClickType clickType = event.getClick();

        // Block all dangerous click types globally (shift, number key, drop, ...).
        if (isBlockedClickType(clickType)) {
            scheduleInventoryUpdate(clicker);
            return;
        }

        int rawSlot = event.getRawSlot();
        int topSize = event.getView().getTopInventory().getSize();

        // Click in the bottom (player) inventory — allow normal interaction.
        if (rawSlot >= topSize) {
            event.setCancelled(false);
            return;
        }

        // Click on a designated input slot — let vanilla move the item.
        if (getInputSlots().contains(rawSlot)) {
            if (clickType == ClickType.LEFT || clickType == ClickType.RIGHT) {
                event.setCancelled(false);
                // Once vanilla has applied the move: refresh the preview and
                // snapshot the input slots for crash recovery.
                Bukkit.getScheduler().runTask(plugin, () -> {
                    updatePreview();
                    persistInputItems();
                });
            } else {
                scheduleInventoryUpdate(clicker);
            }
            return;
        }

        // Any other top slot is a plugin-controlled button/decoration.
        onSlotClick(clicker, rawSlot, event);
        scheduleInventoryUpdate(clicker);
    }

    /**
     * Handles drag events — cancels any drag that touches the top inventory.
     *
     * @param event the inventory drag event
     */
    public void handleDrag(@NotNull InventoryDragEvent event) {
        int topSize = event.getView().getTopInventory().getSize();
        for (int slot : event.getRawSlots()) {
            if (slot < topSize) {
                event.setCancelled(true);
                if (event.getWhoClicked() instanceof Player p) {
                    scheduleInventoryUpdate(p);
                }
                return;
            }
        }
    }

    /**
     * Handles inventory close — returns all items from input slots to the player
     * and unregisters from the active GUI map.
     */
    public void handleClose() {
        // Guard against double-close (InventoryCloseEvent + PlayerQuitEvent)
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        returnItems(player);
        // Items are now back with the player — drop the crash-recovery snapshot.
        PendingItemStore store = plugin.getPendingItemStore();
        if (store != null) {
            store.remove(player.getUniqueId());
        }
        ACTIVE_GUIS.remove(player.getUniqueId());
        plugin.getPluginConfig().getGuiCloseSound().play(player);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    // ────────────────────────────────────────────────────────────
    //  Protected Utilities
    // ────────────────────────────────────────────────────────────

    /**
     * Returns all items in the input slots back to the player. Items that
     * don't fit in the player's inventory are dropped naturally at their location.
     *
     * @param player the player to return items to
     */
    protected void returnItems(@NotNull Player player) {
        for (int slot : getInputSlots()) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && !item.getType().isAir()) {
                inventory.setItem(slot, null);
                Map<Integer, ItemStack> overflow = player.getInventory().addItem(item.clone());
                overflow.values().forEach(remaining ->
                        player.getWorld().dropItemNaturally(player.getLocation(), remaining));
            }
        }
    }

    /**
     * Checks whether the player is currently on the transaction cooldown.
     *
     * @return {@code true} if the player must wait before the next transaction
     */
    protected boolean isOnCooldown() {
        return plugin.getCooldownManager().isOnCooldown(player);
    }

    /**
     * Sets the transaction cooldown for the player.
     */
    protected void applyCooldown() {
        plugin.getCooldownManager().setCooldown(player);
    }

    /**
     * Snapshots the current contents of the input slots to durable storage so a
     * hard server crash cannot lose them. No-op when crash persistence is off.
     */
    protected void persistInputItems() {
        PendingItemStore store = plugin.getPendingItemStore();
        if (store == null) {
            return;
        }
        List<ItemStack> items = new ArrayList<>();
        for (int slot : getInputSlots()) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && !item.getType().isAir()) {
                items.add(item.clone());
            }
        }
        store.put(player.getUniqueId(), items);
    }

    // ────────────────────────────────────────────────────────────
    //  Static Registry Methods
    // ────────────────────────────────────────────────────────────

    /**
     * Returns the active custom GUI for the given player, if any.
     *
     * @param player the player to look up
     * @return the active GUI, or {@code null} if none is open
     */
    public static @Nullable AbstractCustomGUI getActiveGUI(@NotNull Player player) {
        return ACTIVE_GUIS.get(player.getUniqueId());
    }

    /**
     * Checks whether the given player currently has an active custom GUI open.
     *
     * @param player the player to check
     * @return {@code true} if the player has an open custom GUI
     */
    public static boolean hasActiveGUI(@NotNull Player player) {
        return ACTIVE_GUIS.containsKey(player.getUniqueId());
    }

    /**
     * Closes all active custom GUIs. Typically called during plugin shutdown
     * to return all items to players.
     */
    public static void closeAll() {
        // Copy to avoid ConcurrentModificationException — handleClose removes entries
        for (AbstractCustomGUI gui : ACTIVE_GUIS.values().toArray(new AbstractCustomGUI[0])) {
            try {
                gui.handleClose();
                gui.player.closeInventory();
            } catch (Exception e) {
                Bukkit.getLogger().warning("[SuperEnchanter] Error closing GUI for " +
                        gui.player.getName() + ": " + e.getMessage());
            }
        }
        ACTIVE_GUIS.clear();
    }

    // ────────────────────────────────────────────────────────────
    //  Private Helpers
    // ────────────────────────────────────────────────────────────

    /**
     * Checks if the given click type is one of the dangerous types that must
     * always be blocked in custom GUIs.
     */
    private boolean isBlockedClickType(@NotNull ClickType clickType) {
        return switch (clickType) {
            case SHIFT_LEFT, SHIFT_RIGHT, DOUBLE_CLICK, DROP, CONTROL_DROP,
                 NUMBER_KEY, SWAP_OFFHAND, CREATIVE -> true;
            default -> false;
        };
    }

    /**
     * Schedules a {@code player.updateInventory()} call on the next server tick
     * to flush any ghost items caused by cancelled events.
     */
    private void scheduleInventoryUpdate(@NotNull Player player) {
        Bukkit.getScheduler().runTask(plugin, player::updateInventory);
    }
}
