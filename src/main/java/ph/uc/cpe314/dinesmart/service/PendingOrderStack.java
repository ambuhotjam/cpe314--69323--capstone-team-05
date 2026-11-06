package ph.uc.cpe314.dinesmart.service;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.Optional;

/** LIFO stack of pending persistent order IDs; newest pending order is on top. */
public final class PendingOrderStack {
    private final Deque<String> orderIds = new ArrayDeque<>();

    public void rebuild(Collection<String> oldestToNewest) {
        orderIds.clear();
        if (oldestToNewest != null) for (String id : oldestToNewest) {
            if (id != null && !id.isBlank()) orderIds.push(id);
        }
    }

    public Optional<String> popMostRecent() {
        return orderIds.isEmpty() ? Optional.empty() : Optional.of(orderIds.pop());
    }

    public int size() { return orderIds.size(); }
    public boolean isEmpty() { return orderIds.isEmpty(); }
}
