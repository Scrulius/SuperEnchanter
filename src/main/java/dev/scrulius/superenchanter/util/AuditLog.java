/* SuperEnchanter - Author: Scrulius (GitHub) */
package dev.scrulius.superenchanter.util;

import com.google.gson.Gson;
import dev.scrulius.superenchanter.SuperEnchanterPlugin;
import dev.scrulius.superenchanter.config.PluginConfig;
import dev.scrulius.superenchanter.economy.Cost;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Structured, append-only audit trail of every plugin operation (forge / enchant /
 * transfer / extract / purify). Each operation is one JSON line in
 * {@code audit.jsonl} — a <b>forensic / accountability</b> tool (a paper trail for
 * staff to investigate economy anomalies or feature abuse), <em>not</em> a dupe
 * preventer.
 * <p>
 * Records are {@link AuditEntry} objects with an <b>enriched item descriptor</b>
 * per item involved, so the {@code /se audit} GUI can rebuild faithful icons.
 * The whole entry is built on the calling (main) thread — all Bukkit reads happen
 * in {@link #snap} — and only the Gson serialisation + disk write run async, so
 * I/O never stalls a tick. The file is rotated when it exceeds a configurable size.
 */
public final class AuditLog {

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    /** Hard cap on how many recent entries the GUI will load into memory. */
    public static final int MAX_LOAD = 1000;

    private static final Gson GSON = new Gson();
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final SuperEnchanterPlugin plugin;
    private final File file;

    public AuditLog(@NotNull SuperEnchanterPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "audit.jsonl");
    }

    /**
     * Builds an enriched snapshot of an item for an audit record. <b>Must be called
     * on the main thread</b> — it reads item meta. Returns {@code null} for an
     * empty/air item (callers can pass it straight through; nulls are skipped).
     *
     * @param role short role tag ({@code result}, {@code sacrifice}, {@code donor}, …)
     * @param item the item to snapshot (not mutated)
     * @return the snapshot, or {@code null} if the item is empty
     */
    @SuppressWarnings("deprecation")
    public @Nullable AuditEntry.ItemSnapshot snap(@NotNull String role, @Nullable ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }
        String name = null;
        Integer cmd = null;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            Component display = meta.displayName();
            if (display != null) {
                name = MM.serialize(display);
            }
            try {
                if (meta.hasCustomModelData()) {
                    cmd = meta.getCustomModelData();
                }
            } catch (Throwable ignored) {
                // CMD read is best-effort; ignore any API hiccup.
            }
        }
        String mythicId = plugin.getMythicMobsHook().getItemId(item);

        Map<String, Integer> enchants = new LinkedHashMap<>();
        EnchantmentHelper.getEnchantments(item).forEach((ench, lvl) ->
                enchants.put(ench.getKey().toString(), lvl));

        return new AuditEntry.ItemSnapshot(role, item.getType().name(), item.getAmount(),
                name, mythicId, cmd, enchants);
    }

    /**
     * Records one operation. No-op when the audit log is disabled in config.
     * <p>
     * The whole {@link AuditEntry} is assembled on the calling thread; only the
     * Gson encode + disk write are dispatched async.
     *
     * @param player  the acting player
     * @param action  short action tag (e.g. {@code FORGE}, {@code TRANSFER})
     * @param summary short human description (may be empty)
     * @param cost    the cost actually charged (already discounted)
     * @param items   the involved item snapshots (nulls are skipped)
     */
    public void record(@NotNull Player player, @NotNull String action, @NotNull String summary,
                       @NotNull Cost cost, @Nullable AuditEntry.ItemSnapshot... items) {
        final PluginConfig config = plugin.getPluginConfig();
        if (!config.isAuditLogEnabled()) {
            return;
        }

        final AuditEntry entry = new AuditEntry();
        entry.time = System.currentTimeMillis();
        entry.action = action;
        entry.player = player.getName();
        entry.uuid = player.getUniqueId().toString();
        final Location loc = player.getLocation();
        entry.world = loc.getWorld() == null ? "?" : loc.getWorld().getName();
        entry.x = loc.getBlockX();
        entry.y = loc.getBlockY();
        entry.z = loc.getBlockZ();
        entry.costType = cost.type().name();
        entry.costAmount = cost.amount();
        entry.costText = cost.displayText();
        entry.summary = summary;
        if (items != null) {
            for (AuditEntry.ItemSnapshot snap : items) {
                if (snap != null) {
                    entry.items.add(snap);
                }
            }
        }

        if (config.isAuditLogToConsole()) {
            plugin.getLogger().info("[AUDIT] " + formatConsole(entry));
        }

        final String line = GSON.toJson(entry);
        final long maxBytes = (long) config.getAuditMaxFileKb() * 1024L;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> append(line, maxBytes));
    }

    private synchronized void append(@NotNull String line, long maxBytes) {
        try {
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            rotateIfNeeded(maxBytes);
            try (BufferedWriter writer = Files.newBufferedWriter(file.toPath(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                writer.write(line);
                writer.newLine();
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to write audit log: " + e.getMessage());
        }
    }

    /** Rotates {@code audit.jsonl} to {@code audit.jsonl.1} once it grows past the cap. */
    private void rotateIfNeeded(long maxBytes) {
        if (maxBytes <= 0 || !file.exists() || file.length() < maxBytes) {
            return;
        }
        final File backup = new File(file.getParentFile(), file.getName() + ".1");
        try {
            Files.deleteIfExists(backup.toPath());
            Files.move(file.toPath(), backup.toPath());
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to rotate audit log: " + e.getMessage());
        }
    }

    /**
     * Reads the most recent audit entries (newest first), optionally keeping only
     * those whose player name or UUID contains {@code playerFilter}. Caps the result
     * at {@link #MAX_LOAD}. Returns an empty list when the file does not exist yet.
     * <p>
     * Reads the whole current file (bounded by rotation), so it is a manual,
     * low-frequency operation for the {@code /se audit} GUI, not a hot path.
     *
     * @param playerFilter a player-name/UUID substring to filter on, or {@code null} for all
     * @return matching entries, newest first
     */
    @NotNull
    public synchronized List<AuditEntry> readRecent(@Nullable String playerFilter) {
        if (!file.exists()) {
            return List.of();
        }
        final List<String> all;
        try {
            all = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to read audit log: " + e.getMessage());
            return List.of();
        }
        final String needle = playerFilter == null ? null : playerFilter.toLowerCase(Locale.ROOT);
        final List<AuditEntry> matched = new ArrayList<>();
        // Walk newest → oldest, stopping once we have enough.
        for (int i = all.size() - 1; i >= 0 && matched.size() < MAX_LOAD; i--) {
            final String raw = all.get(i);
            if (raw == null || raw.isBlank()) {
                continue;
            }
            final AuditEntry entry;
            try {
                entry = GSON.fromJson(raw, AuditEntry.class);
            } catch (Exception malformed) {
                continue; // skip a corrupt line rather than fail the whole view
            }
            if (entry == null || entry.action == null) {
                continue;
            }
            if (needle != null) {
                final String p = entry.player == null ? "" : entry.player.toLowerCase(Locale.ROOT);
                final String u = entry.uuid == null ? "" : entry.uuid.toLowerCase(Locale.ROOT);
                if (!p.contains(needle) && !u.contains(needle)) {
                    continue;
                }
            }
            matched.add(entry);
        }
        return matched;
    }

    // ── Formatting ──────────────────────────────────────────────────────────

    /** Formats an entry as a compact one-line string for the server console. */
    @NotNull
    public static String formatConsole(@NotNull AuditEntry entry) {
        final StringBuilder items = new StringBuilder();
        for (AuditEntry.ItemSnapshot s : entry.items) {
            if (items.length() > 0) {
                items.append(' ');
            }
            items.append(s.role).append('=').append(describe(s));
        }
        return String.format("[%s] %-12s %s (%s) @ %s %d,%d,%d | %s%s | cost=%s",
                TIMESTAMP.format(Instant.ofEpochMilli(entry.time)),
                entry.action,
                entry.player,
                entry.uuid,
                entry.world, entry.x, entry.y, entry.z,
                entry.summary == null || entry.summary.isBlank() ? "" : entry.summary + " ",
                items,
                entry.costText);
    }

    /** Compact one-line item descriptor, e.g. {@code DIAMOND_SWORD{sharpness=5}}. */
    @NotNull
    public static String describe(@NotNull AuditEntry.ItemSnapshot s) {
        if (s.enchants == null || s.enchants.isEmpty()) {
            return s.material;
        }
        final StringBuilder sb = new StringBuilder(s.material).append('{');
        boolean first = true;
        for (Map.Entry<String, Integer> e : s.enchants.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            // strip the namespace for brevity in the console line
            final String key = e.getKey();
            final int colon = key.indexOf(':');
            sb.append(colon >= 0 ? key.substring(colon + 1) : key).append('=').append(e.getValue());
            first = false;
        }
        return sb.append('}').toString();
    }

    /** @return the formatted timestamp for an entry (for the GUI lore). */
    @NotNull
    public static String formatTime(long epochMillis) {
        return TIMESTAMP.format(Instant.ofEpochMilli(epochMillis));
    }

    /** Pretty-prints an enchantment key path (e.g. {@code fire_aspect} → {@code Fire Aspect}). */
    @NotNull
    public static String prettyEnchantKey(@NotNull String key) {
        final int colon = key.indexOf(':');
        final String path = colon >= 0 ? key.substring(colon + 1) : key;
        final String[] words = path.split("_");
        final StringBuilder sb = new StringBuilder(path.length());
        for (String w : words) {
            if (w.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(w.charAt(0)));
            if (w.length() > 1) {
                sb.append(w.substring(1));
            }
        }
        return sb.toString();
    }
}
