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
    @DisplayName("pityBonus")
    class PityBonus {

        @Test
        @DisplayName("no streak (or disabled knobs) means no bonus")
        void noStreakNoBonus() {
            assertEquals(0, EnchantFormulas.pityBonus(0, 3, 30));
            assertEquals(0, EnchantFormulas.pityBonus(-1, 3, 30));
            assertEquals(0, EnchantFormulas.pityBonus(5, 0, 30));
            assertEquals(0, EnchantFormulas.pityBonus(5, 3, 0));
        }

        @Test
        @DisplayName("bonus grows linearly with the streak")
        void linearGrowth() {
            assertEquals(3, EnchantFormulas.pityBonus(1, 3, 30));
            assertEquals(12, EnchantFormulas.pityBonus(4, 3, 30));
        }

        @Test
        @DisplayName("bonus is capped at max-bonus")
        void capped() {
            assertEquals(30, EnchantFormulas.pityBonus(10, 3, 30));
            assertEquals(30, EnchantFormulas.pityBonus(100, 3, 30));
        }
    }

    @Nested
    @DisplayName("geometricCost")
    class GeometricCost {

        @Test
        @DisplayName("level 1 is just base × rarity (growth^0 = 1)")
        void levelOneIsBase() {
            assertEquals(500, EnchantFormulas.geometricCost(500, 2.0, 1, 1.0, 1.0, 1_000_000));
            assertEquals(20000, EnchantFormulas.geometricCost(500, 2.0, 1, 40.0, 1.0, 1_000_000));
        }

        @Test
        @DisplayName("each level multiplies the previous by the growth factor")
        void geometricGrowth() {
            // base 500, growth 2 → 500, 1000, 2000, 4000, 8000
            assertEquals(1000, EnchantFormulas.geometricCost(500, 2.0, 2, 1.0, 1.0, 1_000_000));
            assertEquals(2000, EnchantFormulas.geometricCost(500, 2.0, 3, 1.0, 1.0, 1_000_000));
            assertEquals(8000, EnchantFormulas.geometricCost(500, 2.0, 5, 1.0, 1.0, 1_000_000));
        }

        @Test
        @DisplayName("rarity multiplier scales the whole curve")
        void rarityScales() {
            // épico ×6 at level 3: 500 * 6 * 2^2 = 12000
            assertEquals(12000, EnchantFormulas.geometricCost(500, 2.0, 3, 6.0, 1.0, 1_000_000));
        }

        @Test
        @DisplayName("the finishing premium multiplies the max-level rung")
        void finalPremium() {
            // common level 5 final: 500 * 1 * 2^4 * 1.5 = 12000
            assertEquals(12000, EnchantFormulas.geometricCost(500, 2.0, 5, 1.0, 1.5, 1_000_000));
        }

        @Test
        @DisplayName("the cap clamps the runaway high-level cost")
        void cappedAtMax() {
            // 500 * 40 * 2^4 = 320000, capped at 150000
            assertEquals(150000, EnchantFormulas.geometricCost(500, 2.0, 5, 40.0, 1.0, 150000));
        }

        @Test
        @DisplayName("an enormous level can never overflow the int return")
        void noOverflow() {
            // 2^200 would overflow; the cap is applied before rounding
            assertEquals(150000, EnchantFormulas.geometricCost(500, 2.0, 200, 40.0, 1.5, 150000));
        }

        @Test
        @DisplayName("never below 1, even with tiny inputs")
        void flooredAtOne() {
            assertEquals(1, EnchantFormulas.geometricCost(0, 2.0, 1, 0.0, 1.0, 1000));
        }

        @Test
        @DisplayName("result is rounded, not truncated")
        void rounds() {
            // raw 2.6 -> round -> 3 (truncation would give 2)
            assertEquals(3, EnchantFormulas.geometricCost(2.6, 1.0, 1, 1.0, 1.0, 1000));
        }
    }

    @Nested
    @DisplayName("filledSegments")
    class FilledSegments {

        @Test
        @DisplayName("proportional fill, rounded to the nearest segment")
        void proportional() {
            assertEquals(5, EnchantFormulas.filledSegments(50, 100, 10));
            assertEquals(1, EnchantFormulas.filledSegments(5, 100, 10));   // 0.5 rounds up
            assertEquals(8, EnchantFormulas.filledSegments(80, 100, 10));
        }

        @Test
        @DisplayName("full and beyond-full clamp to all segments")
        void clampedAtFull() {
            assertEquals(10, EnchantFormulas.filledSegments(100, 100, 10));
            assertEquals(10, EnchantFormulas.filledSegments(330, 100, 10));
        }

        @Test
        @DisplayName("zero or negative value renders empty")
        void emptyAtZero() {
            assertEquals(0, EnchantFormulas.filledSegments(0, 100, 10));
            assertEquals(0, EnchantFormulas.filledSegments(-5, 100, 10));
        }

        @Test
        @DisplayName("non-positive max renders empty (unresolved Magia skill)")
        void emptyOnBadMax() {
            assertEquals(0, EnchantFormulas.filledSegments(25, 0, 10));
            assertEquals(0, EnchantFormulas.filledSegments(25, -1, 10));
        }
    }

    @Nested
    @DisplayName("progressBar")
    class ProgressBar {

        @Test
        @DisplayName("splits the bar into a tagged filled run and a tagged empty run")
        void filledAndEmptyRuns() {
            assertEquals("<#AAA>■■■■■<#BBB>■■■■■",
                    EnchantFormulas.progressBar(50, 100, 10, "<#AAA>", "<#BBB>"));
        }

        @Test
        @DisplayName("a gradient filled tag is auto-closed before the empty run")
        void gradientIsClosed() {
            assertEquals("<gradient:#A:#B>■■■■■</gradient><#BBB>■■■■■",
                    EnchantFormulas.progressBar(50, 100, 10, "<gradient:#A:#B>", "<#BBB>"));
        }

        @Test
        @DisplayName("an empty bar has no filled tag at all")
        void emptyBar() {
            assertEquals("<#BBB>■■■■■■■■■■",
                    EnchantFormulas.progressBar(0, 100, 10, "<#AAA>", "<#BBB>"));
        }

        @Test
        @DisplayName("a full bar has no empty tag at all")
        void fullBar() {
            assertEquals("<#AAA>■■■■■■■■■■",
                    EnchantFormulas.progressBar(100, 100, 10, "<#AAA>", "<#BBB>"));
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
