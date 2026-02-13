package com.example.backend.order;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long> {

    boolean existsByOrderNumber(String orderNumber);

    @EntityGraph(attributePaths = {"items", "items.product"})
    @Query("select distinct so from SalesOrder so where so.id = :id")
    Optional<SalesOrder> findDetailedById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"items", "items.product"})
    @Query("select distinct so from SalesOrder so order by so.createdAt desc")
    List<SalesOrder> findAllDetailed();
}
