/* SuperEnchanter - Author: Scrulius (GitHub) */
package dev.scrulius.superenchanter.gui.anvil;

import org.bukkit.enchantments.Enchantment;
import org.jetbrains.annotations.NotNull;

/**
 * Injected per-enchantment verdict used by {@link AnvilLogic} so the merge math
 * can stay pure and unit-testable while the real EcoEnchants rules (targets,
 * conflicts, required, type limits, blacklist, per-enchant level caps) are decided
 * elsewhere — exactly the same model the enchanting table and the transfer menu
 * use. Production builds a gate from {@code EnchantingLogic.analyze(target)}; unit
 * tests pass {@link #ALLOW_ALL} (or a hand-rolled gate) so no EcoEnchants classes
 * are needed at test time.
 */
@FunctionalInterface
public interface AnvilEnchantGate {

    /**
     * The maximum level the enchantment may reach on the target item, folding in
     * any per-enchant {@code max-level} override.
     *
     * @param enchantment the enchantment a sacrifice wants to contribute
     * @return the allowed maximum level, or {@code 0} when it must not be applied
     *         at all — blacklisted, wrong target, conflicting with what the target
     *         already has, missing a required enchantment, or over its type limit
     */
    int allowedMaxLevel(@NotNull Enchantment enchantment);

    /** Permissive gate (no EcoEnchants restrictions) — for unit tests / fallback. */
    AnvilEnchantGate ALLOW_ALL = enchantment -> Integer.MAX_VALUE;
}
