import { memo, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  clearAllAssistantSessions,
  createAssistantStream,
  deleteAssistantSession,
  getAssistantMemory,
  getAssistantMessages,
  listAssistantSessions,
  renameAssistantSession
} from '../../api/assistant';
import { getStoredUser } from '../../utils/authStorage';
import StudentLayout from '../../components/StudentLayout';
import './AIChat.css';

const CHANNEL = 'page';

const PAGE_CARD_VIEW_LABELS = {
  hotJobs: '热门岗位',
  favorites: '我的收藏',
  growthPath: '成长链路',
  resources: '学习资源',
};

const PAGE_CARD_VIEWS = new Set(Object.keys(PAGE_CARD_VIEW_LABELS));

/** 简洁线框图标，跟消息操作按钮同一视觉语言 */
const PAGE_CARD_VIEW_ICONS = {
  hotJobs: (
    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <path d="M12 3l1.8 5.4H19l-4.2 3.2 1.6 5.4L12 13.8 7.6 17l1.6-5.4L5 8.4h5.2z" />
    </svg>
  ),
  favorites: (
    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <path d="M12 20s-7-4.4-7-10a4 4 0 017-2.6A4 4 0 0119 10c0 5.6-7 10-7 10z" />
    </svg>
  ),
  growthPath: (
    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <path d="M4 18V6M4 18h16M8 14l3-4 3 2 4-6" />
    </svg>
  ),
  resources: (
    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <path d="M4 5h7v14H5a1 1 0 01-1-1V5zM13 5h7v13a1 1 0 01-1 1h-6V5z" />
    </svg>
  ),
  default: (
    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <rect x="4" y="4" width="16" height="16" rx="3" />
      <path d="M8 12h8M12 8v8" />
    </svg>
  ),
};

function buildAssistantPageUrl(view, jobId) {
  const params = new URLSearchParams();
  if (view) params.set('view', view);
  if (jobId != null && jobId !== '') params.set('jobId', String(jobId));
  const qs = params.toString();
  return qs ? `/student/assistant?${qs}` : '/student/assistant';
}

const ROLE_SUGGESTIONS = {
  STUDENT: [
    '查询本学期成绩',
    '高数成绩是多少',
    '查看附加分申请进度',
  ],
  CLASS_COMMITTEE: [
    '查询本学期成绩',
    '班级成绩排名',
    '查看附加分申请进度',
  ],
};

const QUICK_COMMANDS = {
  '查询本学期成绩': 'query_score',
  '高数成绩是多少': 'query_score',
  '班级成绩排名': 'query_score',
  '查看附加分申请进度': 'query_extra_score_progress',
};

const ROLE_TITLES = {
  STUDENT: '同学',
  CLASS_COMMITTEE: '同学',
};

const STORAGE_KEY_PREFIX = `ai-chat:${CHANNEL}:`;

/**
 * 本地存储键按用户隔离：同一浏览器切换账号时不残留上一用户的会话 ID。
 */
function userScopedKey(suffix) {
  let uid = 'guest';
  try {
    const user = getStoredUser();
    if (user?.id) uid = String(user.id);
  } catch (_e) {
    // ignore
  }
  return `${STORAGE_KEY_PREFIX}${uid}:${suffix}`;
}

function readStoredSessionId() {
  try {
    return localStorage.getItem(userScopedKey('sessionId')) || null;
  } catch (_e) {
    return null;
  }
}

function writeStoredSessionId(id) {
  try {
    const key = userScopedKey('sessionId');
    if (id) {
      localStorage.setItem(key, id);
    } else {
      localStorage.removeItem(key);
    }
  } catch (_e) {
    // ignore
  }
}

function truncate(text, max = 18) {
  if (!text) return '';
  if (text.length <= max) return text;
  return `${text.slice(0, max)}…`;
}

function formatTime(ts) {
  if (!ts) return '';
  const date = new Date(ts);
  const now = new Date();
  const sameDay = date.toDateString() === now.toDateString();
  if (sameDay) {
    return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`;
  }
  return `${date.getMonth() + 1}/${date.getDate()}`;
}

function formatElapsed(ms) {
  if (!ms || ms < 0) return '';
  const s = ms / 1000;
  if (s < 60) return `${s.toFixed(1)}s`;
  const m = Math.floor(s / 60);
  const rest = Math.round(s % 60);
  return `${m}分${rest}秒`;
}

/* 知识库依据显示：优先文档名（上传文档去掉“上传文档：”前缀），否则回退到命中标题 */
function formatSourceName(s) {
  const raw = (s && (s.source || s.question)) || '';
  return raw.replace(/^上传文档：/, '') || '知识库';
}

/* ---------- 轻量 Markdown 渲染（零依赖，React 元素输出，天然防 XSS） ---------- */

function CodeBlock({ content }) {
  const [copied, setCopied] = useState(false);
  const handleCopy = useCallback(async () => {
    try {
      await navigator.clipboard.writeText(content);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch (_e) {
      // 剪贴板不可用时静默忽略
    }
  }, [content]);
  return (
    <div className="ai-md-code-wrap">
      <button
        type="button"
        className={`ai-md-code-copy ${copied ? 'copied' : ''}`}
        onClick={handleCopy}
        aria-label="复制代码"
      >
        {copied ? '已复制' : '复制'}
      </button>
      <pre className="ai-md-code">
        <code>{content}</code>
      </pre>
    </div>
  );
}

function renderMarkdown(text) {
  if (!text) return null;
  const lines = text.split('\n');
  const blocks = [];
  let inCode = false;
  let codeLang = '';
  let codeBuf = [];

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    const fence = line.match(/^```(\w*)/);
    if (fence) {
      if (!inCode) {
        inCode = true;
        codeLang = fence[1] || '';
        codeBuf = [];
      } else {
        blocks.push({ type: 'code', lang: codeLang, content: codeBuf.join('\n') });
        inCode = false;
      }
      continue;
    }
    if (inCode) {
      codeBuf.push(line);
      continue;
    }
    blocks.push({ type: 'line', content: line });
  }
  // 容错：流式中断时代码块未闭合，按整块代码收尾
  if (inCode) {
    blocks.push({ type: 'code', lang: codeLang, content: codeBuf.join('\n') });
  }

  return blocks.map((block, idx) => {
    if (block.type === 'code') {
      return <CodeBlock key={idx} content={block.content} />;
    }
    const line = block.content;
    if (!line.trim()) {
      return <div key={idx} className="ai-md-blank"></div>;
    }
    const bullet = line.match(/^\s*[-*•]\s+(.*)/);
    if (bullet) {
      return (
        <div key={idx} className="ai-md-li">
          <span>{renderInline(bullet[1])}</span>
        </div>
      );
    }
    const ordered = line.match(/^\s*\d+[.、]\s+(.*)/);
    if (ordered) {
      return (
        <div key={idx} className="ai-md-li">
          <span>{renderInline(ordered[1])}</span>
        </div>
      );
    }
    const heading = line.match(/^(#{1,4})\s+(.*)/);
    if (heading) {
      return (
        <p key={idx} className="ai-md-p" style={{ fontWeight: 700, fontSize: 15 }}>
          {renderInline(heading[2])}
        </p>
      );
    }
    return <p key={idx} className="ai-md-p">{renderInline(line)}</p>;
  });
}

function renderInline(text) {
  const INLINE_RE = /(`[^`\n]+`)|(\*\*[^*\n]+\*\*)|(\*[^*\n]+\*)|(\[([^\]]+)\]\(([^)\s]+)\))/g;
  const tokens = [];
  let last = 0;
  let m;
  while ((m = INLINE_RE.exec(text)) !== null) {
    if (m.index > last) {
      tokens.push({ t: 'text', v: text.slice(last, m.index) });
    }
    if (m[1]) tokens.push({ t: 'code', v: m[1].slice(1, -1) });
    else if (m[2]) tokens.push({ t: 'strong', v: m[2].slice(2, -2) });
    else if (m[3]) tokens.push({ t: 'em', v: m[3].slice(1, -1) });
    else if (m[4]) {
      const href = m[6];
      // 仅放行 http/https/mailto，其余协议按纯文本展示，避免模型输出构造危险链接
      if (/^(https?:|mailto:)/i.test(href)) {
        tokens.push({ t: 'link', v: m[5], href });
      } else {
        tokens.push({ t: 'text', v: m[0] });
      }
    }
    last = m.index + m[0].length;
  }
  if (last < text.length) {
    tokens.push({ t: 'text', v: text.slice(last) });
  }
  return tokens.map((tok, i) => {
    switch (tok.t) {
      case 'code':
        return <code key={i} className="ai-md-inline-code">{tok.v}</code>;
      case 'strong':
        return <strong key={i}>{renderInline(tok.v)}</strong>;
      case 'em':
        return <em key={i}>{renderInline(tok.v)}</em>;
      case 'link':
        return <a key={i} href={tok.href} target="_blank" rel="noreferrer">{tok.v}</a>;
      default:
        return <span key={i}>{tok.v}</span>;
    }
  });
}

function nextMessageId() {
  return `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

/**
 * 去掉历史数据里遗留的 quickCommand 前缀（如 "[query_score] 高数成绩是多少"），
 * 兼容修复前已保存的会话。
 */
function stripQuickCommand(text) {
  if (!text) return text;
  const m = text.match(/^\[[a-zA-Z_]+\]\s*(.*)$/);
  return m ? (m[1] || text) : text;
}

/**
 * 流式渲染预处理：隐藏未闭合的 Markdown 行内标记与孤立的块级符号，
 * 避免打字机过程中闪现 `**`、`` ` ``、`#`、`-` 等特殊符号。
 * 仅在流式期间使用；流结束后走完整渲染。
 *
 * 星号处理只剥离"类强调位置"的星号（前后非字词字符），
 * 数学表达式（如 3*4、a*b）中间的星号不会被误删。
 */
function sanitizeStreamingMarkdown(text) {
  if (!text) return text;
  let out = text;
  const occurrences = (s, sub) => s.split(sub).length - 1;

  // 行内代码 `` ` `` 未闭合：去掉最后一个
  if (occurrences(out, '`') % 2 === 1) {
    const idx = out.lastIndexOf('`');
    if (idx !== -1) out = out.slice(0, idx) + out.slice(idx + 1);
  }
  // 粗体 ** 未闭合（仅统计类强调位置的 **）：去掉最后一处
  out = balanceEmphasis(out);
  // 孤立的块级符号行（标题/列表标记，尚无内容）：隐藏
  out = out.replace(/^#{1,6}\s*$/gm, '');
  out = out.replace(/^\s*[-*•]\s*$/gm, '');
  out = out.replace(/^\s*\d+[.、]\s*$/gm, '');

  return out;
}

/**
 * 找出所有"类强调"星号标记位置：后跟非空白、前不是字词（或行首）。
 * 数学表达式 3*4 / a*b 中间的星号前后都是字词，不会被计入。
 * 然后从前到后两两配对，剩余的未闭合标记从后往前删除。
 */
function balanceEmphasis(text) {
  const positions = [];
  let i = 0;
  while (i < text.length) {
    if (text[i] !== '*') {
      i += 1;
      continue;
    }
    const prev = i > 0 ? text[i - 1] : '';
    const next = i + 2 < text.length ? text[i + 2] : '';
    const prevWord = /[\w\u4e00-\u9fff]/.test(prev);
    const afterPairSpace = /\s/.test(next) || next === '';
    if (text[i + 1] === '*') {
      // 粗体 **：后一个星号后跟空白（如 "** "）或前是字词则不算强调
      if (!afterPairSpace && !prevWord) {
        positions.push({ start: i, len: 2 });
      }
      i += 2;
      continue;
    }
    const nextChar = i + 1 < text.length ? text[i + 1] : '';
    const nextSpace = /\s/.test(nextChar) || nextChar === '';
    if (!nextSpace && !prevWord) {
      positions.push({ start: i, len: 1 });
    }
    i += 1;
  }

  // 同长度标记两两配对（粗体对粗体、斜体对斜体）
  const used = new Array(positions.length).fill(false);
  const unclosed = [];
  for (let p = 0; p < positions.length; p += 1) {
    if (used[p]) continue;
    let matched = false;
    for (let q = p + 1; q < positions.length; q += 1) {
      if (used[q]) continue;
      if (positions[q].len === positions[p].len) {
        used[p] = true;
        used[q] = true;
        matched = true;
        break;
      }
    }
    if (!matched) unclosed.push(positions[p]);
  }

  if (unclosed.length === 0) return text;
  unclosed.sort((a, b) => b.start - a.start);
  let out = text;
  for (const pos of unclosed) {
    out = out.slice(0, pos.start) + out.slice(pos.start + pos.len);
  }
  return out;
}

/**
 * 单条对话消息（memo 化）。
 * SSE 流式回复时，父组件每次 chunk 都会重建 messages 数组，但只有正在流式的那条
 * msg 引用/内容变化；其余消息若 props 不变则被 memo 短路，不重复执行 renderMarkdown，
 * 避免长对话在打字机过程中全量重渲染。
 */
const ChatMessage = memo(function ChatMessage({
  msg,
  copiedId,
  onCopy,
  onRegenerate,
  onToggleFeedback,
  onOpenPageCard,
}) {
  return (
    <div className={`ai-chat-message ${msg.from}`}>
      <div className="ai-chat-msg-wrap">
        <div className={`ai-chat-bubble ${msg._error ? 'ai-error-bubble' : ''}`}>
          {msg.from === 'ai' ? (
            msg._streaming && !msg.text ? (
              <span className="ai-chat-thinking">
                <span className="ai-thinking-dot"></span>
                <span className="ai-thinking-dot"></span>
                <span className="ai-thinking-dot"></span>
              </span>
            ) : (
              <>
                {renderMarkdown(msg._streaming ? sanitizeStreamingMarkdown(msg.text) : msg.text)}
                {msg._streaming && <span className="ai-stream-cursor"></span>}
              </>
            )
          ) : (
            renderMarkdown(msg.text)
          )}
        </div>
        {msg.from === 'ai' && !msg._streaming && (
          <div className="ai-chat-msg-meta">
            {msg._stopped && <span className="ai-chat-msg-meta-item">已停止</span>}
            {msg._model && <span className="ai-chat-msg-meta-item">{msg._model}</span>}
            {msg._elapsedMs != null && (
              <span className="ai-chat-msg-meta-item">耗时 {formatElapsed(msg._elapsedMs)}</span>
            )}
          </div>
        )}
        {msg.from === 'ai' &&
          !msg._streaming &&
          Array.isArray(msg._cards) &&
          msg._cards.length > 0 && (
            <div className="ai-chat-page-cards">
              <span className="ai-chat-page-cards-label">相关页面</span>
              <div className="ai-chat-page-cards-list">
                {msg._cards.map((card, idx) => {
                  const viewLabel = PAGE_CARD_VIEW_LABELS[card.view] || card.view;
                  const key = `${card.view || 'v'}-${card.recommendationId ?? 'x'}-${idx}`;
                  const sub = card.subtitle || card.jobTitle;
                  return (
                    <button
                      key={key}
                      type="button"
                      className={`ai-chat-page-card ai-chat-page-card--${card.view || 'default'}`}
                      onClick={() => onOpenPageCard?.(card)}
                    >
                      <span className="ai-chat-page-card-icon" aria-hidden="true">
                        {PAGE_CARD_VIEW_ICONS[card.view] || PAGE_CARD_VIEW_ICONS.default}
                      </span>
                      <span className="ai-chat-page-card-body">
                        <span className="ai-chat-page-card-kicker">{viewLabel}</span>
                        <span className="ai-chat-page-card-title">{card.title || viewLabel}</span>
                        {sub ? <span className="ai-chat-page-card-sub">{sub}</span> : null}
                      </span>
                      <span className="ai-chat-page-card-arrow" aria-hidden="true">
                        <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                          <path d="M9 6l6 6-6 6" />
                        </svg>
                      </span>
                    </button>
                  );
                })}
              </div>
            </div>
          )}
        {msg.from === 'ai' &&
          !msg._streaming &&
          Array.isArray(msg._sources) &&
          msg._sources.length > 0 && (
            <div className="ai-chat-sources">
              <span className="ai-chat-sources-label">📖 知识库依据</span>
              {msg._sources.map((s, idx) => (
                <span key={idx} className="ai-chat-source-item">
                  {formatSourceName(s)}
                </span>
              ))}
            </div>
          )}
        {msg.from === 'ai' && !msg._streaming && (
          <div className="ai-chat-msg-actions">
            <button
              type="button"
              className={`ai-chat-msg-action ${copiedId === msg._id ? 'copied' : ''}`}
              onClick={() => onCopy(msg)}
            >
              {copiedId === msg._id ? (
                <>
                  <svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round">
                    <path d="M20 6L9 17l-5-5" />
                  </svg>
                  已复制
                </>
              ) : (
                <>
                  <svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                    <rect x="9" y="9" width="13" height="13" rx="2" />
                    <path d="M5 15H4a2 2 0 01-2-2V4a2 2 0 012-2h9a2 2 0 012 2v1" />
                  </svg>
                  复制
                </>
              )}
            </button>
            <button type="button" className="ai-chat-msg-action" onClick={onRegenerate}>
              {msg._error ? (
                <>
                  <svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                    <path d="M23 4v6h-6M1 20v-6h6" />
                    <path d="M3.51 9a9 9 0 0114.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0020.49 15" />
                  </svg>
                  重试
                </>
              ) : (
                <>
                  <svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                    <path d="M1 4v6h6" />
                    <path d="M3.51 15a9 9 0 102.13-9.36L1 10" />
                  </svg>
                  重新生成
                </>
              )}
            </button>
            <span className="ai-chat-msg-action-divider"></span>
            <button
              type="button"
              className={`ai-chat-msg-action ai-feedback-btn ${msg._feedback === 'up' ? 'active-up' : ''}`}
              onClick={() => onToggleFeedback(msg, 'up')}
              aria-label="有帮助"
              title="有帮助"
            >
              <svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M14 9V5a3 3 0 00-3-3l-4 9v11h11.28a2 2 0 002-1.7l1.38-9a2 2 0 00-2-2.3zM7 22H4a2 2 0 01-2-2v-7a2 2 0 012-2h3" />
              </svg>
            </button>
            <button
              type="button"
              className={`ai-chat-msg-action ai-feedback-btn ${msg._feedback === 'down' ? 'active-down' : ''}`}
              onClick={() => onToggleFeedback(msg, 'down')}
              aria-label="没帮助"
              title="没帮助"
            >
              <svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M10 15v4a3 3 0 003 3l4-9V2H5.72a2 2 0 00-2 1.7l-1.38 9a2 2 0 002 2.3zM17 2h3a2 2 0 012 2v7a2 2 0 01-2 2h-3" />
              </svg>
            </button>
          </div>
        )}
      </div>
    </div>
  );
});

/**
 * AI 对话页。
 * - 独立路由 `/student/ai-chat`：带 StudentLayout。
 * - embedded：嵌在发展助手内部视图，与独立页共用 channel=page 的会话/消息数据。
 * - initialPrompt：外部快捷提问（如发展助手概览建议），会话就绪后自动发送一次。
 * - onOpenAssistantPage：内嵌时优先用回调切视图；独立页则 navigate 深链。
 */
function AIChat({
  embedded = false,
  initialPrompt = null,
  onInitialPromptConsumed,
  onOpenAssistantPage,
}) {
  const navigate = useNavigate();
  const storedUser = getStoredUser();
  const role = storedUser?.role || 'STUDENT';
  const suggestions = ROLE_SUGGESTIONS[role] || ROLE_SUGGESTIONS.STUDENT;
  const title = ROLE_TITLES[role] || ROLE_TITLES.STUDENT;

  const [sessions, setSessions] = useState([]);
  const [sessionsError, setSessionsError] = useState(false);
  const [activeSessionId, setActiveSessionId] = useState(() => readStoredSessionId());
  const [messages, setMessages] = useState([]);
  const [input, setInput] = useState('');
  const [showWelcome, setShowWelcome] = useState(true);
  const [streaming, setStreaming] = useState(false);
  const [loadingHistory, setLoadingHistory] = useState(false);
  const [bootstrapped, setBootstrapped] = useState(false);
  const [memorySummary, setMemorySummary] = useState({ enabled: false, count: 0, preview: [] });
  const [sidebarOpen, setSidebarOpen] = useState(() => {
    if (typeof window === 'undefined') return true;
    return window.innerWidth > 720;
  });
  const [copiedId, setCopiedId] = useState(null);
  // 会话管理：搜索关键字 / 重命名中的会话
  const [sessionSearch, setSessionSearch] = useState('');
  const [renamingId, setRenamingId] = useState(null);
  const [renameValue, setRenameValue] = useState('');

  const bodyRef = useRef(null);
  const inputRef = useRef(null);
  const streamingRef = useRef(null);
  const messagesRef = useRef([]);
  // 发送起始时间（用于计算回复耗时）
  const sendStartRef = useRef(0);
  // 是否贴底（用户上滑阅读历史时停止自动滚动）
  const stickRef = useRef(true);
  // 组件是否已卸载：异步 setTimeout 回调前需判断，避免对卸载组件 setState
  const mountedRef = useRef(true);
  // 外部快捷提问只消费一次（挂载时快照，避免父级清空后重新拉起旧会话）
  const initialPromptRef = useRef(
    typeof initialPrompt === 'string' ? initialPrompt.trim() : ''
  );
  const initialPromptConsumedRef = useRef(false);

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
      // 发展助手内切换视图会卸载本组件，需关闭进行中的 SSE
      if (streamingRef.current) {
        streamingRef.current.close();
        streamingRef.current = null;
      }
    };
  }, []);

  // 宽屏时自动展开侧边栏；窄屏保留用户的选择
  useEffect(() => {
    const onResize = () => {
      if (window.innerWidth > 720) {
        setSidebarOpen(true);
      }
    };
    window.addEventListener('resize', onResize);
    return () => window.removeEventListener('resize', onResize);
  }, []);

  useEffect(() => {
    messagesRef.current = messages;
  }, [messages]);

  // 输入框随内容自动增高（上限约 5 行）
  useEffect(() => {
    const el = inputRef.current;
    if (!el) return;
    el.style.height = 'auto';
    el.style.height = `${Math.min(el.scrollHeight, 132)}px`;
  }, [input]);

  // 消息区滚动：记录用户是否贴底，贴底才自动滚动到最新
  useEffect(() => {
    const body = bodyRef.current;
    if (!body) return;
    const handleScroll = () => {
      stickRef.current = body.scrollHeight - body.scrollTop - body.clientHeight < 80;
    };
    body.addEventListener('scroll', handleScroll, { passive: true });
    return () => body.removeEventListener('scroll', handleScroll);
  }, []);

  useEffect(() => {
    const body = bodyRef.current;
    if (body && stickRef.current) {
      body.scrollTop = body.scrollHeight;
    }
  }, [messages]);

  useEffect(() => {
    writeStoredSessionId(activeSessionId);
  }, [activeSessionId]);

  const refreshMemory = useCallback(async () => {
    try {
      const res = await getAssistantMemory(CHANNEL);
      const data = res?.data;
      if (data && typeof data === 'object') {
        setMemorySummary({
          enabled: !!data.enabled,
          count: Number(data.count) || 0,
          preview: Array.isArray(data.preview) ? data.preview : [],
        });
      }
    } catch (_e) {
      // ignore: 记忆能力可能未启用
    }
  }, []);

  useEffect(() => {
    refreshMemory();
  }, [refreshMemory]);

  const refreshSessions = useCallback(async () => {
    try {
      const res = await listAssistantSessions(CHANNEL);
      const list = Array.isArray(res?.data) ? res.data : [];
      setSessions(list);
      setSessionsError(false);
      return list;
    } catch (_e) {
      console.warn('[AIChat] refreshSessions failed', _e);
      setSessionsError(true);
      return [];
    }
  }, []);

  const loadSessionMessages = useCallback(async (sessionId) => {
    if (!sessionId) return;
    setLoadingHistory(true);
    // 切换会话后回到底部
    stickRef.current = true;
    try {
      const res = await getAssistantMessages(CHANNEL, sessionId);
      const list = Array.isArray(res?.data) ? res.data : [];
      const formatted = list.map((m) => ({
        from: m.role === 'user' ? 'user' : 'ai',
        text: stripQuickCommand(m.content || ''),
        _id: nextMessageId(),
        _sources: Array.isArray(m.sources) ? m.sources : [],
        _cards: Array.isArray(m.cards) ? m.cards.filter((c) => c && PAGE_CARD_VIEWS.has(c.view)) : [],
      }));
      setMessages(formatted);
      setShowWelcome(formatted.length === 0);
    } catch (_e) {
      console.warn('[AIChat] loadSessionMessages failed', _e);
      setMessages([]);
      setShowWelcome(true);
    } finally {
      setLoadingHistory(false);
    }
  }, []);

  // Initial load: list sessions, then resume last session
  // 若带外部快捷提问：新建空白对话，避免旧历史覆盖刚发出的问题
  useEffect(() => {
    let cancelled = false;
    (async () => {
      const list = await refreshSessions();
      if (cancelled) return;
      const pending = initialPromptRef.current;
      if (pending) {
        setActiveSessionId(null);
        setMessages([]);
        setShowWelcome(true);
        setBootstrapped(true);
        return;
      }
      const stored = readStoredSessionId();
      const found = stored && list.find((s) => s.sessionId === stored);
      if (found) {
        setActiveSessionId(stored);
        await loadSessionMessages(stored);
      } else if (list.length > 0) {
        const latest = list[0];
        setActiveSessionId(latest.sessionId);
        await loadSessionMessages(latest.sessionId);
      } else {
        setActiveSessionId(null);
        setShowWelcome(true);
      }
      if (!cancelled) setBootstrapped(true);
    })();
    return () => {
      cancelled = true;
    };
  }, [refreshSessions, loadSessionMessages]);

  const handleSend = useCallback((textOverride, commandOverride, opts) => {
    const text = typeof textOverride === 'string' ? textOverride : input.trim();
    if (!text || streaming) return;

    const quickCommand = typeof commandOverride === 'string' ? commandOverride : undefined;
    const resetTail = Boolean(opts?.resetTail);
    let sessionId = activeSessionId;
    if (!sessionId) {
      // 首条消息即固定会话 ID：失败重试时沿用同一会话，避免分裂
      sessionId = `sess_${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
      setActiveSessionId(sessionId);
    }

    setInput('');
    setShowWelcome(false);
    // 发送后跟随最新消息滚动
    stickRef.current = true;
    // 记录发送时刻，用于计算回复耗时
    sendStartRef.current = Date.now();

    const userMessage = { from: 'user', text, _id: nextMessageId() };
    setMessages((prev) => [...prev, userMessage]);

    const aiMessageId = nextMessageId();
    setMessages((prev) => [...prev, { from: 'ai', text: '', _streaming: true, _id: aiMessageId }]);
    setStreaming(true);

    const params = {
      channel: CHANNEL,
      sessionId: sessionId || undefined,
      message: text,
      quickCommand: quickCommand || undefined,
      resetTail,
    };

    const onChunk = (data) => {
      setMessages((prev) => {
        const idx = prev.findIndex((m) => m._id === aiMessageId);
        if (idx === -1) return prev;
        const updated = [...prev];
        updated[idx] = { ...updated[idx], text: updated[idx].text + (data.chunk || '') };
        return updated;
      });
    };

    const onDone = (data) => {
      setStreaming(false);
      streamingRef.current = null;
      const newSessionId = data?.sessionId;
      if (newSessionId && newSessionId !== sessionId) {
        setActiveSessionId(newSessionId);
      }
      const elapsedMs = sendStartRef.current ? Date.now() - sendStartRef.current : 0;
      setMessages((prev) => {
        const idx = prev.findIndex((m) => m._id === aiMessageId);
        if (idx === -1) return prev;
        const updated = [...prev];
        updated[idx] = {
          from: 'ai',
          text: updated[idx].text || '',
          _id: aiMessageId,
          _sources: Array.isArray(data?.sources) ? data.sources : [],
          _cards: Array.isArray(data?.cards)
            ? data.cards.filter((c) => c && PAGE_CARD_VIEWS.has(c.view))
            : [],
          _model: data?.model || '',
          _provider: data?.provider || '',
          _elapsedMs: elapsedMs,
        };
        return updated;
      });
      refreshSessions();
      // 延迟刷新记忆摘要；避免组件已卸载后仍 setState
      setTimeout(() => {
        if (mountedRef.current) refreshMemory();
      }, 2000);
    };

    const onError = () => {
      setStreaming(false);
      streamingRef.current = null;
      const elapsedMs = sendStartRef.current ? Date.now() - sendStartRef.current : 0;
      setMessages((prev) => {
        const idx = prev.findIndex((m) => m._id === aiMessageId);
        if (idx === -1) return prev;
        const updated = [...prev];
        updated[idx] = {
          from: 'ai',
          text: '回复生成中断（网络或服务异常），可点击下方"重试"。',
          _id: aiMessageId,
          _error: true,
          _elapsedMs: elapsedMs,
        };
        return updated;
      });
    };

    // 发送前关闭上一个可能仍持有引用的流，避免连接泄漏
    if (streamingRef.current) {
      streamingRef.current.close();
      streamingRef.current = null;
    }
    streamingRef.current = createAssistantStream(params, onChunk, onDone, onError);
  }, [input, activeSessionId, streaming, refreshSessions, refreshMemory]);

  // 发展助手概览等外部入口：会话就绪后自动发送一次快捷问题
  useEffect(() => {
    if (!bootstrapped || streaming || loadingHistory) return;
    if (initialPromptConsumedRef.current) return;
    const text = initialPromptRef.current;
    if (!text) return;
    initialPromptConsumedRef.current = true;
    initialPromptRef.current = '';
    if (typeof onInitialPromptConsumed === 'function') {
      onInitialPromptConsumed();
    }
    handleSend(text);
  }, [bootstrapped, streaming, loadingHistory, handleSend, onInitialPromptConsumed]);

  /**
   * 重新生成：截断到最后一条 user 消息，重发该问题，替换其后所有 AI 回复。
   * resetTail 让后端同步回滚 Redis 历史，避免旧回复残留（刷新后复活 / 污染上下文）。
   */
  const regenerate = useCallback(() => {
    if (streaming) return;
    // 从 ref 读取最新消息，避免依赖 messages 导致每帧重建，破坏 ChatMessage 的 memo
    const list = messagesRef.current;
    const lastUserIdx = (() => {
      for (let i = list.length - 1; i >= 0; i--) {
        if (list[i].from === 'user') return i;
      }
      return -1;
    })();
    if (lastUserIdx === -1) return;
    const lastUserText = list[lastUserIdx].text;
    if (!lastUserText) return;
    setMessages((prev) => prev.slice(0, lastUserIdx));
    setShowWelcome(false);
    handleSend(lastUserText, undefined, { resetTail: true });
  }, [streaming, handleSend]);

  const handleCopy = useCallback(async (msg) => {
    try {
      await navigator.clipboard.writeText(msg.text);
      setCopiedId(msg._id);
      setTimeout(() => {
        setCopiedId((cur) => (cur === msg._id ? null : cur));
      }, 1500);
    } catch (_e) {
      // 剪贴板不可用时静默忽略
    }
  }, []);

  const handleOpenPageCard = useCallback((card) => {
    if (!card?.view || !PAGE_CARD_VIEWS.has(card.view)) return;
    const jobId = card.recommendationId != null ? card.recommendationId : null;
    if (typeof onOpenAssistantPage === 'function') {
      onOpenAssistantPage({ view: card.view, jobId });
      return;
    }
    navigate(buildAssistantPageUrl(card.view, jobId));
  }, [navigate, onOpenAssistantPage]);

  const handleKeyDown = (e) => {
    // 中文输入法选词回车（isComposing / keyCode 229）不触发发送
    if (e.key !== 'Enter' || e.nativeEvent.isComposing || e.keyCode === 229) return;
    if (e.shiftKey) {
      // Shift+Enter 换行
      return;
    }
    e.preventDefault();
    handleSend();
  };

  /**
   * 停止生成：中断当前 SSE 流，保留已生成的半截回复。
   */
  const stopGeneration = useCallback(() => {
    if (streamingRef.current) {
      streamingRef.current.close();
      streamingRef.current = null;
    }
    setStreaming(false);
    const elapsedMs = sendStartRef.current ? Date.now() - sendStartRef.current : 0;
    setMessages((prev) => prev.map((m) => (
      m._streaming ? { ...m, _streaming: false, _elapsedMs: elapsedMs, _stopped: true } : m
    )));
    refreshSessions();
  }, [refreshSessions]);

  /**
   * 重置聊天主区域（不触碰侧边栏会话列表）。
   */
  const resetChatArea = () => {
    if (streamingRef.current) {
      streamingRef.current.close();
      streamingRef.current = null;
    }
    setActiveSessionId(null);
    setMessages([]);
    setShowWelcome(true);
    setInput('');
    setStreaming(false);
    stickRef.current = true;
    // 收起自动增高的输入框
    const el = inputRef.current;
    if (el) {
      el.style.height = 'auto';
    }
  };

  const handleNewChat = () => {
    resetChatArea();
    // 保留侧边栏历史会话列表：新建对话只重置主区域，历史记录随时可切回
  };

  const handleSelectSession = (sessionId) => {
    if (!sessionId || sessionId === activeSessionId) return;
    if (streamingRef.current) {
      streamingRef.current.close();
      streamingRef.current = null;
    }
    // 切换会话必须复位 streaming 状态，否则输入框会一直停留在“正在回复”的禁用态
    setStreaming(false);
    setActiveSessionId(sessionId);
    loadSessionMessages(sessionId);
  };

  const handleDeleteSession = async (sessionId, e) => {
    if (e) {
      e.stopPropagation();
    }
    if (!sessionId) return;
    if (!window.confirm('确认删除这段对话？删除后不可恢复。')) {
      return;
    }
    try {
      await deleteAssistantSession(CHANNEL, sessionId);
      setSessions((prev) => prev.filter((s) => s.sessionId !== sessionId));
      if (activeSessionId === sessionId) {
        // 只重置主区域，不清空列表（其他历史会话仍在）
        resetChatArea();
      }
    } catch (_e) {
      // ignore; user can retry
    }
  };

  /**
   * 清空全部会话（含当前对话）。
   */
  const handleClearAllSessions = async () => {
    if (sessions.length === 0) return;
    if (!window.confirm(`确认清空全部 ${sessions.length} 段对话？此操作不可恢复。`)) {
      return;
    }
    try {
      await clearAllAssistantSessions(CHANNEL);
      setSessions([]);
      resetChatArea();
    } catch (_e) {
      // ignore; user can retry
    }
  };

  const startRename = (session, e) => {
    if (e) {
      e.stopPropagation();
    }
    setRenamingId(session.sessionId);
    setRenameValue(stripQuickCommand(session.title) || '新对话');
  };

  const cancelRename = () => {
    setRenamingId(null);
    setRenameValue('');
  };

  const saveRename = async (sessionId) => {
    const nextTitle = renameValue.trim();
    setRenamingId(null);
    setRenameValue('');
    if (!nextTitle || nextTitle === stripQuickCommand(sessions.find((s) => s.sessionId === sessionId)?.title)) {
      return;
    }
    try {
      await renameAssistantSession(CHANNEL, sessionId, nextTitle);
      setSessions((prev) => prev.map((s) => (
        s.sessionId === sessionId ? { ...s, title: nextTitle } : s
      )));
    } catch (_e) {
      // ignore; 刷新后恢复原名
    }
  };

  const handleRenameKeyDown = (e, sessionId) => {
    if (e.nativeEvent.isComposing || e.keyCode === 229) return;
    if (e.key === 'Enter') {
      e.preventDefault();
      saveRename(sessionId);
    } else if (e.key === 'Escape') {
      cancelRename();
    }
  };

  const toggleFeedback = useCallback((msg, value) => {
    setMessages((prev) => prev.map((m) => {
      if (m._id !== msg._id) return m;
      return { ...m, _feedback: m._feedback === value ? null : value };
    }));
  }, []);

  const handleSuggestionClick = (item) => {
    handleSend(item, QUICK_COMMANDS[item]);
  };

  const filteredSessions = useMemo(() => {
    const kw = sessionSearch.trim().toLowerCase();
    if (!kw) return sessions;
    return sessions.filter((s) => {
      const title = stripQuickCommand(s.title) || '';
      return title.toLowerCase().includes(kw) || (s.preview || '').toLowerCase().includes(kw);
    });
  }, [sessions, sessionSearch]);

  const activeSession = useMemo(
    () => sessions.find((s) => s.sessionId === activeSessionId),
    [sessions, activeSessionId]
  );

  const shellClassName = [
    'ai-chat-shell',
    sidebarOpen ? 'sidebar-open' : '',
    embedded ? 'ai-chat-shell--embedded' : '',
  ]
    .filter(Boolean)
    .join(' ');

  const pageContent = (
      <div className={shellClassName}>
        <div className="ai-chat-sidebar-mask" onClick={() => setSidebarOpen(false)} />
        <aside className="ai-chat-sidebar">
          <button
            type="button"
            className="ai-chat-new-btn"
            onClick={handleNewChat}
          >
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M12 5v14M5 12h14" />
            </svg>
            <span>新建对话</span>
          </button>
          <div className="ai-chat-session-search-wrap">
            <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2" className="ai-chat-session-search-icon">
              <circle cx="11" cy="11" r="7" />
              <path d="M21 21l-4.35-4.35" />
            </svg>
            <input
              type="text"
              className="ai-chat-session-search"
              placeholder="搜索对话…"
              value={sessionSearch}
              onChange={(e) => setSessionSearch(e.target.value)}
            />
            {sessionSearch && (
              <button
                type="button"
                className="ai-chat-session-search-clear"
                aria-label="清空搜索"
                onClick={() => setSessionSearch('')}
              >
                ×
              </button>
            )}
          </div>
          <div className="ai-chat-sidebar-title">最近对话</div>
          <div className="ai-chat-session-list">
            {sessions.length === 0 && !sessionsError && (
              <div className="ai-chat-session-empty">暂无历史对话</div>
            )}
            {sessions.length === 0 && sessionsError && (
              <div className="ai-chat-session-empty ai-session-error">
                历史记录加载失败，请稍后重试
              </div>
            )}
            {sessions.length > 0 && filteredSessions.length === 0 && (
              <div className="ai-chat-session-empty">未找到匹配的对话</div>
            )}
            {filteredSessions.map((s) => (
              <div
                key={s.sessionId}
                className={`ai-chat-session-item ${s.sessionId === activeSessionId ? 'active' : ''}`}
                onClick={() => handleSelectSession(s.sessionId)}
              >
                <div className="ai-chat-session-text">
                  {renamingId === s.sessionId ? (
                    <input
                      type="text"
                      className="ai-chat-session-rename-input"
                      value={renameValue}
                      autoFocus
                      onChange={(e) => setRenameValue(e.target.value)}
                      onKeyDown={(e) => handleRenameKeyDown(e, s.sessionId)}
                      onBlur={() => saveRename(s.sessionId)}
                      onClick={(e) => e.stopPropagation()}
                      maxLength={30}
                    />
                  ) : (
                    <div className="ai-chat-session-title">{truncate(stripQuickCommand(s.title) || '新对话', 22)}</div>
                  )}
                  <div className="ai-chat-session-meta">
                    <span className="ai-chat-session-time">{formatTime(s.updatedAt)}</span>
                    {s.preview && (
                      <span className="ai-chat-session-preview">{truncate(stripQuickCommand(s.preview), 16)}</span>
                    )}
                  </div>
                </div>
                <button
                  type="button"
                  className="ai-chat-session-rename"
                  aria-label="重命名对话"
                  title="重命名"
                  onClick={(e) => startRename(s, e)}
                >
                  <svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                    <path d="M17 3a2.828 2.828 0 114 4L7.5 20.5 2 22l1.5-5.5L17 3z" />
                  </svg>
                </button>
                <button
                  type="button"
                  className="ai-chat-session-delete"
                  aria-label="删除对话"
                  title="删除"
                  onClick={(e) => handleDeleteSession(s.sessionId, e)}
                >
                  <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2">
                    <path d="M3 6h18M8 6V4a2 2 0 012-2h4a2 2 0 012 2v2M19 6l-1 14a2 2 0 01-2 2H8a2 2 0 01-2-2L5 6" />
                  </svg>
                </button>
              </div>
            ))}
          </div>
          {sessions.length > 0 && (
            <button
              type="button"
              className="ai-chat-clear-all"
              onClick={handleClearAllSessions}
            >
              <svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M3 6h18M8 6V4a2 2 0 012-2h4a2 2 0 012 2v2M19 6l-1 14a2 2 0 01-2 2H8a2 2 0 01-2-2L5 6" />
              </svg>
              清空全部对话
            </button>
          )}
        </aside>

        <div className="ai-chat-main">
          <div className="ai-chat-header">
            <div className="ai-chat-header-left">
              <button
                type="button"
                className="ai-chat-sidebar-toggle"
                onClick={() => setSidebarOpen((v) => !v)}
                aria-label="切换对话列表"
                title="对话列表"
              >
                <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
                  <path d="M3 6h18M3 12h18M3 18h12" />
                </svg>
              </button>
              <div className="ai-chat-title">
                <span className="ai-title-highlight">建院AI助理</span>
                {activeSession && (
                  <span className="ai-chat-title-sub">{truncate(stripQuickCommand(activeSession.title), 30)}</span>
                )}
              </div>
            </div>
            <div className="ai-chat-actions">
              <button
                type="button"
                className="ai-chat-action-btn"
                onClick={handleNewChat}
                aria-label="新对话"
                title="新对话"
              >
                <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2">
                  <rect x="3" y="3" width="18" height="18" rx="2" />
                  <path d="M8 12h8M12 8v8" />
                </svg>
              </button>
            </div>
          </div>

          <div ref={bodyRef} className="ai-chat-body">
            {showWelcome && messages.length === 0 && (
              <div className="ai-welcome">
                <div className="ai-welcome-text">
                  <h2>
                    {title}你好，我是<span className="ai-highlight">建院AI助理</span>
                  </h2>
                  {memorySummary.enabled && memorySummary.preview.length > 0 ? (
                    <>
                      <h3>我还记得你之前问过：</h3>
                      <ul className="ai-memory-preview">
                        {memorySummary.preview.slice(0, 3).map((item, idx) => (
                          <li key={idx}>{item}</li>
                        ))}
                      </ul>
                    </>
                  ) : (
                    <>
                      <h3>欢迎随时提问</h3>
                      <p>有问题随时找我，我会尽力帮你解答~</p>
                    </>
                  )}
                </div>
                <div className="ai-suggestions">
                  {suggestions.map((item, index) => (
                    <button
                      key={index}
                      type="button"
                      className="ai-suggestion-item"
                      onClick={() => handleSuggestionClick(item)}
                      disabled={streaming}
                    >
                      <span className="ai-suggestion-icon">✦</span>
                      <span className="ai-suggestion-text">{item}</span>
                      <span className="ai-suggestion-arrow">→</span>
                    </button>
                  ))}
                </div>
              </div>
            )}

            {loadingHistory ? (
              <div className="ai-chat-loading-history">
                <span className="ai-loading-dot"></span>
                加载历史消息中…
              </div>
            ) : (
              messages.map((msg) => (
                <ChatMessage
                  key={msg._id}
                  msg={msg}
                  copiedId={copiedId}
                  onCopy={handleCopy}
                  onRegenerate={regenerate}
                  onToggleFeedback={toggleFeedback}
                  onOpenPageCard={handleOpenPageCard}
                />
              ))
            )}
          </div>

          <div className="ai-chat-footer">
            <div className="ai-input-box">
              <textarea
                ref={inputRef}
                value={input}
                onChange={(e) => setInput(e.target.value)}
                onKeyDown={handleKeyDown}
                placeholder={streaming ? 'AI 正在回复…' : '有什么关于建院的问题，都可以问我（Enter 发送，Shift+Enter 换行）'}
                disabled={streaming}
                className="ai-chat-input"
                rows={1}
              />
              <div className="ai-input-tools">
                <span className="ai-input-hint">Enter 发送 · Shift+Enter 换行</span>
                {streaming ? (
                  <button
                    type="button"
                    className="ai-chat-stop"
                    onClick={stopGeneration}
                    aria-label="停止生成"
                    title="停止生成"
                  >
                    <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor">
                      <rect x="6" y="6" width="12" height="12" rx="2" />
                    </svg>
                  </button>
                ) : (
                  <button
                    type="button"
                    className={`ai-chat-send ${input.trim() ? 'active' : ''}`}
                    onClick={() => handleSend()}
                    disabled={!input.trim()}
                    aria-label="发送"
                  >
                    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2">
                      <path d="M22 2L11 13M22 2l-7 20-4-9-9-4 20-7z" />
                    </svg>
                  </button>
                )}
              </div>
            </div>
            <p className="ai-disclaimer">推荐和回答由 AI 生成，仅供参考</p>
          </div>
        </div>
      </div>
  );

  if (embedded) {
    return pageContent;
  }

  return (
    <StudentLayout activePath="/student/ai-chat">
      {pageContent}
    </StudentLayout>
  );
}

export default AIChat;
