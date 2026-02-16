package com.example.backend.purchase;

import com.example.backend.audit.AuditLogService;
import com.example.backend.common.BusinessRuleException;
import com.example.backend.common.ResourceNotFoundException;
import com.example.backend.inventory.Inventory;
import com.example.backend.inventory.InventoryRepository;
import com.example.backend.product.Product;
import com.example.backend.product.ProductRepository;
import com.example.backend.supplier.ProductSupplier;
import com.example.backend.supplier.ProductSupplierRepository;
import com.example.backend.supplier.Supplier;
import com.example.backend.supplier.SupplierRepository;
import com.example.backend.purchase.dto.CreatePurchaseOrderItemRequest;
import com.example.backend.purchase.dto.CreatePurchaseOrderRequest;
import com.example.backend.purchase.dto.PurchaseOrderItemResponse;
import com.example.backend.purchase.dto.PurchaseOrderReceiptItemResponse;
import com.example.backend.purchase.dto.PurchaseOrderReceiptResponse;
import com.example.backend.purchase.dto.PurchaseOrderResponse;
import com.example.backend.purchase.dto.ReceivePurchaseOrderItemRequest;
import com.example.backend.purchase.dto.ReceivePurchaseOrderRequest;
import com.example.backend.purchase.dto.ReplenishmentSuggestionResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class PurchaseOrderService {

    private static final DateTimeFormatter ORDER_NUMBER_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final SupplierRepository supplierRepository;
    private final ProductSupplierRepository productSupplierRepository;
    private final AuditLogService auditLogService;

    public PurchaseOrderService(
            PurchaseOrderRepository purchaseOrderRepository,
            ProductRepository productRepository,
            InventoryRepository inventoryRepository,
            SupplierRepository supplierRepository,
            ProductSupplierRepository productSupplierRepository,
            AuditLogService auditLogService
    ) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.supplierRepository = supplierRepository;
        this.productSupplierRepository = productSupplierRepository;
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
    public List<PurchaseOrderReceiptResponse> getPurchaseOrderReceipts(
            Long purchaseOrderId,
            String receivedBy,
            OffsetDateTime from,
            OffsetDateTime to,
            Integer limit
    ) {
        PurchaseOrder order = findPurchaseOrderDetailedById(purchaseOrderId);
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessRuleException("from must be less than or equal to to");
        }
        int safeLimit = normalizeReceiptLimit(limit);
        return toReceiptResponses(order, receivedBy, from, to, safeLimit);
    }

    @Transactional(readOnly = true)
    public List<ReplenishmentSuggestionResponse> getReplenishmentSuggestions() {
        List<Inventory> inventories = inventoryRepository.findAllWithProduct();
        List<Long> productIds = inventories.stream()
                .map(inventory -> inventory.getProduct().getId())
                .toList();
        Map<Long, List<ProductSupplier>> contractMap = loadContractMap(productIds);

        // 再発注条件に一致した商品のみを補充提案として返す。
        return inventories.stream()
                .map(inventory -> toSuggestion(inventory, contractMap.getOrDefault(inventory.getProduct().getId(), List.of())))
                .filter(Objects::nonNull)
                .sorted(
                        Comparator.comparingInt(ReplenishmentSuggestionResponse::shortageQuantity).reversed()
                                .thenComparingInt(ReplenishmentSuggestionResponse::availableQuantity)
                )
                .toList();
    }

    @Transactional
    public PurchaseOrderResponse createPurchaseOrder(CreatePurchaseOrderRequest request) {
        Supplier supplier = resolveSupplier(request.supplierId());
        String supplierName = resolveSupplierName(supplier, request.supplierName());

        PurchaseOrder order = new PurchaseOrder();
        order.setOrderNumber(generateOrderNumber());
        order.setSupplier(supplier);
        order.setSupplierName(supplierName);
        order.setNote(normalizeNote(request.note()));
        order.setStatus(PurchaseOrderStatus.ORDERED);

        int totalQuantity = 0;
        Set<Long> seenProductIds = new HashSet<>();
        for (CreatePurchaseOrderItemRequest itemRequest : request.items()) {
            if (!seenProductIds.add(itemRequest.productId())) {
                throw new BusinessRuleException("Duplicate product is not allowed in purchase order items");
            }
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
                        + ", supplierCode=" + (savedOrder.getSupplier() == null ? "-" : savedOrder.getSupplier().getCode())
                        + ", itemCount=" + savedOrder.getItems().size()
                        + ", totalQuantity=" + totalQuantity
        );

        return toResponse(savedOrder);
    }

    @Transactional
    public PurchaseOrderResponse receivePurchaseOrder(Long purchaseOrderId, ReceivePurchaseOrderRequest request) {
        PurchaseOrder order = findPurchaseOrderDetailedById(purchaseOrderId);
        if (order.getStatus() == PurchaseOrderStatus.RECEIVED || order.getStatus() == PurchaseOrderStatus.CANCELLED) {
            throw new BusinessRuleException(
                    "Only ORDERED or PARTIALLY_RECEIVED purchase orders can be received. Current status: "
                            + order.getStatus()
            );
        }

        Map<Long, PurchaseOrderItem> itemMap = new HashMap<>();
        for (PurchaseOrderItem item : order.getItems()) {
            PurchaseOrderItem previous = itemMap.put(item.getProduct().getId(), item);
            if (previous != null) {
                throw new BusinessRuleException(
                        "Duplicate product lines are not supported for receive: " + item.getProduct().getId()
                );
            }
        }

        Map<Long, Integer> receiveQuantities = resolveReceiveQuantities(order, itemMap, request);
        String receivedBy = resolveActorUsername();
        PurchaseOrderReceipt receipt = new PurchaseOrderReceipt();
        receipt.setReceivedBy(receivedBy);

        int totalReceivedQuantity = 0;
        for (Map.Entry<Long, Integer> entry : receiveQuantities.entrySet()) {
            Long productId = entry.getKey();
            int receiveQuantity = entry.getValue();
            PurchaseOrderItem item = itemMap.get(productId);
            int remainingQuantity = getRemainingQuantity(item);
            if (receiveQuantity > remainingQuantity) {
                throw new BusinessRuleException(
                        "Receive quantity exceeds remaining quantity for SKU "
                                + item.getProduct().getSku()
                                + ": remaining=" + remainingQuantity
                                + ", requested=" + receiveQuantity
                );
            }

            Inventory inventory = inventoryRepository.findByProductIdForUpdate(productId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Inventory not found for product: " + productId
                    ));

            // 入荷時は販売可能在庫へ直接加算する。
            inventory.setAvailableQuantity(inventory.getAvailableQuantity() + receiveQuantity);
            item.setReceivedQuantity(normalizeNonNegative(item.getReceivedQuantity(), 0) + receiveQuantity);

            PurchaseOrderReceiptItem receiptItem = new PurchaseOrderReceiptItem();
            receiptItem.setProduct(item.getProduct());
            receiptItem.setQuantity(receiveQuantity);
            receipt.addItem(receiptItem);

            totalReceivedQuantity += receiveQuantity;
        }
        order.addReceipt(receipt);

        int totalRemainingQuantity = calculateTotalRemainingQuantity(order.getItems());
        if (totalRemainingQuantity == 0) {
            order.setStatus(PurchaseOrderStatus.RECEIVED);
            order.setReceivedAt(OffsetDateTime.now());
        } else {
            order.setStatus(PurchaseOrderStatus.PARTIALLY_RECEIVED);
        }

        auditLogService.log(
                "PURCHASE_ORDER_RECEIVE",
                "PURCHASE_ORDER",
                order.getId().toString(),
                "orderNumber=" + order.getOrderNumber()
                        + ", receivedBy=" + receivedBy
                        + ", receivedQuantity=" + totalReceivedQuantity
                        + ", remainingQuantity=" + totalRemainingQuantity
                        + ", lineCount=" + receiveQuantities.size()
        );
        return toResponse(order);
    }

    @Transactional
    public PurchaseOrderResponse cancelPurchaseOrder(Long purchaseOrderId) {
        PurchaseOrder order = findPurchaseOrderDetailedById(purchaseOrderId);
        if (order.getStatus() == PurchaseOrderStatus.RECEIVED || order.getStatus() == PurchaseOrderStatus.CANCELLED) {
            throw new BusinessRuleException(
                    "Only ORDERED or PARTIALLY_RECEIVED purchase orders can be cancelled. Current status: "
                            + order.getStatus()
            );
        }

        int remainingQuantity = calculateTotalRemainingQuantity(order.getItems());
        int receivedQuantity = calculateTotalReceivedQuantity(order.getItems());
        order.setStatus(PurchaseOrderStatus.CANCELLED);
        auditLogService.log(
                "PURCHASE_ORDER_CANCEL",
                "PURCHASE_ORDER",
                order.getId().toString(),
                "orderNumber=" + order.getOrderNumber()
                        + ", receivedQuantity=" + receivedQuantity
                        + ", remainingQuantity=" + remainingQuantity
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

    private ReplenishmentSuggestionResponse toSuggestion(Inventory inventory, List<ProductSupplier> contracts) {
        Product product = inventory.getProduct();
        int reorderPoint = normalizeReorderValue(product.getReorderPoint());
        int reorderQuantity = normalizeReorderValue(product.getReorderQuantity());
        int availableQuantity = inventory.getAvailableQuantity() == null ? 0 : inventory.getAvailableQuantity();
        int reservedQuantity = inventory.getReservedQuantity() == null ? 0 : inventory.getReservedQuantity();

        if (reorderQuantity <= 0 || availableQuantity > reorderPoint) {
            return null;
        }

        SuggestedContract suggestedContract = selectSuggestedContract(contracts);
        int moq = suggestedContract == null ? 1 : suggestedContract.moq();
        int lotSize = suggestedContract == null ? 1 : suggestedContract.lotSize();

        // 1ロット以上を発注しつつ、しきい値割れを埋める数量を提案する。
        int shortage = Math.max(0, reorderPoint - availableQuantity);
        int suggestedQuantity = Math.max(reorderQuantity, shortage + reorderQuantity);
        suggestedQuantity = Math.max(suggestedQuantity, moq);
        if (lotSize > 1 && suggestedQuantity % lotSize != 0) {
            suggestedQuantity = ((suggestedQuantity / lotSize) + 1) * lotSize;
        }

        return new ReplenishmentSuggestionResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                availableQuantity,
                reservedQuantity,
                reorderPoint,
                reorderQuantity,
                shortage,
                suggestedQuantity,
                suggestedContract == null ? null : suggestedContract.supplierId(),
                suggestedContract == null ? null : suggestedContract.supplierCode(),
                suggestedContract == null ? null : suggestedContract.supplierName(),
                suggestedContract == null ? null : suggestedContract.unitCost(),
                suggestedContract == null ? null : suggestedContract.leadTimeDays(),
                moq,
                lotSize
        );
    }

    private Map<Long, List<ProductSupplier>> loadContractMap(List<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, List<ProductSupplier>> map = new LinkedHashMap<>();
        for (ProductSupplier contract : productSupplierRepository.findActiveByProductIds(productIds)) {
            map.computeIfAbsent(contract.getProduct().getId(), ignored -> new ArrayList<>()).add(contract);
        }
        return map;
    }

    private SuggestedContract selectSuggestedContract(List<ProductSupplier> contracts) {
        if (contracts == null || contracts.isEmpty()) {
            return null;
        }

        ProductSupplier selected = contracts.stream()
                .sorted(
                        Comparator.comparing(ProductSupplier::getPrimarySupplier, Comparator.reverseOrder())
                                .thenComparing(ProductSupplier::getUnitCost)
                )
                .findFirst()
                .orElse(null);
        if (selected == null) {
            return null;
        }

        Supplier supplier = selected.getSupplier();
        return new SuggestedContract(
                supplier.getId(),
                supplier.getCode(),
                supplier.getName(),
                selected.getUnitCost(),
                normalizeNonNegative(selected.getLeadTimeDays(), 0),
                normalizeAtLeast(selected.getMoq(), 1),
                normalizeAtLeast(selected.getLotSize(), 1)
        );
    }

    private Supplier resolveSupplier(Long supplierId) {
        if (supplierId == null) {
            return null;
        }

        Supplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier not found: " + supplierId));
        if (!Boolean.TRUE.equals(supplier.getActive())) {
            throw new BusinessRuleException("Supplier is inactive: " + supplier.getCode());
        }
        return supplier;
    }

    private String resolveSupplierName(Supplier supplier, String requestedSupplierName) {
        String normalizedRequested = normalizeSupplierName(requestedSupplierName);
        if (supplier != null) {
            return normalizedRequested == null ? supplier.getName() : normalizedRequested;
        }
        if (normalizedRequested == null) {
            throw new BusinessRuleException("supplierId or supplierName is required");
        }
        return normalizedRequested;
    }

    private String normalizeSupplierName(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private int normalizeReorderValue(Integer value) {
        if (value == null || value < 0) {
            return 0;
        }
        return value;
    }

    private int normalizeNonNegative(Integer value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        return Math.max(0, value);
    }

    private int normalizeAtLeast(Integer value, int min) {
        if (value == null) {
            return min;
        }
        return Math.max(min, value);
    }

    private Map<Long, Integer> resolveReceiveQuantities(
            PurchaseOrder order,
            Map<Long, PurchaseOrderItem> itemMap,
            ReceivePurchaseOrderRequest request
    ) {
        // リクエスト未指定時は、未入荷残数をすべて受領する。
        if (request == null || request.items() == null || request.items().isEmpty()) {
            Map<Long, Integer> fullReceive = new LinkedHashMap<>();
            for (PurchaseOrderItem item : order.getItems()) {
                int remainingQuantity = getRemainingQuantity(item);
                if (remainingQuantity > 0) {
                    fullReceive.put(item.getProduct().getId(), remainingQuantity);
                }
            }
            if (fullReceive.isEmpty()) {
                throw new BusinessRuleException("No remaining quantity to receive for this purchase order");
            }
            return fullReceive;
        }

        Map<Long, Integer> requested = new LinkedHashMap<>();
        for (ReceivePurchaseOrderItemRequest itemRequest : request.items()) {
            PurchaseOrderItem orderItem = itemMap.get(itemRequest.productId());
            if (orderItem == null) {
                throw new BusinessRuleException(
                        "Product is not included in purchase order: " + itemRequest.productId()
                );
            }
            requested.merge(itemRequest.productId(), itemRequest.quantity(), Integer::sum);
        }

        if (requested.isEmpty()) {
            throw new BusinessRuleException("At least one receive item is required");
        }

        return requested;
    }

    private int getRemainingQuantity(PurchaseOrderItem item) {
        int orderedQuantity = normalizeNonNegative(item.getQuantity(), 0);
        int receivedQuantity = normalizeNonNegative(item.getReceivedQuantity(), 0);
        return Math.max(0, orderedQuantity - receivedQuantity);
    }

    private int calculateTotalQuantity(List<PurchaseOrderItem> items) {
        return items.stream()
                .map(PurchaseOrderItem::getQuantity)
                .map(quantity -> normalizeNonNegative(quantity, 0))
                .reduce(0, Integer::sum);
    }

    private int calculateTotalReceivedQuantity(List<PurchaseOrderItem> items) {
        return items.stream()
                .map(PurchaseOrderItem::getReceivedQuantity)
                .map(quantity -> normalizeNonNegative(quantity, 0))
                .reduce(0, Integer::sum);
    }

    private int calculateTotalRemainingQuantity(List<PurchaseOrderItem> items) {
        return items.stream()
                .mapToInt(this::getRemainingQuantity)
                .sum();
    }

    private int calculateReceiptTotalQuantity(PurchaseOrderReceipt receipt) {
        return receipt.getItems().stream()
                .map(PurchaseOrderReceiptItem::getQuantity)
                .map(quantity -> normalizeNonNegative(quantity, 0))
                .reduce(0, Integer::sum);
    }

    private int normalizeReceiptLimit(Integer limit) {
        if (limit == null) {
            return 200;
        }
        return Math.max(1, Math.min(limit, 2000));
    }

    private String normalizeFilterValue(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String resolveActorUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getName())) {
            return "SYSTEM";
        }
        return authentication.getName();
    }

    private PurchaseOrderResponse toResponse(PurchaseOrder order) {
        List<PurchaseOrderItemResponse> items = order.getItems().stream()
                .map(item -> new PurchaseOrderItemResponse(
                        item.getProduct().getId(),
                        item.getProduct().getSku(),
                        item.getProduct().getName(),
                        item.getQuantity(),
                        normalizeNonNegative(item.getReceivedQuantity(), 0),
                        getRemainingQuantity(item),
                        item.getUnitCost()
                ))
                .toList();

        List<PurchaseOrderReceiptResponse> receipts = toReceiptResponses(order, null, null, null, Integer.MAX_VALUE);

        int totalQuantity = calculateTotalQuantity(order.getItems());
        int totalReceivedQuantity = calculateTotalReceivedQuantity(order.getItems());
        int totalRemainingQuantity = calculateTotalRemainingQuantity(order.getItems());

        return new PurchaseOrderResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getSupplier() == null ? null : order.getSupplier().getId(),
                order.getSupplier() == null ? null : order.getSupplier().getCode(),
                order.getSupplierName(),
                order.getNote(),
                order.getStatus().name(),
                order.getCreatedAt(),
                order.getReceivedAt(),
                totalQuantity,
                totalReceivedQuantity,
                totalRemainingQuantity,
                items,
                receipts
        );
    }

    private List<PurchaseOrderReceiptResponse> toReceiptResponses(
            PurchaseOrder order,
            String receivedBy,
            OffsetDateTime from,
            OffsetDateTime to,
            int limit
    ) {
        String normalizedReceivedBy = normalizeFilterValue(receivedBy);

        return order.getReceipts().stream()
                .sorted(
                        Comparator.comparing(
                                        PurchaseOrderReceipt::getReceivedAt,
                                        Comparator.nullsLast(Comparator.naturalOrder())
                                )
                                .reversed()
                                .thenComparing(
                                        PurchaseOrderReceipt::getId,
                                        Comparator.nullsLast(Comparator.reverseOrder())
                                )
                )
                .filter(receipt -> {
                    if (normalizedReceivedBy == null) {
                        return true;
                    }
                    String actor = receipt.getReceivedBy() == null ? "" : receipt.getReceivedBy();
                    return actor.toLowerCase().contains(normalizedReceivedBy.toLowerCase());
                })
                .filter(receipt -> from == null || (receipt.getReceivedAt() != null && !receipt.getReceivedAt().isBefore(from)))
                .filter(receipt -> to == null || (receipt.getReceivedAt() != null && !receipt.getReceivedAt().isAfter(to)))
                .limit(limit)
                .map(this::toReceiptResponse)
                .toList();
    }

    private PurchaseOrderReceiptResponse toReceiptResponse(PurchaseOrderReceipt receipt) {
        List<PurchaseOrderReceiptItemResponse> receiptItems = receipt.getItems().stream()
                .sorted(
                        Comparator.comparing(
                                        (PurchaseOrderReceiptItem item) -> item.getProduct().getSku(),
                                        Comparator.nullsLast(Comparator.naturalOrder())
                                )
                                .thenComparing(item -> item.getProduct().getId(), Comparator.nullsLast(Comparator.naturalOrder()))
                )
                .map(receiptItem -> new PurchaseOrderReceiptItemResponse(
                        receiptItem.getProduct().getId(),
                        receiptItem.getProduct().getSku(),
                        receiptItem.getProduct().getName(),
                        receiptItem.getQuantity()
                ))
                .toList();
        return new PurchaseOrderReceiptResponse(
                receipt.getId(),
                receipt.getReceivedBy(),
                receipt.getReceivedAt(),
                calculateReceiptTotalQuantity(receipt),
                receiptItems
        );
    }

    private record SuggestedContract(
            Long supplierId,
            String supplierCode,
            String supplierName,
            BigDecimal unitCost,
            Integer leadTimeDays,
            Integer moq,
            Integer lotSize
    ) {
    }
}
