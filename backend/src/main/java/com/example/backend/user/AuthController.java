package com.example.backend.user;

import com.example.backend.audit.AuditLogService;
import com.example.backend.common.ResourceNotFoundException;
import com.example.backend.security.JwtService;
import com.example.backend.security.RefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
/**
 * HTTPリクエストを受けてユースケースを公開するコントローラ。
 */

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final AppUserRepository appUserRepository;
    private final RefreshTokenService refreshTokenService;
    private final AuditLogService auditLogService;

    public AuthController(
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            AppUserRepository appUserRepository,
            RefreshTokenService refreshTokenService,
            AuditLogService auditLogService
    ) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.appUserRepository = appUserRepository;
        this.refreshTokenService = refreshTokenService;
        this.auditLogService = auditLogService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password())
            );
        } catch (AuthenticationException ex) {
            throw new BadCredentialsException("Invalid username or password");
        }

        String role = authentication.getAuthorities().stream()
                .findFirst()
                .map(grantedAuthority -> grantedAuthority.getAuthority().replace("ROLE_", ""))
                .orElse("UNKNOWN");

        AppUser user = appUserRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));

        String accessToken = jwtService.generateToken(authentication.getName(), role);
        String refreshToken = refreshTokenService.issueToken(user, resolveDeviceContext(servletRequest));
        auditLogService.logAs(
                authentication.getName(),
                role,
                "AUTH_LOGIN",
                "USER",
                user.getId().toString(),
                "login succeeded, ip=" + resolveClientIp(servletRequest)
        );
        return new LoginResponse(
                accessToken,
                "Bearer",
                jwtService.getExpirationSeconds(),
                refreshToken,
                new MeResponse(authentication.getName(), role)
        );
    }

    @PostMapping("/refresh")
    public LoginResponse refresh(@Valid @RequestBody RefreshRequest request, HttpServletRequest servletRequest) {
        RefreshTokenService.RotatedToken rotatedToken = refreshTokenService.rotateToken(
                request.refreshToken(),
                resolveDeviceContext(servletRequest)
        );
        AppUser user = rotatedToken.user();
        String accessToken = jwtService.generateToken(user.getUsername(), user.getRole().name());
        String refreshToken = rotatedToken.refreshToken();
        auditLogService.logAs(
                user.getUsername(),
                user.getRole().name(),
                "AUTH_REFRESH",
                "USER",
                user.getId().toString(),
                "token refreshed, sessionId=" + rotatedToken.sessionId()
        );

        return new LoginResponse(
                accessToken,
                "Bearer",
                jwtService.getExpirationSeconds(),
                refreshToken,
                new MeResponse(user.getUsername(), user.getRole().name())
        );
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest request) {
        refreshTokenService.revokeToken(request.refreshToken())
                .ifPresent(revoked -> auditLogService.logAs(
                        revoked.user().getUsername(),
                        revoked.user().getRole().name(),
                        "AUTH_LOGOUT",
                        "USER",
                        revoked.user().getId().toString(),
                        "logout succeeded, sessionId=" + revoked.sessionId()
                ));
    }

    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER')")
    public MeResponse me(Authentication authentication) {
        String role = authentication.getAuthorities().stream()
                .findFirst()
                .map(grantedAuthority -> grantedAuthority.getAuthority().replace("ROLE_", ""))
                .orElse("UNKNOWN");

        return new MeResponse(authentication.getName(), role);
    }

    @GetMapping("/sessions")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER')")
    public List<SessionResponse> getSessions(Authentication authentication) {
        AppUser currentUser = getAuthenticatedUser(authentication);
        return refreshTokenService.getActiveSessions(currentUser.getId()).stream()
                .map(session -> new SessionResponse(
                        session.sessionId(),
                        session.userAgent(),
                        session.ipAddress(),
                        session.createdAt(),
                        session.lastUsedAt(),
                        session.expiresAt()
                ))
                .toList();
    }

    @DeleteMapping("/sessions/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER')")
    public void revokeSession(@PathVariable String sessionId, Authentication authentication) {
        AppUser currentUser = getAuthenticatedUser(authentication);
        boolean revoked = refreshTokenService.revokeSession(currentUser.getId(), sessionId);
        if (!revoked) {
            throw new ResourceNotFoundException("Session not found: " + sessionId);
        }

        auditLogService.logAs(
                currentUser.getUsername(),
                currentUser.getRole().name(),
                "AUTH_SESSION_REVOKE",
                "SESSION",
                sessionId,
                "session revoked manually"
        );
    }

    private AppUser getAuthenticatedUser(Authentication authentication) {
        return appUserRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));
    }

    private RefreshTokenService.DeviceContext resolveDeviceContext(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        String clientIp = resolveClientIp(request);
        return new RefreshTokenService.DeviceContext(userAgent, clientIp);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            String[] values = forwardedFor.split(",");
            return values[0].trim();
        }
        return request.getRemoteAddr();
    }

    public record MeResponse(String username, String role) {
    }

    public record SessionResponse(
            String sessionId,
            String userAgent,
            String ipAddress,
            OffsetDateTime createdAt,
            OffsetDateTime lastUsedAt,
            OffsetDateTime expiresAt
    ) {
    }

    public record LoginRequest(
            @NotBlank String username,
            @NotBlank String password
    ) {
    }

    public record LoginResponse(
            String accessToken,
            String tokenType,
            long expiresIn,
            String refreshToken,
            MeResponse user
    ) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }
}
