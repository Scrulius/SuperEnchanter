/* SuperEnchanter - Author: Scrulius (GitHub) */
package dev.scrulius.superenchanter.listener;

import dev.scrulius.superenchanter.SuperEnchanterPlugin;
import dev.scrulius.superenchanter.gui.AbstractCustomGUI;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Global listener that delegates all inventory interaction events to the
 * appropriate {@link AbstractCustomGUI} instance.
 * <p>
 * Identification is performed via {@link org.bukkit.inventory.InventoryHolder}
 * pattern matching — never by title string comparison.
 * </p>
 */
public final class GUIProtectionListener implements Listener {

    private final SuperEnchanterPlugin plugin;

    /**
     * @param plugin the owning plugin instance
     */
    public GUIProtectionListener(@NotNull SuperEnchanterPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Intercepts all inventory clicks and delegates to the custom GUI handler
     * when the clicked inventory belongs to a {@link AbstractCustomGUI}.
     *
     * @param event the click event
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        if (event.getInventory().getHolder(false) instanceof AbstractCustomGUI gui) {
            gui.handleClick(event);
        }
    }

    /**
     * Intercepts all inventory drag events and delegates to the custom GUI handler
     * when the target inventory belongs to a {@link AbstractCustomGUI}.
     *
     * @param event the drag event
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryDrag(@NotNull InventoryDragEvent event) {
        if (event.getInventory().getHolder(false) instanceof AbstractCustomGUI gui) {
            gui.handleDrag(event);
        }
    }

    /**
     * Handles inventory close to return items and clean up the GUI session.
     *
     * @param event the close event
     */
    @EventHandler
    public void onInventoryClose(@NotNull InventoryCloseEvent event) {
        if (event.getInventory().getHolder(false) instanceof AbstractCustomGUI gui) {
            gui.handleClose();
        }
    }

    /**
     * Ensures items are returned when a player disconnects with a custom GUI open.
     *
     * @param event the quit event
     */
    @EventHandler
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (AbstractCustomGUI.hasActiveGUI(player)) {
            AbstractCustomGUI gui = AbstractCustomGUI.getActiveGUI(player);
            if (gui != null) {
                gui.handleClose();
            }
        }
    }

    /**
     * Restores any items that were left in a custom GUI when the server crashed,
     * handing them back a tick after the player has fully joined.
     *
     * @param event the join event
     */
    @EventHandler
    public void onPlayerJoin(@NotNull PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> plugin.restorePendingItems(player));
    }
}
