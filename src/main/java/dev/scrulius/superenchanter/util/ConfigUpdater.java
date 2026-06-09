/* SuperEnchanter - Author: Scrulius (GitHub) */
package dev.scrulius.superenchanter.util;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Lightweight config migrator: merges any keys present in the plugin's bundled
 * default resource but missing from the on-disk file, preserving the user's
 * existing values (and copying the comment for each newly-added key).
 * <p>
 * This solves the classic "added a new option in an update but existing servers
 * don't get it" problem. Only leaf values are copied (parent sections are created
 * implicitly), and the file is rewritten only when something actually changed.
 * </p>
 */
public final class ConfigUpdater {

    private ConfigUpdater() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Merges missing keys from the bundled resource into the on-disk config.
     *
     * @param plugin       the owning plugin (source of the bundled resource)
     * @param resourceName the resource/file name, e.g. {@code "config.yml"}
     * @param file         the on-disk file to update
     * @param current      the loaded configuration to merge into (mutated in place)
     * @return the number of keys added
     */
    public static int merge(@NotNull Plugin plugin, @NotNull String resourceName,
                            @NotNull File file, @NotNull FileConfiguration current) {
        InputStream in = plugin.getResource(resourceName);
        if (in == null) {
            return 0;
        }

        final YamlConfiguration defaults;
        try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            defaults = YamlConfiguration.loadConfiguration(reader);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not read bundled " + resourceName + ": " + e.getMessage());
            return 0;
        }

        int added = 0;
        for (String key : defaults.getKeys(true)) {
            // Skip parent sections — setting a missing leaf creates its parents.
            if (defaults.isConfigurationSection(key)) {
                continue;
            }
            // contains(path, true) ignores inherited defaults, so we only add truly-missing keys.
            if (!current.contains(key, true)) {
                current.set(key, defaults.get(key));
                current.setComments(key, defaults.getComments(key));
                added++;
            }
        }

        if (added > 0) {
            try {
                current.save(file);
                plugin.getLogger().info("Updated " + resourceName + ": added " + added + " new key(s).");
            } catch (IOException e) {
                plugin.getLogger().warning("Could not save merged " + resourceName + ": " + e.getMessage());
            }
        }
        return added;
    }
}
