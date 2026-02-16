package com.example.backend.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
/**
 * JPA経由で永続化データへアクセスするリポジトリ。
 */

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByProductId(Long productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Inventory i where i.product.id = :productId")
    Optional<Inventory> findByProductIdForUpdate(@Param("productId") Long productId);

    @Query("select i from Inventory i join fetch i.product p where i.availableQuantity <= :threshold order by i.availableQuantity asc")
    List<Inventory> findLowStockInventories(@Param("threshold") Integer threshold);

    @Query("select i from Inventory i join fetch i.product p order by i.availableQuantity asc")
    List<Inventory> findAllWithProduct();
}
