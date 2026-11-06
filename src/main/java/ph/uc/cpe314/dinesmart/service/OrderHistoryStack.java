package ph.uc.cpe314.dinesmart.service;

import ph.uc.cpe314.dinesmart.model.RestaurantOrder;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

public final class OrderHistoryStack {
    private final Deque<RestaurantOrder> pendingOrders = new ArrayDeque<>();
    public void recordPending(RestaurantOrder order) {
        if (order == null) throw new IllegalArgumentException("Order cannot be null.");
        pendingOrders.push(order);
    }
    public Optional<RestaurantOrder> cancelLatestPending() {
        while (!pendingOrders.isEmpty()) {
            RestaurantOrder order = pendingOrders.pop();
            if ("PENDING".equals(order.getStatus())) {
                order.cancel();
                return Optional.of(order);
            }
        }
        return Optional.empty();
    }
}
