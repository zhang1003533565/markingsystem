package com.markingsystem.service;

import com.markingsystem.entity.ExtraScoreApplication;
import com.markingsystem.entity.ExtraScoreTemplateGroup;
import com.markingsystem.entity.ExtraScoreTemplateItem;
import com.markingsystem.repository.ExtraScoreTemplateGroupRepository;
import com.markingsystem.repository.ExtraScoreTemplateItemRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ExtraScoreEffectiveScoreService {

    private final ExtraScoreTemplateGroupRepository groupRepository;
    private final ExtraScoreTemplateItemRepository itemRepository;

    public ExtraScoreEffectiveScoreService(
            ExtraScoreTemplateGroupRepository groupRepository,
            ExtraScoreTemplateItemRepository itemRepository
    ) {
        this.groupRepository = groupRepository;
        this.itemRepository = itemRepository;
    }

    public record EffectiveScoreResult(
            Long applicationId,
            double originalSignedScore,
            double effectiveSignedScore,
            boolean counted,
            String excludeReason,
            String exclusiveRule,
            Long countedApplicationId,
            String countedApplicationLabel
    ) {
    }

    public double resolveSignedScore(ExtraScoreApplication application) {
        if (application == null || application.getExpectedScore() == null) {
            return 0D;
        }
        return "deduct".equals(normalizeScoreDirection(application.getScoreDirection()))
                ? -application.getExpectedScore()
                : application.getExpectedScore();
    }

    public double sumEffectiveSignedScores(List<ExtraScoreApplication> applications) {
        return resolveEffectiveSignedScores(applications).values().stream()
                .mapToDouble(Double::doubleValue)
                .sum();
    }

    public Map<String, Double> summarizeEffectiveScoresByType(List<ExtraScoreApplication> applications) {
        Map<String, Double> result = new LinkedHashMap<>();
        result.put("MORAL", 0D);
        result.put("INTELLECTUAL", 0D);
        result.put("PHYSICAL", 0D);

        Map<Long, Double> effectiveScores = resolveEffectiveSignedScores(applications);
        for (ExtraScoreApplication application : safeList(applications)) {
            String scoreType = defaultString(application.getScoreType()).trim().toUpperCase(Locale.ROOT);
            if (application.getId() == null || !result.containsKey(scoreType)) {
                continue;
            }
            result.put(scoreType, result.get(scoreType) + effectiveScores.getOrDefault(application.getId(), 0D));
        }
        return result;
    }

    public Map<Long, Double> resolveEffectiveSignedScores(List<ExtraScoreApplication> applications) {
        return resolveEffectiveScoreResults(applications).entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().effectiveSignedScore(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    public Map<Long, EffectiveScoreResult> resolveEffectiveScoreResults(List<ExtraScoreApplication> applications) {
        Map<Long, Evaluation> evaluations = new LinkedHashMap<>();
        List<ExtraScoreApplication> rows = safeList(applications).stream()
                .filter(application -> application != null && application.getId() != null)
                .toList();
        for (ExtraScoreApplication application : rows) {
            evaluations.put(application.getId(), new Evaluation(application, resolveSignedScore(application)));
        }
        if (rows.isEmpty()) {
            return toResultMap(evaluations);
        }

        RuleContext ruleContext = loadRuleContext(rows);
        applyItemMaxCountRules(rows, evaluations, ruleContext.itemRulesById, ruleContext.itemRulesByNameKey);
        applyGroupBestOnlyRules(rows, evaluations, ruleContext.groupRulesByKey);

        return toResultMap(evaluations);
    }

    private void applyItemMaxCountRules(
            List<ExtraScoreApplication> rows,
            Map<Long, Evaluation> evaluations,
            Map<Long, ItemRule> itemRulesById,
            Map<String, ItemRule> itemRulesByNameKey
    ) {
        Map<String, List<ExtraScoreApplication>> itemGroups = new LinkedHashMap<>();
        for (ExtraScoreApplication application : rows) {
            ItemRule rule = resolveItemRule(application, itemRulesById, itemRulesByNameKey);
            if (rule == null || rule.maxCount() <= 0) {
                continue;
            }
            itemGroups
                    .computeIfAbsent(itemRuleKey(application), key -> new ArrayList<>())
                    .add(application);
        }

        for (List<ExtraScoreApplication> applications : itemGroups.values()) {
            if (applications.size() <= 1) {
                continue;
            }
            ExtraScoreApplication sample = applications.get(0);
            ItemRule rule = resolveItemRule(sample, itemRulesById, itemRulesByNameKey);
            int maxCount = Math.max(1, rule == null ? 1 : rule.maxCount());
            List<ExtraScoreApplication> countedApplications = chooseCountedApplications(applications, maxCount);
            if (countedApplications.size() >= applications.size()) {
                continue;
            }
            String countedLabel = countedApplications.stream()
                    .map(this::applicationLabel)
                    .collect(Collectors.joining("、"));
            Set<Long> countedIds = countedApplications.stream()
                    .map(ExtraScoreApplication::getId)
                    .collect(Collectors.toSet());
            String itemName = defaultString(sample.getItemName()).isBlank() ? "该小类" : "小类“" + sample.getItemName() + "”";
            String ruleText = maxCount == 1 ? "同一小类只计一项最高" : "同一小类只计最高" + maxCount + "项";
            String reason = itemName + "设置为“" + ruleText + "”，本项分值低于已计入项";
            for (ExtraScoreApplication application : applications) {
                if (!countedIds.contains(application.getId())) {
                    exclude(evaluations.get(application.getId()), reason, ruleText, countedApplications.get(0), countedLabel);
                }
            }
        }
    }

    private void applyGroupBestOnlyRules(
            List<ExtraScoreApplication> rows,
            Map<Long, Evaluation> evaluations,
            Map<String, GroupRule> groupRulesByKey
    ) {
        Map<String, List<ExtraScoreApplication>> groupBestOnlyApplications = new LinkedHashMap<>();
        for (ExtraScoreApplication application : rows) {
            GroupRule rule = groupRulesByKey.get(templateGroupKey(application));
            if (rule == null || !rule.bestOnly()) {
                continue;
            }
            Evaluation evaluation = evaluations.get(application.getId());
            if (evaluation == null || !evaluation.counted) {
                continue;
            }
            groupBestOnlyApplications
                    .computeIfAbsent(groupRuleKey(application), key -> new ArrayList<>())
                    .add(application);
        }

        for (List<ExtraScoreApplication> applications : groupBestOnlyApplications.values()) {
            if (applications.size() <= 1) {
                continue;
            }
            ExtraScoreApplication counted = chooseCountedApplications(applications, 1).get(0);
            String groupName = defaultString(counted.getGroupName()).isBlank() ? "该大类" : "大类“" + counted.getGroupName() + "”";
            String ruleText = "同一大类只计一项最高";
            String reason = groupName + "设置为“" + ruleText + "”，本项分值低于已计入项";
            for (ExtraScoreApplication application : applications) {
                if (!application.getId().equals(counted.getId())) {
                    exclude(evaluations.get(application.getId()), reason, ruleText, counted, applicationLabel(counted));
                }
            }
        }
    }

    private RuleContext loadRuleContext(List<ExtraScoreApplication> applications) {
        Set<Long> templateIds = applications.stream()
                .map(ExtraScoreApplication::getTemplateId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        Map<String, GroupRule> groupRulesByKey = new LinkedHashMap<>();
        Map<Long, ItemRule> itemRulesById = new LinkedHashMap<>();
        Map<String, ItemRule> itemRulesByNameKey = new LinkedHashMap<>();
        List<Long> groupIds = new ArrayList<>();
        Map<Long, String> groupNameById = new LinkedHashMap<>();
        for (Long templateId : templateIds) {
            for (ExtraScoreTemplateGroup group : groupRepository.findByTemplateIdOrderBySortOrderAscIdAsc(templateId)) {
                String groupKey = templateGroupKey(templateId, group.getGroupName());
                groupRulesByKey.put(groupKey, new GroupRule(Boolean.TRUE.equals(group.getSameEventBestOnly())));
                if (group.getId() != null) {
                    groupIds.add(group.getId());
                    groupNameById.put(group.getId(), group.getGroupName());
                }
            }
        }
        if (!groupIds.isEmpty()) {
            for (ExtraScoreTemplateItem item : itemRepository.findByGroupIdInOrderBySortOrderAscIdAsc(groupIds)) {
                if (item == null || item.getId() == null) {
                    continue;
                }
                int maxCount = item.getSameEventMaxCount() == null ? 0 : item.getSameEventMaxCount();
                ItemRule rule = new ItemRule(Math.max(maxCount, 0));
                itemRulesById.put(item.getId(), rule);
                itemRulesByNameKey.put(itemRuleNameKey(item, groupNameById.get(item.getGroupId())), rule);
            }
        }
        return new RuleContext(groupRulesByKey, itemRulesById, itemRulesByNameKey);
    }

    private List<ExtraScoreApplication> chooseCountedApplications(List<ExtraScoreApplication> applications, int maxCount) {
        Comparator<ExtraScoreApplication> byScore = Comparator.comparingDouble(this::resolveSignedScore);
        boolean deduct = applications.stream()
                .allMatch(application -> "deduct".equals(normalizeScoreDirection(application.getScoreDirection())));
        Comparator<ExtraScoreApplication> comparator = deduct ? byScore : byScore.reversed();
        return applications.stream()
                .sorted(comparator.thenComparing(ExtraScoreApplication::getId))
                .limit(Math.max(1, maxCount))
                .toList();
    }

    private void exclude(
            Evaluation evaluation,
            String reason,
            String rule,
            ExtraScoreApplication countedApplication,
            String countedLabel
    ) {
        if (evaluation == null || !evaluation.counted) {
            return;
        }
        evaluation.effectiveSignedScore = 0D;
        evaluation.counted = false;
        evaluation.excludeReason = reason;
        evaluation.exclusiveRule = rule;
        evaluation.countedApplicationId = countedApplication == null ? null : countedApplication.getId();
        evaluation.countedApplicationLabel = countedLabel;
    }

    private Map<Long, EffectiveScoreResult> toResultMap(Map<Long, Evaluation> evaluations) {
        Map<Long, EffectiveScoreResult> result = new LinkedHashMap<>();
        for (Map.Entry<Long, Evaluation> entry : evaluations.entrySet()) {
            Evaluation evaluation = entry.getValue();
            result.put(entry.getKey(), new EffectiveScoreResult(
                    evaluation.application.getId(),
                    evaluation.originalSignedScore,
                    evaluation.effectiveSignedScore,
                    evaluation.counted,
                    evaluation.excludeReason,
                    evaluation.exclusiveRule,
                    evaluation.countedApplicationId,
                    evaluation.countedApplicationLabel
            ));
        }
        return result;
    }

    private ItemRule resolveItemRule(
            ExtraScoreApplication application,
            Map<Long, ItemRule> itemRulesById,
            Map<String, ItemRule> itemRulesByNameKey
    ) {
        if (application.getItemId() != null && itemRulesById.containsKey(application.getItemId())) {
            return itemRulesById.get(application.getItemId());
        }
        return itemRulesByNameKey.get(itemRuleNameKey(application));
    }

    private String itemRuleKey(ExtraScoreApplication application) {
        return String.join("|",
                defaultString(application.getStudentNo()).trim(),
                defaultString(application.getScoreType()).trim().toUpperCase(Locale.ROOT),
                resolveAcademicYear(application),
                String.valueOf(application.getTemplateId()),
                normalizeText(application.getGroupName()),
                normalizeScoreDirection(application.getScoreDirection()),
                application.getItemId() == null ? normalizeText(application.getItemName()) : String.valueOf(application.getItemId())
        );
    }

    private String groupRuleKey(ExtraScoreApplication application) {
        return String.join("|",
                defaultString(application.getStudentNo()).trim(),
                defaultString(application.getScoreType()).trim().toUpperCase(Locale.ROOT),
                resolveAcademicYear(application),
                String.valueOf(application.getTemplateId()),
                normalizeText(application.getGroupName()),
                normalizeScoreDirection(application.getScoreDirection())
        );
    }

    private String templateGroupKey(ExtraScoreApplication application) {
        return templateGroupKey(application.getTemplateId(), application.getGroupName());
    }

    private String templateGroupKey(Long templateId, String groupName) {
        return String.valueOf(templateId) + "|" + normalizeText(groupName);
    }

    private String itemRuleNameKey(ExtraScoreTemplateItem item, String groupName) {
        return String.valueOf(item.getTemplateId()) + "|" + normalizeText(groupName) + "|" + normalizeText(item.getItemName());
    }

    private String itemRuleNameKey(ExtraScoreApplication application) {
        return String.valueOf(application.getTemplateId()) + "|" + normalizeText(application.getGroupName()) + "|" + normalizeText(application.getItemName());
    }

    private String applicationLabel(ExtraScoreApplication application) {
        if (application == null) {
            return "";
        }
        String certName = defaultString(application.getCertName()).trim();
        if (!certName.isBlank()) {
            return certName;
        }
        String itemName = defaultString(application.getItemName()).trim();
        if (!itemName.isBlank()) {
            return itemName;
        }
        return "记录#" + application.getId();
    }

    private String resolveAcademicYear(ExtraScoreApplication application) {
        String academicYear = defaultString(application.getAcademicYear()).trim();
        if (!academicYear.isBlank()) {
            return academicYear;
        }
        if (application.getSubmissionStartTime() != null) {
            return String.valueOf(application.getSubmissionStartTime().getYear());
        }
        if (application.getAwardTime() != null) {
            return String.valueOf(application.getAwardTime().getYear());
        }
        return "";
    }

    private String normalizeScoreDirection(String scoreDirection) {
        String normalized = defaultString(scoreDirection).trim().toLowerCase(Locale.ROOT);
        return "deduct".equals(normalized) ? "deduct" : "add";
    }

    private String normalizeText(String value) {
        return defaultString(value).trim().toLowerCase(Locale.ROOT);
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private List<ExtraScoreApplication> safeList(List<ExtraScoreApplication> values) {
        return values == null ? List.of() : values;
    }

    private record RuleContext(
            Map<String, GroupRule> groupRulesByKey,
            Map<Long, ItemRule> itemRulesById,
            Map<String, ItemRule> itemRulesByNameKey
    ) {
    }

    private record GroupRule(boolean bestOnly) {
    }

    private record ItemRule(int maxCount) {
    }

    private static class Evaluation {
        private final ExtraScoreApplication application;
        private final double originalSignedScore;
        private double effectiveSignedScore;
        private boolean counted = true;
        private String excludeReason;
        private String exclusiveRule;
        private Long countedApplicationId;
        private String countedApplicationLabel;

        private Evaluation(ExtraScoreApplication application, double originalSignedScore) {
            this.application = application;
            this.originalSignedScore = originalSignedScore;
            this.effectiveSignedScore = originalSignedScore;
        }
    }
}
