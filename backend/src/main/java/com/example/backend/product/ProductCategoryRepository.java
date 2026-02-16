package com.example.backend.product;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 商品カテゴリマスタ用リポジトリ。
 */
public interface ProductCategoryRepository extends JpaRepository<ProductCategory, Long> {

    boolean existsByCode(String code);

    Optional<ProductCategory> findByCode(String code);

    Optional<ProductCategory> findByCodeIgnoreCase(String code);
}
