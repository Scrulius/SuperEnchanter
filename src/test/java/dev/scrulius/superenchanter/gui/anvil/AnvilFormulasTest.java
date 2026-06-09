/* SuperEnchanter - Author: Scrulius (GitHub) */
package dev.scrulius.superenchanter.gui.anvil;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Unit tests for the pure anvil arithmetic in {@link AnvilFormulas}. */
class AnvilFormulasTest {

    @Nested
    @DisplayName("mergeLevel")
    class MergeLevel {

        @Test
        @DisplayName("equal levels upgrade by one")
        void equalLevelsUpgrade() {
            assertEquals(4, AnvilFormulas.mergeLevel(3, 3, 5));
        }

        @Test
        @DisplayName("equal levels at max stay at max (no over-cap)")
        void equalLevelsAtMaxStay() {
            assertEquals(5, AnvilFormulas.mergeLevel(5, 5, 5));
        }

        @Test
        @DisplayName("different levels keep the higher one")
        void differentLevelsKeepHigher() {
            assertEquals(4, AnvilFormulas.mergeLevel(2, 4, 5));
            assertEquals(4, AnvilFormulas.mergeLevel(4, 2, 5));
        }

        @Test
        @DisplayName("adding to an item without the enchant (current 0)")
        void addFromScratch() {
            assertEquals(3, AnvilFormulas.mergeLevel(0, 3, 5));
        }

        @Test
        @DisplayName("a higher level is never allowed past max")
        void higherIsClampedToMax() {
            // e.g. a level-7 book on a vanilla max-5 enchant
            assertEquals(5, AnvilFormulas.mergeLevel(0, 7, 5));
        }

        @ParameterizedTest(name = "current={0}, sacrifice={1}, max={2} -> {3}")
        @CsvSource({
                "0, 1, 5, 1",
                "1, 1, 5, 2",
                "1, 2, 5, 2",
                "4, 4, 5, 5",
                "5, 5, 5, 5",
                "3, 1, 3, 3",
        })
        void table(int current, int sacrifice, int max, int expected) {
            assertEquals(expected, AnvilFormulas.mergeLevel(current, sacrifice, max));
        }
    }

    @Nested
    @DisplayName("xpCost")
    class XpCost {

        @Test
        @DisplayName("base plus per-level term")
        void baseAndPerLevel() {
            // 5 + (3 levels * 4) = 17
            assertEquals(17, AnvilFormulas.xpCost(5, 4, 3, 100));
        }

        @Test
        @DisplayName("clamped to the max cost")
        void clampedToMax() {
            // 5 + (50 * 4) = 205, capped at 30
            assertEquals(30, AnvilFormulas.xpCost(5, 4, 50, 30));
        }

        @Test
        @DisplayName("zero levels yields just the base")
        void zeroLevels() {
            assertEquals(5, AnvilFormulas.xpCost(5, 4, 0, 100));
        }
    }
}
