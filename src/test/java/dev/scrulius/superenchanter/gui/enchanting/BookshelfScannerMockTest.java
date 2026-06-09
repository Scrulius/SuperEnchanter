/* SuperEnchanter - Author: Scrulius (GitHub) */
package dev.scrulius.superenchanter.gui.enchanting;

import dev.scrulius.superenchanter.SuperEnchanterPlugin;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Integration tests for {@link BookshelfScanner#scan} on a MockBukkit world.
 * Bookshelves are placed face-adjacent to the table (Manhattan distance 1) so the
 * air-gap requirement is satisfied without arranging gaps. Exercises both the
 * normal power summation and the enchanted-library override (which round-trips
 * through the chunk PersistentDataContainer).
 */
class BookshelfScannerMockTest {

    private static ServerMock server;
    private static SuperEnchanterPlugin plugin;
    private static World world;

    private Block table;

    @BeforeAll
    static void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(SuperEnchanterPlugin.class);
        world = server.addSimpleWorld("bookshelf-test");
    }

    @AfterAll
    static void tearDown() {
        MockBukkit.unmock();
    }

    @BeforeEach
    void freshTable() {
        // A new table location each test so marks/blocks never leak between cases.
        int base = (int) (Math.random() * 1_000_000);
        table = world.getBlockAt(base, 100, base);
        table.setType(Material.ENCHANTING_TABLE);
    }

    private Block faceAdjacent(int index) {
        return switch (index) {
            case 0 -> table.getRelative(1, 0, 0);
            case 1 -> table.getRelative(-1, 0, 0);
            case 2 -> table.getRelative(0, 0, 1);
            case 3 -> table.getRelative(0, 0, -1);
            case 4 -> table.getRelative(0, 1, 0);
            default -> table.getRelative(0, -1, 0);
        };
    }

    @Test
    @DisplayName("each adjacent vanilla bookshelf contributes its configured power")
    void sumsVanillaBookshelves() {
        for (int i = 0; i < 4; i++) {
            faceAdjacent(i).setType(Material.BOOKSHELF);
        }
        BookshelfScanner.ScanResult r = BookshelfScanner.scan(table, plugin);
        assertEquals(4, r.total());
        assertEquals(4, r.vanilla());
        assertEquals(0, r.library());
    }

    @Test
    @DisplayName("no bookshelves means zero power")
    void noBookshelvesNoPower() {
        assertEquals(0, BookshelfScanner.scan(table, plugin).total());
    }

    @Test
    @DisplayName("a marked enchanted library adds its tier power on top, bucketed separately")
    void enchantedLibraryOverridesPower() {
        // Three plain shelves (1 each) + one enchanted library (config: 10).
        for (int i = 0; i < 3; i++) {
            faceAdjacent(i).setType(Material.BOOKSHELF);
        }
        Block library = faceAdjacent(3);
        library.setType(Material.CHISELED_BOOKSHELF);
        plugin.getEnchantedBookshelfManager().mark(library, "libreria_encantada");

        BookshelfScanner.ScanResult r = BookshelfScanner.scan(table, plugin);
        assertEquals(13, r.total(), "3 vanilla + 10 library");
        assertEquals(3, r.vanilla());
        assertEquals(10, r.library());
    }

    @Test
    @DisplayName("library power bypasses the vanilla soft-cap")
    void libraryPowerBypassesVanillaCap() {
        // Four libraries (4 × 10 = 40) exceed the vanilla cap (30), proving the cap
        // only constrains normal bookshelves — library power stacks on top.
        for (int i = 0; i < 4; i++) {
            Block library = faceAdjacent(i);
            library.setType(Material.CHISELED_BOOKSHELF);
            plugin.getEnchantedBookshelfManager().mark(library, "libreria_encantada");
        }

        BookshelfScanner.ScanResult r = BookshelfScanner.scan(table, plugin);
        assertEquals(40, r.total());
        assertEquals(0, r.vanilla());
        assertEquals(40, r.library());
        assertEquals(plugin.getPluginConfig().getMaxBookshelfPower(), r.max());
    }
}
