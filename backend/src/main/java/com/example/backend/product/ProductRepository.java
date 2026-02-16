package com.example.backend.product;

import org.springframework.data.jpa.repository.JpaRepository;
/**
 * JPA経由で永続化データへアクセスするリポジトリ。
 */

public interface ProductRepository extends JpaRepository<Product, Long> {
    boolean existsBySku(String sku);
}
