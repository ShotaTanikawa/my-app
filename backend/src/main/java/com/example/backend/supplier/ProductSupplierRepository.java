package com.example.backend.supplier;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
/**
 * JPA経由で永続化データへアクセスするリポジトリ。
 */

public interface ProductSupplierRepository extends JpaRepository<ProductSupplier, Long> {

    @Query("""
            select ps
            from ProductSupplier ps
            join fetch ps.supplier s
            where ps.product.id = :productId
            order by ps.primarySupplier desc, s.name asc
            """)
    List<ProductSupplier> findByProductIdWithSupplier(@Param("productId") Long productId);

    Optional<ProductSupplier> findByProductIdAndSupplierId(Long productId, Long supplierId);

    @Query("""
            select ps
            from ProductSupplier ps
            join fetch ps.supplier s
            where ps.product.id in :productIds and s.active = true
            order by ps.primarySupplier desc, ps.unitCost asc
            """)
    List<ProductSupplier> findActiveByProductIds(@Param("productIds") Collection<Long> productIds);
}
