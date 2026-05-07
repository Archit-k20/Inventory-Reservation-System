package com.archit.inventory.reservation;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReservationExpirySchedulerTest {
    private final ReservationService reservationService = mock(ReservationService.class);
    private final ReservationExpiryScheduler scheduler = new ReservationExpiryScheduler(reservationService);

    @Test
    void expireReservationsDelegatesToService() {
        when(reservationService.expireReservations()).thenReturn(3);

        scheduler.expireReservations();

        verify(reservationService).expireReservations();
    }
}
