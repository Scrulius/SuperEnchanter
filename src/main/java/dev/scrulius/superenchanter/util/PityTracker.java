/* SuperEnchanter - Author: Scrulius (GitHub) */
package dev.scrulius.superenchanter.util;

import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Soft-pity bookkeeping for the enchanting table: the consecutive-failure streak
 * of each enchantment ladder, stored in the <b>item's own PDC</b> (no database —
 * the streak belongs to that item's ladder, survives restarts and travels with
 * the item). One string key holds every streak as {@code "ns:key=n;ns:key=n"};
 * a successful attempt clears that enchantment's entry, a failure increments it.
 * <p>
 * The string parsing/serialising is pure and unit-testable; only the thin PDC
 * accessors touch Bukkit.
 */
public final class PityTracker {

    /** PDC key (under the plugin's namespace) holding the serialized streak map. */
    private static final String PDC_KEY = "pity";

    private PityTracker() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    // ── Pure string codec ───────────────────────────────────────────────────

    /**
     * Parses a serialized streak map ({@code "minecraft:sharpness=2;eco:x=1"}).
     * Malformed entries are skipped; never returns {@code null}.
     *
     * @param raw the serialized form (may be {@code null} or empty)
     * @return a mutable map of lowercase enchantment key → streak (≥1)
     */
    @NotNull
    public static Map<String, Integer> parse(@Nullable String raw) {
        final Map<String, Integer> out = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) {
            return out;
        }
        for (String entry : raw.split(";")) {
            final int eq = entry.indexOf('=');
            if (eq <= 0 || eq == entry.length() - 1) {
                continue;
            }
            try {
                final int streak = Integer.parseInt(entry.substring(eq + 1).trim());
                if (streak > 0) {
                    out.put(entry.substring(0, eq).trim().toLowerCase(Locale.ROOT), streak);
                }
            } catch (NumberFormatException ignored) {
                // skip malformed entry
            }
        }
        return out;
    }

    /**
     * Serializes a streak map back to {@code "key=n;key=n"} form. Non-positive
     * streaks are dropped; an empty map serializes to an empty string.
     *
     * @param streaks the streak map
     * @return the serialized form ("" when nothing to store)
     */
    @NotNull
    public static String serialize(@NotNull Map<String, Integer> streaks) {
        final StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : streaks.entrySet()) {
            if (e.getValue() == null || e.getValue() <= 0) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(';');
            }
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        return sb.toString();
    }

    // ── PDC accessors ───────────────────────────────────────────────────────

    /**
     * The consecutive-failure streak stored on the item for an enchantment
     * (0 when none recorded).
     */
    public static int streak(@NotNull Plugin plugin, @Nullable ItemStack item,
                             @NotNull Enchantment enchantment) {
        if (item == null || !item.hasItemMeta()) {
            return 0;
        }
        final String raw = item.getItemMeta().getPersistentDataContainer()
                .get(key(plugin), PersistentDataType.STRING);
        return parse(raw).getOrDefault(enchantKey(enchantment), 0);
    }

    /**
     * Returns a copy of the item with the enchantment's failure streak
     * incremented by one.
     */
    @NotNull
    public static ItemStack incrementStreak(@NotNull Plugin plugin, @NotNull ItemStack item,
                                            @NotNull Enchantment enchantment) {
        return mutate(plugin, item, streaks ->
                streaks.merge(enchantKey(enchantment), 1, Integer::sum));
    }

    /**
     * Returns a copy of the item with the enchantment's failure streak cleared
     * (a success resets the pity). Removes the PDC key entirely when no streaks
     * remain, so a "clean" item carries no leftover tag.
     */
    @NotNull
    public static ItemStack clearStreak(@NotNull Plugin plugin, @NotNull ItemStack item,
                                        @NotNull Enchantment enchantment) {
        return mutate(plugin, item, streaks -> streaks.remove(enchantKey(enchantment)));
    }

    private static ItemStack mutate(@NotNull Plugin plugin, @NotNull ItemStack item,
                                    @NotNull java.util.function.Consumer<Map<String, Integer>> change) {
        final ItemStack copy = item.clone();
        final ItemMeta meta = copy.getItemMeta();
        if (meta == null) {
            return copy;
        }
        final NamespacedKey key = key(plugin);
        final Map<String, Integer> streaks =
                parse(meta.getPersistentDataContainer().get(key, PersistentDataType.STRING));
        change.accept(streaks);
        final String serialized = serialize(streaks);
        if (serialized.isEmpty()) {
            meta.getPersistentDataContainer().remove(key);
        } else {
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, serialized);
        }
        copy.setItemMeta(meta);
        return copy;
    }

    private static NamespacedKey key(@NotNull Plugin plugin) {
        return new NamespacedKey(plugin, PDC_KEY);
    }

    private static String enchantKey(@NotNull Enchantment enchantment) {
        return enchantment.getKey().asString().toLowerCase(Locale.ROOT);
    }
}
