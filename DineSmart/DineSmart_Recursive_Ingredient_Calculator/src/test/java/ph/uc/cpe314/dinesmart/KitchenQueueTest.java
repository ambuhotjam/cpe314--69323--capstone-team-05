
package ph.uc.cpe314.dinesmart;

import org.junit.jupiter.api.Test;
import ph.uc.cpe314.dinesmart.model.RestaurantOrder;
import ph.uc.cpe314.dinesmart.service.KitchenQueue;

import static org.junit.jupiter.api.Assertions.*;

class KitchenQueueTest {

    @Test
    void processesOrdersFirstInFirstOut() {
        KitchenQueue queue = new KitchenQueue();

        RestaurantOrder first = new RestaurantOrder();
        RestaurantOrder second = new RestaurantOrder();

        queue.enqueue(first);
        queue.enqueue(second);

        assertSame(first, queue.nextOrder());
        assertSame(second, queue.nextOrder());
        assertTrue(queue.isEmpty());
    }

    @Test
    void emptyQueueReturnsNull() {
        assertNull(new KitchenQueue().nextOrder());
    }

    @Test
    void rejectsNullOrder() {
        KitchenQueue queue = new KitchenQueue();

        assertThrows(
            IllegalArgumentException.class,
            () -> queue.enqueue(null)
        );
    }
}
