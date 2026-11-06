package ph.uc.cpe314.dinesmart;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ph.uc.cpe314.dinesmart.data.Database;
import ph.uc.cpe314.dinesmart.data.MenuItemRepository;
import ph.uc.cpe314.dinesmart.model.Appetizer;

class MenuItemRepositoryIntegrationTest {

    @TempDir
    Path temporaryDirectory;

    private String previousDatabaseUrl;
    private final MenuItemRepository repository = new MenuItemRepository();

    @BeforeEach
    void useIsolatedDatabase() throws Exception {
        previousDatabaseUrl = System.getProperty("dinesmart.db.url");
        Path databaseFile = temporaryDirectory.resolve("menu-item-test.db");
        System.setProperty("dinesmart.db.url", "jdbc:sqlite:" + databaseFile);
        Database.initialize();
    }

    @AfterEach
    void restoreDatabaseSetting() {
        if (previousDatabaseUrl == null) {
            System.clearProperty("dinesmart.db.url");
        } else {
            System.setProperty("dinesmart.db.url", previousDatabaseUrl);
        }
    }

    @Test
    void insertsMenuItemAndReadsItBackThroughJdbc() throws Exception {
        Appetizer item = new Appetizer("Integration Test Garlic Bread", 99.00, 8);

        long insertedId = repository.insert(item);

        assertTrue(insertedId > 0, "Insert should return a generated database ID.");
        try (Connection connection = Database.connect();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT menu_item_id, name, item_type, price, base_prep_minutes "
                             + "FROM menu_items WHERE menu_item_id = ?")) {
            statement.setLong(1, insertedId);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next(), "Inserted row should be selectable.");
                assertEquals("Integration Test Garlic Bread", result.getString("name"));
                assertEquals("APPETIZER", result.getString("item_type"));
                assertEquals(99.00, result.getDouble("price"), 0.001);
                assertEquals(8, result.getInt("base_prep_minutes"));
                assertFalse(result.next(), "The unique menu item should have one row.");
            }
        }
    }

    @Test
    void insertingSameNameUpdatesExistingMenuItemInsteadOfDuplicatingIt() throws Exception {
        long firstId = repository.insert(new Appetizer("Integration Test Toast", 50.00, 5));
        long secondId = repository.insert(new Appetizer("Integration Test Toast", 65.00, 6));

        assertEquals(firstId, secondId);
        assertTrue(repository.findAllPriceSummaries().stream()
                .anyMatch(summary -> summary.contains("Integration Test Toast")
                        && summary.contains("65.00")));
    }
}
