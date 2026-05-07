package com.archit.inventory.order;

import java.time.Instant;
import java.util.UUID;

public record OrderResponse(
        UUID orderId,
        UUID reservationId,
        UUID productId,
        String userId,
        long quantity,
        Instant createdAt
) {
    public static OrderResponse from(CustomerOrder order) {
        return new OrderResponse(
                order.getOrderId(),
                order.getReservation().getReservationId(),
                order.getProduct().getId(),
                order.getUserId(),
                order.getQuantity(),
                order.getCreatedAt()
        );
    }
}
