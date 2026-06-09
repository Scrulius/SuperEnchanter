/* SuperEnchanter - Author: Scrulius (GitHub) */
package dev.scrulius.superenchanter.gui.anvil;

import dev.scrulius.superenchanter.SuperEnchanterPlugin;
import dev.scrulius.superenchanter.config.PluginConfig;
import dev.scrulius.superenchanter.gui.anvil.AnvilLogic.AnvilResult;
import dev.scrulius.superenchanter.util.EnchantmentHelper;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for {@link AnvilLogic#calculateResult} exercising the real
 * Bukkit merge path (DataComponent enchantments + vanilla conflict rules) on a
 * MockBukkit server. Uses only vanilla enchantments — the EcoEnchants model is
 * not available in unit tests (its classes are {@code compileOnly}).
 */
class AnvilLogicMockTest {

    private static ServerMock server;
    private static SuperEnchanterPlugin plugin;
    private static PluginConfig config;

    @BeforeAll
    static void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(SuperEnchanterPlugin.class);
        config = plugin.getPluginConfig();
    }

    @AfterAll
    static void tearDown() {
        MockBukkit.unmock();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static ItemStack sword(Enchantment ench, int level) {
        ItemStack s = new ItemStack(Material.DIAMOND_SWORD);
        return level <= 0 ? s : EnchantmentHelper.applyEnchantment(s, ench, level);
    }

    private static ItemStack plainSword() {
        return new ItemStack(Material.DIAMOND_SWORD);
    }

    private static ItemStack book(Enchantment ench, int level) {
        ItemStack b = new ItemStack(Material.ENCHANTED_BOOK);
        return EnchantmentHelper.applyEnchantment(b, ench, level);
    }

    private static int levelOf(ItemStack item, Enchantment ench) {
        return EnchantmentHelper.getEnchantments(item).getOrDefault(ench, 0);
    }

    // ── Tests ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("two equal-level swords combine into the next level")
    void equalLevelsUpgrade() {
        AnvilResult r = AnvilLogic.calculateResult(
                sword(Enchantment.SHARPNESS, 1), sword(Enchantment.SHARPNESS, 1), config);

        assertTrue(r.isValid());
        assertEquals(1, r.levelsAdded());
        assertNotNull(r.resultItem());
        assertEquals(2, levelOf(r.resultItem(), Enchantment.SHARPNESS));
    }

    @Test
    @DisplayName("an enchanted book sacrifice transfers its stored enchantment")
    void bookSacrificeTransfers() {
        AnvilResult r = AnvilLogic.calculateResult(
                sword(Enchantment.SHARPNESS, 3), book(Enchantment.SHARPNESS, 3), config);

        assertTrue(r.isValid());
        assertEquals(4, levelOf(r.resultItem(), Enchantment.SHARPNESS));
    }

    @Test
    @DisplayName("adding an enchantment the target lacks")
    void addNewEnchantment() {
        AnvilResult r = AnvilLogic.calculateResult(
                plainSword(), sword(Enchantment.UNBREAKING, 2), config);

        assertTrue(r.isValid());
        assertEquals(2, r.levelsAdded());
        assertEquals(2, levelOf(r.resultItem(), Enchantment.UNBREAKING));
    }

    @Test
    @DisplayName("a conflicting enchantment is rejected, not merged")
    void conflictingEnchantmentRejected() {
        AnvilResult r = AnvilLogic.calculateResult(
                sword(Enchantment.SHARPNESS, 1), sword(Enchantment.SMITE, 1), config);

        assertFalse(r.isValid());
        assertNull(r.resultItem());
        assertTrue(r.conflicts().contains(Enchantment.SMITE));
        assertEquals(0, r.levelsAdded());
    }

    @Test
    @DisplayName("a max-level pair does not exceed the vanilla cap")
    void doesNotExceedMaxLevel() {
        final int max = Enchantment.SHARPNESS.getMaxLevel();
        AnvilResult r = AnvilLogic.calculateResult(
                sword(Enchantment.SHARPNESS, max), sword(Enchantment.SHARPNESS, max), config);

        // Already at max on both sides → nothing to gain → invalid (no-op merge).
        assertFalse(r.isValid());
    }

    @Test
    @DisplayName("missing inputs yield the EMPTY result")
    void missingInputsAreEmpty() {
        assertEquals(AnvilResult.EMPTY,
                AnvilLogic.calculateResult(null, sword(Enchantment.SHARPNESS, 1), config));
        assertEquals(AnvilResult.EMPTY,
                AnvilLogic.calculateResult(sword(Enchantment.SHARPNESS, 1), null, config));
        // A sacrifice with no enchantments contributes nothing.
        assertEquals(AnvilResult.EMPTY,
                AnvilLogic.calculateResult(sword(Enchantment.SHARPNESS, 1), plainSword(), config));
    }

    @Test
    @DisplayName("the XP cost matches the configured formula")
    void xpCostMatchesFormula() {
        AnvilResult r = AnvilLogic.calculateResult(
                sword(Enchantment.SHARPNESS, 1), sword(Enchantment.SHARPNESS, 1), config);

        int expected = AnvilFormulas.xpCost(config.getAnvilBaseXPCost(),
                config.getAnvilCostPerLevel(), r.levelsAdded(), config.getAnvilMaxCost());
        assertEquals(expected, r.xpCost());
    }

    // ── EcoEnchants gate (injected validation) ────────────────────────────────

    @Test
    @DisplayName("a gate that rejects an enchantment turns it into a conflict")
    void gateRejectsEnchantment() {
        // Gate stands in for the eco model: 0 = blacklisted / wrong target / conflict.
        AnvilEnchantGate rejectAll = ench -> 0;
        AnvilResult r = AnvilLogic.calculateResult(
                plainSword(), sword(Enchantment.UNBREAKING, 2), config, rejectAll);

        assertFalse(r.isValid());
        assertNull(r.resultItem());
        assertTrue(r.conflicts().contains(Enchantment.UNBREAKING));
    }

    @Test
    @DisplayName("a gate level cap clamps the merged level below the vanilla max")
    void gateCapClampsLevel() {
        AnvilEnchantGate capAtTwo = ench -> 2; // eco/override cap: Sharpness II
        AnvilResult r = AnvilLogic.calculateResult(
                sword(Enchantment.SHARPNESS, 1), book(Enchantment.SHARPNESS, 3), config, capAtTwo);

        assertTrue(r.isValid());
        // Would merge to III, but the gate caps it at II.
        assertEquals(2, levelOf(r.resultItem(), Enchantment.SHARPNESS));
    }

    @Test
    @DisplayName("ALLOW_ALL reproduces the vanilla-only behaviour")
    void allowAllMatchesDefault() {
        AnvilResult gated = AnvilLogic.calculateResult(
                sword(Enchantment.SHARPNESS, 1), sword(Enchantment.SHARPNESS, 1),
                config, AnvilEnchantGate.ALLOW_ALL);
        AnvilResult plain = AnvilLogic.calculateResult(
                sword(Enchantment.SHARPNESS, 1), sword(Enchantment.SHARPNESS, 1), config);

        assertEquals(plain.isValid(), gated.isValid());
        assertEquals(levelOf(plain.resultItem(), Enchantment.SHARPNESS),
                levelOf(gated.resultItem(), Enchantment.SHARPNESS));
    }
}
