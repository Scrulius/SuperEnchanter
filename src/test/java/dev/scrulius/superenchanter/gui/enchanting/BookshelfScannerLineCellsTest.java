/* SuperEnchanter - Author: Scrulius (GitHub) */
package dev.scrulius.superenchanter.gui.enchanting;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the pure line-of-sight geometry in {@link BookshelfScanner#lineCells}.
 * No Bukkit/MockBukkit needed — the method is integer geometry only.
 */
class BookshelfScannerLineCellsTest {

    private static boolean contains(List<int[]> cells, int x, int y, int z) {
        return cells.stream().anyMatch(c -> c[0] == x && c[1] == y && c[2] == z);
    }

    @Test
    @DisplayName("adjacent candidate has no cells in between")
    void adjacentIsEmpty() {
        assertTrue(BookshelfScanner.lineCells(1, 0, 0).isEmpty());
        assertTrue(BookshelfScanner.lineCells(0, 1, 0).isEmpty());
        assertTrue(BookshelfScanner.lineCells(0, 0, 1).isEmpty());
        // diagonal corner-touch: max axis span 1 → still nothing strictly between
        assertTrue(BookshelfScanner.lineCells(1, 1, 1).isEmpty());
    }

    @Test
    @DisplayName("straight line of distance 2 crosses exactly the middle cell")
    void distanceTwoAxis() {
        final List<int[]> cells = BookshelfScanner.lineCells(2, 0, 0);
        assertEquals(1, cells.size());
        assertTrue(contains(cells, 1, 0, 0));
    }

    @Test
    @DisplayName("straight line of distance 3 crosses both intervening cells")
    void distanceThreeAxis() {
        final List<int[]> cells = BookshelfScanner.lineCells(3, 0, 0);
        assertEquals(2, cells.size());
        assertTrue(contains(cells, 1, 0, 0));
        assertTrue(contains(cells, 2, 0, 0));
    }

    @Test
    @DisplayName("pure diagonal crosses the single diagonal cell")
    void diagonal() {
        final List<int[]> cells = BookshelfScanner.lineCells(2, 2, 0);
        assertEquals(1, cells.size());
        assertTrue(contains(cells, 1, 1, 0));
    }

    @Test
    @DisplayName("vertical line is handled like any other axis")
    void vertical() {
        final List<int[]> cells = BookshelfScanner.lineCells(0, 0, 2);
        assertEquals(1, cells.size());
        assertTrue(contains(cells, 0, 0, 1));
    }

    @Test
    @DisplayName("endpoints (table and candidate) are never returned")
    void endpointsExcluded() {
        final List<int[]> cells = BookshelfScanner.lineCells(4, 0, 0);
        assertFalse(contains(cells, 0, 0, 0), "table cell must not be included");
        assertFalse(contains(cells, 4, 0, 0), "candidate cell must not be included");
    }

    @Test
    @DisplayName("negative offsets are symmetric to positive ones")
    void negativeSymmetry() {
        assertEquals(1, BookshelfScanner.lineCells(-2, 0, 0).size());
        assertTrue(contains(BookshelfScanner.lineCells(-2, 0, 0), -1, 0, 0));
    }

    @Test
    @DisplayName("all four ring corners cross only their own diagonal cell")
    void ringCornersSymmetric() {
        // Regression: Math.round rounds half UP (not away from zero), so the lines to
        // the (-x,+z) and (+x,-z) corners clipped through the NEIGHBOURING shelf cells
        // of the standard ring — two corners scanned fine and two were blocked.
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                final List<int[]> cells = BookshelfScanner.lineCells(2 * sx, 0, 2 * sz);
                assertEquals(1, cells.size(),
                        "corner (" + 2 * sx + ",0," + 2 * sz + ") must only cross its diagonal cell");
                assertTrue(contains(cells, sx, 0, sz));
            }
        }
    }

    @Test
    @DisplayName("mirroring any axis mirrors the crossed cells exactly")
    void fullMirrorSymmetry() {
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    final List<int[]> base = BookshelfScanner.lineCells(dx, dy, dz);
                    final List<int[]> mirX = BookshelfScanner.lineCells(-dx, dy, dz);
                    final List<int[]> mirZ = BookshelfScanner.lineCells(dx, dy, -dz);
                    final List<int[]> mirY = BookshelfScanner.lineCells(dx, -dy, dz);
                    assertEquals(base.size(), mirX.size(), "X mirror at " + dx + "," + dy + "," + dz);
                    assertEquals(base.size(), mirZ.size(), "Z mirror at " + dx + "," + dy + "," + dz);
                    assertEquals(base.size(), mirY.size(), "Y mirror at " + dx + "," + dy + "," + dz);
                    for (int[] c : base) {
                        assertTrue(contains(mirX, -c[0], c[1], c[2]),
                                "X-mirrored cell missing at " + dx + "," + dy + "," + dz);
                        assertTrue(contains(mirZ, c[0], c[1], -c[2]),
                                "Z-mirrored cell missing at " + dx + "," + dy + "," + dz);
                        assertTrue(contains(mirY, c[0], -c[1], c[2]),
                                "Y-mirrored cell missing at " + dx + "," + dy + "," + dz);
                    }
                }
            }
        }
    }
}
