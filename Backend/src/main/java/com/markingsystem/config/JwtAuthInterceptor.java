package com.markingsystem.config;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.markingsystem.entity.Role;
import com.markingsystem.entity.User;
import com.markingsystem.repository.RoleRepository;
import com.markingsystem.repository.UserRepository;
import com.markingsystem.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class JwtAuthInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 放行 OPTIONS 预检请求
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // 从 Authorization Header 提取 Token
        String token = extractToken(request);
        if (token == null) {
            sendUnauthorized(response, "缺少认证凭证");
            return false;
        }

        DecodedJWT jwt = jwtUtil.validateToken(token);
        if (jwt == null) {
            sendUnauthorized(response, "认证凭证无效或已过期");
            return false;
        }

        // 从 Token 中提取用户ID
        Long userId = jwtUtil.getUserId(jwt);
        if (userId == null) {
            sendUnauthorized(response, "认证凭证无效");
            return false;
        }

        // 检查用户是否需要重置密码（管理员重置后强制下线）
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            sendUnauthorized(response, "用户不存在");
            return false;
        }
        if (Boolean.TRUE.equals(user.getPasswordResetRequired())) {
            // 放行密码修改请求，允许用户修改被重置的密码
            if (!"/api/user/password/change".equals(request.getRequestURI())) {
                sendUnauthorized(response, "密码已被重置，请重新登录");
                return false;
            }
        }

        String role = resolveRole(user, jwt);
        if (!isAllowed(request, role)) {
            sendForbidden(response, "当前角色无权访问该接口");
            return false;
        }

        return true;
    }

    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        if (request.getRequestURI().startsWith("/api/uploads/")) {
            String queryToken = request.getParameter("access_token");
            return queryToken == null || queryToken.isBlank() ? null : queryToken;
        }
        return null;
    }

    private String resolveRole(User user, DecodedJWT jwt) {
        if (user.getRoleId() != null) {
            String role = roleRepository.findById(user.getRoleId()).map(Role::getName).orElse(null);
            if (role != null && !role.isBlank()) {
                return role;
            }
        }
        return jwtUtil.getRole(jwt);
    }

    private boolean isAllowed(HttpServletRequest request, String role) {
        String uri = request.getRequestURI();
        String method = request.getMethod();
        boolean admin = "ADMIN".equalsIgnoreCase(role);
        boolean teacher = "TEACHER".equalsIgnoreCase(role);
        boolean committee = "CLASS_COMMITTEE".equalsIgnoreCase(role);
        boolean student = "STUDENT".equalsIgnoreCase(role);

        if (uri.startsWith("/api/admin/system-emails")) {
            return admin;
        }
        if (uri.startsWith("/api/roles")) {
            return admin;
        }
        if (uri.startsWith("/api/teacher/")) {
            return teacher || admin;
        }
        if (uri.startsWith("/api/admin/")) {
            return admin || teacher;
        }
        if (uri.startsWith("/api/email")) {
            return admin || teacher;
        }
        if (uri.startsWith("/api/scores")) {
            return admin || teacher;
        }
        if (uri.startsWith("/api/students") || uri.startsWith("/api/subjects")
                || uri.startsWith("/api/subject-rain") || uri.startsWith("/api/subject-score-rain")) {
            return admin || teacher || (committee && isReadOnly(method));
        }
        if (uri.startsWith("/api/extra-score-review")) {
            return admin || teacher || committee;
        }
        if (uri.startsWith("/api/extra-score-templates")) {
            return admin || teacher;
        }
        if (uri.startsWith("/api/student/job-recommendations/refresh")) {
            return student;
        }
        if (uri.startsWith("/api/student/")) {
            return student || committee;
        }
        if (uri.startsWith("/api/announcements")) {
            return admin || teacher || committee || student;
        }
        return admin || teacher || committee || student;
    }

    private boolean isReadOnly(String method) {
        return "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method);
    }

    private void sendUnauthorized(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"message\":\"" + escapeJson(message) + "\"}");
    }

    private void sendForbidden(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":403,\"message\":\"" + escapeJson(message) + "\"}");
    }

    private String escapeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
