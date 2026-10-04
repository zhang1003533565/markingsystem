package com.markingsystem.controller.teacher;

import com.markingsystem.entity.TeacherPortraitConfig;
import com.markingsystem.entity.TeacherPortraitRule;
import com.markingsystem.service.TeacherPortraitRuleService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/teacher/portrait-rules")
public class TeacherPortraitRuleController {

    private final TeacherPortraitRuleService ruleService;

    public TeacherPortraitRuleController(TeacherPortraitRuleService ruleService) {
        this.ruleService = ruleService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getPortraitRules(
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherIdHeader,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        Long teacherId = resolveTeacherId(teacherIdHeader, userIdHeader, userRole);
        if (teacherId == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 400,
                    "message", "无法确定老师身份"
            ));
        }

        TeacherPortraitConfig config = ruleService.getConfig(teacherId).orElse(null);
        List<TeacherPortraitRule> rules = ruleService.getRules(teacherId);

        // 如果数据库中没有自定义规则，使用默认规则
        if (rules.isEmpty()) {
            rules = ruleService.getDefaultRules();
        }

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);

        Map<String, Object> data = new HashMap<>();
        data.put("enabled", config != null && Boolean.TRUE.equals(config.getEnabled()));
        data.put("competitionKeywordMultiplier", config != null ? config.getCompetitionKeywordMultiplier() : 18D);
        data.put("technicalKeywordMultiplier", config != null ? config.getTechnicalKeywordMultiplier() : 18D);
        data.put("employmentKeywordMultiplier", config != null ? config.getEmploymentKeywordMultiplier() : 18D);
        data.put("serviceKeywordMultiplier", config != null ? config.getServiceKeywordMultiplier() : 18D);

        data.put("rules", rules.stream().map(this::toRuleItemDTO).collect(Collectors.toList()));

        result.put("data", data);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/defaults")
    public ResponseEntity<Map<String, Object>> getDefaultRules(
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherIdHeader,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        List<TeacherPortraitRule> defaults = ruleService.getDefaultRules();

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", Map.of(
                "competitionKeywordMultiplier", 18D,
                "technicalKeywordMultiplier", 18D,
                "employmentKeywordMultiplier", 18D,
                "serviceKeywordMultiplier", 18D,
                "rules", defaults.stream().map(this::toRuleItemDTO).collect(Collectors.toList())
        ));
        return ResponseEntity.ok(result);
    }

    @PutMapping
    public ResponseEntity<Map<String, Object>> savePortraitRules(
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherIdHeader,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        Long teacherId = resolveTeacherId(teacherIdHeader, userIdHeader, userRole);
        if (teacherId == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 400,
                    "message", "无法确定老师身份"
            ));
        }

        TeacherPortraitConfig config = new TeacherPortraitConfig();
        if (request.containsKey("enabled")) {
            config.setEnabled(Boolean.valueOf(request.get("enabled").toString()));
        }
        if (request.containsKey("competitionKeywordMultiplier")) {
            config.setCompetitionKeywordMultiplier(toDouble(request.get("competitionKeywordMultiplier"), 18D));
        }
        if (request.containsKey("technicalKeywordMultiplier")) {
            config.setTechnicalKeywordMultiplier(toDouble(request.get("technicalKeywordMultiplier"), 18D));
        }
        if (request.containsKey("employmentKeywordMultiplier")) {
            config.setEmploymentKeywordMultiplier(toDouble(request.get("employmentKeywordMultiplier"), 18D));
        }
        if (request.containsKey("serviceKeywordMultiplier")) {
            config.setServiceKeywordMultiplier(toDouble(request.get("serviceKeywordMultiplier"), 18D));
        }
        ruleService.saveConfig(teacherId, config);

        List<TeacherPortraitRule> savedRules = new ArrayList<>();
        if (request.containsKey("rules") && request.get("rules") instanceof List<?> rulesList) {
            for (Object item : rulesList) {
                if (item instanceof Map<?, ?> ruleMap) {
                    TeacherPortraitRule rule = toEntity(ruleMap);
                    rule.setTeacherId(teacherId);
                    savedRules.add(rule);
                }
            }
            if (!savedRules.isEmpty()) {
                ruleService.saveRules(teacherId, savedRules);
            }
        }

        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "规则保存成功"
        ));
    }

    @PutMapping("/enable")
    public ResponseEntity<Map<String, Object>> toggleEnabled(
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherIdHeader,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        Long teacherId = resolveTeacherId(teacherIdHeader, userIdHeader, userRole);
        if (teacherId == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 400,
                    "message", "无法确定老师身份"
            ));
        }

        boolean enabled = request.containsKey("enabled") && Boolean.TRUE.equals(request.get("enabled"));
        ruleService.saveConfig(teacherId, enabled);

        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", enabled ? "已启用自定义规则" : "已关闭自定义规则"
        ));
    }

    private Long resolveTeacherId(String teacherIdHeader, String userIdHeader, String userRole) {
        if ("TEACHER".equalsIgnoreCase(userRole)) {
            if (teacherIdHeader != null && !teacherIdHeader.isBlank()) {
                return Long.parseLong(teacherIdHeader);
            }
            if (userIdHeader != null && !userIdHeader.isBlank()) {
                return Long.parseLong(userIdHeader);
            }
        }
        return null;
    }

    private Map<String, Object> toRuleItemDTO(TeacherPortraitRule rule) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("typeKey", rule.getTypeKey());
        dto.put("typeName", rule.getTypeName());
        dto.put("courseWeight", rule.getCourseWeight());
        dto.put("moralWeight", rule.getMoralWeight());
        dto.put("intellectualWeight", rule.getIntellectualWeight());
        dto.put("physicalWeight", rule.getPhysicalWeight());
        dto.put("extraScoreWeight", rule.getExtraScoreWeight());
        dto.put("excellentCourseWeight", rule.getExcellentCourseWeight());
        dto.put("growthWeight", rule.getGrowthWeight());
        dto.put("keywordList", rule.getKeywordList());
        dto.put("keywordBonus", rule.getKeywordBonus());
        dto.put("classCommitteeBonus", rule.getClassCommitteeBonus());
        return dto;
    }

    private TeacherPortraitRule toEntity(Map<?, ?> map) {
        TeacherPortraitRule rule = new TeacherPortraitRule();
        if (map.containsKey("typeKey")) rule.setTypeKey(String.valueOf(map.get("typeKey")));
        if (map.containsKey("typeName")) rule.setTypeName(String.valueOf(map.get("typeName")));
        if (map.containsKey("courseWeight")) rule.setCourseWeight(toDouble(map.get("courseWeight"), 0D));
        if (map.containsKey("moralWeight")) rule.setMoralWeight(toDouble(map.get("moralWeight"), 0D));
        if (map.containsKey("intellectualWeight")) rule.setIntellectualWeight(toDouble(map.get("intellectualWeight"), 0D));
        if (map.containsKey("physicalWeight")) rule.setPhysicalWeight(toDouble(map.get("physicalWeight"), 0D));
        if (map.containsKey("extraScoreWeight")) rule.setExtraScoreWeight(toDouble(map.get("extraScoreWeight"), 0D));
        if (map.containsKey("excellentCourseWeight")) rule.setExcellentCourseWeight(toDouble(map.get("excellentCourseWeight"), 0D));
        if (map.containsKey("growthWeight")) rule.setGrowthWeight(toDouble(map.get("growthWeight"), 0D));
        if (map.containsKey("keywordList")) {
            Object kwList = map.get("keywordList");
            if (kwList instanceof List<?> list) {
                rule.setKeywordList(list.stream().map(Object::toString).toList());
            }
        }
        if (map.containsKey("keywordBonus")) rule.setKeywordBonus(toDouble(map.get("keywordBonus"), 0D));
        if (map.containsKey("classCommitteeBonus")) rule.setClassCommitteeBonus(toDouble(map.get("classCommitteeBonus"), 18D));
        return rule;
    }

    private Double toDouble(Object value, Double defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Number) return ((Number) value).doubleValue();
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
