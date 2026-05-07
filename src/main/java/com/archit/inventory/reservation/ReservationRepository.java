package com.archit.inventory.reservation;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    @Query("select r from Reservation r join fetch r.product where r.reservationId = :reservationId")
    Optional<Reservation> findByReservationId(UUID reservationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reservation r join fetch r.product where r.reservationId = :reservationId")
    Optional<Reservation> findByReservationIdForUpdate(UUID reservationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reservation r join fetch r.product where r.status = com.archit.inventory.reservation.ReservationStatus.ACTIVE and r.expiresAt <= :now order by r.expiresAt asc")
    List<Reservation> findExpiredActiveForUpdate(Instant now, Pageable pageable);
}
