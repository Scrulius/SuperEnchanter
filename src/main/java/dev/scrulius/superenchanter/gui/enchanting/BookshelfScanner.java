/* SuperEnchanter - Author: Scrulius (GitHub) */
package dev.scrulius.superenchanter.gui.enchanting;

import dev.scrulius.superenchanter.SuperEnchanterPlugin;
import dev.scrulius.superenchanter.config.PluginConfig;
import dev.scrulius.superenchanter.util.EnchantedBookshelfManager;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Scans blocks surrounding an enchanting table to calculate the total bookshelf power.
 *
 * <p>Power is determined by iterating over a configurable cubic region around the
 * table block and summing up the power values of recognized materials. An optional
 * air-gap requirement can be enforced to ensure line-of-sight between the table
 * and power-providing blocks.</p>
 *
 * <p>This is a stateless utility class — all methods are static.</p>
 */
public final class BookshelfScanner {

    private BookshelfScanner() {
        // Utility class — no instantiation
    }

    /**
     * The outcome of a scan, split by source so the menu can show the breakdown
     * and so the vanilla soft-cap can be applied independently of libraries.
     *
     * @param total   the final usable power: {@code min(max, vanilla + library)}
     * @param vanilla power from normal bookshelves, already capped at the vanilla cap
     * @param library power from marked enchanted libraries
     * @param max     the configured power cap (for display)
     */
    public record ScanResult(int total, int vanilla, int library, int max) {}

    /**
     * Scans blocks around the given enchanting table and returns the power broken
     * down by source.
     *
     * <p>The scan iterates a cube of configurable radius. Normal blocks contribute
     * their {@code power-values} entry; marked enchanted libraries contribute their
     * configured tier power. Vanilla contribution is capped at
     * {@code vanilla-power-cap}, so the high end of the range can only be reached
     * with libraries. The grand total is capped at {@code max-bookshelf-power}.</p>
     *
     * @param tableBlock the physical enchanting table block
     * @param plugin     the plugin (for config + enchanted-bookshelf marks)
     * @return the power breakdown
     */
    public static ScanResult scan(@NotNull Block tableBlock, @NotNull SuperEnchanterPlugin plugin) {
        final PluginConfig config = plugin.getPluginConfig();
        final EnchantedBookshelfManager bookshelves = plugin.getEnchantedBookshelfManager();
        final int scanRadiusH = config.getScanRadiusH();
        final int scanRadiusV = config.getScanRadiusV();
        final boolean airGapRequired = config.isAirGapRequired();
        final Map<String, Integer> powerValues = config.getPowerValues();
        final int maxPower = config.getMaxBookshelfPower();
        final int vanillaCap = config.getVanillaPowerCap();

        // Parse each chunk's enchanted-library marks at most once per scan.
        final Map<Long, Map<String, String>> chunkMarks = new HashMap<>();

        int vanillaPower = 0;
        int libraryPower = 0;

        for (int dx = -scanRadiusH; dx <= scanRadiusH; dx++) {
            for (int dy = -scanRadiusV; dy <= scanRadiusV; dy++) {
                for (int dz = -scanRadiusH; dz <= scanRadiusH; dz++) {
                    // Skip the table block itself
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }

                    final Block candidate = tableBlock.getRelative(dx, dy, dz);

                    // An enchanted library overrides (and is bucketed separately from)
                    // the normal bookshelf power.
                    final Integer enchanted = enchantedPower(candidate, config, bookshelves, chunkMarks);
                    final boolean isLibrary = enchanted != null;
                    final int blockPower = isLibrary ? enchanted : vanillaPowerOf(candidate, powerValues);

                    if (blockPower <= 0) {
                        continue;
                    }

                    // Enforce air gap if configured
                    if (airGapRequired && !hasAirGap(tableBlock, candidate, dx, dy, dz)) {
                        continue;
                    }

                    if (isLibrary) {
                        libraryPower += blockPower;
                    } else {
                        vanillaPower += blockPower;
                    }
                }
            }
        }

        final int cappedVanilla = Math.min(vanillaPower, vanillaCap);
        final int total = Math.min(maxPower, cappedVanilla + libraryPower);
        return new ScanResult(total, cappedVanilla, libraryPower, maxPower);
    }

    /** Looks up a normal block's configured power, trying name / upper / namespaced key. */
    private static int vanillaPowerOf(@NotNull Block candidate, @NotNull Map<String, Integer> powerValues) {
        final String materialName = candidate.getType().name();
        int power = powerValues.getOrDefault(materialName, -1);
        if (power < 0) {
            power = powerValues.getOrDefault(materialName.toUpperCase(), -1);
        }
        if (power < 0) {
            power = powerValues.getOrDefault(candidate.getType().getKey().toString(), -1);
        }
        return power;
    }

    /**
     * Returns the configured power of an enchanted library at this block, or
     * {@code null} if the block isn't a marked library. Chunk marks are parsed
     * once and cached in {@code chunkMarks} for the duration of the scan.
     */
    private static @Nullable Integer enchantedPower(@NotNull Block block,
                                                    @NotNull PluginConfig config,
                                                    @NotNull EnchantedBookshelfManager bookshelves,
                                                    @NotNull Map<Long, Map<String, String>> chunkMarks) {
        final Material type = block.getType();
        if (type != Material.BOOKSHELF && type != Material.CHISELED_BOOKSHELF) {
            return null;
        }
        final Map<String, String> marks =
                chunkMarks.computeIfAbsent(block.getChunk().getChunkKey(), k -> bookshelves.read(block.getChunk()));
        final String id = marks.get(EnchantedBookshelfManager.posKey(block));
        return id == null ? null : config.getEnchantedBookshelfPower(id);
    }

    /**
     * Checks whether there is at least one air block on the direct line between
     * the table and the candidate block. Uses a simplified approach: checks the
     * adjacent block one step closer toward the table.
     *
     * @param tableBlock the enchanting table block
     * @param candidate  the block being evaluated
     * @param dx         relative X offset from table to candidate
     * @param dy         relative Y offset from table to candidate
     * @param dz         relative Z offset from table to candidate
     * @return {@code true} if an air gap exists, or the block is directly adjacent to the table
     */
    private static boolean hasAirGap(@NotNull Block tableBlock, @NotNull Block candidate,
                                     int dx, int dy, int dz) {
        // If the candidate is directly adjacent (Manhattan distance 1), no gap needed
        if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) <= 1) {
            return true;
        }

        // Compute one step back toward the table (sign-based)
        final int stepX = Integer.signum(-dx);
        final int stepY = Integer.signum(-dy);
        final int stepZ = Integer.signum(-dz);

        final Block adjacent = candidate.getRelative(stepX, stepY, stepZ);
        final Material adjacentType = adjacent.getType();

        return adjacentType == Material.AIR
                || adjacentType == Material.CAVE_AIR
                || adjacentType == Material.VOID_AIR;
    }
}
