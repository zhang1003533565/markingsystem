package com.markingsystem.service;

import com.markingsystem.dto.KnowledgeChatLogDTO;
import com.markingsystem.entity.KnowledgeChatLog;
import com.markingsystem.repository.KnowledgeChatLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * AI 对话日志服务（学生页面聊天记录）。
 *
 * <p>提供记录、列表（关键词 / 反馈过滤）、点赞点踩反馈与删除能力。
 */
@Service
public class KnowledgeChatLogService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeChatLogService.class);

    private static final String FEEDBACK_GOOD = "GOOD";
    private static final String FEEDBACK_BAD = "BAD";

    @Autowired
    private KnowledgeChatLogRepository repository;

    /**
     * 记录一轮对话（学生页面聊天）。记录失败不抛异常，不影响主流程。
     */
    public void log(Long appId, String sessionId, String userName, String userMessage,
                    String assistantMessage, String sourcesJson) {
        log(appId, sessionId, userName, userMessage, assistantMessage, sourcesJson, null, null);
    }

    /**
     * 记录一轮对话（学生页面聊天）。记录失败不抛异常，不影响主流程。
     *
     * @param studentNo 学生学号（用于按班级聚合总结；非学生对话可为 null）
     * @param className 学生班级（用于按班级聚合总结；非学生对话可为 null）
     */
    public void log(Long appId, String sessionId, String userName, String userMessage,
                    String assistantMessage, String sourcesJson, String studentNo, String className) {
        try {
            KnowledgeChatLog entry = new KnowledgeChatLog();
            entry.setAppId(appId);
            entry.setSessionId(sessionId);
            entry.setUserName(userName);
            entry.setUserMessage(userMessage);
            entry.setAssistantMessage(assistantMessage);
            entry.setSourcesJson(sourcesJson);
            entry.setStudentNo(studentNo);
            entry.setClassName(className);
            repository.save(entry);
        } catch (Exception e) {
            log.warn("Save knowledge chat log failed: {}", e.getMessage());
        }
    }

    /**
     * 日志列表（支持关键词 / 反馈过滤，分页）。
     */
    public Map<String, Object> list(String keyword, String feedback, int page, int size) {
        return list(keyword, feedback, page, size, null, null);
    }

    /**
     * 日志列表（支持关键词 / 反馈 / 班级范围过滤，分页）。
     *
     * @param allowedClassNames 非空时仅返回这些班级的记录（教师端带教范围）；null 表示不过滤
     * @param className         进一步按单个班级名过滤；须落在 allowedClassNames 内（若有）
     */
    public Map<String, Object> list(String keyword, String feedback, int page, int size,
                                    Collection<String> allowedClassNames, String className) {
        List<KnowledgeChatLog> logs = new ArrayList<>(repository.findTop500ByOrderByIdDesc());

        Set<String> allowed = normalizeClassNames(allowedClassNames);
        if (allowed != null) {
            logs.removeIf(e -> !matchesAllowedClass(e, allowed));
        }

        String classFilter = className == null || className.isBlank() ? null : className.trim();
        if (classFilter != null) {
            if (allowed != null && !allowed.contains(classFilter.toLowerCase(Locale.ROOT))) {
                logs.clear();
            } else {
                logs.removeIf(e -> e.getClassName() == null
                        || !classFilter.equalsIgnoreCase(e.getClassName().trim()));
            }
        }

        if (keyword != null && !keyword.isBlank()) {
            String lower = keyword.trim().toLowerCase(Locale.ROOT);
            logs.removeIf(e -> !String.valueOf(getOrEmpty(e.getUserMessage())).toLowerCase(Locale.ROOT).contains(lower)
                    && !String.valueOf(getOrEmpty(e.getAssistantMessage())).toLowerCase(Locale.ROOT).contains(lower));
        }
        if (feedback != null && !feedback.isBlank()) {
            if ("NONE".equalsIgnoreCase(feedback)) {
                logs.removeIf(e -> e.getFeedback() != null);
            } else {
                logs.removeIf(e -> !feedback.equalsIgnoreCase(e.getFeedback()));
            }
        }

        List<KnowledgeChatLogDTO> all = logs.stream()
                .map(e -> toDto(e))
                .collect(Collectors.toList());

        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        int from = Math.min((safePage - 1) * safeSize, all.size());
        int to = Math.min(from + safeSize, all.size());
        List<KnowledgeChatLogDTO> paged = from >= all.size() ? List.of() : all.subList(from, to);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", all.size());
        result.put("page", safePage);
        result.put("size", safeSize);
        result.put("data", paged);
        return result;
    }

    /**
     * 设置反馈（GOOD / BAD / null 表示取消）。
     */
    public Optional<KnowledgeChatLogDTO> setFeedback(Long id, String feedback) {
        return setFeedback(id, feedback, null);
    }

    /**
     * 设置反馈；若传入 allowedClassNames，仅允许操作落在范围内的记录。
     */
    public Optional<KnowledgeChatLogDTO> setFeedback(Long id, String feedback, Collection<String> allowedClassNames) {
        if (feedback != null && !feedback.isBlank()
                && !FEEDBACK_GOOD.equalsIgnoreCase(feedback) && !FEEDBACK_BAD.equalsIgnoreCase(feedback)) {
            throw new IllegalArgumentException("无效的反馈类型");
        }
        String normalized = feedback == null || feedback.isBlank() ? null : feedback.toUpperCase(Locale.ROOT);
        Set<String> allowed = normalizeClassNames(allowedClassNames);
        return repository.findById(id).map(entry -> {
            if (allowed != null && !matchesAllowedClass(entry, allowed)) {
                throw new IllegalArgumentException("只能反馈本人带教班级的对话记录");
            }
            entry.setFeedback(normalized);
            entry.setFeedbackAt(normalized == null ? null : LocalDateTime.now());
            repository.save(entry);
            return toDto(entry);
        });
    }

    public boolean delete(Long id) {
        return delete(id, null);
    }

    /**
     * 删除日志；若传入 allowedClassNames，仅允许删除落在范围内的记录。
     */
    public boolean delete(Long id, Collection<String> allowedClassNames) {
        Set<String> allowed = normalizeClassNames(allowedClassNames);
        return repository.findById(id).map(entry -> {
            if (allowed != null && !matchesAllowedClass(entry, allowed)) {
                throw new IllegalArgumentException("只能删除本人带教班级的对话记录");
            }
            repository.delete(entry);
            return true;
        }).orElse(false);
    }

    /** null 表示不过滤；空集合表示无可访问班级。 */
    private static Set<String> normalizeClassNames(Collection<String> classNames) {
        if (classNames == null) {
            return null;
        }
        return classNames.stream()
                .filter(name -> name != null && !name.isBlank())
                .map(name -> name.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

    private static boolean matchesAllowedClass(KnowledgeChatLog entry, Set<String> allowedLower) {
        if (entry.getClassName() == null || entry.getClassName().isBlank()) {
            return false;
        }
        return allowedLower.contains(entry.getClassName().trim().toLowerCase(Locale.ROOT));
    }

    private KnowledgeChatLogDTO toDto(KnowledgeChatLog entry) {
        KnowledgeChatLogDTO dto = new KnowledgeChatLogDTO();
        dto.setId(entry.getId());
        dto.setAppId(entry.getAppId());
        dto.setAppName(entry.getAppId() == null ? "学生页面聊天" : "应用#" + entry.getAppId());
        dto.setSessionId(entry.getSessionId());
        dto.setUserName(entry.getUserName());
        dto.setStudentNo(entry.getStudentNo());
        dto.setClassName(entry.getClassName());
        dto.setUserMessage(entry.getUserMessage());
        dto.setAssistantMessage(entry.getAssistantMessage());
        dto.setSourcesJson(entry.getSourcesJson());
        dto.setFeedback(entry.getFeedback());
        dto.setCreatedAt(entry.getCreatedAt());
        return dto;
    }

    private static String getOrEmpty(String value) {
        return value == null ? "" : value;
    }
}