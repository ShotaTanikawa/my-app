package com.example.backend.security;

import com.example.backend.common.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * ADMIN権限ユーザーのアクセス元IPを制限するフィルタ。
 */
@Component
public class AdminIpRestrictionFilter extends OncePerRequestFilter {

    private final List<IpAddressMatcher> allowedIpMatchers;
    private final ObjectMapper objectMapper;

    public AdminIpRestrictionFilter(
            ObjectMapper objectMapper,
            @Value("${app.security.admin-allowed-ips:}") String allowedIpRanges
    ) {
        this.objectMapper = objectMapper;
        this.allowedIpMatchers = Arrays.stream(allowedIpRanges.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .filter(value -> !"*".equals(value))
                .map(IpAddressMatcher::new)
                .toList();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (allowedIpMatchers.isEmpty()) {
            return true;
        }
        String uri = request.getRequestURI();
        return uri == null || !uri.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!isAdmin(authentication)) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = resolveClientIp(request);
        HttpServletRequest requestForMatch = new HttpServletRequestWrapper(request) {
            @Override
            public String getRemoteAddr() {
                return clientIp;
            }
        };

        boolean allowed = allowedIpMatchers.stream().anyMatch(matcher -> matcher.matches(requestForMatch));
        if (allowed) {
            filterChain.doFilter(request, response);
            return;
        }

        ApiError body = new ApiError(
                OffsetDateTime.now(),
                HttpServletResponse.SC_FORBIDDEN,
                "Forbidden",
                "Admin access is not allowed from this IP",
                request.getRequestURI()
        );

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    private boolean isAdmin(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            String[] values = forwardedFor.split(",");
            return values[0].trim();
        }
        return request.getRemoteAddr();
    }
}
