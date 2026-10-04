package com.markingsystem.service;

import com.markingsystem.dto.CompositePhysicalFullScoreRuleRequest;
import com.markingsystem.entity.ClassInfo;
import com.markingsystem.entity.CompositePhysicalFullScoreRule;
import com.markingsystem.repository.ClassInfoRepository;
import com.markingsystem.repository.CompositePhysicalFullScoreRuleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompositePhysicalFullScoreRuleServiceTest {

    @Mock
    private CompositePhysicalFullScoreRuleRepository ruleRepository;

    @Mock
    private ClassInfoRepository classInfoRepository;

    @InjectMocks
    private CompositePhysicalFullScoreRuleService service;

    @Test
    void listsRulesByAcademicYearAndClass() {
        CompositePhysicalFullScoreRule rule = new CompositePhysicalFullScoreRule();
        rule.setAcademicYear("2024-2025");
        rule.setClassName("数据241");
        rule.setFullScoreEnabled(true);

        when(ruleRepository.findByAcademicYearOrderByClassNameAsc("2024-2025"))
                .thenReturn(List.of(rule));
        when(classInfoRepository.findAll()).thenReturn(List.of(
                buildClass("数据241", "2024"),
                buildClass("数据242", "2024"),
                buildClass("数据231", "2023")
        ));

        Map<String, Object> result = service.list("2024-2025", null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");

        assertEquals(3, items.size());
        assertEquals("数据241", items.get(0).get("className"));
        assertTrue(Boolean.TRUE.equals(items.get(0).get("fullScoreEnabled")));
        assertEquals("数据242", items.get(1).get("className"));
        assertFalse(Boolean.TRUE.equals(items.get(1).get("fullScoreEnabled")));
    }

    @Test
    void resolvesOnlyTheConfiguredClassAsFullScore() {
        Map<String, Boolean> rules = Map.of("数据241", true, "数据242", false);

        assertTrue(service.isFullScoreEnabled(rules, "数据241"));
        assertFalse(service.isFullScoreEnabled(rules, "数据242"));
        assertFalse(service.isFullScoreEnabled(rules, "数据243"));
    }

    @Test
    void savesRuleForTheSelectedClass() {
        CompositePhysicalFullScoreRuleRequest.Item requestItem =
                new CompositePhysicalFullScoreRuleRequest.Item();
        requestItem.setClassName("数据242");
        requestItem.setFullScoreEnabled(true);

        when(ruleRepository.findByAcademicYearOrderByClassNameAsc("2024-2025"))
                .thenReturn(List.of());
        when(classInfoRepository.existsByName("数据242")).thenReturn(true);
        when(classInfoRepository.findAll()).thenReturn(List.of(buildClass("数据242", "2024")));

        service.save("2024-2025", List.of(requestItem), 7L, null);

        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<Iterable<CompositePhysicalFullScoreRule>> captor =
                ArgumentCaptor.forClass(Iterable.class);
        verify(ruleRepository).saveAll(captor.capture());
        CompositePhysicalFullScoreRule savedRule = StreamSupport
                .stream(captor.getValue().spliterator(), false)
                .findFirst()
                .orElseThrow();
        assertEquals("2024-2025", savedRule.getAcademicYear());
        assertEquals("数据242", savedRule.getClassName());
        assertTrue(Boolean.TRUE.equals(savedRule.getFullScoreEnabled()));
        assertEquals(7L, savedRule.getUpdatedByUserId());
    }

    private ClassInfo buildClass(String name, String grade) {
        ClassInfo classInfo = new ClassInfo();
        classInfo.setName(name);
        classInfo.setGrade(grade);
        classInfo.setStatus("active");
        return classInfo;
    }
}
