package com.example.backend.product;

import com.example.backend.audit.AuditLogService;
import com.example.backend.common.BusinessRuleException;
import com.example.backend.common.ResourceNotFoundException;
import com.example.backend.product.dto.CreateProductCategoryRequest;
import com.example.backend.product.dto.ProductCategoryResponse;
import com.example.backend.product.dto.UpdateCategorySkuRuleRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

import java.util.List;

/**
 * 商品カテゴリの業務処理をまとめるサービス。
 */
@Service
public class ProductCategoryService {

    private static final int DEFAULT_SKU_SEQUENCE_DIGITS = 4;
    private static final int MIN_SKU_SEQUENCE_DIGITS = 3;
    private static final int MAX_SKU_SEQUENCE_DIGITS = 6;

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
        category.setSkuPrefix(normalizeSkuPrefix(request.skuPrefix()));
        category.setSkuSequenceDigits(normalizeSkuSequenceDigits(request.skuSequenceDigits()));

        ProductCategory saved = productCategoryRepository.save(category);
        auditLogService.log(
                "CATEGORY_CREATE",
                "CATEGORY",
                saved.getId().toString(),
                "code=" + saved.getCode() + ", name=" + saved.getName()
                        + ", skuPrefix=" + saved.getSkuPrefix()
                        + ", skuSequenceDigits=" + saved.getSkuSequenceDigits()
        );
        return toResponse(saved);
    }

    @Transactional
    public ProductCategoryResponse updateCategorySkuRule(Long categoryId, UpdateCategorySkuRuleRequest request) {
        ProductCategory category = productCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + categoryId));

        category.setSkuPrefix(normalizeSkuPrefix(request.skuPrefix()));
        category.setSkuSequenceDigits(normalizeSkuSequenceDigits(request.skuSequenceDigits()));

        ProductCategory saved = productCategoryRepository.save(category);
        auditLogService.log(
                "CATEGORY_SKU_RULE_UPDATE",
                "CATEGORY",
                saved.getId().toString(),
                "skuPrefix=" + saved.getSkuPrefix() + ", skuSequenceDigits=" + saved.getSkuSequenceDigits()
        );
        return toResponse(saved);
    }

    private ProductCategoryResponse toResponse(ProductCategory category) {
        return new ProductCategoryResponse(
                category.getId(),
                category.getCode(),
                category.getName(),
                category.getActive(),
                category.getSortOrder(),
                category.getSkuPrefix(),
                category.getSkuSequenceDigits()
        );
    }

    private Integer normalizeSkuSequenceDigits(Integer value) {
        if (value == null) {
            return DEFAULT_SKU_SEQUENCE_DIGITS;
        }
        if (value < MIN_SKU_SEQUENCE_DIGITS || value > MAX_SKU_SEQUENCE_DIGITS) {
            throw new BusinessRuleException("SKU連番桁数は " + MIN_SKU_SEQUENCE_DIGITS + "〜" + MAX_SKU_SEQUENCE_DIGITS + " で指定してください。");
        }
        return value;
    }

    private String normalizeSkuPrefix(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT);
        normalized = normalized.replaceAll("[^A-Z0-9-]+", "-");
        normalized = normalized.replaceAll("-{2,}", "-");
        normalized = normalized.replaceAll("^-+|-+$", "");
        if (normalized.isBlank()) {
            throw new BusinessRuleException("SKUプレフィックスの形式が不正です。");
        }
        if (normalized.length() > 20) {
            normalized = normalized.substring(0, 20);
            normalized = normalized.replaceAll("-+$", "");
        }
        if (normalized.isBlank()) {
            throw new BusinessRuleException("SKUプレフィックスの形式が不正です。");
        }
        return normalized;
    }
}
