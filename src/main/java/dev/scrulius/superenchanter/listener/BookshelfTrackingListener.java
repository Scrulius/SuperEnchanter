/* SuperEnchanter - Author: Scrulius (GitHub) */
package dev.scrulius.superenchanter.listener;

import dev.scrulius.superenchanter.SuperEnchanterPlugin;
import dev.scrulius.superenchanter.util.EnchantedBookshelfManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.ChiseledBookshelf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Implements "enchanted libraries": a configured MythicMobs item placed as a real
 * vanilla bookshelf is marked per-coordinate so the enchanting-table scanner can
 * grant it extra power, and is made indestructible by anything except mining (so
 * the mark can never desync and the item is never lost to TNT/fire/etc.).
 */
public final class BookshelfTrackingListener implements Listener {

    private final SuperEnchanterPlugin plugin;
    private final EnchantedBookshelfManager manager;

    public BookshelfTrackingListener(@NotNull SuperEnchanterPlugin plugin) {
        this.plugin = plugin;
        this.manager = plugin.getEnchantedBookshelfManager();
    }

    private static boolean isBookshelf(@NotNull Material material) {
        return material == Material.BOOKSHELF || material == Material.CHISELED_BOOKSHELF;
    }

    // ── Place: mark configured library bookshelves ──────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(@NotNull BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        // Cheap guard: only bookshelves can be libraries → skip the MM lookup otherwise.
        if (!isBookshelf(block.getType())) {
            return;
        }
        String id = plugin.getMythicMobsHook().getItemId(event.getItemInHand());
        if (id == null || plugin.getPluginConfig().getEnchantedBookshelfPower(id) == null) {
            return; // not a configured enchanted library
        }
        manager.mark(block, id);
        // Seal it next tick (after MythicMobs finishes its own placement handling):
        // a chiseled library is shown full of books that can't be taken out.
        Bukkit.getScheduler().runTask(plugin, () -> sealIfChiseled(block));
    }

    // ── Interact: keep sealed chiseled libraries un-lootable ────────────────

    /**
     * Keeps a marked chiseled-bookshelf library sealed: a non-sneaking right click
     * (the only gesture that manipulates books on a chiseled bookshelf) is
     * cancelled, so books can never be taken or added. Sneaking is left alone —
     * that's the vanilla way to place a block against an interactable block, which
     * cannot be bypassed server-side (the client only sends a place when sneaking).
     * So, when the player is holding a block and tries without sneaking, we nudge
     * them via the action bar. Breaking the block (a separate event) still returns
     * the MythicMobs item.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(@NotNull PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getPlayer().isSneaking()) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.CHISELED_BOOKSHELF
                || manager.getMark(block) == null) {
            return;
        }

        // Seal it: no book in/out, no accidental placement on a non-sneak click.
        event.setCancelled(true);

        // If they were clearly trying to build (a block in hand), nudge them to
        // sneak — once, on the main hand, so it isn't doubled across both hands.
        if (event.getHand() == EquipmentSlot.HAND) {
            ItemStack inHand = event.getItem();
            if (inHand != null && inHand.getType().isBlock() && !inHand.getType().isAir()) {
                event.getPlayer().sendActionBar(
                        plugin.getMessages().parsed("enchanting.library-sneak-build"));
            }
        }
    }

    // ── Break: give the library item back, clear the mark ───────────────────

    /**
     * Runs at {@link EventPriority#HIGHEST} so protection plugins (WorldGuard,
     * Towny, GriefPrevention…) that veto the break at HIGH/HIGHEST have already
     * run — {@code ignoreCancelled} then skips us when they cancelled first.
     * <p>
     * The item refund and un-mark are <b>deferred one tick</b> and gated on the
     * block actually having broken. A protection plugin that cancels even later
     * (e.g. at {@code MONITOR}) would otherwise let us hand out the library item
     * while the bookshelf stays standing — a dupe. Suppressing the vanilla drop
     * is done inline (harmless if the break is later cancelled, since a cancelled
     * break drops nothing anyway).
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(@NotNull BlockBreakEvent event) {
        final Block block = event.getBlock();
        final String id = manager.getMark(block);
        if (id == null) {
            return;
        }
        event.setDropItems(false); // never drop a plain vanilla bookshelf
        final boolean creative = event.getPlayer().getGameMode() == GameMode.CREATIVE;

        Bukkit.getScheduler().runTask(plugin, () -> {
            // Break vetoed after us → bookshelf is still standing. Keep the mark
            // and hand out nothing, so the library can never be duplicated.
            if (isBookshelf(block.getType())) {
                return;
            }
            manager.unmark(block);
            if (!creative) {
                ItemStack drop = plugin.getMythicMobsHook().createItem(id);
                if (drop != null) {
                    block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), drop);
                }
            }
        });
    }

    // ── Make marked libraries indestructible except by mining ───────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(@NotNull BlockPistonExtendEvent event) {
        if (manager.anyMarked(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(@NotNull BlockPistonRetractEvent event) {
        if (manager.anyMarked(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(@NotNull EntityExplodeEvent event) {
        protect(event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(@NotNull BlockExplodeEvent event) {
        protect(event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBurn(@NotNull BlockBurnEvent event) {
        if (manager.getMark(event.getBlock()) != null) {
            event.setCancelled(true);
        }
    }

    /** Endermen, withers, falling blocks, silverfish, etc. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChange(@NotNull EntityChangeBlockEvent event) {
        if (manager.getMark(event.getBlock()) != null) {
            event.setCancelled(true);
        }
    }

    private void protect(@NotNull List<Block> blocks) {
        // One chunk-PDC parse per chunk for the whole blast, and non-bookshelf
        // blocks are skipped before any PDC access (see getMark).
        final Map<Long, Map<String, String>> cache = new HashMap<>();
        Iterator<Block> it = blocks.iterator();
        while (it.hasNext()) {
            if (manager.getMark(it.next(), cache) != null) {
                it.remove(); // keep the library standing and the mark intact
            }
        }
    }

    /**
     * Makes a chiseled-bookshelf library render full of books. Two layers, because
     * the snapshot block-state update alone proved unreliable for pushing the look
     * to clients:
     * <ol>
     *   <li>Fill the block-entity inventory (authoritative — gives proper book
     *       spines and is what {@code setDropItems(false)} on break suppresses).</li>
     *   <li>Force every {@code slot_N_occupied} visual flag on the block data as a
     *       backstop, so it looks full even if the inventory write didn't sync.</li>
     * </ol>
     * Combined with {@link #onInteract} the books can never be taken out, so the two
     * layers can never visibly desync. No-op if the block isn't (still) a marked
     * chiseled library.
     */
    private void sealIfChiseled(@NotNull Block block) {
        if (block.getType() != Material.CHISELED_BOOKSHELF || manager.getMark(block) == null) {
            return;
        }

        // 1) Authoritative: fill the block-entity inventory.
        if (block.getState() instanceof ChiseledBookshelf shelf) {
            Inventory inv = shelf.getInventory();
            for (int slot = 0; slot < inv.getSize(); slot++) {
                inv.setItem(slot, new ItemStack(Material.BOOK));
            }
            shelf.update(true, true);
        }

        // 2) Backstop: force the visual occupied flags for any slot still showing empty.
        if (block.getBlockData() instanceof org.bukkit.block.data.type.ChiseledBookshelf data) {
            boolean changed = false;
            for (int slot = 0; slot < 6; slot++) {
                if (!data.isSlotOccupied(slot)) {
                    data.setSlotOccupied(slot, true);
                    changed = true;
                }
            }
            if (changed) {
                block.setBlockData(data, false);
            }
        }
    }
}
