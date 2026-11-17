package ph.uc.cpe314.dinesmart.service;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class PersistentKitchenQueueTest {
    @Test void nextOrderIsOldestFirst() {
        PersistentKitchenQueue queue = new PersistentKitchenQueue();
        queue.rebuild(List.of("DS-FIRST", "DS-SECOND", "DS-THIRD"));
        assertEquals("DS-FIRST", queue.nextOrderId().orElseThrow());
    }

    @Test void emptyQueueHasNoNextOrder() {
        assertTrue(new PersistentKitchenQueue().nextOrderId().isEmpty());
    }

    @Test void rebuildReplacesOldQueue() {
        PersistentKitchenQueue queue = new PersistentKitchenQueue();
        queue.rebuild(List.of("OLD"));
        queue.rebuild(List.of("NEW"));
        assertEquals(1, queue.size());
        assertEquals("NEW", queue.nextOrderId().orElseThrow());
    }
}
