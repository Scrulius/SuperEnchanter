/* SuperEnchanter - Author: Scrulius (GitHub) */
package dev.scrulius.superenchanter.util;

import dev.scrulius.superenchanter.SuperEnchanterPlugin;
import dev.scrulius.superenchanter.config.ParticleEffect;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Spawns subtle enchant particles over enchanted libraries near players so the
 * special bookshelves are visibly distinct from normal ones (no resource pack
 * needed). Cheap by design: only the 3×3 loaded chunks around each player are
 * inspected, each chunk's marks are read once, and duplicates are de-duplicated
 * so a library near several players isn't drawn twice.
 */
public final class LibraryAmbientTask extends BukkitRunnable {

    private final SuperEnchanterPlugin plugin;
    private final EnchantedBookshelfManager manager;

    public LibraryAmbientTask(@NotNull SuperEnchanterPlugin plugin) {
        this.plugin = plugin;
        this.manager = plugin.getEnchantedBookshelfManager();
    }

    @Override
    public void run() {
        final Set<Long> doneChunks = new HashSet<>();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            final World world = player.getWorld();
            final Chunk center = player.getLocation().getChunk();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    final int cx = center.getX() + dx;
                    final int cz = center.getZ() + dz;
                    if (!world.isChunkLoaded(cx, cz)) {
                        continue;
                    }
                    final Chunk chunk = world.getChunkAt(cx, cz);
                    if (!doneChunks.add(chunk.getChunkKey())) {
                        continue; // already drawn this tick for another player
                    }
                    emitForChunk(world, chunk);
                }
            }
        }
    }

    private void emitForChunk(@NotNull World world, @NotNull Chunk chunk) {
        final ParticleEffect effect = plugin.getPluginConfig().getLibraryAmbientParticle();
        final Map<String, String> marks = manager.read(chunk);
        for (String posKey : marks.keySet()) {
            final Location loc = parse(world, posKey);
            if (loc != null) {
                effect.spawn(world, loc);
            }
        }
    }

    /** Parses an {@code "x,y,z"} position key into a particle location at the block's top-centre. */
    private static Location parse(@NotNull World world, @NotNull String posKey) {
        final String[] parts = posKey.split(",");
        if (parts.length != 3) {
            return null;
        }
        try {
            return new Location(world,
                    Integer.parseInt(parts[0]) + 0.5,
                    Integer.parseInt(parts[1]) + 1.05,
                    Integer.parseInt(parts[2]) + 0.5);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
