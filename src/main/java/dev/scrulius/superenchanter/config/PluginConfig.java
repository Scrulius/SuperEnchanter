/* SuperEnchanter - Author: Scrulius (GitHub) */
package dev.scrulius.superenchanter.config;

import dev.scrulius.superenchanter.economy.CostType;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Typed wrapper around the plugin's {@link FileConfiguration}.
 * <p>
 * Provides strongly-typed accessors for all configuration values, eliminating
 * raw string lookups scattered throughout the codebase.
 * </p>
 */
public final class PluginConfig {

    private final JavaPlugin plugin;
    private FileConfiguration config;

    // ── Anvil settings (cached) ──
    private String anvilDefaultCostType;
    private int anvilBaseXPCost;
    private int anvilCostPerLevel;
    private int anvilMaxCost;
    private boolean chainForgingEnabled;
    private List<CostOverride> anvilCostOverrides;

    // ── Enchanting settings (cached) ──
    private int scanRadiusH;
    private int scanRadiusV;
    private boolean airGapRequired;
    private int maxBookshelfPower;
    private int vanillaPowerCap;
    private Map<String, Integer> powerValues;
    private CostType enchantingCostType;
    private Map<String, CostType> rarityCostTypes;
    private int baseXPCost;
    private int levelXPMultiplier;
    private double costExponent;
    private int maxXpCost;
    private int enchantsPerPage;
    private List<String> disabledEnchantments;
    private Map<String, Double> rarityCostMultipliers;
    private double defaultRarityMultiplier;
    private Map<String, Reagent> rarityReagents;
    private Reagent defaultReagent;
    private boolean reagentScalesWithLevel;
    private Map<String, RarityPower> rarityPower;
    private RarityPower defaultRarityPower;
    private Map<String, Integer> enchantedBookshelves;
    private Map<String, Material> categoryIcons;
    private Material defaultCategoryIcon;
    private Map<String, EnchantmentOverride> enchantmentOverrides;

    // ── Probabilistic enchanting (cached) ──
    private boolean successChanceEnabled;
    private int defaultSuccessChance;
    private boolean boostersConsumedOnSuccessOnly;
    private Map<String, Integer> raritySuccessChance;
    private Map<String, Booster> successBoosters;

    // ── Transfer settings (cached) ──
    private boolean transferEnabled;
    private boolean transferAllowExtract;
    private double transferExtractMultiplier;
    private CostType transferCostType;
    private int transferBaseCost;
    private int transferLevelMultiplier;
    private double transferCostExponent;
    private int transferMaxCost;
    private boolean transferUseRarityMultiplier;
    private boolean transferRequireSameMaterial;

    // ── Cost discounts (cached) ──
    private boolean costDiscountsEnabled;

    // ── Anti-dupe settings (cached) ──
    private int cooldownTicks;
    private boolean crashPersistenceEnabled;

    // ── Audit log (cached) ──
    private boolean auditLogEnabled;
    private boolean auditLogToConsole;
    private int auditMaxFileKb;

    // ── General (cached) — worlds where the custom GUIs are NOT used (vanilla stays) ──
    private java.util.Set<String> guiDisabledWorlds = java.util.Collections.emptySet();

    // ── Fail-safe (cached) ──
    private boolean shutdownOnCriticalError;

    // ── Curse chance (cached) — enchanting can curse (no prevention, cured in anvil) ──
    private boolean curseChanceEnabled;
    private double curseBasePercent;
    private Map<String, Double> curseRarityChance = java.util.Collections.emptyMap();
    /** Curse enchantment key-paths the roll must never pick (e.g. vanishing under keepinventory). */
    private java.util.Set<String> curseExcluded = java.util.Collections.emptySet();

    // ── Anvil curse removal (Sello Purificador) ──
    private boolean curseRemovalEnabled;
    private java.util.Set<String> curseRemovalSealIds = java.util.Collections.emptySet();

    // ── Loot control (cached) — no "free" enchantments from natural loot ──
    private boolean lootControlEnabled;
    private boolean lootRemoveEnchantedBooks;
    private boolean lootStripEquipmentEnchantments;
    private boolean lootIncludeMobDrops;
    private java.util.Set<String> lootDisabledWorlds = java.util.Collections.emptySet();

    // ── Banned enchantments (cached) — purged from the whole world (default: mending) ──
    private boolean bannedEnchantmentsEnabled;
    private boolean bannedPurgeInventories;
    private boolean bannedBlockXpRepair;
    private java.util.Set<String> bannedEnchantmentKeys = java.util.Collections.emptySet();

    // ── Villager trades (cached) ──
    private boolean villagerTradesEnabled;
    private boolean villagerBlockBookTrades;

    // ── Sounds (cached) ──
    private SoundEffect anvilSuccessSound;
    private SoundEffect enchantSuccessSound;
    private SoundEffect enchantFailSound;
    private SoundEffect transferSuccessSound;
    private SoundEffect extractSuccessSound;
    private SoundEffect errorSound;
    private SoundEffect guiOpenSound;
    private SoundEffect guiCloseSound;
    private SoundEffect buttonClickSound;

    // ── Particles (cached) ──
    private ParticleEffect enchantSuccessParticle;
    private ParticleEffect enchantFailParticle;
    private ParticleEffect libraryAmbientParticle;
    private long libraryAmbientPeriodTicks;

    /**
     * Typed record for per-item cost overrides defined in the configuration.
     *
     * @param matchType the match strategy (PDC_KEY, MATERIAL, CUSTOM_MODEL_DATA)
     * @param namespace the PDC namespace (may be empty for non-PDC matches)
     * @param key       the PDC key (may be empty for non-PDC matches)
     * @param value     the value to match against
     * @param costType  the cost currency type (XP, VAULT, PLAYER_POINTS)
     * @param amount    the amount to charge
     */
    public record CostOverride(@NotNull String matchType,
                                @NotNull String namespace,
                                @NotNull String key,
                                @NotNull String value,
                                @NotNull String costType,
                                double amount) {
    }

    /**
     * A material reagent consumed when enchanting, resolved per EcoEnchants rarity.
     *
     * @param material        the required material
     * @param amount          how many to consume
     * @param customModelData optional custom model data the item must carry, or {@code null} for any
     */
    public record Reagent(@NotNull Material material, int amount, @Nullable Integer customModelData) {
        /** @return {@code true} when this reagent requires nothing (no material or non-positive amount) */
        public boolean isEmpty() {
            return amount <= 0;
        }
    }

    /**
     * Bookshelf-power requirement for a rarity: {@code floor + level * step},
     * capped at {@code max-bookshelf-power}. The floor makes a rarity demand a
     * minimum power even at level I, so power gates <em>magnitude</em> (rarity),
     * not just the level fraction.
     *
     * @param floor base power required at any level of this rarity
     * @param step  extra power required per level
     */
    public record RarityPower(int floor, int step) {}

    /**
     * A potentiator item ("orb") for probabilistic enchanting.
     * <p>
     * It adds {@code percent} points to the success chance, but only for
     * enchantments whose rarity matches {@code rarity}. A {@code null} {@code rarity}
     * is a universal orb (applies to any rarity). A {@code percent} of {@code 100}
     * (or more) on its matching rarity is a per-rarity <em>guarantee orb</em>:
     * an "orbe raro" makes a rare enchantment land 100% of the time.
     *
     * @param rarity  the EcoEnchants rarity id this orb targets, or {@code null} for any
     * @param percent the percentage points it contributes (100 = guarantee)
     */
    public record Booster(@Nullable String rarity, int percent) {
        /**
         * @param enchantRarity the rarity id of the enchantment being attempted
         * @return {@code percent} when this orb applies to that rarity, else {@code 0}
         */
        public int percentFor(@Nullable String enchantRarity) {
            if (rarity == null) {
                return percent; // universal orb
            }
            return rarity.equalsIgnoreCase(enchantRarity) ? percent : 0;
        }
    }

    /**
     * Per-enchantment override (under {@code enchanting.enchantment-overrides}).
     * Any field may be {@code null} to keep the computed default.
     *
     * @param maxLevel       caps the highest offered level
     * @param xpCost         flat cost per level (wins over the curve)
     * @param requiredPower  flat bookshelf-power requirement per level
     * @param costMultiplier multiplies the curve cost (ignored when {@code xpCost} is set)
     */
    public record EnchantmentOverride(@Nullable Integer maxLevel, @Nullable Integer xpCost,
                                      @Nullable Integer requiredPower, double costMultiplier) {
        /** The no-op override (everything computed normally). */
        public static final EnchantmentOverride NONE = new EnchantmentOverride(null, null, null, 1.0);
    }

    /**
     * Creates and immediately loads the configuration wrapper.
     *
     * @param plugin the owning plugin
     */
    public PluginConfig(@NotNull JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    /**
     * Reloads the configuration from disk and refreshes all cached values.
     */
    public void reload() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
        dev.scrulius.superenchanter.util.ConfigUpdater.merge(plugin, "config.yml",
                new java.io.File(plugin.getDataFolder(), "config.yml"), config);
        loadAnvilSettings();
        loadEnchantingSettings();
        loadTransferSettings();
        loadAntiDupeSettings();
        loadLootControlSettings();
        loadBannedEnchantmentSettings();
        loadVillagerTradeSettings();
        loadGeneralSettings();
        loadSounds();
        loadParticles();
    }

    private void loadGeneralSettings() {
        java.util.Set<String> worlds = new java.util.HashSet<>();
        for (String w : config.getStringList("general.gui-disabled-worlds")) {
            if (w != null && !w.isBlank()) {
                worlds.add(w.toLowerCase(Locale.ROOT));
            }
        }
        guiDisabledWorlds = worlds;
    }

    /**
     * Whether the custom GUIs (anvil / enchanting table / grindstone) should be
     * skipped in the given world, leaving vanilla behaviour intact there.
     *
     * @param worldName the world name
     * @return {@code true} if the custom GUIs are disabled for that world
     */
    public boolean isGuiWorldDisabled(@NotNull String worldName) {
        return guiDisabledWorlds.contains(worldName.toLowerCase(Locale.ROOT));
    }

    // ────────────────────────────────────────────────────────────
    //  Anvil Accessors
    // ────────────────────────────────────────────────────────────

    /** @return the default cost type for anvil operations (XP, VAULT, PLAYER_POINTS) */
    public @NotNull String getAnvilDefaultCostType() { return anvilDefaultCostType; }

    /** @return the base XP level cost for anvil operations */
    public int getAnvilBaseXPCost() { return anvilBaseXPCost; }

    /** @return the XP cost added per enchantment level */
    public int getAnvilCostPerLevel() { return anvilCostPerLevel; }

    /** @return the maximum XP level cost cap for a single anvil operation */
    public int getAnvilMaxCost() { return anvilMaxCost; }

    /** @return whether chain-forging (keeping result in slot 1) is enabled */
    public boolean isChainForgingEnabled() { return chainForgingEnabled; }

    /** @return an unmodifiable list of per-item cost overrides */
    public @NotNull List<CostOverride> getAnvilCostOverrides() {
        return Collections.unmodifiableList(anvilCostOverrides);
    }

    // ────────────────────────────────────────────────────────────
    //  Enchanting Accessors
    // ────────────────────────────────────────────────────────────

    /** @return the horizontal scan radius for bookshelf detection */
    public int getScanRadiusH() { return scanRadiusH; }

    /** @return the vertical scan radius for bookshelf detection */
    public int getScanRadiusV() { return scanRadiusV; }

    /** @return whether an air gap between the enchanting table and bookshelves is required */
    public boolean isAirGapRequired() { return airGapRequired; }

    /** @return the maximum bookshelf power cap */
    public int getMaxBookshelfPower() { return maxBookshelfPower; }

    /** @return the cap on power contributed by vanilla (unmarked) bookshelves */
    public int getVanillaPowerCap() { return vanillaPowerCap; }

    /** @return a map of block identifiers to their bookshelf power contribution */
    public @NotNull Map<String, Integer> getPowerValues() {
        return Collections.unmodifiableMap(powerValues);
    }

    /** @return the global currency the enchanting table charges in (default {@link CostType#XP}) */
    public @NotNull CostType getEnchantingCostType() { return enchantingCostType; }

    /**
     * Returns the currency the enchanting table charges in for an enchantment of a
     * given rarity. A per-rarity override (under {@code enchanting.rarity-cost-type})
     * wins; otherwise the global {@link #getEnchantingCostType()} is used. This is
     * how a premium tier (e.g. {@code divino}) can be bought only with the server's
     * paid currency (PlayerPoints) while the rest stays in XP.
     *
     * @param rarityId the EcoEnchants rarity id (case-insensitive), may be {@code null}
     * @return the cost currency for that rarity, never {@code null}
     */
    public @NotNull CostType getEnchantingCostType(@Nullable String rarityId) {
        if (rarityId == null) {
            return enchantingCostType;
        }
        return rarityCostTypes.getOrDefault(rarityId.toLowerCase(Locale.ROOT), enchantingCostType);
    }

    /** @return the base XP cost for enchanting table operations */
    public int getBaseXPCost() { return baseXPCost; }

    /** @return the XP cost multiplied by the enchantment level */
    public int getLevelXPMultiplier() { return levelXPMultiplier; }

    /** @return the exponent applied to the level in the cost curve (1.0 = linear) */
    public double getCostExponent() { return costExponent; }

    /** @return the hard cap on XP-level cost for a single enchant operation */
    public int getMaxXpCost() { return maxXpCost; }

    /** @return whether the reagent amount scales with the enchantment level */
    public boolean isReagentScalingWithLevel() { return reagentScalesWithLevel; }

    /**
     * @param rarityId the EcoEnchants rarity id (case-insensitive), may be {@code null}
     * @return the power floor/step for that rarity, or the default if unmapped
     */
    public @NotNull RarityPower getRarityPower(@Nullable String rarityId) {
        if (rarityId == null) {
            return defaultRarityPower;
        }
        return rarityPower.getOrDefault(rarityId.toLowerCase(Locale.ROOT), defaultRarityPower);
    }

    /**
     * @param mythicId the MythicMobs item id of a placed library (case-insensitive)
     * @return the bookshelf power that library grants, or {@code null} if the id is
     *         not a configured enchanted library
     */
    public @Nullable Integer getEnchantedBookshelfPower(@Nullable String mythicId) {
        if (mythicId == null) {
            return null;
        }
        return enchantedBookshelves.get(mythicId.toLowerCase(Locale.ROOT));
    }

    /** @return the configured enchanted-library MythicMobs ids (lowercase, unmodifiable) */
    public @NotNull java.util.Set<String> getEnchantedBookshelfIds() {
        return Collections.unmodifiableSet(enchantedBookshelves.keySet());
    }

    // ────────────────────────────────────────────────────────────
    //  Probabilistic enchanting Accessors
    // ────────────────────────────────────────────────────────────

    /** @return whether enchant attempts can fail (probabilistic enchanting) */
    public boolean isSuccessChanceEnabled() { return successChanceEnabled; }

    /**
     * @param rarityId the EcoEnchants rarity id (case-insensitive), may be {@code null}
     * @return the base success chance (0–100) for that rarity, or the default
     */
    public int getBaseSuccessChance(@Nullable String rarityId) {
        if (rarityId == null) {
            return defaultSuccessChance;
        }
        return raritySuccessChance.getOrDefault(rarityId.toLowerCase(Locale.ROOT), defaultSuccessChance);
    }

    /**
     * Returns how many success-chance points the given potentiator item contributes
     * for an enchantment of a given rarity. A rarity-targeted orb only contributes
     * when {@code enchantRarity} matches its rarity (otherwise {@code 0}); a universal
     * orb always contributes its percent.
     *
     * @param mythicId      the MythicMobs id of the orb (case-insensitive), may be {@code null}
     * @param enchantRarity the EcoEnchants rarity id of the enchantment being attempted
     * @return the points contributed, or {@code 0} if not a configured orb / wrong rarity
     */
    public int getBoosterPercent(@Nullable String mythicId, @Nullable String enchantRarity) {
        if (mythicId == null) {
            return 0;
        }
        final Booster booster = successBoosters.get(mythicId.toLowerCase(Locale.ROOT));
        return booster == null ? 0 : booster.percentFor(enchantRarity);
    }

    /**
     * Returns the configured {@link Booster} for a potentiator item, or {@code null}
     * if the id isn't a configured seal. Unlike {@link #getBoosterPercent}, this
     * exposes the seal's target rarity so the menu can tell the player <em>why</em>
     * a seal isn't contributing (wrong rarity) instead of silently showing nothing.
     *
     * @param mythicId the MythicMobs id of the seal (case-insensitive), may be {@code null}
     * @return the booster definition, or {@code null} if not a configured seal
     */
    public @Nullable Booster getBooster(@Nullable String mythicId) {
        if (mythicId == null) {
            return null;
        }
        return successBoosters.get(mythicId.toLowerCase(Locale.ROOT));
    }

    /** @return the configured potentiator MythicMobs ids (lowercase, unmodifiable) */
    public @NotNull java.util.Set<String> getSuccessBoosterIds() {
        return Collections.unmodifiableSet(successBoosters.keySet());
    }

    /** @return whether a potentiator is only consumed on a successful enchant (vs. every attempt) */
    public boolean isBoostersConsumedOnSuccessOnly() { return boostersConsumedOnSuccessOnly; }

    /** @return the number of enchantments shown per page in the GUI */
    public int getEnchantsPerPage() { return enchantsPerPage; }

    /**
     * @param typeId the EcoEnchants category/type id (case-insensitive)
     * @return the configured icon material for that category, or the default icon
     */
    public @NotNull Material getCategoryIcon(@NotNull String typeId) {
        return categoryIcons.getOrDefault(typeId.toLowerCase(Locale.ROOT), defaultCategoryIcon);
    }

    /**
     * @param enchant the enchantment
     * @return its per-enchant override (matched by full key then path), or
     *         {@link EnchantmentOverride#NONE} when none is configured
     */
    public @NotNull EnchantmentOverride getEnchantmentOverride(@NotNull Enchantment enchant) {
        if (enchantmentOverrides.isEmpty()) {
            return EnchantmentOverride.NONE;
        }
        final String full = enchant.getKey().asString().toLowerCase(Locale.ROOT);
        EnchantmentOverride override = enchantmentOverrides.get(full);
        if (override == null) {
            override = enchantmentOverrides.get(enchant.getKey().getKey().toLowerCase(Locale.ROOT));
        }
        return override == null ? EnchantmentOverride.NONE : override;
    }

    /** @return a list of disabled enchantment keys/tokens (e.g. minecraft:mending, #curses) */
    public @NotNull List<String> getDisabledEnchantments() { return Collections.unmodifiableList(disabledEnchantments); }

    /**
     * Checks whether an enchantment is blacklisted. Entries in
     * {@code enchanting.disabled-enchantments} may be:
     * <ul>
     *   <li>a full key ({@code minecraft:mending}, {@code ecoenchants:telekinesis}),</li>
     *   <li>just the key path ({@code mending}) — namespace optional,</li>
     *   <li>the token {@code #curses} to disable every curse — both vanilla
     *       ({@code isCursed}) and EcoEnchants' own {@code curse} type,</li>
     *   <li>the token {@code #type:<id>} to disable a whole EcoEnchants category
     *       (e.g. {@code #type:curse}, {@code #type:artifact}).</li>
     * </ul>
     * Matching is case-insensitive.
     *
     * @param enchant the enchantment to test
     * @param typeId  the EcoEnchants type/category id, or {@code null} if unknown
     * @return {@code true} if the enchantment is disabled
     */
    public boolean isEnchantDisabled(@NotNull Enchantment enchant, @Nullable String typeId) {
        if (disabledEnchantments.isEmpty()) {
            return false;
        }
        final String full = enchant.getKey().asString().toLowerCase(Locale.ROOT);
        final String path = enchant.getKey().getKey().toLowerCase(Locale.ROOT);
        final String type = typeId == null ? null : typeId.toLowerCase(Locale.ROOT);
        for (String raw : disabledEnchantments) {
            final String entry = raw.toLowerCase(Locale.ROOT).trim();
            if (entry.equals("#curses") || entry.equals("#curse")) {
                // Catches both the vanilla cursed flag and EcoEnchants' own "curse" type.
                if (enchant.isCursed() || "curse".equals(type)) {
                    return true;
                }
            } else if (entry.startsWith("#type:")) {
                if (type != null && type.equals(entry.substring("#type:".length()).trim())) {
                    return true;
                }
            } else if (entry.equals(full) || entry.equals(path)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param rarityId the EcoEnchants rarity id (case-insensitive), may be {@code null}
     * @return the XP cost multiplier for that rarity, or the default if unmapped
     */
    public double getRarityCostMultiplier(@Nullable String rarityId) {
        if (rarityId == null) {
            return defaultRarityMultiplier;
        }
        return rarityCostMultipliers.getOrDefault(rarityId.toLowerCase(java.util.Locale.ROOT), defaultRarityMultiplier);
    }

    /**
     * @param rarityId the EcoEnchants rarity id (case-insensitive), may be {@code null}
     * @return the reagent required for that rarity, or the default reagent, or {@code null} if none
     */
    public @Nullable Reagent getReagent(@Nullable String rarityId) {
        if (rarityId != null) {
            Reagent specific = rarityReagents.get(rarityId.toLowerCase(java.util.Locale.ROOT));
            if (specific != null) {
                return specific;
            }
        }
        return defaultReagent;
    }

    // ────────────────────────────────────────────────────────────
    //  Transfer Accessors
    // ────────────────────────────────────────────────────────────

    /** @return whether the enchantment-transfer (grindstone) feature is enabled */
    public boolean isTransferEnabled() { return transferEnabled; }

    /** @return whether extracting an enchantment to a book (no target) is allowed */
    public boolean isTransferAllowExtract() { return transferAllowExtract; }

    /** @return the currency a transfer charges in (default {@link CostType#XP}) */
    public @NotNull CostType getTransferCostType() { return transferCostType; }

    /** @return the flat base cost of a transfer */
    public int getTransferBaseCost() { return transferBaseCost; }

    /** @return the per-level multiplier in the transfer cost curve */
    public int getTransferLevelMultiplier() { return transferLevelMultiplier; }

    /** @return the exponent applied to the level in the transfer cost curve (1.0 = linear) */
    public double getTransferCostExponent() { return transferCostExponent; }

    /** @return the hard cap on a single transfer's cost */
    public int getTransferMaxCost() { return transferMaxCost; }

    /** @return whether the transfer cost is scaled by the enchantment's rarity multiplier */
    public boolean isTransferUseRarityMultiplier() { return transferUseRarityMultiplier; }

    /** @return whether donor and target must be the exact same material to transfer */
    public boolean isTransferRequireSameMaterial() { return transferRequireSameMaterial; }

    // ────────────────────────────────────────────────────────────
    //  Anti-Dupe Accessors
    // ────────────────────────────────────────────────────────────

    /** @return cooldown in ticks between transactions */
    public int getCooldownTicks() { return cooldownTicks; }

    /** @return whether crash persistence for GUI items is enabled */
    public boolean isCrashPersistenceEnabled() { return crashPersistenceEnabled; }

    // ────────────────────────────────────────────────────────────
    //  Sound Accessors
    // ────────────────────────────────────────────────────────────

    /** @return the sound played on a successful anvil forge */
    public @NotNull SoundEffect getAnvilSuccessSound() { return anvilSuccessSound; }

    /** @return the sound played on a successful enchantment */
    public @NotNull SoundEffect getEnchantSuccessSound() { return enchantSuccessSound; }

    /** @return the sound played when a probabilistic enchant attempt fails */
    public @NotNull SoundEffect getEnchantFailSound() { return enchantFailSound; }

    /** @return the sound played on a successful enchantment transfer */
    public @NotNull SoundEffect getTransferSuccessSound() { return transferSuccessSound; }

    /** @return the sound played on a successful extraction to book */
    public @NotNull SoundEffect getExtractSuccessSound() { return extractSuccessSound; }

    /** @return the sound played on an error */
    public @NotNull SoundEffect getErrorSound() { return errorSound; }

    /** @return the sound played when a GUI opens */
    public @NotNull SoundEffect getGuiOpenSound() { return guiOpenSound; }

    /** @return the sound played when a GUI closes */
    public @NotNull SoundEffect getGuiCloseSound() { return guiCloseSound; }

    /** @return the sound played on menu navigation clicks */
    public @NotNull SoundEffect getButtonClickSound() { return buttonClickSound; }

    // ────────────────────────────────────────────────────────────
    //  Particle Accessors
    // ────────────────────────────────────────────────────────────

    /** @return the particle burst shown on a successful enchant */
    public @NotNull ParticleEffect getEnchantSuccessParticle() { return enchantSuccessParticle; }

    /** @return the particle burst shown when a probabilistic enchant attempt fails */
    public @NotNull ParticleEffect getEnchantFailParticle() { return enchantFailParticle; }

    /** @return the ambient particle shown over nearby enchanted libraries */
    public @NotNull ParticleEffect getLibraryAmbientParticle() { return libraryAmbientParticle; }

    /** @return how often (ticks) the library ambient particle effect runs */
    public long getLibraryAmbientPeriodTicks() { return libraryAmbientPeriodTicks; }

    // ────────────────────────────────────────────────────────────
    //  Note: All message methods have been moved to MessagesConfig.
    //  See SuperEnchanterPlugin.getMessages() for the new API.
    // ────────────────────────────────────────────────────────────

    /**
     * Retrieves a list of strings from the {@code messages} config section.
     *
     * @param key the message key (under {@code messages.})
     * @return the list of strings, or an empty list if missing
     */
    public @NotNull List<String> getMessageList(@NotNull String key) {
        List<String> list = config.getStringList("messages." + key);
        return list != null ? list : Collections.emptyList();
    }

    // ────────────────────────────────────────────────────────────
    //  Internal Loaders
    // ────────────────────────────────────────────────────────────

    private void loadAnvilSettings() {
        anvilDefaultCostType = config.getString("anvil.default-cost-type", "XP");
        anvilBaseXPCost = config.getInt("anvil.base-xp-cost", 1);
        anvilCostPerLevel = config.getInt("anvil.cost-per-enchant-level", 3);
        anvilMaxCost = config.getInt("anvil.max-xp-cost", 39);
        chainForgingEnabled = config.getBoolean("anvil.chain-forging", true);
        anvilCostOverrides = parseCostOverrides();
        curseRemovalEnabled = config.getBoolean("anvil.curse-removal.enabled", true);
        curseRemovalSealIds = parseLowerSet("anvil.curse-removal.seal-ids");
    }

    /** @return whether the anvil's curse-removal mode (Sello Purificador) is active */
    public boolean isCurseRemovalEnabled() { return curseRemovalEnabled; }

    /**
     * @param mythicId the MythicMobs id of the item in the sacrifice slot (case-insensitive)
     * @return whether it is a configured curse-removal seal (Sello Purificador)
     */
    public boolean isCurseRemovalSeal(@Nullable String mythicId) {
        return mythicId != null && curseRemovalSealIds.contains(mythicId.toLowerCase(Locale.ROOT));
    }

    /** @return the configured curse-removal seal MythicMobs ids (lowercase, unmodifiable) */
    public @NotNull java.util.Set<String> getCurseRemovalSealIds() {
        return Collections.unmodifiableSet(curseRemovalSealIds);
    }

    private void loadEnchantingSettings() {
        scanRadiusH = config.getInt("enchanting.scan-radius-horizontal", 2);
        scanRadiusV = config.getInt("enchanting.scan-radius-vertical", 2);
        airGapRequired = config.getBoolean("enchanting.air-gap-required", true);
        maxBookshelfPower = config.getInt("enchanting.max-bookshelf-power", 330);
        vanillaPowerCap = config.getInt("enchanting.vanilla-power-cap", 30);
        powerValues = parsePowerValues();
        enchantingCostType = CostType.fromString(config.getString("enchanting.cost-type", "XP"));
        rarityCostTypes = parseRarityCostTypes();
        baseXPCost = config.getInt("enchanting.base-xp-cost", 5);
        levelXPMultiplier = config.getInt("enchanting.level-xp-multiplier", 8);
        costExponent = config.getDouble("enchanting.cost-exponent", 1.0);
        maxXpCost = config.getInt("enchanting.max-xp-cost", 60);
        enchantsPerPage = config.getInt("enchanting.enchantments-per-page", 10);
        disabledEnchantments = config.getStringList("enchanting.disabled-enchantments");
        defaultRarityMultiplier = config.getDouble("enchanting.default-rarity-multiplier", 1.0);
        rarityCostMultipliers = parseRarityMultipliers();
        rarityReagents = parseRarityReagents();
        defaultReagent = parseReagentSection(config.getConfigurationSection("enchanting.default-reagent"));
        reagentScalesWithLevel = config.getBoolean("enchanting.reagent-scales-with-level", true);
        defaultRarityPower = new RarityPower(
                config.getInt("enchanting.default-rarity-power.floor", 0),
                config.getInt("enchanting.default-rarity-power.step", 4));
        rarityPower = parseRarityPower();
        enchantedBookshelves = parseEnchantedBookshelves();
        parseCategoryIcons();
        enchantmentOverrides = parseEnchantmentOverrides();
        successChanceEnabled = config.getBoolean("enchanting.success-chance.enabled", true);
        defaultSuccessChance = clampPercent(config.getInt("enchanting.success-chance.default", 100));
        boostersConsumedOnSuccessOnly =
                config.getBoolean("enchanting.success-chance.boosters-consumed-on-success-only", false);
        raritySuccessChance = parsePercentMap("enchanting.success-chance.by-rarity");
        successBoosters = parseBoosters("enchanting.success-chance.boosters");
        curseChanceEnabled = config.getBoolean("enchanting.curse-chance.enabled", true);
        curseBasePercent = Math.max(0, config.getDouble("enchanting.curse-chance.base-percent", 1.0));
        curseRarityChance = parseDoubleMap("enchanting.curse-chance.by-rarity");
        curseExcluded = parseLowerSet("enchanting.curse-chance.excluded");
    }

    /** @return curse enchantment key-paths the curse roll must never pick (lowercase, unmodifiable) */
    public @NotNull java.util.Set<String> getExcludedCurseKeys() {
        return Collections.unmodifiableSet(curseExcluded);
    }

    /** @return whether enchanting can apply a random curse (prevented by a seal) */
    public boolean isCurseChanceEnabled() { return curseChanceEnabled; }

    /**
     * @param rarityId the EcoEnchants rarity id (case-insensitive), may be {@code null}
     * @return the curse probability (percent, 0–100) for that rarity, or the base
     */
    public double getCurseChance(@Nullable String rarityId) {
        if (rarityId == null) {
            return curseBasePercent;
        }
        return curseRarityChance.getOrDefault(rarityId.toLowerCase(Locale.ROOT), curseBasePercent);
    }

    /** Parses a {@code id → double} section (lowercased keys). */
    private @NotNull Map<String, Double> parseDoubleMap(@NotNull String path) {
        Map<String, Double> values = new HashMap<>();
        ConfigurationSection section = config.getConfigurationSection(path);
        if (section != null) {
            for (String key : section.getKeys(false)) {
                values.put(key.toLowerCase(Locale.ROOT), Math.max(0, section.getDouble(key)));
            }
        }
        return values;
    }

    /** Parses a string list into a set of lowercased ids. */
    private @NotNull java.util.Set<String> parseLowerSet(@NotNull String path) {
        java.util.Set<String> values = new java.util.HashSet<>();
        for (String v : config.getStringList(path)) {
            values.add(v.toLowerCase(Locale.ROOT));
        }
        return values;
    }

    /**
     * Parses the potentiator section. Each entry is either:
     * <ul>
     *   <li>a section {@code { rarity: <id|*>, percent: <int> }} — a rarity-targeted
     *       orb ({@code rarity: '*'}/{@code any}/{@code all} or omitted = universal,
     *       {@code percent} defaults to 100 → a guarantee orb), or</li>
     *   <li>a plain int {@code id: 25} — a universal booster of that many points.</li>
     * </ul>
     */
    private @NotNull Map<String, Booster> parseBoosters(@NotNull String path) {
        Map<String, Booster> values = new HashMap<>();
        ConfigurationSection section = config.getConfigurationSection(path);
        if (section == null) {
            return values;
        }
        for (String key : section.getKeys(false)) {
            final String id = key.toLowerCase(Locale.ROOT);
            if (section.isConfigurationSection(key)) {
                ConfigurationSection entry = section.getConfigurationSection(key);
                String rarity = entry.getString("rarity", null);
                if (rarity != null) {
                    rarity = rarity.trim().toLowerCase(Locale.ROOT);
                    if (rarity.isEmpty() || rarity.equals("*")
                            || rarity.equals("any") || rarity.equals("all")) {
                        rarity = null;
                    }
                }
                values.put(id, new Booster(rarity, Math.max(0, entry.getInt("percent", 100))));
            } else {
                // Plain int form → universal booster.
                values.put(id, new Booster(null, Math.max(0, section.getInt(key, 0))));
            }
        }
        return values;
    }

    /** Clamps an int to the {@code [0, 100]} percentage range. */
    private static int clampPercent(int value) {
        return Math.max(0, Math.min(100, value));
    }

    /**
     * Parses a {@code id → int} section (used for per-rarity chances and booster
     * percentages). Keys are lowercased; rarity values are clamped to {@code [0,100]},
     * booster values are kept as-is (a value {@code >= 100} = guarantee).
     */
    private @NotNull Map<String, Integer> parsePercentMap(@NotNull String path) {
        Map<String, Integer> values = new HashMap<>();
        ConfigurationSection section = config.getConfigurationSection(path);
        if (section != null) {
            for (String key : section.getKeys(false)) {
                values.put(key.toLowerCase(Locale.ROOT), Math.max(0, section.getInt(key, 0)));
            }
        }
        return values;
    }

    private void parseCategoryIcons() {
        categoryIcons = new HashMap<>();
        defaultCategoryIcon = Material.ENCHANTED_BOOK;
        ConfigurationSection section = config.getConfigurationSection("enchanting.category-icons");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            Material material = Material.matchMaterial(section.getString(key, ""));
            if (material == null) {
                plugin.getLogger().warning("Invalid category-icon material for '" + key + "'; ignoring.");
                continue;
            }
            if (key.equalsIgnoreCase("default")) {
                defaultCategoryIcon = material;
            } else {
                categoryIcons.put(key.toLowerCase(Locale.ROOT), material);
            }
        }
    }

    private @NotNull Map<String, EnchantmentOverride> parseEnchantmentOverrides() {
        Map<String, EnchantmentOverride> overrides = new HashMap<>();
        ConfigurationSection section = config.getConfigurationSection("enchanting.enchantment-overrides");
        if (section == null) {
            return overrides;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }
            Integer maxLevel = entry.contains("max-level") ? entry.getInt("max-level") : null;
            Integer xpCost = entry.contains("xp-cost") ? entry.getInt("xp-cost") : null;
            Integer requiredPower = entry.contains("required-power") ? entry.getInt("required-power") : null;
            double costMultiplier = entry.getDouble("cost-multiplier", 1.0);
            overrides.put(key.toLowerCase(Locale.ROOT),
                    new EnchantmentOverride(maxLevel, xpCost, requiredPower, costMultiplier));
        }
        return overrides;
    }

    private @NotNull Map<String, Integer> parseEnchantedBookshelves() {
        Map<String, Integer> values = new HashMap<>();
        ConfigurationSection section = config.getConfigurationSection("enchanting.enchanted-bookshelves");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                values.put(id.toLowerCase(Locale.ROOT), section.getInt(id, 1));
            }
        }
        return values;
    }

    private @NotNull Map<String, RarityPower> parseRarityPower() {
        Map<String, RarityPower> values = new HashMap<>();
        ConfigurationSection section = config.getConfigurationSection("enchanting.rarity-power");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection entry = section.getConfigurationSection(key);
                if (entry != null) {
                    values.put(key.toLowerCase(Locale.ROOT), new RarityPower(
                            entry.getInt("floor", defaultRarityPower.floor()),
                            entry.getInt("step", defaultRarityPower.step())));
                }
            }
        }
        return values;
    }

    /**
     * Parses {@code enchanting.rarity-cost-type} (a {@code rarityId → currency}
     * section). Unknown currency strings fall back to the global cost type with a
     * warning. Keys are lowercased.
     */
    private @NotNull Map<String, CostType> parseRarityCostTypes() {
        Map<String, CostType> values = new HashMap<>();
        ConfigurationSection section = config.getConfigurationSection("enchanting.rarity-cost-type");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                values.put(key.toLowerCase(Locale.ROOT),
                        CostType.fromString(section.getString(key, enchantingCostType.name())));
            }
        }
        return values;
    }

    private @NotNull Map<String, Double> parseRarityMultipliers() {
        Map<String, Double> values = new HashMap<>();
        ConfigurationSection section = config.getConfigurationSection("enchanting.rarity-cost-multipliers");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                values.put(key.toLowerCase(Locale.ROOT), section.getDouble(key, defaultRarityMultiplier));
            }
        }
        return values;
    }

    private @NotNull Map<String, Reagent> parseRarityReagents() {
        Map<String, Reagent> values = new HashMap<>();
        ConfigurationSection section = config.getConfigurationSection("enchanting.rarity-reagents");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                Reagent reagent = parseReagentSection(section.getConfigurationSection(key));
                if (reagent != null) {
                    values.put(key.toLowerCase(Locale.ROOT), reagent);
                }
            }
        }
        return values;
    }

    private @Nullable Reagent parseReagentSection(@Nullable ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        String materialName = section.getString("material");
        if (materialName == null || materialName.isBlank()) {
            return null;
        }
        Material material = Material.matchMaterial(materialName);
        if (material == null) {
            plugin.getLogger().warning("Invalid reagent material '" + materialName + "' in config.yml; ignoring.");
            return null;
        }
        int amount = section.getInt("amount", 1);
        Integer cmd = section.contains("custom-model-data")
                ? section.getInt("custom-model-data")
                : null;
        return new Reagent(material, amount, cmd);
    }

    private void loadTransferSettings() {
        transferEnabled = config.getBoolean("transfer.enabled", true);
        transferAllowExtract = config.getBoolean("transfer.allow-extract", true);
        transferCostType = CostType.fromString(config.getString("transfer.cost-type", "XP"));
        transferBaseCost = config.getInt("transfer.base-cost", 10);
        transferLevelMultiplier = config.getInt("transfer.level-multiplier", 10);
        transferCostExponent = config.getDouble("transfer.cost-exponent", 1.0);
        transferMaxCost = config.getInt("transfer.max-cost", 100);
        transferUseRarityMultiplier = config.getBoolean("transfer.use-rarity-multiplier", true);
        transferRequireSameMaterial = config.getBoolean("transfer.require-same-material", false);
        transferExtractMultiplier = config.getDouble("transfer.extract-cost-multiplier", 2.0);
    }

    /** @return cost multiplier applied to EXTRACTION (vs plain transfer); extraction makes a tradeable book */
    public double getTransferExtractMultiplier() { return transferExtractMultiplier; }

    private void loadAntiDupeSettings() {
        cooldownTicks = config.getInt("anti-dupe.transaction-cooldown-ticks", 5);
        crashPersistenceEnabled = config.getBoolean("anti-dupe.crash-persistence-enabled", true);
        costDiscountsEnabled = config.getBoolean("cost-discounts.enabled", true);
        auditLogEnabled = config.getBoolean("audit-log.enabled", true);
        auditLogToConsole = config.getBoolean("audit-log.to-console", false);
        auditMaxFileKb = config.getInt("audit-log.max-file-kb", 2048);
        shutdownOnCriticalError = config.getBoolean("fail-safe.shutdown-on-critical-error", true);
    }

    private void loadLootControlSettings() {
        lootControlEnabled = config.getBoolean("loot-control.enabled", true);
        lootRemoveEnchantedBooks = config.getBoolean("loot-control.remove-enchanted-books", true);
        lootStripEquipmentEnchantments = config.getBoolean("loot-control.strip-equipment-enchantments", true);
        lootIncludeMobDrops = config.getBoolean("loot-control.include-mob-drops", true);
        java.util.Set<String> worlds = new java.util.HashSet<>();
        for (String w : config.getStringList("loot-control.disabled-worlds")) {
            worlds.add(w.toLowerCase(Locale.ROOT));
        }
        lootDisabledWorlds = worlds;
    }

    /** @return whether loot control (strip free enchantments from natural loot) is active */
    public boolean isLootControlEnabled() { return lootControlEnabled; }

    /** @return whether enchanted books are removed from generated loot */
    public boolean isLootRemoveEnchantedBooks() { return lootRemoveEnchantedBooks; }

    /** @return whether enchantments are stripped from equipment in generated loot */
    public boolean isLootStripEquipmentEnchantments() { return lootStripEquipmentEnchantments; }

    /** @return whether loot control also applies to mob drops (dropped enchanted gear) */
    public boolean isLootIncludeMobDrops() { return lootIncludeMobDrops; }

    /**
     * Whether loot control should be skipped in the given world.
     *
     * @param worldName the world name
     * @return {@code true} if loot control is disabled for that world
     */
    public boolean isLootWorldDisabled(@NotNull String worldName) {
        return lootDisabledWorlds.contains(worldName.toLowerCase(Locale.ROOT));
    }

    private void loadBannedEnchantmentSettings() {
        bannedEnchantmentsEnabled = config.getBoolean("banned-enchantments.enabled", true);
        bannedPurgeInventories = config.getBoolean("banned-enchantments.purge-player-inventories", true);
        bannedBlockXpRepair = config.getBoolean("banned-enchantments.block-xp-repair", true);
        java.util.Set<String> keys = new java.util.HashSet<>();
        for (String raw : config.getStringList("banned-enchantments.keys")) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            keys.add(normalizeEnchantKey(raw));
        }
        bannedEnchantmentKeys = keys;
    }

    /** Normalizes an enchantment key to {@code namespace:key} (defaulting to {@code minecraft:}), lowercased. */
    private static @NotNull String normalizeEnchantKey(@NotNull String raw) {
        String key = raw.trim().toLowerCase(Locale.ROOT);
        return key.contains(":") ? key : "minecraft:" + key;
    }

    /** @return whether globally-banned enchantments (default: mending) are purged from the world */
    public boolean isBannedEnchantmentsEnabled() { return bannedEnchantmentsEnabled; }

    /** @return whether player inventories are purged of banned enchantments (join/open/click) */
    public boolean isBannedPurgeInventories() { return bannedPurgeInventories; }

    /** @return whether XP-repair (mending's effect) is blocked outright via PlayerItemMendEvent */
    public boolean isBannedBlockXpRepair() { return bannedBlockXpRepair; }

    /**
     * Whether the given enchantment is globally banned (must be stripped from any item).
     *
     * @param enchant the enchantment
     * @return {@code true} if its key is in the banned list
     */
    public boolean isEnchantmentBanned(@NotNull org.bukkit.enchantments.Enchantment enchant) {
        return !bannedEnchantmentKeys.isEmpty()
                && bannedEnchantmentKeys.contains(enchant.getKey().toString().toLowerCase(Locale.ROOT));
    }

    private void loadVillagerTradeSettings() {
        villagerTradesEnabled = config.getBoolean("villager-trades.enabled", true);
        villagerBlockBookTrades = config.getBoolean("villager-trades.block-book-trades", true);
    }

    /** @return whether villager-trade control is active */
    public boolean isVillagerTradesEnabled() { return villagerTradesEnabled; }

    /** @return whether villagers are blocked from acquiring any trade whose result is a book */
    public boolean isVillagerBlockBookTrades() { return villagerBlockBookTrades; }

    /** @return whether a critical startup failure shuts the whole server down */
    public boolean isShutdownOnCriticalError() { return shutdownOnCriticalError; }

    /** @return whether permission-based cost discounts/bypass are honoured */
    public boolean isCostDiscountsEnabled() { return costDiscountsEnabled; }

    /** @return whether high-value operations are written to the audit log */
    public boolean isAuditLogEnabled() { return auditLogEnabled; }

    /** @return whether audit lines are also echoed to the server console */
    public boolean isAuditLogToConsole() { return auditLogToConsole; }

    /** @return the size (KiB) at which {@code audit.jsonl} is rotated to {@code .1}; ≤0 disables rotation */
    public int getAuditMaxFileKb() { return auditMaxFileKb; }

    private void loadSounds() {
        anvilSuccessSound = parseSound("anvil-success", "block.anvil.use");
        enchantSuccessSound = parseSound("enchant-success", "block.enchantment_table.use");
        enchantFailSound = parseSound("enchant-fail", "block.fire.extinguish");
        transferSuccessSound = parseSound("transfer-success", "block.amethyst_block.chime");
        extractSuccessSound = parseSound("extract-success", "block.amethyst_block.chime");
        errorSound = parseSound("error", "entity.villager.no");
        guiOpenSound = parseSound("gui-open", "block.barrel.open");
        guiCloseSound = parseSound("gui-close", "block.barrel.close");
        buttonClickSound = parseSound("button-click", "ui.button.click");
    }

    private void loadParticles() {
        enchantSuccessParticle = parseParticle("enchant-success",
                new ParticleEffect(true, Particle.ENCHANT, 50, 0.5, 0.5, 0.5, 0.2));
        enchantFailParticle = parseParticle("enchant-fail",
                new ParticleEffect(true, Particle.LARGE_SMOKE, 25, 0.4, 0.5, 0.4, 0.02));
        libraryAmbientParticle = parseParticle("library-ambient",
                new ParticleEffect(true, Particle.ENCHANT, 4, 0.25, 0.2, 0.25, 0.02));
        libraryAmbientPeriodTicks = Math.max(1L,
                config.getLong("particles.library-ambient.period-ticks", 25L));
    }

    /**
     * Parses a sound under {@code sounds.<path>}. Accepts either a plain key
     * string (volume/pitch default to 1.0) or a section
     * {@code { key, volume, pitch }}.
     */
    private @NotNull SoundEffect parseSound(@NotNull String path, @NotNull String defaultKey) {
        final String base = "sounds." + path;
        ConfigurationSection section = config.getConfigurationSection(base);
        if (section != null) {
            return new SoundEffect(parseSoundKey(section.getString("key", defaultKey)),
                    (float) section.getDouble("volume", 1.0),
                    (float) section.getDouble("pitch", 1.0));
        }
        return new SoundEffect(parseSoundKey(config.getString(base, defaultKey)), 1.0f, 1.0f);
    }

    /**
     * Parses a particle effect under {@code particles.<path>}, falling back to the
     * supplied default for any missing field (or the whole section).
     */
    private @NotNull ParticleEffect parseParticle(@NotNull String path, @NotNull ParticleEffect def) {
        ConfigurationSection section = config.getConfigurationSection("particles." + path);
        if (section == null) {
            return def;
        }
        boolean enabled = section.getBoolean("enabled", def.enabled());
        Particle particle = parseParticleType(section.getString("type"), def.particle());
        int count = section.getInt("count", def.count());
        double speed = section.getDouble("speed", def.speed());
        ConfigurationSection off = section.getConfigurationSection("offset");
        double ox = off != null ? off.getDouble("x", def.offsetX()) : def.offsetX();
        double oy = off != null ? off.getDouble("y", def.offsetY()) : def.offsetY();
        double oz = off != null ? off.getDouble("z", def.offsetZ()) : def.offsetZ();
        return new ParticleEffect(enabled, particle, count, ox, oy, oz, speed);
    }

    /**
     * Resolves a sound from the registry using its namespaced key
     * (e.g. {@code block.anvil.use} or {@code minecraft:block.anvil.use}).
     * Falls back to {@link Sound#ENTITY_VILLAGER_NO} with a warning if the key
     * is unknown or malformed.
     */
    private @NotNull Sound parseSoundKey(@Nullable String soundName) {
        if (soundName == null || soundName.isBlank()) {
            return Sound.ENTITY_VILLAGER_NO;
        }
        String normalized = soundName.toLowerCase(Locale.ROOT).trim();
        NamespacedKey key = normalized.contains(":")
                ? NamespacedKey.fromString(normalized)
                : NamespacedKey.minecraft(normalized);
        Sound sound = key == null ? null : Registry.SOUNDS.get(key);
        if (sound != null) {
            return sound;
        }
        plugin.getLogger().warning("Invalid sound '" + soundName + "' in config (expected a namespaced key "
                + "like 'block.anvil.use'). Falling back to entity.villager.no.");
        return Sound.ENTITY_VILLAGER_NO;
    }

    /** Resolves a {@link Particle} by enum name, falling back with a warning. */
    private @NotNull Particle parseParticleType(@Nullable String name, @NotNull Particle fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        try {
            return Particle.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid particle '" + name + "' in config; using "
                    + fallback.name() + ".");
            return fallback;
        }
    }

    private @NotNull Map<String, Integer> parsePowerValues() {
        Map<String, Integer> values = new HashMap<>();
        ConfigurationSection section = config.getConfigurationSection("enchanting.power-values");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                values.put(key, section.getInt(key, 1));
            }
        }
        if (values.isEmpty()) {
            values.put("BOOKSHELF", 1);
        }
        return values;
    }

    private @NotNull List<CostOverride> parseCostOverrides() {
        List<CostOverride> overrides = new ArrayList<>();
        List<?> rawList = config.getList("anvil.cost-overrides");
        if (rawList == null) {
            return overrides;
        }

        for (Object entry : rawList) {
            if (!(entry instanceof Map<?, ?> map)) {
                continue;
            }

            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> matchSection = (Map<String, Object>) map.get("match");
                if (matchSection == null) {
                    continue;
                }

                String matchType = String.valueOf(matchSection.getOrDefault("type", ""));
                String namespace = String.valueOf(matchSection.getOrDefault("namespace", ""));
                String key = String.valueOf(matchSection.getOrDefault("key", ""));
                String value = String.valueOf(matchSection.getOrDefault("value", ""));
                Object rawCostType = map.get("cost-type");
                String costType = rawCostType != null ? String.valueOf(rawCostType) : "XP";
                Object rawAmount = map.get("amount");
                double amount = rawAmount instanceof Number num ? num.doubleValue() : 0.0;

                overrides.add(new CostOverride(matchType, namespace, key, value, costType, amount));
            } catch (ClassCastException e) {
                plugin.getLogger().warning("Malformed cost-override entry in config.yml: " + e.getMessage());
            }
        }

        return overrides;
    }
}
