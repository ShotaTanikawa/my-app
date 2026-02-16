package com.example.backend.order;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
/**
 * JPA経由で永続化データへアクセスするリポジトリ。
 */

public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long> {

    boolean existsByOrderNumber(String orderNumber);

    @EntityGraph(attributePaths = {"items", "items.product"})
    @Query("select distinct so from SalesOrder so where so.id = :id")
    Optional<SalesOrder> findDetailedById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"items", "items.product"})
    @Query("select distinct so from SalesOrder so order by so.createdAt desc")
    List<SalesOrder> findAllDetailed();

    @EntityGraph(attributePaths = {"items", "items.product"})
    @Query("""
            select distinct so
            from SalesOrder so
            where so.status = :status
              and (:from is null or so.updatedAt >= :from)
              and (:to is null or so.updatedAt <= :to)
            order by so.updatedAt desc, so.id desc
            """)
    List<SalesOrder> findDetailedByStatusAndUpdatedAtBetween(
            @Param("status") OrderStatus status,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to
    );
}
