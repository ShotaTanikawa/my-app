package com.example.backend.product;

import com.example.backend.audit.AuditLogService;
import com.example.backend.common.BusinessRuleException;
import com.example.backend.product.dto.CreateProductCategoryRequest;
import com.example.backend.product.dto.ProductCategoryResponse;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 商品カテゴリの業務処理をまとめるサービス。
 */
@Service
public class ProductCategoryService {

    private final ProductCategoryRepository productCategoryRepository;
    private final AuditLogService auditLogService;

    public ProductCategoryService(
            ProductCategoryRepository productCategoryRepository,
            AuditLogService auditLogService
    ) {
        this.productCategoryRepository = productCategoryRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<ProductCategoryResponse> getCategories() {
        return productCategoryRepository.findAll(
                        Sort.by(Sort.Order.asc("sortOrder"), Sort.Order.asc("id"))
                ).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ProductCategoryResponse createCategory(CreateProductCategoryRequest request) {
        if (productCategoryRepository.existsByCode(request.code())) {
            throw new BusinessRuleException("Category code already exists: " + request.code());
        }

        ProductCategory category = new ProductCategory();
        category.setCode(request.code().trim());
        category.setName(request.name().trim());
        category.setActive(request.active() == null ? Boolean.TRUE : request.active());
        category.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());

        ProductCategory saved = productCategoryRepository.save(category);
        auditLogService.log(
                "CATEGORY_CREATE",
                "CATEGORY",
                saved.getId().toString(),
                "code=" + saved.getCode() + ", name=" + saved.getName()
        );
        return toResponse(saved);
    }

    private ProductCategoryResponse toResponse(ProductCategory category) {
        return new ProductCategoryResponse(
                category.getId(),
                category.getCode(),
                category.getName(),
                category.getActive(),
                category.getSortOrder()
        );
    }
}

