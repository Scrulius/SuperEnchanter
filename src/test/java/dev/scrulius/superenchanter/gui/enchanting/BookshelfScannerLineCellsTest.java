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
}
