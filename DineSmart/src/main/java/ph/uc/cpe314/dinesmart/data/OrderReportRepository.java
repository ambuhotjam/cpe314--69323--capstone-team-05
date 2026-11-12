package ph.uc.cpe314.dinesmart.data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ph.uc.cpe314.dinesmart.util.Money;

/** Read-only SQL aggregations used by cashier and reporting screens. */
public final class OrderReportRepository {
    public record BestSeller(String name, String type, int quantity) { }

    /** Cashier-facing list of active order totals. */
    public List<String> findCashierTotals() throws SQLException {
        List<String> rows = new ArrayList<>();
        String sql = """
                SELECT o.order_id, o.customer_name, o.status, o.payment_status,
                       COALESCE(SUM(oi.quantity * oi.unit_price_cents), 0) AS total
                FROM restaurant_orders o
                LEFT JOIN order_items oi ON oi.order_id = o.order_id
                WHERE o.status <> 'CANCELLED'
                GROUP BY o.order_id
                ORDER BY o.ordered_at DESC
                LIMIT 100
                """;
        try (Connection connection = Database.connect();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                rows.add(rs.getString("order_id") + "  |  "
                        + rs.getString("customer_name") + "  |  "
                        + rs.getString("status") + "  |  PAYMENT: "
                        + rs.getString("payment_status") + "  |  TOTAL ₱"
                        + Money.formatCents(rs.getLong("total")));
            }
        }
        return rows;
    }

    /** Counts non-cancelled orders by local clock hour, including zero-count hours. */
    public Map<Integer, Integer> getOrdersByHour() throws SQLException {
        Map<Integer, Integer> counts = new LinkedHashMap<>();
        for (int hour = 0; hour < 24; hour++) {
            counts.put(hour, 0);
        }
        String sql = """
                SELECT CAST(strftime('%H', ordered_at) AS INTEGER) AS hour,
                       COUNT(*) AS total
                FROM restaurant_orders
                WHERE status <> 'CANCELLED'
                GROUP BY hour
                ORDER BY hour
                """;
        try (Connection connection = Database.connect();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                counts.put(rs.getInt("hour"), rs.getInt("total"));
            }
        }
        return counts;
    }

    /** Returns the busiest order hour from the current local order history. */
    public String getPeakOrderingHour() throws SQLException {
        Map<Integer, Integer> counts = getOrdersByHour();
        Map.Entry<Integer, Integer> peak = counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .orElse(null);
        if (peak == null || peak.getValue() == 0) {
            return "No order history yet";
        }
        int hour = peak.getKey();
        return String.format("%d:00–%d:00 (%d orders)",
                hour, (hour + 1) % 24, peak.getValue());
    }

    /** Counts all historical order records. */
    public int countHistoricalOrders() throws SQLException {
        try (Connection connection = Database.connect();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM restaurant_orders");
             ResultSet rs = statement.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    /** Produces a summary of completed, cancelled, and best-selling orders. */
    public String analyzeHistoricalOrderLog() throws SQLException {
        int total = 0;
        int completed = 0;
        int cancelled = 0;
        String sql = """
                SELECT COUNT(*),
                       SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END),
                       SUM(CASE WHEN status = 'CANCELLED' THEN 1 ELSE 0 END)
                FROM restaurant_orders
                """;
        try (Connection connection = Database.connect();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            if (rs.next()) {
                total = rs.getInt(1);
                completed = rs.getInt(2);
                cancelled = rs.getInt(3);
            }
        }

        StringBuilder report = new StringBuilder(
                "Historical order analysis complete\n")
                .append("Total records: ").append(total)
                .append("\nCompleted: ").append(completed)
                .append("\nCancelled: ").append(cancelled)
                .append("\nPeak hour: ").append(getPeakOrderingHour())
                .append("\n\nTop completed-order items:\n");
        for (BestSeller item : findBestSellers(5)) {
            report.append("• ").append(item.name())
                    .append(" — ").append(item.quantity()).append(" sold\n");
        }
        return report.toString();
    }

    /** Returns the most frequently ordered menu items in completed orders. */
    public List<BestSeller> findBestSellers(int limit) throws SQLException {
        String sql = """
                SELECT mi.name, mi.item_type, SUM(oi.quantity) AS sold
                FROM order_items oi
                JOIN restaurant_orders o ON o.order_id = oi.order_id
                JOIN menu_items mi ON mi.menu_item_id = oi.menu_item_id
                WHERE o.status = 'COMPLETED'
                GROUP BY mi.menu_item_id, mi.name, mi.item_type
                ORDER BY sold DESC, mi.name
                LIMIT ?
                """;
        List<BestSeller> rows = new ArrayList<>();
        try (Connection connection = Database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, Math.max(1, limit));
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    rows.add(new BestSeller(rs.getString("name"),
                            rs.getString("item_type"), rs.getInt("sold")));
                }
            }
        }
        return rows;
    }

    /** Returns completed order sales for today's local date in integer cents. */
    public long getTodaySalesCents() throws SQLException {
        String sql = """
                SELECT COALESCE(SUM(oi.quantity * oi.unit_price_cents), 0)
                FROM order_items oi
                JOIN restaurant_orders o ON o.order_id = oi.order_id
                WHERE o.status = 'COMPLETED'
                  AND date(o.ordered_at) = date('now','localtime')
                """;
        try (Connection connection = Database.connect();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }

    /** Returns today's orders created by one signed-in staff account. */
    public List<String> findOrdersCreatedByToday(long staffUserId) throws SQLException {
        if (staffUserId <= 0) {
            throw new IllegalArgumentException("A valid staff user is required.");
        }
        List<String> rows = new ArrayList<>();
        String sql = """
                SELECT o.order_id, o.customer_name, o.status,
                       COALESCE(SUM(oi.quantity * oi.unit_price_cents), 0) AS total
                FROM restaurant_orders o
                LEFT JOIN order_items oi ON oi.order_id = o.order_id
                WHERE o.created_by = ?
                  AND date(o.ordered_at) = date('now','localtime')
                GROUP BY o.order_id
                ORDER BY o.ordered_at DESC
                """;
        try (Connection connection = Database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, staffUserId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    rows.add(rs.getString("order_id") + " | "
                            + rs.getString("customer_name") + " | "
                            + rs.getString("status") + " | ₱"
                            + Money.formatCents(rs.getLong("total")));
                }
            }
        }
        return rows;
    }

    /** Returns completed sales grouped by staff account for today's local date. */
    public Map<String, Long> getTodaySalesByStaffCents() throws SQLException {
        Map<String, Long> totals = new LinkedHashMap<>();
        String sql = """
                SELECT COALESCE(s.display_name, 'Unassigned') AS staff_name,
                       COALESCE(SUM(oi.quantity * oi.unit_price_cents), 0) AS sales_cents
                FROM restaurant_orders o
                LEFT JOIN staff_users s ON s.staff_user_id = o.created_by
                LEFT JOIN order_items oi ON oi.order_id = o.order_id
                WHERE o.status = 'COMPLETED'
                  AND date(o.ordered_at) = date('now','localtime')
                GROUP BY s.staff_user_id, s.display_name
                ORDER BY staff_name
                """;
        try (Connection connection = Database.connect();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                totals.put(rs.getString("staff_name"), rs.getLong("sales_cents"));
            }
        }
        return totals;
    }

    /** Counts completed orders for today's local date. */
    public int getTodayOrderCount() throws SQLException {
        String sql = """
                SELECT COUNT(*)
                FROM restaurant_orders
                WHERE status = 'COMPLETED'
                  AND date(ordered_at) = date('now','localtime')
                """;
        try (Connection connection = Database.connect();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }
}
