/* SuperEnchanter - Author: Scrulius (GitHub) */
package dev.scrulius.superenchanter.config;

import dev.scrulius.superenchanter.util.MiniMessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Loads and provides access to all user-facing messages from {@code messages.yml}.
 * <p>
 * Every visible text string in the plugin is stored here so that server administrators
 * can fully customise the look and feel without touching Java code.
 * </p>
 *
 * @author Scrulius (GitHub)
 */
public final class MessagesConfig {

    private final JavaPlugin plugin;
    private FileConfiguration config;

    /**
     * Creates a new MessagesConfig, saving the default messages.yml and loading it.
     *
     * @param plugin the owning plugin
     */
    public MessagesConfig(@NotNull JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    /**
     * Reloads messages.yml from disk.
     */
    public void reload() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        // Only write the bundled default when missing; saveResource(...) logs a noisy
        // WARN on every reload otherwise. New keys are merged in by ConfigUpdater below.
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        this.config = YamlConfiguration.loadConfiguration(file);
        dev.scrulius.superenchanter.util.ConfigUpdater.merge(plugin, "messages.yml", file, config);
    }

    // ────────────────────────────────────────────────────────────
    //  Raw string access
    // ────────────────────────────────────────────────────────────

    /**
     * Checks if a key exists in the configuration.
     *
     * @param key the message key
     * @return true if the key exists
     */
    public boolean hasKey(@NotNull String key) {
        return config.contains(key);
    }

    /**
     * Returns the raw MiniMessage string for the given key.
     *
     * @param key the dot-separated config key (e.g. {@code "anvil.gui-title"})
     * @return the raw string, or a fallback if missing
     */
    public @NotNull String raw(@NotNull String key) {
        String value = config.getString(key);
        if (value == null) {
            plugin.getLogger().warning("Missing message key: " + key);
            return "<#FF6B6B>Missing: " + key;
        }
        return value;
    }

    /**
     * Returns a list of raw MiniMessage strings for the given key.
     *
     * @param key the dot-separated config key
     * @return the string list, or an empty list if missing
     */
    public @NotNull List<String> rawList(@NotNull String key) {
        if (!config.contains(key)) {
            plugin.getLogger().warning("Missing message list key: " + key);
            return Collections.emptyList();
        }
        return config.getStringList(key);
    }

    // ────────────────────────────────────────────────────────────
    //  Parsed Component access
    // ────────────────────────────────────────────────────────────

    /**
     * Parses the message at the given key into an Adventure {@link Component}.
     *
     * @param key the message key
     * @return the parsed component
     */
    public @NotNull Component parsed(@NotNull String key) {
        return MiniMessageUtil.parse(raw(key));
    }

    /**
     * Parses the message at the given key, substituting the provided placeholders.
     *
     * @param key          the message key
     * @param placeholders map of {@code {placeholder}} keys to their values
     * @return the parsed component with placeholders replaced
     */
    public @NotNull Component parsed(@NotNull String key, @NotNull Map<String, String> placeholders) {
        return MiniMessageUtil.parse(raw(key), placeholders);
    }

    // ────────────────────────────────────────────────────────────
    //  Formatted string access (for lore building)
    // ────────────────────────────────────────────────────────────

    /**
     * Returns the raw string with placeholders substituted (not parsed to Component).
     * Useful for building lore lists that get parsed later.
     *
     * @param key          the message key
     * @param placeholders map of placeholder keys to values
     * @return the substituted string
     */
    public @NotNull String format(@NotNull String key, @NotNull Map<String, String> placeholders) {
        String result = raw(key);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }

    /**
     * Returns a list of raw strings with placeholders substituted.
     *
     * @param key          the message key
     * @param placeholders map of placeholder keys to values
     * @return the list with placeholders replaced in each line
     */
    public @NotNull List<String> formatList(@NotNull String key, @NotNull Map<String, String> placeholders) {
        return rawList(key).stream()
                .map(line -> {
                    String result = line;
                    for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                        result = result.replace(entry.getKey(), entry.getValue());
                    }
                    return result;
                })
                .toList();
    }
}
