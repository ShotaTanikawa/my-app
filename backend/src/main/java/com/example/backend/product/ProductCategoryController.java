package com.example.backend.product;

import com.example.backend.product.dto.CreateProductCategoryRequest;
import com.example.backend.product.dto.ProductCategoryResponse;
import com.example.backend.product.dto.UpdateCategorySkuRuleRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 商品カテゴリAPIを公開するコントローラ。
 */
@RestController
@RequestMapping("/api/product-categories")
public class ProductCategoryController {

    private final ProductCategoryService productCategoryService;

    public ProductCategoryController(ProductCategoryService productCategoryService) {
        this.productCategoryService = productCategoryService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER')")
    public List<ProductCategoryResponse> getCategories() {
        return productCategoryService.getCategories();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ProductCategoryResponse createCategory(@Valid @RequestBody CreateProductCategoryRequest request) {
        return productCategoryService.createCategory(request);
    }

    @PutMapping("/{categoryId}/sku-rule")
    @PreAuthorize("hasRole('ADMIN')")
    public ProductCategoryResponse updateCategorySkuRule(
            @PathVariable Long categoryId,
            @Valid @RequestBody UpdateCategorySkuRuleRequest request
    ) {
        return productCategoryService.updateCategorySkuRule(categoryId, request);
    }
}
