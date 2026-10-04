package com.markingsystem.service;

import com.markingsystem.dto.ExtraScoreTemplateRequest;
import com.markingsystem.entity.ClassInfo;
import com.markingsystem.entity.ExtraScoreTemplate;
import com.markingsystem.entity.ExtraScoreTemplateGroup;
import com.markingsystem.entity.ExtraScoreTemplateItem;
import com.markingsystem.entity.ExtraScoreTemplateItemAuthorRule;
import com.markingsystem.entity.ExtraScoreTemplateItemLevelRule;
import com.markingsystem.entity.Student;
import com.markingsystem.entity.TeacherExtraScoreTemplate;
import com.markingsystem.entity.User;
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
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class ExtraScoreTemplateService {

    private static final List<String> SCORE_TYPES = List.of("MORAL", "INTELLECTUAL", "PHYSICAL");
    private static final String TEMPLATE_INFO_SHEET_NAME = "模板信息";
    private static final String TEMPLATE_RULE_SHEET_NAME = "分类规则";
    private static final Long UNGROUPED_TEMPLATE_GROUP_ID = Long.MIN_VALUE;
    private static final String UNGROUPED_TEMPLATE_GROUP_NAME = "未分组";
    private static final String[] TEMPLATE_INFO_HEADERS = {"字段", "值", "说明"};
    private static final String[] TEMPLATE_RULE_HEADERS = {
            "大类",
            "大类最高分",
            "同赛事取最高",
            "小类",
            "小类说明",
            "计分方向",
            "小类最多作者数",
            "同赛事最多计入次数",
            "小类状态",
            "级别",
            "级别分值",
            "作者规则",
            "规则类型",
            "比例(%)",
            "固定分"
    };

    private final ExtraScoreTemplateRepository templateRepository;
    private final ExtraScoreTemplateGroupRepository groupRepository;
    private final ExtraScoreTemplateItemRepository itemRepository;
    private final ExtraScoreTemplateItemLevelRuleRepository levelRuleRepository;
    private final ExtraScoreTemplateItemAuthorRuleRepository authorRuleRepository;
    private final TeacherExtraScoreTemplateRepository selectionRepository;
    private final StudentRepository studentRepository;
    private final ClassInfoRepository classInfoRepository;
    private final UserRepository userRepository;

    public ExtraScoreTemplateService(
            ExtraScoreTemplateRepository templateRepository,
            ExtraScoreTemplateGroupRepository groupRepository,
            ExtraScoreTemplateItemRepository itemRepository,
            ExtraScoreTemplateItemLevelRuleRepository levelRuleRepository,
            ExtraScoreTemplateItemAuthorRuleRepository authorRuleRepository,
            TeacherExtraScoreTemplateRepository selectionRepository,
            StudentRepository studentRepository,
            ClassInfoRepository classInfoRepository,
            UserRepository userRepository
    ) {
        this.templateRepository = templateRepository;
        this.groupRepository = groupRepository;
        this.itemRepository = itemRepository;
        this.levelRuleRepository = levelRuleRepository;
        this.authorRuleRepository = authorRuleRepository;
        this.selectionRepository = selectionRepository;
        this.studentRepository = studentRepository;
        this.classInfoRepository = classInfoRepository;
        this.userRepository = userRepository;
    }

    public List<Map<String, Object>> listTemplates() {
        return templateRepository.findAllByOrderByScoreTypeAscIdAsc().stream()
                .map(this::buildTemplateSummaryMap)
                .toList();
    }

    public Map<String, Object> getTemplateDetail(Long id) {
        return buildTemplateMap(id);
    }

    @Transactional
    public Map<String, Object> createTemplate(ExtraScoreTemplateRequest request, RequestUserContext context) {
        ExtraScoreTemplate template = new ExtraScoreTemplate();
        applyTemplateBase(template, request, context);
        ExtraScoreTemplate savedTemplate = templateRepository.save(template);
        saveTemplateStructure(savedTemplate.getId(), request.getGroups());
        return buildTemplateMap(savedTemplate.getId());
    }

    @Transactional
    public Map<String, Object> updateTemplate(Long id, ExtraScoreTemplateRequest request, RequestUserContext context) {
        ExtraScoreTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("模板不存在"));
        validateTemplatePermission(template, context);
        applyTemplateBase(template, request, context);
        templateRepository.save(template);
        saveTemplateStructure(id, request.getGroups());
        return buildTemplateMap(id);
    }

    @Transactional
    public void deleteTemplate(Long id, RequestUserContext context) {
        ExtraScoreTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("模板不存在"));
        validateTemplatePermission(template, context);
        clearTemplateStructure(id);
        templateRepository.delete(template);
    }

    @Transactional
    public Map<String, Object> toggleTemplateStatus(Long id, RequestUserContext context) {
        ExtraScoreTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("模板不存在"));
        validateTemplatePermission(template, context);
        template.setStatus("active".equals(template.getStatus()) ? "inactive" : "active");
        templateRepository.save(template);
        return buildTemplateMap(template);
    }

    @Transactional
    public Map<String, Object> assignTemplateToTeacher(Long id, RequestUserContext context) {
        ExtraScoreTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("模板不存在"));
        if (!"active".equals(template.getStatus())) {
            throw new IllegalArgumentException("只能选择已启用的模板");
        }

        Long teacherId = resolveTeacherIdForAssignment(context);
        TeacherExtraScoreTemplate selection = selectionRepository.findByTeacherIdAndScoreType(teacherId, template.getScoreType())
                .orElseGet(TeacherExtraScoreTemplate::new);
        selection.setTeacherId(teacherId);
        selection.setScoreType(template.getScoreType());
        selection.setTemplateId(template.getId());
        selectionRepository.save(selection);

        return getAssignedTemplate(context, template.getScoreType());
    }

    public Map<String, Object> getAssignedTemplate(RequestUserContext context, String scoreType) {
        String normalizedType = normalizeScoreType(scoreType);
        Long teacherId = resolveTeacherIdForStudentView(context);
        if (teacherId == null) {
            throw new IllegalArgumentException("当前用户未绑定老师，无法获取附加分模板");
        }

        TeacherExtraScoreTemplate selection = selectionRepository.findByTeacherIdAndScoreType(teacherId, normalizedType)
                .orElse(null);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("teacherId", teacherId);
        result.put("scoreType", normalizedType);

        if (selection == null || selection.getTemplateId() == null) {
            result.put("template", null);
            return result;
        }

        ExtraScoreTemplate template = templateRepository.findById(selection.getTemplateId())
                .filter(item -> "active".equals(item.getStatus()))
                .orElse(null);

        result.put("template", template == null ? null : buildTemplateMap(template));
        return result;
    }

    public Map<String, Object> getAssignedTemplateFormContext(RequestUserContext context, String scoreType) {
        String normalizedType = normalizeScoreType(scoreType);
        Long teacherId = resolveTeacherIdForStudentView(context);
        if (teacherId == null) {
            throw new IllegalArgumentException("当前用户未绑定老师，无法获取附加分模板");
        }

        TeacherExtraScoreTemplate selection = selectionRepository.findByTeacherIdAndScoreType(teacherId, normalizedType)
                .orElse(null);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("teacherId", teacherId);
        result.put("scoreType", normalizedType);

        if (selection == null || selection.getTemplateId() == null) {
            result.put("template", null);
            return result;
        }

        ExtraScoreTemplate template = templateRepository.findById(selection.getTemplateId())
                .filter(item -> "active".equals(item.getStatus()))
                .orElse(null);

        result.put("template", template == null ? null : buildTemplateFormMap(template));
        return result;
    }

    public Resource exportAssignedTemplateWorkbook(RequestUserContext context, String scoreType) {
        String normalizedType = normalizeScoreType(scoreType);
        ExtraScoreTemplate template = resolveAssignedActiveTemplate(context, normalizedType);

        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            CellStyle headerStyle = createWorkbookHeaderStyle(workbook);
            writeTemplateInfoSheet(workbook, headerStyle, template, normalizedType);
            writeTemplateRuleSheet(workbook, headerStyle, template);
            workbook.write(outputStream);
            return new ByteArrayResource(outputStream.toByteArray());
        } catch (IOException exception) {
            throw new IllegalArgumentException("导出当前模板失败");
        }
    }

    @Transactional
    public Map<String, Object> importTemplateWorkbook(RequestUserContext context, String scoreType, MultipartFile file) {
        String normalizedType = normalizeScoreType(scoreType);
        if (!context.isTeacher() && !context.isAdmin()) {
            throw new IllegalArgumentException("仅教师或管理员可以导入模板");
        }
        if (context.getUserId() == null) {
            throw new IllegalArgumentException("当前账号无效，无法导入模板");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要导入的模板 Excel");
        }

        ExtraScoreTemplateRequest request;
        try (InputStream inputStream = file.getInputStream(); XSSFWorkbook workbook = new XSSFWorkbook(inputStream)) {
            request = buildTemplateRequestFromWorkbook(workbook, normalizedType);
        } catch (IOException exception) {
            throw new IllegalArgumentException("读取模板 Excel 失败");
        }

        Map<String, Object> templateMap = createTemplate(request, context);
        boolean assigned = false;
        if (context.isTeacher()) {
            Object importedId = templateMap.get("id");
            if (importedId instanceof Number number) {
                assignTemplateToTeacher(number.longValue(), context);
                assigned = true;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("template", templateMap);
        result.put("assigned", assigned);
        result.put("groupCount", safeList(request.getGroups()).size());
        result.put("itemCount", safeList(request.getGroups()).stream()
                .mapToInt(group -> safeList(group.getItems()).size())
                .sum());
        return result;
    }

    public ExtraScoreTemplate findTemplateEntity(Long id) {
        return templateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("模板不存在"));
    }

    private ExtraScoreTemplate resolveAssignedActiveTemplate(RequestUserContext context, String scoreType) {
        Long teacherId = resolveTeacherIdForAssignment(context);
        TeacherExtraScoreTemplate selection = selectionRepository.findByTeacherIdAndScoreType(teacherId, scoreType)
                .orElseThrow(() -> new IllegalArgumentException("当前没有使用中的模板，无法导出"));
        if (selection.getTemplateId() == null) {
            throw new IllegalArgumentException("当前没有使用中的模板，无法导出");
        }
        return templateRepository.findById(selection.getTemplateId())
                .filter(template -> "active".equals(template.getStatus()))
                .orElseThrow(() -> new IllegalArgumentException("当前使用的模板已停用或不存在"));
    }

    private void writeTemplateInfoSheet(
            XSSFWorkbook workbook,
            CellStyle headerStyle,
            ExtraScoreTemplate template,
            String scoreType
    ) {
        Sheet sheet = workbook.createSheet(TEMPLATE_INFO_SHEET_NAME);
        writeWorkbookHeaderRow(sheet, TEMPLATE_INFO_HEADERS, headerStyle);
        writeTemplateInfoRow(sheet, 1, "模板名称", template.getName(), "导入时会作为新模板名称");
        writeTemplateInfoRow(sheet, 2, "附加分类", resolveScoreTypeLabel(scoreType), "由当前页面决定，导入时会按页面类型保存");
        writeTemplateInfoRow(sheet, 3, "模板状态", "启用", "导入后默认启用，教师账号会自动设为当前使用");
        writeTemplateInfoRow(sheet, 4, "证明材料要求", template.getProofRequirement(), "学生提交时看到的凭证要求");
        writeTemplateInfoRow(sheet, 5, "模板说明", template.getDescription(), "模板列表中的说明");
        writeTemplateInfoRow(sheet, 6, "导出时间", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(java.time.LocalDateTime.now()), "");
        for (int index = 0; index < TEMPLATE_INFO_HEADERS.length; index++) {
            sheet.autoSizeColumn(index);
        }
    }

    private void writeTemplateInfoRow(Sheet sheet, int rowIndex, String field, String value, String note) {
        Row row = sheet.createRow(rowIndex);
        row.createCell(0).setCellValue(field);
        row.createCell(1).setCellValue(defaultString(value));
        row.createCell(2).setCellValue(defaultString(note));
    }

    private void writeTemplateRuleSheet(XSSFWorkbook workbook, CellStyle headerStyle, ExtraScoreTemplate template) {
        Sheet sheet = workbook.createSheet(TEMPLATE_RULE_SHEET_NAME);
        writeWorkbookHeaderRow(sheet, TEMPLATE_RULE_HEADERS, headerStyle);

        TemplateStructure structure = loadTemplateStructure(template.getId());

        int rowIndex = 1;
        for (ExtraScoreTemplateGroup group : structure.groups()) {
            for (ExtraScoreTemplateItem item : structure.itemsByGroup().getOrDefault(group.getId(), List.of())) {
                for (ExtraScoreTemplateItemLevelRule levelRule : structure.levelRulesByItem().getOrDefault(item.getId(), List.of())) {
                    List<ExtraScoreTemplateItemAuthorRule> levelAuthorRules = structure.authorRulesByLevelRule().getOrDefault(levelRule.getId(), List.of());
                    if (levelAuthorRules.isEmpty()) {
                        writeTemplateRuleRow(sheet.createRow(rowIndex++), group, item, levelRule, null);
                        continue;
                    }
                    for (ExtraScoreTemplateItemAuthorRule authorRule : levelAuthorRules) {
                        writeTemplateRuleRow(sheet.createRow(rowIndex++), group, item, levelRule, authorRule);
                    }
                }
            }
        }

        for (int index = 0; index < TEMPLATE_RULE_HEADERS.length; index++) {
            sheet.autoSizeColumn(index);
        }
    }

    private void writeTemplateRuleRow(
            Row row,
            ExtraScoreTemplateGroup group,
            ExtraScoreTemplateItem item,
            ExtraScoreTemplateItemLevelRule levelRule,
            ExtraScoreTemplateItemAuthorRule authorRule
    ) {
        row.createCell(0).setCellValue(defaultString(group.getGroupName()));
        writeOptionalNumberCell(row, 1, group.getMaxScore());
        row.createCell(2).setCellValue(Boolean.TRUE.equals(group.getSameEventBestOnly()) ? "是" : "否");
        row.createCell(3).setCellValue(defaultString(item.getItemName()));
        row.createCell(4).setCellValue(defaultString(item.getDescription()));
        row.createCell(5).setCellValue("deduct".equals(normalizeScoreDirection(item.getScoreDirection())) ? "减分" : "加分");
        writeOptionalNumberCell(row, 6, item.getMaxAuthors());
        writeOptionalNumberCell(row, 7, item.getSameEventMaxCount());
        row.createCell(8).setCellValue("inactive".equals(item.getStatus()) ? "停用" : "启用");
        row.createCell(9).setCellValue(defaultString(levelRule.getLevelName()));
        writeOptionalNumberCell(row, 10, levelRule.getScore());
        row.createCell(11).setCellValue(authorRule == null ? "" : defaultString(authorRule.getAuthorLabel()));
        row.createCell(12).setCellValue(authorRule == null || "ratio".equals(normalizeAuthorRuleMode(authorRule.getMode())) ? "比例" : "分值");
        writeOptionalNumberCell(row, 13, authorRule == null ? null : authorRule.getRatio());
        writeOptionalNumberCell(row, 14, authorRule == null ? null : authorRule.getFixedScore());
    }

    private void writeOptionalNumberCell(Row row, int columnIndex, Number value) {
        if (value == null) {
            row.createCell(columnIndex).setCellValue("");
            return;
        }
        row.createCell(columnIndex).setCellValue(value.doubleValue());
    }

    private ExtraScoreTemplateRequest buildTemplateRequestFromWorkbook(XSSFWorkbook workbook, String scoreType) {
        DataFormatter formatter = new DataFormatter();
        Sheet infoSheet = workbook.getSheet(TEMPLATE_INFO_SHEET_NAME);
        Sheet ruleSheet = workbook.getSheet(TEMPLATE_RULE_SHEET_NAME);
        if (ruleSheet == null && workbook.getNumberOfSheets() > 0) {
            ruleSheet = workbook.getNumberOfSheets() > 1 ? workbook.getSheetAt(1) : workbook.getSheetAt(0);
        }
        if (ruleSheet == null) {
            throw new IllegalArgumentException("模板 Excel 中没有分类规则工作表");
        }

        Map<String, String> info = readTemplateInfoSheet(infoSheet, formatter);
        ExtraScoreTemplateRequest request = new ExtraScoreTemplateRequest();
        request.setName(resolveImportedTemplateName(info.get("模板名称"), scoreType));
        request.setScoreType(scoreType);
        request.setDescription(defaultString(info.get("模板说明")));
        request.setProofRequirement(defaultString(info.get("证明材料要求")));
        request.setStatus("active");
        request.setGroups(readTemplateRuleGroups(ruleSheet, formatter));
        request.setMaxScore(resolveTemplateMaxScore(request));
        return request;
    }

    private String resolveImportedTemplateName(String name, String scoreType) {
        String normalizedName = defaultString(name);
        if (!normalizedName.isBlank()) {
            return normalizedName.endsWith("（导入）") ? normalizedName : normalizedName + "（导入）";
        }
        return resolveTemplateName(null, scoreType) + "（导入）";
    }

    private Map<String, String> readTemplateInfoSheet(Sheet sheet, DataFormatter formatter) {
        Map<String, String> result = new LinkedHashMap<>();
        if (sheet == null) {
            return result;
        }
        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            String key = readWorkbookCellText(row, 0, formatter);
            String value = readWorkbookCellText(row, 1, formatter);
            if (!key.isBlank()) {
                result.put(key, value);
            }
        }
        return result;
    }

    private List<ExtraScoreTemplateRequest.GroupRequest> readTemplateRuleGroups(Sheet sheet, DataFormatter formatter) {
        Row headerRow = sheet.getRow(0);
        Map<String, Integer> headers = readWorkbookHeaders(headerRow, formatter);
        int groupIndex = requireTemplateColumn(headers, List.of("大类", "申请大类"));
        int itemIndex = requireTemplateColumn(headers, List.of("小类", "申请小类", "申请分类"));
        int levelIndex = requireTemplateColumn(headers, List.of("级别", "级别名称"));
        int authorIndex = resolveTemplateColumn(headers, List.of("作者规则", "作者名称", "作者"), -1);
        int groupMaxIndex = resolveTemplateColumn(headers, List.of("大类最高分", "大类上限"), -1);
        int sameEventBestIndex = resolveTemplateColumn(headers, List.of("同赛事取最高", "同事件取最高"), -1);
        int itemDescIndex = resolveTemplateColumn(headers, List.of("小类说明", "说明"), -1);
        int scoreDirectionIndex = resolveTemplateColumn(headers, List.of("计分方向", "加减分"), -1);
        int maxAuthorsIndex = resolveTemplateColumn(headers, List.of("小类最多作者数", "最多作者数"), -1);
        int sameEventMaxCountIndex = resolveTemplateColumn(headers, List.of("同赛事最多计入次数", "同事件最多计入次数", "最多计入次数"), -1);
        int itemStatusIndex = resolveTemplateColumn(headers, List.of("小类状态", "状态"), -1);
        int levelScoreIndex = resolveTemplateColumn(headers, List.of("级别分值", "基础分", "分值"), -1);
        int authorModeIndex = resolveTemplateColumn(headers, List.of("规则类型", "作者规则类型"), -1);
        int ratioIndex = resolveTemplateColumn(headers, List.of("比例(%)", "比例", "作者比例"), -1);
        int fixedScoreIndex = resolveTemplateColumn(headers, List.of("固定分", "固定分值", "作者分值"), -1);

        Map<String, ExtraScoreTemplateRequest.GroupRequest> groupsByKey = new LinkedHashMap<>();
        Map<String, Map<String, ExtraScoreTemplateRequest.ItemRequest>> itemsByGroup = new LinkedHashMap<>();
        Map<String, Map<String, ExtraScoreTemplateRequest.LevelRuleRequest>> levelsByItem = new LinkedHashMap<>();

        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (isTemplateRuleRowBlank(row, formatter, headers.values())) {
                continue;
            }

            String groupName = readWorkbookCellText(row, groupIndex, formatter);
            String itemName = readWorkbookCellText(row, itemIndex, formatter);
            String levelName = readWorkbookCellText(row, levelIndex, formatter);
            if (groupName.isBlank() || itemName.isBlank() || levelName.isBlank()) {
                throw new IllegalArgumentException("第 " + (rowIndex + 1) + " 行需填写大类、小类和级别");
            }

            String groupKey = normalizeTemplateLookupKey(groupName);
            ExtraScoreTemplateRequest.GroupRequest group = groupsByKey.computeIfAbsent(groupKey, key -> {
                ExtraScoreTemplateRequest.GroupRequest created = new ExtraScoreTemplateRequest.GroupRequest();
                created.setGroupName(groupName);
                created.setMaxScore(parseOptionalWorkbookDouble(readWorkbookCellText(row, groupMaxIndex, formatter)));
                created.setSameEventBestOnly(parseWorkbookBoolean(readWorkbookCellText(row, sameEventBestIndex, formatter)));
                created.setSortOrder(groupsByKey.size() * 10);
                created.setItems(new ArrayList<>());
                return created;
            });

            Map<String, ExtraScoreTemplateRequest.ItemRequest> items = itemsByGroup.computeIfAbsent(groupKey, key -> new LinkedHashMap<>());
            String itemKey = normalizeTemplateLookupKey(itemName);
            ExtraScoreTemplateRequest.ItemRequest item = items.computeIfAbsent(itemKey, key -> {
                ExtraScoreTemplateRequest.ItemRequest created = new ExtraScoreTemplateRequest.ItemRequest();
                created.setItemName(itemName);
                created.setDescription(readWorkbookCellText(row, itemDescIndex, formatter));
                created.setMaxAuthors(parseOptionalWorkbookInteger(readWorkbookCellText(row, maxAuthorsIndex, formatter)));
                created.setSameEventMaxCount(parseOptionalWorkbookInteger(readWorkbookCellText(row, sameEventMaxCountIndex, formatter)));
                created.setScoreDirection(normalizeImportedScoreDirection(readWorkbookCellText(row, scoreDirectionIndex, formatter)));
                created.setStatus(normalizeImportedStatus(readWorkbookCellText(row, itemStatusIndex, formatter)));
                created.setSortOrder(items.size() * 10);
                created.setLevelRules(new ArrayList<>());
                group.getItems().add(created);
                return created;
            });

            String itemScopeKey = groupKey + "|" + itemKey;
            Map<String, ExtraScoreTemplateRequest.LevelRuleRequest> levels = levelsByItem.computeIfAbsent(itemScopeKey, key -> new LinkedHashMap<>());
            String levelKey = normalizeTemplateLookupKey(levelName);
            ExtraScoreTemplateRequest.LevelRuleRequest levelRule = levels.computeIfAbsent(levelKey, key -> {
                ExtraScoreTemplateRequest.LevelRuleRequest created = new ExtraScoreTemplateRequest.LevelRuleRequest();
                created.setLevelName(levelName);
                created.setScore(parseOptionalWorkbookDouble(readWorkbookCellText(row, levelScoreIndex, formatter)));
                created.setSortOrder(levels.size() * 10);
                created.setAuthorRules(new ArrayList<>());
                item.getLevelRules().add(created);
                return created;
            });

            String authorLabel = readWorkbookCellText(row, authorIndex, formatter);
            if (authorLabel.isBlank()) {
                authorLabel = "第一作者";
            }
            ExtraScoreTemplateRequest.AuthorRuleRequest authorRule = new ExtraScoreTemplateRequest.AuthorRuleRequest();
            Double ratio = parseOptionalWorkbookDouble(readWorkbookCellText(row, ratioIndex, formatter));
            Double fixedScore = parseOptionalWorkbookDouble(readWorkbookCellText(row, fixedScoreIndex, formatter));
            String authorModeText = readWorkbookCellText(row, authorModeIndex, formatter);
            String mode = normalizeImportedAuthorMode(authorModeText);
            if (authorModeText.isBlank() && fixedScore != null && fixedScore > 0 && (ratio == null || ratio <= 0)) {
                mode = "fixed";
            }
            if ("fixed".equals(mode) && (fixedScore == null || fixedScore <= 0)) {
                fixedScore = parseOptionalWorkbookDouble(readWorkbookCellText(row, levelScoreIndex, formatter));
            }
            authorRule.setAuthorLabel(authorLabel);
            authorRule.setMode(mode);
            authorRule.setRatio("fixed".equals(mode) ? null : (ratio == null || ratio <= 0 ? 100D : ratio));
            authorRule.setFixedScore("fixed".equals(mode) ? fixedScore : null);
            authorRule.setSortOrder(levelRule.getAuthorRules().size() * 10);
            levelRule.getAuthorRules().add(authorRule);
        }

        List<ExtraScoreTemplateRequest.GroupRequest> groups = new ArrayList<>(groupsByKey.values());
        if (groups.isEmpty() || groups.stream().noneMatch(this::hasCompleteItem)) {
            throw new IllegalArgumentException("模板 Excel 中没有可导入的有效规则");
        }
        return groups;
    }

    private CellStyle createWorkbookHeaderStyle(XSSFWorkbook workbook) {
        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setColor(IndexedColors.DARK_BLUE.getIndex());
        headerStyle.setFont(headerFont);
        return headerStyle;
    }

    private void writeWorkbookHeaderRow(Sheet sheet, String[] headers, CellStyle headerStyle) {
        Row row = sheet.createRow(0);
        for (int index = 0; index < headers.length; index++) {
            Cell cell = row.createCell(index);
            cell.setCellValue(headers[index]);
            cell.setCellStyle(headerStyle);
        }
    }

    private Map<String, Integer> readWorkbookHeaders(Row row, DataFormatter formatter) {
        if (row == null) {
            throw new IllegalArgumentException("模板 Excel 缺少表头行");
        }
        Map<String, Integer> headers = new LinkedHashMap<>();
        for (int index = 0; index < row.getLastCellNum(); index++) {
            String header = readWorkbookCellText(row, index, formatter);
            if (!header.isBlank()) {
                headers.put(normalizeTemplateHeader(header), index);
            }
        }
        return headers;
    }

    private int requireTemplateColumn(Map<String, Integer> headers, List<String> names) {
        int index = resolveTemplateColumn(headers, names, -1);
        if (index < 0) {
            throw new IllegalArgumentException("模板 Excel 缺少必要列：" + names.get(0));
        }
        return index;
    }

    private int resolveTemplateColumn(Map<String, Integer> headers, List<String> names, int fallback) {
        for (String name : names) {
            Integer index = headers.get(normalizeTemplateHeader(name));
            if (index != null) {
                return index;
            }
        }
        return fallback;
    }

    private String readWorkbookCellText(Row row, int columnIndex, DataFormatter formatter) {
        if (row == null || columnIndex < 0) {
            return "";
        }
        Cell cell = row.getCell(columnIndex);
        if (cell == null) {
            return "";
        }
        return formatter.formatCellValue(cell).trim();
    }

    private boolean isTemplateRuleRowBlank(Row row, DataFormatter formatter, Collection<Integer> columns) {
        if (row == null) {
            return true;
        }
        for (Integer column : columns) {
            if (column != null && !readWorkbookCellText(row, column, formatter).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private String normalizeTemplateHeader(String value) {
        return defaultString(value)
                .replace("（", "(")
                .replace("）", ")")
                .replaceAll("[\\s()]", "")
                .toLowerCase(Locale.ROOT);
    }

    private String normalizeTemplateLookupKey(String value) {
        return defaultString(value)
                .replaceAll("\\s+", "")
                .toLowerCase(Locale.ROOT);
    }

    private Double parseOptionalWorkbookDouble(String value) {
        String text = defaultString(value)
                .replace("%", "")
                .replace("分", "")
                .replace(",", "")
                .trim();
        if (text.isBlank() || "-".equals(text) || "—".equals(text)) {
            return null;
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("数字格式不正确：" + value);
        }
    }

    private Integer parseOptionalWorkbookInteger(String value) {
        Double number = parseOptionalWorkbookDouble(value);
        if (number == null || number <= 0) {
            return null;
        }
        return Math.max(1, number.intValue());
    }

    private Boolean parseWorkbookBoolean(String value) {
        String text = defaultString(value).toLowerCase(Locale.ROOT);
        if (text.isBlank()) {
            return false;
        }
        return List.of("是", "true", "yes", "y", "1", "取最高").contains(text);
    }

    private String normalizeImportedScoreDirection(String value) {
        String text = defaultString(value).toLowerCase(Locale.ROOT);
        if (text.contains("减") || text.contains("扣") || text.contains("deduct") || text.startsWith("-")) {
            return "deduct";
        }
        return "add";
    }

    private String normalizeImportedStatus(String value) {
        String text = defaultString(value).toLowerCase(Locale.ROOT);
        if (text.contains("停") || text.contains("inactive") || text.contains("禁用")) {
            return "inactive";
        }
        return "active";
    }

    private String normalizeImportedAuthorMode(String value) {
        String text = defaultString(value).toLowerCase(Locale.ROOT);
        if (text.contains("分值") || text.contains("固定") || text.contains("fixed")) {
            return "fixed";
        }
        return "ratio";
    }

    private String resolveScoreTypeLabel(String scoreType) {
        return switch (scoreType) {
            case "MORAL" -> "德育";
            case "INTELLECTUAL" -> "智育";
            case "PHYSICAL" -> "体育";
            default -> "附加分";
        };
    }

    private void applyTemplateBase(ExtraScoreTemplate template, ExtraScoreTemplateRequest request, RequestUserContext context) {
        String scoreType = normalizeScoreType(request.getScoreType());
        template.setName(resolveTemplateName(request.getName(), scoreType));
        template.setScoreType(scoreType);
        template.setDescription(defaultString(request.getDescription()));
        template.setProofRequirement(defaultString(request.getProofRequirement()));
        template.setStatus(normalizeStatus(request.getStatus()));
        template.setMaxScore(resolveTemplateMaxScore(request));

        if (template.getCreatorId() == null) {
            if (context.getUserId() == null) {
                throw new IllegalArgumentException("当前用户无效，无法创建模板");
            }
            template.setCreatorId(context.getUserId());
        }
    }

    private String resolveTemplateName(String name, String scoreType) {
        if (name != null && !name.isBlank()) {
            return name.trim();
        }
        return switch (scoreType) {
            case "MORAL" -> "德育附加分模板";
            case "INTELLECTUAL" -> "智育附加分模板";
            case "PHYSICAL" -> "体育附加分模板";
            default -> "附加分模板";
        };
    }

    private Double resolveTemplateMaxScore(ExtraScoreTemplateRequest request) {
        double derivedMax = 0D;
        for (ExtraScoreTemplateRequest.GroupRequest groupRequest : safeList(request.getGroups())) {
            if (groupRequest == null) {
                continue;
            }
            for (ExtraScoreTemplateRequest.ItemRequest itemRequest : safeList(groupRequest.getItems())) {
                if (itemRequest == null) {
                    continue;
                }
                for (ExtraScoreTemplateRequest.LevelRuleRequest levelRuleRequest : safeList(itemRequest.getLevelRules())) {
                    derivedMax = Math.max(derivedMax, resolveLevelRuleMaxScore(levelRuleRequest));
                }
            }
        }

        if (derivedMax > 0) {
            return derivedMax;
        }
        if (request.getMaxScore() != null && request.getMaxScore() > 0) {
            return request.getMaxScore();
        }
        return 0D;
    }

    private void saveTemplateStructure(Long templateId, List<ExtraScoreTemplateRequest.GroupRequest> groups) {
        clearTemplateStructure(templateId);

        for (ExtraScoreTemplateRequest.GroupRequest groupRequest : safeList(groups)) {
            if (groupRequest == null) {
                continue;
            }
            if (groupRequest.getGroupName() == null || groupRequest.getGroupName().isBlank()) {
                continue;
            }
            if (!hasCompleteItem(groupRequest)) {
                continue;
            }

            ExtraScoreTemplateGroup group = new ExtraScoreTemplateGroup();
            group.setTemplateId(templateId);
            group.setGroupName(groupRequest.getGroupName().trim());
            group.setMaxScore(normalizeOptionalPositive(groupRequest.getMaxScore(), "大类最高分必须大于 0"));
            group.setSameEventBestOnly(Boolean.TRUE.equals(groupRequest.getSameEventBestOnly()));
            group.setSortOrder(normalizeNonNegative(groupRequest.getSortOrder(), 0));
            ExtraScoreTemplateGroup savedGroup = groupRepository.save(group);

            for (ExtraScoreTemplateRequest.ItemRequest itemRequest : safeList(groupRequest.getItems())) {
                if (!hasCompleteItem(itemRequest)) {
                    continue;
                }
                ExtraScoreTemplateItem savedItem = saveItem(templateId, savedGroup.getId(), itemRequest);
                saveLevelRules(savedItem.getId(), itemRequest.getLevelRules());
            }
        }
    }

    private boolean hasCompleteItem(ExtraScoreTemplateRequest.GroupRequest groupRequest) {
        return safeList(groupRequest.getItems()).stream().anyMatch(this::hasCompleteItem);
    }

    private boolean hasCompleteItem(ExtraScoreTemplateRequest.ItemRequest itemRequest) {
        return itemRequest != null
                && itemRequest.getItemName() != null
                && !itemRequest.getItemName().isBlank()
                && safeList(itemRequest.getLevelRules()).stream()
                .anyMatch(ruleRequest -> ruleRequest != null
                        && ruleRequest.getLevelName() != null
                        && !ruleRequest.getLevelName().isBlank()
                        && hasValidAuthorRule(ruleRequest));
    }

    private ExtraScoreTemplateItem saveItem(Long templateId, Long groupId, ExtraScoreTemplateRequest.ItemRequest itemRequest) {
        ExtraScoreTemplateItem item = new ExtraScoreTemplateItem();
        item.setTemplateId(templateId);
        item.setGroupId(groupId);
        item.setItemName(defaultString(itemRequest.getItemName()));
        item.setDescription(defaultString(itemRequest.getDescription()));
        item.setMaxAuthors(normalizePositive(itemRequest.getMaxAuthors(), "小类最多作者数必须大于 0"));
        item.setSameEventMaxCount(normalizePositive(itemRequest.getSameEventMaxCount(), "同一赛事最多计入次数必须大于 0"));
        item.setScoreDirection(normalizeScoreDirection(itemRequest.getScoreDirection()));
        item.setSortOrder(normalizeNonNegative(itemRequest.getSortOrder(), 0));
        item.setStatus(normalizeStatus(itemRequest.getStatus()));
        return itemRepository.save(item);
    }

    private void saveLevelRules(Long itemId, List<ExtraScoreTemplateRequest.LevelRuleRequest> rules) {
        for (ExtraScoreTemplateRequest.LevelRuleRequest ruleRequest : safeList(rules)) {
            if (ruleRequest == null) {
                continue;
            }
            if (ruleRequest.getLevelName() == null || ruleRequest.getLevelName().isBlank() || !hasValidAuthorRule(ruleRequest)) {
                continue;
            }
            ExtraScoreTemplateItemLevelRule rule = new ExtraScoreTemplateItemLevelRule();
            rule.setItemId(itemId);
            rule.setLevelName(ruleRequest.getLevelName().trim());
            rule.setScore(resolveStoredLevelScore(ruleRequest));
            rule.setSortOrder(normalizeNonNegative(ruleRequest.getSortOrder(), 0));
            ExtraScoreTemplateItemLevelRule savedRule = levelRuleRepository.save(rule);
            saveAuthorRules(itemId, savedRule.getId(), ruleRequest.getAuthorRules());
        }
    }

    private double resolveStoredLevelScore(ExtraScoreTemplateRequest.LevelRuleRequest ruleRequest) {
        boolean hasRatioRule = safeList(ruleRequest.getAuthorRules()).stream()
                .filter(rule -> rule != null && rule.getAuthorLabel() != null && !rule.getAuthorLabel().isBlank())
                .anyMatch(rule -> "ratio".equals(normalizeAuthorRuleMode(rule.getMode())));
        if (hasRatioRule && ruleRequest.getScore() != null && ruleRequest.getScore() > 0) {
            return ruleRequest.getScore();
        }
        double maxScore = resolveLevelRuleMaxScore(ruleRequest);
        return maxScore > 0 ? maxScore : 1D;
    }

    private double resolveLevelRuleMaxScore(ExtraScoreTemplateRequest.LevelRuleRequest ruleRequest) {
        if (ruleRequest == null) {
            return 0D;
        }
        double baseScore = ruleRequest.getScore() == null ? 0D : ruleRequest.getScore();
        double maxScore = 0D;
        for (ExtraScoreTemplateRequest.AuthorRuleRequest authorRule : safeList(ruleRequest.getAuthorRules())) {
            if (authorRule == null) {
                continue;
            }
            String mode = normalizeAuthorRuleMode(authorRule.getMode());
            if ("fixed".equals(mode)) {
                maxScore = Math.max(maxScore, authorRule.getFixedScore() == null ? 0D : authorRule.getFixedScore());
                continue;
            }
            if (baseScore > 0 && authorRule.getRatio() != null && authorRule.getRatio() > 0) {
                maxScore = Math.max(maxScore, baseScore * authorRule.getRatio() / 100D);
            }
        }
        return maxScore;
    }

    private boolean hasValidAuthorRule(ExtraScoreTemplateRequest.LevelRuleRequest ruleRequest) {
        if (ruleRequest == null) {
            return false;
        }
        double baseScore = ruleRequest.getScore() == null ? 0D : ruleRequest.getScore();
        for (ExtraScoreTemplateRequest.AuthorRuleRequest authorRule : safeList(ruleRequest.getAuthorRules())) {
            if (authorRule == null || authorRule.getAuthorLabel() == null || authorRule.getAuthorLabel().isBlank()) {
                continue;
            }
            String mode = normalizeAuthorRuleMode(authorRule.getMode());
            if ("fixed".equals(mode) && authorRule.getFixedScore() != null && authorRule.getFixedScore() > 0) {
                return true;
            }
            if ("ratio".equals(mode) && baseScore > 0 && authorRule.getRatio() != null && authorRule.getRatio() > 0) {
                return true;
            }
        }
        return false;
    }

    private void saveAuthorRules(Long itemId, Long levelRuleId, List<ExtraScoreTemplateRequest.AuthorRuleRequest> rules) {
        for (ExtraScoreTemplateRequest.AuthorRuleRequest ruleRequest : safeList(rules)) {
            if (ruleRequest == null) {
                continue;
            }
            if (ruleRequest.getAuthorLabel() == null || ruleRequest.getAuthorLabel().isBlank()) {
                continue;
            }
            ExtraScoreTemplateItemAuthorRule rule = new ExtraScoreTemplateItemAuthorRule();
            String mode = normalizeAuthorRuleMode(ruleRequest.getMode());
            Double ratio = ruleRequest.getRatio();
            Double fixedScore = ruleRequest.getFixedScore();
            if ("fixed".equals(mode)) {
                if (fixedScore == null || fixedScore <= 0) {
                    continue;
                }
                ratio = null;
            } else {
                if (ratio == null || ratio <= 0) {
                    continue;
                }
                fixedScore = null;
            }
            rule.setItemId(itemId);
            rule.setLevelRuleId(levelRuleId);
            rule.setAuthorLabel(ruleRequest.getAuthorLabel().trim());
            rule.setMode(mode);
            rule.setRatio(ratio);
            rule.setFixedScore(fixedScore);
            rule.setSortOrder(normalizeNonNegative(ruleRequest.getSortOrder(), 0));
            authorRuleRepository.save(rule);
        }
    }

    private void clearTemplateStructure(Long templateId) {
        List<ExtraScoreTemplateItem> items = itemRepository.findByTemplateIdOrderBySortOrderAscIdAsc(templateId);
        List<Long> itemIds = items.stream()
                .map(ExtraScoreTemplateItem::getId)
                .toList();
        if (!itemIds.isEmpty()) {
            List<ExtraScoreTemplateItemLevelRule> levelRules = levelRuleRepository.findByItemIdInOrderBySortOrderAscIdAsc(itemIds);
            List<Long> levelRuleIds = levelRules.stream().map(ExtraScoreTemplateItemLevelRule::getId).toList();
            if (!levelRuleIds.isEmpty()) {
                authorRuleRepository.deleteByLevelRuleIdIn(levelRuleIds);
            }
            levelRuleRepository.deleteByItemIdIn(itemIds);
            authorRuleRepository.deleteByItemIdIn(itemIds);
        }
        itemRepository.deleteByTemplateId(templateId);
        groupRepository.deleteByTemplateId(templateId);
    }

    private Map<String, Object> buildTemplateMap(Long templateId) {
        ExtraScoreTemplate template = findTemplateEntity(templateId);
        return buildTemplateMap(template);
    }

    private Map<String, Object> buildTemplateSummaryMap(ExtraScoreTemplate template) {
        TemplateStructure structure = loadTemplateStructure(template.getId());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", template.getId());
        result.put("name", template.getName());
        result.put("scoreType", template.getScoreType());
        result.put("maxScore", template.getMaxScore());
        result.put("description", template.getDescription());
        result.put("proofRequirement", template.getProofRequirement());
        result.put("creatorId", template.getCreatorId());
        result.put("creatorName", resolveCreatorName(template.getCreatorId()));
        result.put("status", template.getStatus());
        result.put("createdAt", template.getCreatedAt());
        result.put("updatedAt", template.getUpdatedAt());
        result.put("groupCount", structure.groups().size());
        result.put("itemCount", structure.items().size());
        return result;
    }

    private Map<String, Object> buildTemplateMap(ExtraScoreTemplate template) {
        TemplateStructure structure = loadTemplateStructure(template.getId());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", template.getId());
        result.put("name", template.getName());
        result.put("scoreType", template.getScoreType());
        result.put("maxScore", template.getMaxScore());
        result.put("description", template.getDescription());
        result.put("proofRequirement", template.getProofRequirement());
        result.put("creatorId", template.getCreatorId());
        result.put("creatorName", resolveCreatorName(template.getCreatorId()));
        result.put("status", template.getStatus());
        result.put("createdAt", template.getCreatedAt());
        result.put("updatedAt", template.getUpdatedAt());

        List<Map<String, Object>> groupList = new ArrayList<>();
        for (ExtraScoreTemplateGroup group : structure.groups()) {
            Map<String, Object> groupMap = new LinkedHashMap<>();
            groupMap.put("id", group.getId());
            groupMap.put("groupName", group.getGroupName());
            groupMap.put("maxScore", group.getMaxScore());
            groupMap.put("sameEventBestOnly", Boolean.TRUE.equals(group.getSameEventBestOnly()));
            groupMap.put("sortOrder", group.getSortOrder());

            List<Map<String, Object>> itemList = new ArrayList<>();
            for (ExtraScoreTemplateItem item : structure.itemsByGroup().getOrDefault(group.getId(), List.of())) {
                Map<String, Object> itemMap = new LinkedHashMap<>();
                itemMap.put("id", item.getId());
                itemMap.put("itemName", item.getItemName());
                itemMap.put("description", item.getDescription());
                itemMap.put("maxAuthors", item.getMaxAuthors());
                itemMap.put("sameEventMaxCount", item.getSameEventMaxCount());
                itemMap.put("scoreDirection", normalizeScoreDirection(item.getScoreDirection()));
                itemMap.put("sortOrder", item.getSortOrder());
                itemMap.put("status", item.getStatus());

                List<Map<String, Object>> levelRuleList = structure.levelRulesByItem().getOrDefault(item.getId(), List.of()).stream()
                        .map(rule -> {
                            Map<String, Object> ruleMap = new LinkedHashMap<>();
                            ruleMap.put("id", rule.getId());
                            ruleMap.put("levelName", rule.getLevelName());
                            ruleMap.put("score", rule.getScore());
                            ruleMap.put("sortOrder", rule.getSortOrder());
                            List<Map<String, Object>> authorRuleList = structure.authorRulesByLevelRule().getOrDefault(rule.getId(), List.of()).stream()
                                    .map(authorRule -> {
                                        Map<String, Object> authorMap = new LinkedHashMap<>();
                                        authorMap.put("id", authorRule.getId());
                                        authorMap.put("authorLabel", authorRule.getAuthorLabel());
                                        authorMap.put("mode", authorRule.getMode());
                                        authorMap.put("ratio", authorRule.getRatio());
                                        authorMap.put("fixedScore", authorRule.getFixedScore());
                                        authorMap.put("sortOrder", authorRule.getSortOrder());
                                        return authorMap;
                                    })
                                    .toList();
                            ruleMap.put("authorRules", authorRuleList);
                            return ruleMap;
                        })
                        .toList();

                itemMap.put("levelRules", levelRuleList);
                itemList.add(itemMap);
            }

            groupMap.put("items", itemList);
            groupList.add(groupMap);
        }

        result.put("groups", groupList);
        return result;
    }

    private String resolveCreatorName(Long creatorId) {
        if (creatorId == null) {
            return "";
        }
        return userRepository.findById(creatorId)
                .map(this::formatUserDisplayName)
                .orElse("");
    }

    private String formatUserDisplayName(User user) {
        if (user.getNickname() != null && !user.getNickname().isBlank()) {
            return user.getNickname();
        }
        if (user.getUsername() != null && !user.getUsername().isBlank()) {
            return user.getUsername();
        }
        return "";
    }

    private Map<String, Object> buildTemplateFormMap(ExtraScoreTemplate template) {
        TemplateStructure structure = loadTemplateStructure(template.getId());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", template.getId());
        result.put("name", template.getName());
        result.put("scoreType", template.getScoreType());
        result.put("maxScore", template.getMaxScore());

        List<Map<String, Object>> groupList = new ArrayList<>();
        for (ExtraScoreTemplateGroup group : structure.groups()) {
            Map<String, Object> groupMap = new LinkedHashMap<>();
            groupMap.put("id", group.getId());
            groupMap.put("groupName", group.getGroupName());
            groupMap.put("sameEventBestOnly", Boolean.TRUE.equals(group.getSameEventBestOnly()));

            List<Map<String, Object>> itemList = new ArrayList<>();
            for (ExtraScoreTemplateItem item : structure.itemsByGroup().getOrDefault(group.getId(), List.of())) {
                Map<String, Object> itemMap = new LinkedHashMap<>();
                itemMap.put("id", item.getId());
                itemMap.put("itemName", item.getItemName());
                itemMap.put("scoreDirection", normalizeScoreDirection(item.getScoreDirection()));
                itemMap.put("status", item.getStatus());

                // 从第一个 levelRule 的第一条 authorRule 中派生 authorScoreMode
                String derivedMode = structure.levelRulesByItem().getOrDefault(item.getId(), List.of()).stream()
                        .flatMap(lr -> structure.authorRulesByLevelRule().getOrDefault(lr.getId(), List.of()).stream())
                        .map(ExtraScoreTemplateItemAuthorRule::getMode)
                        .filter(m -> m != null && !m.isBlank())
                        .findFirst()
                        .orElse("ratio");
                itemMap.put("authorScoreMode", derivedMode);

                itemMap.put("levelRules", structure.levelRulesByItem().getOrDefault(item.getId(), List.of()).stream()
                        .map(rule -> {
                            Map<String, Object> ruleMap = new LinkedHashMap<>();
                            ruleMap.put("id", rule.getId());
                            ruleMap.put("levelName", rule.getLevelName());
                            ruleMap.put("score", rule.getScore());
                            List<Map<String, Object>> authorRuleList = structure.authorRulesByLevelRule().getOrDefault(rule.getId(), List.of()).stream()
                                    .map(authorRule -> {
                                        Map<String, Object> authorMap = new LinkedHashMap<>();
                                        authorMap.put("id", authorRule.getId());
                                        authorMap.put("authorLabel", authorRule.getAuthorLabel());
                                        authorMap.put("mode", authorRule.getMode());
                                        authorMap.put("ratio", authorRule.getRatio());
                                        authorMap.put("fixedScore", authorRule.getFixedScore());
                                        return authorMap;
                                    })
                                    .toList();
                            ruleMap.put("authorRules", authorRuleList);
                            return ruleMap;
                        })
                        .toList());
                itemList.add(itemMap);
            }

            groupMap.put("items", itemList);
            groupList.add(groupMap);
        }

        result.put("groups", groupList);
        return result;
    }

    private TemplateStructure loadTemplateStructure(Long templateId) {
        List<ExtraScoreTemplateGroup> persistedGroups = safeList(groupRepository.findByTemplateIdOrderBySortOrderAscIdAsc(templateId));
        List<Long> groupIds = persistedGroups.stream()
                .map(ExtraScoreTemplateGroup::getId)
                .filter(id -> id != null)
                .toList();
        Set<Long> validGroupIds = new LinkedHashSet<>(groupIds);

        List<ExtraScoreTemplateItem> items = safeList(itemRepository.findByTemplateIdOrderBySortOrderAscIdAsc(templateId)).stream()
                .filter(item -> item != null && item.getId() != null)
                .toList();
        List<Long> itemIds = items.stream()
                .map(ExtraScoreTemplateItem::getId)
                .toList();
        List<ExtraScoreTemplateItemLevelRule> levelRules = itemIds.isEmpty()
                ? List.of()
                : safeList(levelRuleRepository.findByItemIdInOrderBySortOrderAscIdAsc(itemIds)).stream()
                .filter(rule -> rule != null && rule.getId() != null && rule.getItemId() != null)
                .toList();
        List<Long> levelRuleIds = levelRules.stream()
                .map(ExtraScoreTemplateItemLevelRule::getId)
                .toList();
        List<ExtraScoreTemplateItemAuthorRule> authorRules = levelRuleIds.isEmpty()
                ? List.of()
                : safeList(authorRuleRepository.findByLevelRuleIdInOrderBySortOrderAscIdAsc(levelRuleIds)).stream()
                .filter(rule -> rule != null && rule.getLevelRuleId() != null)
                .toList();

        Map<Long, List<ExtraScoreTemplateItem>> itemsByGroup = new LinkedHashMap<>();
        boolean hasUngroupedItems = false;
        for (ExtraScoreTemplateItem item : items) {
            Long groupKey = resolveTemplateItemGroupKey(item, validGroupIds);
            hasUngroupedItems = hasUngroupedItems || UNGROUPED_TEMPLATE_GROUP_ID.equals(groupKey);
            itemsByGroup.computeIfAbsent(groupKey, key -> new ArrayList<>()).add(item);
        }

        List<ExtraScoreTemplateGroup> groups = new ArrayList<>(persistedGroups);
        if (hasUngroupedItems) {
            groups.add(createUngroupedTemplateGroup(templateId, groups.size()));
        }

        Map<Long, List<ExtraScoreTemplateItemLevelRule>> levelRulesByItem = new LinkedHashMap<>();
        for (ExtraScoreTemplateItemLevelRule rule : levelRules) {
            levelRulesByItem.computeIfAbsent(rule.getItemId(), key -> new ArrayList<>()).add(rule);
        }

        Map<Long, List<ExtraScoreTemplateItemAuthorRule>> authorRulesByLevelRule = new LinkedHashMap<>();
        for (ExtraScoreTemplateItemAuthorRule rule : authorRules) {
            authorRulesByLevelRule.computeIfAbsent(rule.getLevelRuleId(), key -> new ArrayList<>()).add(rule);
        }

        return new TemplateStructure(groups, items, itemsByGroup, levelRulesByItem, authorRulesByLevelRule);
    }

    private Long resolveTemplateItemGroupKey(ExtraScoreTemplateItem item, Set<Long> validGroupIds) {
        Long groupId = item.getGroupId();
        if (groupId != null && validGroupIds.contains(groupId)) {
            return groupId;
        }
        return UNGROUPED_TEMPLATE_GROUP_ID;
    }

    private ExtraScoreTemplateGroup createUngroupedTemplateGroup(Long templateId, int sortOrder) {
        ExtraScoreTemplateGroup group = new ExtraScoreTemplateGroup();
        group.setId(UNGROUPED_TEMPLATE_GROUP_ID);
        group.setTemplateId(templateId);
        group.setGroupName(UNGROUPED_TEMPLATE_GROUP_NAME);
        group.setSameEventBestOnly(false);
        group.setSortOrder(sortOrder * 10);
        return group;
    }

    private String normalizeScoreType(String scoreType) {
        if (scoreType == null) {
            throw new IllegalArgumentException("模板类型不能为空");
        }
        String normalized = scoreType.trim().toUpperCase();
        if (!SCORE_TYPES.contains(normalized)) {
            throw new IllegalArgumentException("模板类型无效");
        }
        return normalized;
    }

    private String normalizeStatus(String status) {
        String normalized = status == null || status.isBlank() ? "active" : status.trim().toLowerCase();
        if (!List.of("active", "inactive").contains(normalized)) {
            throw new IllegalArgumentException("模板状态无效");
        }
        return normalized;
    }

    private String normalizeScoreDirection(String scoreDirection) {
        String normalized = scoreDirection == null || scoreDirection.isBlank() ? "add" : scoreDirection.trim().toLowerCase();
        if (!List.of("add", "deduct").contains(normalized)) {
            throw new IllegalArgumentException("分值方向无效");
        }
        return normalized;
    }

    private String normalizeAuthorRuleMode(String mode) {
        String normalized = mode == null || mode.isBlank() ? "ratio" : mode.trim().toLowerCase();
        if (!List.of("ratio", "fixed").contains(normalized)) {
            throw new IllegalArgumentException("作者规则类型无效");
        }
        return normalized;
    }

    private Long resolveTeacherIdForStudentView(RequestUserContext context) {
        if (context.isTeacher()) {
            return context.getUserId();
        }
        if (context.getTeacherId() != null) {
            return context.getTeacherId();
        }
        if (context.getClassName() != null) {
            Optional<ClassInfo> classInfo = classInfoRepository.findByName(context.getClassName());
            if (classInfo.isPresent()) {
                return classInfo.get().getTeacherId();
            }
        }
        if (context.getStudentNo() != null) {
            Optional<Student> student = studentRepository.findByStudentNo(context.getStudentNo());
            if (student.isPresent()) {
                return student.get().getTeacherId();
            }
        }
        if (context.getUserId() != null) {
            Optional<Student> student = studentRepository.findByUserId(context.getUserId());
            if (student.isPresent()) {
                return student.get().getTeacherId();
            }
        }
        return null;
    }

    private Long resolveTeacherIdForAssignment(RequestUserContext context) {
        if (!context.isTeacher()) {
            throw new IllegalArgumentException("仅教师账号可以选择当前使用的模板");
        }
        if (context.getUserId() == null) {
            throw new IllegalArgumentException("当前教师账号无效，无法选择模板");
        }
        return context.getUserId();
    }

    private void validateTemplatePermission(ExtraScoreTemplate template, RequestUserContext context) {
        if (context.isAdmin()) {
            return;
        }
        if (context.isTeacher() && context.getUserId() != null && context.getUserId().equals(template.getCreatorId())) {
            return;
        }
        throw new IllegalArgumentException("当前用户无权修改该模板");
    }

    private String defaultString(String value) {
        return value == null ? "" : value.trim();
    }

    private Integer normalizePositive(Integer value, String errorMessage) {
        if (value == null) {
            return null;
        }
        if (value <= 0) {
            return null;
        }
        return value;
    }

    private Double normalizeOptionalPositive(Double value, String errorMessage) {
        if (value == null) {
            return null;
        }
        if (value <= 0) {
            return null;
        }
        return value;
    }

    private Integer normalizeNonNegative(Integer value, int fallback) {
        if (value == null) {
            return fallback;
        }
        if (value < 0) {
            throw new IllegalArgumentException("排序值不能小于 0");
        }
        return value;
    }

    private <T> List<T> safeList(List<T> list) {
        return list == null ? List.of() : list;
    }

    private record TemplateStructure(
            List<ExtraScoreTemplateGroup> groups,
            List<ExtraScoreTemplateItem> items,
            Map<Long, List<ExtraScoreTemplateItem>> itemsByGroup,
            Map<Long, List<ExtraScoreTemplateItemLevelRule>> levelRulesByItem,
            Map<Long, List<ExtraScoreTemplateItemAuthorRule>> authorRulesByLevelRule
    ) {
    }
}
