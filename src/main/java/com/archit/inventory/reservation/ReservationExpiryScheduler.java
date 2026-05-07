package com.archit.inventory.reservation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReservationExpiryScheduler {
    private static final Logger log = LoggerFactory.getLogger(ReservationExpiryScheduler.class);

    private final ReservationService reservationService;

    public ReservationExpiryScheduler(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @Scheduled(fixedDelayString = "${inventory.reservations.expiry-rate-ms:30000}")
    public void expireReservations() {
        int expired = reservationService.expireReservations();
        if (expired > 0) {
            log.info("Expired {} reservations", expired);
        }
    }
}
