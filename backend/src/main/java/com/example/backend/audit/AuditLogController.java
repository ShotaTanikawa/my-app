package com.example.backend.audit;

import com.example.backend.audit.dto.AuditLogCleanupResponse;
import com.example.backend.audit.dto.AuditLogPageResponse;
import com.example.backend.audit.dto.AuditLogResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
/**
 * HTTPリクエストを受けてユースケースを公開するコントローラ。
 */

@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogController {

    private final AuditLogService auditLogService;
    private final int defaultRetentionDays;

    public AuditLogController(
            AuditLogService auditLogService,
            @Value("${jobs.audit-log-retention-days:90}") int defaultRetentionDays
    ) {
        this.auditLogService = auditLogService;
        this.defaultRetentionDays = defaultRetentionDays;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public AuditLogPageResponse getAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to
    ) {
        return auditLogService.getLogs(page, size, action, actor, from, to);
    }

    @GetMapping(value = "/export.csv", produces = "text/csv")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> exportAuditLogsCsv(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "1000") int limit
    ) {
        List<AuditLogResponse> logs = auditLogService.getLogsForExport(action, actor, from, to, limit);
        String csv = toCsv(logs);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"audit-logs.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv);
    }

    @PostMapping("/cleanup")
    @PreAuthorize("hasRole('ADMIN')")
    public AuditLogCleanupResponse cleanupAuditLogs(
            @RequestParam(required = false) Integer retentionDays
    ) {
        // 指定がない場合はサーバ設定の既定保持日数で実行する。
        int resolvedRetentionDays = retentionDays == null ? defaultRetentionDays : retentionDays;
        AuditLogService.AuditLogCleanupResult result = auditLogService.cleanupExpiredLogs(
                resolvedRetentionDays,
                AuditLogService.CleanupTrigger.MANUAL
        );
        return new AuditLogCleanupResponse(
                result.deletedCount(),
                result.retentionDays(),
                result.cutoff(),
                result.executedAt()
        );
    }

    private String toCsv(List<AuditLogResponse> logs) {
        StringBuilder builder = new StringBuilder();
        builder.append("createdAt,actorUsername,actorRole,action,targetType,targetId,detail\n");

        for (AuditLogResponse log : logs) {
            builder.append(escapeCsv(log.createdAt() == null ? "" : log.createdAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)))
                    .append(',')
                    .append(escapeCsv(log.actorUsername()))
                    .append(',')
                    .append(escapeCsv(log.actorRole()))
                    .append(',')
                    .append(escapeCsv(log.action()))
                    .append(',')
                    .append(escapeCsv(log.targetType()))
                    .append(',')
                    .append(escapeCsv(log.targetId()))
                    .append(',')
                    .append(escapeCsv(log.detail()))
                    .append('\n');
        }

        return builder.toString();
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "\"\"";
        }

        String escaped = value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }
}
