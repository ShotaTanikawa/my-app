package com.example.backend.audit;

import org.springframework.data.jpa.repository.JpaRepository;
/**
 * JPA経由で永続化データへアクセスするリポジトリ。
 */

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}
