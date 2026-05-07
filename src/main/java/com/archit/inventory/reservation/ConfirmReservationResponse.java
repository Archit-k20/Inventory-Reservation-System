package com.archit.inventory.reservation;

import java.util.UUID;

public record ConfirmReservationResponse(
        UUID reservationId,
        UUID orderId,
        ReservationStatus status
) {
}
