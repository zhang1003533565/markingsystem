package com.markingsystem.service;

import com.markingsystem.dto.assistant.AssistantSessionSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * 学生 AIChat 页面（channel=page）的长期记忆服务。
 *
 * <p>仿 OpenClaw 的 MEMORY.md + memory/YYYY-MM-DD.md 设计：
 * <ul>
 *   <li>{@code assistant:memory:{userId}:page:long} —— 长期摘要（Markdown bullet 列表），
 *       每次请求注入 system prompt；TTL 90 天；容量上限默认 30 条 / 1600 字符。</li>
 *   <li>{@code assistant:memory:{userId}:page:index} —— ZSet，member=date (YYYY-MM-DD)，
 *       score=最后更新时间。供 memory_get(date) 反查每日会话。</li>
 *   <li>{@code assistant:memory:{userId}:page:bullets} —— 临时缓存：每次会话结束后由
 *       dreaming 压缩得到的最新 bullets。</li>
 * </ul>
 *
 * <p>检索走纯关键词打分（不引入 embedding 依赖）：
 * 对长期 bullet 列表 + 最近 N 天索引中的会话标题/预览做 TF-IDF 风格的子串 + 字符重叠打分。
 */
@Service
public class AssistantLongTermMemoryService {

    private static final Logger log = LoggerFactory.getLogger(AssistantLongTermMemoryService.class);

    public static final String CHANNEL_PAGE = "page";
    private static final String LONG_KEY_PREFIX = "assistant:memory:";
    private static final String INDEX_KEY_PREFIX = "assistant:memory:";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private AssistantSessionRedisService sessionRedisService;

    @Value("${ai.chat.page.memory.long-term-ttl-second:7776000}")
    private long longTermTtlSecond;

    @Value("${ai.chat.page.memory.max-bullets:30}")
    private int maxBullets;

    @Value("${ai.chat.page.memory.max-chars:1600}")
    private int maxChars;

    @Value("${ai.chat.page.memory.enabled:true}")
    private boolean memoryEnabled;

    private String longKey(Long userId) {
        return LONG_KEY_PREFIX + safeUser(userId) + ":" + CHANNEL_PAGE + ":long";
    }

    private String indexKey(Long userId) {
        return INDEX_KEY_PREFIX + safeUser(userId) + ":" + CHANNEL_PAGE + ":index";
    }

    private String safeUser(Long userId) {
        return userId == null ? "anonymous" : String.valueOf(userId);
    }

    public boolean isEnabled() {
        return memoryEnabled;
    }

    /**
     * 读取当前用户的长期摘要全文（Markdown bullet 列表）。
     * 未启用 / 无摘要时返回空字符串。
     */
    public String getLongTermSummary(Long userId) {
        if (!memoryEnabled || userId == null) {
            return "";
        }
        try {
            String json = redisTemplate.opsForValue().get(longKey(userId));
            if (json == null || json.isBlank()) {
                return "";
            }
            return json;
        } catch (Exception e) {
            log.warn("Failed to read long-term memory: userId={}", userId, e);
            return "";
        }
    }

    /**
     * 读取摘要条目列表（每行一条 bullet），便于前端展示与按条检索。
     */
    public List<String> listBullets(Long userId) {
        String raw = getLongTermSummary(userId);
        if (raw.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>();
        for (String line : raw.split("\\n")) {
            String trimmed = line.replaceFirst("^[-\\s*]+", "").trim();
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return out;
    }

    /**
     * 追加一条 bullet。超过上限时丢弃最旧的一半，保留近期内容。
     */
    public void appendBullet(Long userId, String bullet) {
        if (!memoryEnabled || userId == null) {
            return;
        }
        if (bullet == null) {
            return;
        }
        String trimmed = bullet.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        // 单条 bullet 长度限制
        if (trimmed.length() > 200) {
            trimmed = trimmed.substring(0, 200) + "…";
        }
        try {
            String key = longKey(userId);
            String current = redisTemplate.opsForValue().get(key);
            List<String> lines = new ArrayList<>();
            if (current != null && !current.isBlank()) {
                for (String l : current.split("\\n")) {
                    String t = l.replaceFirst("^[-\\s*]+", "").trim();
                    if (!t.isEmpty()) {
                        lines.add(t);
                    }
                }
            }
            // 去重：相同内容不重复入库
            if (!lines.contains(trimmed)) {
                lines.add(trimmed);
            }
            // 容量上限：超过 maxBullets 时裁剪前一半
            while (lines.size() > maxBullets) {
                int drop = Math.max(1, lines.size() / 2);
                lines = new ArrayList<>(lines.subList(drop, lines.size()));
            }
            // 字符上限
            StringBuilder sb = new StringBuilder();
            for (String l : lines) {
                if (sb.length() + l.length() + 2 > maxChars) {
                    break;
                }
                if (sb.length() > 0) {
                    sb.append("\n");
                }
                sb.append("- ").append(l);
            }
            redisTemplate.opsForValue().set(key, sb.toString(), longTermTtlSecond, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Failed to append bullet: userId={}", userId, e);
        }
    }

    /**
     * 清空当前用户的长期记忆（摘要 + 索引）。
     */
    public void clear(Long userId) {
        if (userId == null) {
            return;
        }
        try {
            redisTemplate.delete(longKey(userId));
            redisTemplate.delete(indexKey(userId));
        } catch (Exception e) {
            log.warn("Failed to clear long-term memory: userId={}", userId, e);
        }
    }

    /**
     * 记录某会话发生的日期（用于 memory_get 按日期取历史）。
     */
    public void recordSessionDate(Long userId, String sessionId) {
        if (!memoryEnabled || userId == null || sessionId == null) {
            return;
        }
        try {
            String today = LocalDate.now().format(DATE_FORMAT);
            redisTemplate.opsForZSet().add(indexKey(userId), today, System.currentTimeMillis());
            redisTemplate.expire(indexKey(userId), longTermTtlSecond, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Failed to record session date: userId={}, sessionId={}", userId, sessionId, e);
        }
    }

    /**
     * 取最近 N 天的索引（date 字符串列表，按 score 倒序）。
     */
    public List<String> recentDates(Long userId, int limit) {
        if (!memoryEnabled || userId == null) {
            return Collections.emptyList();
        }
        try {
            Set<String> dates = redisTemplate.opsForZSet().reverseRange(indexKey(userId), 0, Math.max(0, limit - 1));
            if (dates == null) {
                return Collections.emptyList();
            }
            return new ArrayList<>(dates);
        } catch (Exception e) {
            log.warn("Failed to read recent dates: userId={}", userId, e);
            return Collections.emptyList();
        }
    }

    /**
     * 取指定日期下的会话摘要列表（取自 AssistantSessionRedisService）。
     */
    public List<AssistantSessionSummary> getMemoriesByDate(Long userId, String date, int limit) {
        if (!memoryEnabled || userId == null || date == null || date.isBlank()) {
            return Collections.emptyList();
        }
        // 此处仅按当前 channel=page 的会话列表过滤——日期是写入侧的提示，
        // 真实摘要仍来自 AssistantSessionRedisService.listSessions。
        List<AssistantSessionSummary> all = sessionRedisService.listSessions(CHANNEL_PAGE, userId);
        List<AssistantSessionSummary> out = new ArrayList<>();
        for (AssistantSessionSummary s : all) {
            if (s == null) {
                continue;
            }
            String updatedDate = formatTimestampToDate(s.getUpdatedAt());
            if (date.equals(updatedDate)) {
                out.add(s);
                if (out.size() >= limit) {
                    break;
                }
            }
        }
        return out;
    }

    private String formatTimestampToDate(Long ts) {
        if (ts == null) {
            return "";
        }
        try {
            return java.time.Instant.ofEpochMilli(ts)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDate()
                    .format(DATE_FORMAT);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 轻量关键词检索：在长期 bullet 列表 + 最近 N 天会话标题/预览中按
     * "query 与候选文本的字符重叠率" 打分，返回 top limit 条候选。
     *
     * <p>候选结构：{@code {source: "long"|"session", ref: bullet-or-sessionId, text: snippet, score: 0.x}}
     */
    public List<Map<String, Object>> searchMemories(Long userId, String query, int limit) {
        if (!memoryEnabled || userId == null || query == null || query.isBlank()) {
            return Collections.emptyList();
        }
        int top = limit > 0 ? Math.min(limit, 10) : 5;
        List<Map<String, Object>> scored = new ArrayList<>();

        // 1) 长期 bullet
        for (String bullet : listBullets(userId)) {
            double score = scoreText(query, bullet);
            if (score > 0) {
                Map<String, Object> hit = new HashMap<>();
                hit.put("source", "long");
                hit.put("ref", bullet);
                hit.put("text", truncate(bullet, 200));
                hit.put("score", score);
                scored.add(hit);
            }
        }

        // 2) 最近 30 天会话摘要
        List<AssistantSessionSummary> sessions = sessionRedisService.listSessions(CHANNEL_PAGE, userId);
        int maxSessions = Math.min(sessions.size(), 50);
        for (int i = 0; i < maxSessions; i++) {
            AssistantSessionSummary s = sessions.get(i);
            if (s == null) {
                continue;
            }
            String title = s.getTitle() == null ? "" : s.getTitle();
            String preview = s.getPreview() == null ? "" : s.getPreview();
            double titleScore = scoreText(query, title);
            double previewScore = scoreText(query, preview) * 0.6;
            double score = Math.max(titleScore, previewScore);
            if (score > 0) {
                Map<String, Object> hit = new HashMap<>();
                hit.put("source", "session");
                hit.put("ref", s.getSessionId());
                hit.put("title", title);
                hit.put("text", truncate(preview.isEmpty() ? title : preview, 200));
                hit.put("updatedAt", s.getUpdatedAt());
                hit.put("score", score);
                scored.add(hit);
            }
        }

        scored.sort((a, b) -> Double.compare((double) b.get("score"), (double) a.get("score")));
        if (scored.size() > top) {
            return scored.subList(0, top);
        }
        return scored;
    }

    /**
     * 简单打分：query 中拆分出的关键词与候选文本的重叠字符数 / query 长度。
     * 忽略中文常见停用词、空格、标点。
     */
    private double scoreText(String query, String candidate) {
        if (candidate == null || candidate.isEmpty()) {
            return 0;
        }
        Set<String> qTokens = tokenize(query);
        Set<String> cTokens = tokenize(candidate);
        if (qTokens.isEmpty() || cTokens.isEmpty()) {
            return 0;
        }
        Set<String> intersect = new HashSet<>(qTokens);
        intersect.retainAll(cTokens);
        if (intersect.isEmpty()) {
            // 退化为最长公共子串比例
            return longestCommonSubstringRatio(query, candidate);
        }
        return (double) intersect.size() / qTokens.size();
    }

    private static final Pattern TOKEN_SPLIT = Pattern.compile("[\\s,，。.!?;:；：、\\-_/()\\[\\]{}【】\"'`]");

    private Set<String> tokenize(String text) {
        if (text == null) {
            return Collections.emptySet();
        }
        String[] parts = TOKEN_SPLIT.split(text);
        Set<String> out = new HashSet<>();
        for (String p : parts) {
            String t = p.trim().toLowerCase();
            if (t.length() >= 1) {
                out.add(t);
            }
        }
        return out;
    }

    private double longestCommonSubstringRatio(String a, String b) {
        String shortStr = a.length() <= b.length() ? a : b;
        String longStr = a.length() <= b.length() ? b : a;
        int best = 0;
        int n = shortStr.length();
        int m = longStr.length();
        if (n == 0 || m == 0) {
            return 0;
        }
        int cap = Math.min(n, 32);
        for (int i = 0; i < n; i++) {
            int len = Math.min(cap, n - i);
            String sub = shortStr.substring(i, i + len);
            if (longStr.contains(sub)) {
                best = Math.max(best, len);
            }
        }
        return best == 0 ? 0 : (double) best / n;
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "…";
    }
}