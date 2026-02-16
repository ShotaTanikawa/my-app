package com.example.backend.config;

import com.example.backend.inventory.Inventory;
import com.example.backend.inventory.InventoryRepository;
import com.example.backend.order.OrderService;
import com.example.backend.order.SalesOrder;
import com.example.backend.order.SalesOrderRepository;
import com.example.backend.order.dto.CreateSalesOrderItemRequest;
import com.example.backend.order.dto.CreateSalesOrderRequest;
import com.example.backend.product.Product;
import com.example.backend.product.ProductCategory;
import com.example.backend.product.ProductCategoryRepository;
import com.example.backend.product.ProductRepository;
import com.example.backend.purchase.PurchaseOrder;
import com.example.backend.purchase.PurchaseOrderRepository;
import com.example.backend.purchase.PurchaseOrderService;
import com.example.backend.purchase.dto.CreatePurchaseOrderItemRequest;
import com.example.backend.purchase.dto.CreatePurchaseOrderRequest;
import com.example.backend.purchase.dto.ReceivePurchaseOrderItemRequest;
import com.example.backend.purchase.dto.ReceivePurchaseOrderRequest;
import com.example.backend.purchase.dto.ReplenishmentSuggestionResponse;
import com.example.backend.supplier.ProductSupplier;
import com.example.backend.supplier.ProductSupplierRepository;
import com.example.backend.supplier.Supplier;
import com.example.backend.supplier.SupplierRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * ローカル検証向けのサンプル業務データを投入する初期化コンポーネント。
 */
@Component
@ConditionalOnProperty(name = {"app.seed.enabled", "app.seed.sample-data.enabled"}, havingValue = "true")
public class SampleDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SampleDataInitializer.class);
    private static final String SAMPLE_TAG = "[SAMPLE]";

    private static final List<CategorySeed> CATEGORY_SEEDS = List.of(
            new CategorySeed("HOCKEY", "アイスホッケー", null, 10, "IH", 4),
            new CategorySeed("HK_STICK", "スティック", "HOCKEY", 11, "IH-STK", 4),
            new CategorySeed("HK_PROTECT", "プロテクター", "HOCKEY", 12, "IH-PRO", 4),
            new CategorySeed("HK_GOALIE", "ゴーリー用品", "HOCKEY", 13, "IH-GLV", 4),
            new CategorySeed("FIGURE", "フィギュアスケート", null, 20, "FS", 4),
            new CategorySeed("FG_BOOT", "ブーツ", "FIGURE", 21, "FS-BOT", 4),
            new CategorySeed("FG_WEAR", "ウェア", "FIGURE", 22, "FS-WEA", 4),
            new CategorySeed("FG_ACCESS", "アクセサリー", "FIGURE", 23, "FS-ACC", 4),
            new CategorySeed("MAINT", "メンテナンス", null, 30, "MNT", 4),
            new CategorySeed("MNT_BLADE", "ブレードケア", "MAINT", 31, "MNT-BLD", 4),
            new CategorySeed("MNT_TOOL", "工具・消耗品", "MAINT", 32, "MNT-TOL", 4)
    );

    private static final List<ProductLineSeed> PRODUCT_LINE_SEEDS = List.of(
            new ProductLineSeed(
                    "HK_STICK",
                    "IH-STK",
                    "ホッケースティック",
                    BigDecimal.valueOf(22000),
                    BigDecimal.valueOf(1800),
                    List.of("Alpha 55", "Alpha 65", "Alpha 75", "Alpha 85", "Force 65", "Force 75")
            ),
            new ProductLineSeed(
                    "HK_PROTECT",
                    "IH-PRO",
                    "ホッケープロテクター",
                    BigDecimal.valueOf(8600),
                    BigDecimal.valueOf(750),
                    List.of("Shoulder S", "Shoulder M", "Shoulder L", "Elbow S", "Elbow M", "Shin M")
            ),
            new ProductLineSeed(
                    "HK_GOALIE",
                    "IH-GLV",
                    "ゴーリーギア",
                    BigDecimal.valueOf(18000),
                    BigDecimal.valueOf(2200),
                    List.of("Catcher 31", "Catcher 33", "Blocker 31", "Blocker 33", "Pad 34", "Pad 36")
            ),
            new ProductLineSeed(
                    "FG_BOOT",
                    "FS-BOT",
                    "フィギュアブーツ",
                    BigDecimal.valueOf(32000),
                    BigDecimal.valueOf(2600),
                    List.of("Beginner 22.0", "Beginner 23.0", "Intermediate 23.5", "Intermediate 24.0", "Pro 24.5", "Pro 25.0")
            ),
            new ProductLineSeed(
                    "FG_WEAR",
                    "FS-WEA",
                    "フィギュアウェア",
                    BigDecimal.valueOf(6800),
                    BigDecimal.valueOf(900),
                    List.of("Practice S", "Practice M", "Practice L", "Competition S", "Competition M", "Warmup L")
            ),
            new ProductLineSeed(
                    "FG_ACCESS",
                    "FS-ACC",
                    "フィギュアアクセサリー",
                    BigDecimal.valueOf(1200),
                    BigDecimal.valueOf(180),
                    List.of("Blade Cover", "Lace 250cm", "Lace 280cm", "Towel Set", "Glove", "Bag Tag")
            ),
            new ProductLineSeed(
                    "MNT_BLADE",
                    "MNT-BLD",
                    "ブレードメンテナンス",
                    BigDecimal.valueOf(2400),
                    BigDecimal.valueOf(260),
                    List.of("Sharpening Basic", "Sharpening Pro", "Rust Guard", "Edge Checker", "Honing Stone", "Cleaner")
            ),
            new ProductLineSeed(
                    "MNT_TOOL",
                    "MNT-TOL",
                    "メンテナンス工具",
                    BigDecimal.valueOf(1500),
                    BigDecimal.valueOf(210),
                    List.of("Hex Kit", "Driver Kit", "Bearing Tool", "Tape Pack", "Grip Pack", "Nut Set")
            )
    );

    private static final List<SupplierSeed> SUPPLIER_SEEDS = List.of(
            new SupplierSeed("SUP-HK", "North Ice Trading", "Kana Fuji", "hk-sales@example.com", "03-3100-1101", "ホッケー用品メイン"),
            new SupplierSeed("SUP-FG", "Aurora Figure Works", "Rei Sato", "fg-sales@example.com", "03-3200-2202", "フィギュア用品メイン"),
            new SupplierSeed("SUP-MNT", "Rink Maintenance Lab", "Sho Inoue", "mnt-sales@example.com", "03-3300-3303", "研磨・工具"),
            new SupplierSeed("SUP-GEN", "Polar General Supply", "Mio Kato", "gen-sales@example.com", "03-3400-4404", "汎用サプライ"),
            new SupplierSeed("SUP-INT", "Glacier Import Partners", "Aoi Nishi", "int-sales@example.com", "03-3500-5505", "輸入品"),
            new SupplierSeed("SUP-OUT", "East Coast Outlet", "Ken Mori", "out-sales@example.com", "03-3600-6606", "アウトレット")
    );

    private static final List<String> SAMPLE_CUSTOMERS = List.of(
            "Central Hockey Club",
            "Rink Stars Academy",
            "North Youth Team",
            "Winter Dance Studio",
            "Silver Blades School",
            "Metro Ice Sports"
    );

    private final ProductCategoryRepository productCategoryRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final SupplierRepository supplierRepository;
    private final ProductSupplierRepository productSupplierRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final OrderService orderService;
    private final PurchaseOrderService purchaseOrderService;

    @Value("${app.seed.sample-data.product-count-per-leaf:15}")
    private int productCountPerLeaf;

    @Value("${app.seed.sample-data.sales-order-target:24}")
    private int salesOrderTarget;

    @Value("${app.seed.sample-data.purchase-order-target:12}")
    private int purchaseOrderTarget;

    public SampleDataInitializer(
            ProductCategoryRepository productCategoryRepository,
            ProductRepository productRepository,
            InventoryRepository inventoryRepository,
            SupplierRepository supplierRepository,
            ProductSupplierRepository productSupplierRepository,
            SalesOrderRepository salesOrderRepository,
            PurchaseOrderRepository purchaseOrderRepository,
            OrderService orderService,
            PurchaseOrderService purchaseOrderService
    ) {
        this.productCategoryRepository = productCategoryRepository;
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.supplierRepository = supplierRepository;
        this.productSupplierRepository = productSupplierRepository;
        this.salesOrderRepository = salesOrderRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.orderService = orderService;
        this.purchaseOrderService = purchaseOrderService;
    }

    @Override
    public void run(ApplicationArguments args) {
        Map<String, ProductCategory> categories = seedCategories();
        List<Product> seededProducts = seedProducts(categories);
        Map<String, Supplier> suppliers = seedSuppliers();
        seedProductSupplierContracts(seededProducts, suppliers);
        seedSalesOrders();
        seedPurchaseOrders(suppliers.values().stream().filter(supplier -> Boolean.TRUE.equals(supplier.getActive())).findFirst().orElse(null));

        log.info(
                "Sample data seed completed. categories={}, products(target per leaf={}), suppliers={}, sampleSalesOrders={}, samplePurchaseOrders={}",
                categories.size(),
                normalizeCount(productCountPerLeaf, 15, 1, 60),
                suppliers.size(),
                countSampleSalesOrders(),
                countSamplePurchaseOrders()
        );
    }

    private Map<String, ProductCategory> seedCategories() {
        Map<String, ProductCategory> categoryMap = new LinkedHashMap<>();
        for (CategorySeed seed : CATEGORY_SEEDS) {
            ProductCategory category = productCategoryRepository.findByCodeIgnoreCase(seed.code())
                    .orElseGet(ProductCategory::new);

            category.setCode(seed.code());
            category.setName(seed.name());
            category.setActive(true);
            category.setSortOrder(seed.sortOrder());
            category.setSkuPrefix(seed.skuPrefix());
            category.setSkuSequenceDigits(seed.skuSequenceDigits());

            if (seed.parentCode() == null) {
                category.setParent(null);
            } else {
                ProductCategory parent = categoryMap.get(seed.parentCode());
                if (parent == null) {
                    throw new IllegalStateException("Parent category not found for code: " + seed.parentCode());
                }
                category.setParent(productCategoryRepository.getReferenceById(parent.getId()));
            }

            ProductCategory saved = productCategoryRepository.save(category);
            categoryMap.put(seed.code(), saved);
        }
        return categoryMap;
    }

    private List<Product> seedProducts(Map<String, ProductCategory> categoryMap) {
        int perLeaf = normalizeCount(productCountPerLeaf, 15, 1, 60);
        List<Product> seeded = new ArrayList<>();

        for (ProductLineSeed seed : PRODUCT_LINE_SEEDS) {
            ProductCategory category = categoryMap.get(seed.categoryCode());
            if (category == null) {
                continue;
            }

            for (int index = 1; index <= perLeaf; index++) {
                String sku = seed.skuPrefix() + "-" + String.format("%03d", index);
                Optional<Product> existingOptional = productRepository.findBySkuIgnoreCase(sku);
                Product product = existingOptional.orElseGet(Product::new);
                boolean isExistingNonSample = product.getId() != null && !isSampleProduct(product);

                if (isExistingNonSample) {
                    seeded.add(product);
                    continue;
                }

                product.setSku(sku);
                product.setName(seed.namePrefix() + " " + seed.variants().get((index - 1) % seed.variants().size()));
                product.setDescription(SAMPLE_TAG + " " + category.getName() + "の運用データ");
                product.setUnitPrice(seed.basePrice().add(seed.priceStep().multiply(BigDecimal.valueOf((index - 1) % 5L))));
                product.setReorderPoint(4 + (index % 7));
                product.setReorderQuantity(12 + (index % 5) * 4);
                product.setCategory(productCategoryRepository.getReferenceById(category.getId()));

                Product saved = productRepository.save(product);
                seeded.add(saved);
                ensureInventory(saved, index);
            }
        }
        return seeded;
    }

    private void ensureInventory(Product product, int sequence) {
        if (inventoryRepository.findByProductId(product.getId()).isPresent()) {
            return;
        }

        int reorderPoint = product.getReorderPoint() == null ? 0 : product.getReorderPoint();
        int availableQuantity = sequence % 4 == 0
                ? Math.max(0, reorderPoint - (1 + (sequence % 3)))
                : reorderPoint + 8 + (sequence % 18);
        int reservedQuantity = sequence % 6 == 0 ? 2 : 0;

        Inventory inventory = new Inventory();
        inventory.setProduct(productRepository.getReferenceById(product.getId()));
        inventory.setAvailableQuantity(availableQuantity);
        inventory.setReservedQuantity(reservedQuantity);
        inventoryRepository.save(inventory);
    }

    private Map<String, Supplier> seedSuppliers() {
        Map<String, Supplier> supplierMap = new LinkedHashMap<>();
        for (SupplierSeed seed : SUPPLIER_SEEDS) {
            Supplier supplier = supplierRepository.findByCodeIgnoreCase(seed.code())
                    .orElseGet(Supplier::new);

            supplier.setCode(seed.code());
            supplier.setName(seed.name());
            supplier.setContactName(seed.contactName());
            supplier.setEmail(seed.email());
            supplier.setPhone(seed.phone());
            supplier.setNote(seed.note());
            supplier.setActive(true);

            Supplier saved = supplierRepository.save(supplier);
            supplierMap.put(seed.code(), saved);
        }
        return supplierMap;
    }

    private void seedProductSupplierContracts(List<Product> products, Map<String, Supplier> suppliers) {
        for (Product product : products) {
            if (!isSampleProduct(product)) {
                continue;
            }

            String categoryCode = product.getCategory() == null ? "" : product.getCategory().getCode();
            String primaryCode = resolvePrimarySupplierCode(categoryCode);
            Supplier primarySupplier = suppliers.get(primaryCode);
            Supplier secondarySupplier = Objects.equals(primaryCode, "SUP-GEN")
                    ? suppliers.get("SUP-INT")
                    : suppliers.get("SUP-GEN");

            if (primarySupplier != null) {
                ensureProductSupplierContract(product, primarySupplier, true, unitCost(product.getUnitPrice(), "0.58"), 5);
            }
            if (secondarySupplier != null) {
                ensureProductSupplierContract(product, secondarySupplier, false, unitCost(product.getUnitPrice(), "0.66"), 9);
            }
        }
    }

    private void ensureProductSupplierContract(
            Product product,
            Supplier supplier,
            boolean primary,
            BigDecimal unitCost,
            int leadTimeDays
    ) {
        ProductSupplier contract = productSupplierRepository.findByProductIdAndSupplierId(product.getId(), supplier.getId())
                .orElseGet(ProductSupplier::new);

        contract.setProduct(productRepository.getReferenceById(product.getId()));
        contract.setSupplier(supplierRepository.getReferenceById(supplier.getId()));
        contract.setUnitCost(unitCost);
        contract.setLeadTimeDays(leadTimeDays);
        contract.setMoq(primary ? 2 : 4);
        contract.setLotSize(primary ? 2 : 4);
        contract.setPrimarySupplier(primary);

        productSupplierRepository.save(contract);
    }

    private void seedSalesOrders() {
        int target = normalizeCount(salesOrderTarget, 24, 0, 200);
        long existingSampleCount = countSampleSalesOrders();
        int toCreate = (int) Math.max(0, target - existingSampleCount);
        if (toCreate == 0) {
            return;
        }

        int created = 0;
        for (int index = 0; index < toCreate; index++) {
            List<Inventory> candidates = inventoryRepository.findAllWithProduct().stream()
                    .filter(inventory -> inventory.getAvailableQuantity() != null && inventory.getAvailableQuantity() >= 4)
                    .toList();

            List<CreateSalesOrderItemRequest> items = buildSalesOrderItems(candidates, index);
            if (items.isEmpty()) {
                break;
            }

            String customer = SAMPLE_TAG + " " + SAMPLE_CUSTOMERS.get(index % SAMPLE_CUSTOMERS.size());

            try {
                var createdOrder = orderService.createOrder(new CreateSalesOrderRequest(customer, items));

                // 実運用に近い状態を作るため、確定・キャンセル・引当残しを混在させる。
                if (index % 3 == 0) {
                    orderService.confirmOrder(createdOrder.id());
                } else if (index % 7 == 0) {
                    orderService.cancelOrder(createdOrder.id());
                }
                created++;
            } catch (RuntimeException ex) {
                log.warn("Skip sample sales order create. reason={}", ex.getMessage());
            }
        }
        log.info("Sample sales orders seeded: {}", created);
    }

    private List<CreateSalesOrderItemRequest> buildSalesOrderItems(List<Inventory> candidates, int seed) {
        if (candidates.isEmpty()) {
            return List.of();
        }

        int lineCount = 1 + (seed % 3);
        int start = (seed * 2) % candidates.size();
        Set<Long> usedProductIds = new HashSet<>();
        List<CreateSalesOrderItemRequest> items = new ArrayList<>();

        for (int offset = 0; offset < candidates.size() && items.size() < lineCount; offset++) {
            Inventory inventory = candidates.get((start + offset) % candidates.size());
            Product product = inventory.getProduct();
            if (product == null || !usedProductIds.add(product.getId())) {
                continue;
            }

            int available = inventory.getAvailableQuantity() == null ? 0 : inventory.getAvailableQuantity();
            int quantity = Math.max(1, Math.min(3, available / 3));
            if (quantity <= 0) {
                continue;
            }
            items.add(new CreateSalesOrderItemRequest(product.getId(), quantity));
        }

        return items;
    }

    private void seedPurchaseOrders(Supplier fallbackSupplier) {
        if (fallbackSupplier == null) {
            return;
        }

        int target = normalizeCount(purchaseOrderTarget, 12, 0, 120);
        long existingSampleCount = countSamplePurchaseOrders();
        int toCreate = (int) Math.max(0, target - existingSampleCount);
        if (toCreate == 0) {
            return;
        }

        Map<Long, Product> productMap = new HashMap<>();
        for (Product product : productRepository.findAll()) {
            productMap.put(product.getId(), product);
        }

        int created = 0;
        for (int index = 0; index < toCreate; index++) {
            List<ReplenishmentSuggestionResponse> suggestions = purchaseOrderService.getReplenishmentSuggestions();
            List<ReplenishmentSuggestionResponse> selected = selectSuggestions(suggestions, index);
            if (selected.isEmpty()) {
                break;
            }

            Long supplierId = selected.stream()
                    .map(ReplenishmentSuggestionResponse::suggestedSupplierId)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(fallbackSupplier.getId());

            List<CreatePurchaseOrderItemRequest> items = new ArrayList<>();
            for (ReplenishmentSuggestionResponse suggestion : selected) {
                int quantity = Math.max(1, Math.min(suggestion.suggestedQuantity(), 60));
                BigDecimal unitCost = resolveSuggestedUnitCost(suggestion, productMap.get(suggestion.productId()));
                items.add(new CreatePurchaseOrderItemRequest(suggestion.productId(), quantity, unitCost));
            }

            try {
                var createdOrder = purchaseOrderService.createPurchaseOrder(
                        new CreatePurchaseOrderRequest(
                                supplierId,
                                null,
                                SAMPLE_TAG + " 自動生成PO",
                                items
                        )
                );

                // 状態の偏りを防ぐため、全量入荷・部分入荷・キャンセルを混在させる。
                if (index % 4 == 0) {
                    purchaseOrderService.receivePurchaseOrder(createdOrder.id(), new ReceivePurchaseOrderRequest(List.of()));
                } else if (index % 4 == 1 && !createdOrder.items().isEmpty()) {
                    var firstItem = createdOrder.items().get(0);
                    int partialQuantity = Math.max(1, firstItem.quantity() / 2);
                    purchaseOrderService.receivePurchaseOrder(
                            createdOrder.id(),
                            new ReceivePurchaseOrderRequest(
                                    List.of(new ReceivePurchaseOrderItemRequest(firstItem.productId(), partialQuantity))
                            )
                    );
                } else if (index % 4 == 2) {
                    purchaseOrderService.cancelPurchaseOrder(createdOrder.id());
                }
                created++;
            } catch (RuntimeException ex) {
                log.warn("Skip sample purchase order create. reason={}", ex.getMessage());
            }
        }
        log.info("Sample purchase orders seeded: {}", created);
    }

    private List<ReplenishmentSuggestionResponse> selectSuggestions(List<ReplenishmentSuggestionResponse> suggestions, int seed) {
        if (suggestions == null || suggestions.isEmpty()) {
            return List.of();
        }

        int lineCount = 1 + (seed % 3);
        int start = (seed * 2) % suggestions.size();
        Set<Long> usedProductIds = new HashSet<>();
        List<ReplenishmentSuggestionResponse> selected = new ArrayList<>();

        for (int offset = 0; offset < suggestions.size() && selected.size() < lineCount; offset++) {
            ReplenishmentSuggestionResponse suggestion = suggestions.get((start + offset) % suggestions.size());
            if (suggestion.suggestedQuantity() == null || suggestion.suggestedQuantity() <= 0) {
                continue;
            }
            if (suggestion.productId() == null || !usedProductIds.add(suggestion.productId())) {
                continue;
            }
            selected.add(suggestion);
        }
        return selected;
    }

    private BigDecimal resolveSuggestedUnitCost(ReplenishmentSuggestionResponse suggestion, Product product) {
        if (suggestion.suggestedUnitCost() != null) {
            return suggestion.suggestedUnitCost();
        }
        if (product != null && product.getUnitPrice() != null) {
            return unitCost(product.getUnitPrice(), "0.62");
        }
        return BigDecimal.valueOf(1000).setScale(2, RoundingMode.HALF_UP);
    }

    private long countSampleSalesOrders() {
        return salesOrderRepository.findAllDetailed().stream()
                .map(SalesOrder::getCustomerName)
                .filter(Objects::nonNull)
                .filter(name -> name.startsWith(SAMPLE_TAG))
                .count();
    }

    private long countSamplePurchaseOrders() {
        return purchaseOrderRepository.findAllDetailed().stream()
                .map(PurchaseOrder::getNote)
                .filter(Objects::nonNull)
                .filter(note -> note.startsWith(SAMPLE_TAG))
                .count();
    }

    private boolean isSampleProduct(Product product) {
        return product.getDescription() != null && product.getDescription().startsWith(SAMPLE_TAG);
    }

    private String resolvePrimarySupplierCode(String categoryCode) {
        if (categoryCode == null) {
            return "SUP-GEN";
        }
        if (categoryCode.startsWith("HK_")) {
            return "SUP-HK";
        }
        if (categoryCode.startsWith("FG_")) {
            return "SUP-FG";
        }
        if (categoryCode.startsWith("MNT_")) {
            return "SUP-MNT";
        }
        return "SUP-GEN";
    }

    private BigDecimal unitCost(BigDecimal unitPrice, String ratio) {
        if (unitPrice == null) {
            return BigDecimal.valueOf(1000).setScale(2, RoundingMode.HALF_UP);
        }
        return unitPrice.multiply(new BigDecimal(ratio)).setScale(2, RoundingMode.HALF_UP);
    }

    private int normalizeCount(int value, int defaultValue, int min, int max) {
        int base = value < 0 ? defaultValue : value;
        return Math.max(min, Math.min(base, max));
    }

    private record CategorySeed(
            String code,
            String name,
            String parentCode,
            int sortOrder,
            String skuPrefix,
            int skuSequenceDigits
    ) {
    }

    private record ProductLineSeed(
            String categoryCode,
            String skuPrefix,
            String namePrefix,
            BigDecimal basePrice,
            BigDecimal priceStep,
            List<String> variants
    ) {
    }

    private record SupplierSeed(
            String code,
            String name,
            String contactName,
            String email,
            String phone,
            String note
    ) {
    }
}
