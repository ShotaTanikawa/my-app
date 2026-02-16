package com.example.backend.purchase;

import com.example.backend.audit.AuditLogService;
import com.example.backend.common.BusinessRuleException;
import com.example.backend.common.ResourceNotFoundException;
import com.example.backend.inventory.Inventory;
import com.example.backend.inventory.InventoryRepository;
import com.example.backend.product.Product;
import com.example.backend.product.ProductRepository;
import com.example.backend.purchase.dto.CreatePurchaseOrderItemRequest;
import com.example.backend.purchase.dto.CreatePurchaseOrderRequest;
import com.example.backend.purchase.dto.PurchaseOrderItemResponse;
import com.example.backend.purchase.dto.PurchaseOrderResponse;
import com.example.backend.purchase.dto.ReplenishmentSuggestionResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class PurchaseOrderService {

    private static final DateTimeFormatter ORDER_NUMBER_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final AuditLogService auditLogService;

    public PurchaseOrderService(
            PurchaseOrderRepository purchaseOrderRepository,
            ProductRepository productRepository,
            InventoryRepository inventoryRepository,
            AuditLogService auditLogService
    ) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<PurchaseOrderResponse> getPurchaseOrders() {
        return purchaseOrderRepository.findAllDetailed().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PurchaseOrderResponse getPurchaseOrder(Long purchaseOrderId) {
        return toResponse(findPurchaseOrderDetailedById(purchaseOrderId));
    }

    @Transactional(readOnly = true)
    public List<ReplenishmentSuggestionResponse> getReplenishmentSuggestions() {
        // 再発注条件に一致した商品のみを補充提案として返す。
        return inventoryRepository.findAllWithProduct().stream()
                .map(this::toSuggestion)
                .filter(Objects::nonNull)
                .sorted(
                        Comparator.comparingInt(ReplenishmentSuggestionResponse::shortageQuantity).reversed()
                                .thenComparingInt(ReplenishmentSuggestionResponse::availableQuantity)
                )
                .toList();
    }

    @Transactional
    public PurchaseOrderResponse createPurchaseOrder(CreatePurchaseOrderRequest request) {
        PurchaseOrder order = new PurchaseOrder();
        order.setOrderNumber(generateOrderNumber());
        order.setSupplierName(request.supplierName().trim());
        order.setNote(normalizeNote(request.note()));
        order.setStatus(PurchaseOrderStatus.ORDERED);

        int totalQuantity = 0;
        for (CreatePurchaseOrderItemRequest itemRequest : request.items()) {
            Product product = productRepository.findById(itemRequest.productId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + itemRequest.productId()));

            PurchaseOrderItem item = new PurchaseOrderItem();
            item.setProduct(product);
            item.setQuantity(itemRequest.quantity());
            item.setUnitCost(itemRequest.unitCost());
            order.addItem(item);
            totalQuantity += itemRequest.quantity();
        }

        PurchaseOrder savedOrder = purchaseOrderRepository.save(order);
        // 発注作成時点では在庫は増えず、入荷時に在庫へ反映する。
        auditLogService.log(
                "PURCHASE_ORDER_CREATE",
                "PURCHASE_ORDER",
                savedOrder.getId().toString(),
                "orderNumber=" + savedOrder.getOrderNumber()
                        + ", supplier=" + savedOrder.getSupplierName()
                        + ", itemCount=" + savedOrder.getItems().size()
                        + ", totalQuantity=" + totalQuantity
        );

        return toResponse(savedOrder);
    }

    @Transactional
    public PurchaseOrderResponse receivePurchaseOrder(Long purchaseOrderId) {
        PurchaseOrder order = findPurchaseOrderDetailedById(purchaseOrderId);
        if (order.getStatus() != PurchaseOrderStatus.ORDERED) {
            throw new BusinessRuleException(
                    "Only ORDERED purchase orders can be received. Current status: " + order.getStatus()
            );
        }

        int totalReceivedQuantity = 0;
        for (PurchaseOrderItem item : order.getItems()) {
            Inventory inventory = inventoryRepository.findByProductIdForUpdate(item.getProduct().getId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Inventory not found for product: " + item.getProduct().getId()
                    ));

            // 入荷時は販売可能在庫へ直接加算する。
            inventory.setAvailableQuantity(inventory.getAvailableQuantity() + item.getQuantity());
            totalReceivedQuantity += item.getQuantity();
        }

        order.setStatus(PurchaseOrderStatus.RECEIVED);
        order.setReceivedAt(OffsetDateTime.now());
        auditLogService.log(
                "PURCHASE_ORDER_RECEIVE",
                "PURCHASE_ORDER",
                order.getId().toString(),
                "orderNumber=" + order.getOrderNumber() + ", receivedQuantity=" + totalReceivedQuantity
        );
        return toResponse(order);
    }

    @Transactional
    public PurchaseOrderResponse cancelPurchaseOrder(Long purchaseOrderId) {
        PurchaseOrder order = findPurchaseOrderDetailedById(purchaseOrderId);
        if (order.getStatus() != PurchaseOrderStatus.ORDERED) {
            throw new BusinessRuleException(
                    "Only ORDERED purchase orders can be cancelled. Current status: " + order.getStatus()
            );
        }

        order.setStatus(PurchaseOrderStatus.CANCELLED);
        auditLogService.log(
                "PURCHASE_ORDER_CANCEL",
                "PURCHASE_ORDER",
                order.getId().toString(),
                "orderNumber=" + order.getOrderNumber()
        );
        return toResponse(order);
    }

    private PurchaseOrder findPurchaseOrderDetailedById(Long purchaseOrderId) {
        return purchaseOrderRepository.findDetailedById(purchaseOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase order not found: " + purchaseOrderId));
    }

    private String generateOrderNumber() {
        String orderNumber;
        do {
            String timestamp = LocalDateTime.now(ZoneOffset.UTC).format(ORDER_NUMBER_DATE_FORMATTER);
            int random = ThreadLocalRandom.current().nextInt(1000, 10_000);
            orderNumber = "PO-" + timestamp + "-" + random;
        } while (purchaseOrderRepository.existsByOrderNumber(orderNumber));

        return orderNumber;
    }

    private String normalizeNote(String note) {
        if (note == null) {
            return null;
        }
        String trimmed = note.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private ReplenishmentSuggestionResponse toSuggestion(Inventory inventory) {
        Product product = inventory.getProduct();
        int reorderPoint = normalizeReorderValue(product.getReorderPoint());
        int reorderQuantity = normalizeReorderValue(product.getReorderQuantity());
        int availableQuantity = inventory.getAvailableQuantity() == null ? 0 : inventory.getAvailableQuantity();
        int reservedQuantity = inventory.getReservedQuantity() == null ? 0 : inventory.getReservedQuantity();

        if (reorderQuantity <= 0 || availableQuantity > reorderPoint) {
            return null;
        }

        // 1ロット以上を発注しつつ、しきい値割れを埋める数量を提案する。
        int shortage = Math.max(0, reorderPoint - availableQuantity);
        int suggestedQuantity = Math.max(reorderQuantity, shortage + reorderQuantity);

        return new ReplenishmentSuggestionResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                availableQuantity,
                reservedQuantity,
                reorderPoint,
                reorderQuantity,
                shortage,
                suggestedQuantity
        );
    }

    private int normalizeReorderValue(Integer value) {
        if (value == null || value < 0) {
            return 0;
        }
        return value;
    }

    private PurchaseOrderResponse toResponse(PurchaseOrder order) {
        List<PurchaseOrderItemResponse> items = order.getItems().stream()
                .map(item -> new PurchaseOrderItemResponse(
                        item.getProduct().getId(),
                        item.getProduct().getSku(),
                        item.getProduct().getName(),
                        item.getQuantity(),
                        item.getUnitCost()
                ))
                .toList();

        return new PurchaseOrderResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getSupplierName(),
                order.getNote(),
                order.getStatus().name(),
                order.getCreatedAt(),
                order.getReceivedAt(),
                items
        );
    }
}
