/* SuperEnchanter - Author: Scrulius (GitHub) */
package dev.scrulius.superenchanter.listener;

import dev.scrulius.superenchanter.SuperEnchanterPlugin;
import dev.scrulius.superenchanter.config.PluginConfig;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps "free" (cost-0) enchantments out of natural loot so players can't farm
 * structures (End cities, mansions, trial chambers…), fishing or mob drops for
 * enchanted gear — enchantments must always go through the custom enchanting table.
 * <p>
 * Two independent, config-gated actions ({@code loot-control}):
 * <ul>
 *   <li>{@code remove-enchanted-books} — drops enchanted books entirely.</li>
 *   <li>{@code strip-equipment-enchantments} — removes enchantments from gear,
 *       leaving the (now plain) item.</li>
 * </ul>
 * Covers chest/fishing/structure loot via {@link LootGenerateEvent} and, when
 * {@code include-mob-drops} is on, dropped gear via {@link EntityDeathEvent}.
 */
public final class LootControlListener implements Listener {

    private final SuperEnchanterPlugin plugin;

    public LootControlListener(@NotNull SuperEnchanterPlugin plugin) {
        this.plugin = plugin;
    }

    /** Chest / structure / fishing loot (loot-table generated). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLootGenerate(@NotNull LootGenerateEvent event) {
        final PluginConfig config = plugin.getPluginConfig();
        if (!config.isLootControlEnabled()) {
            return;
        }
        final World world = event.getLootContext().getLocation().getWorld();
        if (world != null && config.isLootWorldDisabled(world.getName())) {
            return;
        }
        final List<ItemStack> sanitised = new ArrayList<>(event.getLoot());
        if (sanitise(sanitised, config)) {
            event.setLoot(sanitised);
        }
    }

    /** Gear dropped by mobs on death. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDeath(@NotNull EntityDeathEvent event) {
        final PluginConfig config = plugin.getPluginConfig();
        if (!config.isLootControlEnabled() || !config.isLootIncludeMobDrops()) {
            return;
        }
        if (config.isLootWorldDisabled(event.getEntity().getWorld().getName())) {
            return;
        }
        // getDrops() is mutable — sanitise it in place.
        sanitise(event.getDrops(), config);
    }

    /**
     * Removes enchanted books and/or strips enchantments from a loot list per config.
     *
     * @return {@code true} if the list was modified
     */
    private boolean sanitise(@NotNull List<ItemStack> items, @NotNull PluginConfig config) {
        final boolean removeBooks = config.isLootRemoveEnchantedBooks();
        final boolean stripGear = config.isLootStripEquipmentEnchantments();
        if (!removeBooks && !stripGear) {
            return false;
        }
        boolean changed = false;
        final var it = items.iterator();
        while (it.hasNext()) {
            final ItemStack item = it.next();
            if (item == null || item.getType().isAir()) {
                continue;
            }
            if (removeBooks && item.getType() == Material.ENCHANTED_BOOK) {
                it.remove();
                changed = true;
                continue;
            }
            if (stripGear) {
                changed |= stripEnchantments(item);
            }
        }
        return changed;
    }

    /**
     * Removes every (vanilla or EcoEnchants) enchantment from a non-book item,
     * leaving the item itself intact.
     *
     * @return {@code true} if any enchantment was removed
     */
    private static boolean stripEnchantments(@Nullable ItemStack item) {
        if (item == null || item.getEnchantments().isEmpty()) {
            return false;
        }
        for (Enchantment enchantment : new ArrayList<>(item.getEnchantments().keySet())) {
            item.removeEnchantment(enchantment);
        }
        return true;
    }
}
