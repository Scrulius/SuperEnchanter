/* SuperEnchanter - Author: Scrulius (GitHub) */
package dev.scrulius.superenchanter.gui.anvil;

/**
 * Pure, Bukkit-free anvil arithmetic, split out from {@link AnvilLogic} so it can
 * be unit-tested without a running server. Every method is a deterministic
 * function of its primitive inputs.
 */
public final class AnvilFormulas {

    private AnvilFormulas() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Resolves the new level when merging a sacrifice enchantment into a target.
     * <ul>
     *   <li>Equal levels → upgrade by one, capped at {@code maxLevel}.</li>
     *   <li>Different levels → keep the higher of the two.</li>
     * </ul>
     * The result is always clamped to {@code [0, maxLevel]}.
     *
     * @param currentLevel   the target's current level of the enchantment (0 if absent)
     * @param sacrificeLevel the sacrifice's level of the enchantment
     * @param maxLevel       the enchantment's maximum level
     * @return the merged level
     */
    public static int mergeLevel(int currentLevel, int sacrificeLevel, int maxLevel) {
        final int merged = (currentLevel == sacrificeLevel)
                ? currentLevel + 1
                : Math.max(currentLevel, sacrificeLevel);
        return Math.min(merged, maxLevel);
    }

    /**
     * Computes the XP cost of an anvil merge: a flat base plus a per-level term,
     * hard-capped at {@code maxCost}.
     *
     * @param baseCost     flat cost applied to any valid merge
     * @param costPerLevel cost added per enchantment level gained
     * @param levelsAdded  total enchantment levels added / upgraded
     * @param maxCost      upper bound on the final cost
     * @return the capped XP cost
     */
    public static int xpCost(int baseCost, int costPerLevel, int levelsAdded, int maxCost) {
        return Math.min(baseCost + (levelsAdded * costPerLevel), maxCost);
    }
}
