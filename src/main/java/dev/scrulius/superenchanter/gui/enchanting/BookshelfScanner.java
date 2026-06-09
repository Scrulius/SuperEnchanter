/* SuperEnchanter - Author: Scrulius (GitHub) */
package dev.scrulius.superenchanter.gui.enchanting;

import dev.scrulius.superenchanter.SuperEnchanterPlugin;
import dev.scrulius.superenchanter.config.PluginConfig;
import dev.scrulius.superenchanter.util.EnchantedBookshelfManager;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
                    if (airGapRequired && !hasAirGap(tableBlock, dx, dy, dz)) {
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
     * Real line-of-sight air gap: the straight line from the table to the candidate
     * must pass only through air. Every block cell the line crosses (excluding both
     * endpoints) is checked, so a power-providing block hidden behind a wall no longer
     * counts — unlike the old heuristic, which only inspected the single cell adjacent
     * to the candidate and let deeper blocks slip through.
     *
     * @param tableBlock the enchanting table block (origin of the line)
     * @param dx         relative X offset from table to candidate
     * @param dy         relative Y offset from table to candidate
     * @param dz         relative Z offset from table to candidate
     * @return {@code true} if every intervening cell is air (or the candidate is adjacent)
     */
    private static boolean hasAirGap(@NotNull Block tableBlock, int dx, int dy, int dz) {
        for (int[] cell : lineCells(dx, dy, dz)) {
            final Material type = tableBlock.getRelative(cell[0], cell[1], cell[2]).getType();
            if (type != Material.AIR && type != Material.CAVE_AIR && type != Material.VOID_AIR) {
                return false; // a solid block breaks the line of sight
            }
        }
        return true;
    }

    /**
     * Returns the integer block offsets (relative to the table at the origin) that the
     * straight line to a candidate at {@code (dx, dy, dz)} passes through, excluding both
     * endpoints. Pure geometry with no Bukkit dependency so it can be unit-tested in
     * isolation; the scanner walks these cells to require a clear line of sight.
     *
     * <p>The line between block <em>centres</em> is sampled finely and each sample is
     * rounded to its containing block; adjacent candidates (max axis span ≤ 1) have no
     * cells in between and yield an empty list.</p>
     */
    static @NotNull List<int[]> lineCells(int dx, int dy, int dz) {
        final int steps = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
        final List<int[]> cells = new ArrayList<>();
        if (steps <= 1) {
            return cells; // adjacent (or the table itself): nothing strictly in between
        }
        final int samples = steps * 4; // fine enough to land on every crossed cell
        for (int i = 1; i < samples; i++) {
            final double t = (double) i / samples;
            final int bx = (int) Math.round(dx * t);
            final int by = (int) Math.round(dy * t);
            final int bz = (int) Math.round(dz * t);
            if (bx == 0 && by == 0 && bz == 0) {
                continue; // still inside the table's own cell
            }
            if (bx == dx && by == dy && bz == dz) {
                continue; // reached the candidate
            }
            boolean seen = false;
            for (int[] c : cells) {
                if (c[0] == bx && c[1] == by && c[2] == bz) {
                    seen = true;
                    break;
                }
            }
            if (!seen) {
                cells.add(new int[]{bx, by, bz});
            }
        }
        return cells;
    }
}
