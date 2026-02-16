package com.example.backend.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.OffsetDateTime;
/**
 * JPA経由で永続化データへアクセスするリポジトリ。
 */

public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {
    // 保持期限より古い監査ログを一括削除する。
    long deleteByCreatedAtBefore(OffsetDateTime cutoff);
}
