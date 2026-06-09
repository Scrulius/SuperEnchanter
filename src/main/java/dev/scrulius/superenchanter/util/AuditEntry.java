/* SuperEnchanter - Author: Scrulius (GitHub) */
package dev.scrulius.superenchanter.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One structured audit record, persisted as a single JSON line in {@code audit.jsonl}.
 * <p>
 * Replaces the old free-text audit line: storing structured fields (and an
 * <b>enriched item descriptor</b> per item involved) lets the {@code /se audit}
 * GUI rebuild faithful icons — material, custom name, MythicMobs id and the
 * enchantment list — so staff can hover an operation and see exactly what items
 * were involved, instead of squinting at a log line.
 * <p>
 * Plain fields only (no Bukkit types) so it serialises cleanly with Gson and can
 * be read off the main thread. All Bukkit reads happen when the snapshot is
 * <em>built</em> (on the main thread, in {@link AuditLog#snap}).
 */
public final class AuditEntry {

    /** Epoch millis when the operation happened. */
    public long time;
    /** Short action tag: {@code FORGE}, {@code ENCHANT}, {@code ENCHANT-X}, {@code ENCHANT-CURSE}, {@code TRANSFER}, {@code EXTRACT}, {@code PURIFY}. */
    public String action;
    /** Acting player's name. */
    public String player;
    /** Acting player's UUID (string form). */
    public String uuid;
    /** World name where it happened. */
    public String world;
    public int x;
    public int y;
    public int z;
    /** Currency the cost was charged in ({@code XP}/{@code VAULT}/{@code PLAYER_POINTS}). */
    public String costType;
    /** Numeric cost amount. */
    public double costAmount;
    /** Pre-formatted cost text (e.g. {@code "39 XP"}), so the GUI never re-derives it. */
    public String costText;
    /** Short human summary of the operation (e.g. {@code "sharpness 0 → 5"}). May be empty. */
    public String summary;
    /** The items involved, each tagged with a role. */
    public List<ItemSnapshot> items = new ArrayList<>();

    /**
     * Enriched, Gson-friendly snapshot of one {@link org.bukkit.inventory.ItemStack}
     * involved in an operation. Stores enough to rebuild a faithful display icon
     * without serialising the full NBT.
     */
    public static final class ItemSnapshot {
        /** Role of this item in the operation: {@code result}, {@code sacrifice}, {@code donor}, {@code item}, {@code book}. */
        public String role;
        /** {@link org.bukkit.Material} name. */
        public String material;
        /** Stack size. */
        public int amount;
        /** Custom display name as a MiniMessage string, or {@code null} if the item has no custom name. */
        @Nullable public String name;
        /** MythicMobs internal id, or {@code null} if not a MythicMobs item. */
        @Nullable public String mythicId;
        /** Custom-model-data value, or {@code null} if none. */
        @Nullable public Integer customModelData;
        /** Map of enchantment key ({@code namespace:path}) to level. */
        public Map<String, Integer> enchants;

        public ItemSnapshot() {
        }

        public ItemSnapshot(@NotNull String role, @NotNull String material, int amount,
                            @Nullable String name, @Nullable String mythicId,
                            @Nullable Integer customModelData, @NotNull Map<String, Integer> enchants) {
            this.role = role;
            this.material = material;
            this.amount = amount;
            this.name = name;
            this.mythicId = mythicId;
            this.customModelData = customModelData;
            this.enchants = enchants;
        }
    }
}
