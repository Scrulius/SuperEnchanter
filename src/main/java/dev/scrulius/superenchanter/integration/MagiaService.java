/* SuperEnchanter - Author: Scrulius (GitHub) */
package dev.scrulius.superenchanter.integration;

import com.willfp.ecoskills.skills.Skill;
import dev.scrulius.supercore.api.SuperCore;
import dev.scrulius.superenchanter.SuperEnchanterPlugin;
import dev.scrulius.superenchanter.economy.Cost;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The "Magia" progression loop: enchanting at the custom table grants XP to an EcoSkills
 * skill (via {@link SuperCore}), whose level in turn improves enchanting (success chance,
 * cost discount) and — through the skill's own {@code wisdom} reward — mana.
 *
 * <p>This class is ONLY constructed when SuperCore is installed (see
 * {@code SuperEnchanterPlugin.onEnable}); referencing it otherwise would link SuperCore
 * classes that may be absent. It still degrades gracefully at runtime: if EcoSkills isn't
 * present, {@link #isEnabled()} is {@code false} and every accessor is a no-op/identity.
 *
 * <p>The custom table bypasses vanilla enchanting, so the native {@code enchanting} skill
 * would never level on its own — we feed it XP by API. See {@code docs/PLAN_MAGIA.md}.
 */
public final class MagiaService {

    private final SuperEnchanterPlugin plugin;

    private boolean enabled;
    private String skillId = "enchanting";
    private final Map<String, Integer> xpByRarity = new HashMap<>();
    private int xpDefault = 5;
    private double xpFailFraction = 0.25;
    private double successPerLevel = 0.5;
    private int successMax = 25;
    private double discountPerLevel = 0.4;
    private int discountMax = 20;
    private double refundPerLevel = 0.6;
    private int refundMax = 30;
    private boolean gatingEnabled = false;
    private final Map<String, Integer> gateLevels = new HashMap<>();

    public MagiaService(@NotNull SuperEnchanterPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    /** Re-reads {@code enchanting.magia} and re-evaluates whether the loop is active. */
    public void reload() {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("enchanting.magia");
        boolean cfgEnabled = sec == null || sec.getBoolean("enabled", true);

        if (sec != null) {
            this.skillId = sec.getString("skill-id", "enchanting");
            this.xpFailFraction = sec.getDouble("xp-fail-fraction", 0.25);
            this.successPerLevel = sec.getDouble("success.per-level", 0.5);
            this.successMax = sec.getInt("success.max-bonus", 25);
            this.discountPerLevel = sec.getDouble("discount.per-level", 0.4);
            this.discountMax = sec.getInt("discount.max-percent", 20);
            this.refundPerLevel = sec.getDouble("refund.per-level", 0.6);
            this.refundMax = sec.getInt("refund.max-percent", 30);
            this.gatingEnabled = sec.getBoolean("gating.enabled", false);

            xpByRarity.clear();
            ConfigurationSection xpSec = sec.getConfigurationSection("xp-per-rarity");
            if (xpSec != null) {
                for (String r : xpSec.getKeys(false)) {
                    if (r.equalsIgnoreCase("default")) {
                        this.xpDefault = xpSec.getInt(r, 5);
                    } else {
                        xpByRarity.put(r.toLowerCase(Locale.ROOT), xpSec.getInt(r));
                    }
                }
            }

            gateLevels.clear();
            ConfigurationSection gateSec = sec.getConfigurationSection("gating.required-level");
            if (gateSec != null) {
                for (String r : gateSec.getKeys(false)) {
                    gateLevels.put(r.toLowerCase(Locale.ROOT), gateSec.getInt(r));
                }
            }
        }

        // Active only if configured ON and the runtime infrastructure is actually there.
        boolean infra = SuperCore.isReady() && SuperCore.ecoSkills().isEnabled();
        this.enabled = cfgEnabled && infra;
    }

    public boolean isEnabled() {
        return enabled;
    }

    private @Nullable Skill skill() {
        return SuperCore.ecoSkills().skillById(skillId);
    }

    /** @return the player's current Magia level (0 if inactive/unresolved). */
    public int level(@NotNull Player player) {
        if (!enabled) return 0;
        Skill s = skill();
        return s == null ? 0 : SuperCore.ecoSkills().getSkillLevel(player, s);
    }

    /**
     * The skill's maximum level, read natively from EcoSkills ({@code Skill.getMaxLevel},
     * i.e. the {@code max-level} of the skill's yml) — used to scale the level progress
     * bar in the table's stats panel. 0 when inactive/unresolved (renders an empty bar).
     */
    public int maxLevel() {
        if (!enabled) return 0;
        Skill s = skill();
        return s == null ? 0 : s.getMaxLevel();
    }

    /** Carril 1 — success-chance bonus (percentage points) for this player's level. */
    public int successBonus(@NotNull Player player) {
        if (!enabled) return 0;
        return (int) Math.min(successMax, Math.floor(level(player) * successPerLevel));
    }

    /** Carril 2 — cost multiplier (e.g. 0.80 = −20%). 1.0 when inactive. */
    public double costMultiplier(@NotNull Player player) {
        if (!enabled) return 1.0;
        double discountPct = Math.min(discountMax, level(player) * discountPerLevel);
        return 1.0 - (discountPct / 100.0);
    }

    /**
     * Carril 2 — applies the Magia discount on top of an already permission-discounted
     * cost (combines multiplicatively). Identity when inactive, so call sites can wrap
     * a {@link dev.scrulius.superenchanter.economy.CostService#effectiveCost} result
     * unconditionally and the displayed price always matches what's charged.
     */
    public @NotNull Cost applyDiscount(@NotNull Player player, @NotNull Cost cost) {
        final double m = costMultiplier(player);
        return m >= 1.0 ? cost : new Cost(cost.type(), cost.amount() * m);
    }

    /** Carril 2 as a display percentage (e.g. {@code 8} = −8% coste). 0 when inactive. */
    public int discountPercent(@NotNull Player player) {
        if (!enabled) return 0;
        return (int) Math.min(discountMax, Math.floor(level(player) * discountPerLevel));
    }

    /**
     * Sub-ability "Reembolso Arcano" — % chance to refund a failed rung's cost,
     * scaling with the player's Magia level. 0 when inactive.
     */
    public int refundChance(@NotNull Player player) {
        if (!enabled) return 0;
        return (int) Math.min(refundMax, Math.floor(level(player) * refundPerLevel));
    }

    /**
     * Carril 3 — the max-mana bonus this skill grants (= the {@code wisdom} reward,
     * one per level with the default skill config). For display only. 0 when inactive.
     */
    public int manaBonus(@NotNull Player player) {
        return enabled ? level(player) : 0;
    }

    /**
     * The player's current Magia-XP bonus (percentage points) as ACTUALLY applied when
     * granting XP — read natively from EcoSkills (permission boosters × the libreforge
     * {@code skill_xp_multiplier} effect total). Reflects ANY source automatically: the
     * mage-armor enchantments, purchased boosters, other plugins using the standard
     * systems — no hardcoded list. {@code 44} = +44% (e.g. both mage pieces). 0 when
     * inactive or no bonus is active.
     */
    public int xpBonusPercent(@NotNull Player player) {
        if (!enabled) return 0;
        Skill s = skill();
        if (s == null) return 0;
        double mult = SuperCore.ecoSkills().effectiveSkillXpMultiplier(player, s);
        return (int) Math.round((mult - 1.0) * 100.0);
    }

    /** Carril 5 — gating. True if the player may enchant this rarity (always true when off). */
    public boolean canEnchant(@NotNull Player player, @Nullable String rarityId) {
        if (!enabled || !gatingEnabled) return true;
        Integer req = gateLevels.get(rarityId == null ? "" : rarityId.toLowerCase(Locale.ROOT));
        return req == null || level(player) >= req;
    }

    /** Minimum Magia level required for a rarity (0 if none / gating off) — for messaging. */
    public int requiredLevel(@Nullable String rarityId) {
        if (!gatingEnabled) return 0;
        return gateLevels.getOrDefault(rarityId == null ? "" : rarityId.toLowerCase(Locale.ROOT), 0);
    }

    /**
     * Grants Magia XP for an enchant operation: {@code levelsGained × xp(rarity)}, plus a
     * {@code xp-fail-fraction} slice of one level's XP if a rung's roll failed ("learn from
     * the error"). No-op when inactive or nothing was attempted.
     *
     * @return the XP actually granted (0 when inactive / nothing granted), for feedback.
     */
    public double grantXp(@NotNull Player player, @Nullable String rarityId, int levelsGained, boolean failedRung) {
        if (!enabled || (levelsGained <= 0 && !failedRung)) return 0.0;
        int per = xpByRarity.getOrDefault(rarityId == null ? "" : rarityId.toLowerCase(Locale.ROOT), xpDefault);
        double xp = (double) levelsGained * per + (failedRung ? per * xpFailFraction : 0.0);
        if (xp <= 0) return 0.0;
        Skill s = skill();
        if (s != null) {
            // Grant via the NATURAL path: it fires PlayerSkillXPGainEvent and applies XP
            // multipliers. This is REQUIRED for two independent reasons:
            //   1) the libreforge skill_xp_multiplier effect (mage-armor enchants) listens
            //      to that event;
            //   2) AxBoosters' "ecoskills:experience" booster also works by listening to
            //      that event and multiplying getGainedXP/setGainedXP — raw giveSkillXp
            //      would silently skip ANY booster active for the player.
            // The downside (EcoSkills' own "+xp" action bar colliding with the table's
            // feedback) is solved on the EcoSkills config side (gain-xp feedback moved to
            // a boss bar), NOT by going raw. The {magia} suffix shows the base XP.
            SuperCore.ecoSkills().gainSkillXp(player, s, xp);
            return xp;
        }
        return 0.0;
    }
}
