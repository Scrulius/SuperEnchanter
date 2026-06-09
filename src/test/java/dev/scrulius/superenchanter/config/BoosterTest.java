/* SuperEnchanter - Author: Scrulius (GitHub) */
package dev.scrulius.superenchanter.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link PluginConfig.Booster#percentFor} — the per-rarity seal logic
 * that the enchanting menu relies on (a seal bound to one rarity contributes nothing,
 * and is not consumed, for any other rarity). Pure record method, no Bukkit at runtime.
 */
class BoosterTest {

    @Test
    @DisplayName("rarity-bound seal contributes only to its own rarity")
    void rarityBound() {
        final PluginConfig.Booster seal = new PluginConfig.Booster("raro", 100);
        assertEquals(100, seal.percentFor("raro"), "matching rarity → full percent (guarantee)");
        assertEquals(0, seal.percentFor("epico"), "wrong rarity → no contribution");
    }

    @Test
    @DisplayName("rarity match is case-insensitive")
    void caseInsensitive() {
        final PluginConfig.Booster seal = new PluginConfig.Booster("Raro", 40);
        assertEquals(40, seal.percentFor("raro"));
        assertEquals(40, seal.percentFor("RARO"));
    }

    @Test
    @DisplayName("universal seal (null rarity) contributes to any rarity, including null")
    void universal() {
        final PluginConfig.Booster seal = new PluginConfig.Booster(null, 25);
        assertEquals(25, seal.percentFor("comun"));
        assertEquals(25, seal.percentFor("divino"));
        assertEquals(25, seal.percentFor(null));
    }

    @Test
    @DisplayName("rarity-bound seal contributes nothing when the rarity is unknown (null)")
    void boundVsNullRarity() {
        final PluginConfig.Booster seal = new PluginConfig.Booster("epico", 50);
        assertEquals(0, seal.percentFor(null));
    }
}
