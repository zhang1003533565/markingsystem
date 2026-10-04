package com.markingsystem.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.markingsystem.entity.ExtraScoreApplication;
import com.markingsystem.entity.ExtraScoreApplicationEvent;
import com.markingsystem.repository.ExtraScoreApplicationEventRepository;
import com.markingsystem.util.RequestUserContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ExtraScoreApplicationEventService {

    private static final List<String> SUBMISSION_EVENTS = List.of("SUBMITTED", "RESUBMITTED");

    private static final Map<String, String> EVENT_LABELS = Map.ofEntries(
            Map.entry("CREATED", "创建草稿"),
            Map.entry("UPDATED", "修改申请"),
            Map.entry("SUBMITTED", "提交申请"),
            Map.entry("RESUBMITTED", "重新提交"),
            Map.entry("WITHDRAWAL_REQUESTED", "申请撤回"),
            Map.entry("WITHDRAWN", "撤回成功"),
            Map.entry("WITHDRAWAL_REJECTED", "撤回被拒绝"),
            Map.entry("COMMITTEE_APPROVED", "班委初审通过"),
            Map.entry("COMMITTEE_RETURNED", "班委打回"),
            Map.entry("COMMITTEE_FORCED", "班委推进至教师审核"),
            Map.entry("TEACHER_APPROVED", "教师终审通过"),
            Map.entry("TEACHER_RETURNED", "教师打回"),
            Map.entry("TEACHER_IMPORTED", "教师导入"),
            Map.entry("MIGRATED", "历史记录迁移")
    );

    private final ExtraScoreApplicationEventRepository eventRepository;
    private final ObjectMapper objectMapper;

    public ExtraScoreApplicationEventService(
            ExtraScoreApplicationEventRepository eventRepository,
            ObjectMapper objectMapper
    ) {
        this.eventRepository = eventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void recordSubmission(
            ExtraScoreApplication application,
            String fromStatus,
            RequestUserContext context,
            String operatorName
    ) {
        if (application == null || application.getId() == null) {
            return;
        }
        boolean submittedBefore = eventRepository.existsByApplicationIdAndEventTypeIn(
                application.getId(),
                SUBMISSION_EVENTS
        );
        if (submittedBefore) {
            application.setCurrentRevisionNo(Math.max(1, safeRevision(application)) + 1);
        } else if (application.getCurrentRevisionNo() == null || application.getCurrentRevisionNo() < 1) {
            application.setCurrentRevisionNo(1);
        }
        record(
                application,
                submittedBefore ? "RESUBMITTED" : "SUBMITTED",
                fromStatus,
                context,
                operatorName,
                "",
                true
        );
    }

    @Transactional
    public void record(
            ExtraScoreApplication application,
            String eventType,
            String fromStatus,
            RequestUserContext context,
            String operatorName,
            String comment,
            boolean includeSnapshot
    ) {
        if (application == null || application.getId() == null || eventType == null || eventType.isBlank()) {
            return;
        }
        ExtraScoreApplicationEvent event = new ExtraScoreApplicationEvent();
        event.setApplicationId(application.getId());
        event.setRevisionNo(safeRevision(application));
        event.setEventType(eventType.trim().toUpperCase());
        event.setFromStatus(blankToNull(fromStatus));
        event.setToStatus(blankToNull(application.getStatus()));
        event.setReviewStage(blankToNull(application.getReviewStage()));
        if (context != null) {
            event.setOperatorId(context.getUserId());
            event.setOperatorRole(blankToNull(context.getRole()));
        }
        event.setOperatorName(blankToNull(operatorName));
        event.setComment(blankToNull(comment));
        if (includeSnapshot) {
            event.setSnapshotJson(serializeSnapshot(application));
        }
        eventRepository.save(event);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listTimeline(Long applicationId) {
        if (applicationId == null) {
            return List.of();
        }
        return eventRepository.findByApplicationIdOrderByOccurredAtDescIdDesc(applicationId).stream()
                .map(this::toMap)
                .toList();
    }

    @Transactional
    public void deleteTimeline(Long applicationId) {
        if (applicationId != null) {
            eventRepository.deleteByApplicationId(applicationId);
        }
    }

    private Map<String, Object> toMap(ExtraScoreApplicationEvent event) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", event.getId());
        result.put("revisionNo", event.getRevisionNo());
        result.put("eventType", event.getEventType());
        result.put("eventLabel", EVENT_LABELS.getOrDefault(event.getEventType(), event.getEventType()));
        result.put("fromStatus", event.getFromStatus());
        result.put("toStatus", event.getToStatus());
        result.put("reviewStage", event.getReviewStage());
        result.put("operatorName", event.getOperatorName());
        result.put("operatorRole", event.getOperatorRole());
        result.put("comment", event.getComment());
        result.put("occurredAt", event.getOccurredAt());
        return result;
    }

    private String serializeSnapshot(ExtraScoreApplication application) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("scoreType", application.getScoreType());
        snapshot.put("templateId", application.getTemplateId());
        snapshot.put("templateName", application.getTemplateName());
        snapshot.put("groupName", application.getGroupName());
        snapshot.put("itemId", application.getItemId());
        snapshot.put("itemName", application.getItemName());
        snapshot.put("levelRuleId", application.getLevelRuleId());
        snapshot.put("levelName", application.getLevelName());
        snapshot.put("authorRuleId", application.getAuthorRuleId());
        snapshot.put("authorLabel", application.getAuthorLabel());
        snapshot.put("expectedScore", application.getExpectedScore());
        snapshot.put("scoreDirection", application.getScoreDirection());
        snapshot.put("certName", application.getCertName());
        snapshot.put("certFileName", application.getCertFileName());
        snapshot.put("certFileUrl", application.getCertFileUrl());
        snapshot.put("proofText", application.getProofText());
        snapshot.put("proofImages", application.getProofImages());
        snapshot.put("detailDesc", application.getDetailDesc());
        snapshot.put("awardTime", application.getAwardTime());
        snapshot.put("submissionPeriodId", application.getSubmissionPeriodId());
        snapshot.put("submissionPeriodLabel", application.getSubmissionPeriodLabel());
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    private int safeRevision(ExtraScoreApplication application) {
        return application.getCurrentRevisionNo() == null || application.getCurrentRevisionNo() < 1
                ? 1
                : application.getCurrentRevisionNo();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
