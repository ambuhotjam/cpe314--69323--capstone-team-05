package ph.uc.cpe314.dinesmart.controller;
import java.sql.SQLException;
import java.util.Map;
import ph.uc.cpe314.dinesmart.data.OrderRepository;
import ph.uc.cpe314.dinesmart.service.StaffPermissions;
import ph.uc.cpe314.dinesmart.service.StaffPermissions.Action;

/** Coordinates order actions and enforces role permissions before repository calls. */
public final class OrderController {
    private final OrderRepository repository;

    public OrderController(OrderRepository repository) {
        this.repository = java.util.Objects.requireNonNull(repository);
    }

    public String placeOrder(String role, long staffUserId, String customerName,
                             String tableLabel, Map<String, Integer> items)
            throws SQLException {
        require(role, Action.CREATE_ORDER);
        return repository.placeOrder(customerName, items, tableLabel, staffUserId);
    }

    public void markPaid(String role, String orderId) throws SQLException {
        require(role, Action.MARK_ORDER_PAID);
        repository.markOrderPaid(orderId);
    }

    public void markServed(String role, String orderId) throws SQLException {
        require(role, Action.MARK_ORDER_SERVED);
        repository.markOrderServed(orderId);
    }

    public void changeStatus(String role, String orderId, String status)
            throws SQLException {
        Action action = switch (status) {
            case "PREPARING" -> Action.PREPARE_ORDER;
            case "COMPLETED" -> Action.COMPLETE_ORDER;
            case "CANCELLED" -> Action.CANCEL_ORDER;
            default -> throw new IllegalArgumentException("Unsupported order status.");
        };
        require(role, action);
        repository.changeStatus(orderId, status);
    }

    public void cancelPendingOrder(String role, String orderId) throws SQLException {
        require(role, Action.CANCEL_ORDER);
        repository.cancelPendingOrder(orderId);
    }

    private static void require(String role, Action action) {
        if (!StaffPermissions.can(role, action)) {
            throw new SecurityException("Your staff role is not allowed to perform: "
                    + action.name().toLowerCase().replace('_', ' ') + ".");
        }
    }
}
