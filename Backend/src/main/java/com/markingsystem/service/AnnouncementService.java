package com.markingsystem.service;

import com.markingsystem.dto.BatchEmailResult;
import com.markingsystem.dto.CreateAnnouncementRequest;
import com.markingsystem.dto.EmailTaskProgress;
import com.markingsystem.entity.Announcement;
import com.markingsystem.entity.AnnouncementRecipient;
import com.markingsystem.entity.ClassInfo;
import com.markingsystem.entity.Student;
import com.markingsystem.entity.User;
import com.markingsystem.repository.AnnouncementRecipientRepository;
import com.markingsystem.repository.AnnouncementRepository;
import com.markingsystem.util.RequestUserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class AnnouncementService {

    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<(/?)([a-zA-Z0-9]+)([^>]*)>");
    private static final Pattern HTML_ATTRIBUTE_PATTERN = Pattern.compile(
            "([a-zA-Z_:][a-zA-Z0-9:._-]*)\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)"
    );
    private static final Set<String> SAFE_HTML_TAGS = Set.of(
            "p", "br", "strong", "b", "em", "i", "u", "s", "ul", "ol", "li",
            "h1", "h2", "h3", "h4", "blockquote", "a", "img", "video", "source", "span"
    );
    private static final Set<String> SAFE_HTML_ATTRIBUTES = Set.of("href", "src", "alt", "title", "width", "height");
    private static final String SENDER_TYPE_SITE = "SITE";
    private static final String EMAIL_SUCCESS_STATUS = "SUCCESS";
    private static final String EMAIL_PARTIAL_SUCCESS_STATUS = "PARTIAL_SUCCESS";

    @Autowired
    private AnnouncementRepository announcementRepository;

    @Autowired
    private AnnouncementRecipientRepository announcementRecipientRepository;

    @Autowired
    private StudentService studentService;

    @Autowired
    private ClassInfoService classInfoService;

    @Autowired
    private UserService userService;

    @Autowired
    private EmailNotificationService emailNotificationService;

    @Transactional
    public Map<String, Object> createAnnouncement(CreateAnnouncementRequest request, RequestUserContext context) {
        validatePublisherContext(context);
        if (request.getTitle() == null || request.getTitle().isBlank()
                || request.getContent() == null || request.getContent().isBlank()) {
            throw new IllegalArgumentException("公告标题和内容不能为空");
        }

        User publisher = userService.findById(context.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("发布人不存在"));

        String category = normalizeCategory(request.getCategory(), context);
        List<Student> targets = resolveTargets(context, request);
        if (targets.isEmpty()) {
            throw new IllegalArgumentException("当前范围内没有可通知的学生");
        }

        String senderType = request.getSenderType();
        BatchEmailResult emailResult = null;
        Announcement announcement = buildAnnouncement(request, context, publisher, category, targets.size());

        if (!isSiteOnlySender(senderType)) {
            emailResult = emailNotificationService.validateAnnouncementEmailRequest(targets, senderType, publisher.getEmail());
            if (emailResult != null) {
                throw new EmailDeliveryRequiredException(
                        "邮件发送前置检查失败，站内通知未发布。",
                        toEmailResultMap(emailResult)
                );
            }
        }

        announcement = announcementRepository.save(announcement);
        saveRecipients(announcement, targets);

        if (!isSiteOnlySender(senderType)) {
            try {
                emailResult = emailNotificationService.enqueueAnnouncementEmail(announcement, targets, senderType, publisher.getEmail());
            } catch (Exception e) {
                emailResult = buildEmailExceptionResult(targets.size(), e);
            }
            if (!isEmailTaskQueued(emailResult)) {
                throw new EmailDeliveryRequiredException(
                        "邮件任务提交失败，站内通知未发布。",
                        toEmailResultMap(emailResult)
                );
            }
        }

        return Map.<String, Object>of(
                "announcement", announcement,
                "emailResult", emailResult != null ? toEmailResultMap(emailResult) : Map.of()
        );
    }

    public List<Map<String, Object>> getInbox(RequestUserContext context) {
        if (context == null || context.getStudentNo() == null) {
            return List.of();
        }
        return announcementRecipientRepository.findByStudentNoOrderByCreatedAtDesc(context.getStudentNo()).stream()
                .map(this::toInboxRecord)
                .filter(item -> !item.isEmpty())
                .toList();
    }

    public Map<String, Object> getInbox(RequestUserContext context, int page, int size) {
        if (context == null || context.getStudentNo() == null) {
            return buildPage(List.of(), page, size);
        }
        List<Map<String, Object>> rows = announcementRecipientRepository.findByStudentNoOrderByCreatedAtDesc(context.getStudentNo()).stream()
                .map(this::toInboxListRecord)
                .filter(item -> !item.isEmpty())
                .toList();
        return buildPage(rows, page, size);
    }

    public List<Map<String, Object>> getPublishedAnnouncements(RequestUserContext context) {
        validatePublisherContext(context);
        return announcementRepository.findByPublisherIdOrderByCreatedAtDesc(context.getUserId()).stream()
                .map(this::toPublishedRecord)
                .toList();
    }

    public Map<String, Object> getPublishedAnnouncements(RequestUserContext context, int page, int size) {
        validatePublisherContext(context);
        List<Map<String, Object>> rows = announcementRepository.findByPublisherIdOrderByCreatedAtDesc(context.getUserId()).stream()
                .map(this::toPublishedListRecord)
                .toList();
        return buildPage(rows, page, size);
    }

    @Transactional
    public Map<String, Object> getAnnouncementDetail(Long announcementId, RequestUserContext context) {
        if (context != null && context.getStudentNo() != null && (context.isStudent() || context.isClassCommittee())) {
            AnnouncementRecipient recipient = announcementRecipientRepository
                    .findByAnnouncementIdAndStudentNo(announcementId, context.getStudentNo())
                    .orElseThrow(() -> new IllegalArgumentException("公告不存在或无权访问"));
            if (!Boolean.TRUE.equals(recipient.getRead())) {
                recipient.setRead(true);
                recipient.setReadAt(LocalDateTime.now());
                announcementRecipientRepository.save(recipient);
                refreshAnnouncementStats(announcementId);
            }
            Map<String, Object> record = toInboxRecord(recipient);
            if (record.isEmpty()) {
                throw new IllegalArgumentException("公告不存在");
            }
            return record;
        }
        return toPublishedRecord(getOwnedAnnouncement(announcementId, context));
    }

    public Map<String, Object> getAnnouncementRecipients(Long announcementId, RequestUserContext context) {
        Announcement announcement = getOwnedAnnouncement(announcementId, context);
        List<Map<String, Object>> recipients = announcementRecipientRepository.findByAnnouncementIdOrderByReadAscStudentNoAsc(announcementId)
                .stream()
                .map(this::toRecipientRecord)
                .toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("announcement", toPublishedRecord(announcement));
        result.put("records", recipients);
        return result;
    }

    public Optional<EmailTaskProgress> getEmailTaskProgress(String taskId) {
        return emailNotificationService.getTaskProgress(taskId);
    }

    @Transactional
    public void markAsRead(Long announcementId, RequestUserContext context) {
        if (context == null || context.getStudentNo() == null) {
            throw new IllegalArgumentException("当前用户不是学生");
        }
        AnnouncementRecipient recipient = announcementRecipientRepository
                .findByAnnouncementIdAndStudentNo(announcementId, context.getStudentNo())
                .orElseThrow(() -> new IllegalArgumentException("公告不存在或无权访问"));

        if (Boolean.TRUE.equals(recipient.getRead())) {
            return;
        }

        recipient.setRead(true);
        recipient.setReadAt(LocalDateTime.now());
        announcementRecipientRepository.save(recipient);
        refreshAnnouncementStats(announcementId);
    }

    @Transactional
    public Map<String, Object> remindUnread(Long announcementId, RequestUserContext context) {
        return remindUnread(announcementId, context, "TEACHER");
    }

    @Transactional
    public Map<String, Object> remindUnread(Long announcementId, RequestUserContext context, String senderType) {
        Announcement announcement = getOwnedAnnouncement(announcementId, context);
        List<AnnouncementRecipient> unreadRecipients = announcementRecipientRepository.findByAnnouncementIdOrderByReadAscStudentNoAsc(announcementId)
                .stream()
                .filter(recipient -> !Boolean.TRUE.equals(recipient.getRead()))
                .toList();

        List<Student> unreadStudents = unreadRecipients.stream()
                .map(recipient -> studentService.findByStudentNo(recipient.getStudentNo()).orElse(null))
                .filter(student -> student != null)
                .toList();

        BatchEmailResult emailResult = null;
        if (!isSiteOnlySender(senderType)) {
            String publisherEmail = userService.findById(announcement.getPublisherId())
                    .map(User::getEmail)
                    .orElse(null);
            try {
                emailResult = emailNotificationService.enqueueReminderEmail(announcement, unreadStudents, true, senderType, publisherEmail);
            } catch (Exception e) {
                emailResult = buildEmailExceptionResult(unreadStudents.size(), e);
            }
            if (!isEmailTaskQueued(emailResult)) {
                throw new EmailDeliveryRequiredException(
                        "邮件任务提交失败，站内催读未发布。",
                        toEmailResultMap(emailResult)
                );
            }
        }

        LocalDateTime now = LocalDateTime.now();
        int reminded = 0;
        for (AnnouncementRecipient recipient : unreadRecipients) {
            recipient.setRemindCount((recipient.getRemindCount() == null ? 0 : recipient.getRemindCount()) + 1);
            recipient.setLastRemindedAt(now);
            announcementRecipientRepository.save(recipient);
            reminded++;
        }

        announcement.setRemindCount((announcement.getRemindCount() == null ? 0 : announcement.getRemindCount()) + 1);
        announcement.setLastRemindedAt(now);
        announcementRepository.save(announcement);
        refreshAnnouncementStats(announcementId);

        return Map.of(
                "announcementId", announcementId,
                "remindedCount", reminded,
                "unreadCount", unreadRecipients.size(),
                "emailResult", toEmailResultMap(emailResult)
        );
    }

    private Map<String, Object> toEmailResultMap(BatchEmailResult emailResult) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (emailResult == null) {
            return result;
        }
        result.put("status", emailResult.getStatus());
        result.put("message", emailResult.getMessage());
        result.put("taskId", emailResult.getTaskId());
        result.put("type", emailResult.getType());
        result.put("recipientCount", emailResult.getRecipientCount());
        result.put("total", emailResult.getTotal());
        result.put("successCount", emailResult.getSuccessCount());
        result.put("failureCount", emailResult.getFailureCount());
        result.put("skippedCount", emailResult.getSkippedCount());
        result.put("processedCount", emailResult.getProcessedCount());
        result.put("pendingCount", emailResult.getPendingCount());
        result.put("rateLimitMillis", emailResult.getRateLimitMillis());
        result.put("failedEmails", emailResult.getFailedEmails());
        result.put("failures", emailResult.getFailures());
        return result;
    }

    private Announcement buildAnnouncement(CreateAnnouncementRequest request, RequestUserContext context, User publisher, String category, int recipientCount) {
        Announcement announcement = new Announcement();
        announcement.setTitle(request.getTitle().trim());
        announcement.setContent(sanitizeHtml(request.getContent()));
        announcement.setCategory(category);
        announcement.setPublisherId(publisher.getId());
        announcement.setPublisherName(resolvePublisherName(publisher));
        announcement.setPublisherRole(context.getRole());
        announcement.setTeacherId(resolveTeacherId(context));
        announcement.setClassName(resolveAnnouncementClassName(request, context));
        announcement.setImage(normalizeMedia(request.getImage()));
        announcement.setVideo(normalizeMedia(request.getVideo()));
        announcement.setRecipientCount(recipientCount);
        announcement.setReadCount(0);
        announcement.setUnreadCount(recipientCount);
        return announcement;
    }

    private void saveRecipients(Announcement announcement, List<Student> targets) {
        List<AnnouncementRecipient> recipients = new ArrayList<>();
        for (Student student : targets) {
            AnnouncementRecipient recipient = new AnnouncementRecipient();
            recipient.setAnnouncementId(announcement.getId());
            recipient.setStudentId(student.getId());
            recipient.setStudentNo(student.getStudentNo());
            recipient.setStudentName(student.getName());
            recipient.setTeacherId(student.getTeacherId());
            recipient.setClassName(student.getClassName());
            recipients.add(recipient);
        }
        announcementRecipientRepository.saveAll(recipients);
    }

    private boolean isSiteOnlySender(String senderType) {
        return senderType == null || senderType.isBlank() || SENDER_TYPE_SITE.equalsIgnoreCase(senderType);
    }

    private boolean isEmailDeliverySuccess(BatchEmailResult emailResult) {
        return emailResult != null
                && (EMAIL_SUCCESS_STATUS.equalsIgnoreCase(emailResult.getStatus())
                || EMAIL_PARTIAL_SUCCESS_STATUS.equalsIgnoreCase(emailResult.getStatus()));
    }

    private boolean isEmailTaskQueued(BatchEmailResult emailResult) {
        return emailResult != null
                && emailResult.getTaskId() != null
                && !emailResult.getTaskId().isBlank()
                && "QUEUED".equalsIgnoreCase(emailResult.getStatus());
    }

    private BatchEmailResult buildEmailExceptionResult(int recipientCount, Exception e) {
        BatchEmailResult result = new BatchEmailResult();
        result.setStatus("ERROR");
        result.setRecipientCount(recipientCount);
        result.setTotal(0);
        result.setSuccessCount(0);
        result.setFailureCount(0);
        result.setSkippedCount(recipientCount);
        result.setFailedEmails(List.of());
        result.setFailures(List.of());
        result.setMessage(e.getMessage() != null && !e.getMessage().isBlank()
                ? e.getMessage()
                : "邮件发送时出现异常。");
        return result;
    }

    private BatchEmailResult buildMissingSenderResult(String senderType, int recipientCount) {
        BatchEmailResult result = new BatchEmailResult();
        result.setStatus("NO_SENDER");
        result.setRecipientCount(recipientCount);
        result.setTotal(0);
        result.setSuccessCount(0);
        result.setFailureCount(0);
        result.setSkippedCount(recipientCount);
        result.setFailedEmails(List.of());
        result.setFailures(List.of());
        result.setMessage("SYSTEM".equalsIgnoreCase(senderType)
                ? "默认邮箱未配置或SMTP授权码缺失。"
                : "教师邮箱未配置或SMTP授权码缺失。");
        return result;
    }

    private void refreshAnnouncementStats(Long announcementId) {
        announcementRepository.findById(announcementId).ifPresent(announcement -> {
            int readCount = (int) announcementRecipientRepository.countByAnnouncementIdAndReadTrue(announcementId);
            int unreadCount = (int) announcementRecipientRepository.countByAnnouncementIdAndReadFalse(announcementId);
            announcement.setReadCount(readCount);
            announcement.setUnreadCount(unreadCount);
            announcementRepository.save(announcement);
        });
    }

    private Announcement getOwnedAnnouncement(Long announcementId, RequestUserContext context) {
        validatePublisherContext(context);
        Announcement announcement = announcementRepository.findById(announcementId)
                .orElseThrow(() -> new IllegalArgumentException("公告不存在"));
        if (!context.isAdmin() && !announcement.getPublisherId().equals(context.getUserId())) {
            throw new IllegalArgumentException("无权查看该公告");
        }
        return announcement;
    }

    private void validatePublisherContext(RequestUserContext context) {
        if (context == null || context.getUserId() == null) {
            throw new IllegalArgumentException("缺少用户信息");
        }
        if (!(context.isAdmin() || context.isTeacher() || context.isClassCommittee())) {
            throw new IllegalArgumentException("当前角色不能发布公告");
        }
    }

    private String normalizeCategory(String category, RequestUserContext context) {
        if (context.isAdmin()) {
            return "MANAGEMENT_NOTICE";
        }
        if (context.isTeacher()) {
            return "COLLEGE_NOTICE";
        }
        return "COMMITTEE_NOTICE";
    }

    private Long resolveTeacherId(RequestUserContext context) {
        if (context.isTeacher()) {
            return context.getUserId();
        }
        if (context.isClassCommittee()) {
            return context.getTeacherId();
        }
        return null;
    }

    private List<Student> resolveTargets(RequestUserContext context, CreateAnnouncementRequest request) {
        List<Student> baseTargets = resolveBaseTargets(context);
        if (context.isClassCommittee()) {
            return baseTargets;
        }

        String targetType = request.getTargetType() == null || request.getTargetType().isBlank()
                ? "ALL"
                : request.getTargetType().trim().toUpperCase(Locale.ROOT);

        if ("ALL".equals(targetType)) {
            return baseTargets;
        }

        if ("CLASS".equals(targetType)) {
            Set<String> targetClassNames = normalizeToSet(request.getTargetClassNames());
            if (targetClassNames.isEmpty()) {
                throw new IllegalArgumentException("请选择接收班级");
            }
            return baseTargets.stream()
                    .filter(student -> targetClassNames.contains(student.getClassName()))
                    .toList();
        }

        if ("GRADE".equals(targetType)) {
            Set<String> targetGrades = normalizeToSet(request.getTargetGrades());
            if (targetGrades.isEmpty()) {
                throw new IllegalArgumentException("请选择接收年级");
            }
            Map<String, String> gradeByClassName = classInfoService.findAll().stream()
                    .filter(classInfo -> classInfo.getName() != null && classInfo.getGrade() != null)
                    .collect(Collectors.toMap(
                            ClassInfo::getName,
                            ClassInfo::getGrade,
                            (first, second) -> first
                    ));
            return baseTargets.stream()
                    .filter(student -> targetGrades.contains(gradeByClassName.get(student.getClassName())))
                    .toList();
        }

        throw new IllegalArgumentException("接收范围不正确");
    }

    private List<Student> resolveBaseTargets(RequestUserContext context) {
        if (context.isAdmin()) {
            return studentService.findAll();
        }
        if (context.isTeacher()) {
            return studentService.findManagedByTeacherId(context.getUserId());
        }
        if (context.isClassCommittee()) {
            return studentService.findManagedByTeacherIdAndClassName(context.getTeacherId(), context.getClassName());
        }
        return List.of();
    }

    private Set<String> normalizeToSet(List<String> values) {
        if (values == null) {
            return Set.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .collect(Collectors.toCollection(HashSet::new));
    }

    private String resolveAnnouncementClassName(CreateAnnouncementRequest request, RequestUserContext context) {
        if (context.isClassCommittee()) {
            return context.getClassName();
        }
        String targetType = request.getTargetType() == null ? "" : request.getTargetType().trim();
        Set<String> targetClassNames = normalizeToSet(request.getTargetClassNames());
        if ("CLASS".equalsIgnoreCase(targetType) && targetClassNames.size() == 1) {
            return targetClassNames.iterator().next();
        }
        return null;
    }

    private String resolvePublisherName(User publisher) {
        return publisher.getNickname() != null && !publisher.getNickname().isBlank()
                ? publisher.getNickname()
                : publisher.getUsername();
    }

    private Map<String, Object> toInboxRecord(AnnouncementRecipient recipient) {
        Announcement announcement = announcementRepository.findById(recipient.getAnnouncementId()).orElse(null);
        if (announcement == null) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", announcement.getId());
        result.put("title", announcement.getTitle());
        result.put("content", announcement.getContent());
        result.put("category", displayCategory(announcement.getCategory()));
        result.put("publisherName", announcement.getPublisherName());
        result.put("publisherRole", announcement.getPublisherRole());
        result.put("publishTime", announcement.getCreatedAt());
        result.put("image", announcement.getImage());
        result.put("video", announcement.getVideo());
        result.put("read", recipient.getRead());
        result.put("readAt", recipient.getReadAt());
        result.put("remindCount", recipient.getRemindCount());
        // 学生/班委也展示阅读人数、未读人数、总人数统计
        result.put("recipientCount", announcement.getRecipientCount());
        result.put("readCount", announcement.getReadCount());
        result.put("unreadCount", announcement.getUnreadCount());
        return result;
    }

    private Map<String, Object> toInboxListRecord(AnnouncementRecipient recipient) {
        Announcement announcement = announcementRepository.findById(recipient.getAnnouncementId()).orElse(null);
        if (announcement == null) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", announcement.getId());
        result.put("title", announcement.getTitle());
        result.put("summary", summarizeContent(announcement.getContent()));
        result.put("category", displayCategory(announcement.getCategory()));
        result.put("publisherName", announcement.getPublisherName());
        result.put("publisherRole", announcement.getPublisherRole());
        result.put("publishTime", announcement.getCreatedAt());
        result.put("read", recipient.getRead());
        result.put("readAt", recipient.getReadAt());
        return result;
    }

    private Map<String, Object> toPublishedRecord(Announcement announcement) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", announcement.getId());
        result.put("title", announcement.getTitle());
        result.put("content", announcement.getContent());
        result.put("category", displayCategory(announcement.getCategory()));
        result.put("publisherName", announcement.getPublisherName());
        result.put("publisherRole", announcement.getPublisherRole());
        result.put("teacherId", announcement.getTeacherId());
        result.put("className", announcement.getClassName());
        result.put("recipientCount", announcement.getRecipientCount());
        result.put("readCount", announcement.getReadCount());
        result.put("unreadCount", announcement.getUnreadCount());
        result.put("remindCount", announcement.getRemindCount());
        result.put("lastRemindedAt", announcement.getLastRemindedAt());
        result.put("publishTime", announcement.getCreatedAt());
        result.put("image", announcement.getImage());
        result.put("video", announcement.getVideo());
        return result;
    }

    private Map<String, Object> toPublishedListRecord(Announcement announcement) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", announcement.getId());
        result.put("title", announcement.getTitle());
        result.put("summary", summarizeContent(announcement.getContent()));
        result.put("category", displayCategory(announcement.getCategory()));
        result.put("publisherName", announcement.getPublisherName());
        result.put("publisherRole", announcement.getPublisherRole());
        result.put("recipientCount", announcement.getRecipientCount());
        result.put("readCount", announcement.getReadCount());
        result.put("unreadCount", announcement.getUnreadCount());
        result.put("remindCount", announcement.getRemindCount());
        result.put("lastRemindedAt", announcement.getLastRemindedAt());
        result.put("publishTime", announcement.getCreatedAt());
        return result;
    }

    private Map<String, Object> buildPage(List<Map<String, Object>> rows, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 10 : Math.min(size, 100);
        int totalElements = rows.size();
        int fromIndex = Math.min(safePage * safeSize, totalElements);
        int toIndex = Math.min(fromIndex + safeSize, totalElements);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", rows.subList(fromIndex, toIndex));
        result.put("total", totalElements);
        result.put("page", safePage);
        result.put("size", safeSize);
        return result;
    }

    private String summarizeContent(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String text = content.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        return text.length() > 100 ? text.substring(0, 100) : text;
    }

    private Map<String, Object> toRecipientRecord(AnnouncementRecipient recipient) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", recipient.getId());
        result.put("studentId", recipient.getStudentId());
        result.put("studentNo", recipient.getStudentNo());
        result.put("studentName", recipient.getStudentName());
        result.put("className", recipient.getClassName());
        result.put("read", recipient.getRead());
        result.put("readAt", recipient.getReadAt());
        result.put("remindCount", recipient.getRemindCount());
        result.put("lastRemindedAt", recipient.getLastRemindedAt());
        return result;
    }

    private String displayCategory(String category) {
        if ("MANAGEMENT_NOTICE".equalsIgnoreCase(category)) {
            return "管理通知";
        }
        if ("COLLEGE_NOTICE".equalsIgnoreCase(category)) {
            return "学院通知";
        }
        if ("COMMITTEE_NOTICE".equalsIgnoreCase(category)) {
            return "班委通知";
        }
        return category;
    }

    private String normalizeMedia(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String sanitizeHtml(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String source = value
                .replaceAll("(?is)<!--.*?-->", "")
                .replaceAll("(?is)<(script|style|iframe|object|embed)[^>]*>.*?</\\1>", "");
        Matcher matcher = HTML_TAG_PATTERN.matcher(source);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            String tagName = matcher.group(2).toLowerCase(Locale.ROOT);
            if (!SAFE_HTML_TAGS.contains(tagName)) {
                matcher.appendReplacement(output, "");
                continue;
            }
            if (!matcher.group(1).isEmpty()) {
                matcher.appendReplacement(output, "</" + tagName + ">");
                continue;
            }
            StringBuilder tag = new StringBuilder("<").append(tagName);
            Matcher attributeMatcher = HTML_ATTRIBUTE_PATTERN.matcher(matcher.group(3));
            while (attributeMatcher.find()) {
                String attributeName = attributeMatcher.group(1).toLowerCase(Locale.ROOT);
                if (!SAFE_HTML_ATTRIBUTES.contains(attributeName)) {
                    continue;
                }
                String attributeValue = attributeMatcher.group(2);
                if (attributeValue.length() >= 2
                        && ((attributeValue.startsWith("\"") && attributeValue.endsWith("\""))
                        || (attributeValue.startsWith("'") && attributeValue.endsWith("'")))) {
                    attributeValue = attributeValue.substring(1, attributeValue.length() - 1);
                }
                if ((attributeName.equals("href") || attributeName.equals("src"))
                        && !isSafeUrl(attributeValue)) {
                    continue;
                }
                tag.append(' ').append(attributeName).append("=\"")
                        .append(escapeHtmlAttribute(attributeValue)).append('"');
            }
            tag.append('>');
            matcher.appendReplacement(output, Matcher.quoteReplacement(tag.toString()));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    private boolean isSafeUrl(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("http://")
                || normalized.startsWith("https://")
                || normalized.startsWith("/")
                || normalized.startsWith("data:image/");
    }

    private String escapeHtmlAttribute(String value) {
        return value == null ? "" : value
                .replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    public static class EmailDeliveryRequiredException extends RuntimeException {
        private final Map<String, Object> emailResult;

        public EmailDeliveryRequiredException(String message, Map<String, Object> emailResult) {
            super(message);
            this.emailResult = emailResult;
        }

        public Map<String, Object> getEmailResult() {
            return emailResult;
        }
    }
}
