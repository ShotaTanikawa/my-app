package com.example.backend.supplier;

import com.example.backend.audit.AuditLogService;
import com.example.backend.common.BusinessRuleException;
import com.example.backend.common.ResourceNotFoundException;
import com.example.backend.product.Product;
import com.example.backend.product.ProductRepository;
import com.example.backend.supplier.dto.ProductSupplierResponse;
import com.example.backend.supplier.dto.UpsertProductSupplierRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProductSupplierService {

    private final ProductRepository productRepository;
    private final SupplierRepository supplierRepository;
    private final ProductSupplierRepository productSupplierRepository;
    private final AuditLogService auditLogService;

    public ProductSupplierService(
            ProductRepository productRepository,
            SupplierRepository supplierRepository,
            ProductSupplierRepository productSupplierRepository,
            AuditLogService auditLogService
    ) {
        this.productRepository = productRepository;
        this.supplierRepository = supplierRepository;
        this.productSupplierRepository = productSupplierRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<ProductSupplierResponse> getProductSuppliers(Long productId) {
        ensureProductExists(productId);
        return productSupplierRepository.findByProductIdWithSupplier(productId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ProductSupplierResponse upsertProductSupplier(Long productId, UpsertProductSupplierRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));

        Supplier supplier = supplierRepository.findById(request.supplierId())
                .orElseThrow(() -> new ResourceNotFoundException("Supplier not found: " + request.supplierId()));
        if (!Boolean.TRUE.equals(supplier.getActive())) {
            throw new BusinessRuleException("Supplier is inactive: " + supplier.getCode());
        }

        ProductSupplier contract = productSupplierRepository.findByProductIdAndSupplierId(productId, request.supplierId())
                .orElseGet(ProductSupplier::new);

        contract.setProduct(product);
        contract.setSupplier(supplier);
        contract.setUnitCost(request.unitCost());
        contract.setLeadTimeDays(normalizeNonNegative(request.leadTimeDays(), 0));
        contract.setMoq(normalizeAtLeast(request.moq(), 1));
        contract.setLotSize(normalizeAtLeast(request.lotSize(), 1));
        contract.setPrimarySupplier(Boolean.TRUE.equals(request.primary()));

        ProductSupplier saved = productSupplierRepository.save(contract);

        if (Boolean.TRUE.equals(saved.getPrimarySupplier())) {
            // 主仕入先は商品ごとに1件へ収束させる。
            for (ProductSupplier other : productSupplierRepository.findByProductIdWithSupplier(productId)) {
                if (!other.getId().equals(saved.getId()) && Boolean.TRUE.equals(other.getPrimarySupplier())) {
                    other.setPrimarySupplier(false);
                }
            }
        }

        auditLogService.log(
                "PRODUCT_SUPPLIER_UPSERT",
                "PRODUCT",
                product.getId().toString(),
                "productSku=" + product.getSku()
                        + ", supplierCode=" + supplier.getCode()
                        + ", unitCost=" + saved.getUnitCost()
                        + ", primary=" + saved.getPrimarySupplier()
        );

        return toResponse(saved);
    }

    @Transactional
    public void removeProductSupplier(Long productId, Long supplierId) {
        ProductSupplier contract = productSupplierRepository.findByProductIdAndSupplierId(productId, supplierId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Product-supplier contract not found: productId=" + productId + ", supplierId=" + supplierId
                ));

        productSupplierRepository.delete(contract);
        auditLogService.log(
                "PRODUCT_SUPPLIER_UNLINK",
                "PRODUCT",
                productId.toString(),
                "supplierCode=" + contract.getSupplier().getCode()
        );
    }

    private void ensureProductExists(Long productId) {
        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Product not found: " + productId);
        }
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

    private ProductSupplierResponse toResponse(ProductSupplier contract) {
        Supplier supplier = contract.getSupplier();
        return new ProductSupplierResponse(
                supplier.getId(),
                supplier.getCode(),
                supplier.getName(),
                supplier.getActive(),
                contract.getUnitCost(),
                contract.getLeadTimeDays(),
                contract.getMoq(),
                contract.getLotSize(),
                contract.getPrimarySupplier()
        );
    }
}
