/* SuperEnchanter - Author: Scrulius (GitHub) */
package dev.scrulius.superenchanter.gui.enchanting;

import dev.scrulius.superenchanter.SuperEnchanterPlugin;
import dev.scrulius.superenchanter.config.PluginConfig;
import dev.scrulius.superenchanter.integration.EcoEnchantsHook;
import dev.scrulius.superenchanter.util.EnchantmentHelper;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.enchantments.Enchantment;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable, <b>item-independent</b> precomputation of every enchantment's static
 * metadata (type, rarity, display name, max level, required/conflict sets, type
 * limit), built <b>once</b> at startup and rebuilt on {@code /se reload}.
 * <p>
 * {@link EnchantingLogic#analyze(org.bukkit.inventory.ItemStack, SuperEnchanterPlugin)}
 * scans the whole enchantment registry on every fresh item. Without this index each
 * scan re-derived, per enchant, the type/rarity/name/category and — worse —
 * allocated a fresh {@code ArrayList} for the conflict and required lists
 * ({@link EcoEnchantsHook#getConflicts}/{@link EcoEnchantsHook#getRequired} copy on
 * every call), O(present) times per candidate. The index moves all of that off the
 * hot path: {@code analyze()} now only does the genuinely item-dependent work (the
 * target check plus the present-set classification) against these precomputed,
 * immutable sets.
 * </p>
 * <p>
 * Built only when EcoEnchants is live (never under MockBukkit). The plugin warms it
 * eagerly at enable and invalidates it on reload; {@code analyze()} reads it through
 * {@link SuperEnchanterPlugin#getEnchantmentIndex()} (lazy-builds as a fallback).
 * </p>
 */
public final class EnchantmentIndex {

    /**
     * Item-independent metadata for one enchantment.
     *
     * @param enchantment   the enchantment
     * @param typeId        the EcoEnchants type id, normalised ({@code "other"} when blank)
     * @param categoryName  the localised category display name (from {@code typeId})
     * @param rarityId      the EcoEnchants rarity id, or {@code null} for vanilla-only
     * @param displayName   the resolved display name (never empty)
     * @param maxLevel      the maximum level ({@code enchant.getMaxLevel()})
     * @param required      enchantments that must be present first (immutable)
     * @param conflicts     enchantments this one declares as conflicting (immutable)
     * @param conflictsAll  whether it conflicts with every other enchantment
     * @param typeLimit     how many of this type an item may hold ({@link Integer#MAX_VALUE} = unlimited)
     */
    public record Entry(
            @NotNull Enchantment enchantment,
            @NotNull String typeId,
            @NotNull String categoryName,
            @Nullable String rarityId,
            @NotNull String displayName,
            int maxLevel,
            @NotNull Set<Enchantment> required,
            @NotNull Set<Enchantment> conflicts,
            boolean conflictsAll,
            int typeLimit
    ) {}

    /** Non-disabled enchantments, in registry order — what {@code analyze()} iterates. */
    private final List<Entry> candidates;
    /** Every enchantment (incl. disabled) by key, for present-enchant type lookups. */
    private final Map<Enchantment, Entry> byEnchant;

    private EnchantmentIndex(@NotNull List<Entry> candidates, @NotNull Map<Enchantment, Entry> byEnchant) {
        this.candidates = candidates;
        this.byEnchant = byEnchant;
    }

    /** @return the non-disabled enchantments to consider as offers (immutable) */
    public @NotNull List<Entry> candidates() {
        return candidates;
    }

    /** @return the metadata for any enchantment (incl. disabled), or {@code null} if unknown */
    public @Nullable Entry get(@NotNull Enchantment enchantment) {
        return byEnchant.get(enchantment);
    }

    /**
     * Builds the index by scanning the enchantment registry once and resolving every
     * enchantment's static metadata via EcoEnchants. Requires EcoEnchants on the
     * classpath — do not call under MockBukkit.
     *
     * @param plugin the plugin (for config + the EcoEnchants hook)
     * @return a fresh immutable index
     */
    public static @NotNull EnchantmentIndex build(@NotNull SuperEnchanterPlugin plugin) {
        final PluginConfig config = plugin.getPluginConfig();
        final EcoEnchantsHook eco = plugin.getEcoHook();
        final var registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT);

        final List<Entry> candidates = new ArrayList<>();
        final Map<Enchantment, Entry> byEnchant = new HashMap<>();

        for (Enchantment ench : registry) {
            final String rawType = eco.getTypeId(ench);
            final String typeId = (rawType == null || rawType.isBlank()) ? "other" : rawType;

            String name = eco.getDisplayName(ench, 0);
            if (name.isEmpty()) {
                name = EnchantmentHelper.prettyName(ench);
            }

            final Entry entry = new Entry(
                    ench,
                    typeId,
                    EnchantingLogic.categoryDisplayName(plugin, typeId),
                    eco.getRarityId(ench),
                    name,
                    EnchantmentHelper.getMaxLevel(ench),
                    Set.copyOf(eco.getRequired(ench)),
                    Set.copyOf(eco.getConflicts(ench)),
                    eco.conflictsWithEverything(ench),
                    eco.getTypeLimit(ench));

            byEnchant.put(ench, entry);
            // isEnchantDisabled takes the RAW type (matches analyze()'s old behaviour).
            if (!config.isEnchantDisabled(ench, rawType)) {
                candidates.add(entry);
            }
        }
        return new EnchantmentIndex(List.copyOf(candidates), Map.copyOf(byEnchant));
    }
}
