package com.example.backend.purchase;

import com.example.backend.purchase.dto.CreatePurchaseOrderRequest;
import com.example.backend.purchase.dto.PurchaseOrderResponse;
import com.example.backend.purchase.dto.ReplenishmentSuggestionResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
/**
 * HTTPリクエストを受けてユースケースを公開するコントローラ。
 */

@RestController
@RequestMapping("/api/purchase-orders")
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;

    public PurchaseOrderController(PurchaseOrderService purchaseOrderService) {
        this.purchaseOrderService = purchaseOrderService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER')")
    public List<PurchaseOrderResponse> getPurchaseOrders() {
        return purchaseOrderService.getPurchaseOrders();
    }

    @GetMapping("/{purchaseOrderId}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER')")
    public PurchaseOrderResponse getPurchaseOrder(@PathVariable Long purchaseOrderId) {
        return purchaseOrderService.getPurchaseOrder(purchaseOrderId);
    }

    @GetMapping("/suggestions")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER')")
    public List<ReplenishmentSuggestionResponse> getReplenishmentSuggestions() {
        return purchaseOrderService.getReplenishmentSuggestions();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public PurchaseOrderResponse createPurchaseOrder(@Valid @RequestBody CreatePurchaseOrderRequest request) {
        return purchaseOrderService.createPurchaseOrder(request);
    }

    @PostMapping("/{purchaseOrderId}/receive")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public PurchaseOrderResponse receivePurchaseOrder(@PathVariable Long purchaseOrderId) {
        return purchaseOrderService.receivePurchaseOrder(purchaseOrderId);
    }

    @PostMapping("/{purchaseOrderId}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public PurchaseOrderResponse cancelPurchaseOrder(@PathVariable Long purchaseOrderId) {
        return purchaseOrderService.cancelPurchaseOrder(purchaseOrderId);
    }
}
