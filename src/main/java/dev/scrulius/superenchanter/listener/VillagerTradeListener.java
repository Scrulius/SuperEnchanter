/* SuperEnchanter - Author: Scrulius (GitHub) */
package dev.scrulius.superenchanter.listener;

import dev.scrulius.superenchanter.SuperEnchanterPlugin;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Stops villagers from acquiring any trade whose result is a book, so the enchanting
 * economy can't be bypassed by buying (enchanted) books from librarians. Config-gated
 * ({@code villager-trades.block-book-trades}); cancelling {@link VillagerAcquireTradeEvent}
 * prevents the recipe from ever being added to the villager.
 */
public final class VillagerTradeListener implements Listener {

    /** Every book-family material — selling any of these is blocked. */
    private static final Set<Material> BOOK_MATERIALS = Set.of(
            Material.ENCHANTED_BOOK,
            Material.BOOK,
            Material.WRITABLE_BOOK,
            Material.WRITTEN_BOOK,
            Material.KNOWLEDGE_BOOK
    );

    private final SuperEnchanterPlugin plugin;

    public VillagerTradeListener(@NotNull SuperEnchanterPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAcquireTrade(@NotNull VillagerAcquireTradeEvent event) {
        if (!plugin.getPluginConfig().isVillagerTradesEnabled()
                || !plugin.getPluginConfig().isVillagerBlockBookTrades()) {
            return;
        }
        final ItemStack result = event.getRecipe().getResult();
        if (result != null && BOOK_MATERIALS.contains(result.getType())) {
            event.setCancelled(true);
        }
    }
}
