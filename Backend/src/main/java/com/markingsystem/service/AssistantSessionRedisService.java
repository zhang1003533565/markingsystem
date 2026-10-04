package com.markingsystem.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.markingsystem.dto.assistant.AssistantSessionSummary;
import com.markingsystem.service.llm.spi.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * AI 助理会话的 Redis 存储与索引。
 *
 * <p>每个会话按 {@code channel} 分桶：floating 用于全局悬浮球的临时对话；
 * page 用于学生专属页面聊天，便于后续单独做 RAG 与长期记忆。
 *
 * <p>使用两类 Redis key：
 * <ul>
 *   <li>{@code assistant:session:<channel>:<sessionId>}：JSON 序列化的消息历史</li>
 *   <li>{@code assistant:index:<channel>:<userId>}：Set 结构，记录该用户在该 channel 下的 sessionId 列表</li>
 *   <li>{@code assistant:index:<channel>:<userId>:meta}：ZSet 结构，按 score（即更新时间戳）排序用于列表展示</li>
 * </ul>
 */
@Service
public class AssistantSessionRedisService {

    private static final Logger log = LoggerFactory.getLogger(AssistantSessionRedisService.class);

    public static final String CHANNEL_FLOATING = "floating";
    public static final String CHANNEL_PAGE = "page";

    private static final String SESSION_KEY_PREFIX = "assistant:session:";
    private static final String INDEX_KEY_PREFIX = "assistant:index:";
    private static final String USER_COUNT_KEY_PREFIX = "assistant:ucount:";
    /** 会话标题覆盖：{@code assistant:title:<channel>:<sessionId>} -> 用户重命名的标题 */
    private static final String TITLE_KEY_PREFIX = "assistant:title:";

    private static final TypeReference<List<Message>> MESSAGE_LIST_TYPE = new TypeReference<>() {};

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${ai.chat.session-ttl-second:86400}")
    private long defaultSessionTtlSecond;

    @Value("${ai.chat.session-ttl-second-page:2592000}")
    private long pageSessionTtlSecond;

    public String normalizeChannel(String channel) {
        if (channel == null) {
            return CHANNEL_FLOATING;
        }
        String trimmed = channel.trim().toLowerCase();
        if (CHANNEL_PAGE.equals(trimmed)) {
            return CHANNEL_PAGE;
        }
        return CHANNEL_FLOATING;
    }

    private long resolveTtl(String channel) {
        return CHANNEL_PAGE.equals(channel) ? pageSessionTtlSecond : defaultSessionTtlSecond;
    }

    private String sessionKey(String channel, String sessionId) {
        return SESSION_KEY_PREFIX + channel + ":" + sessionId;
    }

    private String indexKey(String channel, Long userId) {
        return INDEX_KEY_PREFIX + channel + ":" + (userId == null ? "anonymous" : userId);
    }

    private String metaKey(String channel, Long userId) {
        return indexKey(channel, userId) + ":meta";
    }

    private String userCountKey(String channel, String sessionId) {
        return USER_COUNT_KEY_PREFIX + normalizeChannel(channel) + ":" + sessionId;
    }

    private String titleKey(String channel, String sessionId) {
        return TITLE_KEY_PREFIX + normalizeChannel(channel) + ":" + sessionId;
    }

    /**
     * 累计某会话的 user 消息总数（跨 trim 保底，供记忆整合判断触发时机）。
     *
     * @return 递增后的累计值
     */
    public long incrementUserCount(String channel, String sessionId) {
        String ch = normalizeChannel(channel);
        if (sessionId == null || sessionId.isBlank()) {
            return 0;
        }
        try {
            String key = userCountKey(ch, sessionId);
            Long count = redisTemplate.opsForValue().increment(key);
            redisTemplate.expire(key, resolveTtl(ch), TimeUnit.SECONDS);
            return count == null ? 0 : count;
        } catch (Exception e) {
            log.warn("Failed to increment user count: channel={}, sessionId={}", ch, sessionId, e);
            return 0;
        }
    }

    public List<Message> getHistory(String channel, String sessionId) {
        String ch = normalizeChannel(channel);
        if (sessionId == null || sessionId.isBlank()) {
            return new ArrayList<>();
        }
        try {
            String json = redisTemplate.opsForValue().get(sessionKey(ch, sessionId));
            if (json == null || json.isBlank()) {
                return new ArrayList<>();
            }
            return objectMapper.readValue(json, MESSAGE_LIST_TYPE);
        } catch (Exception e) {
            log.warn("Failed to read assistant session history: channel={}, sessionId={}", ch, sessionId, e);
            return new ArrayList<>();
        }
    }

    /**
     * 保存消息历史并刷新会话索引（用于该 channel 下的列表查询）。
     *
     * @param channel   会话频道
     * @param userId    当前用户 ID（为 null 时不入索引，但仍会保存历史）
     * @param sessionId 会话 ID
     * @param history   消息历史
     */
    public void saveHistory(String channel, Long userId, String sessionId, List<Message> history) {
        String ch = normalizeChannel(channel);
        if (sessionId == null || sessionId.isBlank() || history == null) {
            return;
        }
        long ttl = resolveTtl(ch);
        try {
            String json = objectMapper.writeValueAsString(history);
            redisTemplate.opsForValue().set(sessionKey(ch, sessionId), json, ttl, TimeUnit.SECONDS);
            if (userId != null) {
                String idxKey = indexKey(ch, userId);
                String metaKey = metaKey(ch, userId);
                redisTemplate.opsForSet().add(idxKey, sessionId);
                redisTemplate.expire(idxKey, ttl, TimeUnit.SECONDS);
                redisTemplate.opsForZSet().add(metaKey, sessionId, System.currentTimeMillis());
                redisTemplate.expire(metaKey, ttl, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            log.warn("Failed to save assistant session history: channel={}, sessionId={}", ch, sessionId, e);
        }
    }

    public void clearHistory(String channel, String sessionId) {
        String ch = normalizeChannel(channel);
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        try {
            redisTemplate.delete(sessionKey(ch, sessionId));
        } catch (Exception e) {
            log.warn("Failed to clear assistant session history: channel={}, sessionId={}", ch, sessionId, e);
        }
    }

    public void deleteSession(String channel, Long userId, String sessionId) {
        String ch = normalizeChannel(channel);
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        try {
            redisTemplate.delete(sessionKey(ch, sessionId));
            redisTemplate.delete(titleKey(ch, sessionId));
            if (userId != null) {
                String idxKey = indexKey(ch, userId);
                String metaKey = metaKey(ch, userId);
                redisTemplate.opsForSet().remove(idxKey, sessionId);
                redisTemplate.opsForZSet().remove(metaKey, sessionId);
            }
        } catch (Exception e) {
            log.warn("Failed to delete assistant session: channel={}, sessionId={}", ch, sessionId, e);
        }
    }

    /**
     * 重命名会话：写入标题覆盖值（空标题表示恢复默认，即删除覆盖）。
     * 同时刷新 ZSet 时间戳，让被重命名的会话排在列表最前。
     */
    public void renameSession(String channel, Long userId, String sessionId, String title) {
        String ch = normalizeChannel(channel);
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        try {
            String clean = title == null ? "" : title.trim();
            String key = titleKey(ch, sessionId);
            if (clean.isBlank()) {
                redisTemplate.delete(key);
            } else {
                redisTemplate.opsForValue().set(key, clean, resolveTtl(ch), TimeUnit.SECONDS);
            }
            if (userId != null) {
                redisTemplate.opsForZSet().add(metaKey(ch, userId), sessionId, System.currentTimeMillis());
            }
        } catch (Exception e) {
            log.warn("Failed to rename assistant session: channel={}, sessionId={}", ch, sessionId, e);
        }
    }

    /**
     * 清空当前用户在指定 channel 下的全部会话（含标题覆盖与索引）。
     */
    public void clearAllSessions(String channel, Long userId) {
        String ch = normalizeChannel(channel);
        if (userId == null) {
            return;
        }
        try {
            Set<String> ids = redisTemplate.opsForZSet().range(metaKey(ch, userId), 0, -1);
            if (ids != null && !ids.isEmpty()) {
                List<String> keys = new ArrayList<>(ids.size() * 2);
                for (String id : ids) {
                    keys.add(sessionKey(ch, id));
                    keys.add(titleKey(ch, id));
                }
                redisTemplate.delete(keys);
            }
            redisTemplate.delete(indexKey(ch, userId));
            redisTemplate.delete(metaKey(ch, userId));
        } catch (Exception e) {
            log.warn("Failed to clear all assistant sessions: channel={}, userId={}", ch, userId, e);
            throw new IllegalStateException("清空会话失败", e);
        }
    }

    public boolean exists(String channel, String sessionId) {
        String ch = normalizeChannel(channel);
        if (sessionId == null || sessionId.isBlank()) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(sessionKey(ch, sessionId)));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 校验某个会话是否属于指定用户（按 {@code assistant:index:<channel>:<userId>} 集合判定）。
     * 所有按 sessionId 直接读/改/删的接口必须先经此校验，避免跨账号越权（IDOR）。
     */
    public boolean isOwner(String channel, Long userId, String sessionId) {
        String ch = normalizeChannel(channel);
        if (sessionId == null || sessionId.isBlank() || userId == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(indexKey(ch, userId), sessionId));
        } catch (Exception e) {
            log.warn("Failed to check session ownership: channel={}, userId={}, sessionId={}", ch, userId, sessionId, e);
            return false;
        }
    }

    /**
     * 列出当前用户在指定 channel 下的会话摘要，按最近更新时间倒序。
     */
    public List<AssistantSessionSummary> listSessions(String channel, Long userId) {
        String ch = normalizeChannel(channel);
        if (userId == null) {
            return Collections.emptyList();
        }
        try {
            Set<String> ids = redisTemplate.opsForZSet().reverseRange(metaKey(ch, userId), 0, 99);
            if (ids == null || ids.isEmpty()) {
                return Collections.emptyList();
            }
            List<AssistantSessionSummary> results = new ArrayList<>(ids.size());
            for (String id : ids) {
                AssistantSessionSummary summary = buildSummary(ch, userId, id);
                if (summary != null) {
                    results.add(summary);
                }
            }
            return results;
        } catch (Exception e) {
            log.warn("Failed to list assistant sessions: channel={}, userId={}", ch, userId, e);
            // 抛出异常让上层返回明确错误，避免"历史记录服务不可用"伪装成"暂无历史对话"
            throw new IllegalStateException("会话历史服务暂不可用，请稍后重试", e);
        }
    }

    private AssistantSessionSummary buildSummary(String channel, Long userId, String sessionId) {
        try {
            String json = redisTemplate.opsForValue().get(sessionKey(channel, sessionId));
            if (json == null || json.isBlank()) {
                return null;
            }
            List<Message> messages = objectMapper.readValue(json, MESSAGE_LIST_TYPE);
            String title = null;
            String preview = null;
            // 用户重命名的标题优先；没有覆盖时取第一条 user 消息
            String overrideTitle = redisTemplate.opsForValue().get(titleKey(channel, sessionId));
            if (overrideTitle != null && !overrideTitle.isBlank()) {
                title = overrideTitle;
            }
            if (messages != null) {
                for (Message m : messages) {
                    if (title != null) {
                        break;
                    }
                    if ("user".equalsIgnoreCase(m.getRole()) && m.getContent() != null && !m.getContent().isBlank()) {
                        title = m.getContent();
                        break;
                    }
                }
                for (int i = messages.size() - 1; i >= 0; i--) {
                    Message m = messages.get(i);
                    if (m.getContent() != null && !m.getContent().isBlank()) {
                        preview = m.getContent();
                        break;
                    }
                }
            }
            if (title == null) {
                title = "新对话";
            }
            if (preview == null) {
                preview = "";
            }
            Long updatedAt;
            Double score = redisTemplate.opsForZSet().score(metaKey(channel, userId), sessionId);
            updatedAt = score == null ? System.currentTimeMillis() : score.longValue();
            return new AssistantSessionSummary(sessionId, title, preview, updatedAt);
        } catch (Exception e) {
            log.warn("Failed to build session summary: channel={}, sessionId={}", channel, sessionId, e);
            return null;
        }
    }
}