/* SuperEnchanter - Author: Scrulius (GitHub) */
package dev.scrulius.superenchanter.economy;

import dev.scrulius.superenchanter.SuperEnchanterPlugin;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for {@link CostService} on a MockBukkit server. The XP path
 * is exercised end-to-end; Vault and PlayerPoints are absent in the mock, so
 * those currencies must always be unaffordable / un-chargeable.
 */
class CostServiceMockTest {

    private static ServerMock server;
    private static SuperEnchanterPlugin plugin;
    private static CostService cost;

    private Player player;

    @BeforeAll
    static void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(SuperEnchanterPlugin.class);
        cost = plugin.getCostService();
    }

    @AfterAll
    static void tearDown() {
        MockBukkit.unmock();
    }

    @BeforeEach
    void freshPlayer() {
        player = server.addPlayer();
        player.setLevel(30);
    }

    // ── XP (raw POINTS, not levels — level 30 holds exactly 1395 points) ─────

    @Test
    @DisplayName("XP affordability compares against the player's total experience points")
    void xpCanAfford() {
        assertTrue(cost.canAfford(player, Cost.xp(1000)));
        assertTrue(cost.canAfford(player, Cost.xp(1395)));
        assertFalse(cost.canAfford(player, Cost.xp(1396)));
    }

    @Test
    @DisplayName("XP deduct subtracts points and recomputes the level on the vanilla curve")
    void xpDeductSucceeds() {
        // 1395 − 1043 = 352 points = exactly level 16.
        assertTrue(cost.deduct(player, Cost.xp(1043)));
        assertEquals(16, player.getLevel());
    }

    @Test
    @DisplayName("XP deduct takes nothing when the player can't pay")
    void xpDeductInsufficient() {
        assertFalse(cost.deduct(player, Cost.xp(1500)));
        assertEquals(30, player.getLevel(), "level must be untouched on a failed charge");
    }

    @Test
    @DisplayName("XP balance text shows points plus the level for context")
    void xpBalanceText() {
        assertEquals("1,395 XP (nivel 30)", cost.balanceText(player, CostType.XP));
    }

    @Test
    @DisplayName("xpLevelsEquivalent reports the real level hit of a point cost")
    void xpLevelsEquivalent() {
        assertEquals(0, cost.xpLevelsEquivalent(player, Cost.xp(50)));
        assertEquals(14, cost.xpLevelsEquivalent(player, Cost.xp(1043)));
        assertEquals(0, cost.xpLevelsEquivalent(player, new Cost(CostType.VAULT, 1000)),
                "non-XP costs have no level equivalence");
    }

    // ── Absent economies ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Vault is never affordable or chargeable when the plugin is absent")
    void vaultAbsent() {
        assertFalse(cost.canAfford(player, new Cost(CostType.VAULT, 1)));
        assertFalse(cost.deduct(player, new Cost(CostType.VAULT, 1)));
    }

    @Test
    @DisplayName("PlayerPoints is never affordable or chargeable when the plugin is absent")
    void playerPointsAbsent() {
        assertFalse(cost.canAfford(player, new Cost(CostType.PLAYER_POINTS, 1)));
        assertFalse(cost.deduct(player, new Cost(CostType.PLAYER_POINTS, 1)));
    }

    // ── Permission discounts ──────────────────────────────────────────────────

    @Test
    @DisplayName("no discount permission → full price")
    void noDiscount() {
        assertEquals(1.0, cost.discountMultiplier(player));
        assertEquals(20, cost.effectiveCost(player, Cost.xp(20)).intAmount());
    }

    @Test
    @DisplayName("discount.<n> permission applies an N% discount")
    void percentDiscount() {
        player.addAttachment(plugin, "superenchanter.cost.discount.25", true);
        assertEquals(0.75, cost.discountMultiplier(player));
        assertEquals(15, cost.effectiveCost(player, Cost.xp(20)).intAmount()); // 20 * 0.75
    }

    @Test
    @DisplayName("the highest discount permission wins")
    void highestDiscountWins() {
        player.addAttachment(plugin, "superenchanter.cost.discount.10", true);
        player.addAttachment(plugin, "superenchanter.cost.discount.40", true);
        assertEquals(0.6, cost.discountMultiplier(player));
    }

    @Test
    @DisplayName("the bypass permission makes operations free")
    void bypassIsFree() {
        player.addAttachment(plugin, "superenchanter.cost.bypass", true);
        assertEquals(0.0, cost.discountMultiplier(player));
        assertEquals(0, cost.effectiveCost(player, Cost.xp(20)).intAmount());
        assertTrue(cost.canAfford(player, cost.effectiveCost(player, Cost.xp(999))));
    }

    // ── Message-key routing (static, currency-driven) ─────────────────────────

    @Test
    @DisplayName("insufficient-funds message key is chosen per currency")
    void insufficientKeyRouting() {
        assertEquals("anvil.insufficient-xp", CostService.insufficientKey("anvil", CostType.XP));
        assertEquals("anvil.insufficient-money", CostService.insufficientKey("anvil", CostType.VAULT));
        assertEquals("enchanting.insufficient-tokens",
                CostService.insufficientKey("enchanting", CostType.PLAYER_POINTS));
    }
}
