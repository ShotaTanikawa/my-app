package com.example.backend.supplier;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
/**
 * JPA経由で永続化データへアクセスするリポジトリ。
 */

public interface SupplierRepository extends JpaRepository<Supplier, Long> {
    boolean existsByCode(String code);
    boolean existsByCodeAndIdNot(String code, Long id);
    List<Supplier> findAllByOrderByActiveDescNameAsc();
    Optional<Supplier> findByCodeIgnoreCase(String code);
}
