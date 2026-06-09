/* SuperEnchanter - Author: Scrulius (GitHub) */
package dev.scrulius.superenchanter.economy;

import dev.scrulius.superenchanter.SuperEnchanterPlugin;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Single point for checking, charging and displaying transaction costs across
 * both GUIs. Wraps the XP / Vault / PlayerPoints back-ends so neither the anvil
 * nor the enchanting table duplicates economy plumbing, and so a cost can be
 * denominated in any currency in either menu.
 */
public final class CostService {

    /** Permission prefix granting an N% discount, e.g. {@code superenchanter.cost.discount.25}. */
    private static final String DISCOUNT_PREFIX = "superenchanter.cost.discount.";
    /** Permission granting free operations. */
    private static final String BYPASS_PERMISSION = "superenchanter.cost.bypass";

    private final SuperEnchanterPlugin plugin;

    public CostService(@NotNull SuperEnchanterPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * The discount multiplier in {@code [0, 1]} for a player: {@code 1.0} = full
     * price, {@code 0.75} = 25% off, {@code 0.0} = free. Driven by permissions
     * ({@code superenchanter.cost.bypass} and {@code superenchanter.cost.discount.<n>},
     * highest wins). Returns {@code 1.0} when the feature is disabled in config.
     */
    public double discountMultiplier(@NotNull Player player) {
        if (!plugin.getPluginConfig().isCostDiscountsEnabled()) {
            return 1.0;
        }
        if (player.hasPermission(BYPASS_PERMISSION)) {
            return 0.0;
        }
        int best = 0;
        for (var info : player.getEffectivePermissions()) {
            if (!info.getValue()) {
                continue;
            }
            final String perm = info.getPermission();
            if (perm.startsWith(DISCOUNT_PREFIX)) {
                try {
                    best = Math.max(best, Integer.parseInt(perm.substring(DISCOUNT_PREFIX.length())));
                } catch (NumberFormatException ignored) {
                    // malformed discount permission — skip
                }
            }
        }
        return Math.max(0.0, 1.0 - Math.min(100, best) / 100.0);
    }

    /**
     * Applies the player's permission discount to a base cost, returning the cost
     * actually shown and charged. Use this at the point a GUI obtains a {@link Cost}
     * so the displayed price and the deducted amount always match.
     */
    public @NotNull Cost effectiveCost(@NotNull Player player, @NotNull Cost base) {
        final double multiplier = discountMultiplier(player);
        if (multiplier >= 1.0) {
            return base;
        }
        return new Cost(base.type(), base.amount() * multiplier);
    }

    /**
     * @return whether the player can currently pay the cost. A cost in a currency
     *         whose plugin is absent is never affordable.
     */
    public boolean canAfford(@NotNull Player player, @NotNull Cost cost) {
        return switch (cost.type()) {
            case XP -> player.getLevel() >= cost.intAmount();
            case VAULT -> {
                var vault = plugin.getVaultHook();
                yield vault.isEnabled() && vault.getBalance(player) >= cost.amount();
            }
            case PLAYER_POINTS -> {
                var pp = plugin.getPlayerPointsHook();
                yield pp.isEnabled() && pp.getBalance(player) >= cost.intAmount();
            }
        };
    }

    /**
     * Charges the player for the cost. Returns {@code false} without taking
     * anything when the player cannot pay or the currency's plugin is absent.
     *
     * @return whether the charge succeeded
     */
    public boolean deduct(@NotNull Player player, @NotNull Cost cost) {
        return switch (cost.type()) {
            case XP -> {
                int required = cost.intAmount();
                if (player.getLevel() < required) {
                    yield false;
                }
                player.setLevel(player.getLevel() - required);
                yield true;
            }
            case VAULT -> {
                var vault = plugin.getVaultHook();
                yield vault.isEnabled() && vault.withdraw(player, cost.amount());
            }
            case PLAYER_POINTS -> {
                var pp = plugin.getPlayerPointsHook();
                yield pp.isEnabled() && pp.withdraw(player, cost.intAmount());
            }
        };
    }

    /**
     * Refunds (gives back) a previously-charged cost — e.g. Magia's "Reembolso
     * Arcano" returning a failed rung's cost. Best-effort: returns {@code false}
     * when the currency's plugin is absent.
     *
     * @return whether the refund was paid out
     */
    public boolean refund(@NotNull Player player, @NotNull Cost cost) {
        return switch (cost.type()) {
            case XP -> {
                player.setLevel(player.getLevel() + cost.intAmount());
                yield true;
            }
            case VAULT -> {
                var vault = plugin.getVaultHook();
                yield vault.isEnabled() && vault.deposit(player, cost.amount());
            }
            case PLAYER_POINTS -> {
                var pp = plugin.getPlayerPointsHook();
                yield pp.isEnabled() && pp.give(player, cost.intAmount());
            }
        };
    }

    /**
     * @return the player's current balance in the given currency, formatted the
     *         same way a {@link Cost} renders (e.g. {@code "12 XP"}, {@code "$1,500"})
     */
    public @NotNull String balanceText(@NotNull Player player, @NotNull CostType type) {
        return switch (type) {
            case XP -> player.getLevel() + " XP";
            case VAULT -> new Cost(CostType.VAULT, plugin.getVaultHook().getBalance(player)).displayText();
            case PLAYER_POINTS -> new Cost(CostType.PLAYER_POINTS,
                    plugin.getPlayerPointsHook().getBalance(player)).displayText();
        };
    }

    /**
     * @return the {@code messages.yml} key for the "you can't afford this" message
     *         under the given menu prefix (e.g. {@code "anvil"} → {@code "anvil.insufficient-money"})
     */
    public static @NotNull String insufficientKey(@NotNull String prefix, @NotNull CostType type) {
        String suffix = switch (type) {
            case VAULT -> "insufficient-money";
            case PLAYER_POINTS -> "insufficient-tokens";
            case XP -> "insufficient-xp";
        };
        return prefix + "." + suffix;
    }
}
