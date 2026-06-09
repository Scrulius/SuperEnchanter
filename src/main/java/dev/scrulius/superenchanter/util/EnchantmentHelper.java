/* SuperEnchanter - Author: Scrulius (GitHub) */
package dev.scrulius.superenchanter.util;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemEnchantments;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class for enchantment operations using the modern Paper
 * {@link DataComponentTypes} API.
 * <p>
 * Handles both regular items and enchanted books transparently.
 * </p>
 */
@SuppressWarnings("UnstableApiUsage")
public final class EnchantmentHelper {

    private EnchantmentHelper() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Reads all enchantments from the given item. For enchanted books the stored
     * enchantments are returned; for regular items the normal enchantments are used.
     *
     * @param item the item to inspect
     * @return an unmodifiable map of enchantments and their levels
     */
    public static @NotNull Map<Enchantment, Integer> getEnchantments(@NotNull ItemStack item) {
        Map<Enchantment, Integer> result = new HashMap<>();

        // Read standard enchantments
        ItemEnchantments enchants = item.getData(DataComponentTypes.ENCHANTMENTS);
        if (enchants != null) {
            result.putAll(enchants.enchantments());
        }

        // For enchanted books, also (or primarily) check stored enchantments
        if (isEnchantedBook(item)) {
            ItemEnchantments stored = item.getData(DataComponentTypes.STORED_ENCHANTMENTS);
            if (stored != null) {
                result.putAll(stored.enchantments());
            }
        }

        return Collections.unmodifiableMap(result);
    }

    /**
     * Applies an enchantment to the item at the specified level.
     * Uses stored enchantments for books, normal enchantments for everything else.
     *
     * @param item    the item to enchant (will be cloned)
     * @param enchant the enchantment to apply
     * @param level   the enchantment level
     * @return a new {@link ItemStack} with the enchantment applied
     */
    public static @NotNull ItemStack applyEnchantment(@NotNull ItemStack item,
                                                       @NotNull Enchantment enchant,
                                                       int level) {
        ItemStack result = item.clone();

        if (isEnchantedBook(result)) {
            ItemEnchantments existing = result.getData(DataComponentTypes.STORED_ENCHANTMENTS);
            ItemEnchantments.Builder builder = ItemEnchantments.itemEnchantments();
            if (existing != null) {
                existing.enchantments().forEach(builder::add);
            }
            builder.add(enchant, level);
            result.setData(DataComponentTypes.STORED_ENCHANTMENTS, builder.build());
        } else {
            ItemEnchantments existing = result.getData(DataComponentTypes.ENCHANTMENTS);
            ItemEnchantments.Builder builder = ItemEnchantments.itemEnchantments();
            if (existing != null) {
                existing.enchantments().forEach(builder::add);
            }
            builder.add(enchant, level);
            result.setData(DataComponentTypes.ENCHANTMENTS, builder.build());
        }

        return result;
    }

    /**
     * Removes an enchantment from the item.
     *
     * @param item    the item to modify (will be cloned)
     * @param enchant the enchantment to remove
     * @return a new {@link ItemStack} without the specified enchantment
     */
    public static @NotNull ItemStack removeEnchantment(@NotNull ItemStack item,
                                                        @NotNull Enchantment enchant) {
        ItemStack result = item.clone();

        if (isEnchantedBook(result)) {
            ItemEnchantments existing = result.getData(DataComponentTypes.STORED_ENCHANTMENTS);
            if (existing != null) {
                ItemEnchantments.Builder builder = ItemEnchantments.itemEnchantments();
                existing.enchantments().forEach((e, lvl) -> {
                    if (!e.equals(enchant)) {
                        builder.add(e, lvl);
                    }
                });
                result.setData(DataComponentTypes.STORED_ENCHANTMENTS, builder.build());
            }
        } else {
            ItemEnchantments existing = result.getData(DataComponentTypes.ENCHANTMENTS);
            if (existing != null) {
                ItemEnchantments.Builder builder = ItemEnchantments.itemEnchantments();
                existing.enchantments().forEach((e, lvl) -> {
                    if (!e.equals(enchant)) {
                        builder.add(e, lvl);
                    }
                });
                result.setData(DataComponentTypes.ENCHANTMENTS, builder.build());
            }
        }

        return result;
    }

    /**
     * Checks whether the given enchantment can be applied to this item type.
     *
     * @param item    the item to test
     * @param enchant the enchantment to test
     * @return {@code true} if the enchantment is applicable
     */
    public static boolean canEnchant(@NotNull ItemStack item, @NotNull Enchantment enchant) {
        if (isEnchantedBook(item)) {
            return true; // Books accept any enchantment
        }
        return enchant.canEnchantItem(item);
    }

    /**
     * Checks whether any existing enchantment on the item conflicts with the new one.
     *
     * @param item       the item to inspect
     * @param newEnchant the enchantment to test for conflicts
     * @return {@code true} if at least one conflict exists
     */
    public static boolean hasConflict(@NotNull ItemStack item, @NotNull Enchantment newEnchant) {
        return !getConflicting(item, newEnchant).isEmpty();
    }

    /**
     * Returns a list of all enchantments currently on the item that conflict
     * with the specified enchantment.
     *
     * @param item       the item to inspect
     * @param newEnchant the enchantment to test against
     * @return list of conflicting enchantments (may be empty)
     */
    public static @NotNull List<Enchantment> getConflicting(@NotNull ItemStack item,
                                                             @NotNull Enchantment newEnchant) {
        List<Enchantment> conflicts = new ArrayList<>();
        Map<Enchantment, Integer> existing = getEnchantments(item);

        for (Enchantment e : existing.keySet()) {
            if (e.equals(newEnchant)) {
                continue; // Same enchantment is not a "conflict"
            }
            if (e.conflictsWith(newEnchant) || newEnchant.conflictsWith(e)) {
                conflicts.add(e);
            }
        }

        return conflicts;
    }

    /**
     * Returns the maximum vanilla level for the given enchantment.
     *
     * @param enchant the enchantment
     * @return the max level
     */
    public static int getMaxLevel(@NotNull Enchantment enchant) {
        return enchant.getMaxLevel();
    }

    /**
     * Checks if the item is an enchanted book.
     *
     * @param item the item to check
     * @return {@code true} if the material is {@link Material#ENCHANTED_BOOK}
     */
    public static boolean isEnchantedBook(@NotNull ItemStack item) {
        return item.getType() == Material.ENCHANTED_BOOK;
    }

    /**
     * Formats an enchantment key into a human-readable, title-cased name.
     * <p>
     * Example: {@code minecraft:fire_aspect} → {@code Fire Aspect}.
     *
     * @param enchant the enchantment to format
     * @return the prettified name
     */
    public static @NotNull String prettyName(@NotNull Enchantment enchant) {
        String raw = enchant.getKey().getKey();
        String[] words = raw.split("_");
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < words.length; i++) {
            if (words[i].isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(words[i].charAt(0)));
            if (words[i].length() > 1) {
                sb.append(words[i].substring(1).toLowerCase());
            }
        }
        return sb.toString();
    }
}
