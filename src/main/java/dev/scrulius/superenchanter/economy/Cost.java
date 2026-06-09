/* SuperEnchanter - Author: Scrulius (GitHub) */
package dev.scrulius.superenchanter.economy;

import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * An amount of a single {@link CostType}, with the display formatting both GUIs
 * render. Pure and Bukkit-free, so it is unit-testable on its own.
 *
 * @param type   the currency
 * @param amount the numeric amount (interpreted as a whole number for XP/tokens)
 */
public record Cost(@NotNull CostType type, double amount) {

    /** Normalises a {@code null} type to {@link CostType#XP}. */
    public Cost {
        if (type == null) {
            type = CostType.XP;
        }
    }

    /** @return an XP cost of the given amount of raw XP points */
    public static @NotNull Cost xp(int amount) {
        return new Cost(CostType.XP, amount);
    }

    /** @return the amount rounded to a whole number (XP points / tokens are integral) */
    public int intAmount() {
        return (int) Math.round(amount);
    }

    /**
     * @return the human-readable cost string, e.g. {@code "1,250 XP"} (raw points),
     *         {@code "$1,500"} or {@code "25 Tokens"}
     */
    public @NotNull String displayText() {
        return switch (type) {
            case VAULT -> "$" + String.format(Locale.US, "%,.0f", amount);
            case PLAYER_POINTS -> intAmount() + " Tokens";
            case XP -> String.format(Locale.US, "%,d", intAmount()) + " XP";
        };
    }
}
