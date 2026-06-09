/* SuperEnchanter - Author: Scrulius (GitHub) */
package dev.scrulius.superenchanter.gui.audit;

import dev.scrulius.superenchanter.SuperEnchanterPlugin;
import dev.scrulius.superenchanter.gui.AbstractCustomGUI;
import dev.scrulius.superenchanter.util.AuditEntry;
import dev.scrulius.superenchanter.util.AuditLog;
import dev.scrulius.superenchanter.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Read-only, paginated viewer for the structured audit trail ({@code /se audit}).
 * <p>
 * Each operation is one icon you can hover to see the full detail — timestamp,
 * actor, location, the <b>items involved</b> (with their custom names, MythicMobs
 * id and enchantments) and the cost charged. There are no input slots, so the
 * anti-dupe base ({@link AbstractCustomGUI}) cancels every click; only the
 * navigation buttons do anything.
 * <pre>
 * Rows 0-4 (slots 0-44): one icon per audit entry, newest first.
 * Row 5: [48] prev · [49] page info · [50] next · [53] close.
 * </pre>
 */
public final class AuditGUI extends AbstractCustomGUI {

    private static final int PER_PAGE = 45;
    private static final int SLOT_PREV = 48;
    private static final int SLOT_INFO = 49;
    private static final int SLOT_NEXT = 50;
    private static final int SLOT_CLOSE = 53;

    private static final ItemStack FRAME = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE)
            .name(" ").build();

    /** Per-action icon + accent colour, so each operation type reads at a glance. */
    private record ActionStyle(Material icon, String color, String label) {
    }

    private static @NotNull ActionStyle styleFor(@Nullable String action) {
        if (action == null) {
            return new ActionStyle(Material.PAPER, "#A8B2D1", "?");
        }
        return switch (action) {
            case "FORGE" -> new ActionStyle(Material.ANVIL, "#FFB347", "Forja");
            case "ENCHANT" -> new ActionStyle(Material.ENCHANTING_TABLE, "#7FD6FF", "Encantar");
            case "ENCHANT-X" -> new ActionStyle(Material.FIRE_CHARGE, "#FF6B6B", "Encantar (fallo)");
            case "ENCHANT-CURSE" -> new ActionStyle(Material.WITHER_SKELETON_SKULL, "#B967FF", "Encantar (maldición)");
            case "TRANSFER" -> new ActionStyle(Material.GRINDSTONE, "#6BCB77", "Transferir");
            case "EXTRACT" -> new ActionStyle(Material.ENCHANTED_BOOK, "#4D96FF", "Extraer");
            case "PURIFY" -> new ActionStyle(Material.HONEY_BOTTLE, "#FFD93D", "Purificar");
            default -> new ActionStyle(Material.PAPER, "#A8B2D1", action);
        };
    }

    private final List<AuditEntry> entries;
    private final String filterLabel;
    private int page;

    /**
     * @param plugin  the owning plugin
     * @param player  the staff member viewing the log
     * @param entries the entries to show, newest first
     * @param filter  the player filter applied, or {@code null} for all
     */
    public AuditGUI(@NotNull SuperEnchanterPlugin plugin, @NotNull Player player,
                    @NotNull List<AuditEntry> entries, @Nullable String filter) {
        super(plugin, player, plugin.getMessages().parsed("command.audit-title"));
        this.entries = entries;
        this.filterLabel = filter == null ? plugin.getMessages().raw("command.audit-filter-all") : filter;
        fillDecoration();
    }

    @Override
    @NotNull
    protected Set<Integer> getInputSlots() {
        return Set.of(); // read-only viewer: no item slots
    }

    @Override
    protected void fillDecoration() {
        render();
    }

    /** (Re)draws the current page. */
    private void render() {
        inventory.clear();
        // bottom-row frame
        for (int i = 45; i < 54; i++) {
            inventory.setItem(i, FRAME);
        }

        final int totalPages = Math.max(1, (entries.size() + PER_PAGE - 1) / PER_PAGE);
        page = Math.max(0, Math.min(page, totalPages - 1));
        final int start = page * PER_PAGE;
        final int end = Math.min(entries.size(), start + PER_PAGE);

        for (int i = start; i < end; i++) {
            inventory.setItem(i - start, iconFor(entries.get(i)));
        }

        if (page > 0) {
            inventory.setItem(SLOT_PREV, new ItemBuilder(Material.ARROW)
                    .name(msg.raw("command.audit-prev")).build());
        }
        if (page < totalPages - 1) {
            inventory.setItem(SLOT_NEXT, new ItemBuilder(Material.ARROW)
                    .name(msg.raw("command.audit-next")).build());
        }
        inventory.setItem(SLOT_INFO, new ItemBuilder(Material.BOOK)
                .name(msg.format("command.audit-info-name",
                        Map.of("{page}", String.valueOf(page + 1), "{pages}", String.valueOf(totalPages))))
                .lore(msg.formatList("command.audit-info-lore",
                        Map.of("{count}", String.valueOf(entries.size()), "{filter}", filterLabel)))
                .build());
        inventory.setItem(SLOT_CLOSE, new ItemBuilder(Material.BARRIER)
                .name(msg.raw("command.audit-close")).build());
    }

    /** Builds the hover-rich icon for one audit entry. */
    private @NotNull ItemStack iconFor(@NotNull AuditEntry entry) {
        final ActionStyle style = styleFor(entry.action);
        final AuditEntry.ItemSnapshot primary = primarySnapshot(entry);
        final Material iconMat = primary != null ? matOf(primary.material, style.icon()) : style.icon();

        final ItemBuilder b = new ItemBuilder(iconMat)
                .name("<" + style.color() + "><bold>" + style.label() + "</bold> <#6C7293>· <#A8B2D1>" + entry.player);

        final List<String> lore = new ArrayList<>();
        lore.add("<#6C7293>⏱ <#A8B2D1>" + AuditLog.formatTime(entry.time));
        lore.add("<#6C7293>👤 <#A8B2D1>" + entry.player);
        lore.add("<#6C7293>🌍 <#A8B2D1>" + entry.world + " " + entry.x + "," + entry.y + "," + entry.z);
        if (entry.summary != null && !entry.summary.isBlank()) {
            lore.add("<#6C7293>✎ <#A8B2D1>" + sanitize(entry.summary));
        }
        if (entry.items != null && !entry.items.isEmpty()) {
            lore.add(" ");
            lore.add("<" + style.color() + "><bold>Ítems</bold>");
            for (AuditEntry.ItemSnapshot s : entry.items) {
                lore.add("<#6C7293>" + roleLabel(s.role) + ": <reset>" + itemTitle(s)
                        + (s.amount > 1 ? " <#6C7293>x" + s.amount : ""));
                if (s.mythicId != null) {
                    lore.add("  <#6C7293>MM: <#A8B2D1>" + sanitize(s.mythicId));
                }
                if (s.enchants != null) {
                    s.enchants.forEach((key, lvl) ->
                            lore.add("  <#6C7293>• <#8A93B8>" + AuditLog.prettyEnchantKey(key) + " " + lvl));
                }
            }
        }
        lore.add(" ");
        lore.add("<#6C7293>💰 <#A8B2D1>" + sanitize(entry.costText));

        b.lore(lore).hideFlags();
        if (primary != null && primary.enchants != null && !primary.enchants.isEmpty()) {
            b.glow();
        }
        return b.build();
    }

    /** The item whose material best represents the operation (result/item/book), else the first. */
    private @Nullable AuditEntry.ItemSnapshot primarySnapshot(@NotNull AuditEntry entry) {
        if (entry.items == null || entry.items.isEmpty()) {
            return null;
        }
        for (String preferred : new String[]{"result", "item", "book"}) {
            for (AuditEntry.ItemSnapshot s : entry.items) {
                if (preferred.equals(s.role)) {
                    return s;
                }
            }
        }
        return entry.items.get(0);
    }

    /** A faithful display title for a snapshot: its custom name if any, else the prettified material. */
    private @NotNull String itemTitle(@NotNull AuditEntry.ItemSnapshot s) {
        if (s.name != null && !s.name.isBlank()) {
            return s.name; // already a MiniMessage string
        }
        return "<white>" + AuditLog.prettyEnchantKey(s.material.toLowerCase(java.util.Locale.ROOT));
    }

    private static @NotNull String roleLabel(@Nullable String role) {
        if (role == null) {
            return "ítem";
        }
        return switch (role) {
            case "result" -> "Resultado";
            case "sacrifice" -> "Sacrificio";
            case "donor" -> "Donante";
            case "book" -> "Libro";
            case "item" -> "Ítem";
            default -> role;
        };
    }

    private static @NotNull Material matOf(@Nullable String name, @NotNull Material fallback) {
        if (name == null) {
            return fallback;
        }
        final Material m = Material.getMaterial(name);
        return m == null ? fallback : m;
    }

    /** Escapes stray MiniMessage tag openers in dynamic text so '<' isn't read as a tag. */
    private static @NotNull String sanitize(@Nullable String raw) {
        return raw == null ? "" : raw.replace("<", "\\<");
    }

    @Override
    protected void onSlotClick(@NotNull Player player, int slot, @NotNull InventoryClickEvent event) {
        switch (slot) {
            case SLOT_PREV -> {
                if (page > 0) {
                    page--;
                    plugin.getPluginConfig().getButtonClickSound().play(player);
                    render();
                }
            }
            case SLOT_NEXT -> {
                page++;
                plugin.getPluginConfig().getButtonClickSound().play(player);
                render();
            }
            case SLOT_CLOSE -> Bukkit.getScheduler().runTask(plugin, () -> player.closeInventory());
            default -> {
                // Entry icons are display-only.
            }
        }
    }

    @Override
    protected void updatePreview() {
        // No input slots — nothing to preview.
    }
}
