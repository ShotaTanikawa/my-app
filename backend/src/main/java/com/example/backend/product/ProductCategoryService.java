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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Locale;

import java.util.List;
import java.util.Set;

/**
 * 商品カテゴリの業務処理をまとめるサービス。
 */
@Service
public class ProductCategoryService {

    private static final int DEFAULT_SKU_SEQUENCE_DIGITS = 4;
    private static final int MIN_SKU_SEQUENCE_DIGITS = 3;
    private static final int MAX_SKU_SEQUENCE_DIGITS = 6;
    private static final int MAX_CATEGORY_DEPTH = 3;

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
        List<ProductCategory> categories = productCategoryRepository.findAll(
                Sort.by(Sort.Order.asc("sortOrder"), Sort.Order.asc("id"))
        );
        return buildHierarchyResponses(categories);
    }

    @Transactional
    public ProductCategoryResponse createCategory(CreateProductCategoryRequest request) {
        if (productCategoryRepository.existsByCode(request.code())) {
            throw new BusinessRuleException("Category code already exists: " + request.code());
        }

        ProductCategory category = new ProductCategory();
        category.setCode(request.code().trim());
        category.setName(request.name().trim());
        category.setParent(resolveParent(request.parentId()));
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
                        + ", parentId=" + (saved.getParent() == null ? "null" : saved.getParent().getId())
                        + ", skuPrefix=" + saved.getSkuPrefix()
                        + ", skuSequenceDigits=" + saved.getSkuSequenceDigits()
        );
        return toResponseWithHierarchy(saved);
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
        return toResponseWithHierarchy(saved);
    }

    private List<ProductCategoryResponse> buildHierarchyResponses(List<ProductCategory> categories) {
        if (categories.isEmpty()) {
            return List.of();
        }

        Map<Long, ProductCategory> categoryById = new HashMap<>();
        Map<Long, List<ProductCategory>> childrenByParentId = new HashMap<>();
        List<ProductCategory> roots = new ArrayList<>();

        for (ProductCategory category : categories) {
            categoryById.put(category.getId(), category);
        }

        for (ProductCategory category : categories) {
            Long parentId = category.getParent() == null ? null : category.getParent().getId();
            if (parentId == null || !categoryById.containsKey(parentId)) {
                roots.add(category);
                continue;
            }
            childrenByParentId.computeIfAbsent(parentId, key -> new ArrayList<>()).add(category);
        }

        Comparator<ProductCategory> comparator = Comparator
                .comparing(ProductCategory::getSortOrder)
                .thenComparing(ProductCategory::getId);
        roots.sort(comparator);
        for (List<ProductCategory> children : childrenByParentId.values()) {
            children.sort(comparator);
        }

        List<ProductCategoryResponse> responses = new ArrayList<>(categories.size());
        Set<Long> visited = new HashSet<>();
        for (ProductCategory root : roots) {
            appendCategoryHierarchy(root, 0, root.getName(), childrenByParentId, responses, visited);
        }

        // 何らかの不整合で辿れなかったカテゴリもレスポンスに含める。
        if (responses.size() < categories.size()) {
            for (ProductCategory category : categories) {
                if (visited.contains(category.getId())) {
                    continue;
                }
                HierarchyMeta meta = resolveHierarchyMeta(category);
                responses.add(toResponse(category, meta.depth(), meta.pathName()));
            }
        }
        return responses;
    }

    private void appendCategoryHierarchy(
            ProductCategory category,
            int depth,
            String pathName,
            Map<Long, List<ProductCategory>> childrenByParentId,
            List<ProductCategoryResponse> responses,
            Set<Long> visited
    ) {
        if (!visited.add(category.getId())) {
            return;
        }
        responses.add(toResponse(category, depth, pathName));

        List<ProductCategory> children = childrenByParentId.get(category.getId());
        if (children == null || children.isEmpty()) {
            return;
        }
        for (ProductCategory child : children) {
            appendCategoryHierarchy(
                    child,
                    depth + 1,
                    pathName + " / " + child.getName(),
                    childrenByParentId,
                    responses,
                    visited
            );
        }
    }

    private ProductCategoryResponse toResponseWithHierarchy(ProductCategory category) {
        HierarchyMeta hierarchyMeta = resolveHierarchyMeta(category);
        return toResponse(category, hierarchyMeta.depth(), hierarchyMeta.pathName());
    }

    private ProductCategoryResponse toResponse(ProductCategory category, int depth, String pathName) {
        ProductCategory parent = category.getParent();
        return new ProductCategoryResponse(
                category.getId(),
                category.getCode(),
                category.getName(),
                parent == null ? null : parent.getId(),
                parent == null ? null : parent.getCode(),
                parent == null ? null : parent.getName(),
                depth,
                pathName,
                category.getActive(),
                category.getSortOrder(),
                category.getSkuPrefix(),
                category.getSkuSequenceDigits()
        );
    }

    private ProductCategory resolveParent(Long parentId) {
        if (parentId == null) {
            return null;
        }

        ProductCategory parent = productCategoryRepository.findById(parentId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + parentId));
        int parentDepth = resolveHierarchyMeta(parent).depth();
        int nextDepth = parentDepth + 1;
        if (nextDepth >= MAX_CATEGORY_DEPTH) {
            throw new BusinessRuleException("カテゴリ階層は最大" + MAX_CATEGORY_DEPTH + "階層までです。");
        }
        return parent;
    }

    private HierarchyMeta resolveHierarchyMeta(ProductCategory category) {
        List<String> names = new ArrayList<>();
        ProductCategory cursor = category;
        int depth = 0;
        int guard = 0;

        while (cursor != null) {
            guard++;
            if (guard > 32) {
                throw new BusinessRuleException("カテゴリ階層が不正です。");
            }
            names.add(0, cursor.getName());
            ProductCategory parent = cursor.getParent();
            if (parent != null) {
                depth++;
            }
            cursor = parent;
        }
        return new HierarchyMeta(depth, String.join(" / ", names));
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

    private record HierarchyMeta(int depth, String pathName) {
    }
}
