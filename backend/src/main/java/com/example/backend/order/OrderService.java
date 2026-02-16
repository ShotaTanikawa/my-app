package com.example.backend.order;

import com.example.backend.audit.AuditLogService;
import com.example.backend.common.InsufficientStockException;
import com.example.backend.common.InvalidOrderStateException;
import com.example.backend.common.ResourceNotFoundException;
import com.example.backend.inventory.Inventory;
import com.example.backend.inventory.InventoryRepository;
import com.example.backend.order.dto.CreateSalesOrderItemRequest;
import com.example.backend.order.dto.CreateSalesOrderRequest;
import com.example.backend.order.dto.SalesOrderItemResponse;
import com.example.backend.order.dto.SalesOrderResponse;
import com.example.backend.product.Product;
import com.example.backend.product.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class OrderService {

    private static final DateTimeFormatter ORDER_NUMBER_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final SalesOrderRepository salesOrderRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final AuditLogService auditLogService;

    public OrderService(
            SalesOrderRepository salesOrderRepository,
            ProductRepository productRepository,
            InventoryRepository inventoryRepository,
            AuditLogService auditLogService
    ) {
        this.salesOrderRepository = salesOrderRepository;
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<SalesOrderResponse> getOrders() {
        return salesOrderRepository.findAllDetailed().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public SalesOrderResponse getOrder(Long orderId) {
        return toResponse(findOrderDetailedById(orderId));
    }

    @Transactional
    public SalesOrderResponse createOrder(CreateSalesOrderRequest request) {
        // 新規受注はまずRESERVEDとし、在庫を引当済みに振り替える。
        SalesOrder order = new SalesOrder();
        order.setOrderNumber(generateOrderNumber());
        order.setCustomerName(request.customerName());
        order.setStatus(OrderStatus.RESERVED);

        for (CreateSalesOrderItemRequest itemRequest : request.items()) {
            Product product = productRepository.findById(itemRequest.productId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + itemRequest.productId()));

            Inventory inventory = inventoryRepository.findByProductIdForUpdate(itemRequest.productId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Inventory not found for product: " + itemRequest.productId()
                    ));

            // 在庫の不変条件: availableを超える引当は許可しない。
            if (inventory.getAvailableQuantity() < itemRequest.quantity()) {
                throw new InsufficientStockException(
                        "Insufficient stock for SKU " + product.getSku() + ": available="
                                + inventory.getAvailableQuantity() + ", requested=" + itemRequest.quantity()
                );
            }

            // 引当処理: available -> reserved。
            inventory.setAvailableQuantity(inventory.getAvailableQuantity() - itemRequest.quantity());
            inventory.setReservedQuantity(inventory.getReservedQuantity() + itemRequest.quantity());

            SalesOrderItem orderItem = new SalesOrderItem();
            orderItem.setProduct(product);
            orderItem.setQuantity(itemRequest.quantity());
            orderItem.setUnitPrice(product.getUnitPrice());
            order.addItem(orderItem);
        }

        SalesOrder savedOrder = salesOrderRepository.save(order);
        auditLogService.log(
                "ORDER_CREATE",
                "ORDER",
                savedOrder.getId().toString(),
                "orderNumber=" + savedOrder.getOrderNumber() + ", customer=" + savedOrder.getCustomerName()
        );
        return toResponse(savedOrder);
    }

    @Transactional
    public SalesOrderResponse confirmOrder(Long orderId) {
        SalesOrder order = findOrderDetailedById(orderId);

        if (order.getStatus() != OrderStatus.RESERVED) {
            throw new InvalidOrderStateException("Only RESERVED orders can be confirmed. Current status: " + order.getStatus());
        }

        for (SalesOrderItem item : order.getItems()) {
            Inventory inventory = inventoryRepository.findByProductIdForUpdate(item.getProduct().getId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Inventory not found for product: " + item.getProduct().getId()
                    ));

            // 確定前にreserved数量の整合性を再確認する。
            if (inventory.getReservedQuantity() < item.getQuantity()) {
                throw new InsufficientStockException(
                        "Reserved quantity is inconsistent for SKU " + item.getProduct().getSku()
                );
            }

            inventory.setReservedQuantity(inventory.getReservedQuantity() - item.getQuantity());
        }

        order.setStatus(OrderStatus.CONFIRMED);
        auditLogService.log(
                "ORDER_CONFIRM",
                "ORDER",
                order.getId().toString(),
                "orderNumber=" + order.getOrderNumber()
        );
        return toResponse(order);
    }

    @Transactional
    public SalesOrderResponse cancelOrder(Long orderId) {
        SalesOrder order = findOrderDetailedById(orderId);

        if (order.getStatus() != OrderStatus.RESERVED) {
            throw new InvalidOrderStateException("Only RESERVED orders can be cancelled. Current status: " + order.getStatus());
        }

        for (SalesOrderItem item : order.getItems()) {
            Inventory inventory = inventoryRepository.findByProductIdForUpdate(item.getProduct().getId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Inventory not found for product: " + item.getProduct().getId()
                    ));

            if (inventory.getReservedQuantity() < item.getQuantity()) {
                throw new InsufficientStockException(
                        "Reserved quantity is inconsistent for SKU " + item.getProduct().getSku()
                );
            }

            // キャンセル時はreserved -> availableに戻す。
            inventory.setReservedQuantity(inventory.getReservedQuantity() - item.getQuantity());
            inventory.setAvailableQuantity(inventory.getAvailableQuantity() + item.getQuantity());
        }

        order.setStatus(OrderStatus.CANCELLED);
        auditLogService.log(
                "ORDER_CANCEL",
                "ORDER",
                order.getId().toString(),
                "orderNumber=" + order.getOrderNumber()
        );
        return toResponse(order);
    }

    private SalesOrder findOrderDetailedById(Long orderId) {
        return salesOrderRepository.findDetailedById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
    }

    private String generateOrderNumber() {
        String orderNumber;
        do {
            String timestamp = LocalDateTime.now(ZoneOffset.UTC).format(ORDER_NUMBER_DATE_FORMATTER);
            int random = ThreadLocalRandom.current().nextInt(1000, 10_000);
            orderNumber = "SO-" + timestamp + "-" + random;
        } while (salesOrderRepository.existsByOrderNumber(orderNumber));

        return orderNumber;
    }

    private SalesOrderResponse toResponse(SalesOrder order) {
        List<SalesOrderItemResponse> items = order.getItems().stream()
                .map(item -> new SalesOrderItemResponse(
                        item.getProduct().getId(),
                        item.getProduct().getSku(),
                        item.getProduct().getName(),
                        item.getQuantity(),
                        item.getUnitPrice()
                ))
                .toList();

        return new SalesOrderResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getCustomerName(),
                order.getStatus().name(),
                order.getCreatedAt(),
                items
        );
    }
}
