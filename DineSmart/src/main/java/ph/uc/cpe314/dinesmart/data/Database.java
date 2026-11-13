package ph.uc.cpe314.dinesmart.data;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Creates and safely upgrades the local SQLite schema. */
public final class Database {
    private static final String DEFAULT_URL = "jdbc:sqlite:dinesmart.db";

    private Database() {
    }

    /** Opens a connection with SQLite foreign-key enforcement enabled. */
    public static Connection connect() throws SQLException {
        String url = System.getProperty("dinesmart.db.url", DEFAULT_URL);
        Connection connection = DriverManager.getConnection(url);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
        }
        return connection;
    }

    /**
     * Creates the 13-table DineSmart schema and applies repeatable migrations.
     * Existing rows are preserved; legacy decimal money values are copied to
     * integer-cent columns before repositories switch to the exact values.
     * Historical bundled-order lines are converted to archived menu items
     * before obsolete bundle tables are removed.
     */
    public static void initialize() throws SQLException {
        try (Connection connection = connect();
             Statement statement = connection.createStatement()) {
            String[] ddl = {
                """
                CREATE TABLE IF NOT EXISTS restaurant_tables (
                    table_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    table_label TEXT NOT NULL COLLATE NOCASE UNIQUE,
                    capacity INTEGER NOT NULL DEFAULT 0 CHECK(capacity >= 0),
                    active INTEGER NOT NULL DEFAULT 1 CHECK(active IN (0,1))
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS staff_users (
                    staff_user_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    username TEXT NOT NULL UNIQUE,
                    password_hash TEXT NOT NULL,
                    password_salt TEXT NOT NULL,
                    display_name TEXT NOT NULL,
                    role TEXT NOT NULL CHECK(role IN
                        ('ADMIN','SUPERVISOR','WAITER','CASHIER','KITCHEN')),
                    active INTEGER NOT NULL DEFAULT 1
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS menu_items (
                    menu_item_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL UNIQUE,
                    item_type TEXT NOT NULL CHECK(item_type IN
                        ('APPETIZER','MAIN_COURSE','BEVERAGE')),
                    price REAL NOT NULL CHECK(price >= 0),
                    price_cents INTEGER NOT NULL DEFAULT 0 CHECK(price_cents >= 0),
                    base_prep_minutes INTEGER NOT NULL CHECK(base_prep_minutes >= 0),
                    api_meal_id TEXT,
                    image_path TEXT,
                    is_sold_out INTEGER NOT NULL DEFAULT 0,
                    available_today INTEGER NOT NULL DEFAULT 1,
                    daily_menu_date TEXT,
                    is_archived INTEGER NOT NULL DEFAULT 0
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS restaurant_orders (
                    order_id TEXT PRIMARY KEY,
                    ordered_at TEXT NOT NULL,
                    status TEXT NOT NULL CHECK(status IN
                        ('PENDING','PREPARING','COMPLETED','CANCELLED')),
                    payment_status TEXT NOT NULL DEFAULT 'UNPAID'
                        CHECK(payment_status IN ('UNPAID','PAID')),
                    paid_at TEXT,
                    service_status TEXT NOT NULL DEFAULT 'AWAITING_SERVICE'
                        CHECK(service_status IN ('AWAITING_SERVICE','SERVED')),
                    served_at TEXT,
                    customer_name TEXT NOT NULL DEFAULT 'Walk-in Customer',
                    table_id INTEGER REFERENCES restaurant_tables(table_id)
                        ON DELETE SET NULL,
                    created_by INTEGER REFERENCES staff_users(staff_user_id)
                        ON DELETE SET NULL
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS order_items (
                    order_item_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    order_id TEXT NOT NULL REFERENCES restaurant_orders(order_id),
                    menu_item_id INTEGER NOT NULL REFERENCES menu_items(menu_item_id),
                    quantity INTEGER NOT NULL CHECK(quantity > 0),
                    unit_price_at_order REAL NOT NULL CHECK(unit_price_at_order >= 0),
                    unit_price_cents INTEGER NOT NULL DEFAULT 0
                        CHECK(unit_price_cents >= 0)
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
                CREATE TABLE IF NOT EXISTS menu_periods (
                    period_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    menu_item_id INTEGER NOT NULL REFERENCES menu_items(menu_item_id)
                        ON DELETE CASCADE,
                    period_name TEXT NOT NULL CHECK(period_name IN
                        ('BREAKFAST','LUNCH','DINNER','SEASONAL','SPECIAL')),
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
                    recipe_id INTEGER NOT NULL REFERENCES recipe_catalog(recipe_id)
                        ON DELETE CASCADE,
                    ingredient_name TEXT NOT NULL,
                    source_measure TEXT NOT NULL DEFAULT '',
                    quantity REAL,
                    unit TEXT,
                    parse_status TEXT NOT NULL DEFAULT 'NEEDS_REVIEW'
                        CHECK(parse_status IN ('READY','NEEDS_REVIEW')),
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
                    table_id INTEGER REFERENCES restaurant_tables(table_id)
                        ON DELETE SET NULL,
                    notes TEXT NOT NULL DEFAULT '',
                    status TEXT NOT NULL DEFAULT 'BOOKED'
                        CHECK(status IN ('BOOKED','SEATED','CANCELLED','COMPLETED')),
                    created_at TEXT NOT NULL DEFAULT (datetime('now','localtime'))
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS menu_recommendations (
                    recommendation_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    suggested_item TEXT NOT NULL,
                    reason TEXT NOT NULL DEFAULT '',
                    suggested_by TEXT NOT NULL,
                    status TEXT NOT NULL DEFAULT 'PENDING'
                        CHECK(status IN ('PENDING','REVIEWED','APPROVED','DECLINED')),
                    created_at TEXT NOT NULL DEFAULT (datetime('now','localtime'))
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS ingredient_movements (
                    movement_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    ingredient_id INTEGER NOT NULL REFERENCES ingredients(ingredient_id),
                    order_id TEXT REFERENCES restaurant_orders(order_id)
                        ON DELETE SET NULL,
                    quantity_change REAL NOT NULL,
                    reason TEXT NOT NULL,
                    created_at TEXT NOT NULL DEFAULT (datetime('now','localtime'))
                )
                """
            };

            for (String sql : ddl) {
                statement.execute(sql);
            }

            migrateLegacyColumns(connection, statement);
            migrateLegacyBundleTables(connection, statement);
            migrateIngredientMovementOrderForeignKey(connection, statement);
            backfillRestaurantTableLinks(connection, statement);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_orders_created_by_ordered_at
                    ON restaurant_orders(created_by, ordered_at)
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_reservations_table_time
                    ON table_reservations(table_id, reservation_at)
                    """);
            statement.execute("""
                    CREATE UNIQUE INDEX IF NOT EXISTS idx_unique_active_table_reservation
                    ON table_reservations(table_id, reservation_at)
                    WHERE table_id IS NOT NULL
                      AND status IN ('BOOKED','SEATED')
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_movements_order_reason
                    ON ingredient_movements(order_id, reason)
                    """);
            statement.execute("""
                    UPDATE menu_items
                    SET daily_menu_date = date('now','localtime')
                    WHERE daily_menu_date IS NULL
                    """);
        }
    }

    private static void migrateLegacyColumns(Connection connection, Statement statement)
            throws SQLException {
        if (addColumnIfMissing(connection, statement, "menu_items", "price_cents",
                "INTEGER NOT NULL DEFAULT 0 CHECK(price_cents >= 0)")) {
            statement.execute("""
                    UPDATE menu_items
                    SET price_cents = CAST(ROUND(COALESCE(price, 0) * 100) AS INTEGER)
                    """);
        }

        if (addColumnIfMissing(connection, statement, "order_items", "unit_price_cents",
                "INTEGER NOT NULL DEFAULT 0 CHECK(unit_price_cents >= 0)")) {
            statement.execute("""
                    UPDATE order_items
                    SET unit_price_cents =
                        CAST(ROUND(COALESCE(unit_price_at_order, 0) * 100) AS INTEGER)
                    """);
        }

        addColumnIfMissing(connection, statement, "menu_item_ingredients", "measure",
                "TEXT NOT NULL DEFAULT ''");
        addColumnIfMissing(connection, statement, "restaurant_orders", "customer_name",
                "TEXT NOT NULL DEFAULT 'Walk-in Customer'");
        addColumnIfMissing(connection, statement, "restaurant_orders", "payment_status",
                "TEXT NOT NULL DEFAULT 'UNPAID' CHECK(payment_status IN ('UNPAID','PAID'))");
        addColumnIfMissing(connection, statement, "restaurant_orders", "paid_at", "TEXT");
        boolean addedServiceStatus = addColumnIfMissing(connection, statement,
                "restaurant_orders", "service_status",
                "TEXT NOT NULL DEFAULT 'AWAITING_SERVICE' CHECK(service_status IN ('AWAITING_SERVICE','SERVED'))");
        if (addedServiceStatus) {
            statement.execute("""
                    UPDATE restaurant_orders
                    SET service_status = 'SERVED'
                    WHERE status = 'COMPLETED'
                    """);
        }
        addColumnIfMissing(connection, statement, "restaurant_orders", "served_at", "TEXT");
        addColumnIfMissing(connection, statement, "restaurant_orders", "table_id",
                "INTEGER REFERENCES restaurant_tables(table_id) ON DELETE SET NULL");
        addColumnIfMissing(connection, statement, "restaurant_orders", "created_by",
                "INTEGER REFERENCES staff_users(staff_user_id) ON DELETE SET NULL");
        addColumnIfMissing(connection, statement, "table_reservations", "table_id",
                "INTEGER REFERENCES restaurant_tables(table_id) ON DELETE SET NULL");

        addColumnIfMissing(connection, statement, "menu_items", "is_sold_out",
                "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing(connection, statement, "menu_items", "available_today",
                "INTEGER NOT NULL DEFAULT 1");
        addColumnIfMissing(connection, statement, "menu_items", "daily_menu_date", "TEXT");
        addColumnIfMissing(connection, statement, "menu_items", "is_archived",
                "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing(connection, statement, "menu_items", "image_path", "TEXT");
        addColumnIfMissing(connection, statement, "menu_items", "api_meal_id", "TEXT");
    }


    /**
     * Removes the retired bundled-meal schema. If an older database contains
     * historical bundle order lines, they are preserved as archived menu items
     * and ordinary order_items before the legacy tables are dropped.
     *
     * The savepoint makes the conversion and table removal atomic and safe to
     * retry if a migration fails partway through.
     */
    private static void migrateLegacyBundleTables(Connection connection,
                                                  Statement statement)
            throws SQLException {
        boolean hasOrderLines = tableExists(connection, "order_combo_items");
        boolean hasBundles = tableExists(connection, "combos");
        boolean hasComponents = tableExists(connection, "combo_components");
        if (!hasOrderLines && !hasBundles && !hasComponents) {
            return;
        }

        statement.execute("SAVEPOINT remove_legacy_bundle_tables");
        try {
            if (hasOrderLines) {
                String priceExpression = hasColumn(connection, "order_combo_items",
                        "unit_price_at_order")
                        ? "COALESCE(o.unit_price_at_order, 0)"
                        : "COALESCE(o.unit_price_cents, 0) / 100.0";
                String bundleNameExpression = hasBundles
                        ? "COALESCE(c.name, 'Unnamed bundle')"
                        : "'Unnamed bundle'";
                String join = hasBundles
                        ? " LEFT JOIN combos c ON c.combo_id = o.combo_id "
                        : " ";
                List<LegacyBundleOrderLine> legacyLines = new ArrayList<>();
                String query = """
                        SELECT o.order_combo_item_id, o.order_id, o.combo_id,
                               o.quantity, %s AS unit_price, %s AS bundle_name
                        FROM order_combo_items o
                        %s
                        ORDER BY o.order_combo_item_id
                        """.formatted(priceExpression, bundleNameExpression, join);
                try (Statement read = connection.createStatement();
                     ResultSet rs = read.executeQuery(query)) {
                    while (rs.next()) {
                        legacyLines.add(new LegacyBundleOrderLine(
                                rs.getLong("order_combo_item_id"),
                                rs.getString("order_id"),
                                rs.getLong("combo_id"),
                                rs.getInt("quantity"),
                                rs.getDouble("unit_price"),
                                rs.getString("bundle_name")));
                    }
                }

                try (PreparedStatement insertArchivedMenuItem = connection.prepareStatement("""
                        INSERT OR IGNORE INTO menu_items
                            (name, item_type, price, price_cents, base_prep_minutes,
                             is_sold_out, available_today, daily_menu_date, is_archived)
                        VALUES (?, 'MAIN_COURSE', ?, ?, 0, 1, 0,
                                date('now','localtime'), 1)
                        """);
                     PreparedStatement findMenuItem = connection.prepareStatement(
                             "SELECT menu_item_id FROM menu_items WHERE name=?");
                     PreparedStatement archiveMenuItem = connection.prepareStatement("""
                             UPDATE menu_items
                             SET is_archived=1, available_today=0, is_sold_out=1
                             WHERE name=?
                             """);
                     PreparedStatement insertOrderItem = connection.prepareStatement("""
                        INSERT INTO order_items
                            (order_id, menu_item_id, quantity,
                             unit_price_at_order, unit_price_cents)
                        VALUES (?, ?, ?, ?, ?)
                        """)) {
                    for (LegacyBundleOrderLine line : legacyLines) {
                        if (!Double.isFinite(line.unitPrice()) || line.unitPrice() < 0
                                || line.quantity() <= 0) {
                            throw new SQLException("Invalid historical bundled-order line "
                                    + line.legacyLineId() + "; migration was cancelled.");
                        }
                        String archivedName = "Archived bundle #" + line.bundleId()
                                + ": " + line.bundleName();
                        long cents = Math.round(line.unitPrice() * 100.0);

                        insertArchivedMenuItem.setString(1, archivedName);
                        insertArchivedMenuItem.setDouble(2, line.unitPrice());
                        insertArchivedMenuItem.setLong(3, cents);
                        insertArchivedMenuItem.executeUpdate();

                        archiveMenuItem.setString(1, archivedName);
                        archiveMenuItem.executeUpdate();

                        findMenuItem.setString(1, archivedName);
                        long menuItemId;
                        try (ResultSet rs = findMenuItem.executeQuery()) {
                            if (!rs.next()) {
                                throw new SQLException("Could not preserve historical bundle line "
                                        + line.legacyLineId() + ".");
                            }
                            menuItemId = rs.getLong(1);
                        }

                        insertOrderItem.setString(1, line.orderId());
                        insertOrderItem.setLong(2, menuItemId);
                        insertOrderItem.setInt(3, line.quantity());
                        insertOrderItem.setDouble(4, line.unitPrice());
                        insertOrderItem.setLong(5, cents);
                        insertOrderItem.executeUpdate();
                    }
                }
            }

            // Drop dependants first so existing SQLite foreign keys do not block removal.
            statement.execute("DROP TABLE IF EXISTS order_combo_items");
            statement.execute("DROP TABLE IF EXISTS combo_components");
            statement.execute("DROP TABLE IF EXISTS combos");
            statement.execute("RELEASE SAVEPOINT remove_legacy_bundle_tables");
        } catch (SQLException | RuntimeException ex) {
            try {
                statement.execute("ROLLBACK TO SAVEPOINT remove_legacy_bundle_tables");
                statement.execute("RELEASE SAVEPOINT remove_legacy_bundle_tables");
            } catch (SQLException rollbackFailure) {
                ex.addSuppressed(rollbackFailure);
            }
            throw ex;
        }
    }

    private record LegacyBundleOrderLine(long legacyLineId, String orderId,
                                         long bundleId, int quantity,
                                         double unitPrice, String bundleName) {
    }

    private static boolean tableExists(Connection connection, String table)
            throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT 1 FROM sqlite_master
                WHERE type='table' AND name=?
                """)) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static boolean addColumnIfMissing(Connection connection, Statement statement,
                                              String table, String column, String definition)
            throws SQLException {
        if (hasColumn(connection, table, column)) {
            return false;
        }
        statement.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        return true;
    }

    private static boolean hasColumn(Connection connection, String table, String column)
            throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasForeignKey(Connection connection, String table,
                                         String targetTable, String fromColumn)
            throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("PRAGMA foreign_key_list(" + table + ")")) {
            while (rs.next()) {
                if (targetTable.equalsIgnoreCase(rs.getString("table"))
                        && fromColumn.equalsIgnoreCase(rs.getString("from"))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Rebuilds the old ledger table because SQLite cannot add a foreign-key
     * constraint to an existing column with ALTER COLUMN.
     */
    private static void migrateIngredientMovementOrderForeignKey(
            Connection connection, Statement statement) throws SQLException {
        if (hasForeignKey(connection, "ingredient_movements",
                "restaurant_orders", "order_id")) {
            return;
        }

        statement.execute("DROP TABLE IF EXISTS ingredient_movements_new");
        statement.execute("""
                CREATE TABLE ingredient_movements_new (
                    movement_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    ingredient_id INTEGER NOT NULL REFERENCES ingredients(ingredient_id),
                    order_id TEXT REFERENCES restaurant_orders(order_id)
                        ON DELETE SET NULL,
                    quantity_change REAL NOT NULL,
                    reason TEXT NOT NULL,
                    created_at TEXT NOT NULL DEFAULT (datetime('now','localtime'))
                )
                """);
        statement.execute("""
                INSERT INTO ingredient_movements_new
                    (movement_id, ingredient_id, order_id, quantity_change, reason, created_at)
                SELECT movement_id, ingredient_id,
                       CASE
                         WHEN order_id IS NOT NULL AND EXISTS (
                             SELECT 1 FROM restaurant_orders o
                             WHERE o.order_id = ingredient_movements.order_id
                         ) THEN order_id
                         ELSE NULL
                       END,
                       quantity_change, reason, created_at
                FROM ingredient_movements
                """);
        statement.execute("DROP TABLE ingredient_movements");
        statement.execute("""
                ALTER TABLE ingredient_movements_new
                RENAME TO ingredient_movements
                """);
    }

    private static void backfillRestaurantTableLinks(Connection connection,
                                                      Statement statement)
            throws SQLException {
        statement.execute("""
                INSERT OR IGNORE INTO restaurant_tables(table_label)
                SELECT DISTINCT TRIM(table_label)
                FROM table_reservations
                WHERE table_label IS NOT NULL
                  AND TRIM(table_label) <> ''
                  AND LOWER(TRIM(table_label)) <> 'unassigned'
                """);
        statement.execute("""
                UPDATE table_reservations
                SET table_id = (
                    SELECT t.table_id
                    FROM restaurant_tables t
                    WHERE t.table_label = table_reservations.table_label COLLATE NOCASE
                )
                WHERE table_id IS NULL
                  AND LOWER(TRIM(table_label)) <> 'unassigned'
                """);
    }

    /** Clears restaurant transactions while preserving staff accounts and table definitions. */
    public static void resetRestaurantData() throws SQLException {
        String[] tables = {
            "order_items", "ingredient_movements", "menu_item_ingredients",
            "menu_periods", "recipe_ingredients", "restaurant_orders",
            "table_reservations", "menu_recommendations", "recipe_catalog",
            "ingredients", "menu_items"
        };
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                for (String table : tables) {
                    statement.executeUpdate("DELETE FROM " + table);
                }
                statement.executeUpdate("""
                        DELETE FROM sqlite_sequence
                        WHERE name IN (
                            'order_items','menu_periods','recipe_catalog',
                            'table_reservations','ingredient_movements',
                            'ingredients','menu_items','menu_recommendations'
                        )
                        """);
                connection.commit();
            } catch (SQLException ex) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    ex.addSuppressed(rollbackFailure);
                }
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }
}
