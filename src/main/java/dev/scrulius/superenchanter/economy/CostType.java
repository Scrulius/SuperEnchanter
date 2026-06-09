/* SuperEnchanter - Author: Scrulius (GitHub) */
package dev.scrulius.superenchanter.economy;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * The currencies a transaction (anvil forge or table enchant) can be charged in.
 * Shared by both GUIs so cost handling lives in one place.
 */
public enum CostType {
    /** Vanilla experience levels. */
    XP,
    /** Vault economy money. */
    VAULT,
    /** PlayerPoints tokens. */
    PLAYER_POINTS;

    /**
     * Parses a configured cost-type string, defaulting to {@link #XP} for
     * {@code null}, blank or unrecognised values.
     *
     * @param raw the configured value (e.g. {@code "VAULT"})
     * @return the matching type, or {@link #XP} as a safe fallback
     */
    public static CostType fromString(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return XP;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return XP;
        }
    }
}
