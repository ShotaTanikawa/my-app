package com.example.backend.product;

import com.example.backend.audit.AuditLogService;
import com.example.backend.common.BusinessRuleException;
import com.example.backend.common.ResourceNotFoundException;
import com.example.backend.inventory.Inventory;
import com.example.backend.inventory.InventoryRepository;
import com.example.backend.product.dto.CreateProductRequest;
import com.example.backend.product.dto.ProductImportErrorResponse;
import com.example.backend.product.dto.ProductImportResultResponse;
import com.example.backend.product.dto.ProductPageResponse;
import com.example.backend.product.dto.ProductResponse;
import com.example.backend.product.dto.UpdateProductRequest;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Expression;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ProductService {

    private static final int DEFAULT_IMPORT_REORDER_POINT = 5;
    private static final int DEFAULT_IMPORT_REORDER_QUANTITY = 10;
    private static final String ACTION_PRODUCT_IMPORT = "PRODUCT_IMPORT";
    private static final String DEFAULT_SKU_PREFIX = "PRD";
    private static final int SKU_PREFIX_MAX_LENGTH = 20;
    private static final DateTimeFormatter SKU_DATE_FORMAT = DateTimeFormatter.ofPattern("yyMMdd");
    private static final Pattern SKU_PATTERN = Pattern.compile("^[A-Z0-9][A-Z0-9-]{1,63}$");
    private static final int DEFAULT_SKU_SEQUENCE_DIGITS = 4;

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
        String normalizedSku = requireValidSku(request.sku(), "SKU");
        if (productRepository.existsBySkuIgnoreCase(normalizedSku)) {
            throw new BusinessRuleException("SKU already exists: " + normalizedSku);
        }

        Product product = new Product();
        product.setSku(normalizedSku);
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
        List<Long> categoryIds = resolveCategoryIdsForSearch(categoryId);
        Specification<Product> specification = buildSearchSpecification(q, categoryIds, lowStockOnly);
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

    @Transactional(readOnly = true)
    public String suggestNextSku(Long categoryId) {
        ProductCategory category = resolveCategory(categoryId);
        String prefix = buildSkuPrefix(category);
        int sequenceDigits = resolveSkuSequenceDigits(category);
        String datePart = LocalDate.now().format(SKU_DATE_FORMAT);
        String base = prefix + "-" + datePart;
        String skuPrefix = base + "-";
        int sequenceLimit = (int) Math.pow(10, sequenceDigits) - 1;

        int sequence = 1;
        Optional<Product> latest = productRepository.findTopBySkuStartingWithOrderBySkuDesc(skuPrefix);
        if (latest.isPresent()) {
            String latestSku = latest.get().getSku();
            if (latestSku.length() > skuPrefix.length()) {
                String suffix = latestSku.substring(skuPrefix.length());
                if (suffix.matches("\\d{" + sequenceDigits + "}")) {
                    sequence = Integer.parseInt(suffix) + 1;
                }
            }
        }

        while (sequence <= sequenceLimit) {
            String candidate = skuPrefix + String.format("%0" + sequenceDigits + "d", sequence);
            if (!productRepository.existsBySkuIgnoreCase(candidate)) {
                return candidate;
            }
            sequence++;
        }

        throw new BusinessRuleException("SKU候補を採番できませんでした。カテゴリを見直してください。");
    }

    @Transactional
    public ProductImportResultResponse importProductsCsv(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("CSVファイルが空です。");
        }

        List<ProductImportErrorResponse> errors = new ArrayList<>();
        int totalRows = 0;
        int successRows = 0;
        int createdRows = 0;
        int updatedRows = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null || headerLine.isBlank()) {
                throw new BusinessRuleException("CSVヘッダ行が見つかりません。");
            }

            Map<String, Integer> headerIndexMap = buildHeaderIndex(headerLine);
            validateImportHeaders(headerIndexMap);

            String line;
            int rowNumber = 1;
            while ((line = reader.readLine()) != null) {
                rowNumber++;
                if (line.isBlank()) {
                    continue;
                }
                totalRows++;

                try {
                    ImportRow row = parseImportRow(line, rowNumber, headerIndexMap);
                    boolean created = upsertFromImportRow(row);
                    successRows++;
                    if (created) {
                        createdRows++;
                    } else {
                        updatedRows++;
                    }
                } catch (RuntimeException ex) {
                    String message = ex.getMessage() == null ? "不明なエラー" : ex.getMessage();
                    if (!message.startsWith("row ")) {
                        message = "row " + rowNumber + ": " + message;
                    }
                    errors.add(new ProductImportErrorResponse(rowNumber, message));
                }
            }
        } catch (IOException ex) {
            throw new BusinessRuleException("CSVファイルの読み込みに失敗しました。");
        }

        auditLogService.log(
                ACTION_PRODUCT_IMPORT,
                "PRODUCT",
                "BULK",
                "totalRows=" + totalRows + ", successRows=" + successRows + ", createdRows=" + createdRows
                        + ", updatedRows=" + updatedRows + ", failedRows=" + errors.size()
        );

        return new ProductImportResultResponse(
                totalRows,
                successRows,
                createdRows,
                updatedRows,
                errors.size(),
                List.copyOf(errors)
        );
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

    private Specification<Product> buildSearchSpecification(String q, List<Long> categoryIds, Boolean lowStockOnly) {
        return (root, query, criteriaBuilder) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();

            if (q != null && !q.isBlank()) {
                String normalized = "%" + q.trim().toLowerCase() + "%";
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("sku")), normalized),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), normalized)
                ));
            }

            if (categoryIds != null && !categoryIds.isEmpty()) {
                predicates.add(root.get("category").get("id").in(categoryIds));
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

    private List<Long> resolveCategoryIdsForSearch(Long categoryId) {
        if (categoryId == null) {
            return List.of();
        }

        ProductCategory rootCategory = productCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + categoryId));

        Set<Long> resolvedIds = new LinkedHashSet<>();
        List<Long> frontier = List.of(rootCategory.getId());
        while (!frontier.isEmpty()) {
            for (Long id : frontier) {
                resolvedIds.add(id);
            }
            List<ProductCategory> children = productCategoryRepository.findByParent_IdIn(frontier);
            frontier = children.stream()
                    .map(ProductCategory::getId)
                    .filter(id -> !resolvedIds.contains(id))
                    .toList();
        }

        return List.copyOf(resolvedIds);
    }

    private Integer normalizeReorderValue(Integer value) {
        if (value == null || value < 0) {
            return 0;
        }
        return value;
    }

    private boolean upsertFromImportRow(ImportRow row) {
        ProductCategory category = resolveCategoryByCode(row.categoryCode());
        Optional<Product> existing = productRepository.findBySkuIgnoreCase(row.sku());
        boolean created = existing.isEmpty();

        Product product = existing.orElseGet(Product::new);
        if (created) {
            product.setSku(row.sku());
            product.setReorderPoint(DEFAULT_IMPORT_REORDER_POINT);
            product.setReorderQuantity(DEFAULT_IMPORT_REORDER_QUANTITY);
        }
        product.setName(row.name());
        product.setDescription(row.description());
        product.setUnitPrice(row.unitPrice());
        product.setCategory(category);

        Product savedProduct = productRepository.save(product);
        Inventory inventory = inventoryRepository.findByProductId(savedProduct.getId())
                .orElseGet(() -> {
                    Inventory newInventory = new Inventory();
                    newInventory.setProduct(savedProduct);
                    newInventory.setReservedQuantity(0);
                    newInventory.setAvailableQuantity(0);
                    return newInventory;
                });
        inventory.setAvailableQuantity(row.availableQuantity());
        if (inventory.getReservedQuantity() == null) {
            inventory.setReservedQuantity(0);
        }
        inventoryRepository.save(inventory);
        return created;
    }

    private ProductCategory resolveCategoryByCode(String categoryCode) {
        if (categoryCode == null || categoryCode.isBlank()) {
            return null;
        }
        return productCategoryRepository.findByCodeIgnoreCase(categoryCode)
                .orElseThrow(() -> new BusinessRuleException("カテゴリコードが存在しません: " + categoryCode));
    }

    private Map<String, Integer> buildHeaderIndex(String headerLine) {
        List<String> headers = parseCsvLine(headerLine);
        Map<String, Integer> headerIndexMap = new HashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            headerIndexMap.put(normalizeHeader(headers.get(i)), i);
        }
        return headerIndexMap;
    }

    private void validateImportHeaders(Map<String, Integer> headerIndexMap) {
        List<String> requiredHeaders = List.of("sku", "name", "unitprice", "availablequantity");
        List<String> missing = requiredHeaders.stream()
                .filter(header -> !headerIndexMap.containsKey(header))
                .toList();
        if (!missing.isEmpty()) {
            throw new BusinessRuleException("CSVヘッダ不足: " + String.join(", ", missing));
        }
    }

    private ImportRow parseImportRow(String line, int rowNumber, Map<String, Integer> headerIndexMap) {
        List<String> cells = parseCsvLine(line);
        String sku = readCell(cells, headerIndexMap, "sku");
        String name = readCell(cells, headerIndexMap, "name");
        String unitPriceValue = readCell(cells, headerIndexMap, "unitprice");
        String availableQuantityValue = readCell(cells, headerIndexMap, "availablequantity");
        String categoryCode = readCell(cells, headerIndexMap, "categorycode");
        String description = readCell(cells, headerIndexMap, "description");
        String normalizedSku = requireValidSku(sku, "row " + rowNumber + ": sku");
        if (name.isBlank()) {
            throw new BusinessRuleException("row " + rowNumber + ": name が空です。");
        }

        BigDecimal unitPrice;
        try {
            unitPrice = new BigDecimal(unitPriceValue);
        } catch (NumberFormatException ex) {
            throw new BusinessRuleException("row " + rowNumber + ": unitPrice が不正です。");
        }
        if (unitPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("row " + rowNumber + ": unitPrice は0より大きい値を指定してください。");
        }

        int availableQuantity;
        try {
            availableQuantity = Integer.parseInt(availableQuantityValue);
        } catch (NumberFormatException ex) {
            throw new BusinessRuleException("row " + rowNumber + ": availableQuantity が不正です。");
        }
        if (availableQuantity < 0) {
            throw new BusinessRuleException("row " + rowNumber + ": availableQuantity は0以上を指定してください。");
        }

        return new ImportRow(
                normalizedSku,
                name,
                description.isBlank() ? null : description,
                unitPrice,
                availableQuantity,
                categoryCode.isBlank() ? null : categoryCode
        );
    }

    private String normalizeHeader(String header) {
        return header.replace("\uFEFF", "").trim().toLowerCase();
    }

    private String readCell(List<String> cells, Map<String, Integer> headerIndexMap, String headerName) {
        Integer index = headerIndexMap.get(headerName);
        if (index == null || index < 0 || index >= cells.size()) {
            return "";
        }
        return cells.get(index).trim();
    }

    private List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder currentValue = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);

            if (ch == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    currentValue.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
                continue;
            }

            if (ch == ',' && !inQuotes) {
                values.add(currentValue.toString());
                currentValue.setLength(0);
                continue;
            }
            currentValue.append(ch);
        }
        values.add(currentValue.toString());
        return values;
    }

    private String requireValidSku(String rawSku, String fieldName) {
        String normalized = normalizeSku(rawSku);
        if (normalized.isBlank()) {
            throw new BusinessRuleException(fieldName + " が空です。");
        }
        if (!SKU_PATTERN.matcher(normalized).matches()) {
            throw new BusinessRuleException(fieldName + " の形式が不正です。英大文字・数字・ハイフンのみ利用できます。");
        }
        return normalized;
    }

    private String normalizeSku(String rawSku) {
        if (rawSku == null) {
            return "";
        }
        return rawSku.trim().toUpperCase(Locale.ROOT);
    }

    private String buildSkuPrefix(ProductCategory category) {
        String source;
        if (category == null) {
            source = DEFAULT_SKU_PREFIX;
        } else if (category.getSkuPrefix() != null && !category.getSkuPrefix().isBlank()) {
            source = category.getSkuPrefix();
        } else {
            source = category.getCode();
        }
        String normalized = source == null ? "" : source.trim().toUpperCase(Locale.ROOT);
        normalized = normalized.replaceAll("[^A-Z0-9]+", "-");
        normalized = normalized.replaceAll("-{2,}", "-");
        normalized = normalized.replaceAll("^-+|-+$", "");
        if (normalized.isBlank()) {
            normalized = DEFAULT_SKU_PREFIX;
        }
        if (normalized.length() > SKU_PREFIX_MAX_LENGTH) {
            normalized = normalized.substring(0, SKU_PREFIX_MAX_LENGTH);
            normalized = normalized.replaceAll("-+$", "");
        }
        return normalized.isBlank() ? DEFAULT_SKU_PREFIX : normalized;
    }

    private int resolveSkuSequenceDigits(ProductCategory category) {
        if (category == null || category.getSkuSequenceDigits() == null) {
            return DEFAULT_SKU_SEQUENCE_DIGITS;
        }
        int value = category.getSkuSequenceDigits();
        if (value < 3 || value > 6) {
            return DEFAULT_SKU_SEQUENCE_DIGITS;
        }
        return value;
    }

    private record ImportRow(
            String sku,
            String name,
            String description,
            BigDecimal unitPrice,
            Integer availableQuantity,
            String categoryCode
    ) {
    }
}
