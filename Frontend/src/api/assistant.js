import request from '../utils/request';
import { clearStoredUser, getStoredUser } from '../utils/authStorage';

export const getAssistantSummary = () => {
  return request({
    url: '/assistant/summary',
    method: 'get'
  });
};

export const assistantChat = (data, config = {}) => {
  return request({
    url: '/assistant/chat',
    method: 'post',
    data,
    // 非流式同步调用需等待后端 LLM 响应（后端 sync-timeout 20s），覆盖 axios 全局 10s 超时
    timeout: 60000,
    ...config
  });
};

export const clearAssistantSession = (sessionId) => {
  return request({
    url: `/assistant/session/${sessionId}`,
    method: 'delete'
  });
};

/**
 * 列出当前用户在指定 channel 下的历史会话摘要（按最近更新时间倒序）。
 * channel: 'floating' | 'page'
 */
export const listAssistantSessions = (channel) => {
  return request({
    url: '/assistant/sessions',
    method: 'get',
    params: { channel }
  });
};

/**
 * 删除指定 channel 下的某个会话。
 */
export const deleteAssistantSession = (channel, sessionId) => {
  return request({
    url: '/assistant/session',
    method: 'delete',
    params: { channel, sessionId }
  });
};

/**
 * 重命名指定 channel 下的某个会话（空标题恢复默认）。
 */
export const renameAssistantSession = (channel, sessionId, title) => {
  return request({
    url: '/assistant/session/rename',
    method: 'post',
    params: { channel, sessionId, title }
  });
};

/**
 * 清空当前用户在指定 channel 下的全部会话。
 */
export const clearAllAssistantSessions = (channel) => {
  return request({
    url: '/assistant/sessions',
    method: 'delete',
    params: { channel }
  });
};

/**
 * 创建流式对话 SSE 连接。
 *
 * 注意：原生 EventSource 无法携带 Authorization 头（JWT 拦截器会拒绝 401），
 * 因此这里使用 fetch + ReadableStream 解析 SSE 流，可携带完整认证与身份头。
 *
 * @param {Object} params - 包含 sessionId、message、quickCommand、history、channel
 * @param {Function} onChunk - 每次收到文本增量的回调
 * @param {Function} onDone - 流式结束回调
 * @param {Function} onError - 错误回调
 * @returns {{ close: Function }} 兼容 EventSource 的 close() 接口
 */
export const createAssistantStream = (params, onChunk, onDone, onError) => {
  const query = new URLSearchParams();
  if (params.channel) query.append('channel', params.channel);
  if (params.sessionId) query.append('sessionId', params.sessionId);
  if (params.message) query.append('message', params.message);
  if (params.quickCommand) query.append('quickCommand', params.quickCommand);
  if (params.resetTail) query.append('resetTail', 'true');
  if (params.history) query.append('history', JSON.stringify(params.history));

  const url = `/api/assistant/chat/stream?${query.toString()}`;
  const controller = new AbortController();
  const user = getStoredUser();

  const headers = { Accept: 'text/event-stream' };
  if (user.token) {
    headers.Authorization = `Bearer ${user.token}`;
  }
  if (user.id) {
    headers['X-User-Id'] = user.id;
  }
  if (user.role) {
    headers['X-User-Role'] = user.role;
  }
  if (user.teacherId) {
    headers['X-Teacher-Id'] = user.teacherId;
  }
  if (user.nickname || user.username) {
    headers['X-User-Name'] = encodeURIComponent(user.nickname || user.username);
  }
  if (user.className) {
    headers['X-Class-Name'] = encodeURIComponent(user.className);
  }
  if (user.studentNo) {
    headers['X-Student-No'] = user.studentNo;
  }

  (async () => {
    try {
      const response = await fetch(url, { headers, signal: controller.signal });
      if (!response.ok) {
        if (response.status === 401) {
          // 与 request.js 拦截器行为一致：登录失效则清理并跳转
          clearStoredUser();
          window.location.href = '/login';
          return;
        }
        let message = `请求失败（HTTP ${response.status}）`;
        try {
          const data = await response.json();
          if (data?.message) {
            message = data.message;
          }
        } catch (_e) {
          // 非 JSON 错误体保持默认文案
        }
        onError?.(new Error(message));
        return;
      }

      const reader = response.body.getReader();
      const decoder = new TextDecoder('utf-8');
      let buffer = '';
      let finished = false;

      // 空闲超时保护：若长时间（默认 60s）未收到任何数据行，判定后端半挂并中断，
      // 避免 reader.read() 永久挂起导致 UI 一直停在"思考中"。
      const IDLE_TIMEOUT_MS = 60000;
      let idleTimer = null;
      const clearIdle = () => {
        if (idleTimer) {
          clearTimeout(idleTimer);
          idleTimer = null;
        }
      };
      const armIdle = () => {
        clearIdle();
        idleTimer = setTimeout(() => {
          controller.abort();
          onError?.(new Error('回复生成超时（连接空闲过久），请重试'));
        }, IDLE_TIMEOUT_MS);
      };
      armIdle();

      while (!finished) {
        const { done, value } = await reader.read();
        if (done) break;
        if (value?.byteLength) armIdle();
        buffer += decoder.decode(value, { stream: true });

        let newlineIndex;
        while ((newlineIndex = buffer.indexOf('\n')) !== -1) {
          const line = buffer.slice(0, newlineIndex).trim();
          buffer = buffer.slice(newlineIndex + 1);
          if (!line.startsWith('data:')) continue;
          const payload = line.slice(5).trim();
          try {
            const data = JSON.parse(payload);
            if (data.done) {
              finished = true;
              clearIdle();
              onDone?.(data);
              break;
            }
            armIdle();
            onChunk?.(data);
          } catch (_e) {
            // 忽略非 JSON 数据行
          }
        }
      }
      clearIdle();

      // 服务端在未发送 done 事件前就关闭了流（后端异常 / 连接中断 / nginx 超时）：
      // 必须显式回调 onError，否则页面会一直停留在"思考中"状态
      if (!finished) {
        reader.cancel().catch(() => {});
        onError?.(new Error('回复生成中断（连接异常），请重试'));
      }
    } catch (err) {
      if (err.name === 'AbortError') return;
      onError?.(err);
    }
  })();

  // 兼容调用方的 streamingRef.current.close()
  return {
    close: () => controller.abort(),
  };
};

/**
 * 获取当前用户在指定 channel 下的长期记忆摘要。
 */
export const getAssistantMemory = (channel) => {
  return request({
    url: '/assistant/memory',
    method: 'get',
    params: { channel }
  });
};

/**
 * 清除当前用户在指定 channel 下的长期记忆摘要。
 */
export const clearAssistantMemory = (channel) => {
  return request({
    url: '/assistant/memory',
    method: 'delete',
    params: { channel }
  });
};

/**
 * 获取指定会话的历史消息。
 * @param {string} channel - 会话频道
 * @param {string} sessionId - 会话 ID
 */
export const getAssistantMessages = (channel, sessionId) => {
  return request({
    url: `/assistant/session/${sessionId}/messages`,
    method: 'get',
    params: { channel }
  });
};
