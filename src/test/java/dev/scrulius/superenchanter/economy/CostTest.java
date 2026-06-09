/* SuperEnchanter - Author: Scrulius (GitHub) */
package dev.scrulius.superenchanter.economy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Pure, Bukkit-free tests for {@link Cost} formatting and {@link CostType} parsing. */
class CostTest {

    @Nested
    @DisplayName("CostType.fromString")
    class FromString {

        @ParameterizedTest(name = "\"{0}\" -> {1}")
        @CsvSource({
                "XP, XP", "xp, XP", "  Xp  , XP",
                "VAULT, VAULT", "vault, VAULT",
                "PLAYER_POINTS, PLAYER_POINTS", "player_points, PLAYER_POINTS",
        })
        void parsesKnownTypes(String raw, CostType expected) {
            assertEquals(expected, CostType.fromString(raw));
        }

        @ParameterizedTest(name = "unknown \"{0}\" -> XP")
        @ValueSource(strings = {"gold", "money", "PLAYERPOINTS", "123"})
        void unknownFallsBackToXp(String raw) {
            assertEquals(CostType.XP, CostType.fromString(raw));
        }

        @ParameterizedTest(name = "null/blank -> XP")
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        void nullOrBlankFallsBackToXp(String raw) {
            assertEquals(CostType.XP, CostType.fromString(raw));
        }
    }

    @Nested
    @DisplayName("displayText")
    class Display {

        @Test
        @DisplayName("XP renders as whole levels")
        void xp() {
            assertEquals("39 XP", Cost.xp(39).displayText());
        }

        @Test
        @DisplayName("Vault money uses a grouped, currency-prefixed format")
        void vault() {
            assertEquals("$1,500", new Cost(CostType.VAULT, 1500).displayText());
        }

        @Test
        @DisplayName("PlayerPoints renders as tokens")
        void playerPoints() {
            assertEquals("25 Tokens", new Cost(CostType.PLAYER_POINTS, 25).displayText());
        }
    }

    @Nested
    @DisplayName("intAmount")
    class IntAmount {

        @Test
        @DisplayName("rounds to the nearest whole number")
        void rounds() {
            assertEquals(40, new Cost(CostType.XP, 39.6).intAmount());
            assertEquals(39, new Cost(CostType.XP, 39.4).intAmount());
        }
    }

    @Test
    @DisplayName("a null type normalises to XP")
    void nullTypeNormalises() {
        assertEquals(CostType.XP, new Cost(null, 10).type());
    }
}
