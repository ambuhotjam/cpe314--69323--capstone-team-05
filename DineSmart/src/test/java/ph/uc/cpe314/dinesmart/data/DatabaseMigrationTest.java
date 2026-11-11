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
            statement.execute("""
                    CREATE TABLE order_items (
                        order_item_id INTEGER PRIMARY KEY AUTOINCREMENT,
                        order_id TEXT NOT NULL REFERENCES restaurant_orders(order_id),
                        menu_item_id INTEGER NOT NULL REFERENCES menu_items(menu_item_id),
                        quantity INTEGER NOT NULL,
                        unit_price_at_order REAL NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE combos (
                        combo_id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL UNIQUE,
                        discount_percent REAL NOT NULL DEFAULT 0
                    )
                    """);
            statement.execute("""
                    CREATE TABLE order_combo_items (
                        order_combo_item_id INTEGER PRIMARY KEY AUTOINCREMENT,
                        order_id TEXT NOT NULL REFERENCES restaurant_orders(order_id),
                        combo_id INTEGER NOT NULL REFERENCES combos(combo_id),
                        quantity INTEGER NOT NULL,
                        unit_price_at_order REAL NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE ingredients (
                        ingredient_id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL UNIQUE,
                        stock_quantity REAL NOT NULL DEFAULT 0,
                        stock_unit TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE ingredient_movements (
                        movement_id INTEGER PRIMARY KEY AUTOINCREMENT,
                        ingredient_id INTEGER NOT NULL REFERENCES ingredients(ingredient_id),
                        order_id TEXT,
                        quantity_change REAL NOT NULL,
                        reason TEXT NOT NULL,
                        created_at TEXT NOT NULL DEFAULT (datetime('now','localtime'))
                    )
                    """);
            statement.execute("INSERT INTO ingredients(name,stock_quantity,stock_unit) VALUES('Legacy Rice',10,'g')");
            statement.execute("INSERT INTO menu_items(name,item_type,price,base_prep_minutes) VALUES('Legacy Adobo','MAIN_COURSE',180,20)");
            statement.execute("INSERT INTO restaurant_orders(order_id,ordered_at,status) VALUES('LEGACY-DONE','2026-11-01 12:00','COMPLETED')");
            statement.execute("INSERT INTO restaurant_orders(order_id,ordered_at,status) VALUES('LEGACY-PENDING','2026-11-01 12:05','PENDING')");
            statement.execute("INSERT INTO order_items(order_id,menu_item_id,quantity,unit_price_at_order) VALUES('LEGACY-PENDING',1,1,12.34)");
            statement.execute("INSERT INTO combos(name,discount_percent) VALUES('Legacy Combo',12.5)");
            statement.execute("INSERT INTO order_combo_items(order_id,combo_id,quantity,unit_price_at_order) VALUES('LEGACY-PENDING',1,1,29.99)");
            statement.execute("INSERT INTO ingredient_movements(ingredient_id,order_id,quantity_change,reason) VALUES(1,'LEGACY-PENDING',-1,'ORDER_USAGE')");
            statement.execute("INSERT INTO ingredient_movements(ingredient_id,order_id,quantity_change,reason) VALUES(1,'MISSING-ORDER',-1,'OLD_ORPHAN')");
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
             ResultSet rs = statement.executeQuery(
                     "SELECT name,price,price_cents,image_path,api_meal_id FROM menu_items")) {
            assertTrue(rs.next());
            assertEquals("Legacy Adobo", rs.getString("name"));
            assertEquals(180d, rs.getDouble("price"), 0.001);
            assertEquals(18000L, rs.getLong("price_cents"));
            assertNull(rs.getString("image_path"));
            assertNull(rs.getString("api_meal_id"));
        }
        try (Connection migrated = Database.connect();
             Statement statement = migrated.createStatement();
             ResultSet rs = statement.executeQuery("""
                     SELECT unit_price_cents FROM order_items
                     WHERE order_id = 'LEGACY-PENDING'
                     """)) {
            assertTrue(rs.next());
            assertEquals(1234L, rs.getLong(1));
        }
        try (Connection migrated = Database.connect();
             Statement statement = migrated.createStatement();
             ResultSet rs = statement.executeQuery("""
                     SELECT o.unit_price_cents, c.discount_basis_points
                     FROM order_combo_items o
                     JOIN combos c ON c.combo_id = o.combo_id
                     WHERE o.order_id = 'LEGACY-PENDING'
                     """)) {
            assertTrue(rs.next());
            assertEquals(2999L, rs.getLong("unit_price_cents"));
            assertEquals(1250, rs.getInt("discount_basis_points"));
        }
        try (Connection migrated = Database.connect();
             Statement statement = migrated.createStatement();
             ResultSet rs = statement.executeQuery("""
                     SELECT order_id, reason
                     FROM ingredient_movements
                     ORDER BY movement_id
                     """)) {
            assertTrue(rs.next());
            assertEquals("LEGACY-PENDING", rs.getString("order_id"));
            assertEquals("ORDER_USAGE", rs.getString("reason"));
            assertTrue(rs.next());
            assertNull(rs.getString("order_id"),
                    "An old orphan order ID is nulled during the foreign-key migration.");
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
                    "order_combo_items", "ingredients", "staff_users", "recipe_catalog",
                    "recipe_ingredients", "table_reservations", "restaurant_tables",
                    "ingredient_movements", "menu_item_ingredients", "menu_periods",
                    "menu_recommendations", "combos", "combo_components")));
        }
        try (Connection c = Database.connect();
             Statement statement = c.createStatement();
             ResultSet rs = statement.executeQuery("PRAGMA foreign_key_list(ingredient_movements)")) {
            boolean hasOrderForeignKey = false;
            while (rs.next()) {
                if ("restaurant_orders".equalsIgnoreCase(rs.getString("table"))
                        && "order_id".equalsIgnoreCase(rs.getString("from"))) {
                    hasOrderForeignKey = true;
                }
            }
            assertTrue(hasOrderForeignKey,
                    "Ingredient movement order_id must reference restaurant_orders.");
        }
        try (Connection c = Database.connect();
             Statement statement = c.createStatement()) {
            statement.executeUpdate(
                    "INSERT INTO ingredients(name,stock_quantity,stock_unit) VALUES('FK Test Rice',1,'g')");
            assertThrows(java.sql.SQLException.class, () -> {
                try (PreparedStatement ps = c.prepareStatement("""
                        INSERT INTO ingredient_movements
                            (ingredient_id, order_id, quantity_change, reason)
                        VALUES (1, 'NO-SUCH-ORDER', -1, 'ORDER_USAGE')
                        """)) {
                    ps.executeUpdate();
                }
            });
        }
    }
}
