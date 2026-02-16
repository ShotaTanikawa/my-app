package com.example.backend.jobs;

import com.example.backend.audit.AuditLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 監査ログの保持期限を定期実行で適用するジョブ。
 */
@Component
public class AuditLogRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(AuditLogRetentionJob.class);

    private final AuditLogService auditLogService;
    private final boolean enabled;
    private final int retentionDays;

    public AuditLogRetentionJob(
            AuditLogService auditLogService,
            @Value("${jobs.audit-log-retention-enabled:true}") boolean enabled,
            @Value("${jobs.audit-log-retention-days:90}") int retentionDays
    ) {
        this.auditLogService = auditLogService;
        this.enabled = enabled;
        this.retentionDays = retentionDays;
    }

    @Scheduled(cron = "${jobs.audit-log-retention-cron:0 30 2 * * *}")
    public void runRetentionCleanup() {
        // 運用中に無効化できるよう、実行直前にフラグ判定する。
        if (!enabled) {
            return;
        }

        AuditLogService.AuditLogCleanupResult result = auditLogService.cleanupExpiredLogs(
                retentionDays,
                AuditLogService.CleanupTrigger.SCHEDULED
        );

        log.info(
                "Audit log retention cleanup finished: deletedCount={}, retentionDays={}, cutoff={}",
                result.deletedCount(),
                result.retentionDays(),
                result.cutoff()
        );
    }
}
