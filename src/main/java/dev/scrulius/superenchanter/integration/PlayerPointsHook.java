/* SuperEnchanter - Author: Scrulius (GitHub) */
package dev.scrulius.superenchanter.integration;

import org.black_ixx.playerpoints.PlayerPoints;
import org.black_ixx.playerpoints.PlayerPointsAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Soft integration hook for the PlayerPoints token economy.
 * <p>
 * Locates the PlayerPoints plugin and obtains its API instance. All methods
 * return safe defaults when the hook is disabled.
 * </p>
 */
public final class PlayerPointsHook {

    private final boolean enabled;
    @Nullable
    private final PlayerPointsAPI api;

    /**
     * Initializes the hook by checking for the PlayerPoints plugin and obtaining its API.
     */
    public PlayerPointsHook() {
        PlayerPointsAPI foundApi = null;
        boolean found = false;

        try {
            Plugin ppPlugin = Bukkit.getPluginManager().getPlugin("PlayerPoints");
            if (ppPlugin instanceof PlayerPoints playerPoints && ppPlugin.isEnabled()) {
                foundApi = playerPoints.getAPI();
                found = true;
            }
        } catch (Exception | NoClassDefFoundError e) {
            Bukkit.getLogger().warning("[SuperEnchanter] Failed to hook into PlayerPoints: " + e.getMessage());
        }

        this.api = foundApi;
        this.enabled = found;
    }

    /**
     * Returns whether the PlayerPoints integration is active.
     *
     * @return {@code true} if the PlayerPoints API was found
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Returns the player's PlayerPoints token balance.
     *
     * @param player the player to query
     * @return the token balance, or {@code 0} if the hook is disabled
     */
    public int getBalance(@NotNull Player player) {
        if (!enabled || api == null) {
            return 0;
        }
        return api.look(player.getUniqueId());
    }

    /**
     * Withdraws the specified amount of tokens from the player.
     *
     * @param player the player to withdraw from
     * @param amount the number of tokens to withdraw
     * @return {@code true} if the withdrawal was successful
     */
    public boolean withdraw(@NotNull Player player, int amount) {
        if (!enabled || api == null) {
            return false;
        }
        return api.take(player.getUniqueId(), amount);
    }

    /**
     * Gives the specified amount of tokens to the player (e.g. a refund).
     *
     * @param player the player to pay
     * @param amount the number of tokens to give
     * @return {@code true} if the transaction was successful
     */
    public boolean give(@NotNull Player player, int amount) {
        if (!enabled || api == null) {
            return false;
        }
        return api.give(player.getUniqueId(), amount);
    }
}
