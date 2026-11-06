package ph.uc.cpe314.dinesmart.service;

import ph.uc.cpe314.dinesmart.model.RestaurantOrder;
import java.util.ArrayDeque;
import java.util.Queue;

public final class KitchenQueue {
    private final Queue<RestaurantOrder> orders = new ArrayDeque<>();
    public void enqueue(RestaurantOrder order) {
        if (order == null) throw new IllegalArgumentException("Order cannot be null.");
        orders.offer(order);
    }
    public RestaurantOrder nextOrder() { return orders.poll(); }
    public int size() { return orders.size(); }
    public boolean isEmpty() { return orders.isEmpty(); }
}
