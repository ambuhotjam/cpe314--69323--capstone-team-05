
package ph.uc.cpe314.dinesmart.data;

import ph.uc.cpe314.dinesmart.model.MenuItem;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class MenuItemRepository {

    public long insert(MenuItem item) throws SQLException {
        if (item == null) {
            throw new IllegalArgumentException("Menu item is required.");
        }

        String name = item.getName();
        if (name == null || name.isBlank()
                || "NULL".equalsIgnoreCase(name.trim())) {
            throw new IllegalArgumentException(
                    "Menu item name cannot be empty or NULL.");
        }

        if (!Double.isFinite(item.getPrice()) || item.getPrice() < 0) {
            throw new IllegalArgumentException(
                    "Menu item price must be a valid non-negative number.");
        }

        if (item.getBasePrepMinutes() < 0) {
            throw new IllegalArgumentException(
                    "Preparation time cannot be negative.");
        }

        String sql = """
            INSERT INTO menu_items
                (name, item_type, price, base_prep_minutes)
            VALUES (?, ?, ?, ?)
            ON CONFLICT(name) DO UPDATE SET
                item_type = excluded.item_type,
                price = excluded.price,
                base_prep_minutes = excluded.base_prep_minutes
            """;

        try (Connection connection = Database.connect();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, item.getName());
            statement.setString(2, item.getItemType());
            statement.setDouble(3, item.getPrice());
            statement.setInt(4, item.getBasePrepMinutes());
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

    public List<String> findByTypeSummaries(String itemType) throws SQLException {
        if (itemType == null || !List.of("APPETIZER", "MAIN_COURSE", "BEVERAGE").contains(itemType))
            throw new IllegalArgumentException("Unknown menu category.");
        List<String> items = new ArrayList<>();
        String sql = "SELECT menu_item_id, name, item_type, price FROM menu_items WHERE item_type = ? AND name IS NOT NULL AND TRIM(name) <> '' AND UPPER(TRIM(name)) <> 'NULL' ORDER BY name";
        try (Connection connection = Database.connect(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, itemType);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) items.add(rs.getString("name") + " | " + rs.getString("item_type") + " | Price: ₱" + String.format("%.2f", rs.getDouble("price")));
            }
        }
        return items;
    }

    public List<String> findAllSummaries() throws SQLException {
        List<String> items = new ArrayList<>();

        String sql = """
            SELECT menu_item_id, name, item_type, price
            FROM menu_items
            WHERE name IS NOT NULL
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
                        + String.format("%.2f", rs.getDouble("price"))
                );
            }
        }

        return items;
    }

    public List<String> findAllPriceSummaries() throws SQLException {
        List<String> items = new ArrayList<>();

        String sql = """
            SELECT name, item_type, price
            FROM menu_items
            WHERE name IS NOT NULL
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
                        + String.format("%.2f", rs.getDouble("price"))
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
            SELECT name, item_type, price
            FROM menu_items
            WHERE name IS NOT NULL
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
                            + String.format(
                                    "%.2f", rs.getDouble("price"))
                    );
                }
            }
        }

        return items;
    }

    public void updatePriceByName(String name, double price)
            throws SQLException {

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "Menu item name is required."
            );
        }

        if (!Double.isFinite(price) || price <= 0) {
            throw new IllegalArgumentException(
                    "Price must be a positive number."
            );
        }

        String sql = "UPDATE menu_items SET price = ? WHERE name = ?";

        try (Connection connection = Database.connect();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setDouble(1, price);
            statement.setString(2, name);

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
               OR price IS NULL
               OR base_prep_minutes IS NULL
               OR price < 0
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
                VALUES ('APPETIZER'), ('MAIN_COURSE'), ('BEVERAGE'), ('COMBO')
            ), saved_counts AS (
                SELECT item_type AS category, COUNT(*) AS item_count
                FROM menu_items
                WHERE name IS NOT NULL AND TRIM(name) <> '' AND UPPER(TRIM(name)) <> 'NULL'
                GROUP BY item_type
                UNION ALL
                SELECT 'COMBO' AS category, COUNT(*) AS item_count FROM combos
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

    public boolean deleteByName(String name) throws SQLException {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "Menu item name is required."
            );
        }

        String sql = "DELETE FROM menu_items WHERE name = ?";

        try (Connection connection = Database.connect();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, name);

            return statement.executeUpdate() > 0;
        }
    }
}
