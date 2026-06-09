/* SuperEnchanter - Author: Scrulius (GitHub) */
package dev.scrulius.superenchanter.gui.enchanting;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Unit tests for the pure enchanting curves in {@link EnchantFormulas}. */
class EnchantFormulasTest {

    @Nested
    @DisplayName("xpCostForLevel")
    class XpCostForLevel {

        @Test
        @DisplayName("linear curve (exponent 1) with no rarity scaling")
        void linearCurve() {
            // base 10 + 5 * 3^1 = 25, rarity 1.0
            assertEquals(25, EnchantFormulas.xpCostForLevel(10, 5, 3, 1.0, 1.0, 1000));
        }

        @Test
        @DisplayName("quadratic curve (exponent 2)")
        void quadraticCurve() {
            // base 10 + 5 * 4^2 = 10 + 80 = 90
            assertEquals(90, EnchantFormulas.xpCostForLevel(10, 5, 4, 2.0, 1.0, 1000));
        }

        @Test
        @DisplayName("rarity multiplier scales the raw cost")
        void rarityScales() {
            // (10 + 5*2) = 20, * 2.5 = 50
            assertEquals(50, EnchantFormulas.xpCostForLevel(10, 5, 2, 1.0, 2.5, 1000));
        }

        @Test
        @DisplayName("the cap stops the legendary explosion")
        void cappedAtMax() {
            // a steep curve that would blow past the cap
            assertEquals(30, EnchantFormulas.xpCostForLevel(10, 50, 10, 2.0, 3.0, 30));
        }

        @Test
        @DisplayName("never below 1, even with tiny inputs")
        void flooredAtOne() {
            assertEquals(1, EnchantFormulas.xpCostForLevel(0, 0, 1, 1.0, 0.0, 1000));
        }

        @Test
        @DisplayName("result is rounded, not truncated")
        void rounds() {
            // raw 2.6 -> round -> 3 (truncation would give 2)
            assertEquals(3, EnchantFormulas.xpCostForLevel(2.6, 0, 1, 1.0, 1.0, 1000));
        }
    }

    @Nested
    @DisplayName("cumulativeCost")
    class CumulativeCost {

        // Steps: reaching I costs 10, II costs 20, III costs 30, IV costs 40, V costs 50.
        private final int[] steps = {10, 20, 30, 40, 50};

        @Test
        @DisplayName("from scratch (level 0) to V sums every step")
        void fromZeroToMax() {
            // 10 + 20 + 30 + 40 + 50 = 150
            assertEquals(150, EnchantFormulas.cumulativeCost(steps, 0, 5));
        }

        @Test
        @DisplayName("from scratch to level III sums the first three steps")
        void fromZeroToThree() {
            // 10 + 20 + 30 = 60
            assertEquals(60, EnchantFormulas.cumulativeCost(steps, 0, 3));
        }

        @Test
        @DisplayName("upgrading from an existing level only charges the remaining steps")
        void fromExistingLevel() {
            // already at II -> upgrade to V: 30 + 40 + 50 = 120
            assertEquals(120, EnchantFormulas.cumulativeCost(steps, 2, 5));
        }

        @Test
        @DisplayName("a single step up charges exactly that step")
        void singleStep() {
            // already at III -> IV: just 40
            assertEquals(40, EnchantFormulas.cumulativeCost(steps, 3, 4));
        }

        @Test
        @DisplayName("the whole path equals the sum of its single steps")
        void pathEqualsSumOfSteps() {
            int piecewise = EnchantFormulas.cumulativeCost(steps, 0, 1)
                    + EnchantFormulas.cumulativeCost(steps, 1, 2)
                    + EnchantFormulas.cumulativeCost(steps, 2, 3)
                    + EnchantFormulas.cumulativeCost(steps, 3, 4)
                    + EnchantFormulas.cumulativeCost(steps, 4, 5);
            assertEquals(EnchantFormulas.cumulativeCost(steps, 0, 5), piecewise);
        }

        @Test
        @DisplayName("target at or below current level floors at 1 (nothing to charge)")
        void noStepsFloorsAtOne() {
            assertEquals(1, EnchantFormulas.cumulativeCost(steps, 5, 5));
            assertEquals(1, EnchantFormulas.cumulativeCost(steps, 3, 2));
        }

        @Test
        @DisplayName("target beyond the array is clamped to available steps")
        void targetBeyondArray() {
            // steps only define 5 levels; asking for VII sums what exists
            assertEquals(150, EnchantFormulas.cumulativeCost(steps, 0, 7));
        }
    }

    @Nested
    @DisplayName("effectiveChance")
    class EffectiveChance {

        @Test
        @DisplayName("base plus booster, no clamping needed")
        void basePlusBooster() {
            assertEquals(75, EnchantFormulas.effectiveChance(50, 25));
        }

        @Test
        @DisplayName("a guarantee orb (+100) always reaches the cap")
        void guaranteeReachesCap() {
            assertEquals(100, EnchantFormulas.effectiveChance(30, 100));
            assertEquals(100, EnchantFormulas.effectiveChance(0, 100));
        }

        @Test
        @DisplayName("sum is clamped at 100")
        void clampedAtHundred() {
            assertEquals(100, EnchantFormulas.effectiveChance(90, 50));
        }

        @Test
        @DisplayName("no booster leaves the base untouched")
        void noBooster() {
            assertEquals(50, EnchantFormulas.effectiveChance(50, 0));
        }

        @Test
        @DisplayName("never below 0")
        void flooredAtZero() {
            assertEquals(0, EnchantFormulas.effectiveChance(0, 0));
        }
    }

    @Nested
    @DisplayName("requiredPowerForLevel")
    class RequiredPowerForLevel {

        @Test
        @DisplayName("floor plus per-level step")
        void floorPlusStep() {
            // 10 + 3*4 = 22
            assertEquals(22, EnchantFormulas.requiredPowerForLevel(10, 4, 3, 1000));
        }

        @Test
        @DisplayName("capped at maxPower")
        void cappedAtMaxPower() {
            // 10 + 5*100 = 510, capped at 64
            assertEquals(64, EnchantFormulas.requiredPowerForLevel(10, 100, 5, 64));
        }

        @Test
        @DisplayName("level 1 is floor + one step")
        void levelOne() {
            assertEquals(13, EnchantFormulas.requiredPowerForLevel(10, 3, 1, 1000));
        }
    }

    @Nested
    @DisplayName("toRoman")
    class ToRoman {

        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource({
                "1, I", "2, II", "3, III", "4, IV", "5, V",
                "6, VI", "7, VII", "8, VIII", "9, IX", "10, X",
        })
        void mapsOneToTen(int number, String expected) {
            assertEquals(expected, EnchantFormulas.toRoman(number));
        }

        @ParameterizedTest(name = "{0} falls back to decimal")
        @ValueSource(ints = {0, -1, 11, 42})
        void fallsBackToDecimalOutOfRange(int number) {
            assertEquals(String.valueOf(number), EnchantFormulas.toRoman(number));
        }

        @Test
        @DisplayName("zero is not a Roman numeral")
        void zeroIsDecimal() {
            assertTrue(EnchantFormulas.toRoman(0).equals("0"));
        }
    }
}
