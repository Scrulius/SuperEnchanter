/* SuperEnchanter - Author: Scrulius (GitHub) */
package dev.scrulius.superenchanter.integration;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Soft integration hook for the Vault economy system.
 * <p>
 * Attempts to locate an {@link Economy} provider via Bukkit's
 * {@link org.bukkit.plugin.ServicesManager}. All methods return safe
 * defaults when the hook is disabled.
 * </p>
 */
public final class VaultHook {

    private final boolean enabled;
    @Nullable
    private final Economy economy;

    /**
     * Initializes the hook by querying the services manager for an Economy provider.
     */
    public VaultHook() {
        Economy foundEconomy = null;
        boolean found = false;

        if (Bukkit.getPluginManager().isPluginEnabled("Vault")) {
            try {
                RegisteredServiceProvider<Economy> rsp =
                        Bukkit.getServicesManager().getRegistration(Economy.class);
                if (rsp != null) {
                    foundEconomy = rsp.getProvider();
                    found = true;
                }
            } catch (Exception e) {
                Bukkit.getLogger().warning("[SuperEnchanter] Failed to hook into Vault Economy: " + e.getMessage());
            }
        }

        this.economy = foundEconomy;
        this.enabled = found;
    }

    /**
     * Returns whether the Vault economy integration is active.
     *
     * @return {@code true} if an Economy provider was found
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Returns the player's balance in the Vault economy.
     *
     * @param player the player to query
     * @return the balance, or {@code 0.0} if the hook is disabled
     */
    public double getBalance(@NotNull Player player) {
        if (!enabled || economy == null) {
            return 0.0;
        }
        return economy.getBalance(player);
    }

    /**
     * Withdraws the specified amount from the player's Vault account.
     *
     * @param player the player to withdraw from
     * @param amount the amount to withdraw
     * @return {@code true} if the transaction was successful
     */
    public boolean withdraw(@NotNull Player player, double amount) {
        if (!enabled || economy == null) {
            return false;
        }
        EconomyResponse response = economy.withdrawPlayer(player, amount);
        return response.transactionSuccess();
    }

    /**
     * Deposits the specified amount into the player's Vault account (e.g. a refund).
     *
     * @param player the player to pay
     * @param amount the amount to deposit
     * @return {@code true} if the transaction was successful
     */
    public boolean deposit(@NotNull Player player, double amount) {
        if (!enabled || economy == null) {
            return false;
        }
        EconomyResponse response = economy.depositPlayer(player, amount);
        return response.transactionSuccess();
    }

    /**
     * Formats a currency amount according to the economy provider's format.
     *
     * @param amount the amount to format
     * @return the formatted string, or {@code "$0"} if the hook is disabled
     */
    public @NotNull String format(double amount) {
        if (!enabled || economy == null) {
            return "$0";
        }
        return economy.format(amount);
    }
}
