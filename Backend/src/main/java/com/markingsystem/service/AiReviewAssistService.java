package com.markingsystem.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.markingsystem.entity.ExtraScoreApplication;
import com.markingsystem.repository.ExtraScoreApplicationRepository;
import com.markingsystem.service.llm.provider.OpenAiCompatibleProvider;
import com.markingsystem.service.llm.spi.ChatRequest;
import com.markingsystem.service.llm.spi.ChatResponse;
import com.markingsystem.service.llm.spi.LlmException;
import com.markingsystem.service.llm.spi.Message;
import com.markingsystem.util.RequestUserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * AI 审核助手：针对附加分申请生成「证书字段提取 + 结构化审核建议 + 疑点说明」。
 *
 * <p>复用 {@link OpenAiCompatibleProvider}（OpenAI 兼容视觉模型）一次调用完成识别与推理；
 * 视觉模型未配置 / 调用失败时降级为规则兜底（基于系统分值 + 材料链防伪信号），保证
 * 「AI 不可用 ≠ 审核不可用」。结果落 {@link ExtraScoreApplication#getAiSuggestionJson()}，
 * 教师终审时仅作参考，最终判定仍由人工完成。
 */
@Service
public class AiReviewAssistService {

    private static final Logger log = LoggerFactory.getLogger(AiReviewAssistService.class);

    private static final String SYSTEM_PROMPT = """
            你是高校综合素质测评「附加分」审核助手。根据证书图片、学生填写的申请信息和系统规则，输出结构化审核参考意见。

            必须严格遵守：
            1. 只输出一个 JSON 对象，不要输出 Markdown 代码块、解释或任何前后缀文字。
            2. 只能基于图片中可辨认的内容与给定信息判断；图片中看不到或不确定的字段用 null，禁止编造。
            3. 疑点（suspects）只列有明确依据的：证书图片与填写信息不一致、分值异常、材料存在篡改/重复风险等；没有疑点返回空数组。

            输出 JSON 结构：
            {
              "ocr": {"certName": "证书名称或null", "certNo": "证书编号或null", "levelName": "奖项级别或null", "awardTime": "获奖时间或null", "authorLabel": "作者/排名或null"},
              "suggestion": {"decision": "建议通过|建议驳回|需人工复核", "reason": "一句话理由", "scoreCheck": {"declared": 申报分值, "formula": "分值计算依据说明", "match": true}},
              "suspects": [{"type": "疑点类型", "level": "高|中|低", "detail": "具体说明"}],
              "confidence": "高|中|低"
            }
            """;

    private static final int MAX_IMAGE_BYTES = 4 * 1024 * 1024;
    private static final int MAX_IMAGES = 3;

    private final ExtraScoreApplicationRepository applicationRepository;
    private final ExtraScoreApplicationService extraScoreApplicationService;
    private final MaterialChainService materialChainService;
    private final ProofFileStorageService proofFileStorageService;
    private final ObjectMapper objectMapper;
    private final OpenAiCompatibleProvider visionProvider;

    public AiReviewAssistService(
            ExtraScoreApplicationRepository applicationRepository,
            ExtraScoreApplicationService extraScoreApplicationService,
            MaterialChainService materialChainService,
            ProofFileStorageService proofFileStorageService,
            ObjectMapper objectMapper,
            @Qualifier("visionModelProvider") OpenAiCompatibleProvider visionProvider) {
        this.applicationRepository = applicationRepository;
        this.extraScoreApplicationService = extraScoreApplicationService;
        this.materialChainService = materialChainService;
        this.proofFileStorageService = proofFileStorageService;
        this.objectMapper = objectMapper;
        this.visionProvider = visionProvider;
    }

    public Map<String, Object> assist(RequestUserContext context, Long applicationId) {
        ExtraScoreApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("申请记录不存在"));
        if (!extraScoreApplicationService.canAccessReviewApplication(context, application)) {
            throw new IllegalArgumentException("无权查看该申请记录");
        }

        List<Map<String, Object>> materialRisks = materialChainService.listByApplication(applicationId);

        Map<String, Object> result;
        if (!visionProvider.isEnabled()) {
            result = buildRuleBasedFallback(application, materialRisks, "视觉模型未配置（ai.vision.api-key 为空），已降级为规则审核");
        } else {
            result = callVisionModel(application, materialRisks);
        }

        persist(application, result);
        return result;
    }

    private Map<String, Object> callVisionModel(ExtraScoreApplication application, List<Map<String, Object>> materialRisks) {
        List<String> imageDataUrls = collectImageDataUrls(application);
        Message system = new Message("system", SYSTEM_PROMPT);
        Message user = new Message("user", buildUserPrompt(application, materialRisks));
        user.setImageUrls(imageDataUrls);

        ChatRequest request = new ChatRequest();
        request.setTemperature(0.2);
        request.setMaxTokens(1500);
        request.setMessages(List.of(system, user));

        try {
            ChatResponse response = visionProvider.chat(request);
            Map<String, Object> parsed = parseStructured(response == null ? null : response.getContent());
            parsed.put("mode", "ai");
            parsed.put("model", response == null ? null : response.getModel());
            parsed.put("generatedAt", LocalDateTime.now().toString());
            return parsed;
        } catch (LlmException e) {
            log.warn("AI 审核助手调用失败: {}", e.getMessage());
            return buildRuleBasedFallback(application, materialRisks, "视觉模型调用失败（" + e.getMessage() + "），已降级为规则审核");
        }
    }

    private Map<String, Object> buildRuleBasedFallback(
            ExtraScoreApplication application,
            List<Map<String, Object>> materialRisks,
            String note) {
        List<Map<String, Object>> suspects = new ArrayList<>();
        if (materialRisks != null) {
            for (Map<String, Object> risk : materialRisks) {
                String tamperRisk = defaultString(risk.get("tamperRisk")).toUpperCase(Locale.ROOT);
                if ("MEDIUM".equals(tamperRisk) || "HIGH".equals(tamperRisk)) {
                    Map<String, Object> suspect = new LinkedHashMap<>();
                    suspect.put("type", "材料篡改风险");
                    suspect.put("level", "MEDIUM".equals(tamperRisk) ? "中" : "高");
                    suspect.put("detail", defaultString(risk.get("tamperDetail")).isBlank() ? "材料链检测到图片异常" : risk.get("tamperDetail"));
                    suspects.add(suspect);
                }
                if ("DUPLICATE".equalsIgnoreCase(defaultString(risk.get("certDuplicateRisk")))) {
                    Map<String, Object> suspect = new LinkedHashMap<>();
                    suspect.put("type", "证书编号重复");
                    suspect.put("level", "高");
                    suspect.put("detail", defaultString(risk.get("certDuplicateDetail")).isBlank() ? "该证书编号已被其他学生提交" : risk.get("certDuplicateDetail"));
                    suspects.add(suspect);
                }
            }
        }

        double declared = application.getExpectedScore() == null ? 0D : application.getExpectedScore();
        Map<String, Object> scoreCheck = new LinkedHashMap<>();
        scoreCheck.put("declared", declared);
        scoreCheck.put("formula", "分值由系统规则在提交时计算（级别基础分 × 作者比例），此处为人工复核参考");
        scoreCheck.put("match", true);

        Map<String, Object> suggestion = new LinkedHashMap<>();
        suggestion.put("decision", suspects.isEmpty() ? "需人工复核" : "建议驳回");
        suggestion.put("reason", suspects.isEmpty() ? "未发现明确疑点，请结合证书原件人工确认" : "存在 " + suspects.size() + " 项疑点，建议人工核实");
        suggestion.put("scoreCheck", scoreCheck);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ocr", Map.of());
        result.put("suggestion", suggestion);
        result.put("suspects", suspects);
        result.put("confidence", "低");
        result.put("mode", "fallback");
        result.put("note", note);
        result.put("generatedAt", LocalDateTime.now().toString());
        return result;
    }

    private Map<String, Object> parseStructured(String content) {
        if (content == null || content.isBlank()) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("confidence", "低");
            result.put("note", "模型返回空内容");
            return result;
        }
        String cleaned = stripCodeFence(content.trim());
        try {
            return objectMapper.readValue(cleaned, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("raw", content);
            result.put("confidence", "低");
            result.put("note", "模型返回非结构化文本，已原样保留");
            return result;
        }
    }

    private String stripCodeFence(String content) {
        String text = content;
        if (text.startsWith("```")) {
            int firstNewline = text.indexOf('\n');
            if (firstNewline >= 0) {
                text = text.substring(firstNewline + 1);
            }
        }
        if (text.endsWith("```")) {
            text = text.substring(0, text.length() - 3);
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            text = text.substring(start, end + 1);
        }
        return text.trim();
    }

    private String buildUserPrompt(ExtraScoreApplication application, List<Map<String, Object>> materialRisks) {
        StringBuilder sb = new StringBuilder();
        sb.append("学生申请信息：\n");
        sb.append("- 学号/姓名/班级：").append(defaultString(application.getStudentNo())).append(" / ")
                .append(defaultString(application.getStudentName())).append(" / ")
                .append(defaultString(application.getClassName())).append("\n");
        sb.append("- 模板/大类/小类：").append(defaultString(application.getTemplateName())).append(" / ")
                .append(defaultString(application.getGroupName())).append(" / ")
                .append(defaultString(application.getItemName())).append("\n");
        sb.append("- 分值方向/级别/作者：").append(defaultString(application.getScoreDirection())).append(" / ")
                .append(defaultString(application.getLevelName())).append(" / ")
                .append(defaultString(application.getAuthorLabel())).append("\n");
        sb.append("- 申报分值：").append(application.getExpectedScore()).append("（基础分 ")
                .append(application.getBaseScore()).append("，作者比例 ")
                .append(application.getAuthorRatio()).append("）\n");
        sb.append("- 学生填写证书名称：").append(defaultString(application.getCertName())).append("\n");
        sb.append("- 学生填写获奖时间：").append(application.getAwardTime() == null ? "-" : application.getAwardTime()).append("\n");
        sb.append("- 具体说明：").append(defaultString(application.getDetailDesc())).append("\n");
        sb.append("- 文字凭证：").append(defaultString(application.getProofText())).append("\n");

        if (materialRisks != null && !materialRisks.isEmpty()) {
            sb.append("\n材料链防伪信号：\n");
            for (Map<String, Object> risk : materialRisks) {
                sb.append("- 篡改风险 ").append(defaultString(risk.get("tamperRisk")))
                        .append("：").append(defaultString(risk.get("tamperDetail"))).append("；")
                        .append("证书重复 ").append(defaultString(risk.get("certDuplicateRisk")))
                        .append("：").append(defaultString(risk.get("certDuplicateDetail"))).append("\n");
            }
        } else {
            sb.append("\n材料链防伪信号：无\n");
        }

        sb.append("\n请根据证书图片和以上信息，按 system 要求输出 JSON。");
        return sb.toString();
    }

    private List<String> collectImageDataUrls(ExtraScoreApplication application) {
        Set<String> paths = new LinkedHashSet<>();
        paths.addAll(splitStored(application.getCertFileUrl()));
        paths.addAll(splitStored(application.getProofImages()));
        List<String> urls = new ArrayList<>();
        for (String path : paths) {
            if (urls.size() >= MAX_IMAGES) {
                break;
            }
            String dataUrl = toDataUrl(path);
            if (dataUrl != null) {
                urls.add(dataUrl);
            }
        }
        return urls;
    }

    private String toDataUrl(String value) {
        String path = defaultString(value).trim();
        if (path.isBlank()) {
            return null;
        }
        if (path.startsWith("data:")) {
            return path;
        }
        try (InputStream in = proofFileStorageService.open(path)) {
            if (in == null) {
                return null;
            }
            byte[] bytes = in.readNBytes(MAX_IMAGE_BYTES + 1);
            if (bytes.length == 0 || bytes.length > MAX_IMAGE_BYTES) {
                return null;
            }
            return "data:" + resolveImageMime(path) + ";base64," + Base64.getEncoder().encodeToString(bytes);
        } catch (IOException e) {
            log.warn("读取凭证图片失败，跳过: path={}, reason={}", path, e.getMessage());
            return null;
        }
    }

    private String resolveImageMime(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".bmp")) {
            return "image/bmp";
        }
        return "image/jpeg";
    }

    private void persist(ExtraScoreApplication application, Map<String, Object> result) {
        try {
            application.setAiSuggestionJson(objectMapper.writeValueAsString(result));
        } catch (Exception e) {
            log.warn("AI 审核建议序列化失败: {}", e.getMessage());
            application.setAiSuggestionJson(null);
        }
        application.setAiReviewedAt(LocalDateTime.now());
        applicationRepository.save(application);
    }

    private List<String> splitStored(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String item : value.split("\\|\\|")) {
            String trimmed = item.trim();
            if (!trimmed.isBlank()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private String defaultString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
