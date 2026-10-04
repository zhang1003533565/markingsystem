package com.markingsystem.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.markingsystem.dto.StudentAnalysisResultDTO;
import com.markingsystem.dto.TimeMachineRequestDTO;
import com.markingsystem.dto.TimeMachineResultDTO;
import com.markingsystem.exception.RateLimitException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * AI 时光机（反事实未来推演）服务。
 *
 * <p>基于学生真实画像数据（复用 {@link StudentAnalysisService} 的画像分析结果），
 * 叠加学生设定的「如果……」假设参数，推演未来各发展路径的概率变化、八维雷达变化、
 * 未来时间线与 AI 点评。纯实时计算，不落库。
 *
 * <p>DeepSeek 不可用时自动降级为规则引擎推算（雷达与概率均本地计算），保证功能始终可用。
 */
@Service
public class TimeMachineService {

    private static final Logger log = LoggerFactory.getLogger(TimeMachineService.class);

    private final StudentAnalysisService studentAnalysisService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final String deepSeekApiKey;
    private final String deepSeekBaseUrl;
    private final String deepSeekModel;
    private final StringRedisTemplate redisTemplate;
    private final int dailyQuota;

    private static final String QUOTA_KEY_PREFIX = "ai:timemachine:quota:";

    public TimeMachineService(
            StudentAnalysisService studentAnalysisService,
            ObjectMapper objectMapper,
            StringRedisTemplate redisTemplate,
            @Value("${ai.deepseek.api-key:}") String deepSeekApiKey,
            @Value("${ai.deepseek.base-url:https://api.deepseek.com}") String deepSeekBaseUrl,
            @Value("${ai.deepseek.model:deepseek-chat}") String deepSeekModel,
            @Value("${ai.timemachine.daily-quota:10}") int dailyQuota,
            @Value("${ai.analysis.connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${ai.analysis.read-timeout-ms:20000}") int readTimeoutMs) {
        this.studentAnalysisService = studentAnalysisService;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
        this.deepSeekApiKey = deepSeekApiKey;
        this.deepSeekBaseUrl = deepSeekBaseUrl;
        this.deepSeekModel = deepSeekModel;
        this.dailyQuota = dailyQuota;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        this.restTemplate = new RestTemplate(requestFactory);
    }

    /**
     * 执行一次时光机推演。
     *
     * @return 结果；找不到学生或无法生成画像时返回 empty
     */
    public Optional<TimeMachineResultDTO> simulate(String studentNo, Long userId, TimeMachineRequestDTO request) {
        checkAndIncrementQuota(userId);
        Optional<StudentAnalysisResultDTO> analysisOpt = resolveAnalysis(studentNo, userId);
        if (analysisOpt.isEmpty()) {
            return Optional.empty();
        }

        StudentAnalysisResultDTO analysis = analysisOpt.get();
        TimeMachineResultDTO result = new TimeMachineResultDTO();
        result.setPrimaryType(analysis.getPrimaryType());
        result.setCurrentRadar(new LinkedHashMap<>(analysis.getRadarDimensions()));
        Map<String, Double> simulatedRadar = applyRadarAdjustments(analysis.getRadarDimensions(), request);
        result.setSimulatedRadar(simulatedRadar);
        result.setAdjustmentSummary(buildAdjustmentSummary(request));

        Map<String, Object> simulatedSnapshot = buildSimulatedSnapshot(analysis.getFeatureSnapshot(), request);

        if (deepSeekEnabled()) {
            try {
                TimeMachineResultDTO llmResult = callDeepSeek(analysis, simulatedSnapshot, request, simulatedRadar);
                llmResult.setPrimaryType(result.getPrimaryType());
                llmResult.setCurrentRadar(result.getCurrentRadar());
                llmResult.setSimulatedRadar(result.getSimulatedRadar());
                llmResult.setAdjustmentSummary(result.getAdjustmentSummary());
                return Optional.of(llmResult);
            } catch (Exception ex) {
                log.warn("AI 时光机 DeepSeek 调用失败，走规则兜底: {}", ex.getMessage());
            }
        }

        fillRuleFallback(result, analysis, request);
        return Optional.of(result);
    }

    /**
     * 按用户 + 日期做每日推演额度限流（Redis 计数，键内带日期，天然按天失效）。
     * 时光机每次推演都会调用 DeepSeek，需防止无限制调用造成成本失控。
     */
    private void checkAndIncrementQuota(Long userId) {
        if (userId == null || dailyQuota <= 0) {
            return;
        }
        try {
            String key = QUOTA_KEY_PREFIX + userId + ":" + LocalDate.now();
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                redisTemplate.expire(key, 1, TimeUnit.DAYS);
            }
            if (count != null && count > dailyQuota) {
                throw new RateLimitException("今日时光机推演次数已用完（" + dailyQuota + " 次/天），请明天再试");
            }
        } catch (RateLimitException e) {
            throw e;
        } catch (Exception e) {
            // Redis 异常时限流降级为不阻断，保证功能可用
            log.warn("时光机额度计数失败，本次不限流: {}", e.getMessage());
        }
    }

    private Optional<StudentAnalysisResultDTO> resolveAnalysis(String studentNo, Long userId) {
        Optional<StudentAnalysisResultDTO> existing = studentAnalysisService.getLatestAnalysis(studentNo, userId);
        if (existing.isPresent()) {
            return existing;
        }
        // 第一次使用时光机时自动生成画像（复用画像分析，含规则兜底，不会失败）
        return studentAnalysisService.analyzeStudent(studentNo, userId);
    }

    /* ---------------- 规则引擎部分 ---------------- */

    private Map<String, Double> applyRadarAdjustments(Map<String, Double> current, TimeMachineRequestDTO request) {
        Map<String, Double> adjusted = new LinkedHashMap<>(current);
        double scoreDelta = request.scoreDeltaValue();
        int competition = request.competitionCountValue();
        int practice = request.practiceCountValue();
        int certs = request.certCountValue();

        bump(adjusted, "学业成绩", scoreDelta * 1.5);
        bump(adjusted, "发展潜力", scoreDelta * 1.0);
        bump(adjusted, "综合素质", scoreDelta * 0.6);
        bump(adjusted, "科研创新", competition * 10D);
        bump(adjusted, "技能素养", competition * 6D + certs * 5D);
        bump(adjusted, "实践服务", practice * 8D);
        bump(adjusted, "思想品德", practice * 4D);
        bump(adjusted, "综合素质", certs * 3D);
        return adjusted;
    }

    private void bump(Map<String, Double> radar, String key, double delta) {
        if (delta <= 0) {
            return;
        }
        double value = radar.getOrDefault(key, 0D) + delta;
        radar.put(key, Math.max(0D, Math.min(100D, Math.round(value * 10D) / 10D)));
    }

    private Map<String, Object> buildSimulatedSnapshot(Map<String, Object> current, TimeMachineRequestDTO request) {
        Map<String, Object> snapshot = new LinkedHashMap<>(current == null ? Map.of() : current);
        double scoreDelta = request.scoreDeltaValue();
        Map<String, Object> scenario = new LinkedHashMap<>();
        scenario.put("scoreDelta", scoreDelta);
        scenario.put("competitionCount", request.competitionCountValue());
        scenario.put("practiceCount", request.practiceCountValue());
        scenario.put("certCount", request.certCountValue());
        snapshot.put("scenarioAdjustments", scenario);

        if (current != null && current.get("courseAverage") instanceof Number courseAverage) {
            snapshot.put("courseAverageAdjusted", roundOne(courseAverage.doubleValue() + scoreDelta));
        }
        if (current != null && current.get("competitionCount") instanceof Number competition) {
            snapshot.put("competitionCountAdjusted", competition.intValue() + request.competitionCountValue());
        }
        if (current != null && current.get("approvedExtraScore") instanceof Number extraScore) {
            snapshot.put("approvedExtraScoreAdjusted", roundOne(extraScore.doubleValue() + request.practiceCountValue() * 2D));
        }
        return snapshot;
    }

    private String buildAdjustmentSummary(TimeMachineRequestDTO request) {
        List<String> parts = new ArrayList<>();
        if (request.scoreDeltaValue() > 0) {
            parts.add("均分提升 " + trimDouble(request.scoreDeltaValue()) + " 分");
        }
        if (request.competitionCountValue() > 0) {
            parts.add("多参加 " + request.competitionCountValue() + " 项竞赛");
        }
        if (request.practiceCountValue() > 0) {
            parts.add("多参加 " + request.practiceCountValue() + " 项实践/志愿活动");
        }
        if (request.certCountValue() > 0) {
            parts.add("多考取 " + request.certCountValue() + " 本证书");
        }
        return parts.isEmpty() ? "不做任何调整（维持现状）" : String.join("、", parts);
    }

    private void fillRuleFallback(TimeMachineResultDTO result, StudentAnalysisResultDTO analysis, TimeMachineRequestDTO request) {
        result.setPaths(buildRulePaths(analysis.getPrimaryType(), request));
        result.setTimeline(buildRuleTimeline(analysis, request));
        result.setSummary(buildRuleSummary(analysis.getPrimaryType(), request));
        result.setSuggestions(buildRuleSuggestions(analysis, request, result.getSimulatedRadar()));
        result.setSource("rules");
        result.setModel("rule-engine");
    }

    private List<TimeMachineResultDTO.PathProbability> buildRulePaths(String primaryType, TimeMachineRequestDTO request) {
        Map<String, Integer> base = switch (primaryType == null ? "" : primaryType) {
            case "考公" -> new LinkedHashMap<>(Map.of("考研", 40, "考公", 70, "竞赛发展", 30, "直接就业", 35));
            case "竞赛型人才" -> new LinkedHashMap<>(Map.of("考研", 45, "考公", 25, "竞赛发展", 75, "直接就业", 30));
            default -> new LinkedHashMap<>(Map.of("考研", 70, "考公", 35, "竞赛发展", 45, "直接就业", 30));
        };

        double scoreDelta = request.scoreDeltaValue();
        int competition = request.competitionCountValue();
        int practice = request.practiceCountValue();
        int certs = request.certCountValue();
        Map<String, Integer> increments = new LinkedHashMap<>();
        increments.put("考研", (int) Math.round(scoreDelta * 2D));
        increments.put("考公", practice * 5);
        increments.put("竞赛发展", competition * 8);
        increments.put("直接就业", certs * 4 + (int) Math.round(scoreDelta * 1D));

        Map<String, Integer> currentValues = normalizePathValues(base);
        Map<String, Integer> simulatedRaw = new LinkedHashMap<>(base);
        increments.forEach((name, increment) -> simulatedRaw.put(name, base.get(name) + increment));
        Map<String, Integer> simulatedValues = normalizePathValues(simulatedRaw);

        List<TimeMachineResultDTO.PathProbability> paths = new ArrayList<>();
        for (String name : List.of("考研", "考公", "竞赛发展", "直接就业")) {
            TimeMachineResultDTO.PathProbability path = new TimeMachineResultDTO.PathProbability();
            path.setName(name);
            path.setCurrent(currentValues.get(name));
            path.setSimulated(simulatedValues.get(name));
            path.setReason(switch (name) {
                case "考研" -> scoreDelta > 0 ? "成绩提升直接增强升学竞争力" : "维持现状";
                case "竞赛发展" -> competition > 0 ? "新增竞赛经历扩大保研/就业筹码" : "维持现状";
                case "考公" -> practice > 0 ? "实践与志愿服务经历贴合考公画像" : "维持现状";
                default -> certs > 0 ? "证书让求职更有抓手" : "维持现状";
            });
            paths.add(path);
        }
        return paths;
    }

    private Map<String, Integer> normalizePathValues(Map<String, Integer> values) {
        List<String> names = List.of("考研", "考公", "竞赛发展", "直接就业");
        Map<String, Integer> weights = new LinkedHashMap<>();
        names.forEach(name -> weights.put(name, Math.max(0, values.getOrDefault(name, 0))));
        int total = weights.values().stream().mapToInt(Integer::intValue).sum();
        Map<String, Integer> normalized = new LinkedHashMap<>();
        if (total == 0) {
            names.forEach(name -> normalized.put(name, 25));
            return normalized;
        }

        Map<String, Double> remainders = new LinkedHashMap<>();
        int assigned = 0;
        for (String name : names) {
            double exactValue = weights.get(name) * 100D / total;
            int floorValue = (int) Math.floor(exactValue);
            normalized.put(name, floorValue);
            remainders.put(name, exactValue - floorValue);
            assigned += floorValue;
        }

        int remainderUnits = 100 - assigned;
        remainders.entrySet().stream()
                .sorted((left, right) -> Double.compare(right.getValue(), left.getValue()))
                .limit(remainderUnits)
                .forEach(entry -> normalized.put(entry.getKey(), normalized.get(entry.getKey()) + 1));
        return normalized;
    }

    private List<TimeMachineResultDTO.TimelineEvent> buildRuleTimeline(StudentAnalysisResultDTO analysis, TimeMachineRequestDTO request) {
        List<TimeMachineResultDTO.TimelineEvent> timeline = new ArrayList<>();
        double scoreDelta = request.scoreDeltaValue();
        int competition = request.competitionCountValue();
        int practice = request.practiceCountValue();
        int certs = request.certCountValue();
        List<String> weakSubjects = extractSubjectNames(analysis.getFeatureSnapshot().get("weakSubjects"), 3);
        List<String> topSubjects = extractSubjectNames(analysis.getFeatureSnapshot().get("topSubjects"), 3);

        String stage1Desc;
        if (scoreDelta > 0) {
            if (!weakSubjects.isEmpty()) {
                stage1Desc = "目标：平均分提升 " + trimDouble(scoreDelta) + " 分。重点补弱：" + String.join("、", weakSubjects)
                        + "，为每门课定目标分，安排每周固定复习时段（刷题 + 答疑），期末前逐科复盘对照成绩单";
            } else {
                stage1Desc = "目标：平均分提升 " + trimDouble(scoreDelta) + " 分。按学期拆解到各科：每门课设目标分，每周固定复习时段，期末前完成一次全科复盘";
            }
            if (!topSubjects.isEmpty()) {
                stage1Desc += "；优势科目（" + String.join("、", topSubjects) + "）保持投入节奏，防止滑坡";
            }
            stage1Desc += "。";
        } else {
            stage1Desc = topSubjects.isEmpty()
                    ? "维持当前学习节奏，按周复盘各科进度，盯住平均分不滑坡。"
                    : "维持当前学习节奏，优势科目（" + String.join("、", topSubjects) + "）保持投入，其余科目按周复盘防止滑坡。";
        }
        addTimeline(timeline, "本学年", "稳住学业基线", stage1Desc);

        if (competition > 0) {
            addTimeline(timeline, "下一学年", "竞赛窗口期",
                    "参加 " + competition + " 项与专业相关的竞赛（如挑战杯、数学建模、蓝桥杯等），"
                            + "提前 2~3 个月组队备赛，按获奖目标倒排训练计划；获奖后及时申报附加分，同步增益综测、保研与简历");
        } else if (practice > 0) {
            addTimeline(timeline, "下一学年", "实践积累期",
                    "参加 " + practice + " 项社会实践或志愿服务（如三下乡、社区志愿、学生工作），"
                            + "每次活动留存证明与总结，按期申报道德实践类附加分");
        } else {
            addTimeline(timeline, "下一学年", "纵深积累", "围绕画像主导方向持续积累：专业课、证书或实践活动，任选一条主线深耕，期末前形成阶段性成果");
        }

        if (certs > 0) {
            String certText = switch (analysis.getPrimaryType() == null ? "" : analysis.getPrimaryType()) {
                case "考公" -> "优先考取与本专业相关的职业资格证书，同步准备四六级与计算机等级证书";
                default -> "优先通过英语六级，再按考研方向考取专业证书（如软考、教资、职业资格）";
            };
            addTimeline(timeline, "下一学年至再下学年", "证书备考",
                    "考取 " + certs + " 本证书：" + certText + "。按报名时间提前 2 个月备考，集中刷真题，"
                            + "证书到手后补充进简历并申报附加分");
        }

        String primaryType = analysis.getPrimaryType();
        String stage3Title;
        String stage3Desc;
        if ("考公".equals(primaryType)) {
            stage3Title = "毕业季";
            stage3Desc = "把实践与志愿服务经历整理进报考材料，研究目标岗位近 3 年招录要求与分数线，"
                    + "同步关注基层项目与选调渠道，笔试按模块刷题、面试提前模拟";
        } else if ("竞赛型人才".equals(primaryType)) {
            stage3Title = "毕业季";
            stage3Desc = "用竞赛与项目成果冲击保研/优质岗位：整理获奖与作品集，准备 1 分钟自我介绍与项目追问，"
                    + "同步关注保研夏令营与校招时间节点";
        } else {
            stage3Title = "毕业季";
            stage3Desc = "基于成绩优势冲刺考研：结合成绩与兴趣圈定目标院校专业，按公共课 + 专业课双线制定备考计划，"
                    + "同步留一条就业/保研备选路线";
        }
        addTimeline(timeline, "冲刺与选择", stage3Title, stage3Desc);
        return timeline;
    }

    private void addTimeline(List<TimeMachineResultDTO.TimelineEvent> timeline, String stage, String title, String description) {
        TimeMachineResultDTO.TimelineEvent event = new TimeMachineResultDTO.TimelineEvent();
        event.setStage(stage);
        event.setTitle(title);
        event.setDescription(description);
        timeline.add(event);
    }

    private String buildRuleSummary(String primaryType, TimeMachineRequestDTO request) {
        String typeText = switch (primaryType == null ? "" : primaryType) {
            case "考公" -> "考公方向";
            case "竞赛型人才" -> "竞赛型人才方向";
            default -> "考研方向";
        };
        if (!request.hasAnyAdjustment()) {
            return "当前画像偏向" + typeText + "。若保持现状，建议围绕现有优势稳步推进，同时保留 1~2 条备选路线。";
        }
        return "叠加假设调整后，学业与实践筹码同步增厚，" + typeText + "的竞争力进一步提升。建议把推演中的关键动作落进学期计划，定期复盘对照。";
    }

    private List<String> buildRuleSuggestions(StudentAnalysisResultDTO analysis, TimeMachineRequestDTO request, Map<String, Double> simulatedRadar) {
        List<String> suggestions = new ArrayList<>();
        List<String> weakSubjects = extractSubjectNames(analysis.getFeatureSnapshot().get("weakSubjects"), 3);
        if (!weakSubjects.isEmpty()) {
            suggestions.add("先攻薄弱科目：" + String.join("、", weakSubjects) + "，为每门课定目标分并安排每周固定复习时段，期末前完成一次摸底对照。");
        } else if (request.scoreDeltaValue() > 0) {
            suggestions.add("按学期把平均分目标拆到各科：每门课设目标分，每周固定复习时段，期末复盘对照。");
        }

        List<String> lowDimensions = lowestRadarDimensions(simulatedRadar, 2);
        if (!lowDimensions.isEmpty()) {
            suggestions.add("针对性补强画像短板（" + String.join("、", lowDimensions) + "）：一个学期主攻一项，期末回来重新推演，对照前后变化。");
        }

        if (request.competitionCountValue() > 0) {
            suggestions.add("竞赛从 1 项高质量的做起：选与专业相关的赛项，提前 2~3 个月组队备赛，获奖后及时申报附加分。");
        }
        if (request.certCountValue() > 0) {
            suggestions.add("证书按考研/就业方向取舍：优先英语六级或专业核心证书，按报名时间提前 2 个月备考。");
        }
        suggestions.add("每学期末回来重新推演一次，用真实成绩与活动数据校准路径判断，及时调整下阶段重点。");
        return suggestions;
    }

    /** 从画像快照中提取科目名（含均分），兼容 List<SubjectStat> 与 JSON 反序列化后的 List<Map>。 */
    private List<String> extractSubjectNames(Object value, int limit) {
        List<String> names = new ArrayList<>();
        if (!(value instanceof List<?> list)) {
            return names;
        }
        for (Object item : list) {
            if (names.size() >= limit) {
                break;
            }
            try {
                JsonNode node = objectMapper.valueToTree(item);
                String name = node.path("subjectName").asText("");
                double average = node.path("averageScore").asDouble(0);
                if (!name.isBlank()) {
                    names.add(average > 0 ? name + "（" + trimDouble(average) + " 分）" : name);
                }
            } catch (Exception ignored) {
                // 单个科目解析失败不影响整体
            }
        }
        return names;
    }

    /** 取推演后雷达中数值最低的若干维度名。 */
    private List<String> lowestRadarDimensions(Map<String, Double> radar, int limit) {
        List<Map.Entry<String, Double>> entries = new ArrayList<>(radar.entrySet());
        entries.sort(Map.Entry.comparingByValue());
        List<String> names = new ArrayList<>();
        for (Map.Entry<String, Double> entry : entries) {
            if (names.size() >= limit) {
                break;
            }
            if (entry.getValue() != null) {
                names.add(entry.getKey());
            }
        }
        return names;
    }

    /* ---------------- DeepSeek 部分 ---------------- */

    private boolean deepSeekEnabled() {
        return deepSeekApiKey != null && !deepSeekApiKey.isBlank();
    }

    private TimeMachineResultDTO callDeepSeek(
            StudentAnalysisResultDTO analysis,
            Map<String, Object> simulatedSnapshot,
            TimeMachineRequestDTO request,
            Map<String, Double> simulatedRadar) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(deepSeekApiKey);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", deepSeekModel);
        body.put("stream", false);
        body.put("messages", List.of(
                Map.of(
                        "role", "system",
                        "content", "你是高校学生发展推演助手。你基于学生真实画像与假设参数，输出未来路径推演，必须输出严格 JSON。"
                ),
                Map.of(
                        "role", "user",
                        "content", buildPrompt(analysis, simulatedSnapshot, request, simulatedRadar)
                )
        ));

        JsonNode response = restTemplate.postForObject(
                deepSeekBaseUrl + "/chat/completions",
                new HttpEntity<>(body, headers),
                JsonNode.class
        );
        if (response == null) {
            throw new IllegalStateException("deepseek empty response");
        }
        String content = response.path("choices").path(0).path("message").path("content").asText();
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("deepseek content empty");
        }

        JsonNode jsonNode = objectMapper.readTree(unwrapJsonBlock(content));
        TimeMachineResultDTO result = new TimeMachineResultDTO();
        result.setPaths(parsePaths(jsonNode.path("paths")));
        if (result.getPaths().size() != 4) {
            throw new IllegalStateException("deepseek paths incomplete");
        }
        result.setTimeline(parseTimeline(jsonNode.path("timeline")));
        result.setSummary(jsonNode.path("summary").asText(""));
        result.setSuggestions(parseStringList(jsonNode.path("suggestions")));
        result.setSource("deepseek");
        result.setModel(deepSeekModel);
        return result;
    }

    private String buildPrompt(StudentAnalysisResultDTO analysis, Map<String, Object> simulatedSnapshot, TimeMachineRequestDTO request, Map<String, Double> simulatedRadar) throws Exception {
        return """
                请基于学生的真实画像和用户的「如果……」假设，做一次未来路径推演。

                约束：
                1. 只能基于输入的真实数据与假设参数展开，禁止编造实际经历；数据缺失时给出通用但可执行的动作与工具，不要虚构数据中不存在的具体经历。
                2. 输出严格 JSON，不要输出 Markdown 或多余文字。
                3. paths 必须包含恰好四类：考研、考公、竞赛发展、直接就业；current 为当前概率，simulated 为叠加假设后的概率，均为 0~100 整数。
                4. reason 简短说明某个路径概率变化的原因，要结合真实数据（如课程、竞赛、实践数量）与假设参数，不要泛泛而谈。
                5. timeline 输出 4~5 条，按时间先后；stage 是时间标签，title 是事件标题，description 必须写成该阶段的具体行动方案：点名输入数据中出现的薄弱课程（含当前分数）并给出目标分与复习方法（如每周固定时段刷题、真题、答疑）；竞赛写明具体赛项与备赛节奏（组队、倒排训练计划、申报附加分）；证书写明具体证书（四六级、软考、教资等）与备考时间窗。数据缺失时给出可执行步骤，禁止"提升成绩""增强能力"这类无抓手表述。
                6. suggestions 输出 3~5 条，每条必须 = 具体动作 + 可量化目标 + 时间窗口，按优先级排序；针对输入雷达中最低的 1~2 个维度给出补强动作；不得与 timeline 简单重复，不得出现无抓手套话。
                7. summary 一句话总结假设下的整体判断。
                8. 文本内容不得出现英文字段名或变量名（如 courseAverage、competitionCount 等）。
                9. 涉及「直接就业」路径的 reason、timeline、suggestions 时，默认以软件工程/互联网研发岗位为导向（后端、前端、测试、运维、算法工程等），不要主动导向销售、行政、硬件产线等非软件岗位。

                输出 JSON 结构：
                {
                  "paths": [
                    {"name": "考研", "current": 70, "simulated": 78, "reason": "原因"},
                    {"name": "考公", "current": 35, "simulated": 36, "reason": "原因"},
                    {"name": "竞赛发展", "current": 45, "simulated": 53, "reason": "原因"},
                    {"name": "直接就业", "current": 30, "simulated": 33, "reason": "原因"}
                  ],
                  "timeline": [
                    {"stage": "本学年", "title": "标题", "description": "具体行动方案"}
                  ],
                  "summary": "一句话点评",
                  "suggestions": ["建议1", "建议2", "建议3"]
                }

                学生当前画像主导类型：%s
                当前画像摘要：
                %s

                假设参数（scenarioAdjustments 字段）：%s
                叠加假设后的特征快照：
                %s

                当前八维雷达：
                %s

                推演后八维雷达（叠加假设后）：
                %s
                """.formatted(
                analysis.getPrimaryType() == null ? "考研" : analysis.getPrimaryType(),
                analysis.getFeatureSnapshot().isEmpty()
                        ? "{}"
                        : objectMapper.writeValueAsString(analysis.getFeatureSnapshot()),
                objectMapper.writeValueAsString(simulatedSnapshot.get("scenarioAdjustments")),
                simulatedSnapshot.isEmpty() ? "{}" : objectMapper.writeValueAsString(simulatedSnapshot),
                analysis.getRadarDimensions().isEmpty()
                        ? "{}"
                        : objectMapper.writeValueAsString(analysis.getRadarDimensions()),
                simulatedRadar.isEmpty() ? "{}" : objectMapper.writeValueAsString(simulatedRadar)
        );
    }

    private List<TimeMachineResultDTO.PathProbability> parsePaths(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> names = List.of("考研", "考公", "竞赛发展", "直接就业");
        Map<String, Integer> currentValues = new LinkedHashMap<>();
        Map<String, Integer> simulatedValues = new LinkedHashMap<>();
        for (JsonNode item : node) {
            String name = item.path("name").asText("");
            if (!names.contains(name)) {
                continue;
            }
            currentValues.put(name, clampInt(item.path("current").asInt(50), 0, 100));
            simulatedValues.put(name, clampInt(item.path("simulated").asInt(50), 0, 100));
        }
        if (currentValues.size() != names.size() || simulatedValues.size() != names.size()) {
            return List.of();
        }

        Map<String, Integer> normalizedCurrent = normalizePathValues(currentValues);
        Map<String, Integer> normalizedSimulated = normalizePathValues(simulatedValues);
        List<TimeMachineResultDTO.PathProbability> paths = new ArrayList<>();
        for (JsonNode item : node) {
            String name = item.path("name").asText("");
            if (!names.contains(name) || paths.stream().anyMatch(path -> name.equals(path.getName()))) {
                continue;
            }
            TimeMachineResultDTO.PathProbability path = new TimeMachineResultDTO.PathProbability();
            path.setName(name);
            path.setCurrent(normalizedCurrent.get(name));
            path.setSimulated(normalizedSimulated.get(name));
            path.setReason(item.path("reason").asText(""));
            paths.add(path);
        }
        return paths;
    }

    private List<TimeMachineResultDTO.TimelineEvent> parseTimeline(JsonNode node) {
        List<TimeMachineResultDTO.TimelineEvent> timeline = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return timeline;
        }
        for (JsonNode item : node) {
            TimeMachineResultDTO.TimelineEvent event = new TimeMachineResultDTO.TimelineEvent();
            event.setStage(item.path("stage").asText(""));
            event.setTitle(item.path("title").asText(""));
            event.setDescription(item.path("description").asText(""));
            timeline.add(event);
        }
        return timeline;
    }

    private List<String> parseStringList(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return values;
        }
        node.forEach(item -> {
            if (!item.isNull()) {
                values.add(item.asText());
            }
        });
        return values;
    }

    private String unwrapJsonBlock(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            int firstLineBreak = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstLineBreak > -1 && lastFence > firstLineBreak) {
                return trimmed.substring(firstLineBreak + 1, lastFence).trim();
            }
        }
        return trimmed;
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double roundOne(double value) {
        return Math.round(value * 10D) / 10D;
    }

    private static String trimDouble(double value) {
        if (value == Math.floor(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(Math.round(value * 10D) / 10D);
    }
}
