/* SuperEnchanter - Author: Scrulius (GitHub) */
package dev.scrulius.superenchanter.util;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Durable, crash-safe store for items that are sitting in a custom GUI's input
 * slots.
 * <p>
 * While items live in a GUI they are held in a virtual (server-memory-only)
 * inventory, so a hard server crash would lose them. This store snapshots those
 * items to a flat YAML file the instant they enter the GUI and clears the entry
 * the instant they leave (clean close, quit, or restore). On the next join the
 * items are handed back to the player.
 * </p>
 * <p>
 * Restoring items that were snapshotted is dupe-safe: the snapshotted items were
 * removed from the player's real (saved) inventory when they were placed into
 * the GUI, so returning them simply undoes a loss — it never creates copies.
 * </p>
 */
public final class PendingItemStore {

    private final Plugin plugin;
    private final File file;
    private final FileConfiguration data;

    /**
     * Loads (or creates) the pending-items file in the plugin data folder.
     *
     * @param plugin the owning plugin
     */
    public PendingItemStore(@NotNull Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "pending-items.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    /**
     * Records the given items as pending for a player, overwriting any previous
     * snapshot. An empty list clears the entry.
     *
     * @param playerId the player's UUID
     * @param items    the items currently held in the GUI's input slots
     */
    public synchronized void put(@NotNull UUID playerId, @NotNull List<ItemStack> items) {
        if (items.isEmpty()) {
            remove(playerId);
            return;
        }
        data.set(playerId.toString(), items);
        save();
    }

    /**
     * Clears any pending snapshot for the given player.
     *
     * @param playerId the player's UUID
     */
    public synchronized void remove(@NotNull UUID playerId) {
        if (data.contains(playerId.toString())) {
            data.set(playerId.toString(), null);
            save();
        }
    }

    /**
     * Removes and returns the pending items for a player.
     *
     * @param playerId the player's UUID
     * @return the previously pending items (never {@code null}; empty if none)
     */
    public synchronized @NotNull List<ItemStack> drain(@NotNull UUID playerId) {
        List<?> raw = data.getList(playerId.toString());
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<ItemStack> items = new ArrayList<>(raw.size());
        for (Object element : raw) {
            if (element instanceof ItemStack stack) {
                items.add(stack);
            }
        }
        data.set(playerId.toString(), null);
        save();
        return items;
    }

    /**
     * @return the set of player UUIDs that currently have pending items
     */
    public synchronized @NotNull Set<UUID> pendingPlayers() {
        Set<UUID> players = new HashSet<>();
        for (String key : data.getKeys(false)) {
            try {
                players.add(UUID.fromString(key));
            } catch (IllegalArgumentException ignored) {
                // Skip malformed keys.
            }
        }
        return players;
    }

    private void save() {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                //noinspection ResultOfMethodCallIgnored
                parent.mkdirs();
            }
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("[SuperEnchanter] Could not save pending-items.yml: " + e.getMessage());
        }
    }
}
