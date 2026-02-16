package com.example.backend.supplier;

import com.example.backend.supplier.dto.ProductSupplierResponse;
import com.example.backend.supplier.dto.UpsertProductSupplierRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
/**
 * HTTPリクエストを受けてユースケースを公開するコントローラ。
 */

@RestController
@RequestMapping("/api/products/{productId}/suppliers")
public class ProductSupplierController {

    private final ProductSupplierService productSupplierService;

    public ProductSupplierController(ProductSupplierService productSupplierService) {
        this.productSupplierService = productSupplierService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER')")
    public List<ProductSupplierResponse> getProductSuppliers(@PathVariable Long productId) {
        return productSupplierService.getProductSuppliers(productId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('ADMIN')")
    public ProductSupplierResponse upsertProductSupplier(
            @PathVariable Long productId,
            @Valid @RequestBody UpsertProductSupplierRequest request
    ) {
        return productSupplierService.upsertProductSupplier(productId, request);
    }

    @DeleteMapping("/{supplierId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void removeProductSupplier(@PathVariable Long productId, @PathVariable Long supplierId) {
        productSupplierService.removeProductSupplier(productId, supplierId);
    }
}
