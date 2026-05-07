package com.archit.inventory.event;

import com.archit.inventory.reservation.ReservationService;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OrderEventListenerTest {

    @Test
    void orderFailedEventReleasesReservationThroughDedicatedEventPath() {
        ReservationService reservationService = mock(ReservationService.class);
        OrderEventListener listener = new OrderEventListener(reservationService);
        UUID reservationId = UUID.randomUUID();

        listener.handleOrderFailed(new OrderFailedEvent(reservationId, "payment_failed"));

        verify(reservationService).releaseAfterOrderFailed(reservationId);
    }
}
