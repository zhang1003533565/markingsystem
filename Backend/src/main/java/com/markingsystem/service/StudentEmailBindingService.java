package com.markingsystem.service;

import com.markingsystem.entity.User;
import com.markingsystem.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Service
public class StudentEmailBindingService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final int CODE_TTL_MINUTES = 10;

    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, VerificationRecord> verificationRecords = new ConcurrentHashMap<>();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailNotificationService emailNotificationService;

    public Map<String, Object> sendVerificationCode(Long userId, String email) {
        User user = requireUser(userId);
        String normalizedEmail = normalizeEmail(email);
        ensureEmailAvailable(user.getId(), normalizedEmail);

        String code = String.format("%06d", secureRandom.nextInt(1_000_000));
        String key = recordKey(user.getId(), normalizedEmail);
        verificationRecords.put(key, new VerificationRecord(code, LocalDateTime.now().plusMinutes(CODE_TTL_MINUTES)));

        try {
            emailNotificationService.sendStudentEmailVerificationCode(normalizedEmail, code);
        } catch (RuntimeException exception) {
            verificationRecords.remove(key);
            throw exception;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("email", normalizedEmail);
        result.put("expiresInMinutes", CODE_TTL_MINUTES);
        return result;
    }

    @Transactional
    public Map<String, Object> bindEmail(Long userId, String email, String code) {
        User user = requireUser(userId);
        String normalizedEmail = normalizeEmail(email);
        String normalizedCode = code == null ? "" : code.trim();
        if (!normalizedCode.matches("\\d{6}")) {
            throw new IllegalArgumentException("请输入6位邮箱验证码");
        }
        ensureEmailAvailable(user.getId(), normalizedEmail);

        String key = recordKey(user.getId(), normalizedEmail);
        VerificationRecord record = verificationRecords.get(key);
        if (record == null) {
            throw new IllegalArgumentException("请先获取邮箱验证码");
        }
        if (record.expiresAt().isBefore(LocalDateTime.now())) {
            verificationRecords.remove(key);
            throw new IllegalArgumentException("验证码已过期，请重新获取");
        }
        if (!record.code().equals(normalizedCode)) {
            throw new IllegalArgumentException("验证码不正确");
        }

        user.setEmail(normalizedEmail);
        userRepository.save(user);
        verificationRecords.remove(key);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("email", normalizedEmail);
        result.put("bound", true);
        return result;
    }

    private User requireUser(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("缺少用户标识");
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
    }

    private String normalizeEmail(String email) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase();
        if (normalizedEmail.isBlank()) {
            throw new IllegalArgumentException("邮箱不能为空");
        }
        if (!EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
            throw new IllegalArgumentException("邮箱格式不正确");
        }
        return normalizedEmail;
    }

    private void ensureEmailAvailable(Long currentUserId, String email) {
        Optional<User> existing = userRepository.findFirstByEmailOrderByIdAsc(email);
        if (existing.isPresent() && !existing.get().getId().equals(currentUserId)) {
            throw new IllegalArgumentException("该邮箱已被其他账号绑定");
        }
    }

    private String recordKey(Long userId, String email) {
        return userId + ":" + email;
    }

    private record VerificationRecord(String code, LocalDateTime expiresAt) {
    }
}
