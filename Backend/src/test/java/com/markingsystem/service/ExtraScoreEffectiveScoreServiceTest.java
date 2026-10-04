package com.markingsystem.service;

import com.markingsystem.entity.ExtraScoreApplication;
import com.markingsystem.entity.ExtraScoreTemplateGroup;
import com.markingsystem.entity.ExtraScoreTemplateItem;
import com.markingsystem.repository.ExtraScoreTemplateGroupRepository;
import com.markingsystem.repository.ExtraScoreTemplateItemRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtraScoreEffectiveScoreServiceTest {

    @Test
    void keepsOnlyHighestScoreWhenItemAllowsOneCountedApplication() {
        ExtraScoreTemplateGroup group = group(11L, 1L, "竞赛获奖", false);
        ExtraScoreTemplateItem item = item(21L, 1L, 11L, "程序设计竞赛", 1);
        ExtraScoreEffectiveScoreService service = service(List.of(group), List.of(item));

        ExtraScoreApplication lower = application(101L, 1L, "竞赛获奖", 21L, "程序设计竞赛", 2D);
        ExtraScoreApplication higher = application(102L, 1L, "竞赛获奖", 21L, "程序设计竞赛", 4D);

        Map<Long, ExtraScoreEffectiveScoreService.EffectiveScoreResult> result =
                service.resolveEffectiveScoreResults(List.of(lower, higher));

        assertFalse(result.get(101L).counted());
        assertEquals(0D, result.get(101L).effectiveSignedScore());
        assertTrue(result.get(101L).excludeReason().contains("小类"));
        assertTrue(result.get(101L).exclusiveRule().contains("同一小类"));
        assertTrue(result.get(102L).counted());
        assertEquals(4D, result.get(102L).effectiveSignedScore());
    }

    @Test
    void keepsOnlyHighestScoreWhenGroupBestOnlyIsEnabled() {
        ExtraScoreTemplateGroup group = group(11L, 1L, "荣誉奖励", true);
        ExtraScoreTemplateItem firstItem = item(21L, 1L, 11L, "优秀学生", null);
        ExtraScoreTemplateItem secondItem = item(22L, 1L, 11L, "优秀干部", null);
        ExtraScoreEffectiveScoreService service = service(List.of(group), List.of(firstItem, secondItem));

        ExtraScoreApplication lower = application(101L, 1L, "荣誉奖励", 21L, "优秀学生", 1D);
        ExtraScoreApplication higher = application(102L, 1L, "荣誉奖励", 22L, "优秀干部", 3D);

        Map<Long, ExtraScoreEffectiveScoreService.EffectiveScoreResult> result =
                service.resolveEffectiveScoreResults(List.of(lower, higher));

        assertFalse(result.get(101L).counted());
        assertEquals(0D, result.get(101L).effectiveSignedScore());
        assertTrue(result.get(101L).excludeReason().contains("大类"));
        assertTrue(result.get(101L).exclusiveRule().contains("同一大类"));
        assertTrue(result.get(102L).counted());
        assertEquals(3D, result.get(102L).effectiveSignedScore());
    }

    private ExtraScoreEffectiveScoreService service(
            List<ExtraScoreTemplateGroup> groups,
            List<ExtraScoreTemplateItem> items
    ) {
        ExtraScoreTemplateGroupRepository groupRepository = proxy(
                ExtraScoreTemplateGroupRepository.class,
                "findByTemplateIdOrderBySortOrderAscIdAsc",
                groups
        );
        ExtraScoreTemplateItemRepository itemRepository = proxy(
                ExtraScoreTemplateItemRepository.class,
                "findByGroupIdInOrderBySortOrderAscIdAsc",
                items
        );
        return new ExtraScoreEffectiveScoreService(groupRepository, itemRepository);
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(Class<T> type, String methodName, Object value) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                (proxy, method, args) -> {
                    if (methodName.equals(method.getName())) {
                        return value;
                    }
                    if ("toString".equals(method.getName())) {
                        return type.getSimpleName() + "TestProxy";
                    }
                    if ("hashCode".equals(method.getName())) {
                        return System.identityHashCode(proxy);
                    }
                    if ("equals".equals(method.getName())) {
                        return proxy == args[0];
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private ExtraScoreTemplateGroup group(Long id, Long templateId, String groupName, boolean bestOnly) {
        ExtraScoreTemplateGroup group = new ExtraScoreTemplateGroup();
        group.setId(id);
        group.setTemplateId(templateId);
        group.setGroupName(groupName);
        group.setSameEventBestOnly(bestOnly);
        return group;
    }

    private ExtraScoreTemplateItem item(Long id, Long templateId, Long groupId, String itemName, Integer maxCount) {
        ExtraScoreTemplateItem item = new ExtraScoreTemplateItem();
        item.setId(id);
        item.setTemplateId(templateId);
        item.setGroupId(groupId);
        item.setItemName(itemName);
        item.setSameEventMaxCount(maxCount);
        return item;
    }

    private ExtraScoreApplication application(
            Long id,
            Long templateId,
            String groupName,
            Long itemId,
            String itemName,
            Double score
    ) {
        ExtraScoreApplication application = new ExtraScoreApplication();
        application.setId(id);
        application.setStudentNo("20230001");
        application.setAcademicYear("2024-2025");
        application.setScoreType("MORAL");
        application.setTemplateId(templateId);
        application.setGroupName(groupName);
        application.setItemId(itemId);
        application.setItemName(itemName);
        application.setScoreDirection("add");
        application.setExpectedScore(score);
        application.setCertName(itemName + score);
        return application;
    }
}
