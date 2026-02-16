package com.example.backend.product;

import com.example.backend.audit.AuditLogService;
import com.example.backend.common.BusinessRuleException;
import com.example.backend.common.ResourceNotFoundException;
import com.example.backend.inventory.Inventory;
import com.example.backend.inventory.InventoryRepository;
import com.example.backend.product.dto.CreateProductRequest;
import com.example.backend.product.dto.ProductPageResponse;
import com.example.backend.product.dto.ProductResponse;
import com.example.backend.product.dto.UpdateProductRequest;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Expression;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductCategoryRepository productCategoryRepository;
    private final InventoryRepository inventoryRepository;
    private final AuditLogService auditLogService;

    public ProductService(
            ProductRepository productRepository,
            ProductCategoryRepository productCategoryRepository,
            InventoryRepository inventoryRepository,
            AuditLogService auditLogService
    ) {
        this.productRepository = productRepository;
        this.productCategoryRepository = productCategoryRepository;
        this.inventoryRepository = inventoryRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {
        if (productRepository.existsBySku(request.sku())) {
            throw new BusinessRuleException("SKU already exists: " + request.sku());
        }

        Product product = new Product();
        product.setSku(request.sku());
        product.setName(request.name());
        product.setDescription(request.description());
        product.setUnitPrice(request.unitPrice());
        product.setReorderPoint(normalizeReorderValue(request.reorderPoint()));
        product.setReorderQuantity(normalizeReorderValue(request.reorderQuantity()));
        product.setCategory(resolveCategory(request.categoryId()));
        Product savedProduct = productRepository.save(product);

        // 商品作成時に在庫レコードを1件同時作成し、1商品1在庫を保証する。
        Inventory inventory = new Inventory();
        inventory.setProduct(savedProduct);
        inventory.setAvailableQuantity(0);
        inventory.setReservedQuantity(0);
        Inventory savedInventory = inventoryRepository.save(inventory);
        auditLogService.log(
                "PRODUCT_CREATE",
                "PRODUCT",
                savedProduct.getId().toString(),
                "sku=" + savedProduct.getSku() + ", name=" + savedProduct.getName()
        );

        return toResponse(savedProduct, savedInventory);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getProducts() {
        List<Product> products = productRepository.findAll(Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id")));
        return toResponses(products);
    }

    @Transactional(readOnly = true)
    public ProductPageResponse getProductsPage(
            String q,
            Long categoryId,
            Boolean lowStockOnly,
            int page,
            int size
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(1, Math.min(size, 200));
        Pageable pageable = PageRequest.of(
                safePage,
                safeSize,
                Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"))
        );
        Specification<Product> specification = buildSearchSpecification(q, categoryId, lowStockOnly);
        Page<Product> resultPage = productRepository.findAll(specification, pageable);

        List<ProductResponse> items = toResponses(resultPage.getContent());
        return new ProductPageResponse(
                items,
                resultPage.getNumber(),
                resultPage.getSize(),
                resultPage.getTotalElements(),
                resultPage.getTotalPages(),
                resultPage.hasNext(),
                resultPage.hasPrevious()
        );
    }

    @Transactional(readOnly = true)
    public ProductResponse getProduct(Long productId) {
        Product product = findProductById(productId);
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found for product: " + productId));

        return toResponse(product, inventory);
    }

    @Transactional
    public ProductResponse updateProduct(Long productId, UpdateProductRequest request) {
        Product product = findProductById(productId);
        product.setName(request.name());
        product.setDescription(request.description());
        product.setUnitPrice(request.unitPrice());
        product.setReorderPoint(request.reorderPoint() == null
                ? product.getReorderPoint()
                : normalizeReorderValue(request.reorderPoint()));
        product.setReorderQuantity(request.reorderQuantity() == null
                ? product.getReorderQuantity()
                : normalizeReorderValue(request.reorderQuantity()));
        product.setCategory(resolveCategory(request.categoryId()));

        Product updatedProduct = productRepository.save(product);
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found for product: " + productId));
        auditLogService.log(
                "PRODUCT_UPDATE",
                "PRODUCT",
                updatedProduct.getId().toString(),
                "name=" + updatedProduct.getName() + ", unitPrice=" + updatedProduct.getUnitPrice()
        );

        return toResponse(updatedProduct, inventory);
    }

    @Transactional
    public ProductResponse addStock(Long productId, Integer quantity) {
        Product product = findProductById(productId);
        // 複数オペレータの同時入庫で更新が競合しないよう悲観ロックで更新する。
        Inventory inventory = inventoryRepository.findByProductIdForUpdate(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found for product: " + productId));

        int before = inventory.getAvailableQuantity();
        inventory.setAvailableQuantity(inventory.getAvailableQuantity() + quantity);
        Inventory updatedInventory = inventoryRepository.save(inventory);
        auditLogService.log(
                "STOCK_ADD",
                "PRODUCT",
                productId.toString(),
                "quantity=" + quantity + ", availableBefore=" + before + ", availableAfter=" + updatedInventory.getAvailableQuantity()
        );

        return toResponse(product, updatedInventory);
    }

    private Product findProductById(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));
    }

    private ProductCategory resolveCategory(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        return productCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + categoryId));
    }

    private List<ProductResponse> toResponses(List<Product> products) {
        if (products.isEmpty()) {
            return List.of();
        }

        List<Long> productIds = products.stream().map(Product::getId).toList();
        Map<Long, Inventory> inventories = inventoryRepository.findByProductIdIn(productIds).stream()
                .collect(Collectors.toMap(inv -> inv.getProduct().getId(), inv -> inv));

        List<ProductResponse> responses = new ArrayList<>(products.size());
        for (Product product : products) {
            responses.add(toResponse(product, inventories.get(product.getId())));
        }
        return responses;
    }

    private ProductResponse toResponse(Product product, Inventory inventory) {
        ProductCategory category = product.getCategory();
        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getDescription(),
                product.getUnitPrice(),
                normalizeReorderValue(product.getReorderPoint()),
                normalizeReorderValue(product.getReorderQuantity()),
                category == null ? null : category.getId(),
                category == null ? null : category.getCode(),
                category == null ? null : category.getName(),
                inventory == null ? 0 : inventory.getAvailableQuantity(),
                inventory == null ? 0 : inventory.getReservedQuantity()
        );
    }

    private Specification<Product> buildSearchSpecification(String q, Long categoryId, Boolean lowStockOnly) {
        return (root, query, criteriaBuilder) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();

            if (q != null && !q.isBlank()) {
                String normalized = "%" + q.trim().toLowerCase() + "%";
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("sku")), normalized),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), normalized)
                ));
            }

            if (categoryId != null) {
                predicates.add(criteriaBuilder.equal(root.get("category").get("id"), categoryId));
            }

            if (Boolean.TRUE.equals(lowStockOnly)) {
                var inventoryJoin = root.join("inventory", JoinType.LEFT);
                Expression<Integer> availableExpr = criteriaBuilder.coalesce(
                        inventoryJoin.get("availableQuantity"),
                        criteriaBuilder.literal(0)
                );
                predicates.add(criteriaBuilder.lessThanOrEqualTo(availableExpr, root.get("reorderPoint")));
            }

            return criteriaBuilder.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    private Integer normalizeReorderValue(Integer value) {
        if (value == null || value < 0) {
            return 0;
        }
        return value;
    }
}
