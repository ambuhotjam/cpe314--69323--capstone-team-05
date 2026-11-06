package ph.uc.cpe314.dinesmart.service;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import ph.uc.cpe314.dinesmart.model.RestaurantOrder;

class OrderHistoryStackTest {

    @Test
    void cancelsMostRecentlyRecordedPendingOrder() {
        OrderHistoryStack history = new OrderHistoryStack();
        RestaurantOrder olderOrder = new RestaurantOrder();
        RestaurantOrder newerOrder = new RestaurantOrder();

        history.recordPending(olderOrder);
        history.recordPending(newerOrder);

        assertSame(newerOrder, history.cancelLatestPending().orElseThrow());
        assertEquals("CANCELLED", newerOrder.getStatus());
        assertEquals("PENDING", olderOrder.getStatus());
    }

    @Test
    void skipsOrdersThatAreNoLongerPending() {
        OrderHistoryStack history = new OrderHistoryStack();
        RestaurantOrder olderPendingOrder = new RestaurantOrder();
        RestaurantOrder newerPreparingOrder = new RestaurantOrder();
        newerPreparingOrder.markPreparing();

        history.recordPending(olderPendingOrder);
        history.recordPending(newerPreparingOrder);

        assertSame(olderPendingOrder, history.cancelLatestPending().orElseThrow());
        assertEquals("CANCELLED", olderPendingOrder.getStatus());
        assertEquals("PREPARING", newerPreparingOrder.getStatus());
    }

    @Test
    void returnsEmptyWhenThereAreNoPendingOrders() {
        assertTrue(new OrderHistoryStack().cancelLatestPending().isEmpty());
    }

    @Test
    void rejectsNullOrder() {
        OrderHistoryStack history = new OrderHistoryStack();
        assertThrows(IllegalArgumentException.class, () -> history.recordPending(null));
    }
}
