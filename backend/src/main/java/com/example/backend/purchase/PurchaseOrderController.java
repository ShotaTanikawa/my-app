package com.example.backend.purchase;

import com.example.backend.idempotency.IdempotencyService;
import com.example.backend.purchase.dto.CreatePurchaseOrderRequest;
import com.example.backend.purchase.dto.PurchaseOrderResponse;
import com.example.backend.purchase.dto.PurchaseOrderReceiptResponse;
import com.example.backend.purchase.dto.ReceivePurchaseOrderRequest;
import com.example.backend.purchase.dto.ReplenishmentSuggestionResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
/**
 * HTTPリクエストを受けてユースケースを公開するコントローラ。
 */

@RestController
@RequestMapping("/api/purchase-orders")
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;
    private final IdempotencyService idempotencyService;

    public PurchaseOrderController(
            PurchaseOrderService purchaseOrderService,
            IdempotencyService idempotencyService
    ) {
        this.purchaseOrderService = purchaseOrderService;
        this.idempotencyService = idempotencyService;
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

    @GetMapping("/{purchaseOrderId}/receipts")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER')")
    public List<PurchaseOrderReceiptResponse> getPurchaseOrderReceipts(
            @PathVariable Long purchaseOrderId,
            @RequestParam(required = false) String receivedBy,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "200") Integer limit
    ) {
        return purchaseOrderService.getPurchaseOrderReceipts(purchaseOrderId, receivedBy, from, to, limit);
    }

    @GetMapping(value = "/{purchaseOrderId}/receipts/export.csv", produces = "text/csv")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER')")
    public ResponseEntity<String> exportReceiptHistoryCsv(
            @PathVariable Long purchaseOrderId,
            @RequestParam(required = false) String receivedBy,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "2000") Integer limit
    ) {
        PurchaseOrderResponse purchaseOrder = purchaseOrderService.getPurchaseOrder(purchaseOrderId);
        List<PurchaseOrderReceiptResponse> receipts = purchaseOrderService.getPurchaseOrderReceipts(
                purchaseOrderId,
                receivedBy,
                from,
                to,
                limit
        );
        String csv = toReceiptCsv(purchaseOrder.orderNumber(), receipts);
        String filename = "purchase-order-" + sanitizeFilename(purchaseOrder.orderNumber()) + "-receipts.csv";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv);
    }

    @GetMapping("/suggestions")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER')")
    public List<ReplenishmentSuggestionResponse> getReplenishmentSuggestions() {
        return purchaseOrderService.getReplenishmentSuggestions();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public PurchaseOrderResponse createPurchaseOrder(
            @Valid @RequestBody CreatePurchaseOrderRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication authentication
    ) {
        return idempotencyService.execute(
                resolveActor(authentication),
                "purchase-orders.create",
                idempotencyKey,
                PurchaseOrderResponse.class,
                () -> purchaseOrderService.createPurchaseOrder(request)
        );
    }

    @PostMapping("/{purchaseOrderId}/receive")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public PurchaseOrderResponse receivePurchaseOrder(
            @PathVariable Long purchaseOrderId,
            @Valid @RequestBody(required = false) ReceivePurchaseOrderRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication authentication
    ) {
        return idempotencyService.execute(
                resolveActor(authentication),
                "purchase-orders." + purchaseOrderId + ".receive",
                idempotencyKey,
                PurchaseOrderResponse.class,
                () -> purchaseOrderService.receivePurchaseOrder(purchaseOrderId, request)
        );
    }

    @PostMapping("/{purchaseOrderId}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public PurchaseOrderResponse cancelPurchaseOrder(
            @PathVariable Long purchaseOrderId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication authentication
    ) {
        return idempotencyService.execute(
                resolveActor(authentication),
                "purchase-orders." + purchaseOrderId + ".cancel",
                idempotencyKey,
                PurchaseOrderResponse.class,
                () -> purchaseOrderService.cancelPurchaseOrder(purchaseOrderId)
        );
    }

    private String toReceiptCsv(String purchaseOrderNumber, List<PurchaseOrderReceiptResponse> receipts) {
        StringBuilder builder = new StringBuilder();
        builder.append("purchaseOrderNumber,receiptId,receivedAt,receivedBy,sku,productName,quantity\n");

        for (PurchaseOrderReceiptResponse receipt : receipts) {
            for (var item : receipt.items()) {
                builder.append(escapeCsv(purchaseOrderNumber))
                        .append(',')
                        .append(escapeCsv(receipt.id() == null ? "" : receipt.id().toString()))
                        .append(',')
                        .append(escapeCsv(
                                receipt.receivedAt() == null
                                        ? ""
                                        : receipt.receivedAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                        ))
                        .append(',')
                        .append(escapeCsv(receipt.receivedBy()))
                        .append(',')
                        .append(escapeCsv(item.sku()))
                        .append(',')
                        .append(escapeCsv(item.productName()))
                        .append(',')
                        .append(escapeCsv(item.quantity() == null ? "" : item.quantity().toString()))
                        .append('\n');
            }
        }

        return builder.toString();
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "\"\"";
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private String sanitizeFilename(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private String resolveActor(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return "anonymous";
        }
        return authentication.getName();
    }
}
