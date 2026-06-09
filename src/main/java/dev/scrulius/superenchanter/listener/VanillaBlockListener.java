/* SuperEnchanter - Author: Scrulius (GitHub) */
package dev.scrulius.superenchanter.listener;

import dev.scrulius.superenchanter.SuperEnchanterPlugin;
import dev.scrulius.superenchanter.gui.AbstractCustomGUI;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.jetbrains.annotations.NotNull;

/**
 * Failsafe listener that prevents vanilla Anvil and Enchanting Table
 * inventories from opening.
 * <p>
 * Only cancels the event when the inventory is <b>not</b> owned by one
 * of our custom {@link AbstractCustomGUI} instances, avoiding interference
 * with the plugin's own GUI system.
 * </p>
 */
public final class VanillaBlockListener implements Listener {

    private final SuperEnchanterPlugin plugin;

    /**
     * @param plugin the owning plugin instance
     */
    public VanillaBlockListener(@NotNull SuperEnchanterPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Cancels vanilla anvil and enchanting table inventory open events.
     * Our custom GUIs bypass this check because their holder is an
     * {@link AbstractCustomGUI} instance, not a vanilla block holder.
     *
     * @param event the inventory open event
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryOpen(@NotNull InventoryOpenEvent event) {
        // If the holder is one of our custom GUIs, allow it through
        if (event.getInventory().getHolder(false) instanceof AbstractCustomGUI) {
            return;
        }

        InventoryType type = event.getInventory().getType();
        if (type == InventoryType.ANVIL || type == InventoryType.ENCHANTING) {
            event.setCancelled(true);
        } else if (type == InventoryType.GRINDSTONE && plugin.getPluginConfig().isTransferEnabled()) {
            // Only intercept the vanilla grindstone when we're repurposing it.
            event.setCancelled(true);
        }
    }

    /**
     * Belt-and-suspenders: cancels the vanilla enchanting-table preview so that even
     * if some other plugin force-opened a vanilla enchant inventory, it shows nothing.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareEnchant(@NotNull PrepareItemEnchantEvent event) {
        event.setCancelled(true);
    }

    /**
     * Belt-and-suspenders: hard-cancels any vanilla enchant that somehow gets through.
     * Our custom enchanting never fires this event, so this only ever blocks vanilla.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEnchantItem(@NotNull EnchantItemEvent event) {
        event.setCancelled(true);
    }
}
