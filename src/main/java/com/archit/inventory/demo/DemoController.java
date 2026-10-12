package com.archit.inventory.demo;

import com.archit.inventory.event.InventoryEventPublisher;
import com.archit.inventory.event.OrderFailedEvent;
import com.archit.inventory.common.ConflictException;
import com.archit.inventory.common.NotFoundException;
import com.archit.inventory.reservation.ReservationRepository;
import com.archit.inventory.reservation.ReservationStatus;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.transaction.annotation.Transactional;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/demo")
@Tag(name = "Demo", description = "Local demo helpers for showcasing asynchronous flows")
public class DemoController {
    private final InventoryEventPublisher eventPublisher;
    private final ReservationRepository reservations;

    public DemoController(InventoryEventPublisher eventPublisher, ReservationRepository reservations) {
        this.eventPublisher = eventPublisher;
        this.reservations = reservations;
    }

    @Operation(summary = "Simulate failed order", description = "Durably queues an order.failed event; delivery is asynchronous.")
    @PostMapping("/orders/{reservationId}/fail")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Transactional
    public DemoOrderFailureResponse failOrder(
            @PathVariable UUID reservationId,
            @Valid @RequestBody(required = false) DemoOrderFailureRequest request
    ) {
        var reservation = reservations.findByReservationId(reservationId)
                .orElseThrow(() -> new NotFoundException("Reservation not found"));
        if (reservation.getStatus() != ReservationStatus.ACTIVE) {
            throw new ConflictException("Reservation is not active");
        }
        String reason = request == null || request.reason() == null || request.reason().isBlank()
                ? "demo_order_failed"
                : request.reason().trim();
        UUID eventId = eventPublisher.publishOrderFailed(new OrderFailedEvent(reservationId, reason));
        return new DemoOrderFailureResponse(reservationId, "order.failed", reason, false, true, eventId);
    }
}
