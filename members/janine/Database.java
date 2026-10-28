package ph.uc.cpe314.dinesmart.data;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class Database {
    private static final String URL = "jdbc:sqlite:dinesmart.db";
    private Database() {}

    public static Connection connect() throws SQLException {
        Connection connection = DriverManager.getConnection(URL);
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
              api_meal_id TEXT
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS restaurant_orders (
              order_id TEXT PRIMARY KEY,
              ordered_at TEXT NOT NULL,
              status TEXT NOT NULL CHECK(status IN ('PENDING','PREPARING','COMPLETED','CANCELLED'))
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
            CREATE TABLE IF NOT EXISTS combos (
              combo_id INTEGER PRIMARY KEY AUTOINCREMENT,
              name TEXT NOT NULL UNIQUE
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
        }
    }
}
