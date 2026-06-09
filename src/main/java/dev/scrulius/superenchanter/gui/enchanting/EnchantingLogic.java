/* SuperEnchanter - Author: Scrulius (GitHub) */
package dev.scrulius.superenchanter.gui.enchanting;

import dev.scrulius.superenchanter.config.MessagesConfig;
import dev.scrulius.superenchanter.config.PluginConfig;
import dev.scrulius.superenchanter.config.PluginConfig.Reagent;
import dev.scrulius.superenchanter.economy.Cost;
import dev.scrulius.superenchanter.integration.EcoEnchantsHook;
import dev.scrulius.superenchanter.util.EnchantmentHelper;
import dev.scrulius.superenchanter.util.ItemBuilder;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Encapsulates all enchanting-table logic: compatible enchantment discovery,
 * offer computation, enchantment application, and icon rendering.
 */
public final class EnchantingLogic {

    private EnchantingLogic() {
        // Utility class
    }

    /** Why an enchantment cannot currently be applied ({@code NONE} = available). */
    public enum BlockReason { NONE, MAXED, CONFLICT, MISSING_REQUIRED, TYPE_LIMIT }

    /**
     * A relevant enchantment for an item (correct target, not blacklisted) plus
     * its current status, so the menu can show available and blocked ones alike
     * — and explain <em>why</em> the blocked ones are blocked.
     *
     * @param reasonDetail names of the enchantments behind a CONFLICT / MISSING_REQUIRED reason
     */
    public record AnalyzedEnchant(
            @NotNull Enchantment enchantment,
            @NotNull String displayName,
            @NotNull String typeId,
            @NotNull String categoryName,
            @Nullable String rarityId,
            int currentLevel,
            int maxLevel,
            @NotNull BlockReason reason,
            @NotNull List<String> reasonDetail
    ) {
        public boolean isAvailable() {
            return reason == BlockReason.NONE;
        }
    }

    /**
     * A selectable category (EcoEnchants type) in the first tier of the menu.
     *
     * @param typeId      the EcoEnchants type id (e.g. {@code curse})
     * @param displayName the prettified category name
     * @param count       how many relevant enchantments fall under this category
     */
    public record CategoryOffer(
            @NotNull String typeId,
            @NotNull String displayName,
            int count
    ) {}

    public record LevelEnchantmentOffer(
            @NotNull Enchantment enchantment,
            @NotNull String displayName,
            int level,
            int maxLevel,
            @Nullable String rarityId,
            @NotNull Cost cost,
            @NotNull Cost stepCost,
            int requiredPower,
            @Nullable Reagent reagent,
            boolean alreadyApplied,
            boolean hasEnoughPower
    ) {
        /** Roman-numeral suffix for the level, or empty for single-level enchantments. */
        public @NotNull String levelSuffix() {
            return maxLevel > 1 ? " " + toRoman(level) : "";
        }
    }

    /**
     * Scans the enchantment registry <b>once</b> and returns every enchantment
     * relevant to the item (correct target, not blacklisted), each annotated with
     * its category and a {@link BlockReason}. The GUI caches this per item so that
     * navigating categories and levels never triggers another scan.
     */
    @NotNull
    public static List<AnalyzedEnchant> analyze(
            @NotNull ItemStack item,
            @NotNull dev.scrulius.superenchanter.SuperEnchanterPlugin plugin) {

        final PluginConfig config = plugin.getPluginConfig();
        final EcoEnchantsHook eco = plugin.getEcoHook();
        final Map<Enchantment, Integer> current = EnchantmentHelper.getEnchantments(item);
        final Set<Enchantment> present = current.keySet();
        final List<AnalyzedEnchant> result = new ArrayList<>();
        final var registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT);

        for (Enchantment ench : registry) {
            final String rawType = eco.getTypeId(ench);
            if (config.isEnchantDisabled(ench, rawType)) {
                continue;
            }
            if (!eco.appliesToItem(ench, item)) {
                continue;
            }

            final String typeId = (rawType == null || rawType.isBlank()) ? "other" : rawType;
            final int currentLevel = current.getOrDefault(ench, 0);
            final int maxLevel = EnchantmentHelper.getMaxLevel(ench);

            // Only adding a NEW enchant can be blocked by required/conflict/type-limit;
            // upgrading an already-present one is always allowed (until maxed). Lists are
            // computed lazily in precedence order; the pure classifyBlock decides the reason.
            final boolean isNew = currentLevel == 0;
            final List<Enchantment> missing = isNew ? missingRequired(ench, present, eco) : List.of();
            final List<Enchantment> conflicts =
                    (isNew && missing.isEmpty()) ? conflictingPresent(ench, present, eco) : List.of();
            final boolean typeLimit = isNew && missing.isEmpty() && conflicts.isEmpty()
                    && typeLimitReached(ench, present, eco);

            final BlockReason reason = classifyBlock(
                    currentLevel, maxLevel, !missing.isEmpty(), !conflicts.isEmpty(), typeLimit);
            final List<String> detail = switch (reason) {
                case MISSING_REQUIRED -> names(missing, eco);
                case CONFLICT -> names(conflicts, eco);
                default -> List.of();
            };

            String name = eco.getDisplayName(ench, 0);
            if (name.isEmpty()) {
                name = EnchantmentHelper.prettyName(ench);
            }

            result.add(new AnalyzedEnchant(ench, name, typeId,
                    categoryDisplayName(plugin, typeId), eco.getRarityId(ench),
                    currentLevel, maxLevel, reason, detail));
        }
        return result;
    }

    /**
     * Pure precedence rule for why an enchantment is (un)available on an item — the
     * Bukkit/EcoEnchants-free core of {@link #analyze}, extracted so the ordering can be
     * unit-tested without a server. Precedence: an owned enchant at max level is
     * {@code MAXED}; otherwise only a <em>new</em> enchant ({@code currentLevel == 0}) can be
     * blocked, in order MISSING_REQUIRED → CONFLICT → TYPE_LIMIT; everything else is
     * {@code NONE} (available, including upgrading an owned-but-not-maxed enchant).
     *
     * @param currentLevel      the level currently on the item (0 = not present)
     * @param maxLevel          the enchantment's maximum level
     * @param missingRequired   whether a required prerequisite enchant is absent
     * @param conflictPresent   whether a conflicting enchant is present
     * @param typeLimitReached  whether the item already holds the max of this type
     * @return the {@link BlockReason}
     */
    @NotNull
    public static BlockReason classifyBlock(int currentLevel, int maxLevel,
                                            boolean missingRequired, boolean conflictPresent,
                                            boolean typeLimitReached) {
        if (currentLevel > 0 && currentLevel >= maxLevel) {
            return BlockReason.MAXED;
        }
        if (currentLevel == 0) {
            if (missingRequired) {
                return BlockReason.MISSING_REQUIRED;
            }
            if (conflictPresent) {
                return BlockReason.CONFLICT;
            }
            if (typeLimitReached) {
                return BlockReason.TYPE_LIMIT;
            }
        }
        return BlockReason.NONE;
    }

    /**
     * Localised display name for a category (EcoEnchants type id): uses
     * {@code messages.yml → category-names.<typeId>} when present, otherwise a
     * title-cased version of the id.
     */
    @NotNull
    private static String categoryDisplayName(
            @NotNull dev.scrulius.superenchanter.SuperEnchanterPlugin plugin, @NotNull String typeId) {
        final String key = "category-names." + typeId.toLowerCase(java.util.Locale.ROOT);
        return plugin.getMessages().hasKey(key) ? plugin.getMessages().raw(key) : prettyId(typeId);
    }

    /**
     * Localised display name for an EcoEnchants rarity id: uses
     * {@code messages.yml → rarity-names.<id>} when present, otherwise title-cased.
     * Returns an empty string for a {@code null} rarity (vanilla-only enchantment).
     */
    @NotNull
    private static String rarityDisplayName(
            @NotNull dev.scrulius.superenchanter.SuperEnchanterPlugin plugin, @Nullable String rarityId) {
        if (rarityId == null || rarityId.isBlank()) {
            return "";
        }
        final String key = "rarity-names." + rarityId.toLowerCase(java.util.Locale.ROOT);
        return plugin.getMessages().hasKey(key) ? plugin.getMessages().raw(key) : prettyId(rarityId);
    }

    /**
     * Canonical category order in the menu: the 5 rarities ascending, then spell
     * (habilidades) and curse last. Types not listed fall after these (then alphabetical),
     * so a new/unknown type never breaks the layout.
     */
    private static final List<String> CATEGORY_ORDER =
            List.of("comun", "raro", "epico", "legendario", "divino", "spell", "curse");

    /** Position of a type id in {@link #CATEGORY_ORDER}, or a large value if unlisted. */
    private static int categoryRank(@NotNull String typeId) {
        final int i = CATEGORY_ORDER.indexOf(typeId.toLowerCase(java.util.Locale.ROOT));
        return i < 0 ? Integer.MAX_VALUE : i;
    }

    /** Groups an analysis into categories (EcoEnchants types), sorted by rarity then name. */
    @NotNull
    public static List<CategoryOffer> getCategories(@NotNull List<AnalyzedEnchant> analysis) {
        final Map<String, CategoryOffer> byType = new java.util.LinkedHashMap<>();
        for (AnalyzedEnchant e : analysis) {
            byType.merge(e.typeId(),
                    new CategoryOffer(e.typeId(), e.categoryName(), 1),
                    (a, b) -> new CategoryOffer(a.typeId(), a.displayName(), a.count() + 1));
        }
        final List<CategoryOffer> categories = new ArrayList<>(byType.values());
        categories.sort(Comparator.comparingInt((CategoryOffer c) -> categoryRank(c.typeId()))
                .thenComparing(CategoryOffer::displayName, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(categories);
    }

    /** Filters an analysis to one category, available first, then owned, then by name. */
    @NotNull
    public static List<AnalyzedEnchant> enchantsInCategory(@NotNull List<AnalyzedEnchant> analysis,
                                                           @NotNull String typeId) {
        final List<AnalyzedEnchant> out = new ArrayList<>();
        for (AnalyzedEnchant e : analysis) {
            if (e.typeId().equalsIgnoreCase(typeId)) {
                out.add(e);
            }
        }
        out.sort(Comparator
                .comparingInt((AnalyzedEnchant e) -> e.isAvailable() ? 0 : 1)
                .thenComparingInt(e -> e.currentLevel() > 0 ? 0 : 1)
                .thenComparing(AnalyzedEnchant::displayName, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(out);
    }

    // ── Block-reason helpers ────────────────────────────────────────────────

    private static List<Enchantment> missingRequired(@NotNull Enchantment ench,
                                                     @NotNull Set<Enchantment> present,
                                                     @NotNull EcoEnchantsHook eco) {
        final List<Enchantment> required = eco.getRequired(ench);
        if (required.isEmpty()) {
            return List.of();
        }
        final List<Enchantment> missing = new ArrayList<>();
        for (Enchantment r : required) {
            if (!present.contains(r)) {
                missing.add(r);
            }
        }
        return missing;
    }

    private static List<Enchantment> conflictingPresent(@NotNull Enchantment ench,
                                                        @NotNull Set<Enchantment> present,
                                                        @NotNull EcoEnchantsHook eco) {
        if (present.isEmpty()) {
            return List.of();
        }
        final boolean enchAll = eco.conflictsWithEverything(ench);
        final Set<Enchantment> enchConflicts = new HashSet<>(eco.getConflicts(ench));
        final List<Enchantment> out = new ArrayList<>();
        for (Enchantment other : present) {
            if (other.equals(ench)) {
                continue;
            }
            final boolean conflict = enchAll
                    || eco.conflictsWithEverything(other)
                    || enchConflicts.contains(other)
                    || eco.getConflicts(other).contains(ench)
                    || ench.conflictsWith(other) || other.conflictsWith(ench);
            if (conflict) {
                out.add(other);
            }
        }
        return out;
    }

    private static boolean typeLimitReached(@NotNull Enchantment ench,
                                            @NotNull Set<Enchantment> present,
                                            @NotNull EcoEnchantsHook eco) {
        final int limit = eco.getTypeLimit(ench);
        if (limit == Integer.MAX_VALUE) {
            return false;
        }
        final String typeId = eco.getTypeId(ench);
        if (typeId == null) {
            return false;
        }
        int count = 0;
        for (Enchantment other : present) {
            if (!other.equals(ench) && typeId.equalsIgnoreCase(eco.getTypeId(other))) {
                count++;
            }
        }
        return count >= limit;
    }

    private static List<String> names(@NotNull List<Enchantment> enchantments,
                                      @NotNull EcoEnchantsHook eco) {
        final List<String> out = new ArrayList<>(enchantments.size());
        for (Enchantment e : enchantments) {
            String n = eco.getDisplayName(e, 0);
            out.add(n.isEmpty() ? EnchantmentHelper.prettyName(e) : n);
        }
        return out;
    }

    @NotNull
    public static List<LevelEnchantmentOffer> getEnchantmentLevels(
            @NotNull ItemStack item,
            @NotNull Enchantment enchantment,
            int bookshelfPower,
            @NotNull dev.scrulius.superenchanter.SuperEnchanterPlugin plugin) {
        
        PluginConfig config = plugin.getPluginConfig();
        final PluginConfig.EnchantmentOverride override = config.getEnchantmentOverride(enchantment);
        final int currentLevel = EnchantmentHelper.getEnchantments(item).getOrDefault(enchantment, 0);
        final int rawMax = EnchantmentHelper.getMaxLevel(enchantment);
        // A per-enchant override can cap the maximum offered level.
        final int maxLevel = override.maxLevel() != null
                ? Math.max(1, Math.min(rawMax, override.maxLevel()))
                : rawMax;

        String displayName = plugin.getEcoHook().getDisplayName(enchantment, 0);
        if (displayName.isEmpty()) {
            displayName = EnchantmentHelper.prettyName(enchantment);
        }

        // Rarity drives cost, required power and the reagent — magnitude, not level fraction.
        final String rarityId = plugin.getEcoHook().getRarityId(enchantment);
        final double rarityMultiplier = config.getRarityCostMultiplier(rarityId);
        final PluginConfig.RarityPower rarityPower = config.getRarityPower(rarityId);
        final Reagent baseReagent = config.getReagent(rarityId);

        final double exponent = config.getCostExponent();
        final int costCap = config.getMaxXpCost();
        final int maxPower = config.getMaxBookshelfPower();
        final boolean scaleReagent = config.isReagentScalingWithLevel();

        final List<LevelEnchantmentOffer> offers = new ArrayList<>();

        // Per-rarity currency override (e.g. divino → PlayerPoints) falls back to global.
        final var costType = config.getEnchantingCostType(rarityId);

        // Pre-compute the per-level STEP cost (flat override wins; otherwise the
        // rarity-scaled, capped curve × the per-enchant multiplier). The offer for
        // a target level then charges the CUMULATIVE sum from currentLevel+1 to it,
        // so leaping to level V costs the same as forging each step by hand —
        // making the curve a genuine sink instead of a single final-level toll.
        final int[] perLevelCost = new int[maxLevel];
        for (int lvl = 1; lvl <= maxLevel; lvl++) {
            if (override.xpCost() != null) {
                perLevelCost[lvl - 1] = Math.max(1, override.xpCost());
            } else {
                final int curve = EnchantFormulas.xpCostForLevel(config.getBaseXPCost(),
                        config.getLevelXPMultiplier(), lvl, exponent, rarityMultiplier, costCap);
                perLevelCost[lvl - 1] = Math.max(1, (int) Math.round(curve * override.costMultiplier()));
            }
        }

        for (int lvl = 1; lvl <= maxLevel; lvl++) {
            final int amount = EnchantFormulas.cumulativeCost(perLevelCost, currentLevel, lvl);
            final Cost cost = new Cost(costType, amount);
            // Single-level (step) cost — what one rung costs in the per-step roll.
            final Cost stepCost = new Cost(costType, perLevelCost[lvl - 1]);

            // Power: a per-enchant override wins; otherwise rarity floor + per-level step.
            final int requiredPower = override.requiredPower() != null
                    ? Math.max(0, override.requiredPower())
                    : EnchantFormulas.requiredPowerForLevel(
                            rarityPower.floor(), rarityPower.step(), lvl, maxPower);

            final Reagent reagent = scaledReagent(baseReagent, lvl, scaleReagent);

            final boolean alreadyApplied = lvl <= currentLevel;
            final boolean hasEnoughPower = bookshelfPower >= requiredPower;

            offers.add(new LevelEnchantmentOffer(
                    enchantment,
                    displayName,
                    lvl,
                    maxLevel,
                    rarityId,
                    cost,
                    stepCost,
                    requiredPower,
                    reagent,
                    alreadyApplied,
                    hasEnoughPower
            ));
        }
        return List.copyOf(offers);
    }

    @NotNull
    public static ItemStack applyEnchantment(@NotNull ItemStack item,
                                             @NotNull Enchantment enchant,
                                             int newLevel) {
        return EnchantmentHelper.applyEnchantment(item, enchant, newLevel);
    }

    @NotNull
    public static ItemStack createCategoryIcon(@NotNull CategoryOffer category,
                                               @NotNull dev.scrulius.superenchanter.SuperEnchanterPlugin plugin) {
        MessagesConfig msg = plugin.getMessages();
        return new ItemBuilder(plugin.getPluginConfig().getCategoryIcon(category.typeId()))
                .name(msg.format("enchanting.category-name", Map.of("{name}", category.displayName())))
                .lore(msg.formatList("enchanting.category-lore",
                        Map.of("{count}", String.valueOf(category.count()))))
                .glow()
                .build();
    }

    /**
     * Appends an enchantment's declared usage-requirement lines (e.g. "Requiere Combate XV",
     * from a {@code has_skill_level} gate's {@code not-met-lines}) to its icon description
     * block, so the player sees the requirement <em>before</em> enchanting. No-op when the
     * enchantment declares none.
     */
    private static void appendRequirementLines(@NotNull List<String> description,
                                               @NotNull org.bukkit.enchantments.Enchantment enchantment,
                                               @NotNull dev.scrulius.superenchanter.SuperEnchanterPlugin plugin) {
        final List<String> requirements = plugin.getEcoHook().getRequirementLines(enchantment);
        if (requirements.isEmpty()) {
            return;
        }
        if (!description.isEmpty()) {
            description.add("");
        }
        description.addAll(requirements);
    }

    @NotNull
    public static ItemStack createEnchantIcon(@NotNull AnalyzedEnchant offer,
                                              @NotNull Player player,
                                              @NotNull dev.scrulius.superenchanter.SuperEnchanterPlugin plugin) {
        MessagesConfig msg = plugin.getMessages();
        List<String> description = new ArrayList<>(
                plugin.getEcoHook().getDescription(offer.enchantment(), offer.currentLevel(), player));
        if (description.isEmpty()) {
            final String descKey = "enchantment-descriptions." + offer.enchantment().getKey().getKey();
            if (msg.hasKey(descKey)) {
                description.addAll(msg.rawList(descKey));
            }
        }
        appendRequirementLines(description, offer.enchantment(), plugin);

        // ── Maxed (owned at max level) ──────────────────────────────────
        if (offer.reason() == BlockReason.MAXED) {
            final String roman = offer.maxLevel() > 1 ? " " + toRoman(offer.currentLevel()) : "";
            final List<String> lore = new ArrayList<>();
            if (!description.isEmpty()) {
                lore.addAll(description);
                lore.add("");
            }
            lore.addAll(msg.rawList("enchant-icons.maxed-lore"));
            return new ItemBuilder(Material.ENCHANTED_BOOK)
                    .name(msg.format("enchant-icons.maxed-name", Map.of("{name}", offer.displayName() + roman)))
                    .lore(lore)
                    .glow()
                    .build();
        }

        // ── Blocked — explain why ───────────────────────────────────────
        if (!offer.isAvailable()) {
            final List<String> lore = new ArrayList<>();
            if (!description.isEmpty()) {
                lore.addAll(description);
                lore.add("");
            }
            final String joined = String.join(", ", offer.reasonDetail());
            switch (offer.reason()) {
                case CONFLICT -> lore.add(msg.format("enchant-icons.blocked-conflict",
                        Map.of("{enchantments}", joined)));
                case MISSING_REQUIRED -> lore.add(msg.format("enchant-icons.blocked-required",
                        Map.of("{enchantments}", joined)));
                case TYPE_LIMIT -> lore.add(msg.raw("enchant-icons.blocked-type-limit"));
                default -> { }
            }
            return new ItemBuilder(Material.GRAY_DYE)
                    .name(msg.format("enchant-icons.blocked-name", Map.of("{name}", offer.displayName())))
                    .lore(lore)
                    .build();
        }

        // ── Available (possibly already owned and upgradeable) ──────────
        final List<String> lore = new ArrayList<>();
        if (!description.isEmpty()) {
            lore.addAll(description);
            lore.add("");
        }
        final String rarity = rarityDisplayName(plugin, offer.rarityId());
        if (!rarity.isEmpty()) {
            lore.add(msg.format("enchant-icons.lore-rarity", Map.of("{rarity}", rarity)));
        }
        // Level status — only meaningful for multi-level enchantments.
        if (offer.maxLevel() > 1) {
            lore.add(msg.format("enchant-icons.lore-level", Map.of(
                    "{current}", offer.currentLevel() > 0 ? toRoman(offer.currentLevel()) : "0",
                    "{max}", toRoman(offer.maxLevel()))));
        }
        lore.add("");
        lore.add(msg.raw("enchant-icons.tier1-lore"));

        return new ItemBuilder(Material.ENCHANTED_BOOK)
                .name(msg.format("enchant-icons.available-name", Map.of("{name}", offer.displayName())))
                .lore(lore)
                .build();
    }

    @NotNull
    public static ItemStack createLevelOfferIcon(@NotNull LevelEnchantmentOffer offer,
                                                 @NotNull Player player,
                                                 int boosterPercent,
                                                 @Nullable String sealRarityId,
                                                 @NotNull dev.scrulius.superenchanter.SuperEnchanterPlugin plugin) {
        MessagesConfig msg = plugin.getMessages();
        // Single-level enchantments show no roman numeral ("Vitalidad", not "Vitalidad I").
        final String levelText = offer.levelSuffix();
        final var magia = plugin.getMagiaService();

        // Hard gate (Magia carril 5): a locked rarity (legendario/divino) shows a barrier
        // with the required level UP FRONT, instead of a clickable offer that bounces on
        // click. Maxed items skip this (nothing left to enchant).
        if (!offer.alreadyApplied() && magia != null && magia.isEnabled()
                && !magia.canEnchant(player, offer.rarityId())) {
            final List<String> lockedLore = msg.formatList("enchant-icons.locked-lore",
                    Map.of("{required}", String.valueOf(magia.requiredLevel(offer.rarityId())),
                            "{level}", String.valueOf(magia.level(player))));
            return new ItemBuilder(Material.BARRIER)
                    .name(msg.format("enchant-icons.locked-name",
                            Map.of("{name}", offer.displayName() + levelText)))
                    .lore(lockedLore)
                    .build();
        }

        // Discounted cost actually shown/charged for this player (permission + Carril 2 Magia).
        Cost cost = plugin.getCostService().effectiveCost(player, offer.cost());
        if (magia != null && magia.isEnabled()) cost = magia.applyDiscount(player, cost);
        final boolean canAfford = plugin.getCostService().canAfford(player, cost);
        final boolean hasReagent = hasReagent(player, offer.reagent());

        List<String> description = new ArrayList<>(
                plugin.getEcoHook().getDescription(offer.enchantment(), offer.level(), player));
        if (description.isEmpty()) {
            final String descKey = "enchantment-descriptions." + offer.enchantment().getKey().getKey();
            if (msg.hasKey(descKey)) {
                description.addAll(msg.rawList(descKey));
            }
        }
        appendRequirementLines(description, offer.enchantment(), plugin);

        if (offer.alreadyApplied()) {
            final List<String> lore = new ArrayList<>();
            if (!description.isEmpty()) {
                lore.addAll(description);
                lore.add("");
            }
            lore.addAll(msg.rawList("enchant-icons.already-applied-lore"));
            return new ItemBuilder(Material.ENCHANTED_BOOK)
                    .name(msg.format("enchant-icons.maxed-name",
                            Map.of("{name}", offer.displayName() + levelText)))
                    .lore(lore)
                    .glow()
                    .build();
        }

        if (canAfford && offer.hasEnoughPower() && hasReagent) {
            final List<String> lore = new ArrayList<>();
            if (!description.isEmpty()) {
                lore.addAll(description);
                lore.add("");
            }
            final String rarity = rarityDisplayName(plugin, offer.rarityId());
            if (!rarity.isEmpty()) {
                lore.add(msg.format("enchant-icons.lore-rarity", Map.of("{rarity}", rarity)));
                lore.add("");
            }
            appendChanceLore(lore, offer, boosterPercent, sealRarityId, player, plugin);
            lore.add(msg.format("enchant-icons.available-lore-cost",
                    Map.of("{cost}", cost.displayText())));
            lore.add(msg.format("enchant-icons.available-lore-power",
                    Map.of("{power}", String.valueOf(offer.requiredPower()))));
            if (requiresReagent(offer.reagent())) {
                lore.add(msg.format("enchant-icons.available-lore-reagent", reagentPlaceholders(offer.reagent())));
            }
            // Magia bonuses are no longer listed per-icon (cost/chance already show them applied);
            // the full breakdown lives in the player stats head (see EnchantingGUI#buildStatsHead).
            lore.add("");
            lore.add(msg.raw("enchant-icons.available-lore-button"));

            return new ItemBuilder(Material.ENCHANTED_BOOK)
                    .name(msg.format("enchant-icons.available-name",
                            Map.of("{name}", offer.displayName() + levelText)))
                    .lore(lore)
                    .glow()
                    .build();
        }

        final List<String> lore = new ArrayList<>();
        if (!description.isEmpty()) {
            lore.addAll(description);
            lore.add("");
        }
        if (!canAfford) {
            lore.add(msg.format("enchant-icons.unavailable-xp-fail",
                    Map.of("{cost}", cost.displayText())));
            lore.add(msg.format("enchant-icons.unavailable-xp-fail-current",
                    Map.of("{balance}", plugin.getCostService().balanceText(player, cost.type()))));
        } else {
            lore.add(msg.raw("enchant-icons.unavailable-xp-ok"));
        }

        if (!offer.hasEnoughPower()) {
            lore.add(msg.format("enchant-icons.unavailable-power-fail",
                    Map.of("{required}", String.valueOf(offer.requiredPower()))));
        } else {
            lore.add(msg.raw("enchant-icons.unavailable-power-ok"));
        }

        if (requiresReagent(offer.reagent())) {
            final String key = hasReagent ? "enchant-icons.unavailable-reagent-ok"
                    : "enchant-icons.unavailable-reagent-fail";
            lore.add(msg.format(key, reagentPlaceholders(offer.reagent())));
        }

        appendChanceLore(lore, offer, boosterPercent, sealRarityId, player, plugin);
        lore.add("");
        lore.add(msg.raw("enchant-icons.unavailable-footer"));

        return new ItemBuilder(Material.GRAY_DYE)
                .name(msg.format("enchant-icons.unavailable-name",
                        Map.of("{name}", offer.displayName() + levelText)))
                .lore(lore)
                .build();
    }

    @NotNull
    public static String toRoman(int number) {
        return EnchantFormulas.toRoman(number);
    }

    /**
     * Appends the success-chance lore line to a level-offer icon when probabilistic
     * enchanting is enabled. Shows the effective chance, and when a potentiator is
     * applied also breaks down the base + booster contribution.
     */
    private static void appendChanceLore(@NotNull List<String> lore,
                                         @NotNull LevelEnchantmentOffer offer,
                                         int boosterPercent,
                                         @Nullable String sealRarityId,
                                         @NotNull Player player,
                                         @NotNull dev.scrulius.superenchanter.SuperEnchanterPlugin plugin) {
        final PluginConfig config = plugin.getPluginConfig();
        if (!config.isSuccessChanceEnabled()) {
            return;
        }
        final MessagesConfig msg = plugin.getMessages();
        final var magia = plugin.getMagiaService();
        final int magiaBonus = (magia != null && magia.isEnabled()) ? magia.successBonus(player) : 0;
        final int base = config.getBaseSuccessChance(offer.rarityId());
        // The shown chance MUST match the roll in handleLevelClick (base + booster + Magia).
        final int effective = EnchantFormulas.effectiveChance(base, boosterPercent + magiaBonus);
        if (boosterPercent > 0) {
            lore.add(msg.format("enchant-icons.lore-chance-boosted", Map.of(
                    "{chance}", String.valueOf(effective),
                    "{base}", String.valueOf(base),
                    "{booster}", String.valueOf(boosterPercent))));
        } else {
            lore.add(msg.format("enchant-icons.lore-chance",
                    Map.of("{chance}", String.valueOf(effective))));
            // A seal sitting in the slot that targets a *different* rarity contributes 0
            // and isn't consumed. Without this line the player just sees no boost and
            // assumes the seal is broken — tell them which rarity it actually serves.
            if (sealRarityId != null) {
                lore.add(msg.format("enchant-icons.lore-seal-mismatch",
                        Map.of("{rarity}", rarityDisplayName(plugin, sealRarityId))));
            }
        }
    }

    // ── Reagent handling ────────────────────────────────────────────────────

    /** @return {@code true} if the reagent is non-null and actually requires items */
    public static boolean requiresReagent(@Nullable Reagent reagent) {
        return reagent != null && !reagent.isEmpty();
    }

    /**
     * Returns the reagent scaled for a given level. When scaling is enabled the
     * required amount is multiplied by the level (level V costs 5× a level I).
     */
    @Nullable
    private static Reagent scaledReagent(@Nullable Reagent base, int level, boolean scale) {
        if (base == null || base.isEmpty() || !scale || level <= 1) {
            return base;
        }
        return new Reagent(base.material(), base.amount() * level, base.customModelData());
    }

    /** @return {@code true} if the player carries enough of the reagent (or none is required) */
    public static boolean hasReagent(@NotNull Player player, @Nullable Reagent reagent) {
        if (!requiresReagent(reagent)) {
            return true;
        }
        return countReagent(player, reagent) >= reagent.amount();
    }

    /**
     * Consumes the reagent from the player's inventory. Returns {@code false}
     * (and consumes nothing) if the player does not carry enough.
     *
     * @param player  the player to charge
     * @param reagent the reagent to consume (may be {@code null}/empty → no-op success)
     * @return whether the reagent was successfully consumed
     */
    public static boolean consumeReagent(@NotNull Player player, @Nullable Reagent reagent) {
        if (!requiresReagent(reagent)) {
            return true;
        }
        if (countReagent(player, reagent) < reagent.amount()) {
            return false;
        }
        int remaining = reagent.amount();
        final ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            final ItemStack stack = contents[i];
            if (!matchesReagent(stack, reagent)) {
                continue;
            }
            final int take = Math.min(stack.getAmount(), remaining);
            stack.setAmount(stack.getAmount() - take);
            remaining -= take;
            if (stack.getAmount() <= 0) {
                contents[i] = null;
            }
        }
        player.getInventory().setStorageContents(contents);
        return true;
    }

    private static int countReagent(@NotNull Player player, @NotNull Reagent reagent) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (matchesReagent(stack, reagent)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    @SuppressWarnings("UnstableApiUsage")
    private static boolean matchesReagent(@Nullable ItemStack stack, @NotNull Reagent reagent) {
        if (stack == null || stack.getType() != reagent.material()) {
            return false;
        }
        if (reagent.customModelData() == null) {
            return true;
        }
        var cmd = stack.getData(DataComponentTypes.CUSTOM_MODEL_DATA);
        return cmd != null && cmd.floats().contains((float) reagent.customModelData());
    }

    @NotNull
    public static Map<String, String> reagentPlaceholders(@Nullable Reagent reagent) {
        if (reagent == null) {
            return Map.of("{amount}", "0", "{item}", "");
        }
        return Map.of(
                "{amount}", String.valueOf(reagent.amount()),
                "{item}", prettyMaterial(reagent.material()));
    }

    @NotNull
    private static String prettyMaterial(@NotNull Material material) {
        return prettyId(material.name());
    }

    /**
     * Title-cases a snake_case / lowercase id (e.g. {@code wither_skeleton} or
     * {@code curse}) into a display string ({@code Wither Skeleton} / {@code Curse}).
     */
    @NotNull
    private static String prettyId(@NotNull String raw) {
        final String[] words = raw.split("_");
        final StringBuilder sb = new StringBuilder(raw.length());
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                sb.append(word.substring(1).toLowerCase());
            }
        }
        return sb.toString();
    }
}
