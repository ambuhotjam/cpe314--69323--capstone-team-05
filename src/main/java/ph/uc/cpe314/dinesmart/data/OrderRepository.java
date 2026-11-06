package ph.uc.cpe314.dinesmart.data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.LinkedHashMap;
import java.util.Map;

/** Persists customer orders, queue status changes, and completed sales reports. */
public final class OrderRepository {
    public record MenuChoice(String name, String type, double price, boolean combo) {
        public MenuChoice(String name, String type, double price) { this(name, type, price, false); }
        public String cartKey() { return (combo ? "@COMBO@" : "") + name; }
        @Override public String toString() {
            return name + "  ·  ₱" + String.format("%.2f", price) + "  ·  " + type.replace('_', ' ');
        }
    }
    public record BestSeller(String name, String type, int quantity) { }

    public List<MenuChoice> findOrderableItems() throws SQLException {
        String sql = "SELECT name, item_type, price FROM menu_items ORDER BY item_type, name";
        List<MenuChoice> items = new ArrayList<>();
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) items.add(new MenuChoice(rs.getString("name"), rs.getString("item_type"), rs.getDouble("price")));
        }
        String comboSql = "SELECT c.name, COALESCE(SUM(m.price * cc.quantity),0) * (1-c.discount_percent/100.0) price FROM combos c LEFT JOIN combo_components cc ON cc.combo_id=c.combo_id LEFT JOIN menu_items m ON m.menu_item_id=cc.menu_item_id GROUP BY c.combo_id,c.name,c.discount_percent ORDER BY c.name";
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement(comboSql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) items.add(new MenuChoice(rs.getString("name"), "COMBO", rs.getDouble("price"), true));
        }
        return items;
    }

    public String placeOrder(String customerName, Map<String, Integer> items) throws SQLException {
        if (customerName == null || customerName.isBlank()) throw new IllegalArgumentException("Enter the customer's name.");
        if (items == null || items.isEmpty()) throw new IllegalArgumentException("Add at least one item to the order.");
        String orderId = "DS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        try (Connection c = Database.connect()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO restaurant_orders(order_id, ordered_at, status, customer_name) VALUES(?, datetime('now','localtime'), 'PENDING', ?)")) {
                    ps.setString(1, orderId); ps.setString(2, customerName.trim()); ps.executeUpdate();
                }
                try (PreparedStatement find = c.prepareStatement("SELECT menu_item_id, price FROM menu_items WHERE name = ?");
                     PreparedStatement add = c.prepareStatement("INSERT INTO order_items(order_id, menu_item_id, quantity, unit_price_at_order) VALUES(?,?,?,?)")) {
                    for (Map.Entry<String, Integer> entry : items.entrySet()) {
                        if (entry.getValue() == null || entry.getValue() < 1) throw new IllegalArgumentException("Item quantities must be at least 1.");
                        boolean isCombo = entry.getKey().startsWith("@COMBO@");
                        String itemName = isCombo ? entry.getKey().substring(7) : entry.getKey();
                        if (isCombo) {
                            try (PreparedStatement comboFind = c.prepareStatement("SELECT c.combo_id, COALESCE(SUM(m.price * cc.quantity),0) * (1-c.discount_percent/100.0) price FROM combos c LEFT JOIN combo_components cc ON cc.combo_id=c.combo_id LEFT JOIN menu_items m ON m.menu_item_id=cc.menu_item_id WHERE c.name=? GROUP BY c.combo_id,c.discount_percent")) {
                                comboFind.setString(1, itemName);
                                try (ResultSet rs = comboFind.executeQuery()) {
                                    if (!rs.next()) throw new IllegalArgumentException("Combo not found: " + itemName);
                                    try (PreparedStatement comboAdd = c.prepareStatement("INSERT INTO order_combo_items(order_id,combo_id,quantity,unit_price_at_order) VALUES(?,?,?,?)")) {
                                        comboAdd.setString(1, orderId); comboAdd.setInt(2, rs.getInt("combo_id")); comboAdd.setInt(3, entry.getValue()); comboAdd.setDouble(4, rs.getDouble("price")); comboAdd.executeUpdate();
                                    }
                                }
                            }
                        } else {
                            find.setString(1, itemName);
                            try (ResultSet rs = find.executeQuery()) {
                                if (!rs.next()) throw new IllegalArgumentException("Menu item not found: " + itemName);
                                add.setString(1, orderId); add.setInt(2, rs.getInt("menu_item_id"));
                                add.setInt(3, entry.getValue()); add.setDouble(4, rs.getDouble("price")); add.executeUpdate();
                            }
                        }
                    }
                }
                c.commit();
                return orderId;
            } catch (Exception ex) {
                c.rollback();
                if (ex instanceof SQLException sql) throw sql;
                if (ex instanceof IllegalArgumentException arg) throw arg;
                throw new SQLException("Could not save order.", ex);
            } finally { c.setAutoCommit(true); }
        }
    }


public List<String> findActiveQueue() throws SQLException {
    return findOrders(
        "WHERE o.status IN ('PENDING','PREPARING')",
        "ORDER BY o.ordered_at ASC, o.rowid ASC"
    );
}

public List<String> findOrderHistory() throws SQLException {
    return findOrders(
        "",
        "ORDER BY o.ordered_at DESC, o.rowid DESC LIMIT 100"
    );
}

private List<String> findOrders(
        String whereClause,
        String orderClause) throws SQLException {

    String sql =
        "SELECT o.order_id, o.customer_name, o.status, o.ordered_at, " +
        "COALESCE(SUM(oi.quantity * oi.unit_price_at_order), 0) + " +
        "COALESCE((SELECT SUM(oci.quantity * oci.unit_price_at_order) " +
        "FROM order_combo_items oci WHERE oci.order_id=o.order_id), 0) AS total, " +

        "COALESCE((SELECT GROUP_CONCAT(" +
        "mi.name || ' x' || oi2.quantity, ', ') " +
        "FROM order_items oi2 " +
        "JOIN menu_items mi ON mi.menu_item_id=oi2.menu_item_id " +
        "WHERE oi2.order_id=o.order_id), '') || " +

        "CASE WHEN EXISTS(" +
        "SELECT 1 FROM order_combo_items ocx " +
        "WHERE ocx.order_id=o.order_id) " +
        "THEN ', ' ELSE '' END || " +

        "COALESCE((SELECT GROUP_CONCAT(" +
        "c.name || ' combo x' || oc2.quantity, ', ') " +
        "FROM order_combo_items oc2 " +
        "JOIN combos c ON c.combo_id=oc2.combo_id " +
        "WHERE oc2.order_id=o.order_id), '') AS items " +

        "FROM restaurant_orders o " +
        "LEFT JOIN order_items oi ON oi.order_id=o.order_id " +

        whereClause + " " +
        "GROUP BY o.order_id " +
        orderClause;

    List<String> rows = new ArrayList<>();

    try (
        Connection c = Database.connect();
        PreparedStatement ps = c.prepareStatement(sql);
        ResultSet rs = ps.executeQuery()
    ) {

        while (rs.next()) {

            String id = rs.getString("order_id");

            int prepMinutes = estimatePreparationMinutes(id);

            rows.add(
                id + "  |  " +
                rs.getString("customer_name") + "  |  " +
                rs.getString("status") + "  |  ₱" +
                String.format("%.2f", rs.getDouble("total")) +
                "  |  " + rs.getString("items") +
                "  |  Est. prep: " + prepMinutes +
                " min  |  " + rs.getString("ordered_at")
            );
        }
    }

    return rows;
}


    public void changeStatus(String orderId, String newStatus) throws SQLException {
        if (!List.of("PREPARING", "COMPLETED", "CANCELLED").contains(newStatus)) throw new IllegalArgumentException("Unsupported order status.");
        String allowed = switch (newStatus) {
            case "PREPARING" -> "PENDING";
            case "COMPLETED" -> "PREPARING";
            case "CANCELLED" -> "PENDING";
            default -> "";
        };
        try (Connection c = Database.connect()) {
            if ("PREPARING".equals(newStatus)) {
                try (PreparedStatement first = c.prepareStatement("SELECT order_id FROM restaurant_orders WHERE status='PENDING' ORDER BY ordered_at ASC, rowid ASC LIMIT 1");
                     ResultSet rs = first.executeQuery()) {
                    if (rs.next() && !orderId.equals(rs.getString(1))) {
                        throw new IllegalStateException("FIFO rule: prepare the oldest pending order first (" + rs.getString(1) + ").");
                    }
                }
            }
            try (PreparedStatement ps = c.prepareStatement("UPDATE restaurant_orders SET status=? WHERE order_id=? AND status=?")) {
                ps.setString(1, newStatus); ps.setString(2, orderId); ps.setString(3, allowed);
                if (ps.executeUpdate() == 0) throw new IllegalStateException("This order can no longer be changed. Refresh the queue.");
            }
        }
    }

    public List<String> findPendingOrderIdsOldestFirst() throws SQLException {
        List<String> ids = new ArrayList<>();
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement("SELECT order_id FROM restaurant_orders WHERE status='PENDING' ORDER BY ordered_at ASC, rowid ASC"); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) ids.add(rs.getString(1));
        }
        return ids;
    }

    public void cancelPendingOrder(String orderId) throws SQLException {
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement("UPDATE restaurant_orders SET status='CANCELLED' WHERE order_id=? AND status='PENDING'")) {
            ps.setString(1, orderId);
            if (ps.executeUpdate() != 1) throw new IllegalStateException("Order is no longer pending and cannot be cancelled.");
        }
    }

    /** Stack-style rush action: cancel the most recently placed order that is still pending. */
    public String cancelMostRecentPendingOrder() throws SQLException {
        try (Connection c = Database.connect()) {
            c.setAutoCommit(false);
            try {
                String latest = null;
                try (PreparedStatement ps = c.prepareStatement("SELECT order_id FROM restaurant_orders WHERE status='PENDING' ORDER BY ordered_at DESC, rowid DESC LIMIT 1"); ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) latest = rs.getString(1);
                }
                if (latest == null) { c.rollback(); return null; }
                try (PreparedStatement ps = c.prepareStatement("UPDATE restaurant_orders SET status='CANCELLED' WHERE order_id=? AND status='PENDING'")) {
                    ps.setString(1, latest);
                    if (ps.executeUpdate() != 1) throw new SQLException("The latest pending order changed before cancellation.");
                }
                c.commit();
                return latest;
            } catch (Exception ex) {
                c.rollback();
                if (ex instanceof SQLException sql) throw sql;
                throw new SQLException("Could not cancel the most recent pending order.", ex);
            } finally { c.setAutoCommit(true); }
        }
    }

    /** Counts non-cancelled orders by local clock hour for the required peak-hour analysis. */
    public Map<Integer, Integer> getOrdersByHour() throws SQLException {
        Map<Integer, Integer> counts = new LinkedHashMap<>();
        for (int hour = 0; hour < 24; hour++) counts.put(hour, 0);
        String sql = "SELECT CAST(strftime('%H', ordered_at) AS INTEGER) hour, COUNT(*) total FROM restaurant_orders WHERE status <> 'CANCELLED' GROUP BY hour ORDER BY hour";
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) counts.put(rs.getInt("hour"), rs.getInt("total"));
        }
        return counts;
    }

    public String getPeakOrderingHour() throws SQLException {
        Map<Integer, Integer> counts = getOrdersByHour();
        Map.Entry<Integer, Integer> peak = counts.entrySet().stream().max(Map.Entry.comparingByValue()).orElse(null);
        if (peak == null || peak.getValue() == 0) return "No order history yet";
        int hour = peak.getKey();
        return String.format("%d:00–%d:00 (%d orders)", hour, (hour + 1) % 24, peak.getValue());
    }

    /** Processes the historical order log in one repository call; invoke from a background thread. */
    public int countHistoricalOrders() throws SQLException {
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM restaurant_orders"); ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    /** Aggregate historical orders and top sellers; intended to run on a background executor. */
    public String analyzeHistoricalOrderLog() throws SQLException {
        int total = 0, completed = 0, cancelled = 0;
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement("SELECT COUNT(*), SUM(CASE WHEN status='COMPLETED' THEN 1 ELSE 0 END), SUM(CASE WHEN status='CANCELLED' THEN 1 ELSE 0 END) FROM restaurant_orders"); ResultSet rs = ps.executeQuery()) {
            if (rs.next()) { total = rs.getInt(1); completed = rs.getInt(2); cancelled = rs.getInt(3); }
        }
        StringBuilder report = new StringBuilder("Historical order analysis complete\n")
                .append("Total records: ").append(total).append("\nCompleted: ").append(completed)
                .append("\nCancelled: ").append(cancelled).append("\nPeak hour: ").append(getPeakOrderingHour())
                .append("\n\nTop completed-order items:\n");
        for (BestSeller item : findBestSellers(5)) report.append("• ").append(item.name()).append(" — ").append(item.quantity()).append(" sold\n");
        return report.toString();
    }

    /** Estimated prep minutes from saved menu item preparation estimates in an order. */
    public int estimatePreparationMinutes(String orderId) throws SQLException {
        String sql = "SELECT COALESCE(SUM(mi.base_prep_minutes * oi.quantity),0) FROM order_items oi JOIN menu_items mi ON mi.menu_item_id=oi.menu_item_id WHERE oi.order_id=?";
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, orderId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getInt(1) : 0; }
        }
    }

    public List<BestSeller> findBestSellers(int limit) throws SQLException {
        String sql = "SELECT mi.name, mi.item_type, SUM(oi.quantity) sold FROM order_items oi " +
                "JOIN restaurant_orders o ON o.order_id=oi.order_id JOIN menu_items mi ON mi.menu_item_id=oi.menu_item_id " +
                "WHERE o.status='COMPLETED' GROUP BY mi.menu_item_id, mi.name, mi.item_type ORDER BY sold DESC, mi.name LIMIT ?";
        List<BestSeller> rows = new ArrayList<>();
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) rows.add(new BestSeller(rs.getString("name"), rs.getString("item_type"), rs.getInt("sold"))); }
        }
        return rows;
    }

    public double getTodaySales() throws SQLException {
        String sql = "SELECT COALESCE((SELECT SUM(oi.quantity * oi.unit_price_at_order) FROM order_items oi JOIN restaurant_orders o ON o.order_id=oi.order_id WHERE o.status='COMPLETED' AND date(o.ordered_at)=date('now','localtime')),0) + COALESCE((SELECT SUM(oci.quantity * oci.unit_price_at_order) FROM order_combo_items oci JOIN restaurant_orders o ON o.order_id=oci.order_id WHERE o.status='COMPLETED' AND date(o.ordered_at)=date('now','localtime')),0)";
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getDouble(1) : 0; }
    }
    public int getTodayOrderCount() throws SQLException {
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM restaurant_orders WHERE status='COMPLETED' AND date(ordered_at)=date('now','localtime')"); ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getInt(1) : 0; }
    }
}
