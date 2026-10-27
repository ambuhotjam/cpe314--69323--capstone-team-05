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

    public List<String> findAllSummaries() throws SQLException {
        List<String> items = new ArrayList<>();

        String sql = """
            SELECT menu_item_id, name, item_type, price
            FROM menu_items
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
            WHERE LOWER(name) LIKE LOWER(?)
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
