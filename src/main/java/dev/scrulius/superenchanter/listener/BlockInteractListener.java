/* SuperEnchanter - Author: Scrulius (GitHub) */
package dev.scrulius.superenchanter.listener;

import dev.scrulius.superenchanter.SuperEnchanterPlugin;
import dev.scrulius.superenchanter.gui.AbstractCustomGUI;
import dev.scrulius.superenchanter.gui.anvil.AnvilGUI;
import dev.scrulius.superenchanter.gui.enchanting.EnchantingGUI;
import dev.scrulius.superenchanter.gui.transfer.TransferGUI;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Intercepts right-click interactions on Anvil and Enchanting Table blocks,
 * replacing the vanilla inventory with the appropriate custom GUI.
 */
public final class BlockInteractListener implements Listener {

    private final SuperEnchanterPlugin plugin;

    /**
     * @param plugin the owning plugin instance
     */
    public BlockInteractListener(@NotNull SuperEnchanterPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Handles player right-click on anvil or enchanting table blocks.
     * Cancels the vanilla interaction and opens the custom GUI instead.
     *
     * @param event the player interact event
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(@NotNull PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        if (event.useInteractedBlock() == Event.Result.DENY) {
            return;
        }

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) {
            return;
        }

        Material material = clickedBlock.getType();
        Player player = event.getPlayer();

        if (isAnvil(material)) {
            handleAnvilInteract(event, player);
        } else if (material == Material.ENCHANTING_TABLE) {
            handleEnchantingTableInteract(event, player, clickedBlock);
        } else if (material == Material.GRINDSTONE) {
            handleGrindstoneInteract(event, player, clickedBlock);
        }
    }

    /**
     * Opens the custom anvil GUI, blocking the vanilla interaction.
     */
    private void handleAnvilInteract(@NotNull PlayerInteractEvent event, @NotNull Player player) {
        event.setCancelled(true);
        event.setUseInteractedBlock(Event.Result.DENY);

        if (!player.hasPermission("superenchanter.anvil.use")) {
            player.sendActionBar(plugin.getMessages().parsed("general.no-permission"));
            return;
        }

        if (AbstractCustomGUI.hasActiveGUI(player)) {
            return;
        }

        AnvilGUI anvilGUI = new AnvilGUI(plugin, player);
        anvilGUI.open();
    }

    /**
     * Opens the custom enchanting table GUI, blocking the vanilla interaction.
     */
    private void handleEnchantingTableInteract(@NotNull PlayerInteractEvent event,
                                                @NotNull Player player,
                                                @NotNull Block block) {
        event.setCancelled(true);
        event.setUseInteractedBlock(Event.Result.DENY);

        if (!player.hasPermission("superenchanter.enchant.use")) {
            player.sendActionBar(plugin.getMessages().parsed("general.no-permission"));
            return;
        }

        if (AbstractCustomGUI.hasActiveGUI(player)) {
            return;
        }

        EnchantingGUI enchantingGUI = new EnchantingGUI(plugin, player, block);
        enchantingGUI.open();
    }

    /**
     * Opens the custom transfer GUI (repurposed grindstone), blocking the vanilla
     * interaction. No-op (vanilla stays blocked, nothing opens) when the feature
     * is disabled in config.
     */
    private void handleGrindstoneInteract(@NotNull PlayerInteractEvent event,
                                          @NotNull Player player,
                                          @NotNull Block block) {
        // Feature off → leave the grindstone fully vanilla (don't even cancel).
        if (!plugin.getPluginConfig().isTransferEnabled()) {
            return;
        }

        event.setCancelled(true);
        event.setUseInteractedBlock(Event.Result.DENY);

        if (!player.hasPermission("superenchanter.transfer.use")) {
            player.sendActionBar(plugin.getMessages().parsed("general.no-permission"));
            return;
        }
        if (AbstractCustomGUI.hasActiveGUI(player)) {
            return;
        }

        TransferGUI transferGUI = new TransferGUI(plugin, player, block);
        transferGUI.open();
    }

    /**
     * Checks whether the material represents any anvil variant.
     */
    private boolean isAnvil(@NotNull Material material) {
        return material == Material.ANVIL
                || material == Material.CHIPPED_ANVIL
                || material == Material.DAMAGED_ANVIL;
    }
}
