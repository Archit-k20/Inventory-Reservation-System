package com.archit.inventory.order;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
@Tag(name = "Orders", description = "Customer order management APIs")
public class OrderController {
    private final OrderRepository orderRepository;

    public OrderController(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Operation(summary = "List orders", description = "Retrieve a paginated list of all customer orders.")
    @GetMapping
    public Page<OrderResponse> listOrders(@Parameter(hidden = true) Pageable pageable) {
        return orderRepository.findAll(pageable).map(OrderResponse::from);
    }
}
