package com.example.backend.audit;

import com.example.backend.audit.dto.AuditLogPageResponse;
import com.example.backend.audit.dto.AuditLogResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
/**
 * ドメインルールと業務処理をまとめるサービス。
 */

@Service
public class AuditLogService {

    private static final String SYSTEM_USER = "SYSTEM";
    private static final String SYSTEM_ROLE = "SYSTEM";
    private static final String AUDIT_LOG_CLEANUP_ACTION = "AUDIT_LOG_CLEANUP";

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(readOnly = true)
    public AuditLogPageResponse getLogs(
            int page,
            int size,
            String action,
            String actor,
            OffsetDateTime from,
            OffsetDateTime to
    ) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 200));
        PageRequest pageable = PageRequest.of(
                safePage,
                safeSize,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );

        Specification<AuditLog> specification = buildSpecification(action, actor, from, to);

        Page<AuditLogResponse> pageResult = auditLogRepository.findAll(specification, pageable)
                .map(this::toResponse);

        return new AuditLogPageResponse(
                pageResult.getContent(),
                pageResult.getNumber(),
                pageResult.getSize(),
                pageResult.getTotalElements(),
                pageResult.getTotalPages(),
                pageResult.hasNext(),
                pageResult.hasPrevious()
        );
    }

    @Transactional(readOnly = true)
    public List<AuditLogResponse> getLogsForExport(
            String action,
            String actor,
            OffsetDateTime from,
            OffsetDateTime to,
            int limit
    ) {
        int safeLimit = Math.max(1, Math.min(limit, 5000));
        PageRequest pageable = PageRequest.of(
                0,
                safeLimit,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );

        return auditLogRepository.findAll(buildSpecification(action, actor, from, to), pageable).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AuditLogResponse> getRecentLogs(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        PageRequest pageable = PageRequest.of(
                0,
                safeLimit,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );

        return auditLogRepository.findAll(pageable).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void log(String action, String targetType, String targetId, String detail) {
        Actor actor = resolveActorFromContext();
        save(actor.username(), actor.role(), action, targetType, targetId, detail);
    }

    @Transactional
    public void logAs(String actorUsername, String actorRole, String action, String targetType, String targetId, String detail) {
        String username = actorUsername == null || actorUsername.isBlank() ? SYSTEM_USER : actorUsername;
        String role = actorRole == null || actorRole.isBlank() ? SYSTEM_ROLE : actorRole;
        save(username, role, action, targetType, targetId, detail);
    }

    @Transactional
    public AuditLogCleanupResult cleanupExpiredLogs(int retentionDays, CleanupTrigger trigger) {
        // 不正値が来ても監査ログ全消去を避けるため1日以上へ補正する。
        int safeRetentionDays = Math.max(1, retentionDays);
        OffsetDateTime executedAt = OffsetDateTime.now();
        OffsetDateTime cutoff = executedAt.minusDays(safeRetentionDays);
        long deletedCount = auditLogRepository.deleteByCreatedAtBefore(cutoff);

        String detail = "trigger=" + trigger
                + ", retentionDays=" + safeRetentionDays
                + ", cutoff=" + cutoff
                + ", deletedCount=" + deletedCount;

        if (trigger == CleanupTrigger.MANUAL) {
            log(AUDIT_LOG_CLEANUP_ACTION, "AUDIT_LOG", null, detail);
        } else {
            logAs(SYSTEM_USER, SYSTEM_ROLE, AUDIT_LOG_CLEANUP_ACTION, "AUDIT_LOG", null, detail);
        }

        return new AuditLogCleanupResult(deletedCount, safeRetentionDays, cutoff, executedAt);
    }

    private void save(String actorUsername, String actorRole, String action, String targetType, String targetId, String detail) {
        AuditLog auditLog = new AuditLog();
        auditLog.setActorUsername(actorUsername);
        auditLog.setActorRole(actorRole);
        auditLog.setAction(action);
        auditLog.setTargetType(targetType);
        auditLog.setTargetId(targetId);
        auditLog.setDetail(detail);
        auditLogRepository.save(auditLog);
    }

    private Specification<AuditLog> buildSpecification(
            String action,
            String actor,
            OffsetDateTime from,
            OffsetDateTime to
    ) {
        Specification<AuditLog> specification = (root, query, criteriaBuilder) -> criteriaBuilder.conjunction();

        if (action != null && !action.isBlank()) {
            String normalizedAction = action.trim();
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("action"), normalizedAction));
        }

        if (actor != null && !actor.isBlank()) {
            String likePattern = "%" + actor.trim().toLowerCase() + "%";
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("actorUsername")), likePattern));
        }

        if (from != null) {
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), from));
        }

        if (to != null) {
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), to));
        }

        return specification;
    }

    private Actor resolveActorFromContext() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getName())) {
            return new Actor(SYSTEM_USER, SYSTEM_ROLE);
        }

        String role = authentication.getAuthorities().stream()
                .findFirst()
                .map(GrantedAuthority::getAuthority)
                .map(value -> value.replace("ROLE_", ""))
                .orElse(SYSTEM_ROLE);

        return new Actor(authentication.getName(), role);
    }

    private AuditLogResponse toResponse(AuditLog auditLog) {
        return new AuditLogResponse(
                auditLog.getId(),
                auditLog.getActorUsername(),
                auditLog.getActorRole(),
                auditLog.getAction(),
                auditLog.getTargetType(),
                auditLog.getTargetId(),
                auditLog.getDetail(),
                auditLog.getCreatedAt()
        );
    }

    private record Actor(String username, String role) {
    }

    public enum CleanupTrigger {
        SCHEDULED,
        MANUAL
    }

    public record AuditLogCleanupResult(
            long deletedCount,
            int retentionDays,
            OffsetDateTime cutoff,
            OffsetDateTime executedAt
    ) {
    }
}
