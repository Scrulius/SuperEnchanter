/* SuperEnchanter - Author: Scrulius (GitHub) */
package dev.scrulius.superenchanter.economy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pure tests for {@link XpMath} — the vanilla experience curve used to translate
 * point costs into the honest "≈ N niveles" feedback. Expected values come
 * straight from the vanilla formulas (Minecraft wiki).
 */
class XpMathTest {

    @Nested
    @DisplayName("totalPointsForLevel")
    class TotalPoints {

        @ParameterizedTest(name = "level {0} = {1} points")
        @CsvSource({
                "0, 0",
                "1, 7",
                "16, 352",   // first-branch boundary: 16² + 6·16
                "17, 394",   // 352 + (5·16 − 38)
                "30, 1395",  // the classic level-30 enchanting benchmark
                "31, 1507",  // second-branch boundary
                "32, 1628",  // 1507 + (9·31 − 158)
                "50, 5345",
        })
        void vanillaCurve(int level, long expected) {
            assertEquals(expected, XpMath.totalPointsForLevel(level));
        }

        @Test
        @DisplayName("negative levels are treated as zero")
        void negativeIsZero() {
            assertEquals(0, XpMath.totalPointsForLevel(-3));
        }
    }

    @Nested
    @DisplayName("levelForPoints")
    class LevelForPoints {

        @ParameterizedTest(name = "{0} points -> level {1}")
        @CsvSource({
                "0, 0",
                "6, 0",      // one point short of level 1
                "7, 1",
                "351, 15",   // one point short of the 16 boundary
                "352, 16",
                "1506, 30",
                "1507, 31",
                "1627, 31",
                "1628, 32",
                "5345, 50",
        })
        void inverseOfTotal(long points, int expected) {
            assertEquals(expected, XpMath.levelForPoints(points));
        }

        @Test
        @DisplayName("roundtrips with totalPointsForLevel across every band")
        void roundtrip() {
            for (int level = 0; level <= 100; level++) {
                assertEquals(level, XpMath.levelForPoints(XpMath.totalPointsForLevel(level)),
                        "exact boundary for level " + level);
                if (level > 0) {
                    assertEquals(level - 1,
                            XpMath.levelForPoints(XpMath.totalPointsForLevel(level) - 1),
                            "one point short of level " + level);
                }
            }
        }
    }

    @Nested
    @DisplayName("levelsLost")
    class LevelsLost {

        @Test
        @DisplayName("a cost below one level's worth reports zero levels")
        void lessThanALevel() {
            // Level 30 with 55 points of progress (total 1450; the 30 boundary is
            // 1395): a 50-point cost stays at 30, a 100-point cost dips to 29.
            assertEquals(0, XpMath.levelsLost(30, 1450, 50));
            assertEquals(1, XpMath.levelsLost(30, 1450, 100));
        }

        @Test
        @DisplayName("at an exact level boundary any cost loses a level")
        void exactBoundary() {
            assertEquals(1, XpMath.levelsLost(30, 1395, 1));
        }

        @Test
        @DisplayName("level 30 paying down to the level-16 boundary loses 14 levels")
        void bigHit() {
            assertEquals(14, XpMath.levelsLost(30, 1395, 1395 - 352));
        }

        @Test
        @DisplayName("a cost above the player's total drains every level")
        void overdraw() {
            assertEquals(30, XpMath.levelsLost(30, 1395, 9999));
        }

        @Test
        @DisplayName("never negative")
        void neverNegative() {
            assertEquals(0, XpMath.levelsLost(0, 0, 100));
        }
    }
}
