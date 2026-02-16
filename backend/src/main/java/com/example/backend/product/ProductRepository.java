package com.example.backend.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
/**
 * JPA経由で永続化データへアクセスするリポジトリ。
 */

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {
    boolean existsBySku(String sku);
    boolean existsBySkuIgnoreCase(String sku);

    Optional<Product> findBySku(String sku);
    Optional<Product> findBySkuIgnoreCase(String sku);

    Optional<Product> findTopBySkuStartingWithOrderBySkuDesc(String skuPrefix);
}
