package com.example.backend.purchase;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
/**
 * JPA経由で永続化データへアクセスするリポジトリ。
 */

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    boolean existsByOrderNumber(String orderNumber);

    @EntityGraph(attributePaths = {
            "supplier",
            "items",
            "items.product",
            "receipts",
            "receipts.items",
            "receipts.items.product"
    })
    @Query("select distinct po from PurchaseOrder po where po.id = :id")
    Optional<PurchaseOrder> findDetailedById(@Param("id") Long id);

    @EntityGraph(attributePaths = {
            "supplier",
            "items",
            "items.product",
            "receipts",
            "receipts.items",
            "receipts.items.product"
    })
    @Query("select distinct po from PurchaseOrder po order by po.createdAt desc")
    List<PurchaseOrder> findAllDetailed();
}
