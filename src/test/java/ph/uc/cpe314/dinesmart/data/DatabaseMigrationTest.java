package ph.uc.cpe314.dinesmart.data;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Uses a disposable legacy database to verify safe schema upgrades. */
class DatabaseMigrationTest {
    @TempDir Path tempDir;
    private String previousUrl;

    @AfterEach void restoreDatabaseOverride() {
        if (previousUrl == null) System.clearProperty("dinesmart.db.url");
        else System.setProperty("dinesmart.db.url", previousUrl);
    }

    @Test void legacyDatabaseMigrationAddsPaymentServiceAndPhotoColumnsWithoutLosingRows() throws Exception {
        previousUrl = System.getProperty("dinesmart.db.url");
        Path databaseFile = tempDir.resolve("legacy-dinesmart.db");
        String url = "jdbc:sqlite:" + databaseFile;
        try (Connection legacy = DriverManager.getConnection(url); Statement statement = legacy.createStatement()) {
            statement.execute("""
                    CREATE TABLE menu_items (
                        menu_item_id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL UNIQUE,
                        item_type TEXT NOT NULL,
                        price REAL NOT NULL,
                        base_prep_minutes INTEGER NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE restaurant_orders (
                        order_id TEXT PRIMARY KEY,
                        ordered_at TEXT NOT NULL,
                        status TEXT NOT NULL
                    )
                    """);
            statement.execute("INSERT INTO menu_items(name,item_type,price,base_prep_minutes) VALUES('Legacy Adobo','MAIN_COURSE',180,20)");
            statement.execute("INSERT INTO restaurant_orders(order_id,ordered_at,status) VALUES('LEGACY-DONE','2026-11-01 12:00','COMPLETED')");
            statement.execute("INSERT INTO restaurant_orders(order_id,ordered_at,status) VALUES('LEGACY-PENDING','2026-11-01 12:05','PENDING')");
        }

        System.setProperty("dinesmart.db.url", url);
        Database.initialize();
        Database.initialize(); // migration must be repeatable

        try (Connection migrated = Database.connect();
             PreparedStatement ps = migrated.prepareStatement("""
                     SELECT order_id,customer_name,payment_status,service_status
                     FROM restaurant_orders ORDER BY order_id
                     """); ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next());
            assertEquals("LEGACY-DONE", rs.getString("order_id"));
            assertEquals("Walk-in Customer", rs.getString("customer_name"));
            assertEquals("UNPAID", rs.getString("payment_status"));
            assertEquals("SERVED", rs.getString("service_status"),
                    "Old COMPLETED orders were already finished in the previous workflow.");

            assertTrue(rs.next());
            assertEquals("LEGACY-PENDING", rs.getString("order_id"));
            assertEquals("UNPAID", rs.getString("payment_status"));
            assertEquals("AWAITING_SERVICE", rs.getString("service_status"));
            assertFalse(rs.next());
        }

        try (Connection migrated = Database.connect(); Statement statement = migrated.createStatement();
             ResultSet rs = statement.executeQuery("SELECT name,price,image_path,api_meal_id FROM menu_items")) {
            assertTrue(rs.next());
            assertEquals("Legacy Adobo", rs.getString("name"));
            assertEquals(180d, rs.getDouble("price"), 0.001);
            assertNull(rs.getString("image_path"));
            assertNull(rs.getString("api_meal_id"));
        }
    }

    @Test void initializedSchemaContainsRequiredTablesAndRoleConstraints() throws Exception {
        previousUrl = System.getProperty("dinesmart.db.url");
        System.setProperty("dinesmart.db.url", "jdbc:sqlite:" + tempDir.resolve("fresh-dinesmart.db"));
        Database.initialize();
        try (Connection c = Database.connect(); Statement statement = c.createStatement();
             ResultSet rs = statement.executeQuery("SELECT name FROM sqlite_master WHERE type='table'")) {
            Set<String> tables = new HashSet<>();
            while (rs.next()) tables.add(rs.getString(1));
            assertTrue(tables.containsAll(Set.of("menu_items", "restaurant_orders", "order_items",
                    "ingredients", "staff_users", "recipe_catalog", "recipe_ingredients",
                    "table_reservations", "ingredient_movements")));
        }
    }
}
