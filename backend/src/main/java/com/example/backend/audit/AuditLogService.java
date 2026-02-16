package com.example.backend.audit;

import com.example.backend.audit.dto.AuditLogResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AuditLogService {

    private static final String SYSTEM_USER = "SYSTEM";
    private static final String SYSTEM_ROLE = "SYSTEM";

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
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
}
