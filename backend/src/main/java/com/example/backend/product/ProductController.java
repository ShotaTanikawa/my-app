package com.example.backend.product;

import com.example.backend.product.dto.AdjustStockRequest;
import com.example.backend.product.dto.CreateProductRequest;
import com.example.backend.product.dto.ProductImportResultResponse;
import com.example.backend.product.dto.ProductPageResponse;
import com.example.backend.product.dto.ProductResponse;
import com.example.backend.product.dto.UpdateProductRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
/**
 * HTTPリクエストを受けてユースケースを公開するコントローラ。
 */

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER')")
    public List<ProductResponse> getProducts() {
        return productService.getProducts();
    }

    @GetMapping("/page")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER')")
    public ProductPageResponse getProductsPage(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Boolean lowStockOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return productService.getProductsPage(q, categoryId, lowStockOnly, page, size);
    }

    @GetMapping("/{productId}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER')")
    public ProductResponse getProduct(@PathVariable Long productId) {
        return productService.getProduct(productId);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ProductImportResultResponse importProducts(@RequestPart("file") MultipartFile file) {
        return productService.importProductsCsv(file);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ProductResponse createProduct(@Valid @RequestBody CreateProductRequest request) {
        return productService.createProduct(request);
    }

    @PutMapping("/{productId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ProductResponse updateProduct(@PathVariable Long productId, @Valid @RequestBody UpdateProductRequest request) {
        return productService.updateProduct(productId, request);
    }

    @PostMapping("/{productId}/stock")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public ProductResponse addStock(@PathVariable Long productId, @Valid @RequestBody AdjustStockRequest request) {
        return productService.addStock(productId, request.quantity());
    }
}
