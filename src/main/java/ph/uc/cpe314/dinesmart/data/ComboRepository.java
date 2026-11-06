package ph.uc.cpe314.dinesmart.data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Persists combos and computes their live price from current menu-item prices. */
public final class ComboRepository {
    public void createCombo(String name, Map<String, Integer> itemQuantities, double discountPercent) throws SQLException {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Combo name is required.");
        if (itemQuantities == null || itemQuantities.isEmpty()) throw new IllegalArgumentException("Add at least one menu item to the combo.");
        if (!Double.isFinite(discountPercent) || discountPercent < 0 || discountPercent >= 100) throw new IllegalArgumentException("Discount must be from 0 up to, but not including, 100 percent.");
        Database.initialize();
        try (Connection c = Database.connect()) {
            c.setAutoCommit(false);
            try {
                long comboId;
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO combos(name, discount_percent) VALUES (?, ?)", PreparedStatement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, name.trim()); ps.setDouble(2, discountPercent); ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (keys.next()) comboId = keys.getLong(1);
                        else try (PreparedStatement find = c.prepareStatement("SELECT combo_id FROM combos WHERE name=?")) {
                            find.setString(1, name.trim()); try (ResultSet rs = find.executeQuery()) { if (!rs.next()) throw new SQLException("Could not retrieve combo ID."); comboId = rs.getLong(1); }
                        }
                    }
                }
                for (Map.Entry<String, Integer> entry : itemQuantities.entrySet()) {
                    if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null || entry.getValue() <= 0) throw new IllegalArgumentException("Each combo item needs a name and positive quantity.");
                    try (PreparedStatement ps = c.prepareStatement("INSERT INTO combo_components(combo_id, menu_item_id, quantity) SELECT ?, menu_item_id, ? FROM menu_items WHERE name=? AND price > 0")) {
                        ps.setLong(1, comboId); ps.setInt(2, entry.getValue()); ps.setString(3, entry.getKey().trim());
                        if (ps.executeUpdate() == 0) throw new SQLException("Menu item not found or its selling price is still ₱0.00: " + entry.getKey());
                    }
                }
                c.commit();
            } catch (Exception ex) {
                c.rollback();
                if (ex instanceof SQLException sql) throw sql;
                if (ex instanceof RuntimeException runtime) throw runtime;
                throw new SQLException("Could not create combo.", ex);
            } finally { c.setAutoCommit(true); }
        }
    }

    public List<String> findAllSummaries() throws SQLException {
        List<String> result = new java.util.ArrayList<>();
        String sql = """
            SELECT c.name, c.discount_percent,
                   COALESCE(SUM(m.price * cc.quantity), 0) AS regular_total,
                   COALESCE(SUM(m.price * cc.quantity), 0) * (1 - c.discount_percent / 100.0) AS selling_price
            FROM combos c LEFT JOIN combo_components cc ON c.combo_id=cc.combo_id
            LEFT JOIN menu_items m ON cc.menu_item_id=m.menu_item_id
            GROUP BY c.combo_id, c.name, c.discount_percent ORDER BY c.name
            """;
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) result.add(rs.getString("name") + " | COMBO | Regular: ₱" + String.format("%.2f", rs.getDouble("regular_total")) + " | Discount: " + String.format("%.1f%%", rs.getDouble("discount_percent")) + " | Price: ₱" + String.format("%.2f", rs.getDouble("selling_price")));
        }
        return result;
    }

    public double calculateSellingPrice(String comboName) throws SQLException {
        String sql = "SELECT COALESCE(SUM(m.price * cc.quantity),0) * (1-c.discount_percent/100.0) FROM combos c LEFT JOIN combo_components cc ON c.combo_id=cc.combo_id LEFT JOIN menu_items m ON cc.menu_item_id=m.menu_item_id WHERE c.name=? GROUP BY c.combo_id";
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, comboName);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return rs.getDouble(1); }
        }
        throw new SQLException("Combo not found: " + comboName);
    }
}
