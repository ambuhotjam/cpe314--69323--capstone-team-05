package ph.uc.cpe314.dinesmart.data;
import ph.uc.cpe314.dinesmart.model.MenuItem;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import ph.uc.cpe314.dinesmart.util.Money;

public final class MenuItemRepository {

    /** A saved menu entry with its local photo path for the visual menu catalog. */
    public record MenuCard(long id, String name, String itemType, long priceCents, String imagePath, String apiMealId) { }

    public long insert(MenuItem item) throws SQLException {
        return insert(item, null);
    }

    /** Saves or updates a menu item and, when supplied, its local food-photo path. */
    public long insert(MenuItem item, String imagePath) throws SQLException {
        return insert(item, imagePath, null);
    }

    /** Saves a menu item with an optional local image and TheMealDB meal ID. */
    public long insert(MenuItem item, String imagePath, String apiMealId) throws SQLException {
        if (item == null) {
            throw new IllegalArgumentException("Menu item is required.");
        }

        String name = item.getName();
        if (name == null || name.isBlank()
                || "NULL".equalsIgnoreCase(name.trim())) {
            throw new IllegalArgumentException(
                    "Menu item name cannot be empty or NULL.");
        }

        if (item.getPriceCents() < 0) {
            throw new IllegalArgumentException(
                    "Menu item price must be a valid non-negative number.");
        }

        if (item.getBasePrepMinutes() < 0) {
            throw new IllegalArgumentException(
                    "Preparation time cannot be negative.");
        }

        String sql = """
            INSERT INTO menu_items
                (name, item_type, price, price_cents, base_prep_minutes, daily_menu_date, available_today, image_path, api_meal_id)
            VALUES (?, ?, CAST(? AS REAL), ?, ?, date('now','localtime'), 0, ?, ?)
            ON CONFLICT(name) DO UPDATE SET
                item_type = excluded.item_type,
                price = excluded.price,
                price_cents = excluded.price_cents,
                base_prep_minutes = excluded.base_prep_minutes,
                image_path = COALESCE(excluded.image_path, menu_items.image_path),
                api_meal_id = COALESCE(excluded.api_meal_id, menu_items.api_meal_id),
                is_archived = 0
            """;

        try (Connection connection = Database.connect();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, item.getName());
            statement.setString(2, item.getItemType());
            statement.setString(3, Money.toDatabaseDecimal(item.getPriceCents()));
            statement.setLong(4, item.getPriceCents());
            statement.setInt(5, item.getBasePrepMinutes());
            statement.setString(6, imagePath);
            statement.setString(7, apiMealId);
            statement.executeUpdate();

            try (PreparedStatement select =
                         connection.prepareStatement(
                                 "SELECT menu_item_id FROM menu_items WHERE name = ?")) {

                select.setString(1, item.getName());

                try (ResultSet rs = select.executeQuery()) {
                    if (rs.next()) {
                        return rs.getLong("menu_item_id");
                    }
                }
            }
        }

        throw new SQLException(
                "Could not retrieve the saved menu item ID."
        );
    }

    /** Returns active menu entries for the photo gallery in Menu Management. */
    public List<MenuCard> findMenuCards() throws SQLException {
        List<MenuCard> cards = new ArrayList<>();
        String sql = """
            SELECT menu_item_id, name, item_type, price_cents, image_path, api_meal_id
            FROM menu_items
            WHERE COALESCE(is_archived,0)=0
              AND name IS NOT NULL
              AND TRIM(name) <> ''
              AND UPPER(TRIM(name)) <> 'NULL'
            ORDER BY item_type, name
            """;
        try (Connection connection = Database.connect();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                cards.add(new MenuCard(
                        rs.getLong("menu_item_id"),
                        rs.getString("name"),
                        rs.getString("item_type"),
                        rs.getLong("price_cents"),
                        rs.getString("image_path"),
                        rs.getString("api_meal_id")));
            }
        }
        return cards;
    }


    /**
     * Attaches a verified TheMealDB photo to a saved menu item. It never replaces
     * a photo that a staff member has already selected manually.
     */
    public boolean updateMealDbPhoto(long menuItemId, String imagePath, String apiMealId,
                                     String expectedCurrentPath) throws SQLException {
        if (menuItemId <= 0 || imagePath == null || imagePath.isBlank()) {
            throw new IllegalArgumentException("A saved menu item and local photo path are required.");
        }
        String sql = "UPDATE menu_items SET image_path=?, api_meal_id=COALESCE(?,api_meal_id) "
                + "WHERE menu_item_id=? AND COALESCE(is_archived,0)=0 "
                + "AND (image_path IS NULL OR TRIM(image_path)='' OR image_path=?)";
        try (Connection connection = Database.connect(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, imagePath);
            statement.setString(2, apiMealId);
            statement.setLong(3, menuItemId);
            statement.setString(4, expectedCurrentPath);
            return statement.executeUpdate() == 1;
        }
    }

    public List<String> findByTypeSummaries(String itemType) throws SQLException {
        if (itemType == null
                || !List.of("APPETIZER", "MAIN_COURSE", "BEVERAGE").contains(itemType)) {
            throw new IllegalArgumentException("Unknown menu category.");
        }
        List<String> items = new ArrayList<>();
        String sql = "SELECT menu_item_id, name, item_type, price_cents FROM menu_items WHERE item_type = ? AND COALESCE(is_archived,0)=0 AND name IS NOT NULL AND TRIM(name) <> '' AND UPPER(TRIM(name)) <> 'NULL' ORDER BY name";
        try (Connection connection = Database.connect(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, itemType);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) { items.add(rs.getString("name") + " | " + rs.getString("item_type") + " | Price: ₱" + Money.formatCents(rs.getLong("price_cents"))); }
            }
        }
        return items;
    }

    public List<String> findAllSummaries() throws SQLException {
        List<String> items = new ArrayList<>();

        String sql = """
            SELECT menu_item_id, name, item_type, price_cents, is_sold_out, available_today, daily_menu_date,
                   CASE WHEN EXISTS (SELECT 1 FROM menu_periods mp WHERE mp.menu_item_id=menu_items.menu_item_id AND mp.active=1
                                     AND date('now','localtime') BETWEEN date(mp.starts_on) AND date(mp.ends_on))
                        THEN 1
                        WHEN NOT EXISTS (SELECT 1 FROM menu_periods mp WHERE mp.menu_item_id=menu_items.menu_item_id AND mp.active=1)
                             AND available_today=1 AND daily_menu_date=date('now','localtime')
                        THEN 1 ELSE 0 END AS is_on_today
            FROM menu_items
            WHERE COALESCE(is_archived,0)=0
              AND name IS NOT NULL
              AND TRIM(name) <> ''
              AND UPPER(TRIM(name)) <> 'NULL'
            ORDER BY menu_item_id
            """;

        try (Connection connection = Database.connect();
             PreparedStatement statement =
                     connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {

            while (rs.next()) {
                items.add(
                        "#" + rs.getLong("menu_item_id")
                        + " " + rs.getString("name")
                        + " [" + rs.getString("item_type") + "] ₱"
                        + Money.formatCents(rs.getLong("price_cents"))
                        + (rs.getInt("is_sold_out") == 1 ? "  • SOLD OUT" : "")
                        + (rs.getInt("is_on_today") == 0 ? "  • NOT ON TODAY'S MENU" : "")
                );
            }
        }

        return items;
    }

    public List<String> findAvailableByType(String itemType) throws SQLException {
        if (itemType == null || !List.of("APPETIZER", "MAIN_COURSE", "BEVERAGE").contains(itemType)) { throw new IllegalArgumentException("Unknown menu category."); }
        List<String> items = new ArrayList<>();
        String sql = "SELECT name,item_type,price_cents FROM menu_items WHERE item_type=? AND COALESCE(is_archived,0)=0 AND is_sold_out=0 AND (EXISTS (SELECT 1 FROM menu_periods mp WHERE mp.menu_item_id=menu_items.menu_item_id AND mp.active=1 AND date('now','localtime') BETWEEN date(mp.starts_on) AND date(mp.ends_on)) OR (NOT EXISTS (SELECT 1 FROM menu_periods mp WHERE mp.menu_item_id=menu_items.menu_item_id AND mp.active=1) AND available_today=1 AND daily_menu_date=date('now','localtime'))) ORDER BY name";
        try (Connection c=Database.connect(); PreparedStatement ps=c.prepareStatement(sql)) {
            ps.setString(1,itemType);
            try (ResultSet rs=ps.executeQuery()) { while(rs.next()) items.add(rs.getString("name")+" | "+rs.getString("item_type")+" | Price: ₱"+Money.formatCents(rs.getLong("price_cents"))); }
        }
        return items;
    }

    public List<String> searchAvailableSummaries(String query) throws SQLException {
        List<String> items = new ArrayList<>();
        String sql = "SELECT name,item_type,price_cents FROM menu_items WHERE COALESCE(is_archived,0)=0 AND is_sold_out=0 AND (EXISTS (SELECT 1 FROM menu_periods mp WHERE mp.menu_item_id=menu_items.menu_item_id AND mp.active=1 AND date('now','localtime') BETWEEN date(mp.starts_on) AND date(mp.ends_on)) OR (NOT EXISTS (SELECT 1 FROM menu_periods mp WHERE mp.menu_item_id=menu_items.menu_item_id AND mp.active=1) AND available_today=1 AND daily_menu_date=date('now','localtime'))) AND LOWER(name) LIKE LOWER(?) ORDER BY name";
        try (Connection c=Database.connect(); PreparedStatement ps=c.prepareStatement(sql)) {
            ps.setString(1,"%"+(query==null?"":query.trim())+"%");
            try (ResultSet rs=ps.executeQuery()) { while(rs.next()) items.add(rs.getString("name")+" | "+rs.getString("item_type")+" | Price: ₱"+Money.formatCents(rs.getLong("price_cents"))); }
        }
        return items;
    }

    public List<String> findAvailableSummaries() throws SQLException {
        List<String> items = new ArrayList<>();
        String sql = "SELECT name,item_type,price_cents FROM menu_items WHERE COALESCE(is_archived,0)=0 AND is_sold_out=0 AND (EXISTS (SELECT 1 FROM menu_periods mp WHERE mp.menu_item_id=menu_items.menu_item_id AND mp.active=1 AND date('now','localtime') BETWEEN date(mp.starts_on) AND date(mp.ends_on)) OR (NOT EXISTS (SELECT 1 FROM menu_periods mp WHERE mp.menu_item_id=menu_items.menu_item_id AND mp.active=1) AND available_today=1 AND daily_menu_date=date('now','localtime'))) AND name IS NOT NULL AND TRIM(name)<>'' AND UPPER(TRIM(name))<>'NULL' ORDER BY item_type,name";
        try (Connection c=Database.connect(); PreparedStatement ps=c.prepareStatement(sql); ResultSet rs=ps.executeQuery()) {
            while(rs.next()) { items.add(rs.getString("name")+" | "+rs.getString("item_type")+" | Price: ₱"+Money.formatCents(rs.getLong("price_cents"))); }
        }
        return items;
    }

    public List<String> findAllPriceSummaries() throws SQLException {
        List<String> items = new ArrayList<>();

        String sql = """
            SELECT name, item_type, price_cents, is_sold_out, available_today, daily_menu_date,
                   CASE WHEN EXISTS (SELECT 1 FROM menu_periods mp WHERE mp.menu_item_id=menu_items.menu_item_id AND mp.active=1
                                     AND date('now','localtime') BETWEEN date(mp.starts_on) AND date(mp.ends_on))
                        THEN 1
                        WHEN NOT EXISTS (SELECT 1 FROM menu_periods mp WHERE mp.menu_item_id=menu_items.menu_item_id AND mp.active=1)
                             AND available_today=1 AND daily_menu_date=date('now','localtime')
                        THEN 1 ELSE 0 END AS is_on_today
            FROM menu_items
            WHERE COALESCE(is_archived,0)=0
              AND name IS NOT NULL
              AND TRIM(name) <> ''
              AND UPPER(TRIM(name)) <> 'NULL'
            ORDER BY name
            """;

        try (Connection connection = Database.connect();
             PreparedStatement statement =
                     connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {

            while (rs.next()) {
                items.add(
                        rs.getString("name")
                        + " | " + rs.getString("item_type")
                        + " | Price: ₱"
                        + Money.formatCents(rs.getLong("price_cents"))
                        + (rs.getInt("is_sold_out") == 1 ? "  • SOLD OUT" : "")
                        + (rs.getInt("is_on_today") == 0 ? "  • NOT ON TODAY'S MENU" : "")
                );
            }
        }

        return items;
    }

    public List<String> searchPriceSummaries(String query)
            throws SQLException {

        List<String> items = new ArrayList<>();

        if (query == null || query.isBlank()) {
            return findAllPriceSummaries();
        }

        String sql = """
            SELECT name, item_type, price_cents, is_sold_out, available_today, daily_menu_date,
                   CASE WHEN EXISTS (SELECT 1 FROM menu_periods mp WHERE mp.menu_item_id=menu_items.menu_item_id AND mp.active=1
                                     AND date('now','localtime') BETWEEN date(mp.starts_on) AND date(mp.ends_on))
                        THEN 1
                        WHEN NOT EXISTS (SELECT 1 FROM menu_periods mp WHERE mp.menu_item_id=menu_items.menu_item_id AND mp.active=1)
                             AND available_today=1 AND daily_menu_date=date('now','localtime')
                        THEN 1 ELSE 0 END AS is_on_today
            FROM menu_items
            WHERE COALESCE(is_archived,0)=0
              AND name IS NOT NULL
              AND TRIM(name) <> ''
              AND UPPER(TRIM(name)) <> 'NULL'
              AND LOWER(name) LIKE LOWER(?)
            ORDER BY name
            """;

        try (Connection connection = Database.connect();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, "%" + query.trim() + "%");

            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    items.add(
                            rs.getString("name")
                            + " | " + rs.getString("item_type")
                            + " | Price: ₱"
                            + Money.formatCents(rs.getLong("price_cents"))
                            + (rs.getInt("is_sold_out") == 1 ? "  • SOLD OUT" : "")
                            + (rs.getInt("is_on_today") == 0 ? "  • NOT ON TODAY'S MENU" : "")
                    );
                }
            }
        }

        return items;
    }

    public void updatePriceByName(String name, long priceCents)
            throws SQLException {

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "Menu item name is required."
            );
        }

        if (priceCents <= 0) {
            throw new IllegalArgumentException(
                    "Price must be a positive number."
            );
        }

        String sql = "UPDATE menu_items SET price = CAST(? AS REAL), price_cents = ? WHERE name = ? AND COALESCE(is_archived,0)=0";

        try (Connection connection = Database.connect();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, Money.toDatabaseDecimal(priceCents));
            statement.setLong(2, priceCents);
            statement.setString(3, name);

            int updatedRows = statement.executeUpdate();

            if (updatedRows == 0) {
                throw new SQLException(
                        "Menu item was not found: " + name
                );
            }
        }
    }

    /**
     * Removes malformed menu rows left by older versions or manual edits.
     * Valid rows are preserved. SQLite stores actual missing values as NULL;
     * the text "NULL" and blank names are also treated as invalid.
     */
    public int deleteInvalidRows() throws SQLException {
        String sql = """
            DELETE FROM menu_items
            WHERE name IS NULL
               OR TRIM(name) = ''
               OR UPPER(TRIM(name)) = 'NULL'
               OR item_type IS NULL
               OR price_cents IS NULL
               OR base_prep_minutes IS NULL
               OR price_cents < 0
               OR base_prep_minutes < 0
            """;

        try (Connection connection = Database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            return statement.executeUpdate();
        }
    }


    /**
     * Mines the current SQLite menu by counting saved items in each category.
     * The result is based on live database rows, not hard-coded chart values.
     */
    public java.util.Map<String, Integer> countByItemType() throws SQLException {
        java.util.Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        String sql = """
            WITH categories(category) AS (
                VALUES ('APPETIZER'), ('MAIN_COURSE'), ('BEVERAGE')
            ), saved_counts AS (
                SELECT item_type AS category, COUNT(*) AS item_count
                FROM menu_items
                WHERE name IS NOT NULL AND TRIM(name) <> '' AND UPPER(TRIM(name)) <> 'NULL'
                GROUP BY item_type
            )
            SELECT categories.category, COALESCE(SUM(saved_counts.item_count), 0) AS item_count
            FROM categories LEFT JOIN saved_counts ON categories.category = saved_counts.category
            GROUP BY categories.category
            ORDER BY categories.category
            """;
        try (Connection connection = Database.connect();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                counts.put(rs.getString("category"), rs.getInt("item_count"));
            }
        }
        return counts;
    }

    public void toggleSoldOutByName(String name) throws SQLException {
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement(
                "UPDATE menu_items SET is_sold_out=CASE WHEN is_sold_out=1 THEN 0 ELSE 1 END WHERE name=?")) {
            ps.setString(1, name);
            if (ps.executeUpdate() == 0) { throw new SQLException("Menu item not found: " + name); }
        }
    }

    public void setDailyAvailabilityByName(String name, boolean available) throws SQLException {
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement(
                "UPDATE menu_items SET available_today=?, daily_menu_date=date('now','localtime') WHERE name=? AND COALESCE(is_archived,0)=0")) {
            ps.setInt(1, available ? 1 : 0); ps.setString(2, name);
            if (ps.executeUpdate() == 0) { throw new SQLException("Menu item not found: " + name); }
        }
    }

    /**
     * Permanently removes unused menu items. Items referenced by order history
     * are archived so historical receipts and reports remain intact.
     */
    public boolean deleteByName(String name) throws SQLException {
        if (name == null || name.isBlank()) { throw new IllegalArgumentException("Menu item name is required."); }
        try (Connection c = Database.connect()) {
            c.setAutoCommit(false);
            try {
                long id;
                try (PreparedStatement find = c.prepareStatement(
                        "SELECT menu_item_id FROM menu_items WHERE name=? AND COALESCE(is_archived,0)=0")) {
                    find.setString(1, name.trim());
                    try (ResultSet rs = find.executeQuery()) {
                        if (!rs.next()) { c.rollback(); return false; }
                        id = rs.getLong(1);
                    }
                }
                boolean hasHistory;
                try (PreparedStatement check = c.prepareStatement(
                        "SELECT (EXISTS(SELECT 1 FROM order_items WHERE menu_item_id=?) OR EXISTS(SELECT 1 FROM combo_components WHERE menu_item_id=?))")) {
                    check.setLong(1, id);
                    check.setLong(2, id);
                    try (ResultSet rs = check.executeQuery()) { rs.next(); hasHistory = rs.getInt(1) != 0; }
                }
                if (hasHistory) {
                    try (PreparedStatement archive = c.prepareStatement(
                            "UPDATE menu_items SET is_archived=1, available_today=0, daily_menu_date=date('now','localtime') WHERE menu_item_id=?")) {
                        archive.setLong(1, id); archive.executeUpdate();
                    }
                    try (PreparedStatement periods = c.prepareStatement("UPDATE menu_periods SET active=0 WHERE menu_item_id=?")) {
                        periods.setLong(1, id); periods.executeUpdate();
                    }
                } else {
                    for (String sql : List.of(
                            "DELETE FROM menu_periods WHERE menu_item_id=?",
                            "DELETE FROM menu_item_ingredients WHERE menu_item_id=?",
                            "DELETE FROM combo_components WHERE menu_item_id=?")) {
                        try (PreparedStatement child = c.prepareStatement(sql)) {
                            child.setLong(1, id); child.executeUpdate();
                        }
                    }
                    try (PreparedStatement delete = c.prepareStatement("DELETE FROM menu_items WHERE menu_item_id=?")) {
                        delete.setLong(1, id); delete.executeUpdate();
                    }
                }
                c.commit();
                return true;
            } catch (SQLException | RuntimeException ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

}
