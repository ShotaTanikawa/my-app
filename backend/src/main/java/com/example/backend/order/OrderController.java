package com.example.backend.order;

import com.example.backend.idempotency.IdempotencyService;
import com.example.backend.order.dto.CreateSalesOrderRequest;
import com.example.backend.order.dto.SalesOrderResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
/**
 * HTTPリクエストを受けてユースケースを公開するコントローラ。
 */

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final IdempotencyService idempotencyService;

    public OrderController(OrderService orderService, IdempotencyService idempotencyService) {
        this.orderService = orderService;
        this.idempotencyService = idempotencyService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER')")
    public List<SalesOrderResponse> getOrders() {
        return orderService.getOrders();
    }

    @GetMapping("/{orderId}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER')")
    public SalesOrderResponse getOrder(@PathVariable Long orderId) {
        return orderService.getOrder(orderId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public SalesOrderResponse createOrder(
            @Valid @RequestBody CreateSalesOrderRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication authentication
    ) {
        return idempotencyService.execute(
                resolveActor(authentication),
                "orders.create",
                idempotencyKey,
                SalesOrderResponse.class,
                () -> orderService.createOrder(request)
        );
    }

    @PostMapping("/{orderId}/confirm")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public SalesOrderResponse confirmOrder(
            @PathVariable Long orderId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication authentication
    ) {
        return idempotencyService.execute(
                resolveActor(authentication),
                "orders." + orderId + ".confirm",
                idempotencyKey,
                SalesOrderResponse.class,
                () -> orderService.confirmOrder(orderId)
        );
    }

    @PostMapping("/{orderId}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public SalesOrderResponse cancelOrder(
            @PathVariable Long orderId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication authentication
    ) {
        return idempotencyService.execute(
                resolveActor(authentication),
                "orders." + orderId + ".cancel",
                idempotencyKey,
                SalesOrderResponse.class,
                () -> orderService.cancelOrder(orderId)
        );
    }

    private String resolveActor(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return "anonymous";
        }
        return authentication.getName();
    }
}
