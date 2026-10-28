package ph.uc.cpe314.dinesmart.model;

import java.time.LocalDateTime;
import java.util.UUID;

public final class RestaurantOrder {
    private final String orderId = UUID.randomUUID().toString();
    private final LocalDateTime orderedAt = LocalDateTime.now();
    private String status = "PENDING";

    public String getOrderId() { return orderId; }
    public LocalDateTime getOrderedAt() { return orderedAt; }
    public String getStatus() { return status; }
    public void markPreparing() {
        if (!"PENDING".equals(status)) throw new IllegalStateException("Only pending orders can start preparation.");
        status = "PREPARING";
    }
    public void cancel() {
        if (!"PENDING".equals(status)) throw new IllegalStateException("Only pending orders can be cancelled.");
        status = "CANCELLED";
    }
}
