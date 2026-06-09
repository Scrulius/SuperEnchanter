/* SuperEnchanter - Author: Scrulius (GitHub) */
package dev.scrulius.superenchanter.util;

import dev.scrulius.superenchanter.SuperEnchanterPlugin;
import dev.scrulius.superenchanter.config.PluginConfig;
import dev.scrulius.superenchanter.economy.Cost;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Append-only audit trail of every plugin operation (forge / enchant / transfer /
 * extract), written to {@code audit.log} in the plugin's data folder. It is a
 * <b>forensic / accountability</b> tool — a paper trail for staff to investigate
 * economy anomalies or feature abuse — <em>not</em> a dupe preventer or detector.
 * <p>
 * The log line is built on the calling (main) thread so all Bukkit access is safe;
 * only the file write is dispatched asynchronously, so disk I/O never stalls a tick.
 */
public final class AuditLog {

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SuperEnchanterPlugin plugin;
    private final File file;

    public AuditLog(@NotNull SuperEnchanterPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "audit.log");
    }

    /**
     * Records one operation. No-op when the audit log is disabled in config.
     *
     * @param player the acting player
     * @param action short action tag (e.g. {@code FORGE}, {@code TRANSFER})
     * @param detail human-readable detail (items, enchantments, …)
     * @param cost   the cost actually charged (already discounted)
     */
    public void record(@NotNull Player player, @NotNull String action,
                       @NotNull String detail, @NotNull Cost cost) {
        final PluginConfig config = plugin.getPluginConfig();
        if (!config.isAuditLogEnabled()) {
            return;
        }

        // Build the whole line on the main thread (Bukkit access must not go async).
        final String line = String.format("[%s] %-8s %s (%s) @ %s | %s | cost=%s",
                LocalDateTime.now().format(TIMESTAMP),
                action,
                player.getName(),
                player.getUniqueId(),
                describeLocation(player.getLocation()),
                detail,
                cost.displayText());

        if (config.isAuditLogToConsole()) {
            plugin.getLogger().info("[AUDIT] " + line);
        }

        // Disk write off-thread so a slow filesystem never costs a tick.
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> append(line));
    }

    private synchronized void append(@NotNull String line) {
        try {
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            try (BufferedWriter writer = Files.newBufferedWriter(file.toPath(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                writer.write(line);
                writer.newLine();
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to write audit log: " + e.getMessage());
        }
    }

    /**
     * Reads the last {@code maxLines} entries from the audit log, newest last,
     * optionally keeping only lines that mention {@code playerFilter} (case-insensitive).
     * Returns an empty list if the file does not exist yet.
     * <p>
     * Intended for the {@code /se audit} admin command; reads the whole file, so it
     * is a manual, low-frequency operation, not something on a hot path.
     *
     * @param maxLines     how many of the most recent matching lines to return
     * @param playerFilter a player-name substring to filter on, or {@code null} for all
     * @return the matching tail lines (oldest first within the returned window)
     */
    @NotNull
    public synchronized java.util.List<String> tail(int maxLines, @Nullable String playerFilter) {
        if (!file.exists()) {
            return java.util.List.of();
        }
        final java.util.List<String> all;
        try {
            all = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to read audit log: " + e.getMessage());
            return java.util.List.of();
        }
        final String needle = playerFilter == null ? null
                : playerFilter.toLowerCase(java.util.Locale.ROOT);
        final java.util.List<String> matched = new java.util.ArrayList<>();
        for (String line : all) {
            if (needle == null || line.toLowerCase(java.util.Locale.ROOT).contains(needle)) {
                matched.add(line);
            }
        }
        final int from = Math.max(0, matched.size() - Math.max(1, maxLines));
        return java.util.List.copyOf(matched.subList(from, matched.size()));
    }

    // ── Descriptors ───────────────────────────────────────────────────────────

    /**
     * Compact one-line item descriptor, e.g. {@code DIAMOND_SWORD{sharpness=5,unbreaking=3}}.
     */
    @NotNull
    public static String describe(@Nullable ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return "none";
        }
        final Map<Enchantment, Integer> enchants = EnchantmentHelper.getEnchantments(item);
        if (enchants.isEmpty()) {
            return item.getType().name();
        }
        final StringJoiner joiner = new StringJoiner(",", "{", "}");
        enchants.forEach((ench, level) -> joiner.add(ench.getKey().getKey() + "=" + level));
        return item.getType().name() + joiner;
    }

    private static String describeLocation(@NotNull Location loc) {
        return (loc.getWorld() == null ? "?" : loc.getWorld().getName())
                + " " + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }
}
