package com.markingsystem.service;

import com.markingsystem.dto.BatchEmailResult;
import com.markingsystem.dto.EmailTaskProgress;
import com.markingsystem.entity.Announcement;
import com.markingsystem.entity.Student;
import com.markingsystem.entity.SystemEmail;
import com.markingsystem.entity.User;
import com.markingsystem.repository.UserRepository;
import jakarta.mail.Authenticator;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;

@Service
public class EmailNotificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationService.class);
    private static final String SENDER_TYPE_TEACHER = "TEACHER";
    private static final String SENDER_TYPE_SYSTEM = "SYSTEM";
    private static final int MAX_SEND_ATTEMPTS = 2;
    private static final long RETRY_DELAY_MS = 500L;

    private final JavaMailSender defaultMailSender;
    private final boolean enabled;
    private final String fromName;

    @Autowired
    private SystemEmailService systemEmailService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailTaskQueueService emailTaskQueueService;

    @Value("${spring.mail.host:smtp.qq.com}")
    private String mailHost;

    @Value("${spring.mail.port:465}")
    private int mailPort;

    @Value("${spring.mail.properties.mail.smtp.ssl.enable:true}")
    private boolean sslEnabled;

    @Value("${spring.mail.properties.mail.smtp.timeout:5000}")
    private int timeout;

    @Value("${mail.rate-limit-ms:800}")
    private long rateLimitMillis;

    @Value("${app.frontend-base-url:http://localhost:5173}")
    private String frontendBaseUrl;

    public boolean canUseSender(String senderType, String teacherEmail) {
        String senderEmail = determineSenderEmail(senderType, teacherEmail);
        return senderEmail != null && hasSenderPassword(senderEmail);
    }

    public EmailNotificationService(
            @Autowired(required = false) JavaMailSender defaultMailSender,
            @Value("${mail.enabled:true}") boolean enabled,
            @Value("${mail.from-name:河北建筑工程学院}") String fromName
    ) {
        this.defaultMailSender = defaultMailSender;
        this.enabled = enabled;
        this.fromName = fromName;
    }

    public BatchEmailResult sendAnnouncementEmail(Announcement announcement, List<Student> students, String senderType, String teacherEmail) {
        String senderEmail = determineSenderEmail(senderType, teacherEmail);
        if (senderEmail == null) {
            return buildResult("NO_SENDER", buildMissingSenderMessage(senderType), students.size(), 0, 0, 0, new java.util.ArrayList<>());
        }
        if (!hasSenderPassword(senderEmail)) {
            return buildResult("NO_SENDER_PASSWORD", buildMissingSenderPasswordMessage(senderType), students.size(), 0, 0, 0, new java.util.ArrayList<>());
        }
        if (!enabled || defaultMailSender == null) {
            return buildResult("DISABLED", "邮箱功能未启用或邮件发送器未配置。", students.size(), 0, 0, 0, new java.util.ArrayList<>());
        }

        return doSendAnnouncementEmail(announcement, students, senderEmail);
    }

    public BatchEmailResult validateAnnouncementEmailRequest(List<Student> students, String senderType, String teacherEmail) {
        String senderEmail = determineSenderEmail(senderType, teacherEmail);
        if (senderEmail == null) {
            return buildResult("NO_SENDER", buildMissingSenderMessage(senderType), students.size(), 0, 0, 0, new java.util.ArrayList<>());
        }
        if (!hasSenderPassword(senderEmail)) {
            return buildResult("NO_SENDER_PASSWORD", buildMissingSenderPasswordMessage(senderType), students.size(), 0, 0, 0, new java.util.ArrayList<>());
        }
        if (!enabled || defaultMailSender == null) {
            return buildResult("DISABLED", "邮箱功能未启用或邮件发送器未配置。", students.size(), 0, 0, 0, new java.util.ArrayList<>());
        }
        long validRecipientCount = students.stream()
                .map(this::getStudentEmail)
                .filter(email -> email != null && !email.isBlank())
                .count();
        if (validRecipientCount == 0) {
            return buildResult("NO_RECIPIENT_EMAIL", "本次范围内没有学生绑定邮箱。", students.size(), 0, 0, 0, List.of());
        }
        String smtpError = validateSmtpConnection(senderEmail);
        if (smtpError != null) {
            return buildResult("SMTP_CHECK_FAILED", buildSmtpCheckFailedMessage(senderEmail, smtpError), students.size(), (int) validRecipientCount, 0, 0, List.of());
        }
        return null;
    }

    public BatchEmailResult enqueueAnnouncementEmail(Announcement announcement, List<Student> students, String senderType, String teacherEmail) {
        String senderEmail = determineSenderEmail(senderType, teacherEmail);
        if (senderEmail == null) {
            return buildResult("NO_SENDER", buildMissingSenderMessage(senderType), students.size(), 0, 0, 0, new java.util.ArrayList<>());
        }
        if (!hasSenderPassword(senderEmail)) {
            return buildResult("NO_SENDER_PASSWORD", buildMissingSenderPasswordMessage(senderType), students.size(), 0, 0, 0, new java.util.ArrayList<>());
        }
        if (!enabled || defaultMailSender == null) {
            return buildResult("DISABLED", "邮箱功能未启用或邮件发送器未配置。", students.size(), 0, 0, 0, new java.util.ArrayList<>());
        }

        List<StudentEmailRecipient> validStudents = students.stream()
                .map(student -> new StudentEmailRecipient(student, getStudentEmail(student)))
                .filter(item -> item.email() != null && !item.email().isBlank())
                .toList();
        if (validStudents.isEmpty()) {
            return buildResult("NO_RECIPIENT_EMAIL", "本次范围内没有学生绑定邮箱。", students.size(), 0, 0, 0, List.of());
        }
        String smtpError = validateSmtpConnection(senderEmail);
        if (smtpError != null) {
            return buildResult("SMTP_CHECK_FAILED", buildSmtpCheckFailedMessage(senderEmail, smtpError), students.size(), validStudents.size(), 0, 0, List.of());
        }

        EmailTaskProgress progress = emailTaskQueueService.enqueue(
                "ANNOUNCEMENT",
                students.size(),
                validStudents.size(),
                rateLimitMillis,
                task -> processAnnouncementTask(task, announcement, validStudents, senderEmail)
        );
        return buildQueuedResult(progress);
    }

    public BatchEmailResult sendReminderEmail(Announcement announcement, List<Student> students, boolean reminder, String senderType, String teacherEmail) {
        String senderEmail = determineSenderEmail(senderType, teacherEmail);
        if (senderEmail == null) {
            return buildResult("NO_SENDER", buildMissingSenderMessage(senderType), students.size(), 0, 0, 0, new java.util.ArrayList<>());
        }
        if (!hasSenderPassword(senderEmail)) {
            return buildResult("NO_SENDER_PASSWORD", buildMissingSenderPasswordMessage(senderType), students.size(), 0, 0, 0, new java.util.ArrayList<>());
        }
        if (!enabled || defaultMailSender == null) {
            return buildResult("DISABLED", "邮箱功能未启用或邮件发送器未配置。", students.size(), 0, 0, 0, new java.util.ArrayList<>());
        }

        return doSendReminderEmail(announcement, students, reminder, senderEmail);
    }

    public BatchEmailResult enqueueReminderEmail(Announcement announcement, List<Student> students, boolean reminder, String senderType, String teacherEmail) {
        String senderEmail = determineSenderEmail(senderType, teacherEmail);
        if (senderEmail == null) {
            return buildResult("NO_SENDER", buildMissingSenderMessage(senderType), students.size(), 0, 0, 0, new java.util.ArrayList<>());
        }
        if (!hasSenderPassword(senderEmail)) {
            return buildResult("NO_SENDER_PASSWORD", buildMissingSenderPasswordMessage(senderType), students.size(), 0, 0, 0, new java.util.ArrayList<>());
        }
        if (!enabled || defaultMailSender == null) {
            return buildResult("DISABLED", "邮箱功能未启用或邮件发送器未配置。", students.size(), 0, 0, 0, new java.util.ArrayList<>());
        }

        List<StudentEmailRecipient> validStudents = students.stream()
                .map(student -> new StudentEmailRecipient(student, getStudentEmail(student)))
                .filter(item -> item.email() != null && !item.email().isBlank())
                .toList();
        if (validStudents.isEmpty()) {
            return buildResult("NO_RECIPIENT_EMAIL", "本次范围内没有学生绑定邮箱。", students.size(), 0, 0, 0, List.of());
        }
        String smtpError = validateSmtpConnection(senderEmail);
        if (smtpError != null) {
            return buildResult("SMTP_CHECK_FAILED", buildSmtpCheckFailedMessage(senderEmail, smtpError), students.size(), validStudents.size(), 0, 0, List.of());
        }

        EmailTaskProgress progress = emailTaskQueueService.enqueue(
                "REMINDER",
                students.size(),
                validStudents.size(),
                rateLimitMillis,
                task -> processReminderTask(task, announcement, validStudents, reminder, senderEmail)
        );
        return buildQueuedResult(progress);
    }

    private BatchEmailResult doSendAnnouncementEmail(Announcement announcement, List<Student> students, String senderEmail) {
        JavaMailSender sender = createMailSender(senderEmail);
        List<Student> validStudents = students.stream()
                .filter(s -> {
                    String email = getStudentEmail(s);
                    return email != null && !email.isBlank();
                })
                .toList();

        if (validStudents.isEmpty()) {
            return buildResult("NO_RECIPIENT_EMAIL", "本次范围内没有学生绑定邮箱。", students.size(), 0, 0, 0, List.of());
        }

        List<String> failedEmails = new ArrayList<>();
        List<BatchEmailResult.EmailFailure> failures = new ArrayList<>();
        long startTime = System.currentTimeMillis();

        for (Student student : validStudents) {
            String email = getStudentEmail(student);
            try {
                sendWithRetry(email, () -> sendAnnouncementMimeMessage(sender, senderEmail, email, announcement, student));
            } catch (Exception e) {
                String reason = describeEmailFailure(e);
                log.warn("Failed to send announcement email to {}: {}", email, reason);
                failedEmails.add(email);
                failures.add(buildFailure(email, reason));
            }
        }

        int successCount = validStudents.size() - failedEmails.size();
        log.info("批量发送公告邮件完成: 总数={}, 成功={}, 失败={}, 耗时={}ms",
                validStudents.size(), successCount, failedEmails.size(), System.currentTimeMillis() - startTime);

        return buildResult(buildDeliveryStatus(validStudents.size(), successCount), null,
                students.size(), validStudents.size(), successCount, failedEmails.size(),
                failedEmails, failures);
    }

    private void sendAnnouncementMimeMessage(JavaMailSender sender, String senderEmail, String toEmail,
                                              Announcement announcement, Student student) throws Exception {
        var mimeMessage = sender.createMimeMessage();
        var helper = new MimeMessageHelper(mimeMessage, false, "UTF-8");
        helper.setFrom(senderEmail.trim());
        helper.setTo(toEmail.trim());
        helper.setSubject(buildAnnouncementSubject(announcement));
        helper.setText(buildAnnouncementBody(announcement, student), true);
        sender.send(mimeMessage);
    }

    private void sendReminderMimeMessage(JavaMailSender sender, String senderEmail, String toEmail,
                                          Announcement announcement, Student student, boolean reminder) throws Exception {
        var mimeMessage = sender.createMimeMessage();
        var helper = new MimeMessageHelper(mimeMessage, false, "UTF-8");
        helper.setFrom(senderEmail.trim());
        helper.setTo(toEmail.trim());
        helper.setSubject((reminder ? "【催读】" : "") + buildAnnouncementSubject(announcement));
        helper.setText(buildReminderBody(announcement, student, reminder), true);
        sender.send(mimeMessage);
    }

    private void sendSimpleMimeMessage(JavaMailSender sender, String senderEmail, String toEmail,
                                       String title, String body) throws Exception {
        var mimeMessage = sender.createMimeMessage();
        var helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
        helper.setFrom(senderEmail.trim());
        helper.setTo(toEmail.trim());
        helper.setSubject(title);
        helper.setText(body, true);
        sender.send(mimeMessage);
    }

    private BatchEmailResult doSendReminderEmail(Announcement announcement, List<Student> students, boolean reminder, String senderEmail) {
        JavaMailSender sender = createMailSender(senderEmail);
        List<Student> validStudents = students.stream()
                .filter(s -> {
                    String email = getStudentEmail(s);
                    return email != null && !email.isBlank();
                })
                .toList();

        if (validStudents.isEmpty()) {
            return buildResult("NO_RECIPIENT_EMAIL", "本次范围内没有学生绑定邮箱。", students.size(), 0, 0, 0, List.of());
        }

        List<String> failedEmails = new ArrayList<>();
        List<BatchEmailResult.EmailFailure> failures = new ArrayList<>();
        long startTime = System.currentTimeMillis();

        for (Student student : validStudents) {
            String email = getStudentEmail(student);
            try {
                sendWithRetry(email, () -> sendReminderMimeMessage(sender, senderEmail, email, announcement, student, reminder));
            } catch (Exception e) {
                String reason = describeEmailFailure(e);
                log.warn("Failed to send reminder email to {}: {}", email, reason);
                failedEmails.add(email);
                failures.add(buildFailure(email, reason));
            }
        }

        int successCount = validStudents.size() - failedEmails.size();
        log.info("批量发送催读邮件完成: 总数={}, 成功={}, 失败={}, 耗时={}ms",
                validStudents.size(), successCount, failedEmails.size(), System.currentTimeMillis() - startTime);

        return buildResult(buildDeliveryStatus(validStudents.size(), successCount), null,
                students.size(), validStudents.size(), successCount, failedEmails.size(),
                failedEmails, failures);
    }

    public BatchEmailResult sendBatchEmail(List<String> toEmails, String title, String content,
                                          String linkText, String linkUrl, String senderType, String teacherEmail) {
        String senderEmail = determineSenderEmail(senderType, teacherEmail);
        if (senderEmail == null) {
            return buildResult("NO_SENDER", buildMissingSenderMessage(senderType), toEmails.size(), 0, 0, 0, new java.util.ArrayList<>());
        }
        if (!hasSenderPassword(senderEmail)) {
            return buildResult("NO_SENDER_PASSWORD", buildMissingSenderPasswordMessage(senderType), toEmails.size(), 0, 0, 0, new java.util.ArrayList<>());
        }
        if (!enabled || defaultMailSender == null) {
            return buildResult("DISABLED", "邮箱功能未启用或邮件发送器未配置。", toEmails.size(), 0, 0, 0, new java.util.ArrayList<>());
        }

        List<String> validEmails = toEmails.stream()
                .filter(e -> e != null && !e.isBlank())
                .toList();

        if (validEmails.isEmpty()) {
            return buildResult("NO_RECIPIENT_EMAIL", "收件人列表为空。", toEmails.size(), 0, 0, 0, List.of());
        }

        JavaMailSender sender = createMailSender(senderEmail);
        List<String> failedEmails = new ArrayList<>();
        List<BatchEmailResult.EmailFailure> failures = new ArrayList<>();
        long startTime = System.currentTimeMillis();

        String body = String.format(
                "<html><body><p>您好，</p><p>您有一条通知：</p><p><a href='%s'>%s</a></p><p>请及时查收。</p></body></html>",
                escapeHtml(linkUrl), escapeHtml(linkText)
        );

        for (String toEmail : validEmails) {
            try {
                sendWithRetry(toEmail, () -> sendSimpleMimeMessage(sender, senderEmail, toEmail.trim(), title, body));
            } catch (Exception e) {
                String reason = describeEmailFailure(e);
                log.warn("Failed to send batch email to {}: {}", toEmail, reason);
                failedEmails.add(toEmail);
                failures.add(buildFailure(toEmail, reason));
            }
        }

        int successCount = validEmails.size() - failedEmails.size();
        log.info("批量发送邮件完成: 总数={}, 成功={}, 失败={}, 耗时={}ms",
                validEmails.size(), successCount, failedEmails.size(), System.currentTimeMillis() - startTime);

        return buildResult(buildDeliveryStatus(validEmails.size(), successCount), null,
                toEmails.size(), validEmails.size(), successCount, failedEmails.size(),
                failedEmails, failures);
    }

    public void sendStudentEmailVerificationCode(String toEmail, String code) {
        String senderEmail = determineSenderEmail(SENDER_TYPE_SYSTEM, null);
        if (senderEmail == null) {
            throw new IllegalStateException(buildMissingSenderMessage(SENDER_TYPE_SYSTEM));
        }
        if (!hasSenderPassword(senderEmail)) {
            throw new IllegalStateException(buildMissingSenderPasswordMessage(SENDER_TYPE_SYSTEM));
        }
        if (!enabled) {
            throw new IllegalStateException("邮箱功能未启用。");
        }

        JavaMailSender sender = createMailSender(senderEmail);
        String body = """
                <html><body>
                <p>您好，</p>
                <p>您正在绑定或换绑河北建筑工程学院账号邮箱，本次验证码为：</p>
                <p style="font-size:24px;font-weight:700;letter-spacing:4px;">%s</p>
                <p>验证码 10 分钟内有效。若非本人操作，请忽略本邮件。</p>
                </body></html>
                """.formatted(code);
        try {
            sendWithRetry(toEmail, () -> sendSimpleMimeMessage(sender, senderEmail, toEmail.trim(), "知行育评邮箱验证码", body));
        } catch (Exception exception) {
            throw new IllegalStateException(describeEmailFailure(exception));
        }
    }

    public BatchEmailResult enqueueBatchEmail(List<String> toEmails, String title, String content,
                                              String linkText, String linkUrl, String senderType, String teacherEmail) {
        String senderEmail = determineSenderEmail(senderType, teacherEmail);
        if (senderEmail == null) {
            return buildResult("NO_SENDER", buildMissingSenderMessage(senderType), toEmails.size(), 0, 0, 0, new java.util.ArrayList<>());
        }
        if (!hasSenderPassword(senderEmail)) {
            return buildResult("NO_SENDER_PASSWORD", buildMissingSenderPasswordMessage(senderType), toEmails.size(), 0, 0, 0, new java.util.ArrayList<>());
        }
        if (!enabled || defaultMailSender == null) {
            return buildResult("DISABLED", "邮箱功能未启用或邮件发送器未配置。", toEmails.size(), 0, 0, 0, new java.util.ArrayList<>());
        }

        List<String> validEmails = toEmails.stream()
                .filter(e -> e != null && !e.isBlank())
                .map(String::trim)
                .toList();
        if (validEmails.isEmpty()) {
            return buildResult("NO_RECIPIENT_EMAIL", "收件人列表为空。", toEmails.size(), 0, 0, 0, List.of());
        }
        String smtpError = validateSmtpConnection(senderEmail);
        if (smtpError != null) {
            return buildResult("SMTP_CHECK_FAILED", buildSmtpCheckFailedMessage(senderEmail, smtpError), toEmails.size(), validEmails.size(), 0, 0, List.of());
        }

        String body = String.format(
                "<html><body><p>您好，</p><p>您有一条通知：</p><p><a href='%s'>%s</a></p><p>请及时查收。</p></body></html>",
                escapeHtml(linkUrl), escapeHtml(linkText)
        );
        EmailTaskProgress progress = emailTaskQueueService.enqueue(
                "BATCH",
                toEmails.size(),
                validEmails.size(),
                rateLimitMillis,
                task -> processSimpleTask(task, validEmails, senderEmail, title, body)
        );
        return buildQueuedResult(progress);
    }

    public Optional<EmailTaskProgress> getTaskProgress(String taskId) {
        return emailTaskQueueService.getTask(taskId);
    }

    private void processAnnouncementTask(EmailTaskProgress task, Announcement announcement,
                                         List<StudentEmailRecipient> recipients, String senderEmail) {
        JavaMailSender sender = createMailSender(senderEmail);
        for (StudentEmailRecipient recipient : recipients) {
            try {
                sendWithRetry(recipient.email(), () -> sendAnnouncementMimeMessage(sender, senderEmail, recipient.email(), announcement, recipient.student()));
                emailTaskQueueService.markSuccess(task);
            } catch (Exception e) {
                String reason = describeEmailFailure(e);
                log.warn("Failed to send announcement email to {}: {}", recipient.email(), reason);
                emailTaskQueueService.markFailure(task, recipient.email(), reason);
            }
            sleepForRateLimit();
        }
    }

    private void processReminderTask(EmailTaskProgress task, Announcement announcement,
                                     List<StudentEmailRecipient> recipients, boolean reminder, String senderEmail) {
        JavaMailSender sender = createMailSender(senderEmail);
        for (StudentEmailRecipient recipient : recipients) {
            try {
                sendWithRetry(recipient.email(), () -> sendReminderMimeMessage(sender, senderEmail, recipient.email(), announcement, recipient.student(), reminder));
                emailTaskQueueService.markSuccess(task);
            } catch (Exception e) {
                String reason = describeEmailFailure(e);
                log.warn("Failed to send reminder email to {}: {}", recipient.email(), reason);
                emailTaskQueueService.markFailure(task, recipient.email(), reason);
            }
            sleepForRateLimit();
        }
    }

    private void processSimpleTask(EmailTaskProgress task, List<String> emails, String senderEmail, String title, String body) {
        JavaMailSender sender = createMailSender(senderEmail);
        for (String toEmail : emails) {
            try {
                sendWithRetry(toEmail, () -> sendSimpleMimeMessage(sender, senderEmail, toEmail, title, body));
                emailTaskQueueService.markSuccess(task);
            } catch (Exception e) {
                String reason = describeEmailFailure(e);
                log.warn("Failed to send batch email to {}: {}", toEmail, reason);
                emailTaskQueueService.markFailure(task, toEmail, reason);
            }
            sleepForRateLimit();
        }
    }

    private void sleepForRateLimit() {
        if (rateLimitMillis <= 0) {
            return;
        }
        try {
            Thread.sleep(rateLimitMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String validateSmtpConnection(String senderEmail) {
        try {
            JavaMailSender sender = createMailSender(senderEmail);
            if (sender instanceof JavaMailSenderImpl mailSender) {
                mailSender.testConnection();
            }
            return null;
        } catch (Exception e) {
            return describeSmtpCheckFailure(e);
        }
    }

    private String buildSmtpCheckFailedMessage(String senderEmail, String reason) {
        MailServerConfig config = resolveMailServerConfig(senderEmail);
        return String.format("SMTP 校验失败（%s:%d）：%s", config.host(), config.port(), reason);
    }

    private String describeSmtpCheckFailure(Exception exception) {
        String detail = collectExceptionMessages(exception);
        String lowerDetail = detail.toLowerCase();
        if (lowerDetail.contains("authentication") || lowerDetail.contains("auth") || lowerDetail.contains("535")
                || lowerDetail.contains("username") || lowerDetail.contains("password")
                || lowerDetail.contains("login") || lowerDetail.contains("bad credentials")) {
            return "SMTP 登录失败。请检查邮箱账号、SMTP 授权码/密码以及该邮箱是否已开启 SMTP 服务。";
        }
        if (lowerDetail.contains("ssl") || lowerDetail.contains("handshake") || lowerDetail.contains("certificate")
                || lowerDetail.contains("pkix")) {
            return "SMTP 连接失败。请检查网络、端口和 SSL 配置。";
        }
        if (lowerDetail.contains("connect") || lowerDetail.contains("timeout") || lowerDetail.contains("timed out")
                || lowerDetail.contains("unknownhost") || lowerDetail.contains("unknown host")
                || lowerDetail.contains("refused") || lowerDetail.contains("unreachable")
                || lowerDetail.contains("no route") || lowerDetail.contains("connection reset")) {
            return "无法连接 SMTP 服务器，请检查网络环境、SMTP 地址、端口和防火墙设置。";
        }
        if (lowerDetail.contains("rate") || lowerDetail.contains("thrott") || lowerDetail.contains("too many")
                || lowerDetail.contains("limit") || lowerDetail.contains("temporar")
                || lowerDetail.contains("try later") || lowerDetail.contains("busy")) {
            return "SMTP 登录被邮箱服务商临时限制，请稍后再试，或检查该邮箱是否触发了服务商风控。";
        }
        return detail.isBlank()
                ? "SMTP 连接校验未通过，请检查邮箱 SMTP 配置和网络环境。"
                : "SMTP 连接校验未通过：" + detail;
    }

    private void sendWithRetry(String toEmail, EmailSendOperation operation) throws Exception {
        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_SEND_ATTEMPTS; attempt++) {
            try {
                operation.send();
                return;
            } catch (Exception e) {
                lastException = e;
                if (attempt >= MAX_SEND_ATTEMPTS) {
                    break;
                }
                log.warn("Email send attempt {}/{} failed for {}, retrying: {}",
                        attempt, MAX_SEND_ATTEMPTS, toEmail, e.getMessage());
                sleepBeforeRetry();
            }
        }
        throw lastException;
    }

    private void sleepBeforeRetry() {
        try {
            Thread.sleep(RETRY_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String determineSenderEmail(String senderType, String teacherEmail) {
        if (SENDER_TYPE_TEACHER.equals(senderType)) {
            if (teacherEmail == null || teacherEmail.isBlank()) {
                log.warn("Teacher email not provided for TEACHER sender type");
                return null;
            }
            return teacherEmail;
        }
        if (SENDER_TYPE_SYSTEM.equals(senderType)) {
            Optional<SystemEmail> systemEmail = systemEmailService.getActiveEmail();
            if (systemEmail.isEmpty()) {
                log.warn("No system email configured");
                return null;
            }
            return systemEmail.get().getEmail();
        }
        if (teacherEmail != null && !teacherEmail.isBlank()) {
            return teacherEmail;
        }
        Optional<SystemEmail> systemEmail = systemEmailService.getActiveEmail();
        return systemEmail.map(SystemEmail::getEmail).orElse(null);
    }

    private String determineSenderPassword(String senderEmail) {
        Optional<SystemEmail> systemEmail = systemEmailService.getActiveEmail();
        if (systemEmail.isPresent() && systemEmail.get().getEmail().equals(senderEmail)) {
            return systemEmail.get().getPassword();
        }
        return userRepository.findByUsername(senderEmail)
                .map(User::getEmailPassword)
                .or(() -> userRepository.findFirstByEmailOrderByIdAsc(senderEmail).map(User::getEmailPassword))
                .orElse(null);
    }

    private boolean hasSenderPassword(String senderEmail) {
        String password = determineSenderPassword(senderEmail);
        return password != null && !password.isBlank();
    }

    private JavaMailSender createMailSender(String senderEmail) {
        String password = determineSenderPassword(senderEmail);
        MailServerConfig serverConfig = resolveMailServerConfig(senderEmail);
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(serverConfig.host());
        mailSender.setPort(serverConfig.port());
        mailSender.setUsername(senderEmail);
        mailSender.setPassword(password);

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", serverConfig.sslEnabled() ? "smtps" : "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", String.valueOf(serverConfig.startTlsEnabled()));
        props.put("mail.smtp.starttls.required", "false");
        props.put("mail.smtp.ssl.enable", String.valueOf(serverConfig.sslEnabled()));
        props.put("mail.smtp.timeout", String.valueOf(timeout));
        props.put("mail.smtp.connectiontimeout", String.valueOf(timeout));
        props.put("mail.smtps.auth", "true");
        props.put("mail.smtps.ssl.enable", String.valueOf(serverConfig.sslEnabled()));
        props.put("mail.smtps.timeout", String.valueOf(timeout));
        props.put("mail.smtps.connectiontimeout", String.valueOf(timeout));

        Properties sessionProps = new Properties();
        sessionProps.put("mail.smtp.auth", "true");
        sessionProps.put("mail.smtp.starttls.enable", String.valueOf(serverConfig.startTlsEnabled()));
        sessionProps.put("mail.smtp.ssl.enable", String.valueOf(serverConfig.sslEnabled()));
        sessionProps.put("mail.smtp.timeout", String.valueOf(timeout));
        sessionProps.put("mail.smtp.connectiontimeout", String.valueOf(timeout));
        sessionProps.put("mail.smtps.auth", "true");
        sessionProps.put("mail.smtps.ssl.enable", String.valueOf(serverConfig.sslEnabled()));
        sessionProps.put("mail.smtps.timeout", String.valueOf(timeout));
        sessionProps.put("mail.smtps.connectiontimeout", String.valueOf(timeout));

        Session session = Session.getInstance(sessionProps, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(senderEmail, password);
            }
        });
        mailSender.setSession(session);
        return mailSender;
    }

    private MailServerConfig resolveMailServerConfig(String senderEmail) {
        String domain = "";
        if (senderEmail != null) {
            int atIndex = senderEmail.lastIndexOf('@');
            if (atIndex >= 0 && atIndex + 1 < senderEmail.length()) {
                domain = senderEmail.substring(atIndex + 1).trim().toLowerCase();
            }
        }
        return switch (domain) {
            case "qq.com" -> new MailServerConfig("smtp.qq.com", 465, true, false);
            case "163.com" -> new MailServerConfig("smtp.163.com", 465, true, false);
            case "126.com" -> new MailServerConfig("smtp.126.com", 465, true, false);
            case "yeah.net" -> new MailServerConfig("smtp.yeah.net", 465, true, false);
            case "sina.com", "sina.cn" -> new MailServerConfig("smtp.sina.com", 465, true, false);
            case "sohu.com" -> new MailServerConfig("smtp.sohu.com", 465, true, false);
            case "gmail.com" -> new MailServerConfig("smtp.gmail.com", 587, false, true);
            case "outlook.com", "hotmail.com", "live.com" -> new MailServerConfig("smtp-mail.outlook.com", 587, false, true);
            default -> new MailServerConfig(mailHost, mailPort, sslEnabled, !sslEnabled);
        };
    }

    private String buildDeliveryStatus(int total, int successCount) {
        if (total == 0) {
            return "NO_RECIPIENT_EMAIL";
        }
        if (successCount == total) {
            return "SUCCESS";
        }
        if (successCount == 0) {
            return "FAILED";
        }
        return "PARTIAL_SUCCESS";
    }

    private String buildMissingSenderMessage(String senderType) {
        if (SENDER_TYPE_TEACHER.equals(senderType)) {
            return "教师邮箱未配置。";
        }
        if (SENDER_TYPE_SYSTEM.equals(senderType)) {
            return "默认邮箱未配置。";
        }
        return "发件邮箱未配置。";
    }

    private String buildMissingSenderPasswordMessage(String senderType) {
        if (SENDER_TYPE_TEACHER.equals(senderType)) {
            return "教师邮箱SMTP授权码未配置。";
        }
        if (SENDER_TYPE_SYSTEM.equals(senderType)) {
            return "默认邮箱SMTP授权码未配置。";
        }
        return "发件邮箱SMTP授权码未配置。";
    }

    private BatchEmailResult buildResult(String status, String message, int recipientCount, int total, int success, int failure, List<String> failedEmails) {
        return buildResult(status, message, recipientCount, total, success, failure, failedEmails, List.of());
    }

    private BatchEmailResult buildResult(String status, String message, int recipientCount, int total, int success, int failure, List<String> failedEmails, List<BatchEmailResult.EmailFailure> failures) {
        BatchEmailResult result = new BatchEmailResult();
        result.setStatus(status);
        result.setRecipientCount(recipientCount);
        result.setTotal(total);
        result.setSuccessCount(success);
        result.setFailureCount(failure);
        result.setSkippedCount(Math.max(recipientCount - total, 0));
        result.setFailedEmails(failedEmails);
        result.setFailures(failures);
        result.setMessage(message != null ? message : buildResultMessage(result));
        return result;
    }

    private BatchEmailResult buildQueuedResult(EmailTaskProgress progress) {
        BatchEmailResult result = new BatchEmailResult();
        result.setTaskId(progress.getTaskId());
        result.setType(progress.getType());
        result.setStatus(progress.getStatus());
        result.setMessage("邮件任务已提交。");
        result.setRecipientCount(progress.getRecipientCount());
        result.setTotal(progress.getTotal());
        result.setSuccessCount(progress.getSuccessCount());
        result.setFailureCount(progress.getFailureCount());
        result.setSkippedCount(progress.getSkippedCount());
        result.setProcessedCount(progress.getProcessedCount());
        result.setPendingCount(progress.getPendingCount());
        result.setRateLimitMillis(progress.getRateLimitMillis());
        result.setFailedEmails(progress.getFailedEmails());
        result.setFailures(progress.getFailures());
        return result;
    }

    private String buildResultMessage(BatchEmailResult result) {
        if ("NO_RECIPIENT_EMAIL".equals(result.getStatus())) {
            return "本次范围内没有学生绑定邮箱。";
        }
        if ("SUCCESS".equals(result.getStatus())) {
            return String.format("邮件已成功发送给 %d 封邮箱。", result.getSuccessCount());
        }
        if ("FAILED".equals(result.getStatus())) {
            return String.format("邮件全部发送失败，共 %d 封。", result.getFailureCount());
        }
        if ("PARTIAL_SUCCESS".equals(result.getStatus())) {
            return String.format("邮件发送完成：成功 %d 封，失败 %d 封。", result.getSuccessCount(), result.getFailureCount());
        }
        return "邮件发送已完成。";
    }

    private BatchEmailResult.EmailFailure buildFailure(String email, String reason) {
        BatchEmailResult.EmailFailure failure = new BatchEmailResult.EmailFailure();
        failure.setEmail(email);
        failure.setReason(reason);
        return failure;
    }

    private String describeEmailFailure(Exception exception) {
        String detail = collectExceptionMessages(exception);
        String lowerDetail = detail.toLowerCase();
        if (lowerDetail.contains("rate") || lowerDetail.contains("thrott") || lowerDetail.contains("too many")
                || lowerDetail.contains("limit") || lowerDetail.contains("temporar")
                || lowerDetail.contains("try later") || lowerDetail.contains("busy")) {
            return "邮件发送被邮箱服务商临时限制，可能是发送频率过高或同一地址重复发送。";
        }
        if (lowerDetail.contains("authentication") || lowerDetail.contains("auth") || lowerDetail.contains("535")
                || lowerDetail.contains("username") || lowerDetail.contains("password")
                || lowerDetail.contains("login") || lowerDetail.contains("bad credentials")) {
            return "邮件发送被邮箱服务商拒绝，可能是邮箱账号、SMTP 授权码/密码不正确，或未开启 SMTP 服务。";
        }
        if (lowerDetail.contains("ssl") || lowerDetail.contains("handshake") || lowerDetail.contains("certificate")
                || lowerDetail.contains("pkix")) {
            return "SMTP SSL 连接失败，请检查端口、SSL/STARTTLS 配置和证书链。";
        }
        if (lowerDetail.contains("connect") || lowerDetail.contains("timeout") || lowerDetail.contains("timed out")
                || lowerDetail.contains("unknownhost") || lowerDetail.contains("unknown host")
                || lowerDetail.contains("refused") || lowerDetail.contains("unreachable")
                || lowerDetail.contains("no route") || lowerDetail.contains("connection reset")) {
            return "连接邮件服务器失败或超时，请检查 SMTP 地址、端口、SSL 配置和网络。";
        }
        if (lowerDetail.contains("recipient") || lowerDetail.contains("address") || lowerDetail.contains("550")
                || lowerDetail.contains("invalid")) {
            return "收件邮箱地址可能无效，或被邮件服务器拒收。";
        }
        if (lowerDetail.contains("from") || lowerDetail.contains("sender")) {
            return "发件邮箱配置可能不正确，或与登录账号不一致。";
        }
        return detail.isBlank() ? "邮件服务器返回未知错误，请查看后端日志。" : detail;
    }

    private String collectExceptionMessages(Throwable throwable) {
        List<String> messages = new java.util.ArrayList<>();
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                messages.add(current.getMessage());
            }
            current = current.getCause();
        }
        return messages.stream().distinct().collect(Collectors.joining("；"));
    }

    private String buildAnnouncementSubject(Announcement announcement) {
        return String.format("【%s】%s", fromName, announcement.getTitle());
    }

    private String buildAnnouncementBody(Announcement announcement, Student student) {
        return String.format(
                "<html><body><p>%s同学，你好：</p><p>你收到一条新的通知。</p><p>标题：%s</p><p>发布人：%s</p><p>内容：<a href=\"%s\">查看通知</a></p><p>请尽快登录河北建筑工程学院系统确认已读。</p></body></html>",
                escapeHtml(student.getName()),
                escapeHtml(announcement.getTitle()),
                escapeHtml(announcement.getPublisherName()),
                escapeHtml(frontendBaseUrl + "/student/announcements")
        );
    }

    private String buildReminderBody(Announcement announcement, Student student, boolean reminder) {
        return String.format(
                "<html><body><p>%s同学，你好：</p>%s<p>通知</p><p>标题：%s</p><p>发布人：%s</p><p>内容：<a href=\"%s\">查看通知</a></p><p>请尽快登录知行育评系统确认已读。</p></body></html>",
                escapeHtml(student.getName()),
                reminder ? "<p>你有一条未读公告，请及时查看。</p>" : "",
                escapeHtml(announcement.getTitle()),
                escapeHtml(announcement.getPublisherName()),
                escapeHtml(frontendBaseUrl + "/student/announcements")
        );
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    public boolean sendEmail(String toEmail, String title, String linkText, String linkUrl) {
        return sendEmail(toEmail, title, linkText, linkUrl, null, null);
    }

    public boolean sendEmail(String toEmail, String title, String linkText, String linkUrl, String senderEmail) {
        return sendEmail(toEmail, title, linkText, linkUrl, senderEmail, null);
    }

    public boolean sendEmail(String toEmail, String title, String linkText, String linkUrl, String senderEmail, String senderType) {
        if (!enabled || defaultMailSender == null) {
            return false;
        }
        if (toEmail == null || toEmail.isBlank()) {
            return false;
        }
        String actualSender = determineSenderEmail(senderType, senderEmail);
        if (actualSender == null) {
            return false;
        }
        try {
            JavaMailSender sender = createMailSender(actualSender);
            String body = String.format(
                    "<html><body><p>您好，</p><p>您收到一条通知：</p><p><a href='%s'>%s</a></p><p>请及时查收。</p></body></html>",
                    escapeHtml(linkUrl), escapeHtml(linkText)
            );
            sendSimpleMimeMessage(sender, actualSender, toEmail.trim(), title, body);
            return true;
        } catch (Exception e) {
            log.warn("Failed to send email to {}: {}", toEmail, e.getMessage());
            return false;
        }
    }

    /**
     * 获取学生邮箱，从关联的User获取
     */
    private String getStudentEmail(Student student) {
        try {
            if (student.getUser() != null && student.getUser().getEmail() != null && !student.getUser().getEmail().isBlank()) {
                return student.getUser().getEmail();
            }
        } catch (Exception ignored) {
            // Async email tasks may run after the original JPA session has closed.
        }
        if (student.getUserId() != null) {
            return userRepository.findById(student.getUserId())
                    .map(User::getEmail)
                    .filter(email -> email != null && !email.isBlank())
                    .orElse(null);
        }
        return null;
    }

    private record StudentEmailRecipient(Student student, String email) {
    }

    private record MailServerConfig(String host, int port, boolean sslEnabled, boolean startTlsEnabled) {
    }

    @FunctionalInterface
    private interface EmailSendOperation {
        void send() throws Exception;
    }
}
