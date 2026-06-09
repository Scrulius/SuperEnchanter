/* SuperEnchanter - Author: Scrulius (GitHub) */
package dev.scrulius.superenchanter.util;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Fluent builder for {@link ItemStack} with full MiniMessage support.
 * <p>
 * All setter methods return {@code this} for method chaining. Call {@link #build()}
 * to obtain the final {@link ItemStack}.
 * </p>
 */
public final class ItemBuilder {

    private final ItemStack itemStack;
    private final ItemMeta meta;
    private final List<Component> loreLines = new ArrayList<>();
    private Integer pendingCustomModelData;

    /**
     * Creates a builder for a single item of the given material.
     *
     * @param material the item material
     */
    public ItemBuilder(@NotNull Material material) {
        this(material, 1);
    }

    /**
     * Creates a builder for the given material and amount.
     *
     * @param material the item material
     * @param amount   the stack size
     */
    public ItemBuilder(@NotNull Material material, int amount) {
        this.itemStack = new ItemStack(material, amount);
        this.meta = itemStack.getItemMeta();
    }

    /**
     * Sets the display name using a MiniMessage string.
     *
     * @param miniMessage the MiniMessage-formatted display name
     * @return this builder
     */
    public @NotNull ItemBuilder name(@NotNull String miniMessage) {
        meta.displayName(MiniMessageUtil.parse(miniMessage));
        return this;
    }

    /**
     * Sets the lore lines from MiniMessage strings, replacing any existing lore.
     *
     * @param miniMessageLines MiniMessage-formatted lore lines
     * @return this builder
     */
    public @NotNull ItemBuilder lore(@NotNull String... miniMessageLines) {
        loreLines.clear();
        Arrays.stream(miniMessageLines)
                .map(MiniMessageUtil::parse)
                .forEach(loreLines::add);
        return this;
    }

    /**
     * Sets the lore lines from a list of MiniMessage strings, replacing any existing lore.
     *
     * @param miniMessageLines list of MiniMessage-formatted lore lines
     * @return this builder
     */
    public @NotNull ItemBuilder lore(@NotNull List<String> miniMessageLines) {
        loreLines.clear();
        miniMessageLines.stream()
                .map(MiniMessageUtil::parse)
                .forEach(loreLines::add);
        return this;
    }

    /**
     * Appends a single lore line using a MiniMessage string.
     *
     * @param miniMessageLine the MiniMessage-formatted lore line
     * @return this builder
     */
    public @NotNull ItemBuilder addLore(@NotNull String miniMessageLine) {
        loreLines.add(MiniMessageUtil.parse(miniMessageLine));
        return this;
    }

    /**
     * Adds an enchantment at the given level.
     *
     * @param ench  the enchantment to add
     * @param level the enchantment level
     * @return this builder
     */
    public @NotNull ItemBuilder enchant(@NotNull Enchantment ench, int level) {
        meta.addEnchant(ench, level, true);
        return this;
    }

    /**
     * Applies a fake enchantment glow by adding LUCK 1 and hiding enchantments
     * via {@link DataComponentTypes}.
     *
     * @return this builder
     */
    @SuppressWarnings("UnstableApiUsage")
    public @NotNull ItemBuilder glow() {
        meta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        return this;
    }

    /**
     * Hides all item flags (enchantments, attributes, unbreakable, etc.).
     *
     * @return this builder
     */
    public @NotNull ItemBuilder hideFlags() {
        meta.addItemFlags(ItemFlag.values());
        return this;
    }

    /**
     * Sets a (legacy-style) integer custom model data value, applied via the
     * modern {@link DataComponentTypes#CUSTOM_MODEL_DATA} component as a single
     * float entry.
     *
     * @param data the custom model data value
     * @return this builder
     */
    public @NotNull ItemBuilder customModelData(int data) {
        this.pendingCustomModelData = data;
        return this;
    }

    /**
     * Adds a value to the item's {@link PersistentDataContainer}.
     *
     * @param key   the namespaced key
     * @param type  the persistent data type
     * @param value the value to store
     * @param <T>   the primary (stored) type
     * @param <Z>   the retrieved type
     * @return this builder
     */
    @SuppressWarnings("unchecked")
    public <T, Z> @NotNull ItemBuilder persistentData(@NotNull NamespacedKey key,
                                                       @NotNull PersistentDataType<T, Z> type,
                                                       @NotNull Z value) {
        meta.getPersistentDataContainer().set(key, type, value);
        return this;
    }

    /**
     * Sets the unbreakable flag on the item.
     *
     * @param unbreakable whether the item should be unbreakable
     * @return this builder
     */
    public @NotNull ItemBuilder unbreakable(boolean unbreakable) {
        meta.setUnbreakable(unbreakable);
        return this;
    }

    /**
     * Sets the skin of a player-head item. No-op on non-head materials (the meta
     * won't be a {@link org.bukkit.inventory.meta.SkullMeta}).
     *
     * @param owner the player whose skin to show
     * @return this builder
     */
    public @NotNull ItemBuilder skullOwner(@NotNull org.bukkit.OfflinePlayer owner) {
        if (meta instanceof org.bukkit.inventory.meta.SkullMeta skull) {
            skull.setOwningPlayer(owner);
        }
        return this;
    }

    /**
     * Builds and returns the final {@link ItemStack}.
     *
     * @return the constructed item stack
     */
    @SuppressWarnings("UnstableApiUsage")
    public @NotNull ItemStack build() {
        if (!loreLines.isEmpty()) {
            meta.lore(loreLines);
        }
        itemStack.setItemMeta(meta);
        // Components must be applied after setItemMeta, which would otherwise overwrite them.
        if (pendingCustomModelData != null) {
            itemStack.setData(DataComponentTypes.CUSTOM_MODEL_DATA,
                    CustomModelData.customModelData().addFloat(pendingCustomModelData).build());
        }
        return itemStack;
    }
}
