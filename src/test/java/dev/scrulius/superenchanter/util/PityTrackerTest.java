/* SuperEnchanter - Author: Scrulius (GitHub) */
package dev.scrulius.superenchanter.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Unit tests for the pure streak codec in {@link PityTracker} (the PDC string form). */
class PityTrackerTest {

    @Test
    @DisplayName("null / blank input parses to an empty map")
    void emptyInput() {
        assertTrue(PityTracker.parse(null).isEmpty());
        assertTrue(PityTracker.parse("").isEmpty());
        assertTrue(PityTracker.parse("   ").isEmpty());
    }

    @Test
    @DisplayName("round-trips a multi-enchant streak map")
    void roundTrip() {
        final Map<String, Integer> in = new LinkedHashMap<>();
        in.put("minecraft:sharpness", 3);
        in.put("ecoenchants:filo_abisal", 1);
        final String serialized = PityTracker.serialize(in);
        assertEquals("minecraft:sharpness=3;ecoenchants:filo_abisal=1", serialized);
        assertEquals(in, PityTracker.parse(serialized));
    }

    @Test
    @DisplayName("malformed and non-positive entries are skipped")
    void malformedEntriesSkipped() {
        final Map<String, Integer> parsed =
                PityTracker.parse("minecraft:sharpness=2;garbage;=5;a=;b=zero;c=0;d=-1");
        assertEquals(Map.of("minecraft:sharpness", 2), parsed);
    }

    @Test
    @DisplayName("serialize drops non-positive streaks and empties to \"\"")
    void serializeDropsNonPositive() {
        final Map<String, Integer> in = new LinkedHashMap<>();
        in.put("minecraft:sharpness", 0);
        in.put("minecraft:unbreaking", -2);
        assertEquals("", PityTracker.serialize(in));
    }

    @Test
    @DisplayName("keys are lowercased on parse")
    void keysLowercased() {
        assertEquals(Map.of("minecraft:sharpness", 4),
                PityTracker.parse("Minecraft:Sharpness=4"));
    }
}
