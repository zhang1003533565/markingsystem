package com.markingsystem.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.markingsystem.dto.assistant.AssistantChatRequest;
import com.markingsystem.dto.assistant.AssistantChatResponse;
import com.markingsystem.dto.assistant.AssistantSessionSummary;
import com.markingsystem.dto.assistant.AssistantStreamChunk;
import com.markingsystem.service.AssistantChatService;
import com.markingsystem.service.AssistantLongTermMemoryService;
import com.markingsystem.service.AssistantSessionRedisService;
import com.markingsystem.util.RequestUserContext;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/assistant")
public class AssistantChatController {

    private static final Logger log = LoggerFactory.getLogger(AssistantChatController.class);

    @Autowired
    private AssistantChatService assistantChatService;

    @Autowired
    private AssistantSessionRedisService sessionRedisService;

    @Autowired
    private AssistantLongTermMemoryService longTermMemoryService;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${ai.chat.enable:true}")
    private boolean chatEnabled;

    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(
            @RequestBody AssistantChatRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {

        if (!chatEnabled) {
            return ResponseEntity.ok(wrapResponse(200, "AI 助理功能已关闭", null));
        }

        RequestUserContext ctx = RequestUserContext.fromHeaders(userId, userRole, teacherId, className, studentNo);
        try {
            AssistantChatResponse response = assistantChatService.chat(request, ctx);
            return ResponseEntity.ok(wrapResponse(200, "success", response));
        } catch (Exception e) {
            log.warn("Assistant chat failed", e);
            return ResponseEntity.ok(wrapResponse(500, "AI 服务异常：" + e.getMessage(), null));
        }
    }

    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(
            @RequestParam(value = "channel", required = false) String channel,
            @RequestParam(value = "sessionId", required = false) String sessionId,
            @RequestParam(value = "message", required = false) String message,
            @RequestParam(value = "quickCommand", required = false) String quickCommand,
            @RequestParam(value = "history", required = false) String historyJson,
            @RequestParam(value = "resetTail", required = false) Boolean resetTail,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {

        SseEmitter emitter = new SseEmitter(0L);

        if (!chatEnabled) {
            sendAndComplete(emitter, "AI 助理功能已关闭");
            return emitter;
        }

        AssistantChatRequest request = new AssistantChatRequest();
        request.setChannel(channel);
        request.setSessionId(sessionId);
        request.setMessage(message);
        request.setQuickCommand(quickCommand);
        request.setResetTail(resetTail);
        if (historyJson != null && !historyJson.isBlank()) {
            try {
                request.setHistory(objectMapper.readValue(historyJson, objectMapper.getTypeFactory().constructCollectionType(java.util.List.class, com.markingsystem.service.llm.spi.Message.class)));
            } catch (IOException e) {
                log.warn("Failed to parse history json", e);
            }
        }

        RequestUserContext ctx = RequestUserContext.fromHeaders(userId, userRole, teacherId, className, studentNo);
        assistantChatService.streamChat(request, ctx, emitter);
        return emitter;
    }

    /**
     * 列出当前用户在指定 channel 下的会话摘要（按最近更新时间倒序）。
     */
    @GetMapping("/sessions")
    public ResponseEntity<Map<String, Object>> listSessions(
            @RequestParam(value = "channel", required = false) String channel,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        RequestUserContext ctx = RequestUserContext.fromHeaders(userId, userRole, teacherId, className, studentNo);
        try {
            List<AssistantSessionSummary> summaries = sessionRedisService.listSessions(channel, ctx.getUserId());
            return ResponseEntity.ok(wrapResponse(200, "success", summaries));
        } catch (Exception e) {
            log.warn("Failed to list assistant sessions", e);
            return ResponseEntity.ok(wrapResponse(500, "获取会话列表失败：" + e.getMessage(), null));
        }
    }

    /**
     * 删除某个 channel 下的指定会话。
     */
    @DeleteMapping("/session")
    public ResponseEntity<Map<String, Object>> deleteSession(
            @RequestParam(value = "channel", required = false) String channel,
            @RequestParam(value = "sessionId") String sessionId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        RequestUserContext ctx = RequestUserContext.fromHeaders(userId, userRole, teacherId, className, studentNo);
        try {
            if (!sessionRedisService.isOwner(channel, ctx.getUserId(), sessionId)) {
                return ResponseEntity.ok(wrapResponse(404, "会话不存在或无权访问", false));
            }
            assistantChatService.clearSession(channel, ctx.getUserId(), sessionId);
            return ResponseEntity.ok(wrapResponse(200, "success", true));
        } catch (Exception e) {
            log.warn("Failed to delete assistant session: channel={}, sessionId={}", channel, sessionId, e);
            return ResponseEntity.ok(wrapResponse(500, "删除会话失败：" + e.getMessage(), false));
        }
    }

    /**
     * 获取指定会话的历史消息。
     */
    @GetMapping("/session/{sessionId}/messages")
    public ResponseEntity<Map<String, Object>> getSessionMessages(
            @PathVariable String sessionId,
            @RequestParam(value = "channel", required = false) String channel,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        RequestUserContext ctx = RequestUserContext.fromHeaders(userId, userRole, teacherId, className, studentNo);
        try {
            String ch = sessionRedisService.normalizeChannel(channel);
            if (!sessionRedisService.isOwner(ch, ctx.getUserId(), sessionId)) {
                return ResponseEntity.ok(wrapResponse(404, "会话不存在或无权访问", List.of()));
            }
            List<com.markingsystem.service.llm.spi.Message> messages = sessionRedisService.getHistory(ch, sessionId);
            return ResponseEntity.ok(wrapResponse(200, "success", messages));
        } catch (Exception e) {
            log.warn("Failed to get session messages: sessionId={}", sessionId, e);
            return ResponseEntity.ok(wrapResponse(500, "获取会话消息失败：" + e.getMessage(), null));
        }
    }

    /**
     * 重命名指定 channel 下的某个会话（标题覆盖存储，空标题表示恢复默认）。
     */
    @PostMapping("/session/rename")
    public ResponseEntity<Map<String, Object>> renameSession(
            @RequestParam(value = "channel", required = false) String channel,
            @RequestParam(value = "sessionId") String sessionId,
            @RequestParam(value = "title", required = false) String title,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        RequestUserContext ctx = RequestUserContext.fromHeaders(userId, userRole, teacherId, className, studentNo);
        try {
            if (!sessionRedisService.isOwner(channel, ctx.getUserId(), sessionId)) {
                return ResponseEntity.ok(wrapResponse(404, "会话不存在或无权访问", false));
            }
            sessionRedisService.renameSession(channel, ctx.getUserId(), sessionId, title);
            return ResponseEntity.ok(wrapResponse(200, "success", true));
        } catch (Exception e) {
            log.warn("Failed to rename assistant session: sessionId={}", sessionId, e);
            return ResponseEntity.ok(wrapResponse(500, "重命名会话失败：" + e.getMessage(), false));
        }
    }

    /**
     * 清空当前用户在指定 channel 下的全部会话。
     */
    @DeleteMapping("/sessions")
    public ResponseEntity<Map<String, Object>> clearAllSessions(
            @RequestParam(value = "channel", required = false) String channel,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        RequestUserContext ctx = RequestUserContext.fromHeaders(userId, userRole, teacherId, className, studentNo);
        try {
            sessionRedisService.clearAllSessions(channel, ctx.getUserId());
            return ResponseEntity.ok(wrapResponse(200, "success", true));
        } catch (Exception e) {
            log.warn("Failed to clear all assistant sessions: channel={}", channel, e);
            return ResponseEntity.ok(wrapResponse(500, "清空会话失败：" + e.getMessage(), false));
        }
    }

    /**
     * 兼容旧版路径：按 sessionId 直接清除（不传 channel，按 floating 兜底）。
     */
    @DeleteMapping("/session/{sessionId}")
    public ResponseEntity<Map<String, Object>> clearSessionLegacy(
            @PathVariable String sessionId,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        try {
            Long parsedUserId = null;
            if (userId != null && !userId.isBlank()) {
                try {
                    parsedUserId = Long.parseLong(userId.trim());
                } catch (NumberFormatException ignored) {
                    // 身份无效时拒绝操作
                }
            }
            if (parsedUserId == null || !sessionRedisService.isOwner(null, parsedUserId, sessionId)) {
                return ResponseEntity.ok(wrapResponse(404, "会话不存在或无权访问", false));
            }
            assistantChatService.clearSession(null, parsedUserId, sessionId);
            return ResponseEntity.ok(wrapResponse(200, "success", true));
        } catch (Exception e) {
            log.warn("Failed to clear assistant session: {}", sessionId, e);
            return ResponseEntity.ok(wrapResponse(500, "清理会话失败：" + e.getMessage(), false));
        }
    }

    /**
     * 获取当前用户在指定 channel 下的长期记忆摘要元信息。
     */
    @GetMapping("/memory")
    public ResponseEntity<Map<String, Object>> getMemory(
            @RequestParam(value = "channel", required = false) String channel,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        RequestUserContext ctx = RequestUserContext.fromHeaders(userId, userRole, teacherId, className, studentNo);
        try {
            String ch = sessionRedisService.normalizeChannel(channel);
            if (!AssistantLongTermMemoryService.CHANNEL_PAGE.equals(ch) || !longTermMemoryService.isEnabled()) {
                Map<String, Object> payload = new HashMap<>();
                payload.put("enabled", longTermMemoryService.isEnabled());
                payload.put("count", 0);
                payload.put("preview", List.of());
                return ResponseEntity.ok(wrapResponse(200, "success", payload));
            }
            List<String> all = longTermMemoryService.listBullets(ctx.getUserId());
            List<String> preview = all.size() > 5 ? new ArrayList<>(all.subList(0, 5)) : all;
            Map<String, Object> payload = new HashMap<>();
            payload.put("enabled", true);
            payload.put("count", all.size());
            payload.put("preview", preview);
            return ResponseEntity.ok(wrapResponse(200, "success", payload));
        } catch (Exception e) {
            log.warn("Failed to read assistant memory", e);
            return ResponseEntity.ok(wrapResponse(500, "读取长期记忆失败：" + e.getMessage(), null));
        }
    }

    /**
     * 清空当前用户在指定 channel 下的长期记忆摘要与日期索引。
     */
    @DeleteMapping("/memory")
    public ResponseEntity<Map<String, Object>> clearMemory(
            @RequestParam(value = "channel", required = false) String channel,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        RequestUserContext ctx = RequestUserContext.fromHeaders(userId, userRole, teacherId, className, studentNo);
        try {
            String ch = sessionRedisService.normalizeChannel(channel);
            if (!AssistantLongTermMemoryService.CHANNEL_PAGE.equals(ch)) {
                return ResponseEntity.ok(wrapResponse(200, "当前 channel 不支持长期记忆", false));
            }
            longTermMemoryService.clear(ctx.getUserId());
            return ResponseEntity.ok(wrapResponse(200, "success", true));
        } catch (Exception e) {
            log.warn("Failed to clear assistant memory", e);
            return ResponseEntity.ok(wrapResponse(500, "清除长期记忆失败：" + e.getMessage(), false));
        }
    }

    private Map<String, Object> wrapResponse(int code, String message, Object data) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", code);
        result.put("message", message);
        result.put("data", data);
        return result;
    }

    private void sendAndComplete(SseEmitter emitter, String message) {
        try {
            // 前端按 JSON 解析 SSE data，禁用时必须发结构化 done 事件，否则页面会卡在"思考中"
            AssistantStreamChunk chunk = new AssistantStreamChunk(message, false);
            emitter.send(SseEmitter.event().data(chunk));
            AssistantStreamChunk done = new AssistantStreamChunk("", true);
            emitter.send(SseEmitter.event().data(done));
        } catch (Exception ignored) {
        } finally {
            emitter.complete();
        }
    }
}