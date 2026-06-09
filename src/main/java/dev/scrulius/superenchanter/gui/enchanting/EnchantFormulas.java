/* SuperEnchanter - Author: Scrulius (GitHub) */
package dev.scrulius.superenchanter.gui.enchanting;

/**
 * Pure, Bukkit-free enchanting-table arithmetic, split out from
 * {@link EnchantingLogic} so the cost / power curves can be unit-tested without a
 * running server. Every method is a deterministic function of its inputs.
 */
public final class EnchantFormulas {

    private static final String[] ROMAN_NUMERALS = {
            "", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"
    };

    private EnchantFormulas() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Rarity-scaled XP cost for a single enchantment level.
     * <p>
     * {@code raw = base + levelMultiplier * level^exponent}, scaled by the rarity
     * multiplier, rounded, then clamped to {@code [1, costCap]}. The cap is what
     * stops high rarities from demanding an unreachable amount of XP.
     *
     * @param base             flat base cost
     * @param levelMultiplier  weight of the level term
     * @param level            the enchantment level (1-based)
     * @param exponent         curve exponent applied to the level
     * @param rarityMultiplier per-rarity cost multiplier
     * @param costCap          hard upper bound on the cost
     * @return the final XP cost, never below 1 nor above {@code costCap}
     */
    public static int xpCostForLevel(double base, double levelMultiplier, int level,
                                     double exponent, double rarityMultiplier, int costCap) {
        final double raw = base + levelMultiplier * Math.pow(level, exponent);
        return Math.max(1, Math.min(costCap, (int) Math.round(raw * rarityMultiplier)));
    }

    /**
     * Cumulative XP cost of upgrading an enchantment from {@code currentLevel} to
     * {@code targetLevel}: the sum of every individual level step in between, so
     * jumping straight to level V in one click costs exactly the same as forging
     * I→II→III→IV→V by hand. This is what turns the cost curve into a real sink —
     * the per-level cap still applies to each step before they are summed.
     * <p>
     * {@code perLevelCost[i]} is the cost of reaching level {@code i + 1} from the
     * level below it. Steps at or below {@code currentLevel} are already paid for
     * and therefore never re-charged.
     *
     * @param perLevelCost the per-level step costs, indexed from level 1 at [0]
     * @param currentLevel the level already on the item (0 if absent)
     * @param targetLevel  the level being purchased (1-based)
     * @return the summed cost of every step in {@code (currentLevel, targetLevel]}, at least 1
     */
    public static int cumulativeCost(int[] perLevelCost, int currentLevel, int targetLevel) {
        long total = 0;
        for (int lvl = currentLevel + 1; lvl <= targetLevel; lvl++) {
            if (lvl >= 1 && lvl <= perLevelCost.length) {
                total += perLevelCost[lvl - 1];
            }
        }
        return (int) Math.max(1L, Math.min((long) Integer.MAX_VALUE, total));
    }

    /**
     * Effective success chance (0–100) of an enchant attempt: the per-rarity base
     * chance plus the percentage contributed by any potentiator item, clamped to
     * {@code [0, 100]}. A potentiator worth {@code >= 100} therefore <em>guarantees</em>
     * the enchant (a "guarantee orb") — boosters and guarantee orbs are the same
     * additive mechanic, a guarantee just reaches the cap.
     *
     * @param baseChance     the per-rarity base success chance (0–100)
     * @param boosterPercent extra percentage points from the potentiator (0 if none)
     * @return the clamped effective chance in {@code [0, 100]}
     */
    public static int effectiveChance(int baseChance, int boosterPercent) {
        final int sum = baseChance + boosterPercent;
        return Math.max(0, Math.min(100, sum));
    }

    /**
     * Required bookshelf power for a single enchantment level: a per-rarity floor
     * plus a per-level step, capped at {@code maxPower}. Rarity therefore gates
     * the magnitude reachable, not the fraction of a level.
     *
     * @param floor    per-rarity base power requirement
     * @param step     additional power required per level
     * @param level    the enchantment level (1-based)
     * @param maxPower the maximum attainable bookshelf power
     * @return the required power, capped at {@code maxPower}
     */
    public static int requiredPowerForLevel(int floor, int step, int level, int maxPower) {
        return Math.min(maxPower, floor + level * step);
    }

    /**
     * Renders {@code 1..10} as Roman numerals; any other value falls back to its
     * decimal string.
     *
     * @param number the number to render
     * @return the Roman numeral, or the decimal string when out of {@code [1,10]}
     */
    public static String toRoman(int number) {
        if (number < 1 || number > 10) {
            return String.valueOf(number);
        }
        return ROMAN_NUMERALS[number];
    }
}
