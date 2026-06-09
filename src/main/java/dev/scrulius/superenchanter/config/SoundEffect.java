/* SuperEnchanter - Author: Scrulius (GitHub) */
package dev.scrulius.superenchanter.config;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * A configured sound with its own volume and pitch, so every cue can be tuned
 * from {@code config.yml} instead of being hardcoded to {@code 1.0f / 1.0f}.
 *
 * @param sound  the resolved sound
 * @param volume playback volume (≥ 0)
 * @param pitch  playback pitch (0.5–2.0 in vanilla)
 */
public record SoundEffect(@NotNull Sound sound, float volume, float pitch) {

    /** Plays this sound to the player at their own location. */
    public void play(@NotNull Player player) {
        player.playSound(player.getLocation(), sound, volume, pitch);
    }
}
