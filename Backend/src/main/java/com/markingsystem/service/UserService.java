package com.markingsystem.service;

import com.markingsystem.dto.LoginRequest;
import com.markingsystem.dto.LoginResponse;
import com.markingsystem.entity.Role;
import com.markingsystem.entity.User;
import com.markingsystem.repository.RoleRepository;
import com.markingsystem.repository.UserRepository;
import com.markingsystem.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class UserService {
    
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordService passwordService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private SystemSettingService systemSettingService;
    
    public LoginResponse login(LoginRequest request) {
        Optional<User> userOpt = userRepository.findByUsername(request.getUsername());
        
        if (userOpt.isEmpty()) {
            return LoginResponse.error("用户名或密码错误");
        }
        
        User user = userOpt.get();
        
        if (!passwordService.matches(request.getPassword(), user.getPassword())) {
            return LoginResponse.error("用户名或密码错误");
        }
        if (passwordService.shouldUpgrade(user.getPassword())) {
            user.setPassword(passwordService.encode(request.getPassword()));
            user = userRepository.save(user);
        }
        
        LoginResponse.UserInfo userInfo = new LoginResponse.UserInfo();
        userInfo.setId(user.getId());
        userInfo.setUsername(user.getUsername());
        userInfo.setNickname(user.getNickname());
        // 从关联的Role对象获取角色名称
        if (user.getRole() != null) {
            userInfo.setRole(user.getRole().getName());
        } else {
            userInfo.setRole("UNKNOWN");
        }
        userInfo.setTeacherId(user.getTeacherId());
        userInfo.setClassName(user.getClassName());
        userInfo.setStudentNo(user.getStudentNo());
        userInfo.setAvatarUrl(user.getAvatarUrl());
        userInfo.setEmail(user.getEmail());
        userInfo.setPhone(user.getPhone());
        userInfo.setPasswordResetRequired(Boolean.TRUE.equals(user.getPasswordResetRequired()));
        userInfo.setForceEmailBindingRequired(systemSettingService.isForceEmailBindingRequired());

        // 签发 JWT Token
        String token = jwtUtil.generateToken(user.getId(), userInfo.getRole());
        userInfo.setToken(token);
        
        return LoginResponse.success(userInfo);
    }

    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    public List<User> findTeachers() {
        return findUsersByRoleName("TEACHER");
    }

    public List<User> findClassCommittees() {
        return findUsersByRoleName("CLASS_COMMITTEE");
    }

    public List<Map<String, Object>> findAllUserSummaries() {
        Map<Long, String> roleNameMap = roleRepository.findAll().stream()
                .collect(Collectors.toMap(Role::getId, Role::getName));

        return userRepository.findAll().stream()
                .sorted((left, right) -> Long.compare(
                        left.getId() == null ? 0L : left.getId(),
                        right.getId() == null ? 0L : right.getId()
                ))
                .map(user -> toUserSummary(user, roleNameMap))
                .toList();
    }

    public Map<String, Object> findUserSummariesPage(int page, int pageSize, String keyword, String role) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        String normalizedRole = role == null || role.isBlank() || "ALL".equalsIgnoreCase(role)
                ? null
                : role.trim();
        Long roleId = normalizedRole == null
                ? null
                : roleRepository.findByName(normalizedRole).map(Role::getId).orElse(-1L);
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        Pageable pageable = PageRequest.of(safePage - 1, safePageSize, Sort.by(Sort.Direction.ASC, "id"));
        Page<User> users = userRepository.searchUsers(normalizedKeyword, roleId, pageable);
        Map<Long, String> roleNameMap = roleRepository.findAll().stream()
                .collect(Collectors.toMap(Role::getId, Role::getName));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", users.getContent().stream()
                .map(user -> toUserSummary(user, roleNameMap))
                .toList());
        long total = users.getTotalElements();
        long pendingPasswordCount = userRepository.countPasswordResetRequiredUsers(normalizedKeyword, roleId);
        result.put("total", total);
        result.put("changedPasswordCount", Math.max(total - pendingPasswordCount, 0));
        result.put("pendingPasswordCount", pendingPasswordCount);
        result.put("page", safePage);
        result.put("pageSize", safePageSize);
        result.put("totalPages", users.getTotalPages());
        return result;
    }

    public Map<String, Object> createUser(Map<String, String> request) {
        String username = normalizeRequired(request.get("username"), "账号不能为空");
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("账号已存在");
        }
        String roleName = normalizeRequired(request.get("role"), "角色不能为空").toUpperCase();
        if (!"TEACHER".equals(roleName)) {
            throw new IllegalArgumentException("当前页面仅支持新增教师账号");
        }
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalArgumentException("角色不存在"));

        String password = normalizeOptional(request.get("password"));

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordService.encode(password == null ? username : password));
        user.setNickname(username);
        user.setRoleId(role.getId());
        user.setPasswordResetRequired(true);
        User saved = userRepository.save(user);

        Map<Long, String> roleNameMap = roleRepository.findAll().stream()
                .collect(Collectors.toMap(Role::getId, Role::getName));
        return toUserSummary(saved, roleNameMap);
    }

    public Optional<Map<String, Object>> findUserSummaryById(Long id) {
        Map<Long, String> roleNameMap = roleRepository.findAll().stream()
                .collect(Collectors.toMap(Role::getId, Role::getName));

        return userRepository.findById(id)
                .map(user -> toUserSummary(user, roleNameMap));
    }

    public Optional<User> updatePassword(Long id, String password, boolean passwordResetRequired) {
        return userRepository.findById(id).map(user -> {
            user.setPassword(passwordService.encode(password));
            user.setPasswordResetRequired(passwordResetRequired);
            return userRepository.save(user);
        });
    }

    public Optional<User> resetPasswordToUsername(Long id) {
        return userRepository.findById(id).map(user -> {
            String username = user.getUsername() == null ? "" : user.getUsername().trim();
            if (username.isBlank()) {
                throw new IllegalArgumentException("账号为空，无法重置密码");
            }
            user.setPassword(passwordService.encode(username));
            user.setPasswordResetRequired(true);
            return userRepository.save(user);
        });
    }

    public String changePassword(Long userId, String oldPassword, String newPassword) {
        if (userId == null) {
            return "用户ID不能为空";
        }
        if (oldPassword == null || oldPassword.isBlank()) {
            return "原密码不能为空";
        }
        if (newPassword == null || newPassword.isBlank()) {
            return "新密码不能为空";
        }
        String passwordRuleError = validatePasswordStrength(newPassword);
        if (passwordRuleError != null) {
            return passwordRuleError;
        }

        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return "用户不存在";
        }

        User user = userOpt.get();
        if (!passwordService.matches(oldPassword, user.getPassword())) {
            return "原密码错误";
        }

        user.setPassword(passwordService.encode(newPassword));
        user.setPasswordResetRequired(false);
        userRepository.save(user);
        return null;
    }

    private String validatePasswordStrength(String password) {
        if (password.length() < 8 || password.length() > 16) {
            return "密码长度必须为8-16位";
        }
        if (!password.matches(".*[a-z].*")) {
            return "密码必须包含小写字母";
        }
        if (!password.matches(".*[A-Z].*")) {
            return "密码必须包含大写字母";
        }
        if (!password.matches(".*\\d.*")) {
            return "密码必须包含数字";
        }
        if (!password.matches(".*[^A-Za-z0-9].*")) {
            return "密码必须包含特殊符号";
        }
        if (hasSequentialDigits(password)) {
            return "密码中的数字不能连续递增或递减";
        }
        return null;
    }

    private boolean hasSequentialDigits(String password) {
        for (int i = 0; i < password.length() - 2; i++) {
            char first = password.charAt(i);
            char second = password.charAt(i + 1);
            char third = password.charAt(i + 2);
            if (Character.isDigit(first) && Character.isDigit(second) && Character.isDigit(third)) {
                int a = first - '0';
                int b = second - '0';
                int c = third - '0';
                if ((b == a + 1 && c == b + 1) || (b == a - 1 && c == b - 1)) {
                    return true;
                }
            }
        }
        return false;
    }

    public Optional<User> updateEmailConfig(Long id, String email, String emailPassword) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("邮箱地址不能为空");
        }
        if (emailPassword == null || emailPassword.isBlank()) {
            throw new IllegalArgumentException("SMTP授权码不能为空");
        }
        return userRepository.findById(id).map(user -> {
            user.setEmail(email.trim());
            user.setEmailPassword(emailPassword);
            return userRepository.save(user);
        });
    }

    public User save(User user) {
        return userRepository.save(user);
    }

    private String normalizeRequired(String value, String message) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private Long parseLongOrNull(String value) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            return null;
        }
        try {
            return Long.parseLong(normalized);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("所属老师ID格式不正确");
        }
    }

    private List<User> findUsersByRoleName(String roleName) {
        Optional<Role> role = roleRepository.findByName(roleName);
        if (role.isEmpty()) {
            return List.of();
        }
        return userRepository.findByRoleId(role.get().getId());
    }

    private Map<String, Object> toUserSummary(User user, Map<Long, String> roleNameMap) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("id", user.getId());
        summary.put("username", user.getUsername());
        summary.put("nickname", user.getNickname() == null ? "" : user.getNickname());
        summary.put("role", roleNameMap.getOrDefault(user.getRoleId(), "UNKNOWN"));
        summary.put("studentNo", user.getStudentNo() == null ? "" : user.getStudentNo());
        summary.put("teacherId", user.getTeacherId());
        summary.put("className", user.getClassName() == null ? "" : user.getClassName());
        summary.put("avatarUrl", user.getAvatarUrl() == null ? "" : user.getAvatarUrl());
        summary.put("email", user.getEmail() == null ? "" : user.getEmail());
        summary.put("phone", user.getPhone() == null ? "" : user.getPhone());
        summary.put("emailConfigured", user.getEmail() != null && !user.getEmail().isBlank()
                && user.getEmailPassword() != null && !user.getEmailPassword().isBlank());
        summary.put("passwordResetRequired", Boolean.TRUE.equals(user.getPasswordResetRequired()));
        summary.put("createdAt", user.getCreatedAt());
        summary.put("updatedAt", user.getUpdatedAt());
        return summary;
    }
}
