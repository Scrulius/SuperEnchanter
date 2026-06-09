/* SuperEnchanter - Author: Scrulius (GitHub) */
package dev.scrulius.superenchanter.config;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

/**
 * A configured particle burst: type, amount, spread and speed, plus an on/off
 * toggle. Lets every visual effect be tuned (or disabled) from {@code config.yml}.
 *
 * @param enabled  whether the effect is shown at all
 * @param particle the particle type
 * @param count    how many particles to spawn
 * @param offsetX  spread on X
 * @param offsetY  spread on Y
 * @param offsetZ  spread on Z
 * @param speed    particle speed / data
 */
public record ParticleEffect(boolean enabled, @NotNull Particle particle, int count,
                             double offsetX, double offsetY, double offsetZ, double speed) {

    /** Spawns the burst at the given location, unless disabled or empty. */
    public void spawn(@NotNull World world, @NotNull Location location) {
        if (enabled && count > 0) {
            world.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, speed);
        }
    }
}
