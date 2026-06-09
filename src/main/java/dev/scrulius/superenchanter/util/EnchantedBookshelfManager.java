/* SuperEnchanter - Author: Scrulius (GitHub) */
package dev.scrulius.superenchanter.util;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tracks which placed bookshelves are "enchanted libraries", recreating the
 * per-block memory vanilla lacks.
 * <p>
 * Marks are stored in each chunk's {@link PersistentDataContainer} as a compact
 * string ({@code "x,y,z=mythicId;..."}), so they persist across restarts (saved
 * with the region file) without any global in-memory map or database. The value
 * stored is the MythicMobs item id, so different library tiers can grant
 * different power.
 * </p>
 */
public final class EnchantedBookshelfManager {

    private final NamespacedKey key;

    public EnchantedBookshelfManager(@NotNull Plugin plugin) {
        this.key = new NamespacedKey(plugin, "enchanted_bookshelves");
    }

    /** Records a block as an enchanted library of the given MythicMobs id. */
    public void mark(@NotNull Block block, @NotNull String mythicId) {
        Map<String, String> marks = read(block.getChunk());
        marks.put(posKey(block), mythicId);
        write(block.getChunk(), marks);
    }

    /** Clears the mark on a block, if any. */
    public void unmark(@NotNull Block block) {
        Map<String, String> marks = read(block.getChunk());
        if (marks.remove(posKey(block)) != null) {
            write(block.getChunk(), marks);
        }
    }

    /**
     * @return the MythicMobs id marked at this block, or {@code null} if unmarked
     * <p>
     * Short-circuits on the block material before touching the chunk PDC: marks
     * only ever live on bookshelves (they're created on bookshelf placement and
     * the scanner self-heals stale ones), so a non-bookshelf can never be marked.
     * This keeps explosion/piston sweeps cheap — the overwhelming majority of
     * blocks are dirt/stone and skip the PDC parse entirely.
     */
    public @Nullable String getMark(@NotNull Block block) {
        if (!canBeMarked(block.getType())) {
            return null;
        }
        return read(block.getChunk()).get(posKey(block));
    }

    /**
     * Chunk-cached variant of {@link #getMark(Block)} for hot loops over many
     * blocks in the same area (explosions, pistons). Each chunk's marks are parsed
     * at most once into {@code chunkCache} and reused for subsequent blocks.
     *
     * @param block      the block to look up
     * @param chunkCache a per-sweep cache of chunk-key → parsed marks (mutated)
     * @return the MythicMobs id marked at this block, or {@code null} if unmarked
     */
    public @Nullable String getMark(@NotNull Block block,
                                    @NotNull Map<Long, Map<String, String>> chunkCache) {
        if (!canBeMarked(block.getType())) {
            return null;
        }
        Map<String, String> marks = chunkCache.computeIfAbsent(
                block.getChunk().getChunkKey(), k -> read(block.getChunk()));
        return marks.get(posKey(block));
    }

    /** @return whether this block type can ever be an enchanted library (cheap; no PDC access) */
    public static boolean canBeMarked(@NotNull Material type) {
        return type == Material.BOOKSHELF || type == Material.CHISELED_BOOKSHELF;
    }

    /** @return {@code true} if any of the given blocks is a marked library (chunk-cached) */
    public boolean anyMarked(@NotNull Iterable<Block> blocks) {
        final Map<Long, Map<String, String>> cache = new HashMap<>();
        for (Block block : blocks) {
            if (getMark(block, cache) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns all marks in a chunk (position key → MythicMobs id). Parse the chunk
     * once and reuse this when scanning many blocks in the same chunk.
     */
    public @NotNull Map<String, String> read(@NotNull Chunk chunk) {
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        String raw = pdc.getOrDefault(key, PersistentDataType.STRING, "");
        Map<String, String> marks = new LinkedHashMap<>();
        if (!raw.isEmpty()) {
            for (String entry : raw.split(";")) {
                int eq = entry.lastIndexOf('=');
                if (eq > 0) {
                    marks.put(entry.substring(0, eq), entry.substring(eq + 1));
                }
            }
        }
        return marks;
    }

    /** Position key of a block within its world (used as the map key). */
    public static @NotNull String posKey(@NotNull Block block) {
        return block.getX() + "," + block.getY() + "," + block.getZ();
    }

    private void write(@NotNull Chunk chunk, @NotNull Map<String, String> marks) {
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        if (marks.isEmpty()) {
            pdc.remove(key);
            return;
        }
        StringBuilder sb = new StringBuilder();
        marks.forEach((k, v) -> {
            if (sb.length() > 0) {
                sb.append(';');
            }
            sb.append(k).append('=').append(v);
        });
        pdc.set(key, PersistentDataType.STRING, sb.toString());
    }
}
