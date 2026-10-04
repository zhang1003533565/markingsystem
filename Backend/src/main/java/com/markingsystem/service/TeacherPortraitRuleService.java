package com.markingsystem.service;

import com.markingsystem.entity.TeacherPortraitConfig;
import com.markingsystem.entity.TeacherPortraitRule;
import com.markingsystem.repository.TeacherPortraitConfigRepository;
import com.markingsystem.repository.TeacherPortraitRuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class TeacherPortraitRuleService {

    private final TeacherPortraitRuleRepository ruleRepository;
    private final TeacherPortraitConfigRepository configRepository;

    public TeacherPortraitRuleService(
            TeacherPortraitRuleRepository ruleRepository,
            TeacherPortraitConfigRepository configRepository) {
        this.ruleRepository = ruleRepository;
        this.configRepository = configRepository;
    }

    public Optional<TeacherPortraitConfig> getConfig(Long teacherId) {
        return configRepository.findByTeacherId(teacherId);
    }

    public boolean isEnabled(Long teacherId) {
        return configRepository.findByTeacherId(teacherId)
                .map(TeacherPortraitConfig::getEnabled)
                .orElse(false);
    }

    @Transactional
    public TeacherPortraitConfig saveConfig(Long teacherId, boolean enabled) {
        TeacherPortraitConfig config = configRepository.findByTeacherId(teacherId)
                .orElseGet(() -> {
                    TeacherPortraitConfig newConfig = new TeacherPortraitConfig();
                    newConfig.setTeacherId(teacherId);
                    return newConfig;
                });
        config.setEnabled(enabled);
        return configRepository.save(config);
    }

    @Transactional
    public TeacherPortraitConfig saveConfig(Long teacherId, TeacherPortraitConfig configData) {
        TeacherPortraitConfig config = configRepository.findByTeacherId(teacherId)
                .orElseGet(() -> {
                    TeacherPortraitConfig newConfig = new TeacherPortraitConfig();
                    newConfig.setTeacherId(teacherId);
                    return newConfig;
                });

        if (configData.getCompetitionKeywordMultiplier() != null) {
            config.setCompetitionKeywordMultiplier(configData.getCompetitionKeywordMultiplier());
        }
        if (configData.getTechnicalKeywordMultiplier() != null) {
            config.setTechnicalKeywordMultiplier(configData.getTechnicalKeywordMultiplier());
        }
        if (configData.getEmploymentKeywordMultiplier() != null) {
            config.setEmploymentKeywordMultiplier(configData.getEmploymentKeywordMultiplier());
        }
        if (configData.getServiceKeywordMultiplier() != null) {
            config.setServiceKeywordMultiplier(configData.getServiceKeywordMultiplier());
        }
        if (configData.getEnabled() != null) {
            config.setEnabled(configData.getEnabled());
        }

        return configRepository.save(config);
    }

    public List<TeacherPortraitRule> getRules(Long teacherId) {
        List<TeacherPortraitRule> rules = ruleRepository.findByTeacherId(teacherId);
        // 如果数据库中没有自定义规则，使用默认规则
        if (rules.isEmpty()) {
            return getDefaultRules();
        }
        return rules;
    }

    @Transactional
    public List<TeacherPortraitRule> saveRules(Long teacherId, List<TeacherPortraitRule> rules) {
        ruleRepository.deleteByTeacherId(teacherId);
        for (TeacherPortraitRule rule : rules) {
            rule.setTeacherId(teacherId);
        }
        return ruleRepository.saveAll(rules);
    }

    public List<TeacherPortraitRule> getDefaultRules() {
        List<TeacherPortraitRule> defaults = new ArrayList<>();

        defaults.add(createDefaultRule("all_round", "全面优秀型",
                List.of("moralWeight:20", "intellectualWeight:25", "physicalWeight:15",
                        "extraScoreWeight:20", "excellentCourseWeight:20")));

        defaults.add(createDefaultRule("academic", "学术升学型",
                List.of("moralWeight:5", "intellectualWeight:40", "physicalWeight:5",
                        "extraScoreWeight:10", "excellentCourseWeight:40")));

        defaults.add(createDefaultRule("competition", "复合竞赛型",
                List.of("moralWeight:5", "intellectualWeight:15", "physicalWeight:5",
                        "extraScoreWeight:45", "excellentCourseWeight:30")));

        defaults.add(createDefaultRule("technical", "技术专长型",
                List.of("moralWeight:5", "intellectualWeight:35", "physicalWeight:5",
                        "extraScoreWeight:10", "excellentCourseWeight:45")));

        defaults.add(createDefaultRule("employment", "就业实践型",
                List.of("moralWeight:10", "intellectualWeight:20", "physicalWeight:5",
                        "extraScoreWeight:45", "excellentCourseWeight:20")));

        defaults.add(createDefaultRule("civil_service", "稳定考公型",
                List.of("moralWeight:60", "intellectualWeight:10", "physicalWeight:20",
                        "extraScoreWeight:5", "excellentCourseWeight:5")));

        defaults.add(createDefaultRule("potential", "发展潜力型",
                List.of("moralWeight:10", "intellectualWeight:30", "physicalWeight:10",
                        "extraScoreWeight:10", "excellentCourseWeight:40")));

        return defaults;
    }

    private TeacherPortraitRule createDefaultRule(String typeKey, String typeName, List<String> weights) {
        TeacherPortraitRule rule = new TeacherPortraitRule();
        rule.setTypeKey(typeKey);
        rule.setTypeName(typeName);

        for (String w : weights) {
            String[] parts = w.split(":");
            if (parts.length == 2) {
                double value = Double.parseDouble(parts[1]);
                switch (parts[0]) {
                    case "courseWeight" -> rule.setCourseWeight(value);
                    case "moralWeight" -> rule.setMoralWeight(value);
                    case "intellectualWeight" -> rule.setIntellectualWeight(value);
                    case "physicalWeight" -> rule.setPhysicalWeight(value);
                    case "extraScoreWeight" -> rule.setExtraScoreWeight(value);
                    case "excellentCourseWeight" -> rule.setExcellentCourseWeight(value);
                    case "growthWeight" -> rule.setGrowthWeight(value);
                    case "classCommitteeBonus" -> rule.setClassCommitteeBonus(value);
                }
            }
        }

        return rule;
    }

    public static class PortraitRuleDTO {
        private Long teacherId;
        private Boolean enabled;
        private Double competitionKeywordMultiplier;
        private Double technicalKeywordMultiplier;
        private Double employmentKeywordMultiplier;
        private Double serviceKeywordMultiplier;
        private List<RuleItemDTO> rules;

        public Long getTeacherId() { return teacherId; }
        public void setTeacherId(Long teacherId) { this.teacherId = teacherId; }
        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }
        public Double getCompetitionKeywordMultiplier() { return competitionKeywordMultiplier; }
        public void setCompetitionKeywordMultiplier(Double v) { this.competitionKeywordMultiplier = v; }
        public Double getTechnicalKeywordMultiplier() { return technicalKeywordMultiplier; }
        public void setTechnicalKeywordMultiplier(Double v) { this.technicalKeywordMultiplier = v; }
        public Double getEmploymentKeywordMultiplier() { return employmentKeywordMultiplier; }
        public void setEmploymentKeywordMultiplier(Double v) { this.employmentKeywordMultiplier = v; }
        public Double getServiceKeywordMultiplier() { return serviceKeywordMultiplier; }
        public void setServiceKeywordMultiplier(Double v) { this.serviceKeywordMultiplier = v; }
        public List<RuleItemDTO> getRules() { return rules; }
        public void setRules(List<RuleItemDTO> rules) { this.rules = rules; }
    }

    public static class RuleItemDTO {
        private String typeKey;
        private String typeName;
        private Double courseWeight;
        private Double moralWeight;
        private Double intellectualWeight;
        private Double physicalWeight;
        private Double extraScoreWeight;
        private Double excellentCourseWeight;
        private Double growthWeight;
        private List<String> keywordList;
        private Double keywordBonus;
        private Double classCommitteeBonus;

        public String getTypeKey() { return typeKey; }
        public void setTypeKey(String typeKey) { this.typeKey = typeKey; }
        public String getTypeName() { return typeName; }
        public void setTypeName(String typeName) { this.typeName = typeName; }
        public Double getCourseWeight() { return courseWeight; }
        public void setCourseWeight(Double v) { this.courseWeight = v; }
        public Double getMoralWeight() { return moralWeight; }
        public void setMoralWeight(Double v) { this.moralWeight = v; }
        public Double getIntellectualWeight() { return intellectualWeight; }
        public void setIntellectualWeight(Double v) { this.intellectualWeight = v; }
        public Double getPhysicalWeight() { return physicalWeight; }
        public void setPhysicalWeight(Double v) { this.physicalWeight = v; }
        public Double getExtraScoreWeight() { return extraScoreWeight; }
        public void setExtraScoreWeight(Double v) { this.extraScoreWeight = v; }
        public Double getExcellentCourseWeight() { return excellentCourseWeight; }
        public void setExcellentCourseWeight(Double v) { this.excellentCourseWeight = v; }
        public Double getGrowthWeight() { return growthWeight; }
        public void setGrowthWeight(Double v) { this.growthWeight = v; }
        public List<String> getKeywordList() { return keywordList; }
        public void setKeywordList(List<String> v) { this.keywordList = v; }
        public Double getKeywordBonus() { return keywordBonus; }
        public void setKeywordBonus(Double v) { this.keywordBonus = v; }
        public Double getClassCommitteeBonus() { return classCommitteeBonus; }
        public void setClassCommitteeBonus(Double v) { this.classCommitteeBonus = v; }
    }
}
