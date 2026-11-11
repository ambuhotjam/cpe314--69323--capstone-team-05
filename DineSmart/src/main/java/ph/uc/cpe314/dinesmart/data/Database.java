package ph.uc.cpe314.dinesmart.data;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class Database {
    /** Test override keeps automated tests isolated from the restaurant database. */
    private static final String DEFAULT_URL = "jdbc:sqlite:dinesmart.db";
    private Database() {}

    public static Connection connect() throws SQLException {
        String url = System.getProperty("dinesmart.db.url", DEFAULT_URL);
        Connection connection = DriverManager.getConnection(url);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
        }
        return connection;
    }

    public static void initialize() throws SQLException {
        String[] ddl = {
            """
            CREATE TABLE IF NOT EXISTS menu_items (
              menu_item_id INTEGER PRIMARY KEY AUTOINCREMENT,
              name TEXT NOT NULL UNIQUE,
              item_type TEXT NOT NULL CHECK(item_type IN ('APPETIZER','MAIN_COURSE','BEVERAGE')),
              price REAL NOT NULL CHECK(price >= 0),
              base_prep_minutes INTEGER NOT NULL CHECK(base_prep_minutes >= 0),
              api_meal_id TEXT,
              image_path TEXT
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS restaurant_orders (
              order_id TEXT PRIMARY KEY,
              ordered_at TEXT NOT NULL,
              status TEXT NOT NULL CHECK(status IN ('PENDING','PREPARING','COMPLETED','CANCELLED')),
              payment_status TEXT NOT NULL DEFAULT 'UNPAID' CHECK(payment_status IN ('UNPAID','PAID')),
              paid_at TEXT,
              service_status TEXT NOT NULL DEFAULT 'AWAITING_SERVICE' CHECK(service_status IN ('AWAITING_SERVICE','SERVED')),
              served_at TEXT
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS order_items (
              order_item_id INTEGER PRIMARY KEY AUTOINCREMENT,
              order_id TEXT NOT NULL REFERENCES restaurant_orders(order_id),
              menu_item_id INTEGER NOT NULL REFERENCES menu_items(menu_item_id),
              quantity INTEGER NOT NULL CHECK(quantity > 0),
              unit_price_at_order REAL NOT NULL CHECK(unit_price_at_order >= 0)
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS order_combo_items (
              order_combo_item_id INTEGER PRIMARY KEY AUTOINCREMENT,
              order_id TEXT NOT NULL REFERENCES restaurant_orders(order_id),
              combo_id INTEGER NOT NULL REFERENCES combos(combo_id),
              quantity INTEGER NOT NULL CHECK(quantity > 0),
              unit_price_at_order REAL NOT NULL CHECK(unit_price_at_order >= 0)
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS ingredients (
              ingredient_id INTEGER PRIMARY KEY AUTOINCREMENT,
              name TEXT NOT NULL UNIQUE,
              stock_quantity REAL NOT NULL DEFAULT 0 CHECK(stock_quantity >= 0),
              stock_unit TEXT NOT NULL
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS menu_item_ingredients (
              menu_item_id INTEGER NOT NULL REFERENCES menu_items(menu_item_id),
              ingredient_id INTEGER NOT NULL REFERENCES ingredients(ingredient_id),
              quantity_required REAL NOT NULL CHECK(quantity_required > 0),
              measure TEXT NOT NULL DEFAULT '',
              PRIMARY KEY(menu_item_id, ingredient_id)
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS staff_users (
              staff_user_id INTEGER PRIMARY KEY AUTOINCREMENT,
              username TEXT NOT NULL UNIQUE,
              password_hash TEXT NOT NULL,
              password_salt TEXT NOT NULL,
              display_name TEXT NOT NULL,
              role TEXT NOT NULL CHECK(role IN ('ADMIN','SUPERVISOR','WAITER','CASHIER','KITCHEN')),
              active INTEGER NOT NULL DEFAULT 1
            )
            """,

            """
            CREATE TABLE IF NOT EXISTS menu_periods (
              period_id INTEGER PRIMARY KEY AUTOINCREMENT,
              menu_item_id INTEGER NOT NULL REFERENCES menu_items(menu_item_id) ON DELETE CASCADE,
              period_name TEXT NOT NULL CHECK(period_name IN ('BREAKFAST','LUNCH','DINNER','SEASONAL','SPECIAL')),
              starts_on TEXT NOT NULL,
              ends_on TEXT NOT NULL,
              active INTEGER NOT NULL DEFAULT 1,
              CHECK(date(ends_on) >= date(starts_on))
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS recipe_catalog (
              recipe_id INTEGER PRIMARY KEY AUTOINCREMENT,
              source TEXT NOT NULL CHECK(source IN ('THEMEALDB','ADMIN')),
              external_id TEXT,
              name TEXT NOT NULL COLLATE NOCASE UNIQUE,
              category TEXT NOT NULL DEFAULT 'MAIN_COURSE',
              area TEXT NOT NULL DEFAULT '',
              instructions TEXT NOT NULL DEFAULT '',
              thumbnail_url TEXT NOT NULL DEFAULT '',
              ingredients_text TEXT NOT NULL DEFAULT '',
              created_at TEXT NOT NULL DEFAULT (datetime('now','localtime'))
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS recipe_ingredients (
              recipe_id INTEGER NOT NULL REFERENCES recipe_catalog(recipe_id) ON DELETE CASCADE,
              ingredient_name TEXT NOT NULL,
              source_measure TEXT NOT NULL DEFAULT '',
              quantity REAL,
              unit TEXT,
              parse_status TEXT NOT NULL DEFAULT 'NEEDS_REVIEW' CHECK(parse_status IN ('READY','NEEDS_REVIEW')),
              PRIMARY KEY(recipe_id, ingredient_name)
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS table_reservations (
              reservation_id INTEGER PRIMARY KEY AUTOINCREMENT,
              guest_name TEXT NOT NULL,
              guest_phone TEXT NOT NULL DEFAULT '',
              party_size INTEGER NOT NULL CHECK(party_size > 0),
              reservation_at TEXT NOT NULL,
              table_label TEXT NOT NULL DEFAULT 'Unassigned',
              notes TEXT NOT NULL DEFAULT '',
              status TEXT NOT NULL DEFAULT 'BOOKED' CHECK(status IN ('BOOKED','SEATED','CANCELLED','COMPLETED')),
              created_at TEXT NOT NULL DEFAULT (datetime('now','localtime'))
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS menu_recommendations (
              recommendation_id INTEGER PRIMARY KEY AUTOINCREMENT,
              suggested_item TEXT NOT NULL,
              reason TEXT NOT NULL DEFAULT '',
              suggested_by TEXT NOT NULL,
              status TEXT NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','REVIEWED','APPROVED','DECLINED')),
              created_at TEXT NOT NULL DEFAULT (datetime('now','localtime'))
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS ingredient_movements (
              movement_id INTEGER PRIMARY KEY AUTOINCREMENT,
              ingredient_id INTEGER NOT NULL REFERENCES ingredients(ingredient_id),
              order_id TEXT,
              quantity_change REAL NOT NULL,
              reason TEXT NOT NULL,
              created_at TEXT NOT NULL DEFAULT (datetime('now','localtime'))
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS combos (
              combo_id INTEGER PRIMARY KEY AUTOINCREMENT,
              name TEXT NOT NULL UNIQUE,
              discount_percent REAL NOT NULL DEFAULT 0 CHECK(discount_percent >= 0 AND discount_percent < 100)
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS combo_components (
              component_id INTEGER PRIMARY KEY AUTOINCREMENT,
              combo_id INTEGER NOT NULL REFERENCES combos(combo_id),
              menu_item_id INTEGER REFERENCES menu_items(menu_item_id),
              child_combo_id INTEGER REFERENCES combos(combo_id),
              quantity INTEGER NOT NULL CHECK(quantity > 0),
              CHECK ((menu_item_id IS NOT NULL AND child_combo_id IS NULL)
                  OR (menu_item_id IS NULL AND child_combo_id IS NOT NULL))
            )
            """
        };
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            for (String sql : ddl) statement.execute(sql);
            boolean hasMeasure = false;
            try (var rs = statement.executeQuery("PRAGMA table_info(menu_item_ingredients)")) {
                while (rs.next()) if ("measure".equalsIgnoreCase(rs.getString("name"))) hasMeasure = true;
            }
            if (!hasMeasure) statement.execute("ALTER TABLE menu_item_ingredients ADD COLUMN measure TEXT NOT NULL DEFAULT ''");
            boolean hasDiscount = false;
            try (var rs = statement.executeQuery("PRAGMA table_info(combos)")) {
                while (rs.next()) if ("discount_percent".equalsIgnoreCase(rs.getString("name"))) hasDiscount = true;
            }
            if (!hasDiscount) statement.execute("ALTER TABLE combos ADD COLUMN discount_percent REAL NOT NULL DEFAULT 0 CHECK(discount_percent >= 0 AND discount_percent < 100)");
            boolean hasCustomerName = false;
            try (var rs = statement.executeQuery("PRAGMA table_info(restaurant_orders)")) {
                while (rs.next()) if ("customer_name".equalsIgnoreCase(rs.getString("name"))) hasCustomerName = true;
            }
            if (!hasCustomerName) statement.execute("ALTER TABLE restaurant_orders ADD COLUMN customer_name TEXT NOT NULL DEFAULT 'Walk-in Customer'");
            boolean hasPaymentStatus = false;
            boolean hasPaidAt = false;
            boolean hasServiceStatus = false;
            boolean hasServedAt = false;
            try (var rs = statement.executeQuery("PRAGMA table_info(restaurant_orders)")) {
                while (rs.next()) {
                    String column = rs.getString("name");
                    if ("payment_status".equalsIgnoreCase(column)) hasPaymentStatus = true;
                    if ("paid_at".equalsIgnoreCase(column)) hasPaidAt = true;
                    if ("service_status".equalsIgnoreCase(column)) hasServiceStatus = true;
                    if ("served_at".equalsIgnoreCase(column)) hasServedAt = true;
                }
            }
            if (!hasPaymentStatus) statement.execute("ALTER TABLE restaurant_orders ADD COLUMN payment_status TEXT NOT NULL DEFAULT 'UNPAID' CHECK(payment_status IN ('UNPAID','PAID'))");
            if (!hasPaidAt) statement.execute("ALTER TABLE restaurant_orders ADD COLUMN paid_at TEXT");
            if (!hasServiceStatus) {
                statement.execute("ALTER TABLE restaurant_orders ADD COLUMN service_status TEXT NOT NULL DEFAULT 'AWAITING_SERVICE' CHECK(service_status IN ('AWAITING_SERVICE','SERVED'))");
                // Before service tracking existed, COMPLETED meant the old workflow was finished.
                // Treat historical completed orders as already served so they do not flood the
                // waiter's new Ready to serve list after an upgrade.
                statement.execute("UPDATE restaurant_orders SET service_status='SERVED' WHERE status='COMPLETED'");
            }
            if (!hasServedAt) statement.execute("ALTER TABLE restaurant_orders ADD COLUMN served_at TEXT");
            boolean hasSoldOut = false;
            try (var rs = statement.executeQuery("PRAGMA table_info(menu_items)")) {
                while (rs.next()) if ("is_sold_out".equalsIgnoreCase(rs.getString("name"))) hasSoldOut = true;
            }
            if (!hasSoldOut) statement.execute("ALTER TABLE menu_items ADD COLUMN is_sold_out INTEGER NOT NULL DEFAULT 0");
            boolean hasDaily = false;
            try (var rs = statement.executeQuery("PRAGMA table_info(menu_items)")) {
                while (rs.next()) if ("available_today".equalsIgnoreCase(rs.getString("name"))) hasDaily = true;
            }
            if (!hasDaily) statement.execute("ALTER TABLE menu_items ADD COLUMN available_today INTEGER NOT NULL DEFAULT 1");
            boolean hasDailyDate = false;
            try (var rs = statement.executeQuery("PRAGMA table_info(menu_items)")) {
                while (rs.next()) if ("daily_menu_date".equalsIgnoreCase(rs.getString("name"))) hasDailyDate = true;
            }
            if (!hasDailyDate) statement.execute("ALTER TABLE menu_items ADD COLUMN daily_menu_date TEXT");
            boolean hasArchived = false;
            try (var rs = statement.executeQuery("PRAGMA table_info(menu_items)")) {
                while (rs.next()) if ("is_archived".equalsIgnoreCase(rs.getString("name"))) hasArchived = true;
            }
            if (!hasArchived) statement.execute("ALTER TABLE menu_items ADD COLUMN is_archived INTEGER NOT NULL DEFAULT 0");
            boolean hasImagePath = false;
            try (var rs = statement.executeQuery("PRAGMA table_info(menu_items)")) {
                while (rs.next()) if ("image_path".equalsIgnoreCase(rs.getString("name"))) hasImagePath = true;
            }
            if (!hasImagePath) statement.execute("ALTER TABLE menu_items ADD COLUMN image_path TEXT");
            boolean hasApiMealId = false;
            try (var rs = statement.executeQuery("PRAGMA table_info(menu_items)")) {
                while (rs.next()) if ("api_meal_id".equalsIgnoreCase(rs.getString("name"))) hasApiMealId = true;
            }
            if (!hasApiMealId) statement.execute("ALTER TABLE menu_items ADD COLUMN api_meal_id TEXT");
            statement.execute("UPDATE menu_items SET daily_menu_date=date('now','localtime') WHERE daily_menu_date IS NULL");
        }
    }
    /** Clears restaurant business data while preserving staff accounts and schema.
     * Call only after an explicit administrator confirmation.
     */
    public static void resetRestaurantData() throws SQLException {
        String[] tables = {
            "order_combo_items", "order_items", "ingredient_movements",
            "menu_item_ingredients", "combo_components", "menu_periods",
            "recipe_ingredients", "restaurant_orders", "table_reservations",
            "combos", "recipe_catalog", "ingredients", "menu_items"
        };
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                for (String table : tables) statement.executeUpdate("DELETE FROM " + table);
                statement.executeUpdate("DELETE FROM sqlite_sequence WHERE name IN (" +
                    "'order_items','menu_periods','recipe_catalog','table_reservations'," +
                    "'ingredient_movements','combo_components','ingredients','menu_items')");
                connection.commit();
            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

}
