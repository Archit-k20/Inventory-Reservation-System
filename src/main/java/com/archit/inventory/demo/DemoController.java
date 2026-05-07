package com.archit.inventory.demo;

import com.archit.inventory.event.InventoryEventPublisher;
import com.archit.inventory.event.OrderFailedEvent;
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

    public DemoController(InventoryEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Operation(summary = "Simulate failed order", description = "Publishes an order.failed Kafka event for dashboard demos.")
    @PostMapping("/orders/{reservationId}/fail")
    public DemoOrderFailureResponse failOrder(
            @PathVariable UUID reservationId,
            @RequestBody(required = false) DemoOrderFailureRequest request
    ) {
        String reason = request == null || request.reason() == null || request.reason().isBlank()
                ? "demo_order_failed"
                : request.reason().trim();
        eventPublisher.publishOrderFailed(new OrderFailedEvent(reservationId, reason));
        return new DemoOrderFailureResponse(reservationId, "order.failed", reason, true);
    }
}
