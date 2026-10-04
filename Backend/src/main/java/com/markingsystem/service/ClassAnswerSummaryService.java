package com.markingsystem.service;

import com.markingsystem.entity.KnowledgeChatLog;
import com.markingsystem.entity.Student;
import com.markingsystem.entity.TeacherAiReport;
import com.markingsystem.repository.KnowledgeChatLogRepository;
import com.markingsystem.repository.StudentRepository;
import com.markingsystem.repository.TeacherAiReportRepository;
import com.markingsystem.service.llm.LlmChatService;
import com.markingsystem.service.llm.spi.ChatRequest;
import com.markingsystem.service.llm.spi.ChatResponse;
import com.markingsystem.service.llm.spi.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 班级答疑总结服务。
 *
 * <p>根据某个班级学生与 AI 的对话记录，让大模型从「班级疑惑 / 典型问题 / 学情 /
 * 给老师的教学建议」等维度生成一份班情总结，供老师快速掌握班级共性诉求。
 * 历史落在通用表 {@link TeacherAiReport}（reportType=CLASS_ANSWER_SUMMARY），
 * 同类教师侧报告应复用该表，禁止再开专用历史表。
 */
@Service
public class ClassAnswerSummaryService {

    private static final Logger log = LoggerFactory.getLogger(ClassAnswerSummaryService.class);

    private static final int MAX_CONVERSATIONS = 400;

    @Autowired
    private KnowledgeChatLogRepository chatLogRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private TeacherAiReportRepository teacherAiReportRepository;

    @Autowired
    private LlmChatService llmChatService;

    /**
     * 生成班级答疑总结，并落库供回看。
     *
     * @param teacherId 生成教师 ID
     * @param className 班级名称（对话日志按对话发生时记录的班级归属）
     * @param startTime 起始时间（可为 null，表示不限）
     * @param endTime   结束时间（可为 null，表示不限）
     * @return 结构化总结报告
     */
    public Map<String, Object> generate(Long teacherId, String className, String startTime, String endTime) {
        LocalDateTime start = parseTime(startTime);
        LocalDateTime end = parseTime(endTime);

        List<Student> classStudents = studentRepository.findByClassName(className.trim());
        Set<String> studentNos = new HashSet<>();
        Set<String> studentNames = new HashSet<>();
        for (Student student : classStudents) {
            if (student.getStudentNo() != null && !student.getStudentNo().isBlank()) {
                studentNos.add(student.getStudentNo().trim().toLowerCase(Locale.ROOT));
            }
            if (student.getName() != null && !student.getName().isBlank()) {
                studentNames.add(student.getName().trim());
            }
        }

        List<KnowledgeChatLog> allPageLogs = new ArrayList<>(chatLogRepository.findTop5000ByOrderByIdDesc());
        allPageLogs.removeIf(e -> e.getAppId() != null);
        if (start != null) {
            allPageLogs.removeIf(e -> e.getCreatedAt() == null || e.getCreatedAt().isBefore(start));
        }
        if (end != null) {
            allPageLogs.removeIf(e -> e.getCreatedAt() == null || e.getCreatedAt().isAfter(end));
        }

        List<KnowledgeChatLog> logs = new ArrayList<>();
        int unattributedCount = 0;
        for (KnowledgeChatLog entry : allPageLogs) {
            if (belongsToClass(entry, className, studentNos, studentNames)) {
                logs.add(entry);
            } else if (isUnattributed(entry)) {
                unattributedCount++;
            }
        }

        // 取最新的一段，避免超出上限时丢弃无关的旧记录
        logs.sort(Comparator.comparing(KnowledgeChatLog::getId, Comparator.reverseOrder()));
        if (logs.size() > MAX_CONVERSATIONS) {
            logs = new ArrayList<>(logs.subList(0, MAX_CONVERSATIONS));
        }
        // 恢复时间正序，便于模型理解上下文
        logs.sort(Comparator.comparing(KnowledgeChatLog::getCreatedAt,
                Comparator.nullsLast(Comparator.naturalOrder())));

        int count = logs.size();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("conversationCount", count);

        if (count == 0) {
            String emptyMsg;
            if (unattributedCount > 0) {
                emptyMsg = "该时间段内有 " + unattributedCount
                        + " 条学生对话，但缺少班级/学号归属，无法归集到本班。"
                        + "请让本班学生重新向 AI 提问后再生成总结。";
            } else {
                emptyMsg = "该时间段内没有可用的学生会话记录。";
            }
            result.put("summary", emptyMsg);
            TeacherAiReport saved = persistRecord(
                    teacherId, className, startTime, endTime, count, emptyMsg, unattributedCount);
            result.put("meta", toMeta(saved, unattributedCount));
            return result;
        }

        String summaryText = summarize(className, logs);
        result.put("summary", summaryText);
        TeacherAiReport saved = persistRecord(
                teacherId, className, startTime, endTime, count, summaryText, null);
        result.put("meta", toMeta(saved, null));
        return result;
    }

    /**
     * 当前教师的历史总结列表；可按班级筛选。
     */
    public List<Map<String, Object>> listHistory(Long teacherId, String className) {
        List<TeacherAiReport> records;
        if (className != null && !className.isBlank()) {
            records = teacherAiReportRepository
                    .findByTeacherIdAndReportTypeAndClassNameOrderByGeneratedAtDesc(
                            teacherId, TeacherAiReport.TYPE_CLASS_ANSWER_SUMMARY, className.trim());
        } else {
            records = teacherAiReportRepository.findByTeacherIdAndReportTypeOrderByGeneratedAtDesc(
                    teacherId, TeacherAiReport.TYPE_CLASS_ANSWER_SUMMARY);
        }
        List<Map<String, Object>> list = new ArrayList<>();
        for (TeacherAiReport record : records) {
            list.add(toHistoryItem(record, false));
        }
        return list;
    }

    /**
     * 查看单条历史（仅本人）。
     */
    public Optional<Map<String, Object>> getHistory(Long teacherId, Long id) {
        return teacherAiReportRepository
                .findByIdAndTeacherIdAndReportType(id, teacherId, TeacherAiReport.TYPE_CLASS_ANSWER_SUMMARY)
                .map(record -> toHistoryItem(record, true));
    }

    private TeacherAiReport persistRecord(Long teacherId, String className,
                                          String startTime, String endTime,
                                          int conversationCount, String summary,
                                          Integer unattributedCount) {
        TeacherAiReport record = new TeacherAiReport();
        record.setTeacherId(teacherId);
        record.setReportType(TeacherAiReport.TYPE_CLASS_ANSWER_SUMMARY);
        record.setClassName(className);
        record.setTimeRangeStart(startTime);
        record.setTimeRangeEnd(endTime);
        record.setConversationCount(conversationCount);
        record.setContent(summary);
        if (unattributedCount != null) {
            record.setMetaJson("{\"unattributedCount\":" + unattributedCount + "}");
        }
        record.setGeneratedAt(LocalDateTime.now());
        return teacherAiReportRepository.save(record);
    }

    private Map<String, Object> toMeta(TeacherAiReport record, Integer unattributedCount) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("id", record.getId());
        meta.put("className", record.getClassName());
        meta.put("startTime", safeText(record.getTimeRangeStart()));
        meta.put("endTime", safeText(record.getTimeRangeEnd()));
        meta.put("generatedAt", record.getGeneratedAt() == null
                ? LocalDateTime.now().toString()
                : record.getGeneratedAt().toString());
        if (unattributedCount != null) {
            meta.put("unattributedCount", unattributedCount);
        }
        return meta;
    }

    private Map<String, Object> toHistoryItem(TeacherAiReport record, boolean includeSummary) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", record.getId());
        item.put("className", record.getClassName());
        item.put("startTime", safeText(record.getTimeRangeStart()));
        item.put("endTime", safeText(record.getTimeRangeEnd()));
        item.put("conversationCount", record.getConversationCount() == null ? 0 : record.getConversationCount());
        item.put("generatedAt", record.getGeneratedAt() == null ? null : record.getGeneratedAt().toString());
        if (includeSummary) {
            item.put("summary", record.getContent() == null ? "" : record.getContent());
        } else {
            item.put("summaryPreview", preview(record.getContent(), 80));
        }
        return item;
    }

    private String preview(String value, int max) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String plain = value.replaceAll("[#*`>\\[\\]()]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return plain.length() <= max ? plain : plain.substring(0, max) + "…";
    }

    /**
     * 判断日志是否归属指定班级：优先按记录的班级名，其次按学号命中花名册，再次按姓名命中（排除通用“学生”）。
     */
    private boolean belongsToClass(KnowledgeChatLog entry, String className,
                                   Set<String> studentNos, Set<String> studentNames) {
        if (entry.getClassName() != null && !entry.getClassName().isBlank()
                && className.equalsIgnoreCase(entry.getClassName().trim())) {
            return true;
        }
        if (entry.getStudentNo() != null && !entry.getStudentNo().isBlank()
                && studentNos.contains(entry.getStudentNo().trim().toLowerCase(Locale.ROOT))) {
            return true;
        }
        String userName = entry.getUserName();
        return userName != null && !userName.isBlank()
                && !"学生".equals(userName.trim())
                && studentNames.contains(userName.trim());
    }

    /** 学生页面聊天但既无班级也无学号，无法按班归集。 */
    private boolean isUnattributed(KnowledgeChatLog entry) {
        return (entry.getClassName() == null || entry.getClassName().isBlank())
                && (entry.getStudentNo() == null || entry.getStudentNo().isBlank());
    }

    private String summarize(String className, List<KnowledgeChatLog> logs) {
        try {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < logs.size(); i++) {
                KnowledgeChatLog e = logs.get(i);
                builder.append("【第 ").append(i + 1).append(" 轮】学生：")
                        .append(nonNull(e.getUserName()))
                        .append("\n学生提问：").append(nonNull(e.getUserMessage()))
                        .append("\nAI 回复：").append(elide(nonNull(e.getAssistantMessage()), 500));
                if (e.getCreatedAt() != null) {
                    builder.append("\n时间：").append(e.getCreatedAt());
                }
                builder.append("\n\n");
            }

            ChatRequest req = new ChatRequest();
            List<Message> msgs = new ArrayList<>();
            msgs.add(new Message("system",
                    "你是高校班主任的教学助理。请基于下方【" + className + "】班级若干学生与 AI 助手的对话记录，"
                            + "分析并总结本班的共性表现，输出一份给任课老师 / 班主任看的《班级答疑分析》。\n"
                            + "请严格按以下 Markdown 结构输出（只输出正文，不要解读、不要寒暄）：\n"
                            + "## 一、班级疑惑\n列出学生问得最多的知识点或制度理解不清的点。\n"
                            + "## 二、典型问题\n列出几类有代表性、值得关注的问题（现象、涉及面）。\n"
                            + "## 三、学情观察\n从提问频率、内容深浅、关注方向总结班级整体学习状态。\n"
                            + "## 四、给老师的建议\n给出具体、可操作的教学或管理建议。\n"
                            + "要求：用中文；基于记录客观提炼，不要编造记录中没有的信息；"
                            + "若某方面信息不足，明确写“记录有限，暂无明显特征”。\n"
                            + "证据与措辞约束（必须遵守）：\n"
                            + "1. 只陈述可从对话直接观察到的行为与诉求，禁止把常见操作过度解读为心理状态、能力缺陷或系统故障。\n"
                            + "2. 同一学生就同一诉求间隔数小时（甚至一天内）再次提问，默认视为正常复核/再确认，"
                            + "不要写成“不会查、路径不清、反馈不稳定、成绩焦虑、反复确认焦虑”等；"
                            + "除非另有更强证据（如数分钟内连续刷同一问、明确抱怨查不到/报错、或学生自己表述焦虑）。\n"
                            + "3. 不要用单科成绩或挂科去反推提问动机；成绩与提问动机无直接证据时不要强行关联。\n"
                            + "4. 样本很少（如仅个别学生、少量轮次）时，明确写样本有限，避免把个人行为上升为班级共性。\n"
                            + "5. 用中性、克制的表述：可写“再次查询成绩”“希望了解专业学习资源”；"
                            + "避免夸张归因与主观推测。"));
            msgs.add(new Message("user", builder.toString()));
            req.setMessages(msgs);
            req.setMaxTokens(3000);

            ChatResponse resp = llmChatService.chat(req);
            if (resp == null || resp.getContent() == null || resp.getContent().isBlank()) {
                return "AI 暂时无法生成总结，请稍后重试。";
            }
            return resp.getContent().trim();
        } catch (Exception ex) {
            log.warn("Class answer summary generation failed: {}", ex.getMessage());
            return "AI 总结生成失败：" + ex.getMessage();
        }
    }

    private LocalDateTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            String v = value.trim();
            if (v.matches("^\\d{4}-\\d{2}-\\d{2}$")) {
                return LocalDate.parse(v).atStartOfDay();
            }
            if (v.indexOf('.') > 0) {
                return LocalDateTime.parse(v);
            }
            return LocalDateTime.parse(v.replace(' ', 'T'));
        } catch (Exception ex) {
            return null;
        }
    }

    private String safeText(String value) {
        return value == null || value.isBlank() ? "不限" : value;
    }

    private String nonNull(String value) {
        return value == null ? "" : value;
    }

    private String elide(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max) + "…（已截断）";
    }
}
