package com.markingsystem.service;

import com.markingsystem.dto.ExtraScoreTemplateRequest;
import com.markingsystem.entity.ExtraScoreTemplate;
import com.markingsystem.entity.ExtraScoreTemplateGroup;
import com.markingsystem.entity.ExtraScoreTemplateItem;
import com.markingsystem.entity.ExtraScoreTemplateItemAuthorRule;
import com.markingsystem.entity.ExtraScoreTemplateItemLevelRule;
import com.markingsystem.repository.ClassInfoRepository;
import com.markingsystem.repository.ExtraScoreTemplateGroupRepository;
import com.markingsystem.repository.ExtraScoreTemplateItemAuthorRuleRepository;
import com.markingsystem.repository.ExtraScoreTemplateItemLevelRuleRepository;
import com.markingsystem.repository.ExtraScoreTemplateItemRepository;
import com.markingsystem.repository.ExtraScoreTemplateRepository;
import com.markingsystem.repository.StudentRepository;
import com.markingsystem.repository.TeacherExtraScoreTemplateRepository;
import com.markingsystem.repository.UserRepository;
import com.markingsystem.util.RequestUserContext;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExtraScoreTemplateServiceTest {

    @Test
    void templateDetailKeepsLegacyItemsThatAreNotAttachedToGroups() {
        ExtraScoreTemplate template = template(4L);
        ExtraScoreTemplateItem legacyItem = item(11L, 4L, null, "测试小类");
        ExtraScoreTemplateItemLevelRule levelRule = levelRule(21L, 11L, "测试级别", 0.5D);
        ExtraScoreTemplateItemAuthorRule authorRule = authorRule(31L, 21L, "第一作者", 0.5D);

        ExtraScoreTemplateService service = service(
                template,
                List.of(),
                List.of(legacyItem),
                List.of(levelRule),
                List.of(authorRule)
        );

        Map<String, Object> detail = service.getTemplateDetail(4L);

        List<Map<String, Object>> groups = list(detail.get("groups"));
        assertEquals(1, groups.size());
        assertEquals("未分组", groups.get(0).get("groupName"));

        List<Map<String, Object>> items = list(groups.get(0).get("items"));
        assertEquals(1, items.size());
        assertEquals("测试小类", items.get(0).get("itemName"));

        List<Map<String, Object>> levelRules = list(items.get(0).get("levelRules"));
        assertEquals(1, levelRules.size());
        assertEquals("测试级别", levelRules.get(0).get("levelName"));

        List<Map<String, Object>> authorRules = list(levelRules.get(0).get("authorRules"));
        assertEquals(1, authorRules.size());
        assertEquals("第一作者", authorRules.get(0).get("authorLabel"));
    }

    @Test
    void saveTemplateStructureKeepsAuthorRuleItemIdForExistingDatabaseConstraint() {
        List<ExtraScoreTemplateGroup> savedGroups = new ArrayList<>();
        List<ExtraScoreTemplateItem> savedItems = new ArrayList<>();
        List<ExtraScoreTemplateItemLevelRule> savedLevelRules = new ArrayList<>();
        List<ExtraScoreTemplateItemAuthorRule> savedAuthorRules = new ArrayList<>();
        ExtraScoreTemplate[] savedTemplate = new ExtraScoreTemplate[1];

        ExtraScoreTemplateService service = savingService(
                savedTemplate,
                savedGroups,
                savedItems,
                savedLevelRules,
                savedAuthorRules
        );

        service.createTemplate(templateRequest(), new RequestUserContext(7L, "TEACHER", null, null, null));

        assertEquals(1, savedAuthorRules.size());
        assertEquals(savedItems.get(0).getId(), savedAuthorRules.get(0).getItemId());
    }

    private ExtraScoreTemplateService service(
            ExtraScoreTemplate template,
            List<ExtraScoreTemplateGroup> groups,
            List<ExtraScoreTemplateItem> items,
            List<ExtraScoreTemplateItemLevelRule> levelRules,
            List<ExtraScoreTemplateItemAuthorRule> authorRules
    ) {
        ExtraScoreTemplateRepository templateRepository = proxy(
                ExtraScoreTemplateRepository.class,
                Map.of("findById", Optional.of(template))
        );
        ExtraScoreTemplateGroupRepository groupRepository = proxy(
                ExtraScoreTemplateGroupRepository.class,
                Map.of("findByTemplateIdOrderBySortOrderAscIdAsc", groups)
        );
        ExtraScoreTemplateItemRepository itemRepository = proxy(
                ExtraScoreTemplateItemRepository.class,
                Map.of(
                        "findByTemplateIdOrderBySortOrderAscIdAsc", items,
                        "findByGroupIdInOrderBySortOrderAscIdAsc", items
                )
        );
        ExtraScoreTemplateItemLevelRuleRepository levelRuleRepository = proxy(
                ExtraScoreTemplateItemLevelRuleRepository.class,
                Map.of("findByItemIdInOrderBySortOrderAscIdAsc", levelRules)
        );
        ExtraScoreTemplateItemAuthorRuleRepository authorRuleRepository = proxy(
                ExtraScoreTemplateItemAuthorRuleRepository.class,
                Map.of("findByLevelRuleIdInOrderBySortOrderAscIdAsc", authorRules)
        );
        return new ExtraScoreTemplateService(
                templateRepository,
                groupRepository,
                itemRepository,
                levelRuleRepository,
                authorRuleRepository,
                proxy(TeacherExtraScoreTemplateRepository.class, Map.of()),
                proxy(StudentRepository.class, Map.of()),
                proxy(ClassInfoRepository.class, Map.of()),
                proxy(UserRepository.class, Map.of("findById", Optional.empty()))
        );
    }

    private ExtraScoreTemplateService savingService(
            ExtraScoreTemplate[] savedTemplate,
            List<ExtraScoreTemplateGroup> savedGroups,
            List<ExtraScoreTemplateItem> savedItems,
            List<ExtraScoreTemplateItemLevelRule> savedLevelRules,
            List<ExtraScoreTemplateItemAuthorRule> savedAuthorRules
    ) {
        ExtraScoreTemplateRepository templateRepository = proxy(
                ExtraScoreTemplateRepository.class,
                Map.of(
                        "save", (MethodHandler) args -> {
                            ExtraScoreTemplate template = (ExtraScoreTemplate) args[0];
                            template.setId(4L);
                            savedTemplate[0] = template;
                            return template;
                        },
                        "findById", (MethodHandler) args -> Optional.ofNullable(savedTemplate[0])
                )
        );
        ExtraScoreTemplateGroupRepository groupRepository = proxy(
                ExtraScoreTemplateGroupRepository.class,
                Map.of(
                        "findByTemplateIdOrderBySortOrderAscIdAsc", (MethodHandler) args -> savedGroups,
                        "deleteByTemplateId", (MethodHandler) args -> null,
                        "save", (MethodHandler) args -> {
                            ExtraScoreTemplateGroup group = (ExtraScoreTemplateGroup) args[0];
                            group.setId(14L);
                            savedGroups.add(group);
                            return group;
                        }
                )
        );
        ExtraScoreTemplateItemRepository itemRepository = proxy(
                ExtraScoreTemplateItemRepository.class,
                Map.of(
                        "findByTemplateIdOrderBySortOrderAscIdAsc", (MethodHandler) args -> savedItems,
                        "deleteByTemplateId", (MethodHandler) args -> null,
                        "save", (MethodHandler) args -> {
                            ExtraScoreTemplateItem item = (ExtraScoreTemplateItem) args[0];
                            item.setId(24L);
                            savedItems.add(item);
                            return item;
                        }
                )
        );
        ExtraScoreTemplateItemLevelRuleRepository levelRuleRepository = proxy(
                ExtraScoreTemplateItemLevelRuleRepository.class,
                Map.of(
                        "findByItemIdInOrderBySortOrderAscIdAsc", (MethodHandler) args -> savedLevelRules,
                        "deleteByItemIdIn", (MethodHandler) args -> null,
                        "save", (MethodHandler) args -> {
                            ExtraScoreTemplateItemLevelRule rule = (ExtraScoreTemplateItemLevelRule) args[0];
                            rule.setId(34L);
                            savedLevelRules.add(rule);
                            return rule;
                        }
                )
        );
        ExtraScoreTemplateItemAuthorRuleRepository authorRuleRepository = proxy(
                ExtraScoreTemplateItemAuthorRuleRepository.class,
                Map.of(
                        "findByLevelRuleIdInOrderBySortOrderAscIdAsc", (MethodHandler) args -> savedAuthorRules,
                        "deleteByItemIdIn", (MethodHandler) args -> null,
                        "deleteByLevelRuleIdIn", (MethodHandler) args -> null,
                        "save", (MethodHandler) args -> {
                            ExtraScoreTemplateItemAuthorRule rule = (ExtraScoreTemplateItemAuthorRule) args[0];
                            if (rule.getItemId() == null) {
                                throw new AssertionError("item_id must be stored with author rules");
                            }
                            rule.setId(44L);
                            savedAuthorRules.add(rule);
                            return rule;
                        }
                )
        );

        return new ExtraScoreTemplateService(
                templateRepository,
                groupRepository,
                itemRepository,
                levelRuleRepository,
                authorRuleRepository,
                proxy(TeacherExtraScoreTemplateRepository.class, Map.of()),
                proxy(StudentRepository.class, Map.of()),
                proxy(ClassInfoRepository.class, Map.of()),
                proxy(UserRepository.class, Map.of("findById", Optional.empty()))
        );
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(Class<T> type, Map<String, Object> responses) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                (proxy, method, args) -> {
                    if (responses.containsKey(method.getName())) {
                        Object response = responses.get(method.getName());
                        if (response instanceof MethodHandler handler) {
                            return handler.invoke(args == null ? new Object[0] : args);
                        }
                        return response;
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

    private interface MethodHandler {
        Object invoke(Object[] args);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> list(Object value) {
        return (List<Map<String, Object>>) value;
    }

    private ExtraScoreTemplate template(Long id) {
        ExtraScoreTemplate template = new ExtraScoreTemplate();
        template.setId(id);
        template.setName("德育测试模板");
        template.setScoreType("MORAL");
        template.setStatus("active");
        template.setMaxScore(2D);
        return template;
    }

    private ExtraScoreTemplateItem item(Long id, Long templateId, Long groupId, String name) {
        ExtraScoreTemplateItem item = new ExtraScoreTemplateItem();
        item.setId(id);
        item.setTemplateId(templateId);
        item.setGroupId(groupId);
        item.setItemName(name);
        item.setScoreDirection("add");
        item.setStatus("active");
        return item;
    }

    private ExtraScoreTemplateItemLevelRule levelRule(Long id, Long itemId, String name, Double score) {
        ExtraScoreTemplateItemLevelRule rule = new ExtraScoreTemplateItemLevelRule();
        rule.setId(id);
        rule.setItemId(itemId);
        rule.setLevelName(name);
        rule.setScore(score);
        return rule;
    }

    private ExtraScoreTemplateItemAuthorRule authorRule(Long id, Long levelRuleId, String label, Double fixedScore) {
        ExtraScoreTemplateItemAuthorRule rule = new ExtraScoreTemplateItemAuthorRule();
        rule.setId(id);
        rule.setLevelRuleId(levelRuleId);
        rule.setAuthorLabel(label);
        rule.setMode("fixed");
        rule.setFixedScore(fixedScore);
        return rule;
    }

    private ExtraScoreTemplateRequest templateRequest() {
        ExtraScoreTemplateRequest.AuthorRuleRequest authorRule = new ExtraScoreTemplateRequest.AuthorRuleRequest();
        authorRule.setAuthorLabel("第一作者");
        authorRule.setMode("ratio");
        authorRule.setRatio(100D);

        ExtraScoreTemplateRequest.LevelRuleRequest levelRule = new ExtraScoreTemplateRequest.LevelRuleRequest();
        levelRule.setLevelName("测试级别");
        levelRule.setScore(1D);
        levelRule.setAuthorRules(List.of(authorRule));

        ExtraScoreTemplateRequest.ItemRequest item = new ExtraScoreTemplateRequest.ItemRequest();
        item.setItemName("测试小类");
        item.setScoreDirection("add");
        item.setStatus("active");
        item.setLevelRules(List.of(levelRule));

        ExtraScoreTemplateRequest.GroupRequest group = new ExtraScoreTemplateRequest.GroupRequest();
        group.setGroupName("测试大类");
        group.setItems(List.of(item));

        ExtraScoreTemplateRequest request = new ExtraScoreTemplateRequest();
        request.setName("测试模板");
        request.setScoreType("MORAL");
        request.setStatus("active");
        request.setGroups(List.of(group));
        return request;
    }
}
