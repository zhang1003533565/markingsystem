package com.markingsystem.service.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.markingsystem.service.EmbeddingConfigService;
import com.markingsystem.service.EmbeddingConfigService.EmbeddingConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.net.InetAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * OpenAI 兼容 embedding 客户端（POST {baseUrl}/v1/embeddings）。
 *
 * <p>配置动态读取：每次请求从 {@link EmbeddingConfigService} 取 base-url / model / api-key
 * （管理员前端页面可维护），未配置时回退 yml 默认值。零新增 Maven 依赖。
 */
public class EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingClient.class);

    private final ObjectMapper objectMapper;
    private final EmbeddingConfigService configService;
    private final RestTemplate restTemplate;

    public EmbeddingClient(ObjectMapper objectMapper, EmbeddingConfigService configService) {
        this.objectMapper = objectMapper;
        this.configService = configService;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(15000);
        this.restTemplate = new RestTemplate(factory);
    }

    public boolean isEnabled() {
        return configService.isEnabled();
    }

    public String modelName() {
        return configService.getConfig().model();
    }

    /**
     * 单条文本向量化。
     *
     * @return 向量；服务不可用或失败时返回 null（调用方降级为关键词检索或跳过向量化）。
     */
    public float[] embed(String text) {
        List<float[]> result = embedBatch(List.of(text));
        return result == null || result.isEmpty() ? null : result.get(0);
    }

    /**
     * 批量文本向量化（保持输入顺序）。
     */
    public List<float[]> embedBatch(List<String> texts) {
        if (!isEnabled()) {
            log.warn("Embedding client disabled: missing api key or disabled by admin");
            return null;
        }
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        return embedWithConfig(texts, configService.getConfig());
    }

    /**
     * 用指定配置（不读库）批量向量化，供"测试连接"临时覆盖 base-url / model / api-key 使用。
     */
    private List<float[]> embedWithConfig(List<String> texts, EmbeddingConfig config) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(config.apiKey());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.model());
        body.put("input", texts);

        try {
            JsonNode response = restTemplate.postForObject(
                    config.baseUrl() + "/v1/embeddings",
                    new HttpEntity<>(body, headers),
                    JsonNode.class
            );
            if (response == null) {
                log.warn("Embedding empty response");
                return null;
            }
            JsonNode data = response.path("data");
            List<float[]> vectors = new ArrayList<>();
            for (JsonNode item : data) {
                JsonNode embedding = item.path("embedding");
                float[] vector = new float[embedding.size()];
                for (int i = 0; i < embedding.size(); i++) {
                    vector[i] = (float) embedding.get(i).asDouble(0);
                }
                vectors.add(vector);
            }
            return vectors;
        } catch (Exception e) {
            log.warn("Embedding request failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 用当前配置测试连接（管理员页面"测试连接"用）。
     *
     * @return 成功返回 {ok=true, dimension, model}；失败返回 {ok=false, message}
     */
    public Map<String, Object> testConnection() {
        return testConnection(null, null, null);
    }

    /**
     * 用表单中的 base-url / model / api-key 临时测试连接（管理员页面"测试连接"用）。
     *
     * <p>传入值非空时覆盖已保存配置，仅用于本次测试、不落库；
     * 传入值为空时回退已保存配置（含 yml 默认值）。
     *
     * @return 成功返回 {ok=true, dimension, model}；失败返回 {ok=false, message}
     */
    public Map<String, Object> testConnection(String baseUrl, String model, String apiKey) {
        Map<String, Object> result = new LinkedHashMap<>();
        EmbeddingConfig saved = configService.getConfig();
        String effectiveBaseUrl = baseUrl != null && !baseUrl.isBlank() ? baseUrl.trim() : saved.baseUrl();
        String effectiveModel = model != null && !model.isBlank() ? model.trim() : saved.model();
        String effectiveApiKey = apiKey != null && !apiKey.isBlank() ? apiKey.trim() : saved.apiKey();
        if (effectiveApiKey == null || effectiveApiKey.isBlank()) {
            result.put("ok", false);
            result.put("message", "未配置 API Key");
            return result;
        }
        // 防 SSRF：临时 baseUrl 只允许公网 http/https，禁止指向内网 / 回环 / 链路本地 / 云元数据地址
        String baseUrlError = validatePublicBaseUrl(effectiveBaseUrl);
        if (baseUrlError != null) {
            result.put("ok", false);
            result.put("message", baseUrlError);
            return result;
        }
        List<float[]> vectors = embedWithConfig(
                List.of("test-connection"),
                new EmbeddingConfig(effectiveBaseUrl, effectiveModel, effectiveApiKey, true)
        );
        float[] vector = vectors == null || vectors.isEmpty() ? null : vectors.get(0);
        if (vector == null) {
            result.put("ok", false);
            result.put("message", "调用 embedding 服务失败，请检查 base-url / model / api-key 与网络");
            return result;
        }
        result.put("ok", true);
        result.put("message", "连接成功");
        result.put("dimension", vector.length);
        result.put("model", effectiveModel);
        result.put("baseUrl", effectiveBaseUrl);
        return result;
    }

    /**
     * 校验 embedding base-url 只能指向公网 http/https 地址，防止 SSRF：
     * 拒绝非 http(s) scheme、回环（127.* / ::1）、私网（10/8、172.16/12、192.168/16）、
     * 链路本地（169.254/16、fe80::/10）与常见云元数据主机名。
     */
    private String validatePublicBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "base-url 不能为空";
        }
        try {
            URI uri = new URI(baseUrl.trim());
            String scheme = uri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                return "base-url 仅支持 http/https";
            }
            String host = uri.getHost();
            if (host == null) {
                return "base-url 缺少有效主机名";
            }
            String lowerHost = host.toLowerCase(Locale.ROOT);
            if (lowerHost.equals("localhost")
                    || lowerHost.equals("metadata.google.internal")
                    || lowerHost.endsWith(".internal")
                    || lowerHost.endsWith(".local")) {
                return "不允许访问内网/本机地址";
            }
            InetAddress address = InetAddress.getByName(host);
            if (address.isLoopbackAddress()
                    || address.isSiteLocalAddress()
                    || address.isLinkLocalAddress()
                    || address.isAnyLocalAddress()) {
                return "不允许访问内网/本机地址";
            }
            return null;
        } catch (Exception e) {
            return "base-url 无法解析（非法地址或 DNS 解析失败）";
        }
    }
}
