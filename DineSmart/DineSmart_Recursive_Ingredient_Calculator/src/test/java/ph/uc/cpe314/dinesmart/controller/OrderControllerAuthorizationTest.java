package ph.uc.cpe314.dinesmart.controller;

import org.junit.jupiter.api.Test;
import ph.uc.cpe314.dinesmart.data.OrderRepository;

import static org.junit.jupiter.api.Assertions.*;

/** Confirms that order commands are rejected before reaching persistence for the wrong role. */
class OrderControllerAuthorizationTest {
    private final OrderController controller =
            new OrderController(new OrderRepository());

    @Test
    void waiterCannotMarkAnOrderPaidOrPrepareIt() {
        assertThrows(SecurityException.class,
                () -> controller.markPaid("WAITER", "ORDER-1"));
        assertThrows(SecurityException.class,
                () -> controller.changeStatus("WAITER", "ORDER-1", "PREPARING"));
    }

    @Test
    void cashierCannotCreateOrServeOrders() {
        assertThrows(SecurityException.class,
                () -> controller.placeOrder("CASHIER", 1L, "Guest",
                        "Table 1", java.util.Map.of("Pasta", 1)));
        assertThrows(SecurityException.class,
                () -> controller.markServed("CASHIER", "ORDER-1"));
    }

    @Test
    void kitchenCannotTakeCashierWaiterOrManagementActions() {
        assertThrows(SecurityException.class,
                () -> controller.markPaid("KITCHEN", "ORDER-1"));
        assertThrows(SecurityException.class,
                () -> controller.markServed("KITCHEN", "ORDER-1"));
        assertThrows(SecurityException.class,
                () -> controller.cancelPendingOrder("KITCHEN", "ORDER-1"));
    }
}
