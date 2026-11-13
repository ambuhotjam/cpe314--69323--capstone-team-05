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
import ph.uc.cpe314.dinesmart.service.RecursiveIngredientCalculator;
import java.util.UUID;

/**
 * Persists customer orders, queue status changes, inventory movements,
 * and completed-sales reports.
 */
public final class OrderRepository {
    private final OrderReportRepository reportRepository = new OrderReportRepository();
    private final RecursiveIngredientCalculator ingredientCalculator =
            new RecursiveIngredientCalculator();

    public record MenuChoice(String name, String type, long priceCents) {
        public String cartKey() {
            return name;
        }

        @Override
        public String toString() {
            return name + "  ·  ₱" + Money.formatCents(priceCents)
                    + "  ·  " + type.replace('_', ' ');
        }
    }

    public record BestSeller(String name, String type, int quantity) { }

    public List<MenuChoice> findOrderableItems() throws SQLException {
        String sql = """
                SELECT name, item_type, price_cents
                FROM menu_items
                WHERE COALESCE(is_archived,0)=0
                  AND is_sold_out = 0
                  AND (
                    EXISTS (SELECT 1 FROM menu_periods mp
                            WHERE mp.menu_item_id=menu_items.menu_item_id AND mp.active=1
                              AND date('now','localtime') BETWEEN date(mp.starts_on) AND date(mp.ends_on))
                    OR (
                      NOT EXISTS (SELECT 1 FROM menu_periods mp
                                  WHERE mp.menu_item_id=menu_items.menu_item_id AND mp.active=1)
                      AND available_today = 1
                      AND daily_menu_date = date('now','localtime')
                    )
                  )
                ORDER BY item_type, name
                """;

        List<MenuChoice> items = new ArrayList<>();
        try (Connection c = Database.connect();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                items.add(new MenuChoice(
                        rs.getString("name"),
                        rs.getString("item_type"),
                        rs.getLong("price_cents")));
            }
        }
        return items;
    }

    public String placeOrder(String customerName, Map<String, Integer> items)
            throws SQLException {
        return placeOrder(customerName, items, null, null);
    }

    /**
     * Saves an order with optional table and signed-in staff ownership.
     *
     * @param customerName customer name shown to staff
     * @param items menu item names and quantities
     * @param tableLabel optional restaurant table label
     * @param staffUserId optional authenticated staff identifier
     * @return generated order identifier
     * @throws SQLException when the transaction cannot be saved
     */
    public String placeOrder(String customerName, Map<String, Integer> items,
                             String tableLabel, Long staffUserId) throws SQLException {

        if (customerName == null || customerName.isBlank()) {
            throw new IllegalArgumentException("Enter the customer's name.");
        }
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Add at least one item to the order.");
        }

        String orderId = "DS-" + UUID.randomUUID()
                .toString().substring(0, 8).toUpperCase();

        try (Connection c = Database.connect()) {
            c.setAutoCommit(false);
            try {
                Long tableId = resolveTableId(c, tableLabel);
                try (PreparedStatement ps = c.prepareStatement("""
                        INSERT INTO restaurant_orders
                            (order_id, ordered_at, status, customer_name, table_id, created_by)
                        VALUES (?, datetime('now','localtime'), 'PENDING', ?, ?, ?)
                        """)) {
                    ps.setString(1, orderId);
                    ps.setString(2, customerName.trim());
                    if (tableId == null) {
                        ps.setNull(3, java.sql.Types.INTEGER);
                    } else {
                        ps.setLong(3, tableId);
                    }
                    if (staffUserId == null) {
                        ps.setNull(4, java.sql.Types.INTEGER);
                    } else {
                        ps.setLong(4, staffUserId);
                    }
                    ps.executeUpdate();
                }

                String findSql = "SELECT menu_item_id, price_cents FROM menu_items WHERE name = ?";
                String addSql = """
                        INSERT INTO order_items
                            (order_id, menu_item_id, quantity,
                             unit_price_at_order, unit_price_cents)
                        VALUES (?, ?, ?, CAST(? AS REAL), ?)
                        """;

                try (PreparedStatement find = c.prepareStatement(findSql);
                     PreparedStatement add = c.prepareStatement(addSql)) {

                    for (Map.Entry<String, Integer> entry : items.entrySet()) {
                        Integer quantity = entry.getValue();
                        if (quantity == null || quantity < 1) {
                            throw new IllegalArgumentException(
                                    "Item quantities must be at least 1.");
                        }

                        String itemName = entry.getKey();
                        find.setString(1, itemName);

                        try (ResultSet rs = find.executeQuery()) {
                            if (!rs.next()) {
                                throw new IllegalArgumentException(
                                        "Menu item not found: " + itemName);
                            }

                            long menuId = rs.getLong("menu_item_id");
                            long priceCents = rs.getLong("price_cents");

                            if (isSoldOutOrNotOnMenu(c, menuId)) {
                                throw new IllegalStateException(
                                        itemName + " is sold out or not on today's menu.");
                            }

                            add.setString(1, orderId);
                            add.setLong(2, menuId);
                            add.setInt(3, quantity);
                            add.setString(4, Money.toDatabaseDecimal(priceCents));
                            add.setLong(5, priceCents);
                            add.executeUpdate();
                        }
                    }
                }

                // Aggregate every dish's ingredient requirements recursively before
                // deducting stock. Shared ingredients are deducted once per order.
                deductRecipeIngredients(c, orderId);

                c.commit();
                return orderId;
            } catch (SQLException | RuntimeException ex) {
                rollback(c, ex);
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    private Long resolveTableId(Connection c, String tableLabel) throws SQLException {
        if (tableLabel == null || tableLabel.isBlank()
                || "Unassigned".equalsIgnoreCase(tableLabel.trim())) {
            return null;
        }
        String label = tableLabel.trim();
        try (PreparedStatement insert = c.prepareStatement(
                "INSERT OR IGNORE INTO restaurant_tables(table_label) VALUES(?)")) {
            insert.setString(1, label);
            insert.executeUpdate();
        }
        try (PreparedStatement find = c.prepareStatement(
                "SELECT table_id FROM restaurant_tables WHERE table_label = ? COLLATE NOCASE")) {
            find.setString(1, label);
            try (ResultSet rs = find.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        throw new SQLException("Could not find or create restaurant table: " + label);
    }

    private static void rollback(Connection connection, Exception cause) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            cause.addSuppressed(rollbackFailure);
        }
    }

    private boolean isSoldOutOrNotOnMenu(Connection c, long menuId)
            throws SQLException {

        String sql = """
                SELECT
                    is_sold_out,
                    (
                      EXISTS (
                        SELECT 1 FROM menu_periods mp
                        WHERE mp.menu_item_id=menu_items.menu_item_id AND mp.active=1
                          AND date('now','localtime') BETWEEN date(mp.starts_on) AND date(mp.ends_on)
                      )
                      OR (
                        NOT EXISTS (SELECT 1 FROM menu_periods mp
                                    WHERE mp.menu_item_id=menu_items.menu_item_id AND mp.active=1)
                        AND available_today=1
                        AND daily_menu_date=date('now','localtime')
                      )
                    ) AS is_available
                FROM menu_items
                WHERE menu_item_id = ?
                """;

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, menuId);
            try (ResultSet rs = ps.executeQuery()) {
                return !rs.next()
                        || rs.getInt("is_sold_out") == 1
                        || rs.getInt("is_available") != 1;
            }
        }
    }

    private void deductRecipeIngredients(Connection c, String orderId)
            throws SQLException {

        Map<Long, OrderLineBuilder> orderLines = new LinkedHashMap<>();
        String sql = """
                SELECT oi.order_item_id, oi.menu_item_id, oi.quantity,
                       mi.name AS menu_item_name,
                       i.ingredient_id, i.name AS ingredient_name,
                       i.stock_unit, mii.quantity_required
                FROM order_items oi
                JOIN menu_items mi ON mi.menu_item_id = oi.menu_item_id
                LEFT JOIN menu_item_ingredients mii
                       ON mii.menu_item_id = oi.menu_item_id
                LEFT JOIN ingredients i ON i.ingredient_id = mii.ingredient_id
                WHERE oi.order_id = ?
                ORDER BY oi.order_item_id, i.ingredient_id
                """;

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long orderItemId = rs.getLong("order_item_id");
                    OrderLineBuilder line = orderLines.get(orderItemId);
                    if (line == null) {
                        line = new OrderLineBuilder(
                                rs.getString("menu_item_name"), rs.getInt("quantity"));
                        orderLines.put(orderItemId, line);
                    }

                    long ingredientId = rs.getLong("ingredient_id");
                    if (!rs.wasNull()) {
                        line.ingredients.add(new RecursiveIngredientCalculator.IngredientUse(
                                ingredientId,
                                rs.getString("ingredient_name"),
                                rs.getString("stock_unit"),
                                rs.getDouble("quantity_required")));
                    }
                }
            }
        }

        List<RecursiveIngredientCalculator.OrderedMenuItem> calculatorInput =
                orderLines.values().stream()
                        .map(OrderLineBuilder::toOrderedMenuItem)
                        .toList();
        List<RecursiveIngredientCalculator.IngredientTotal> totals =
                ingredientCalculator.calculate(calculatorInput);

        for (RecursiveIngredientCalculator.IngredientTotal total : totals) {
            double available = findIngredientStock(c, total.ingredientId());
            double required = total.requiredQuantity();
            if (available + 0.000001 < required) {
                throw new IllegalStateException(
                        "Insufficient " + total.ingredientName() + " for this order. "
                        + "Required total " + required + " " + total.stockUnit()
                        + ", available " + available + " " + total.stockUnit() + ".");
            }

            try (PreparedStatement update = c.prepareStatement("""
                    UPDATE ingredients
                    SET stock_quantity = stock_quantity - ?
                    WHERE ingredient_id = ? AND stock_quantity >= ?
                    """)) {
                update.setDouble(1, required);
                update.setLong(2, total.ingredientId());
                update.setDouble(3, required);
                if (update.executeUpdate() != 1) {
                    throw new IllegalStateException(
                            "Stock changed while placing the order: " + total.ingredientName());
                }
            }

            try (PreparedStatement movement = c.prepareStatement("""
                    INSERT INTO ingredient_movements
                        (ingredient_id, order_id, quantity_change, reason)
                    VALUES (?, ?, ?, 'ORDER_USAGE')
                    """)) {
                movement.setLong(1, total.ingredientId());
                movement.setString(2, orderId);
                movement.setDouble(3, -required);
                movement.executeUpdate();
            }
        }
    }

    private double findIngredientStock(Connection c, long ingredientId)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT stock_quantity FROM ingredients WHERE ingredient_id = ?")) {
            ps.setLong(1, ingredientId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("Ingredient inventory record was not found: " + ingredientId);
                }
                return rs.getDouble("stock_quantity");
            }
        }
    }

    private static final class OrderLineBuilder {
        private final String menuItemName;
        private final int quantity;
        private final List<RecursiveIngredientCalculator.IngredientUse> ingredients =
                new ArrayList<>();

        private OrderLineBuilder(String menuItemName, int quantity) {
            this.menuItemName = menuItemName;
            this.quantity = quantity;
        }

        private RecursiveIngredientCalculator.OrderedMenuItem toOrderedMenuItem() {
            return new RecursiveIngredientCalculator.OrderedMenuItem(
                    menuItemName, quantity, ingredients);
        }
    }

    public List<String> findActiveQueue() throws SQLException {
        return findOrders(
                "WHERE o.status IN ('PENDING','PREPARING')",
                "ORDER BY o.ordered_at ASC, o.rowid ASC");
    }

    public List<String> findKitchenQueue() throws SQLException {
        List<String> rows = new ArrayList<>();
        String sql = """
                SELECT o.order_id, o.customer_name, o.status, o.ordered_at,
                       COALESCE((
                           SELECT GROUP_CONCAT(
                               mi.name || ' x' || oi.quantity, ', ')
                           FROM order_items oi
                           JOIN menu_items mi ON mi.menu_item_id = oi.menu_item_id
                           WHERE oi.order_id = o.order_id
                       ), '') AS items
                FROM restaurant_orders o
                WHERE o.status IN ('PENDING','PREPARING')
                ORDER BY o.ordered_at ASC, o.rowid ASC
                """;

        try (Connection c = Database.connect();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rows.add(rs.getString("order_id") + "  |  "
                        + rs.getString("customer_name") + "  |  "
                        + rs.getString("status") + "  |  "
                        + rs.getString("items") + "  |  "
                        + rs.getString("ordered_at"));
            }
        }
        return rows;
    }

    public List<String> findOrderHistory() throws SQLException {
        return findOrders("", "ORDER BY o.ordered_at DESC, o.rowid DESC LIMIT 100");
    }

    private List<String> findOrders(String whereClause, String orderClause)
            throws SQLException {

        String sql = """
                SELECT o.order_id,
                       o.customer_name,
                       o.status,
                       o.ordered_at,
                       COALESCE(SUM(oi.quantity * oi.unit_price_cents), 0) AS total,
                       COALESCE((
                           SELECT GROUP_CONCAT(mi.name || ' x' || oi2.quantity, ', ')
                           FROM order_items oi2
                           JOIN menu_items mi ON mi.menu_item_id = oi2.menu_item_id
                           WHERE oi2.order_id = o.order_id
                       ), '') AS items
                FROM restaurant_orders o
                LEFT JOIN order_items oi ON oi.order_id = o.order_id
                """ + whereClause + """
                GROUP BY o.order_id
                """ + orderClause;

        List<String> rows = new ArrayList<>();
        try (Connection c = Database.connect();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String id = rs.getString("order_id");
                int prepMinutes = estimatePreparationMinutes(id);

                rows.add(id + "  |  "
                        + rs.getString("customer_name") + "  |  "
                        + rs.getString("status") + "  |  ₱"
                        + Money.formatCents(rs.getLong("total"))
                        + "  |  " + rs.getString("items")
                        + "  |  Est. prep: " + prepMinutes
                        + " min  |  " + rs.getString("ordered_at"));
            }
        }
        return rows;
    }

    /** Cashier-facing list: order identifier, customer, status, and total only. */
    public List<String> findCashierTotals() throws SQLException {
        return reportRepository.findCashierTotals();
    }

    /** Completed kitchen orders that have not yet been served to the customer. */
    public List<String> findOrdersReadyToServe() throws SQLException {
        List<String> rows = new ArrayList<>();
        String sql = """
                SELECT o.order_id, o.customer_name,
                       COALESCE((
                           SELECT GROUP_CONCAT(mi.name || ' x' || oi.quantity, ', ')
                           FROM order_items oi
                           JOIN menu_items mi ON mi.menu_item_id = oi.menu_item_id
                           WHERE oi.order_id = o.order_id
                       ), '') AS items,
                       COALESCE(SUM(oi.quantity * oi.unit_price_cents), 0) AS total
                FROM restaurant_orders o
                LEFT JOIN order_items oi ON oi.order_id = o.order_id
                WHERE o.status = 'COMPLETED'
                  AND o.service_status = 'AWAITING_SERVICE'
                GROUP BY o.order_id
                ORDER BY o.ordered_at ASC, o.rowid ASC
                """;
        try (Connection c = Database.connect();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rows.add(rs.getString("order_id") + "  |  "
                        + rs.getString("customer_name") + "  |  READY TO SERVE  |  "
                        + rs.getString("items") + "  |  ₱"
                        + Money.formatCents(rs.getLong("total")));
            }
        }
        return rows;
    }

    /** Records that payment has been received without changing the kitchen order status. */
    public void markOrderPaid(String orderId) throws SQLException {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("Select a valid order first.");
        }
        try (Connection c = Database.connect();
             PreparedStatement ps = c.prepareStatement("""
                     UPDATE restaurant_orders
                     SET payment_status = 'PAID', paid_at = datetime('now','localtime')
                     WHERE order_id = ?
                       AND payment_status = 'UNPAID'
                       AND status <> 'CANCELLED'
                     """)) {
            ps.setString(1, orderId);
            if (ps.executeUpdate() == 0) {
                throw new IllegalStateException(paymentUpdateFailure(c, orderId));
            }
        }
    }

    /** Records waiter service only after the kitchen has marked the order completed. */
    public void markOrderServed(String orderId) throws SQLException {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("Select a valid order first.");
        }
        try (Connection c = Database.connect();
             PreparedStatement ps = c.prepareStatement("""
                     UPDATE restaurant_orders
                     SET service_status = 'SERVED', served_at = datetime('now','localtime')
                     WHERE order_id = ?
                       AND status = 'COMPLETED'
                       AND service_status = 'AWAITING_SERVICE'
                     """)) {
            ps.setString(1, orderId);
            if (ps.executeUpdate() == 0) {
                throw new IllegalStateException(serviceUpdateFailure(c, orderId));
            }
        }
    }

    private String paymentUpdateFailure(Connection c, String orderId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT status, payment_status FROM restaurant_orders WHERE order_id = ?")) {
            ps.setString(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) { return "Order not found. Refresh the cashier list."; }
                if ("CANCELLED".equals(rs.getString("status"))) { return "Cancelled orders cannot be marked as paid."; }
                if ("PAID".equals(rs.getString("payment_status"))) { return "This order is already marked as paid."; }
            }
        }
        return "Payment status could not be updated. Refresh the cashier list and try again.";
    }

    private String serviceUpdateFailure(Connection c, String orderId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT status, service_status FROM restaurant_orders WHERE order_id = ?")) {
            ps.setString(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) { return "Order not found. Refresh the ready-to-serve list."; }
                if (!"COMPLETED".equals(rs.getString("status"))) { return "The kitchen must complete this order before it can be marked as served."; }
                if ("SERVED".equals(rs.getString("service_status"))) { return "This order is already marked as served."; }
            }
        }
        return "Service status could not be updated. Refresh the ready-to-serve list and try again.";
    }

    /** Orders that are within the configured warning window before their estimated completion time. */
    public List<String> findOrdersApproachingPreparationDeadline(int warningMinutes) throws SQLException {
        if (warningMinutes < 0) { throw new IllegalArgumentException("Warning minutes cannot be negative."); }
        List<String> rows = new ArrayList<>();
        String sql = """
                WITH order_timing AS (
                    SELECT o.order_id, o.customer_name, o.status,
                           (julianday('now','localtime') - julianday(o.ordered_at)) * 1440.0 AS age_minutes,
                           COALESCE((
                               SELECT SUM(mi.base_prep_minutes * oi.quantity)
                               FROM order_items oi
                               JOIN menu_items mi ON mi.menu_item_id = oi.menu_item_id
                               WHERE oi.order_id = o.order_id
                           ), 0) AS prep_minutes
                    FROM restaurant_orders o
                    WHERE o.status IN ('PENDING','PREPARING')
                )
                SELECT order_id, customer_name, status,
                       CAST(age_minutes AS INTEGER) AS elapsed_minutes,
                       prep_minutes,
                       MAX(0, CAST(prep_minutes - age_minutes AS INTEGER)) AS remaining_minutes
                FROM order_timing
                WHERE prep_minutes > 0
                  AND age_minutes >= MAX(0, prep_minutes - ?)
                  AND age_minutes < prep_minutes
                ORDER BY age_minutes DESC
                """;
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, warningMinutes);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(rs.getString("order_id") + " — " + rs.getString("customer_name")
                            + " — " + rs.getString("remaining_minutes") + " min until estimated completion"
                            + " (" + rs.getString("elapsed_minutes") + "/" + rs.getString("prep_minutes")
                            + " min elapsed; " + rs.getString("status") + ")");
                }
            }
        }
        return rows;
    }

    public List<String> findDelayedOrders(int graceMinutes) throws SQLException {
        List<String> rows = new ArrayList<>();
        String sql = """
                SELECT o.order_id, o.customer_name, o.status,
                       CAST((julianday('now','localtime')
                           - julianday(o.ordered_at)) * 1440 AS INTEGER) AS age,
                       COALESCE((
                           SELECT SUM(mi.base_prep_minutes * oi.quantity)
                           FROM order_items oi
                           JOIN menu_items mi ON mi.menu_item_id = oi.menu_item_id
                           WHERE oi.order_id = o.order_id
                       ), 0) AS prep
                FROM restaurant_orders o
                WHERE o.status IN ('PENDING','PREPARING')
                  AND (julianday('now','localtime') - julianday(o.ordered_at)) * 1440
                      > COALESCE((
                          SELECT SUM(mi.base_prep_minutes * oi.quantity)
                          FROM order_items oi
                          JOIN menu_items mi ON mi.menu_item_id = oi.menu_item_id
                          WHERE oi.order_id = o.order_id
                      ), 0) + ?
                ORDER BY o.ordered_at
                """;

        try (Connection c = Database.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, graceMinutes);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(rs.getString("order_id") + " — "
                            + rs.getString("customer_name") + " — "
                            + rs.getString("age") + " min elapsed (estimate "
                            + rs.getString("prep") + " min)");
                }
            }
        }
        return rows;
    }

    public void changeStatus(String orderId, String newStatus) throws SQLException {
        if (!List.of("PREPARING", "COMPLETED", "CANCELLED").contains(newStatus)) {
            throw new IllegalArgumentException("Unsupported order status.");
        }

        String allowed = switch (newStatus) {
            case "PREPARING" -> "PENDING";
            case "COMPLETED" -> "PREPARING";
            case "CANCELLED" -> "PENDING";
            default -> "";
        };

        try (Connection c = Database.connect()) {
            c.setAutoCommit(false);
            try {
                if ("PREPARING".equals(newStatus)) {
                    try (PreparedStatement first = c.prepareStatement("""
                            SELECT order_id
                            FROM restaurant_orders
                            WHERE status = 'PENDING'
                            ORDER BY ordered_at ASC, rowid ASC
                            LIMIT 1
                            """);
                         ResultSet rs = first.executeQuery()) {
                        if (rs.next() && !orderId.equals(rs.getString(1))) {
                            throw new IllegalStateException(
                                    "FIFO rule: prepare the oldest pending order first ("
                                    + rs.getString(1) + ").");
                        }
                    }
                }

                try (PreparedStatement ps = c.prepareStatement("""
                        UPDATE restaurant_orders
                        SET status = ?
                        WHERE order_id = ? AND status = ?
                        """)) {
                    ps.setString(1, newStatus);
                    ps.setString(2, orderId);
                    ps.setString(3, allowed);
                    if (ps.executeUpdate() == 0) {
                        throw new IllegalStateException(
                                "This order can no longer be changed. Refresh the queue.");
                    }
                }
                if ("CANCELLED".equals(newStatus)) {
                    restoreOrderIngredients(c, orderId);
                }
                c.commit();
            } catch (SQLException | RuntimeException ex) {
                rollback(c, ex);
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    private void restoreOrderIngredients(Connection c, String orderId)
            throws SQLException {
        // Restore only if this order has not already been restored.
        try (PreparedStatement already = c.prepareStatement("""
                SELECT COUNT(*)
                FROM ingredient_movements
                WHERE order_id = ? AND reason = 'ORDER_CANCEL_RESTORE'
                """)) {
            already.setString(1, orderId);
            try (ResultSet rs = already.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    return;
                }
            }
        }

        try (PreparedStatement ps = c.prepareStatement("""
                SELECT ingredient_id, -quantity_change AS quantity
                FROM ingredient_movements
                WHERE order_id = ? AND reason = 'ORDER_USAGE'
                """)) {
            ps.setString(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long ingredientId = rs.getLong("ingredient_id");
                    double quantity = rs.getDouble("quantity");

                    try (PreparedStatement update = c.prepareStatement("""
                            UPDATE ingredients
                            SET stock_quantity = stock_quantity + ?
                            WHERE ingredient_id = ?
                            """)) {
                        update.setDouble(1, quantity);
                        update.setLong(2, ingredientId);
                        update.executeUpdate();
                    }

                    try (PreparedStatement log = c.prepareStatement("""
                            INSERT INTO ingredient_movements
                                (ingredient_id, order_id, quantity_change, reason)
                            VALUES (?, ?, ?, 'ORDER_CANCEL_RESTORE')
                            """)) {
                        log.setLong(1, ingredientId);
                        log.setString(2, orderId);
                        log.setDouble(3, quantity);
                        log.executeUpdate();
                    }
                }
            }
        }
    }

    public List<String> findPendingOrderIdsOldestFirst() throws SQLException {
        List<String> ids = new ArrayList<>();
        try (Connection c = Database.connect();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT order_id
                     FROM restaurant_orders
                     WHERE status = 'PENDING'
                     ORDER BY ordered_at ASC, rowid ASC
                     """);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                ids.add(rs.getString(1));
            }
        }
        return ids;
    }

    public void cancelPendingOrder(String orderId) throws SQLException {
        try (Connection c = Database.connect()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement("""
                        UPDATE restaurant_orders
                        SET status = 'CANCELLED'
                        WHERE order_id = ? AND status = 'PENDING'
                        """)) {
                    ps.setString(1, orderId);
                    if (ps.executeUpdate() != 1) {
                        throw new IllegalStateException(
                                "Order is no longer pending and cannot be cancelled.");
                    }
                }
                restoreOrderIngredients(c, orderId);
                c.commit();
            } catch (SQLException | RuntimeException ex) {
                rollback(c, ex);
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    /** Stack-style rush action: cancel the most recently placed pending order. */
    public String cancelMostRecentPendingOrder() throws SQLException {
        try (Connection c = Database.connect()) {
            c.setAutoCommit(false);
            try {
                String latest = null;
                try (PreparedStatement ps = c.prepareStatement("""
                        SELECT order_id
                        FROM restaurant_orders
                        WHERE status = 'PENDING'
                        ORDER BY ordered_at DESC, rowid DESC
                        LIMIT 1
                        """);
                     ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        latest = rs.getString(1);
                    }
                }

                if (latest == null) {
                    c.rollback();
                    return null;
                }

                try (PreparedStatement ps = c.prepareStatement("""
                        UPDATE restaurant_orders
                        SET status = 'CANCELLED'
                        WHERE order_id = ? AND status = 'PENDING'
                        """)) {
                    ps.setString(1, latest);
                    if (ps.executeUpdate() != 1) {
                        throw new SQLException(
                                "The latest pending order changed before cancellation.");
                    }
                }

                restoreOrderIngredients(c, latest);
                c.commit();
                return latest;
            } catch (SQLException | RuntimeException ex) {
                rollback(c, ex);
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    /** Counts non-cancelled orders by local clock hour. */
    public Map<Integer, Integer> getOrdersByHour() throws SQLException {
        return reportRepository.getOrdersByHour();
    }

    public String getPeakOrderingHour() throws SQLException {
        return reportRepository.getPeakOrderingHour();
    }

    /** Processes the historical order log in one repository call. */
    public int countHistoricalOrders() throws SQLException {
        return reportRepository.countHistoricalOrders();
    }

    /** Aggregate historical orders and top sellers. */
    public String analyzeHistoricalOrderLog() throws SQLException {
        return reportRepository.analyzeHistoricalOrderLog();
    }

    /** Estimated preparation minutes based on saved menu-item estimates. */
    public int estimatePreparationMinutes(String orderId) throws SQLException {
        String sql = """
                SELECT COALESCE(SUM(mi.base_prep_minutes * oi.quantity), 0)
                FROM order_items oi
                JOIN menu_items mi ON mi.menu_item_id = oi.menu_item_id
                WHERE oi.order_id = ?
                """;

        try (Connection c = Database.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public List<BestSeller> findBestSellers(int limit) throws SQLException {
        return reportRepository.findBestSellers(limit).stream()
                .map(item -> new BestSeller(item.name(), item.type(), item.quantity()))
                .toList();
    }

    public long getTodaySalesCents() throws SQLException {
        return reportRepository.getTodaySalesCents();
    }

    /** Returns today's orders created by one signed-in staff account. */
    public List<String> findOrdersCreatedByToday(long staffUserId) throws SQLException {
        return reportRepository.findOrdersCreatedByToday(staffUserId);
    }

    /** Returns completed sales grouped by staff account for today's local date. */
    public Map<String, Long> getTodaySalesByStaffCents() throws SQLException {
        return reportRepository.getTodaySalesByStaffCents();
    }

    public int getTodayOrderCount() throws SQLException {
        return reportRepository.getTodayOrderCount();
    }
}
