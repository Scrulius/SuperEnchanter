/* SuperEnchanter - Author: Scrulius (GitHub) */
package dev.scrulius.superenchanter.economy;

/**
 * Pure, Bukkit-free vanilla experience curve math. Costs are charged in raw XP
 * <b>points</b> (via Paper's native {@code calculateTotalExperiencePoints} /
 * {@code setExperienceLevelAndProgress}); this class only exists for the honest
 * level feedback shown to the player ("≈ N niveles") — converting between points
 * and levels using the exact vanilla formulas.
 * <p>
 * Vanilla curve (Minecraft wiki): total points to reach level L from zero is
 * {@code L² + 6L} (L ≤ 16), {@code 2.5L² − 40.5L + 360} (17 ≤ L ≤ 31) and
 * {@code 4.5L² − 162.5L + 2220} (L ≥ 32).
 */
public final class XpMath {

    /** Total points at the level-16 boundary (16² + 6·16). */
    private static final long POINTS_AT_16 = 352;
    /** Total points at the level-31 boundary (2.5·31² − 40.5·31 + 360). */
    private static final long POINTS_AT_31 = 1507;

    private XpMath() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Total XP points needed to reach {@code level} starting from zero.
     *
     * @param level the experience level (negative is treated as 0)
     * @return the cumulative points for that level
     */
    public static long totalPointsForLevel(int level) {
        if (level <= 0) {
            return 0;
        }
        if (level <= 16) {
            return (long) level * level + 6L * level;
        }
        if (level <= 31) {
            return Math.round(2.5 * level * level - 40.5 * level + 360.0);
        }
        return Math.round(4.5 * level * level - 162.5 * level + 2220.0);
    }

    /**
     * The experience level a player holding {@code points} total XP points would
     * be at (the floor — partial progress toward the next level is discarded).
     * Exact inverse of {@link #totalPointsForLevel}, piecewise per the vanilla curve.
     *
     * @param points the total XP points (negative is treated as 0)
     * @return the corresponding level
     */
    public static int levelForPoints(long points) {
        if (points <= 0) {
            return 0;
        }
        final double level;
        if (points <= POINTS_AT_16) {
            level = Math.sqrt(points + 9.0) - 3.0;
        } else if (points <= POINTS_AT_31) {
            level = (40.5 + Math.sqrt(1640.25 - 10.0 * (360.0 - points))) / 5.0;
        } else {
            level = (162.5 + Math.sqrt(26406.25 - 18.0 * (2220.0 - points))) / 9.0;
        }
        // Tiny epsilon so exact boundaries (e.g. 352 → 16.0) don't floor down a level.
        return (int) Math.floor(level + 1e-7);
    }

    /**
     * How many experience levels paying {@code costPoints} would actually take
     * from a player at {@code currentLevel} holding {@code currentTotalPoints} —
     * the honest "≈ N niveles" feedback. Never negative.
     *
     * @param currentLevel       the player's current level
     * @param currentTotalPoints the player's current total XP points
     * @param costPoints         the cost in points
     * @return the levels the player would drop (0 = less than one level)
     */
    public static int levelsLost(int currentLevel, long currentTotalPoints, long costPoints) {
        final long after = Math.max(0, currentTotalPoints - Math.max(0, costPoints));
        return Math.max(0, currentLevel - levelForPoints(after));
    }
}
