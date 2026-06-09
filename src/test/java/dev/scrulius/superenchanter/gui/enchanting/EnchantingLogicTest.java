/* SuperEnchanter - Author: Scrulius (GitHub) */
package dev.scrulius.superenchanter.gui.enchanting;

import dev.scrulius.superenchanter.gui.enchanting.EnchantingLogic.BlockReason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pure unit tests for {@link EnchantingLogic#classifyBlock} — the Bukkit/EcoEnchants-free
 * precedence rule extracted from {@code analyze()}. These run without MockBukkit.
 */
@DisplayName("EnchantingLogic.classifyBlock")
class EnchantingLogicTest {

    @Nested
    @DisplayName("available")
    class Available {

        @Test
        @DisplayName("new enchant with no blockers is NONE")
        void newNoBlockers() {
            assertEquals(BlockReason.NONE,
                    EnchantingLogic.classifyBlock(0, 5, false, false, false));
        }

        @Test
        @DisplayName("owned below max (an upgrade) is always NONE, even with blockers present")
        void upgradeNeverBlocked() {
            // currentLevel > 0 and < max: upgrading is always allowed regardless of
            // missing/conflict/type-limit, which only gate brand-new enchants.
            assertEquals(BlockReason.NONE,
                    EnchantingLogic.classifyBlock(2, 5, true, true, true));
        }
    }

    @Nested
    @DisplayName("maxed")
    class Maxed {

        @Test
        @DisplayName("owned at max level is MAXED")
        void ownedAtMax() {
            assertEquals(BlockReason.MAXED,
                    EnchantingLogic.classifyBlock(5, 5, false, false, false));
        }

        @Test
        @DisplayName("owned above max (defensive) is MAXED")
        void ownedAboveMax() {
            assertEquals(BlockReason.MAXED,
                    EnchantingLogic.classifyBlock(6, 5, false, false, false));
        }

        @Test
        @DisplayName("single-level enchant already owned is MAXED")
        void singleLevelOwned() {
            assertEquals(BlockReason.MAXED,
                    EnchantingLogic.classifyBlock(1, 1, false, false, false));
        }

        @Test
        @DisplayName("MAXED wins over any new-enchant blocker flags")
        void maxedPrecedence() {
            assertEquals(BlockReason.MAXED,
                    EnchantingLogic.classifyBlock(5, 5, true, true, true));
        }
    }

    @Nested
    @DisplayName("new-enchant blockers (precedence: required > conflict > type-limit)")
    class Blockers {

        @Test
        @DisplayName("missing required beats conflict and type-limit")
        void missingRequiredFirst() {
            assertEquals(BlockReason.MISSING_REQUIRED,
                    EnchantingLogic.classifyBlock(0, 5, true, true, true));
        }

        @Test
        @DisplayName("conflict beats type-limit when nothing required is missing")
        void conflictBeatsTypeLimit() {
            assertEquals(BlockReason.CONFLICT,
                    EnchantingLogic.classifyBlock(0, 5, false, true, true));
        }

        @Test
        @DisplayName("type-limit only when no missing and no conflict")
        void typeLimitLast() {
            assertEquals(BlockReason.TYPE_LIMIT,
                    EnchantingLogic.classifyBlock(0, 5, false, false, true));
        }
    }
}
