package com.archit.inventory.reservation;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.UUID;

@RestController
@RequestMapping("/reservations")
@Tag(name = "Reservations", description = "Inventory reservation management APIs")
public class ReservationController {
    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @Operation(summary = "Create a new reservation", description = "Reserves product inventory for a specific duration.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponse reserve(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ReserveInventoryRequest request
    ) {
        return reservationService.reserve(request, idempotencyKey);
    }

    @Operation(summary = "Confirm a reservation", description = "Confirms a reservation and creates an order.")
    @PostMapping("/{id}/confirm")
    public ConfirmReservationResponse confirm(@PathVariable UUID id) {
        return reservationService.confirm(id);
    }

    @Operation(summary = "Release a reservation", description = "Releases reserved inventory back to available stock.")
    @PostMapping("/{id}/release")
    public ReservationResponse release(@PathVariable UUID id) {
        return reservationService.release(id);
    }

    @Operation(summary = "List reservations", description = "Retrieve a paginated list of all reservations.")
    @GetMapping
    public Page<ReservationResponse> listReservations(@Parameter(hidden = true) Pageable pageable) {
        return reservationService.getReservations(pageable);
    }
}
