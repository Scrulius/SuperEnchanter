/* SuperEnchanter - Author: Scrulius (GitHub) */
package dev.scrulius.superenchanter.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe cooldown manager that tracks per-player cooldowns in server ticks.
 * <p>
 * Stores the tick at which each player last performed an action. A player is
 * considered "on cooldown" until {@code cooldownTicks} have elapsed since their
 * last recorded tick.
 * </p>
 */
public final class CooldownManager {

    private final ConcurrentHashMap<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private final long cooldownTicks;

    /**
     * Creates a new cooldown manager with the specified duration.
     *
     * @param cooldownTicks the number of ticks a cooldown lasts
     */
    public CooldownManager(long cooldownTicks) {
        this.cooldownTicks = cooldownTicks;
    }

    /**
     * Checks whether the given player is currently on cooldown.
     *
     * @param player the player to check
     * @return {@code true} if the player's cooldown has not yet expired
     */
    public boolean isOnCooldown(@NotNull Player player) {
        Long lastTick = cooldowns.get(player.getUniqueId());
        if (lastTick == null) {
            return false;
        }
        long currentTick = Bukkit.getCurrentTick();
        return (currentTick - lastTick) < cooldownTicks;
    }

    /**
     * Records the current server tick as the player's cooldown start.
     *
     * @param player the player to place on cooldown
     */
    public void setCooldown(@NotNull Player player) {
        cooldowns.put(player.getUniqueId(), (long) Bukkit.getCurrentTick());
    }

    /**
     * Removes the cooldown entry for the given player.
     *
     * @param player the player whose cooldown should be cleared
     */
    public void removeCooldown(@NotNull Player player) {
        cooldowns.remove(player.getUniqueId());
    }

    /**
     * Clears all stored cooldowns.
     */
    public void clearAll() {
        cooldowns.clear();
    }
}
