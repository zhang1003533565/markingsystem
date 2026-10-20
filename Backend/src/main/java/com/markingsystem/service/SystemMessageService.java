package com.markingsystem.service;

import com.markingsystem.entity.SystemMessage;
import com.markingsystem.repository.SystemMessageRepository;
import com.markingsystem.util.RequestUserContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SystemMessageService {

    public static final String TYPE_NOTICE = "NOTICE";
    public static final String TYPE_REVIEW_RESULT = "REVIEW_RESULT";
    public static final String TYPE_WARNING = "WARNING";
    public static final String TYPE_TODO = "TODO";

    private final SystemMessageRepository systemMessageRepository;

    public SystemMessageService(SystemMessageRepository systemMessageRepository) {
        this.systemMessageRepository = systemMessageRepository;
    }

    @Transactional
    public SystemMessage push(Long userId, String role, String type, String title, String content, String link) {
        if (userId == null) {
            return null;
        }
        SystemMessage message = new SystemMessage();
        message.setUserId(userId);
        message.setUserRole(role);
        message.setType(type);
        message.setTitle(title);
        message.setContent(content);
        message.setLink(link);
        message.setRead(false);
        return systemMessageRepository.save(message);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getInbox(RequestUserContext context, String type, int page, int size) {
        if (context == null) {
            return buildPage(List.of(), 0L, page, size);
        }
        boolean admin = context.isAdmin();
        List<SystemMessage> source;
        if (admin) {
            source = systemMessageRepository.findAllByOrderByCreatedAtDescIdDesc();
        } else if (context.getUserId() != null) {
            source = systemMessageRepository.findByUserIdOrderByCreatedAtDescIdDesc(context.getUserId());
        } else {
            source = List.of();
        }
        List<Map<String, Object>> rows = source.stream()
                .filter(message -> type == null || type.isBlank() || type.equalsIgnoreCase(message.getType()))
                .map(this::toMap)
                .toList();
        long unreadCount = admin
                ? systemMessageRepository.countByReadFalse()
                : (context.getUserId() == null ? 0L : systemMessageRepository.countByUserIdAndReadFalse(context.getUserId()));
        return buildPage(rows, unreadCount, page, size);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> unreadCount(RequestUserContext context) {
        if (context == null) {
            return Map.of("unreadCount", 0L);
        }
        long count = context.isAdmin()
                ? systemMessageRepository.countByReadFalse()
                : (context.getUserId() == null ? 0L : systemMessageRepository.countByUserIdAndReadFalse(context.getUserId()));
        return Map.of("unreadCount", count);
    }

    @Transactional
    public void markRead(Long id, RequestUserContext context) {
        Long userId = context == null ? null : context.getUserId();
        if (userId == null) {
            return;
        }
        systemMessageRepository.findById(id).ifPresent(message -> {
            if (message.getUserId() != null && message.getUserId().equals(userId) && !Boolean.TRUE.equals(message.getRead())) {
                message.setRead(true);
                message.setReadAt(LocalDateTime.now());
                systemMessageRepository.save(message);
            }
        });
    }

    @Transactional
    public int markAllRead(RequestUserContext context) {
        Long userId = context == null ? null : context.getUserId();
        if (userId == null) {
            return 0;
        }
        List<SystemMessage> unread = systemMessageRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId).stream()
                .filter(message -> !Boolean.TRUE.equals(message.getRead()))
                .toList();
        unread.forEach(message -> {
            message.setRead(true);
            message.setReadAt(LocalDateTime.now());
        });
        systemMessageRepository.saveAll(unread);
        return unread.size();
    }

    private Map<String, Object> toMap(SystemMessage message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", message.getId());
        result.put("type", message.getType());
        result.put("title", message.getTitle());
        result.put("content", message.getContent());
        result.put("link", message.getLink());
        result.put("read", Boolean.TRUE.equals(message.getRead()));
        result.put("readAt", message.getReadAt());
        result.put("createdAt", message.getCreatedAt());
        return result;
    }

    private Map<String, Object> buildPage(List<Map<String, Object>> rows, long unreadCount, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 10 : Math.min(size, 100);
        int total = rows.size();
        int from = Math.min(safePage * safeSize, total);
        int to = Math.min(from + safeSize, total);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", rows.subList(from, to));
        result.put("total", total);
        result.put("page", safePage);
        result.put("size", safeSize);
        result.put("unreadCount", unreadCount);
        return result;
    }
}
