package com.example.backend.user;

import com.example.backend.audit.AuditLogService;
import com.example.backend.common.ResourceNotFoundException;
import com.example.backend.security.JwtService;
import com.example.backend.security.LoginAttemptService;
import com.example.backend.security.PasswordResetService;
import com.example.backend.security.RefreshTokenService;
import com.example.backend.security.TotpService;
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
    private final LoginAttemptService loginAttemptService;
    private final TotpService totpService;
    private final PasswordResetService passwordResetService;

    public AuthController(
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            AppUserRepository appUserRepository,
            RefreshTokenService refreshTokenService,
            AuditLogService auditLogService,
            LoginAttemptService loginAttemptService,
            TotpService totpService,
            PasswordResetService passwordResetService
    ) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.appUserRepository = appUserRepository;
        this.refreshTokenService = refreshTokenService;
        this.auditLogService = auditLogService;
        this.loginAttemptService = loginAttemptService;
        this.totpService = totpService;
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        String clientIp = resolveClientIp(servletRequest);
        loginAttemptService.checkAllowed(request.username(), clientIp);

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password())
            );
        } catch (AuthenticationException ex) {
            loginAttemptService.recordFailure(request.username(), clientIp);
            throw new BadCredentialsException("Invalid username or password");
        }

        AppUser user = appUserRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));
        if (Boolean.TRUE.equals(user.getMfaEnabled())) {
            if (!totpService.verifyCode(user.getMfaSecret(), request.mfaCode())) {
                loginAttemptService.recordFailure(request.username(), clientIp);
                throw new BadCredentialsException("MFA code is invalid or missing");
            }
        }
        loginAttemptService.recordSuccess(request.username(), clientIp);

        String role = user.getRole().name();

        String accessToken = jwtService.generateToken(authentication.getName(), role);
        String refreshToken = refreshTokenService.issueToken(user, resolveDeviceContext(servletRequest));
        auditLogService.logAs(
                authentication.getName(),
                role,
                "AUTH_LOGIN",
                "USER",
                user.getId().toString(),
                "login succeeded, ip=" + clientIp
        );
        return new LoginResponse(
                accessToken,
                "Bearer",
                jwtService.getExpirationSeconds(),
                refreshToken,
                new MeResponse(authentication.getName(), role, Boolean.TRUE.equals(user.getMfaEnabled()))
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
                new MeResponse(user.getUsername(), user.getRole().name(), Boolean.TRUE.equals(user.getMfaEnabled()))
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
        AppUser user = getAuthenticatedUser(authentication);
        return new MeResponse(
                user.getUsername(),
                user.getRole().name(),
                Boolean.TRUE.equals(user.getMfaEnabled())
        );
    }

    @PostMapping("/password-reset/request")
    public PasswordResetRequestResponse requestPasswordReset(
            @Valid @RequestBody PasswordResetRequest request,
            HttpServletRequest servletRequest
    ) {
        PasswordResetService.PasswordResetRequestResult result = passwordResetService.requestReset(
                request.username(),
                resolveClientIp(servletRequest)
        );
        return new PasswordResetRequestResponse(result.message(), result.resetToken());
    }

    @PostMapping("/password-reset/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        passwordResetService.resetPassword(request.token(), request.newPassword());
    }

    @PostMapping("/mfa/setup")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER')")
    public MfaSetupResponse setupMfa(Authentication authentication) {
        AppUser user = getAuthenticatedUser(authentication);

        String secret = totpService.generateSecret();
        user.setMfaSecret(secret);
        user.setMfaEnabled(false);
        appUserRepository.save(user);

        auditLogService.logAs(
                user.getUsername(),
                user.getRole().name(),
                "AUTH_MFA_SETUP",
                "USER",
                user.getId().toString(),
                "mfa setup initialized"
        );

        return new MfaSetupResponse(secret, totpService.buildOtpAuthUri(user.getUsername(), secret));
    }

    @PostMapping("/mfa/enable")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER')")
    public MeResponse enableMfa(@Valid @RequestBody MfaCodeRequest request, Authentication authentication) {
        AppUser user = getAuthenticatedUser(authentication);
        if (user.getMfaSecret() == null || user.getMfaSecret().isBlank()) {
            throw new BadCredentialsException("MFA setup is not initialized");
        }
        if (!totpService.verifyCode(user.getMfaSecret(), request.code())) {
            throw new BadCredentialsException("Invalid MFA code");
        }

        user.setMfaEnabled(true);
        appUserRepository.save(user);
        auditLogService.logAs(
                user.getUsername(),
                user.getRole().name(),
                "AUTH_MFA_ENABLE",
                "USER",
                user.getId().toString(),
                "mfa enabled"
        );

        return new MeResponse(user.getUsername(), user.getRole().name(), true);
    }

    @PostMapping("/mfa/disable")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER')")
    public MeResponse disableMfa(@Valid @RequestBody MfaCodeRequest request, Authentication authentication) {
        AppUser user = getAuthenticatedUser(authentication);
        if (Boolean.TRUE.equals(user.getMfaEnabled())
                && !totpService.verifyCode(user.getMfaSecret(), request.code())) {
            throw new BadCredentialsException("Invalid MFA code");
        }

        user.setMfaEnabled(false);
        user.setMfaSecret(null);
        appUserRepository.save(user);
        auditLogService.logAs(
                user.getUsername(),
                user.getRole().name(),
                "AUTH_MFA_DISABLE",
                "USER",
                user.getId().toString(),
                "mfa disabled"
        );

        return new MeResponse(user.getUsername(), user.getRole().name(), false);
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

    public record MeResponse(String username, String role, boolean mfaEnabled) {
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
            @NotBlank String password,
            String mfaCode
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

    public record PasswordResetRequest(@NotBlank String username) {
    }

    public record PasswordResetConfirmRequest(
            @NotBlank String token,
            @NotBlank String newPassword
    ) {
    }

    public record PasswordResetRequestResponse(String message, String resetToken) {
    }

    public record MfaCodeRequest(@NotBlank String code) {
    }

    public record MfaSetupResponse(String secret, String otpauthUrl) {
    }
}
