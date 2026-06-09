/* SuperEnchanter - Author: Scrulius (GitHub) */
package dev.scrulius.superenchanter.listener;

import dev.scrulius.superenchanter.SuperEnchanterPlugin;
import dev.scrulius.superenchanter.config.PluginConfig;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerItemMendEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

/**
 * Globally purges banned enchantments (default: <b>mending</b>) so they can never
 * exist on this server — the successor to the standalone <i>AntiMending</i> plugin,
 * generalised to a configurable key list and folded into SuperEnchanter.
 * <p>
 * Mending is intentionally disabled on the RPG server (XP repair would undercut the
 * enchanting economy). The custom table never offers it (blacklist) and the vanilla
 * anvil is fully blocked, but pre-existing items, villager books and creative spawns
 * are still vectors — this listener closes them. All actions are config-gated
 * ({@code banned-enchantments}):
 * <ul>
 *   <li>{@code purge-player-inventories} — strips banned enchants on join / inventory
 *       open / click (the click strip is deferred 1 tick to avoid breaking Bukkit's
 *       item tracking and duping in creative).</li>
 *   <li>{@code block-xp-repair} — cancels {@link PlayerItemMendEvent} so XP never
 *       repairs gear even if a banned item slips through.</li>
 *   <li>pickups and fishing are always sanitised when the feature is on.</li>
 * </ul>
 * Loot is handled separately by {@link LootControlListener} (which strips ALL
 * enchantments from natural loot), so it isn't duplicated here.
 */
public final class BannedEnchantmentListener implements Listener {

    private final SuperEnchanterPlugin plugin;

    public BannedEnchantmentListener(@NotNull SuperEnchanterPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean active() {
        return plugin.getPluginConfig().isBannedEnchantmentsEnabled();
    }

    /**
     * Removes every banned enchantment (held or book-stored) from an item.
     *
     * @return {@code true} if the item was modified
     */
    private boolean strip(@Nullable ItemStack item) {
        if (item == null || item.isEmpty() || !item.hasItemMeta()) {
            return false;
        }
        final PluginConfig config = plugin.getPluginConfig();
        final ItemMeta meta = item.getItemMeta();
        boolean stripped = false;

        for (Enchantment ench : new ArrayList<>(meta.getEnchants().keySet())) {
            if (config.isEnchantmentBanned(ench)) {
                meta.removeEnchant(ench);
                stripped = true;
            }
        }
        if (meta instanceof EnchantmentStorageMeta storage) {
            for (Enchantment ench : new ArrayList<>(storage.getStoredEnchants().keySet())) {
                if (config.isEnchantmentBanned(ench)) {
                    storage.removeStoredEnchant(ench);
                    stripped = true;
                }
            }
        }
        if (stripped) {
            item.setItemMeta(meta);
        }
        return stripped;
    }

    /** Sanitises every slot of an inventory in place. */
    private boolean purge(@Nullable Inventory inv) {
        if (inv == null) {
            return false;
        }
        final ItemStack[] contents = inv.getContents();
        boolean changed = false;
        for (ItemStack item : contents) {
            changed |= strip(item);
        }
        return changed;
    }

    /** Purges a player's inventory and cursor, refreshing the client if anything changed. */
    private void purgePlayer(@NotNull Player player) {
        final PlayerInventory inv = player.getInventory();
        boolean changed = purge(inv);

        final ItemStack cursor = player.getItemOnCursor();
        if (strip(cursor)) {
            player.setItemOnCursor(cursor);
            changed = true;
        }
        if (changed) {
            player.updateInventory();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(@NotNull PlayerJoinEvent event) {
        if (active() && plugin.getPluginConfig().isBannedPurgeInventories()) {
            purgePlayer(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryOpen(@NotNull InventoryOpenEvent event) {
        if (!active() || !plugin.getPluginConfig().isBannedPurgeInventories()) {
            return;
        }
        if (event.getPlayer() instanceof Player player) {
            purgePlayer(player);
        }
        purge(event.getInventory());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        if (!active() || !plugin.getPluginConfig().isBannedPurgeInventories()) {
            return;
        }
        if (event.getWhoClicked() instanceof Player player) {
            // Mutating items mid-click breaks Bukkit's item tracking (dupes, esp. creative);
            // defer the strip 1 tick so the click resolves naturally first.
            final Inventory top = event.getInventory();
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                purgePlayer(player);
                purge(top);
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryCreative(@NotNull InventoryCreativeEvent event) {
        if (!active() || !plugin.getPluginConfig().isBannedPurgeInventories()) {
            return;
        }
        if (event.getWhoClicked() instanceof Player player) {
            final Inventory top = event.getInventory();
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                purgePlayer(player);
                purge(top);
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(@NotNull EntityPickupItemEvent event) {
        if (!active()) {
            return;
        }
        final ItemStack item = event.getItem().getItemStack();
        if (strip(item)) {
            event.getItem().setItemStack(item);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFish(@NotNull PlayerFishEvent event) {
        if (active() && event.getCaught() instanceof Item itemEntity) {
            final ItemStack item = itemEntity.getItemStack();
            if (strip(item)) {
                itemEntity.setItemStack(item);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemMend(@NotNull PlayerItemMendEvent event) {
        // Block the very effect of mending: XP never repairs durability.
        if (active() && plugin.getPluginConfig().isBannedBlockXpRepair()) {
            event.setCancelled(true);
        }
    }
}
