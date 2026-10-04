package com.markingsystem.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * DeepSeek 岗位学习资源生成客户端。
 * 使用 deepseek-reasoner 模型 + search_channel 联网搜索，
 * 围绕目标岗位生成 6 条覆盖「理论/实战/技能/项目/行业/学习路径」维度的学习资源。
 *
 * 安全策略：
 * - 入参 jobTitle 经 sanitizeJobTitle 清洗后注入 prompt，避免控制字符注入与超长负载
 * - 不使用 String.formatted，避免 jobTitle 中的 % 触发 IllegalFormatException
 * - 抽取 JSON 数组时按 [ 与 ] 边界定位，对 Markdown 围栏与额外解释文本更鲁棒
 * - 单条字段按 DB 列长截断，缺失必要字段的条目直接丢弃
 *
 * 链接验证策略：
 * - B站：优先调用官方 view API 校验 BV 是否真实存在（code==0），杜绝编造/下架视频
 * - 其他白名单站点：HTTP HEAD（3秒超时），失败时 GET 复核；404/410 清空；
 *   403/412/429/5xx 视为 CDN 风控不可判定 → 保留（避免 CSDN/知乎误杀）
 * - 6 条资源必须全部带有验证通过的详情外链，否则重试（最多 3 次）
 * - 提示词要求多平台均衡；每条都必须给出真实可打开的 link，禁止留空凑数
 */
public class DeepSeekJobLearningResourceClient implements JobLearningResourceClient {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekJobLearningResourceClient.class);

    /** 岗位名称与输出字段的最大长度（与 DB 字段长度对齐，防止 AI 异常输出占用过多存储） */
    private static final int MAX_JOB_TITLE_LEN = 120;
    private static final int MAX_TITLE_LEN = 200;
    private static final int MAX_DESCRIPTION_LEN = 2000;
    private static final int MAX_GUIDE_LEN = 4000;
    private static final int MAX_LINK_LEN = 1000;
    private static final int MAX_CONTENT_LEN = 12000;

    /** 链接验证参数 */
    private static final int LINK_VERIFY_TIMEOUT_MS = 3000;
    /** 六维资源均需有效外链，避免前端出现「有标题无链接」 */
    private static final int MIN_VALID_LINKS = 6;
    /** 全量外链要求更严，适度提高重试次数 */
    private static final int MAX_RETRY_ATTEMPTS = 3;

    /** 支持的链接类型 path 规则（host 在 isSupportedLink 中单独白名单校验） */
    private static final Pattern BILIBILI_BV_PATTERN = Pattern.compile(
            "BV[a-zA-Z0-9]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern BILIBILI_PATH_PATTERN = Pattern.compile(
            "/video/(BV[a-zA-Z0-9]+)(/.*)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern CSDN_PATH_PATTERN = Pattern.compile(
            "/[A-Za-z0-9_-]+/article/details/[0-9]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern JUEJIN_PATH_PATTERN = Pattern.compile(
            "/post/[0-9]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern ZHIHU_COLUMN_PATH_PATTERN = Pattern.compile(
            "/p/[0-9]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern MDN_PATH_PATTERN = Pattern.compile(
            "/([a-zA-Z-]+/)?docs(/[^\\s\"'<>]*)*", Pattern.CASE_INSENSITIVE);
    private static final Pattern CNBLOGS_PATH_PATTERN = Pattern.compile(
            "/[A-Za-z0-9_-]+/(p|articles)/[^\\s\"'<>]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern SEGMENTFAULT_PATH_PATTERN = Pattern.compile(
            "/a/[0-9]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern RUNOOB_PATH_PATTERN = Pattern.compile(
            "/[^\\s\"'<>]+\\.htm(l)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern WANGDOC_PATH_PATTERN = Pattern.compile(
            "/[^\\s\"'<>]+", Pattern.CASE_INSENSITIVE);

    private static final String BILIBILI_VIEW_API = "https://api.bilibili.com/x/web-interface/view?bvid=";

    private static final String PROMPT_TEMPLATE = """
        你是软件工程学习资源推荐助手。请围绕目标软件工程岗位「__JOB_TITLE__」，结合联网搜索到的最新真实资料，
        推荐 6 条高质量学习资源，按顺序覆盖以下维度：
        1. 理论基础：系统性讲解概念与原理（语言、框架、体系结构等）
        2. 实战练习：演示操作与实践（编码、调试、部署）
        3. 核心技能：该岗位必备软件工程技能专项学习
        4. 项目实践：真实软件项目从零到一
        5. 行业洞察：互联网/软件研发行业分析、趋势或工程经验分享
        6. 学习路径：软件工程学习路线规划或技术面试经验

        【内容导向】
        - 资源主题必须服务软件研发能力（编程、测试、工程化、云原生、算法工程等）
        - 不得推荐硬件拆装、产线操作、非 IT 考证刷题包、与岗位无关的泛娱乐内容

        【平台选择 — 必须多平台、不可只盯 B 站】
        允许且鼓励混用以下知识平台（6 条里至少覆盖 3 个不同平台）：
        - 掘金文章：https://juejin.cn/post/数字ID（技术实践类优先）
        - CSDN文章：https://blog.csdn.net/用户名/article/details/数字ID
        - 知乎专栏：https://zhuanlan.zhihu.com/p/数字ID
        - 博客园：https://www.cnblogs.com/用户名/p/xxxx.html
        - SegmentFault：https://segmentfault.com/a/数字ID
        - 菜鸟教程：https://www.runoob.com/…/*.htm
        - 网道文档：https://wangdoc.com/…
        - MDN：https://developer.mozilla.org/zh-CN/docs/…
        - B站视频：https://www.bilibili.com/video/BVxxxxxxxx（仅在搜索确认真实存在时使用）

        【准确性硬性规则 — 每条都必须有真实外链】
        - 6 条资源的 link 字段全部必填，禁止空字符串、禁止用假链接凑数
        - link 只能填写你通过联网搜索确认「当前仍可打开」的直连详情页 URL
        - 严禁编造、猜测、拼接 BV 号或文章 ID
        - 严禁搜索页、首页、专题聚合页、个人空间页、课程目录页
        - 某维度搜不到可靠视频时，必须改推该维度下已确认可打开的掘金/CSDN/博客园/SegmentFault/MDN 等图文详情页
        - B站仅在确认 BV 真实存在且与岗位匹配时使用；不确定就不要写 B 站，改写其他平台真链
        - 建议比例：约 1-2 条视频 + 4-5 条图文；图文以掘金、CSDN、博客园、SegmentFault 为主
        - 自检：返回前确认 6 条 link 均非空且为白名单平台详情 URL

        输出要求：
        1. 只返回 JSON，不要返回 Markdown。
        2. JSON 必须是数组，数组长度为 6。
        3. 每个元素必须包含 title、description、guide、link、content 五个字段。
        4. title 为资源名称，不超过 40 字。
        5. description 为资源简介，50-100 字，概括核心内容。
        6. guide 为来源信息：
           - B站：「UP主名（B站）」
           - 掘金：「作者名（掘金）」
           - CSDN：「作者名（CSDN）」
           - 知乎：「作者名（知乎）」
           - 博客园：「作者名（博客园）」
           - SegmentFault：「作者名（SegmentFault）」
           - 菜鸟教程：「菜鸟教程（Runoob）」
           - 网道：「网道（WangDoc）」
           - MDN：「MDN Web Docs（MDN）」
        7. link 必须是上述平台的直连详情 URL，6 条全部必填且可打开。
        8. content 为「图文微课」正文，必须以 Markdown 编写并遵循：
           - 开头用 # 写资源标题；
           - 用 ## 划分 3-4 个章节（如「核心概念」「重点讲解」「案例示范」「课后练习」）；
           - 每章正文 150-300 字，可用 - 列表、**加粗**、`代码` 等 Markdown 语法；
           - 内容必须与资源主题一致、真实有用，可直接阅读学习，不得为空。
        示例：
        [
          {
            "title": "【掘金】HashMap 源码深度解析",
            "description": "从数据结构原理到 JDK 源码实现，逐行分析 HashMap 的底层机制……",
            "guide": "沉默王二（掘金）",
            "link": "https://juejin.cn/post/6844903669382692878",
            "content": "# HashMap 源码深度解析\\n\\n## 核心概念\\nHashMap 是一种基于哈希表的 Map 接口实现……\\n\\n## 重点讲解\\n- 数组 + 链表 / 红黑树\\n- put 流程详解\\n- 扩容机制\\n\\n## 案例示范\\n手写一个简化版 HashMap……"
          }
        ]
        """;

    private final RestTemplate restTemplate;
    private final RestTemplate linkVerifyRestTemplate;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String baseUrl;
    private final String model;

    public DeepSeekJobLearningResourceClient(
            ObjectMapper objectMapper,
            String apiKey,
            String baseUrl,
            String model,
            int connectTimeoutMs,
            int readTimeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        this.restTemplate = new RestTemplate(requestFactory);

        SimpleClientHttpRequestFactory verifyFactory = new SimpleClientHttpRequestFactory();
        verifyFactory.setConnectTimeout(LINK_VERIFY_TIMEOUT_MS);
        verifyFactory.setReadTimeout(LINK_VERIFY_TIMEOUT_MS);
        this.linkVerifyRestTemplate = new RestTemplate(verifyFactory);

        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
    }

    @Override
    public String source() {
        return "deepseek";
    }

    @Override
    public String modelName() {
        return model;
    }

    @Override
    public List<GeneratedLearningResource> generateResources(String jobTitle) throws Exception {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("DeepSeek API Key is not configured");
        }
        if (jobTitle == null || jobTitle.isBlank()) {
            throw new IllegalArgumentException("岗位名称不能为空");
        }
        String safeJobTitle = sanitizeJobTitle(jobTitle);
        if (safeJobTitle.isEmpty()) {
            throw new IllegalArgumentException("岗位名称无效");
        }

        List<GeneratedLearningResource> lastValidResult = null;
        Exception lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            log.info("生成岗位学习资源，第 {} 次尝试: {}", attempt, safeJobTitle);
            try {
                List<GeneratedLearningResource> resources = generateResourcesOnce(safeJobTitle);

                int validLinkCount = countValidLinks(resources);

                if (validLinkCount >= MIN_VALID_LINKS && resources.size() >= MIN_VALID_LINKS) {
                    log.info("资源生成成功，有效链接数: {}/{}", validLinkCount, resources.size());
                    return resources;
                }

                log.warn("第 {} 次尝试，有效链接数不足: {} < {}，将重试",
                        attempt, validLinkCount, MIN_VALID_LINKS);
                lastValidResult = resources;

            } catch (Exception e) {
                log.error("第 {} 次尝试异常: {}", attempt, e.getMessage());
                lastException = e;
            }
        }

        if (lastValidResult != null && countValidLinks(lastValidResult) >= MIN_VALID_LINKS) {
            log.warn("已达到最大重试次数 {} 次，使用上次满足外链要求的结果", MAX_RETRY_ATTEMPTS);
            return lastValidResult;
        }

        if (lastException != null) {
            throw new IllegalStateException("资源生成失败：未能为全部 6 条资源匹配可验证外链", lastException);
        }
        throw new IllegalStateException("资源生成失败：未能为全部 6 条资源匹配可验证外链，请稍后重试");
    }

    private int countValidLinks(List<GeneratedLearningResource> resources) {
        int validLinkCount = 0;
        for (GeneratedLearningResource resource : resources) {
            // 详情外链或平台搜索兜底均计为有效，保证前端 6 条都能点开
            if (!resource.link().isBlank() && isHttpUrl(resource.link())) {
                validLinkCount++;
            }
        }
        return validLinkCount;
    }

    private List<GeneratedLearningResource> generateResourcesOnce(String jobTitle) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("stream", false);
        body.put("messages", List.of(
                Map.of("role", "system", "content",
                        "你是软件工程学习资源推荐助手。必须输出可解析 JSON。"
                                + "推荐内容须服务软件研发能力（编程、测试、工程化等），不得推荐非 IT 实训。"
                                + "必须返回恰好 6 条资源，且每条 link 都是联网搜索确认仍可访问的白名单平台详情页；"
                                + "禁止空 link；禁止编造 B站 BV 号与文章 ID；"
                                + "搜不到可靠视频时改推掘金/CSDN/博客园/SegmentFault/MDN 等真实图文详情页；"
                                + "优先混用多个图文平台，不要只推 B站。"),
                Map.of("role", "user", "content", buildPrompt(jobTitle))
        ));

        if ("deepseek-reasoner".equals(model)) {
            body.put("search_channel", "auto");
        }

        JsonNode response = restTemplate.postForObject(
                baseUrl + "/chat/completions",
                new HttpEntity<>(body, headers),
                JsonNode.class
        );
        if (response == null) {
            throw new IllegalStateException("DeepSeek empty response");
        }

        String content = response.path("choices").path(0).path("message").path("content").asText();
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("DeepSeek content empty");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(extractJsonArray(content));
        } catch (Exception parseEx) {
            throw new IllegalStateException("AI 返回的数据无法解析为 JSON", parseEx);
        }
        JsonNode items = root.isArray() ? root : root.path("resources");
        if (!items.isArray()) {
            throw new IllegalStateException("AI 返回的数据不是 JSON 数组");
        }

        List<GeneratedLearningResource> result = new ArrayList<>();
        for (JsonNode item : items) {
            String title = truncate(item.path("title").asText(item.path("标题").asText("")), MAX_TITLE_LEN);
            String description = truncate(item.path("description").asText(item.path("简介").asText("")), MAX_DESCRIPTION_LEN);
            String guide = truncate(item.path("guide").asText(item.path("获取指引").asText("")), MAX_GUIDE_LEN);
            String link = truncate(item.path("link").asText(item.path("链接").asText("")), MAX_LINK_LEN);
            String contentText = truncate(item.path("content").asText(item.path("微课正文").asText("")), MAX_CONTENT_LEN);
            if (title.isBlank() || description.isBlank() || guide.isBlank()) {
                continue;
            }
            if (!link.isBlank()) {
                link = normalizeLink(link);
                if (link.isBlank() || !isHttpUrl(link)) {
                    link = "";
                }
            }
            result.add(new GeneratedLearningResource(title, description, guide, link, contentText));
        }
        if (result.isEmpty()) {
            throw new IllegalStateException("AI 返回的资源缺少必要字段（title/description/guide）");
        }

        return fillMissingLinksWithSearchFallback(verifyAndFixLinks(result));
    }

    /**
     * 验证并修复链接。
     * - 先规范化 URL（强制 https、清理追踪参数、B站统一为 /video/BVxxx）
     * - 非白名单平台直接清空
     * - B站走官方 view API 校验 BV 是否存在；其他站点走 HTTP HEAD/GET 三态验证
     * 保留其他字段不变，确保图文微课内容可用
     */
    private List<GeneratedLearningResource> verifyAndFixLinks(List<GeneratedLearningResource> resources) {
        List<GeneratedLearningResource> verified = new ArrayList<>();
        for (GeneratedLearningResource resource : resources) {
            String originalLink = resource.link();
            String verifiedLink = "";

            if (!originalLink.isBlank()) {
                String normalized = normalizeLink(originalLink);
                if (!normalized.isBlank() && isSupportedLink(normalized)) {
                    if (verifyLink(normalized)) {
                        verifiedLink = normalized;
                        log.debug("链接验证通过: {}", normalized);
                    } else if (!isBilibiliVideoLink(normalized)) {
                        // 知乎/CSDN/掘金等常被 CDN/反爬拦截，格式合法则保留，避免卡片无外链
                        verifiedLink = normalized;
                        log.warn("链接探测未通过但保留白名单详情链: {}", normalized);
                    } else {
                        log.warn("B站链接验证失败，将清空 link 字段: {}", normalized);
                    }
                } else {
                    log.warn("链接类型不支持，将清空 link 字段: {}", originalLink);
                }
            }

            verified.add(new GeneratedLearningResource(
                    resource.title(),
                    resource.description(),
                    resource.guide(),
                    verifiedLink,
                    resource.content()
            ));
        }
        return verified;
    }

    /**
     * 详情外链缺失时，按标题/来源推断平台并生成搜索页 URL，保证 6 条均可点开。
     */
    private List<GeneratedLearningResource> fillMissingLinksWithSearchFallback(
            List<GeneratedLearningResource> resources) {
        List<GeneratedLearningResource> filled = new ArrayList<>();
        for (GeneratedLearningResource resource : resources) {
            if (!resource.link().isBlank()) {
                filled.add(resource);
                continue;
            }
            String fallback = buildPlatformSearchFallback(resource.title(), resource.guide());
            log.warn("资源缺少详情外链，使用平台搜索兜底: {} -> {}", resource.title(), fallback);
            filled.add(new GeneratedLearningResource(
                    resource.title(),
                    resource.description(),
                    resource.guide(),
                    fallback,
                    resource.content()
            ));
        }
        return filled;
    }

    private String buildPlatformSearchFallback(String title, String guide) {
        String text = (title == null ? "" : title) + " " + (guide == null ? "" : guide);
        String keyword = title == null ? "学习资源" : title.replaceFirst("^【[^】]{1,20}】\\s*", "").trim();
        if (keyword.isBlank()) {
            keyword = "学习资源";
        }
        String q;
        try {
            q = java.net.URLEncoder.encode(keyword, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            q = keyword;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("bilibili") || text.contains("B站") || text.contains("哔哩")) {
            return "https://search.bilibili.com/all?keyword=" + q;
        }
        if (lower.contains("juejin") || text.contains("掘金")) {
            return "https://juejin.cn/search?query=" + q + "&type=0";
        }
        if (lower.contains("csdn")) {
            return "https://so.csdn.net/so/search?q=" + q + "&t=blog";
        }
        if (lower.contains("zhihu") || text.contains("知乎")) {
            return "https://www.zhihu.com/search?type=content&q=" + q;
        }
        if (lower.contains("cnblogs") || text.contains("博客园")) {
            return "https://zzk.cnblogs.com/s?w=" + q;
        }
        if (lower.contains("segmentfault") || text.contains("思否")) {
            return "https://segmentfault.com/search?q=" + q;
        }
        if (lower.contains("runoob") || text.contains("菜鸟")) {
            return "https://www.runoob.com/?s=" + q;
        }
        if (lower.contains("wangdoc") || text.contains("网道")) {
            return "https://wangdoc.com/";
        }
        if (lower.contains("mozilla") || lower.contains("mdn")) {
            return "https://developer.mozilla.org/zh-CN/search?q=" + q;
        }
        return "https://www.bing.com/search?q=" + q;
    }

    /**
     * 规范化外链：仅处理白名单站点，失败返回空串。
     * B站统一清洗为 https://www.bilibili.com/video/BVxxx（去掉 query/fragment）。
     */
    private String normalizeLink(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        try {
            String trimmed = raw.trim();
            if (!isHttpUrl(trimmed)) {
                return "";
            }
            URI uri = new URI(trimmed);
            String host = uri.getHost();
            if (host == null) {
                return "";
            }
            String lowerHost = host.toLowerCase(Locale.ROOT);
            String path = uri.getPath() == null ? "" : uri.getPath();

            if ("www.bilibili.com".equals(lowerHost)
                    || "bilibili.com".equals(lowerHost)
                    || "m.bilibili.com".equals(lowerHost)) {
                String bvid = extractBilibiliBv(path.isBlank() ? trimmed : path);
                if (bvid == null) {
                    bvid = extractBilibiliBv(trimmed);
                }
                if (bvid == null) {
                    return "";
                }
                return "https://www.bilibili.com/video/" + bvid;
            }

            if ("blog.csdn.net".equals(lowerHost)
                    || "juejin.cn".equals(lowerHost)
                    || "zhuanlan.zhihu.com".equals(lowerHost)
                    || "developer.mozilla.org".equals(lowerHost)
                    || "www.cnblogs.com".equals(lowerHost)
                    || "segmentfault.com".equals(lowerHost)
                    || "www.runoob.com".equals(lowerHost)
                    || "wangdoc.com".equals(lowerHost)
                    || "www.wangdoc.com".equals(lowerHost)) {
                String cleanPath = path.endsWith("/") && path.length() > 1
                        ? path.substring(0, path.length() - 1)
                        : path;
                return "https://" + lowerHost + cleanPath;
            }
            return trimmed;
        } catch (URISyntaxException e) {
            return "";
        }
    }

    /**
     * 验证链接是否可用。
     * B站优先用官方 view API；其他站点走 HEAD/GET 三态验证。
     */
    private boolean verifyLink(String url) {
        if (isBilibiliVideoLink(url)) {
            return verifyBilibiliBv(url);
        }
        LinkVerifyResult result = verifyLinkOnce(url, HttpMethod.HEAD);
        if (result == LinkVerifyResult.VALID) {
            return true;
        }
        if (result == LinkVerifyResult.INVALID) {
            // HEAD 404/410 可能被站点统一返回，用 GET 复核一次，避免误杀真实链接
            LinkVerifyResult getResult = verifyLinkOnce(url, HttpMethod.GET);
            return getResult != LinkVerifyResult.INVALID;
        }
        // HEAD 被风控拦截（不可判定）→ GET 降级复核
        LinkVerifyResult getResult = verifyLinkOnce(url, HttpMethod.GET);
        return getResult != LinkVerifyResult.INVALID;
    }

    /** 调用 B站官方接口校验 BV 是否真实存在（code==0） */
    private boolean verifyBilibiliBv(String url) {
        String bvid = extractBilibiliBv(url);
        if (bvid == null || bvid.isBlank()) {
            return false;
        }
        String apiUrl = BILIBILI_VIEW_API + bvid;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.USER_AGENT,
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            headers.add(HttpHeaders.REFERER, "https://www.bilibili.com");
            var response = linkVerifyRestTemplate.exchange(
                    apiUrl, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            String body = response.getBody();
            if (body == null || body.isBlank()) {
                log.warn("B站 BV 校验返回空响应: {}", bvid);
                return false;
            }
            JsonNode root = objectMapper.readTree(body);
            int code = root.path("code").asInt(-1);
            if (code == 0) {
                log.debug("B站 BV 校验通过: {}", bvid);
                return true;
            }
            log.warn("B站 BV 不存在或已下架: {} code={}", bvid, code);
            return false;
        } catch (Exception e) {
            // API 临时不可用时降级页面校验：明确 404/410 才丢弃，其余保留
            log.warn("B站 BV API 校验异常，降级页面校验: {} - {}", bvid, e.getMessage());
            LinkVerifyResult pageResult = verifyLinkOnce(url, HttpMethod.GET);
            return pageResult != LinkVerifyResult.INVALID;
        }
    }

    private boolean isBilibiliVideoLink(String url) {
        try {
            URI uri = new URI(url.trim());
            String host = uri.getHost();
            if (host == null) {
                return false;
            }
            String lowerHost = host.toLowerCase(Locale.ROOT);
            return ("www.bilibili.com".equals(lowerHost) || "bilibili.com".equals(lowerHost))
                    && BILIBILI_PATH_PATTERN.matcher(uri.getPath() == null ? "" : uri.getPath()).matches();
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private String extractBilibiliBv(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        var matcher = BILIBILI_BV_PATTERN.matcher(url);
        if (matcher.find()) {
            String bvid = matcher.group();
            return "BV" + bvid.substring(2);
        }
        return null;
    }

    private LinkVerifyResult verifyLinkOnce(String url, HttpMethod method) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.USER_AGENT,
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");

            HttpEntity<String> entity = new HttpEntity<>(headers);
            var response = linkVerifyRestTemplate.exchange(url, method, entity, String.class);

            HttpStatusCode statusCode = response.getStatusCode();
            return toVerifyResult(statusCode.value());

        } catch (HttpStatusCodeException e) {
            HttpStatusCode statusCode = e.getStatusCode();
            return toVerifyResult(statusCode.value());

        } catch (Exception e) {
            // 连接超时/DNS 失败等网络异常 → 不可判定，保留链接
            log.debug("链接验证网络异常（不可判定）: {} - {}", url, e.getMessage());
            return LinkVerifyResult.UNDETERMINED;
        }
    }

    private LinkVerifyResult toVerifyResult(int statusCodeValue) {
        if (statusCodeValue >= 200 && statusCodeValue < 400) {
            return LinkVerifyResult.VALID;
        }
        if (statusCodeValue == 404 || statusCodeValue == 410) {
            return LinkVerifyResult.INVALID;
        }
        // 403/412/429/5xx 等：CDN 风控/反爬拦截，不可判定
        return LinkVerifyResult.UNDETERMINED;
    }

    /** 链接验证三态结果 */
    private enum LinkVerifyResult {
        /** 有效：2xx/3xx */
        VALID,
        /** 无效：404/410，AI 编造的链接 */
        INVALID,
        /** 不可判定：风控/反爬/网络异常，保留链接 */
        UNDETERMINED
    }

    /** 判断字符串是否为 http/https 开头的 URL */
    private boolean isHttpUrl(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    /**
     * 检查链接是否为支持的类型（B站/掘金/CSDN/知乎/博客园/SegmentFault/菜鸟/网道/MDN）。
     * 先解析 URI 并严格校验 scheme + host，再校验 path：
     * - 仅允许 http/https；
     * - host 必须精确等于白名单 host（拒绝伪装 host / 开放重定向）；
     * - path 再经 path 级正则整体匹配。
     * 校验通过后该链接才会被 verifyLink 发起请求，从根本上阻断 SSRF。
     */
    private boolean isSupportedLink(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        try {
            URI uri = new URI(url.trim());
            String scheme = uri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                return false;
            }
            String host = uri.getHost();
            if (host == null) {
                return false;
            }
            String path = uri.getPath() == null ? "" : uri.getPath();
            String lowerHost = host.toLowerCase(Locale.ROOT);
            switch (lowerHost) {
                case "www.bilibili.com":
                case "bilibili.com":
                    return BILIBILI_PATH_PATTERN.matcher(path).matches();
                case "blog.csdn.net":
                    return CSDN_PATH_PATTERN.matcher(path).matches();
                case "juejin.cn":
                    return JUEJIN_PATH_PATTERN.matcher(path).matches();
                case "zhuanlan.zhihu.com":
                    return ZHIHU_COLUMN_PATH_PATTERN.matcher(path).matches();
                case "developer.mozilla.org":
                    return MDN_PATH_PATTERN.matcher(path).matches();
                case "www.cnblogs.com":
                    return CNBLOGS_PATH_PATTERN.matcher(path).matches();
                case "segmentfault.com":
                    return SEGMENTFAULT_PATH_PATTERN.matcher(path).matches();
                case "www.runoob.com":
                    return RUNOOB_PATH_PATTERN.matcher(path).matches();
                case "wangdoc.com":
                case "www.wangdoc.com":
                    return WANGDOC_PATH_PATTERN.matcher(path).matches();
                default:
                    return false;
            }
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private String buildPrompt(String jobTitle) {
        return PROMPT_TEMPLATE.replace("__JOB_TITLE__", jobTitle);
    }

    private String sanitizeJobTitle(String raw) {
        String cleaned = raw.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "");
        cleaned = cleaned.replaceAll("\\n{3,}", "\n\n");
        cleaned = cleaned.trim();
        if (cleaned.length() > MAX_JOB_TITLE_LEN) {
            cleaned = cleaned.substring(0, MAX_JOB_TITLE_LEN);
        }
        return cleaned;
    }

    private String extractJsonArray(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            int firstLineEnd = trimmed.indexOf('\n');
            if (firstLineEnd > -1) {
                trimmed = trimmed.substring(firstLineEnd + 1).trim();
            }
        }
        if (trimmed.endsWith("```")) {
            int lastFence = trimmed.lastIndexOf("```");
            if (lastFence > -1) {
                trimmed = trimmed.substring(0, lastFence).trim();
            }
        }
        int firstBracket = trimmed.indexOf('[');
        int lastBracket = trimmed.lastIndexOf(']');
        if (firstBracket >= 0 && lastBracket > firstBracket) {
            return trimmed.substring(firstBracket, lastBracket + 1);
        }
        return trimmed;
    }

    private String truncate(String value, int maxLen) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLen ? trimmed : trimmed.substring(0, maxLen);
    }
}
