/* SuperEnchanter - Author: Scrulius (GitHub) */
package dev.scrulius.superenchanter.integration;

import dev.scrulius.superenchanter.SuperEnchanterPlugin;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * PlaceholderAPI expansion exposing the live "Magia" skill bonuses so other configs
 * (the EcoSkills skill GUI, scoreboards, …) can show the REAL values a player gets —
 * the carriles live in this plugin, so EcoSkills can't compute them on its own.
 *
 * <p>Identifier {@code superenchanter}. Placeholders (all need an online player except
 * {@code magia_active}):
 * <ul>
 *   <li>{@code %superenchanter_magia_active%} — {@code true}/{@code false}</li>
 *   <li>{@code %superenchanter_magia_level%} — current Magia level</li>
 *   <li>{@code %superenchanter_magia_success%} — Carril 1 success bonus (percentage points)</li>
 *   <li>{@code %superenchanter_magia_discount%} — Carril 2 cost discount (percent)</li>
 *   <li>{@code %superenchanter_magia_refund%} — Reembolso Arcano chance (percent)</li>
 *   <li>{@code %superenchanter_magia_mana_bonus%} — max-mana bonus from the skill</li>
 * </ul>
 *
 * <p>Only registered when PlaceholderAPI is installed (guarded in
 * {@code SuperEnchanterPlugin.onEnable}); referencing it otherwise would link PAPI
 * classes that may be absent. Values fall back to {@code 0} when Magia is inactive.
 */
public final class SuperEnchanterPlaceholders extends PlaceholderExpansion {

    private final SuperEnchanterPlugin plugin;

    public SuperEnchanterPlaceholders(@NotNull SuperEnchanterPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "superenchanter";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Scrulius";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    /** Keep the expansion registered across PlaceholderAPI reloads. */
    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(@Nullable OfflinePlayer player, @NotNull String params) {
        final MagiaService magia = plugin.getMagiaService();
        final boolean active = magia != null && magia.isEnabled();
        final String key = params.toLowerCase(Locale.ROOT);

        if (key.equals("magia_active")) {
            return String.valueOf(active);
        }
        // Every other placeholder needs the player's level → an online player.
        if (!(player instanceof Player p) || !active) {
            return "0";
        }
        return switch (key) {
            case "magia_level" -> String.valueOf(magia.level(p));
            case "magia_success" -> String.valueOf(magia.successBonus(p));
            case "magia_discount" -> String.valueOf(magia.discountPercent(p));
            case "magia_refund" -> String.valueOf(magia.refundChance(p));
            case "magia_mana_bonus" -> String.valueOf(magia.manaBonus(p));
            default -> null;
        };
    }
}
