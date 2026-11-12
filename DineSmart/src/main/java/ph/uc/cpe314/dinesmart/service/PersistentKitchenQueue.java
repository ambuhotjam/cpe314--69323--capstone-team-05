package ph.uc.cpe314.dinesmart.service;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Optional;
import java.util.Queue;

/** FIFO view of pending SQLite order IDs. Rebuilt from persisted order timestamps. */
public final class PersistentKitchenQueue {
    private final Queue<String> orderIds = new ArrayDeque<>();

    public void rebuild(Collection<String> oldestToNewest) {
        orderIds.clear();
        if (oldestToNewest != null) for (String id : oldestToNewest) {
            if (id != null && !id.isBlank()) { orderIds.offer(id); }
        }
    }

    public Optional<String> nextOrderId() { return Optional.ofNullable(orderIds.peek()); }
    public int size() { return orderIds.size(); }
    public boolean isEmpty() { return orderIds.isEmpty(); }
}
