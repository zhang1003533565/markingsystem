package com.markingsystem.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.markingsystem.service.llm.LlmChatService;
import com.markingsystem.service.llm.spi.ChatRequest;
import com.markingsystem.service.llm.spi.ChatResponse;
import com.markingsystem.service.llm.spi.Message;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 仿 OpenClaw "dreaming" 的会话压缩服务。
 *
 * <p>每次 chat/streamChat 成功后异步触发；触发条件为本次会话累计 user 消息数达到
 * 阈值（默认 6 的整数倍）。压缩产物通过 LLM 抽取 1-3 条 bullet（学生偏好 /
 * 重要事实 / 反复询问的主题），写入 {@link AssistantLongTermMemoryService}。
 *
 * <p>失败时退化为本地规则：取最近一条 user 消息前 60 字作为 bullet 写入。
 */
@Service
public class AssistantMemoryConsolidationService {

    private static final Logger log = LoggerFactory.getLogger(AssistantMemoryConsolidationService.class);

    @Autowired
    private AssistantLongTermMemoryService longTermMemoryService;

    @Autowired
    private LlmChatService llmChatService;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${ai.chat.page.memory.enabled:true}")
    private boolean memoryEnabled;

    @Value("${ai.chat.page.memory.consolidate-threshold:6}")
    private int consolidateThreshold;

    @Value("${ai.chat.page.memory.consolidate-on-every-send:false}")
    private boolean consolidateOnEverySend;

    private ExecutorService executor;

    @PostConstruct
    public void init() {
        ThreadFactory tf = new ThreadFactory() {
            private final AtomicInteger seq = new AtomicInteger();
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "assistant-memory-" + seq.incrementAndGet());
                t.setDaemon(true);
                return t;
            }
        };
        // core=2, max=4, queue=64，按计划约定
        this.executor = new ThreadPoolExecutor(2, 4, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(64), tf, new ThreadPoolExecutor.DiscardOldestPolicy());
    }

    @PreDestroy
    public void shutdown() {
        if (executor != null) {
            executor.shutdown();
        }
    }

    /**
     * 仅对 channel=page 生效。异步触发；返回的 Future 可被忽略。
     *
     * @param channel   会话频道
     * @param userId    用户 ID
     * @param sessionId 会话 ID
     * @param history   当前会话完整历史（已 trim 过）
     * @param userCount 该会话累计的 user 消息数（来自 Redis 计数器，不受 trim 影响）
     */
    public void consolidateIfNeeded(String channel, Long userId, String sessionId, List<Message> history, long userCount) {
        if (!memoryEnabled) {
            return;
        }
        if (!AssistantLongTermMemoryService.CHANNEL_PAGE.equals(channel)) {
            return;
        }
        if (userId == null || sessionId == null || history == null) {
            return;
        }

        boolean shouldFire;
        if (consolidateOnEverySend) {
            shouldFire = userCount >= 1;
        } else if (consolidateThreshold <= 0) {
            shouldFire = false;
        } else {
            shouldFire = userCount >= consolidateThreshold && (userCount % consolidateThreshold == 0);
        }
        if (!shouldFire) {
            // 仍然记录日期索引，但不写 bullet
            longTermMemoryService.recordSessionDate(userId, sessionId);
            return;
        }

        final List<Message> snapshot = new ArrayList<>(history);
        final Long uid = userId;
        final String sid = sessionId;
        try {
            executor.execute(() -> doConsolidate(uid, sid, snapshot));
        } catch (Exception e) {
            log.warn("Failed to submit consolidation task: userId={}, sessionId={}", uid, sid, e);
        }
    }

    private void doConsolidate(Long userId, String sessionId, List<Message> history) {
        try {
            // 仅取 user + assistant 配对，截短内容，避免 LLM 上下文过长
            List<Message> compact = new ArrayList<>();
            for (Message m : history) {
                if (m == null) {
                    continue;
                }
                String role = m.getRole();
                if (!"user".equalsIgnoreCase(role) && !"assistant".equalsIgnoreCase(role)) {
                    continue;
                }
                String content = m.getContent() == null ? "" : m.getContent();
                if (content.length() > 200) {
                    content = content.substring(0, 200) + "…";
                }
                Message mm = new Message(role, content);
                compact.add(mm);
                if (compact.size() >= 16) {
                    break;
                }
            }

            List<String> bullets = tryLlmExtract(userId, compact);
            if (bullets == null || bullets.isEmpty()) {
                bullets = localFallback(compact);
            }
            for (String b : bullets) {
                longTermMemoryService.appendBullet(userId, b);
            }
            longTermMemoryService.recordSessionDate(userId, sessionId);
        } catch (Exception e) {
            log.warn("Consolidation failed: userId={}, sessionId={}", userId, sessionId, e);
        }
    }

    private List<String> tryLlmExtract(Long userId, List<Message> compact) {
        try {
            ChatRequest req = new ChatRequest();
            List<Message> msgs = new ArrayList<>();
            msgs.add(new Message("system",
                    "你是建院 AI 助理的记忆整理助手。请根据下方学生与 AI 的对话记录，提取 1-3 条值得长期记住的事实或偏好。"
                            + "要求：1) 用中文；2) 每条不超过 30 字；3) 仅输出 bullet 列表，每行以 '-' 开头，"
                            + "不要解释、不要 JSON、不要多余文字。若无值得记忆的内容，输出单行 '-' 暂无。"));
            // 注入该用户已有的长期记忆，避免重复
            String existing = longTermMemoryService.getLongTermSummary(userId);
            if (!existing.isEmpty()) {
                msgs.add(new Message("system", "【已有长期记忆】\n" + existing));
            }
            StringBuilder convo = new StringBuilder();
            for (Message m : compact) {
                convo.append('[').append(m.getRole()).append("] ").append(m.getContent()).append('\n');
            }
            msgs.add(new Message("user", convo.toString()));
            req.setMessages(msgs);

            ChatResponse resp = llmChatService.chat(req);
            if (resp == null || resp.getContent() == null || resp.getContent().isBlank()) {
                return null;
            }
            List<String> out = new ArrayList<>();
            for (String line : resp.getContent().split("\\n")) {
                String t = line.replaceFirst("^[-\\s*]+", "").trim();
                if (!t.isEmpty() && !"暂无".equals(t) && !t.startsWith("暂无")) {
                    out.add(t);
                }
            }
            if (out.isEmpty()) {
                return null;
            }
            if (out.size() > 3) {
                out = out.subList(0, 3);
            }
            return out;
        } catch (Exception e) {
            log.debug("LLM extract failed, will fallback: {}", e.getMessage());
            return null;
        }
    }

    private List<String> localFallback(List<Message> compact) {
        List<String> out = new ArrayList<>();
        for (int i = compact.size() - 1; i >= 0 && out.size() < 2; i--) {
            Message m = compact.get(i);
            if (m != null && "user".equalsIgnoreCase(m.getRole())) {
                String c = m.getContent() == null ? "" : m.getContent();
                if (!c.isEmpty()) {
                    out.add(c.length() > 60 ? c.substring(0, 60) + "…" : c);
                }
            }
        }
        return out;
    }
}