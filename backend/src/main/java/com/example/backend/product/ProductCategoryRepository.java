package com.example.backend.product;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 商品カテゴリマスタ用リポジトリ。
 */
public interface ProductCategoryRepository extends JpaRepository<ProductCategory, Long> {

    boolean existsByCode(String code);
}

