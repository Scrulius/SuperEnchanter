/* SuperEnchanter - Author: Scrulius (GitHub) */
package dev.scrulius.superenchanter.integration;

import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.items.MythicItem;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Soft integration with MythicMobs, used by the "enchanted bookshelf" feature to
 * recognise the custom library item on placement and to give it back on break.
 * All MythicMobs access is guarded so the plugin works fine without it.
 */
public final class MythicMobsHook {

    private final boolean enabled;

    public MythicMobsHook() {
        this.enabled = Bukkit.getPluginManager().isPluginEnabled("MythicMobs");
    }

    /** @return whether MythicMobs is present */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Returns the MythicMobs internal id of an item, or {@code null} if it isn't a
     * MythicMobs item (or MythicMobs is absent).
     */
    public @Nullable String getItemId(@Nullable ItemStack item) {
        if (!enabled || item == null || item.getType().isAir()) {
            return null;
        }
        try {
            return MythicBukkit.inst().getItemManager().getMythicTypeFromItem(item);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Generates a fresh MythicMobs item from its id, or {@code null} if unknown.
     */
    public @Nullable ItemStack createItem(@NotNull String id) {
        if (!enabled) {
            return null;
        }
        try {
            MythicItem item = MythicBukkit.inst().getItemManager().getItem(id).orElse(null);
            if (item == null) {
                return null;
            }
            return BukkitAdapter.adapt(item.generateItemStack(1));
        } catch (Throwable t) {
            Bukkit.getLogger().warning("[SuperEnchanter] Could not generate MythicMobs item '" + id + "': " + t.getMessage());
            return null;
        }
    }
}
