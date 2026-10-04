package com.markingsystem.service;

import com.markingsystem.entity.SystemSetting;
import com.markingsystem.repository.SystemSettingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Embedding（向量化）配置服务。
 *
 * <p>配置项存 system_setting 表（key-value），管理员可在前端页面维护；
 * 未配置的项回退到 application.yml 的默认值（ai.embedding.*）。
 *
 * <p>配置项：
 * <ul>
 *   <li>{@code ai.embedding.base-url} —— OpenAI 兼容服务地址</li>
 *   <li>{@code ai.embedding.model} —— embedding 模型名</li>
 *   <li>{@code ai.embedding.api-key} —— API Key（仅存数据库，不回传明文）</li>
 *   <li>{@code ai.embedding.enabled} —— 是否启用向量检索（false 时降级关键词检索）</li>
 *   <li>{@code ai.retrieval.top-k} —— 学生对话 RAG 检索条数</li>
 *   <li>{@code ai.retrieval.similarity-threshold} —— 向量检索相似度阈值（低于该值不命中）</li>
 *   <li>{@code ai.chunk.length} —— 文档导入单块最大字符数</li>
 * </ul>
 */
@Service
public class EmbeddingConfigService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingConfigService.class);

    public static final String KEY_BASE_URL = "ai.embedding.base-url";
    public static final String KEY_MODEL = "ai.embedding.model";
    public static final String KEY_API_KEY = "ai.embedding.api-key";
    public static final String KEY_ENABLED = "ai.embedding.enabled";
    public static final String KEY_RETRIEVAL_TOP_K = "ai.retrieval.top-k";
    public static final String KEY_RETRIEVAL_SIMILARITY = "ai.retrieval.similarity-threshold";
    public static final String KEY_CHUNK_LENGTH = "ai.chunk.length";

    private final SystemSettingRepository settingRepository;

    // 默认值只来自 application.yml（ai.embedding.*），代码内不硬编码具体服务商/模型；
    // 管理员在"AI 知识库"页面保存后，数据库值优先于 yml 默认值。
    @Value("${ai.embedding.base-url:}")
    private String defaultBaseUrl;

    @Value("${ai.embedding.model:}")
    private String defaultModel;

    @Value("${ai.embedding.api-key:}")
    private String defaultApiKey;

    @Value("${ai.retrieval.top-k:3}")
    private int defaultTopK;

    @Value("${ai.retrieval.similarity-threshold:0}")
    private double defaultSimilarityThreshold;

    @Value("${ai.chunk.length:450}")
    private int defaultChunkLength;

    public EmbeddingConfigService(SystemSettingRepository settingRepository) {
        this.settingRepository = settingRepository;
    }

    /**
     * 当前生效的 embedding 配置（数据库优先，缺省回退 yml 默认值）。
     */
    public EmbeddingConfig getConfig() {
        return new EmbeddingConfig(
                read(KEY_BASE_URL, defaultBaseUrl),
                read(KEY_MODEL, defaultModel),
                read(KEY_API_KEY, defaultApiKey),
                parseEnabled(read(KEY_ENABLED, "true"))
        );
    }

    /**
     * 保存配置。apiKey 为空表示不修改已保存的 Key（保留旧 Key 或 yml 默认）。
     */
    @Transactional
    public EmbeddingConfig saveConfig(String baseUrl, String model, String apiKey, Boolean enabled) {
        if (baseUrl != null && !baseUrl.isBlank()) {
            write(KEY_BASE_URL, baseUrl.trim());
        }
        if (model != null && !model.isBlank()) {
            write(KEY_MODEL, model.trim());
        }
        if (apiKey != null && !apiKey.isBlank()) {
            write(KEY_API_KEY, apiKey.trim());
        }
        if (enabled != null) {
            write(KEY_ENABLED, Boolean.toString(enabled));
        }
        return getConfig();
    }

    /**
     * 当前生效的检索与分段参数（Top-K / 相似度阈值 / 文档切块长度）。
     */
    public RetrievalConfig getRetrievalConfig() {
        return new RetrievalConfig(
                clampTopK(parseInt(read(KEY_RETRIEVAL_TOP_K, String.valueOf(defaultTopK)), defaultTopK)),
                clampThreshold(parseDouble(read(KEY_RETRIEVAL_SIMILARITY, String.valueOf(defaultSimilarityThreshold)), defaultSimilarityThreshold)),
                clampChunkLength(parseInt(read(KEY_CHUNK_LENGTH, String.valueOf(defaultChunkLength)), defaultChunkLength))
        );
    }

    /**
     * 保存检索与分段参数。
     */
    @Transactional
    public RetrievalConfig saveRetrievalConfig(Integer topK, Double similarityThreshold, Integer chunkLength) {
        if (topK != null) {
            write(KEY_RETRIEVAL_TOP_K, String.valueOf(clampTopK(topK)));
        }
        if (similarityThreshold != null) {
            write(KEY_RETRIEVAL_SIMILARITY, String.valueOf(clampThreshold(similarityThreshold)));
        }
        if (chunkLength != null) {
            write(KEY_CHUNK_LENGTH, String.valueOf(clampChunkLength(chunkLength)));
        }
        return getRetrievalConfig();
    }

    private static int clampTopK(int value) {
        return Math.max(1, Math.min(10, value));
    }

    private static double clampThreshold(double value) {
        return Math.max(0D, Math.min(0.99D, value));
    }

    private static int clampChunkLength(int value) {
        return Math.max(100, Math.min(2000, value));
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static double parseDouble(String value, double fallback) {
        try {
            return Double.parseDouble(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    /**
     * API Key 掩码（前 4 后 4），用于前端展示是否已配置。
     */
    public String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return "";
        }
        if (apiKey.length() <= 8) {
            return "****";
        }
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }

    public boolean isEnabled() {
        EmbeddingConfig config = getConfig();
        return config.enabled() && config.apiKey() != null && !config.apiKey().isBlank();
    }

    private String read(String key, String defaultValue) {
        try {
            Optional<SystemSetting> setting = settingRepository.findBySettingKey(key);
            return setting.map(SystemSetting::getSettingValue)
                    .filter(value -> !value.isBlank())
                    .orElse(defaultValue);
        } catch (Exception e) {
            log.warn("Failed to read system setting {}: {}", key, e.getMessage());
            return defaultValue;
        }
    }

    private void write(String key, String value) {
        try {
            SystemSetting setting = settingRepository.findBySettingKey(key)
                    .orElseGet(() -> {
                        SystemSetting next = new SystemSetting();
                        next.setSettingKey(key);
                        next.setDescription(describe(key));
                        return next;
                    });
            setting.setSettingValue(value);
            settingRepository.save(setting);
        } catch (Exception e) {
            log.warn("Failed to write system setting {}: {}", key, e.getMessage());
            throw new IllegalStateException("保存 embedding 配置失败：" + e.getMessage());
        }
    }

    private String describe(String key) {
        return switch (key) {
            case KEY_BASE_URL -> "AI 知识库 Embedding 服务地址";
            case KEY_MODEL -> "AI 知识库 Embedding 模型";
            case KEY_API_KEY -> "AI 知识库 Embedding API Key";
            case KEY_ENABLED -> "AI 知识库向量检索开关";
            case KEY_RETRIEVAL_TOP_K -> "AI 知识库检索 Top-K";
            case KEY_RETRIEVAL_SIMILARITY -> "AI 知识库检索相似度阈值";
            case KEY_CHUNK_LENGTH -> "AI 知识库文档导入切块长度";
            default -> key;
        };
    }

    private boolean parseEnabled(String value) {
        return value == null || !"false".equalsIgnoreCase(value.trim());
    }

    /**
     * Embedding 配置快照。
     */
    public record EmbeddingConfig(String baseUrl, String model, String apiKey, boolean enabled) {
    }

    /**
     * 检索与分段参数快照。
     */
    public record RetrievalConfig(int topK, double similarityThreshold, int chunkLength) {
    }
}
