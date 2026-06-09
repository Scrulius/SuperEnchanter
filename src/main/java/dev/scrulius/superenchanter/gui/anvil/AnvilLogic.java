/* SuperEnchanter - Author: Scrulius (GitHub) */
package dev.scrulius.superenchanter.gui.anvil;

import dev.scrulius.superenchanter.config.PluginConfig;
import dev.scrulius.superenchanter.config.PluginConfig.CostOverride;
import dev.scrulius.superenchanter.economy.Cost;
import dev.scrulius.superenchanter.economy.CostType;
import dev.scrulius.superenchanter.util.EnchantmentHelper;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemEnchantments;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Encapsulates all anvil combination / merge logic for the custom anvil GUI.
 * <p>
 * This class is stateless; every computation is derived from the supplied inputs.
 * Cost overrides from {@link PluginConfig} are evaluated in declaration order—first
 * match wins.
 */
public final class AnvilLogic {

    // ── Result types ──────────────────────────────────────────────────────

    /**
     * Immutable result of an anvil merge calculation.
     *
     * @param resultItem   the merged item, or {@code null} when the merge is not valid
     * @param xpCost       base XP cost (before override substitution)
     * @param conflicts    enchantments that could not be applied due to conflicts
     * @param isValid      {@code true} when at least one enchantment was successfully merged
     * @param levelsAdded  total number of enchantment levels that were added / upgraded
     */
    public record AnvilResult(
            @Nullable ItemStack resultItem,
            int xpCost,
            @NotNull List<Enchantment> conflicts,
            boolean isValid,
            int levelsAdded
    ) {
        /** Sentinel value returned when inputs are missing or invalid. */
        public static final AnvilResult EMPTY = new AnvilResult(null, 0, List.of(), false, 0);
    }

    // ── Private constructor (utility class) ───────────────────────────────

    private AnvilLogic() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    // ── Core merge logic ──────────────────────────────────────────────────

    /**
     * Calculates the result of combining {@code target} with {@code sacrifice}.
     *
     * <ol>
     *   <li>Extract enchantments from the sacrifice (stored enchantments for books,
     *       regular enchantments otherwise).</li>
     *   <li>For each sacrifice enchantment, check compatibility and conflicts.</li>
     *   <li>Merge levels: same level → level+1 (capped at max), otherwise keep higher.</li>
     *   <li>Build the result item and compute XP cost.</li>
     * </ol>
     *
     * @param target    the item receiving enchantments (left slot)
     * @param sacrifice the item donating enchantments (right slot)
     * @param config    plugin configuration for cost parameters
     * @return a fully populated {@link AnvilResult}
     */
    @NotNull
    public static AnvilResult calculateResult(@Nullable ItemStack target,
                                              @Nullable ItemStack sacrifice,
                                              @NotNull PluginConfig config) {
        return calculateResult(target, sacrifice, config, AnvilEnchantGate.ALLOW_ALL);
    }

    /**
     * Same as {@link #calculateResult(ItemStack, ItemStack, PluginConfig)} but with
     * an injected {@link AnvilEnchantGate} that applies the EcoEnchants rules
     * (targets, conflicts, required, type limits, blacklist, per-enchant level
     * caps) on top of the vanilla checks — so the anvil obeys exactly what the
     * enchanting table and transfer menu enforce, instead of vanilla rules alone.
     *
     * @param gate per-enchantment verdict (allowed max level, or 0 to reject)
     */
    @NotNull
    public static AnvilResult calculateResult(@Nullable ItemStack target,
                                              @Nullable ItemStack sacrifice,
                                              @NotNull PluginConfig config,
                                              @NotNull AnvilEnchantGate gate) {
        if (target == null || target.getType().isAir()
                || sacrifice == null || sacrifice.getType().isAir()) {
            return AnvilResult.EMPTY;
        }

        // Determine which enchantments the sacrifice provides
        Map<Enchantment, Integer> sacrificeEnchants = extractEnchantments(sacrifice);
        if (sacrificeEnchants.isEmpty()) {
            return AnvilResult.EMPTY;
        }

        // Current enchantments on the target
        Map<Enchantment, Integer> targetEnchants = EnchantmentHelper.getEnchantments(target);

        // Accumulators
        Map<Enchantment, Integer> mergedEnchants = new LinkedHashMap<>(targetEnchants);
        List<Enchantment> conflicts = new ArrayList<>();
        int totalLevelsAdded = 0;

        for (Map.Entry<Enchantment, Integer> entry : sacrificeEnchants.entrySet()) {
            Enchantment enchantment = entry.getKey();
            int sacrificeLevel = entry.getValue();

            // Compatibility check (does the enchantment apply to this item type?)
            if (!EnchantmentHelper.canEnchant(target, enchantment)) {
                conflicts.add(enchantment);
                continue;
            }

            // Conflict check (mutually exclusive enchantments)
            // Check against the evolving merged map, not the original target,
            // to prevent two conflicting sacrifice enchants from both being added.
            boolean hasConflict = false;
            for (Enchantment existing : mergedEnchants.keySet()) {
                if (!existing.equals(enchantment)
                        && (existing.conflictsWith(enchantment) || enchantment.conflictsWith(existing))) {
                    hasConflict = true;
                    break;
                }
            }
            if (hasConflict) {
                conflicts.add(enchantment);
                continue;
            }

            // EcoEnchants gate: 0 = rejected (blacklist / wrong target / eco conflict /
            // missing required / type limit); otherwise the per-enchant level cap.
            int allowedMax = gate.allowedMaxLevel(enchantment);
            if (allowedMax <= 0) {
                conflicts.add(enchantment);
                continue;
            }

            int maxLevel = Math.min(EnchantmentHelper.getMaxLevel(enchantment), allowedMax);
            int currentLevel = mergedEnchants.getOrDefault(enchantment, 0);
            int newLevel = AnvilFormulas.mergeLevel(currentLevel, sacrificeLevel, maxLevel);

            int levelsGained = newLevel - currentLevel;
            if (levelsGained > 0) {
                totalLevelsAdded += levelsGained;
            }

            mergedEnchants.put(enchantment, newLevel);
        }

        // If nothing was actually merged, the result is invalid
        if (totalLevelsAdded == 0 && conflicts.isEmpty()) {
            return AnvilResult.EMPTY;
        }

        if (totalLevelsAdded == 0) {
            // Only conflicts, nothing merged
            return new AnvilResult(null, 0, List.copyOf(conflicts), false, 0);
        }

        // Build the result ItemStack
        ItemStack result = target.clone();
        for (Map.Entry<Enchantment, Integer> entry : mergedEnchants.entrySet()) {
            result = EnchantmentHelper.applyEnchantment(result, entry.getKey(), entry.getValue());
        }

        // Calculate XP cost
        int xpCost = AnvilFormulas.xpCost(config.getAnvilBaseXPCost(),
                config.getAnvilCostPerLevel(), totalLevelsAdded, config.getAnvilMaxCost());

        return new AnvilResult(
                result,
                xpCost,
                List.copyOf(conflicts),
                true,
                totalLevelsAdded
        );
    }

    // ── Cost override resolution ──────────────────────────────────────────

    /**
     * Determines the final cost for a merge operation, taking configured overrides
     * into account. Overrides are matched against the <em>sacrifice</em> item
     * in declaration order; the first match wins.
     *
     * @param sacrifice the sacrifice item to match against overrides
     * @param result    the calculated anvil result (provides default XP cost)
     * @param config    plugin configuration holding overrides
     * @return resolved {@link Cost}
     */
    @NotNull
    public static Cost determineCost(@Nullable ItemStack sacrifice,
                                     @NotNull AnvilResult result,
                                     @NotNull PluginConfig config) {
        if (sacrifice == null || sacrifice.getType().isAir()) {
            return Cost.xp(result.xpCost());
        }

        List<CostOverride> overrides = config.getAnvilCostOverrides();
        if (overrides != null) {
            for (CostOverride override : overrides) {
                if (matchesOverride(sacrifice, override)) {
                    return new Cost(CostType.fromString(override.costType()), override.amount());
                }
            }
        }

        // Default: the standard cost type from config, charged at the computed XP amount.
        return new Cost(CostType.fromString(config.getAnvilDefaultCostType()), result.xpCost());
    }

    // ── Internal helpers ──────────────────────────────────────────────────

    /**
     * Extracts enchantments from an item, using stored enchantments for enchanted
     * books and regular enchantments otherwise.
     */
    @NotNull
    private static Map<Enchantment, Integer> extractEnchantments(@NotNull ItemStack item) {
        if (EnchantmentHelper.isEnchantedBook(item)) {
            return extractStoredEnchantments(item);
        }
        return EnchantmentHelper.getEnchantments(item);
    }

    /**
     * Extracts stored enchantments from an enchanted book via the
     * {@link DataComponentTypes#STORED_ENCHANTMENTS} data component.
     */
    @NotNull
    @SuppressWarnings("UnstableApiUsage")
    private static Map<Enchantment, Integer> extractStoredEnchantments(@NotNull ItemStack book) {
        ItemEnchantments stored = book.getData(DataComponentTypes.STORED_ENCHANTMENTS);
        if (stored == null) {
            // Fallback: some books might expose enchantments via the regular helper
            return EnchantmentHelper.getEnchantments(book);
        }

        Map<Enchantment, Integer> result = new LinkedHashMap<>();
        stored.enchantments().forEach((enchantment, level) -> result.put(enchantment, level));
        return result;
    }

    /**
     * Checks whether the sacrifice item matches a specific {@link CostOverride}.
     */
    @SuppressWarnings("UnstableApiUsage")
    private static boolean matchesOverride(@NotNull ItemStack sacrifice,
                                           @NotNull CostOverride override) {
        return switch (override.matchType().toUpperCase()) {
            case "MATERIAL" -> sacrifice.getType().getKey().toString()
                    .equalsIgnoreCase(override.value());
            case "NAMESPACE" -> {
                String ns = override.namespace();
                String key = override.key();
                // Match against the item's material key
                var itemKey = sacrifice.getType().getKey();
                boolean nsMatch = ns == null || ns.isBlank()
                        || itemKey.getNamespace().equalsIgnoreCase(ns);
                boolean keyMatch = key == null || key.isBlank()
                        || itemKey.getKey().equalsIgnoreCase(key);
                yield nsMatch && keyMatch;
            }
            case "CUSTOM_MODEL_DATA" -> {
                var cmd = sacrifice.getData(DataComponentTypes.CUSTOM_MODEL_DATA);
                if (cmd == null) {
                    yield false;
                }
                try {
                    // Legacy integer CMD is stored as a float entry in the modern component.
                    yield cmd.floats().contains(Float.parseFloat(override.value()));
                } catch (NumberFormatException e) {
                    yield false;
                }
            }
            case "PDC", "PDC_KEY" -> {
                if (!sacrifice.hasItemMeta()
                        || override.namespace() == null || override.namespace().isBlank()
                        || override.key() == null || override.key().isBlank()) {
                    yield false;
                }
                var pdc = sacrifice.getItemMeta().getPersistentDataContainer();
                var namespacedKey = new org.bukkit.NamespacedKey(
                        override.namespace(), override.key());
                String wanted = override.value();
                if (wanted == null || wanted.isBlank()) {
                    // No value given → match on key presence alone.
                    yield pdc.has(namespacedKey);
                }
                // Value given → read the stored String (e.g. MythicMobs' "type") and compare.
                String stored = pdc.get(namespacedKey,
                        org.bukkit.persistence.PersistentDataType.STRING);
                yield wanted.equalsIgnoreCase(stored);
            }
            default -> false;
        };
    }

}
