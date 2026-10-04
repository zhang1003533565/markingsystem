package com.markingsystem.config;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.markingsystem.entity.Role;
import com.markingsystem.entity.User;
import com.markingsystem.repository.RoleRepository;
import com.markingsystem.repository.UserRepository;
import com.markingsystem.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TrustedIdentityHeaderFilter extends OncePerRequestFilter {

    private static final List<String> TRUSTED_IDENTITY_HEADERS = List.of(
            "X-User-Id",
            "X-User-Role",
            "X-Teacher-Id",
            "X-Class-Name",
            "X-Student-No"
    );

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    public TrustedIdentityHeaderFilter(JwtUtil jwtUtil, UserRepository userRepository, RoleRepository roleRepository) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (shouldSkip(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        TrustedUserIdentity identity = resolveTrustedIdentity(request);
        if (identity == null) {
            filterChain.doFilter(request, response);
            return;
        }

        filterChain.doFilter(new TrustedIdentityHeaderRequest(request, identity.toHeaderMap()), response);
    }

    private boolean shouldSkip(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String uri = request.getRequestURI();
        return uri == null
                || !uri.startsWith("/api/")
                || "/api/auth/login".equals(uri);
    }

    private TrustedUserIdentity resolveTrustedIdentity(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }

        DecodedJWT jwt = jwtUtil.validateToken(authHeader.substring(7));
        if (jwt == null) {
            return null;
        }

        Long userId = jwtUtil.getUserId(jwt);
        if (userId == null) {
            return null;
        }

        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return null;
        }

        User user = userOpt.get();
        String role = resolveRoleName(user).orElseGet(() -> jwtUtil.getRole(jwt));
        Long teacherId = user.getTeacherId();
        if (teacherId == null && "TEACHER".equalsIgnoreCase(role)) {
            teacherId = user.getId();
        }

        return new TrustedUserIdentity(
                user.getId(),
                role,
                teacherId,
                user.getClassName(),
                user.getStudentNo()
        );
    }

    private Optional<String> resolveRoleName(User user) {
        if (user.getRoleId() == null) {
            return Optional.empty();
        }
        return roleRepository.findById(user.getRoleId()).map(Role::getName);
    }

    record TrustedUserIdentity(Long userId, String role, Long teacherId, String className, String studentNo) {

        Map<String, String> toHeaderMap() {
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("X-User-Id", userId == null ? null : String.valueOf(userId));
            headers.put("X-User-Role", role);
            headers.put("X-Teacher-Id", teacherId == null ? null : String.valueOf(teacherId));
            headers.put("X-Class-Name", className);
            headers.put("X-Student-No", studentNo);
            return headers;
        }
    }

    static final class TrustedIdentityHeaderRequest extends HttpServletRequestWrapper {
        private final Map<String, String> trustedHeaders;

        TrustedIdentityHeaderRequest(HttpServletRequest request, Map<String, String> trustedHeaders) {
            super(request);
            this.trustedHeaders = new LinkedHashMap<>(trustedHeaders);
        }

        @Override
        public String getHeader(String name) {
            String trustedName = resolveTrustedName(name);
            if (trustedName != null) {
                return trustedHeaders.get(trustedName);
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            String trustedName = resolveTrustedName(name);
            if (trustedName != null) {
                String value = trustedHeaders.get(trustedName);
                return value == null || value.isBlank()
                        ? Collections.emptyEnumeration()
                        : Collections.enumeration(List.of(value));
            }
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            List<String> names = new ArrayList<>();
            Enumeration<String> originalNames = super.getHeaderNames();
            while (originalNames.hasMoreElements()) {
                String name = originalNames.nextElement();
                if (resolveTrustedName(name) == null) {
                    names.add(name);
                }
            }
            trustedHeaders.forEach((name, value) -> {
                if (value != null && !value.isBlank()) {
                    names.add(name);
                }
            });
            return Collections.enumeration(names);
        }

        private String resolveTrustedName(String name) {
            if (name == null) {
                return null;
            }
            String normalized = name.toLowerCase(Locale.ROOT);
            return TRUSTED_IDENTITY_HEADERS.stream()
                    .filter(header -> header.toLowerCase(Locale.ROOT).equals(normalized))
                    .findFirst()
                    .orElse(null);
        }
    }
}
