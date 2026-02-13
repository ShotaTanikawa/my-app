package com.example.backend.product;

import com.example.backend.common.BusinessRuleException;
import com.example.backend.common.ResourceNotFoundException;
import com.example.backend.inventory.Inventory;
import com.example.backend.inventory.InventoryRepository;
import com.example.backend.product.dto.CreateProductRequest;
import com.example.backend.product.dto.ProductResponse;
import com.example.backend.product.dto.UpdateProductRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;

    public ProductService(ProductRepository productRepository, InventoryRepository inventoryRepository) {
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
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
        Product savedProduct = productRepository.save(product);

        // 商品作成時に在庫レコードを1件同時作成し、1商品1在庫を保証する。
        Inventory inventory = new Inventory();
        inventory.setProduct(savedProduct);
        inventory.setAvailableQuantity(0);
        inventory.setReservedQuantity(0);
        Inventory savedInventory = inventoryRepository.save(inventory);

        return toResponse(savedProduct, savedInventory);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getProducts() {
        List<Product> products = productRepository.findAll();
        Map<Long, Inventory> inventories = inventoryRepository.findAll().stream()
                .collect(Collectors.toMap(inv -> inv.getProduct().getId(), inv -> inv));

        return products.stream()
                .map(product -> toResponse(product, inventories.get(product.getId())))
                .toList();
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

        Product updatedProduct = productRepository.save(product);
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found for product: " + productId));

        return toResponse(updatedProduct, inventory);
    }

    @Transactional
    public ProductResponse addStock(Long productId, Integer quantity) {
        Product product = findProductById(productId);
        // 複数オペレータの同時入庫で更新が競合しないよう悲観ロックで更新する。
        Inventory inventory = inventoryRepository.findByProductIdForUpdate(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found for product: " + productId));

        inventory.setAvailableQuantity(inventory.getAvailableQuantity() + quantity);
        Inventory updatedInventory = inventoryRepository.save(inventory);

        return toResponse(product, updatedInventory);
    }

    private Product findProductById(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));
    }

    private ProductResponse toResponse(Product product, Inventory inventory) {
        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getDescription(),
                product.getUnitPrice(),
                inventory == null ? 0 : inventory.getAvailableQuantity(),
                inventory == null ? 0 : inventory.getReservedQuantity()
        );
    }
}
