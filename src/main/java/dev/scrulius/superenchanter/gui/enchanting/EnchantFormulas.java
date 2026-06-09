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

    /** Glyph used for every text progress bar segment (renders in the vanilla font). */
    private static final char BAR_SEGMENT = '■';

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
     * Effective success chance (0–100) of an enchant attempt: the per-rarity base
     * chance plus any bonus percentage points (today that's only the Magia skill
     * bonus — the rarity seals were removed by design), clamped to {@code [0, 100]}.
     *
     * @param baseChance   the per-rarity base success chance (0–100)
     * @param bonusPercent extra percentage points (0 if none)
     * @return the clamped effective chance in {@code [0, 100]}
     */
    public static int effectiveChance(int baseChance, int bonusPercent) {
        final int sum = baseChance + bonusPercent;
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
     * How many segments of a text progress bar are filled for {@code value} out of
     * {@code max}: proportional, rounded to the nearest segment, clamped to
     * {@code [0, segments]}. A non-positive {@code max} renders an empty bar.
     *
     * @param value    the current value (clamped to {@code [0, max]})
     * @param max      the value that fills the whole bar
     * @param segments the total number of bar segments
     * @return the number of filled segments in {@code [0, segments]}
     */
    public static int filledSegments(double value, double max, int segments) {
        if (segments <= 0) {
            return 0;
        }
        if (max <= 0 || value <= 0) {
            return 0;
        }
        final double fraction = Math.min(1.0, value / max);
        return Math.min(segments, (int) Math.round(fraction * segments));
    }

    /**
     * Renders a MiniMessage text progress bar: the filled run wrapped in
     * {@code filledTag} and the empty run wrapped in {@code emptyTag}, both using
     * the {@code ■} glyph (present in the vanilla font — no resource pack). Tags
     * are raw MiniMessage open tags, e.g. {@code "<gradient:#43E97B:#38D9A9>"} or
     * {@code "<#3A3F46>"}; gradient tags are auto-closed.
     *
     * @param value     the current value
     * @param max       the value that fills the whole bar
     * @param segments  the total number of bar segments
     * @param filledTag MiniMessage open tag for the filled run
     * @param emptyTag  MiniMessage open tag for the empty run
     * @return the MiniMessage string for the bar
     */
    public static String progressBar(double value, double max, int segments,
                                     String filledTag, String emptyTag) {
        final int filled = filledSegments(value, max, segments);
        final StringBuilder sb = new StringBuilder(segments + 48);
        if (filled > 0) {
            sb.append(filledTag);
            sb.append(String.valueOf(BAR_SEGMENT).repeat(filled));
            if (filledTag.startsWith("<gradient")) {
                sb.append("</gradient>");
            }
        }
        if (filled < segments) {
            sb.append(emptyTag);
            sb.append(String.valueOf(BAR_SEGMENT).repeat(segments - filled));
        }
        return sb.toString();
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
