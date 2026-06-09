/* SuperEnchanter - Author: Scrulius (GitHub) */
package dev.scrulius.superenchanter.integration;

import com.willfp.eco.core.config.interfaces.Config;
import com.willfp.ecoenchants.display.EnchantmentFormattingKt;
import com.willfp.ecoenchants.enchant.EcoEnchant;
import com.willfp.ecoenchants.enchant.EcoEnchantLike;
import com.willfp.ecoenchants.enchant.UtilKt;
import com.willfp.ecoenchants.enchant.impl.EcoEnchantBase;
import com.willfp.ecoenchants.target.EnchantmentTarget;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Direct integration with EcoEnchants, which is a hard dependency.
 * <p>
 * EcoEnchants registers every enchantment (including vanilla ones) as a real
 * {@link Enchantment}, and {@link UtilKt#wrap(Enchantment)} exposes the rich
 * EcoEnchants model for any of them — rarity, type, max level, targets,
 * conflicts and required enchantments. We use that model directly (no
 * reflection) to drive display, pricing and the "blocked, and here's why" UI.
 * </p>
 * <p>
 * {@link UtilKt#wrap(Enchantment)} results are cached per enchantment — the
 * wrapper is stable for the lifetime of the registry, so this turns the
 * per-enchantment lookups (done many times while building the menu) into cheap
 * map hits.
 * </p>
 */
public final class EcoEnchantsHook {

    /** Cache of wrap() results; enchantments are stable so this is safe. */
    private final Map<Enchantment, Optional<EcoEnchantLike>> wrapCache = new ConcurrentHashMap<>();
    /** Cache of base (level-0) display names — requested repeatedly while listing. */
    private final Map<Enchantment, String> baseNameCache = new ConcurrentHashMap<>();
    /** Cache of declared requirement ({@code not-met}) lines — read from config, stable. */
    private final Map<Enchantment, List<String>> requirementCache = new ConcurrentHashMap<>();
    /** Cache of all curse-type enchantments (registry is stable for the runtime). */
    private volatile List<Enchantment> curseCache;

    /**
     * Wraps a Bukkit enchantment into its EcoEnchants model (cached).
     *
     * @param enchantment the enchantment
     * @return the EcoEnchants wrapper, or {@code null} if it is not registered with EcoEnchants
     */
    private @Nullable EcoEnchantLike wrap(@NotNull Enchantment enchantment) {
        return wrapCache.computeIfAbsent(enchantment, k -> Optional.ofNullable(UtilKt.wrap(k))).orElse(null);
    }

    /**
     * Returns the EcoEnchants rarity id for an enchantment (e.g. {@code common},
     * {@code legendary}), used to scale enchanting cost.
     *
     * @param enchantment the enchantment
     * @return the rarity id, or {@code null} if unavailable
     */
    public @Nullable String getRarityId(@NotNull Enchantment enchantment) {
        EcoEnchantLike like = wrap(enchantment);
        return like == null ? null : like.getEnchantmentRarity().getId();
    }

    /**
     * Returns the EcoEnchants type/category id for an enchantment (e.g.
     * {@code normal}, {@code special}, {@code curse}, {@code artifact}), used to
     * group enchantments into categories.
     *
     * @param enchantment the enchantment
     * @return the type id, or {@code null} if unavailable
     */
    public @Nullable String getTypeId(@NotNull Enchantment enchantment) {
        EcoEnchantLike like = wrap(enchantment);
        if (like == null) {
            return null;
        }
        var type = like.getType();
        return type == null ? null : type.getId();
    }

    /**
     * Target-only relevance check: whether this enchantment <em>could</em> apply
     * to the item's type, ignoring conflicts/requirements/level (those are
     * reported separately as block reasons). Enchanted books accept anything.
     *
     * @param enchantment the enchantment
     * @param item        the item being enchanted
     * @return {@code true} if the enchantment targets this item type
     */
    public boolean appliesToItem(@NotNull Enchantment enchantment, @NotNull ItemStack item) {
        if (item.getType() == Material.ENCHANTED_BOOK) {
            return true;
        }
        EcoEnchantLike like = wrap(enchantment);
        if (like instanceof EcoEnchant eco) {
            for (EnchantmentTarget target : eco.getTargets()) {
                if (target.matches(item)) {
                    return true;
                }
            }
            return false;
        }
        // Pure-vanilla fallback: Bukkit's target check.
        return enchantment.canEnchantItem(item);
    }

    /** @return the enchantments EcoEnchants declares as conflicting with this one (empty for vanilla) */
    public @NotNull List<Enchantment> getConflicts(@NotNull Enchantment enchantment) {
        EcoEnchantLike like = wrap(enchantment);
        return like instanceof EcoEnchant eco ? new ArrayList<>(eco.getConflicts()) : List.of();
    }

    /** @return whether this enchantment conflicts with every other enchantment */
    public boolean conflictsWithEverything(@NotNull Enchantment enchantment) {
        EcoEnchantLike like = wrap(enchantment);
        return like instanceof EcoEnchant eco && eco.getConflictsWithEverything();
    }

    /** @return the enchantments that must already be present before this one can be applied */
    public @NotNull List<Enchantment> getRequired(@NotNull Enchantment enchantment) {
        EcoEnchantLike like = wrap(enchantment);
        return like instanceof EcoEnchant eco ? new ArrayList<>(eco.getRequired()) : List.of();
    }

    /**
     * @return how many enchantments of this enchantment's type an item may hold,
     *         or {@link Integer#MAX_VALUE} when unlimited
     */
    public int getTypeLimit(@NotNull Enchantment enchantment) {
        EcoEnchantLike like = wrap(enchantment);
        if (like == null || like.getType() == null) {
            return Integer.MAX_VALUE;
        }
        int limit = like.getType().getLimit();
        return limit <= 0 ? Integer.MAX_VALUE : limit;
    }

    /**
     * Returns the EcoEnchants maximum level for an enchantment, falling back to
     * the Bukkit registry value if it is not an EcoEnchants enchantment.
     *
     * @param enchantment the enchantment
     * @return the maximum level
     */
    public int getMaxLevel(@NotNull Enchantment enchantment) {
        EcoEnchantLike like = wrap(enchantment);
        return like == null ? enchantment.getMaxLevel() : like.getMaximumLevel();
    }

    /**
     * Returns the config-formatted, colored display name of an enchantment at the
     * given level, or an empty string if it could not be resolved.
     *
     * @param enchantment the enchantment
     * @param level       the level to render (0 typically omits roman numerals)
     * @return the formatted name, or an empty string
     */
    public @NotNull String getDisplayName(@NotNull Enchantment enchantment, int level) {
        if (level == 0) {
            return baseNameCache.computeIfAbsent(enchantment, this::computeName0);
        }
        return computeName(enchantment, level);
    }

    private @NotNull String computeName0(@NotNull Enchantment enchantment) {
        return computeName(enchantment, 0);
    }

    private @NotNull String computeName(@NotNull Enchantment enchantment, int level) {
        EcoEnchantLike like = wrap(enchantment);
        if (like == null) {
            return "";
        }
        String name = EnchantmentFormattingKt.getFormattedName(like, level);
        return name != null ? name : "";
    }

    /**
     * The localised, EcoEnchants-formatted base display name of an enchantment,
     * falling back to a prettified version of its key when EcoEnchants has no name
     * for it. Use this everywhere an enchantment name is shown to the player so
     * vanilla and EcoEnchants names both come out translated, never as raw keys.
     *
     * @param enchantment the enchantment
     * @return the display name, never empty
     */
    public @NotNull String displayNameOrFallback(@NotNull Enchantment enchantment) {
        String name = getDisplayName(enchantment, 0);
        return name.isEmpty()
                ? dev.scrulius.superenchanter.util.EnchantmentHelper.prettyName(enchantment)
                : name;
    }

    /**
     * Returns the config-formatted, colored description lines of an enchantment at
     * the given level, or an empty list if unavailable.
     *
     * @param enchantment the enchantment
     * @param level       the level to render
     * @return the description lines, or an empty list
     */
    public @NotNull List<String> getDescription(@NotNull Enchantment enchantment, int level) {
        return getDescription(enchantment, level, null);
    }

    /**
     * Player-aware variant: returns the same description EcoEnchants renders on the
     * real item for that player, which <em>includes the unmet-requirement lines</em>
     * (e.g. an EcoSkills {@code has_skill_level} gate's {@code not-met-lines}) when the
     * player does not meet a condition. Pass the viewing player so the enchanting menu
     * shows the usage requirement <em>before</em> enchanting, exactly as it will appear
     * on the item; pass {@code null} for the requirement-agnostic base description.
     *
     * @param enchantment the enchantment
     * @param level       the level to render
     * @param player      the viewing player, or {@code null} to omit per-player requirement lines
     * @return the description lines, or an empty list
     */
    public @NotNull List<String> getDescription(@NotNull Enchantment enchantment, int level, @Nullable Player player) {
        EcoEnchantLike like = wrap(enchantment);
        if (like == null) {
            return Collections.emptyList();
        }
        List<String> description = player != null
                ? EnchantmentFormattingKt.getFormattedDescription(like, Math.max(1, level), player)
                : EnchantmentFormattingKt.getFormattedDescription(like, Math.max(1, level));
        return description != null ? description : Collections.emptyList();
    }

    /**
     * Returns the usage-requirement ({@code not-met}) lines an enchantment declares in
     * its libreforge {@code conditions} (e.g. an EcoSkills {@code has_skill_level} gate's
     * {@code not-met-lines}, like "Requiere Combate XV").
     * <p>
     * These are the lines EcoEnchants paints on the item via its display module
     * ({@code ItemProvidedHolder.getNotMetLines}) only when a player fails a condition —
     * NOT part of {@link #getDescription}. We read them straight from the enchant's config
     * (no libreforge on the classpath needed) so the enchanting menu can always show the
     * requirement <em>before</em> enchanting, regardless of whether the player meets it.
     * Both placements are honoured: {@code not-met-lines} at the condition's top level and
     * nested under its {@code args}. Cached (config is stable for the registry lifetime).
     * </p>
     *
     * @param enchantment the enchantment
     * @return the declared requirement lines (legacy {@code &}/{@code §} codes ok), or empty
     */
    public @NotNull List<String> getRequirementLines(@NotNull Enchantment enchantment) {
        return requirementCache.computeIfAbsent(enchantment, this::computeRequirementLines);
    }

    private @NotNull List<String> computeRequirementLines(@NotNull Enchantment enchantment) {
        Config config = configOf(wrap(enchantment));
        if (config == null) {
            return List.of();
        }
        List<? extends Config> conditions = config.getSubsections("conditions");
        if (conditions == null || conditions.isEmpty()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        for (Config condition : conditions) {
            // Honour not-met-lines both at the condition's top level (libreforge default)
            // and nested under its args (where this catalog puts them).
            collectNotMetLines(condition, lines);
            collectNotMetLines(condition.getSubsectionOrNull("args"), lines);
        }
        return lines.isEmpty() ? List.of() : List.copyOf(lines);
    }

    private static void collectNotMetLines(@Nullable Config section, @NotNull List<String> out) {
        if (section == null) {
            return;
        }
        List<String> nml = section.getStringsOrNull("not-met-lines");
        if (nml != null) {
            out.addAll(nml);
        }
    }

    /**
     * Resolves the eco {@link Config} backing an enchantment. {@link UtilKt#wrap} returns a
     * registration proxy ({@code EcoEnchantsCraftEnchantment}), NOT the {@link EcoEnchantBase}
     * model — and {@code getConfig()} lives on the impl/proxy, not on the {@link EcoEnchant}
     * interface — so we take the typed path when possible and otherwise reflect {@code getConfig()}
     * (the proxy class is not on our compile classpath). Returns {@code null} for vanilla-only
     * wrappers that expose no config.
     */
    private static @Nullable Config configOf(@Nullable Object wrapped) {
        if (wrapped == null) {
            return null;
        }
        if (wrapped instanceof EcoEnchantBase base) {
            return base.getConfig();
        }
        try {
            Object cfg = wrapped.getClass().getMethod("getConfig").invoke(wrapped);
            return cfg instanceof Config c ? c : null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    /**
     * Clears every per-enchantment cache. Call on {@code /se reload} so edits to
     * EcoEnchants ymls (display names, descriptions, requirement {@code not-met-lines})
     * take effect after {@code /ecoenchants reload} without a full SuperEnchanter restart.
     */
    public void clearCaches() {
        wrapCache.clear();
        baseNameCache.clear();
        requirementCache.clear();
        curseCache = null;
    }

    /** @return whether the enchantment is a curse (EcoEnchants {@code type: curse}) */
    public boolean isCurse(@NotNull Enchantment enchantment) {
        return "curse".equalsIgnoreCase(getTypeId(enchantment));
    }

    /** All curse-type enchantments in the registry (cached). */
    private @NotNull List<Enchantment> allCurses() {
        List<Enchantment> cached = curseCache;
        if (cached != null) {
            return cached;
        }
        List<Enchantment> curses = new ArrayList<>();
        var registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT);
        for (Enchantment ench : registry) {
            if (isCurse(ench)) {
                curses.add(ench);
            }
        }
        curseCache = curses;
        return curses;
    }

    /**
     * Picks a random curse that can apply to the item (targets it, not already present,
     * not vetoed), used by the enchanting table's curse roll.
     *
     * @param item            the item being enchanted
     * @param excludedKeyPaths curse key-paths to never pick (lowercase), e.g. {@code vanishing_curse}
     * @return a random applicable curse, or {@code null} if none applies
     */
    public @Nullable Enchantment randomApplicableCurse(@NotNull ItemStack item,
                                                       @NotNull java.util.Set<String> excludedKeyPaths) {
        var present = dev.scrulius.superenchanter.util.EnchantmentHelper.getEnchantments(item).keySet();
        List<Enchantment> applicable = new ArrayList<>();
        for (Enchantment curse : allCurses()) {
            if (excludedKeyPaths.contains(curse.getKey().getKey().toLowerCase(java.util.Locale.ROOT))) {
                continue; // vetoed (e.g. vanishing under keepinventory)
            }
            if (!present.contains(curse) && appliesToItem(curse, item)) {
                applicable.add(curse);
            }
        }
        if (applicable.isEmpty()) {
            return null;
        }
        return applicable.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(applicable.size()));
    }

    /**
     * Startup diagnostic for the EcoEnchants integration. Iterates the registry and
     * logs how many enchantments are EcoEnchants-wrapped, how many expose a readable
     * config (via {@link #configOf}) and how many declare requirement lines. If the
     * config is unreadable for ALL of them, it logs a loud WARN — that is exactly the
     * silent-failure mode (e.g. {@code wrap()} returning a proxy without {@code getConfig},
     * or an EcoEnchants API change) that would otherwise hide missing requirement lines.
     * Also warms the requirement cache.
     *
     * @param logger the plugin logger
     */
    public void logIntegrationSelfCheck(@NotNull java.util.logging.Logger logger) {
        int wrapped = 0;
        int configReadable = 0;
        int withRequirements = 0;
        try {
            var registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT);
            for (Enchantment ench : registry) {
                EcoEnchantLike like = wrap(ench);
                if (like == null) {
                    continue;
                }
                wrapped++;
                if (configOf(like) != null) {
                    configReadable++;
                }
                if (!getRequirementLines(ench).isEmpty()) {
                    withRequirements++;
                }
            }
        } catch (Exception e) {
            logger.warning("[SelfCheck] No se pudo recorrer el registro de encantamientos: " + e.getMessage());
            return;
        }
        if (wrapped > 0 && configReadable == 0) {
            logger.warning("[SelfCheck] ⚠ No se pudo leer el config de NINGÚN encantamiento vía reflexión "
                    + "(getConfig). Los requisitos de uso NO aparecerán en la mesa. ¿Cambió la API de EcoEnchants?");
        } else {
            logger.info("[SelfCheck] EcoEnchants OK — " + wrapped + " encantamientos, "
                    + configReadable + " con config legible, " + withRequirements + " con líneas de requisito.");
        }
    }
}
