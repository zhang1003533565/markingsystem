import { useEffect, useRef, useState } from 'react'
import { assistantChat } from '../api/assistant'
import renderMarkdown from '../utils/markdown'
import { getStoredUser } from '../utils/authStorage'
import './AIFloatingChat.css'

const CHANNEL = 'floating'

const ROLE_SUGGESTIONS = {
  STUDENT: ['查询本学期成绩', '高数成绩是多少', '查看附加分申请进度'],
  CLASS_COMMITTEE: ['查询本学期成绩', '班级成绩排名', '查看附加分申请进度'],
  TEACHER: ['我现在管着几个班', '查看班级成绩统计', '审批学生附加分申请'],
  ADMIN: ['查看全校成绩统计', '管理系统用户', '发布公告通知'],
}

const QUICK_COMMANDS = {
  查询本学期成绩: 'query_score',
  高数成绩是多少: 'query_score',
  班级成绩排名: 'query_score',
  查看附加分申请进度: 'query_extra_score_progress',
  我现在管着几个班: 'query_teacher_classes',
  查看班级成绩统计: 'query_teacher_classes',
  审批学生附加分申请: 'query_teacher_pending_reviews',
  查看全校成绩统计: 'query_admin_overview',
  管理系统用户: 'query_admin_users',
  发布公告通知: 'query_admin_announcements',
}

const ROLE_TITLES = {
  STUDENT: '同学',
  CLASS_COMMITTEE: '同学',
  TEACHER: '老师',
  ADMIN: '管理员',
}

const STORAGE_KEY_PREFIX = `ai-floating-chat:${CHANNEL}:sessionId`

// 悬浮球会话 id 按用户隔离存储：共用浏览器（如机房电脑）时不同账号
// 不会读到彼此遗留的 floating sessionId，避免串号读取/污染他人会话。
function storageKey() {
  try {
    const user = getStoredUser()
    const uid = user?.id != null ? String(user.id) : 'anon'
    return `${STORAGE_KEY_PREFIX}:${uid}`
  } catch (_e) {
    return `${STORAGE_KEY_PREFIX}:anon`
  }
}

function readStoredSessionId() {
  try {
    return localStorage.getItem(storageKey()) || null
  } catch (_e) {
    return null
  }
}

function writeStoredSessionId(id) {
  try {
    if (id) {
      localStorage.setItem(storageKey(), id)
    } else {
      localStorage.removeItem(storageKey())
    }
  } catch (_e) {
    // ignore
  }
}

function generateSessionId() {
  return `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`
}

function AIFloatingChat({ role = 'STUDENT' }) {
  const [open, setOpen] = useState(false)
  const [messages, setMessages] = useState([])
  const [input, setInput] = useState('')
  const [showWelcome, setShowWelcome] = useState(true)
  const [darkMode, setDarkMode] = useState(false)
  const [sessionId, setSessionId] = useState(() => readStoredSessionId())
  const [loading, setLoading] = useState(false)
  const [dragging, setDragging] = useState(false)

  const orbRef = useRef(null)
  const bodyRef = useRef(null)
  const draggingRef = useRef(false)
  const dragMovedRef = useRef(false)
  const offsetRef = useRef({ x: 0, y: 0 })
  const startRef = useRef({ x: 0, y: 0 })

  useEffect(() => {
    const orb = orbRef.current
    if (!orb) return

    const handleMouseDown = (e) => {
      draggingRef.current = true
      dragMovedRef.current = false
      setDragging(true)
      const rect = orb.getBoundingClientRect()
      offsetRef.current = {
        x: e.clientX - rect.left,
        y: e.clientY - rect.top,
      }
      startRef.current = { x: e.clientX, y: e.clientY }
      orb.style.transition = 'none'
    }

    const handleMouseMove = (e) => {
      if (!draggingRef.current) return
      const dx = Math.abs(e.clientX - startRef.current.x)
      const dy = Math.abs(e.clientY - startRef.current.y)
      if (dx > 3 || dy > 3) {
        dragMovedRef.current = true
      }
      e.preventDefault()
      let x = e.clientX - offsetRef.current.x
      let y = e.clientY - offsetRef.current.y
      const size = orb.offsetWidth
      const maxX = window.innerWidth - size
      const maxY = window.innerHeight - size
      x = Math.max(0, Math.min(x, maxX))
      y = Math.max(0, Math.min(y, maxY))
      orb.style.left = `${x}px`
      orb.style.top = `${y}px`
      orb.style.right = 'auto'
      orb.style.bottom = 'auto'
    }

    const handleMouseUp = () => {
      if (draggingRef.current) {
        draggingRef.current = false
        setDragging(false)
        orb.style.transition = 'transform 0.2s ease, box-shadow 0.2s ease'
      }
    }

    orb.addEventListener('mousedown', handleMouseDown)
    window.addEventListener('mousemove', handleMouseMove)
    window.addEventListener('mouseup', handleMouseUp)

    return () => {
      orb.removeEventListener('mousedown', handleMouseDown)
      window.removeEventListener('mousemove', handleMouseMove)
      window.removeEventListener('mouseup', handleMouseUp)
    }
  }, [])

  const handleOrbClick = () => {
    if (dragMovedRef.current) {
      dragMovedRef.current = false
      return
    }
    setOpen((prev) => !prev)
  }

  useEffect(() => {
    const body = bodyRef.current
    if (body) {
      body.scrollTop = body.scrollHeight
    }
  }, [messages, loading])

  useEffect(() => {
    writeStoredSessionId(sessionId)
  }, [sessionId])

  const normalizedRole = ['STUDENT', 'CLASS_COMMITTEE', 'TEACHER', 'ADMIN'].includes(role)
    ? role
    : 'STUDENT'
  const suggestions = ROLE_SUGGESTIONS[normalizedRole] || ROLE_SUGGESTIONS.STUDENT
  const title = ROLE_TITLES[normalizedRole] || ROLE_TITLES.STUDENT

  const handleSend = (textOverride, commandOverride) => {
    const text = typeof textOverride === 'string' ? textOverride : input.trim()
    if (!text || loading) return

    const quickCommand = typeof commandOverride === 'string' ? commandOverride : undefined
    let sid = sessionId
    if (!sid) {
      sid = generateSessionId()
      setSessionId(sid)
    }
    setShowWelcome(false)
    setLoading(true)
    setMessages((prev) => [...prev, { from: 'user', text }])
    setInput('')

    const historyPayload = messages
      .filter((m) => (m.from === 'user' || m.from === 'ai') && m.text)
      .slice(-10)
      .map((m) => ({ role: m.from === 'user' ? 'user' : 'assistant', content: m.text }))

    assistantChat({
      channel: CHANNEL,
      sessionId: sid,
      message: text,
      quickCommand,
      history: historyPayload,
    })
      .then((res) => {
        const answer = res?.data?.content || res?.content || '暂无回复'
        const returnedSid = res?.data?.sessionId || sid
        setMessages((prev) => [...prev, { from: 'ai', text: answer }])
        if (returnedSid && returnedSid !== sid) {
          setSessionId(returnedSid)
        }
      })
      .catch((err) => {
        setMessages((prev) => [
          ...prev,
          { from: 'ai', text: `请求失败：${err?.message || 'AI 服务暂时不可用，请稍后重试。'}` },
        ])
      })
      .finally(() => {
        setLoading(false)
      })
  }

  const handleKeyDown = (e) => {
    // 中文输入法选词回车（isComposing / keyCode 229）不触发发送
    if (e.key !== 'Enter' || e.nativeEvent.isComposing || e.keyCode === 229) return
    e.preventDefault()
    handleSend()
  }

  const handleNewChat = () => {
    setSessionId(generateSessionId())
    setMessages([])
    setShowWelcome(true)
    setInput('')
    setLoading(false)
  }

  const handleSuggestionClick = (item) => {
    handleSend(item, QUICK_COMMANDS[item])
  }

  const toggleTheme = () => {
    setDarkMode((prev) => !prev)
  }

  return (
    <div className="ai-floating-chat">
      <div
        ref={orbRef}
        className={`ai-floating-orb ${dragging ? 'is-dragging' : ''}`}
        onClick={handleOrbClick}
        role="button"
        tabIndex={0}
        aria-label="打开 AI 助手"
      >
        <Pet variant="orb" dragging={dragging} />
        <span className="ai-orb-badge" aria-hidden="true">
          ✦
        </span>
      </div>

      {open && (
        <div className={`afc-dialog ${darkMode ? 'dark' : ''}`}>
          <div className="afc-header">
            <div className="afc-brand">
              <div className="afc-header-pet">
                <Pet variant="header" thinking={loading} />
              </div>
              <div className="afc-title">
                <span className="afc-title-name">建院AI助理</span>
                <span className={`afc-status ${loading ? 'is-loading' : ''}`}>
                  <i className="afc-status-dot" />
                  {loading ? '思考中' : '在线'}
                </span>
              </div>
            </div>
            <div className="afc-actions">
              <button
                type="button"
                className="afc-action-btn"
                onClick={handleNewChat}
                aria-label="新对话"
                title="新对话"
              >
                <svg
                  viewBox="0 0 24 24"
                  width="18"
                  height="18"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2"
                >
                  <rect x="3" y="3" width="18" height="18" rx="2" />
                  <path d="M8 12h8M12 8v8" />
                </svg>
              </button>
              <button
                type="button"
                className="afc-action-btn"
                onClick={toggleTheme}
                aria-label={darkMode ? '切换浅色模式' : '切换深色模式'}
                title={darkMode ? '切换浅色模式' : '切换深色模式'}
              >
                {darkMode ? (
                  <svg
                    viewBox="0 0 24 24"
                    width="18"
                    height="18"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="2"
                  >
                    <circle cx="12" cy="12" r="5" />
                    <path d="M12 1v2M12 21v2M4.22 4.22l1.42 1.42M18.36 18.36l1.42 1.42M1 12h2M21 12h2M4.22 19.78l1.42-1.42M18.36 5.64l1.42-1.42" />
                  </svg>
                ) : (
                  <svg
                    viewBox="0 0 24 24"
                    width="18"
                    height="18"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="2"
                  >
                    <path d="M21 12.79A9 9 0 1111.21 3 7 7 0 0021 12.79z" />
                  </svg>
                )}
              </button>
            </div>
            <button
              type="button"
              className="afc-close"
              onClick={() => setOpen(false)}
              aria-label="关闭"
            >
              ×
            </button>
          </div>

          <div ref={bodyRef} className="afc-body">
            {showWelcome && messages.length === 0 && (
              <div className="afc-welcome">
                <div className="afc-welcome-pet">
                  <Pet variant="welcome" />
                </div>
                <div className="afc-welcome-text">
                  <h2>
                    {title}你好，我是<span className="afc-highlight">建院AI助理</span>
                  </h2>
                  <h3>欢迎随时提问</h3>
                  <p>有问题随时找我，我会尽力帮你解答~</p>
                </div>
                <div className="afc-suggestions">
                  {suggestions.map((item, index) => (
                    <button
                      key={index}
                      type="button"
                      className="afc-suggestion-item"
                      onClick={() => handleSuggestionClick(item)}
                      disabled={loading}
                    >
                      <span className="afc-suggestion-icon">✦</span>
                      <span className="afc-suggestion-text">{item}</span>
                      <span className="afc-suggestion-arrow">→</span>
                    </button>
                  ))}
                </div>
              </div>
            )}

            {!showWelcome &&
              messages.map((msg, index) => (
                <div key={index} className={`afc-message ${msg.from}`}>
                  {msg.from === 'ai' && (
                    <div className="afc-msg-avatar">
                      <Pet variant="tiny" />
                    </div>
                  )}
                  <div className={`afc-bubble ${msg.from === 'ai' ? 'afc-md' : ''}`}>
                    {msg.from === 'ai' ? renderMarkdown(msg.text) : msg.text}
                  </div>
                </div>
              ))}

            {loading && !showWelcome && (
              <div className="afc-message ai">
                <div className="afc-msg-avatar">
                  <Pet variant="tiny" thinking />
                </div>
                <div className="afc-bubble afc-loading-bubble">
                  <span className="afc-dot" />
                  <span className="afc-dot" />
                  <span className="afc-dot" />
                </div>
              </div>
            )}
          </div>

          <div className="afc-footer">
            <div className="afc-input-box">
              <input
                type="text"
                value={input}
                onChange={(e) => setInput(e.target.value)}
                onKeyDown={handleKeyDown}
                placeholder={loading ? 'AI 正在思考…' : '有什么关于建院的问题，都可以问我'}
                disabled={loading}
                className="afc-input"
              />
              <div className="afc-input-tools">
                <button
                  type="button"
                  className={`afc-send ${input.trim() ? 'active' : ''}`}
                  onClick={() => handleSend()}
                  disabled={loading}
                  aria-label="发送"
                >
                  <svg
                    viewBox="0 0 24 24"
                    width="18"
                    height="18"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="2"
                  >
                    <path d="M22 2L11 13M22 2l-7 20-4-9-9-4 20-7z" />
                  </svg>
                </button>
              </div>
            </div>
            <p className="afc-disclaimer">推荐和回答由 AI 生成，仅供参考</p>
          </div>
        </div>
      )}
    </div>
  )
}

function Pet({ variant = 'orb', thinking = false, dragging = false }) {
  const cls = [
    'pet',
    `pet--${variant}`,
    thinking ? 'pet--thinking' : '',
    dragging ? 'pet--dragging' : '',
  ]
    .filter(Boolean)
    .join(' ')

  return (
    <div className={cls} aria-hidden="true">
      <svg viewBox="0 0 120 120" className="pet-svg">
        <defs>
          <linearGradient id="petBody" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="#5aa2ff" />
            <stop offset="55%" stopColor="#1677ff" />
            <stop offset="100%" stopColor="#0958d9" />
          </linearGradient>
        </defs>
        {/* 闪电尾巴 */}
        <path className="pet-tail" d="M88 70 L105 63 L97 75 L107 79 L90 89 Z" />
        {/* 耳朵 */}
        <path
          className="pet-ear pet-ear--l"
          d="M34 46 C26 30 34 16 46 20 C54 23 56 32 54 42 C48 46 40 48 34 46 Z"
        />
        <path
          className="pet-ear pet-ear--r"
          d="M86 46 C94 30 86 16 74 20 C66 23 64 32 66 42 C72 46 80 48 86 46 Z"
        />
        <path
          className="pet-ear-inner pet-ear-inner--l"
          d="M37 42 C32 32 38 24 45 26 C50 28 51 34 49 40 C45 43 40 44 37 42 Z"
        />
        <path
          className="pet-ear-inner pet-ear-inner--r"
          d="M83 42 C88 32 82 24 75 26 C70 28 69 34 71 40 C75 43 80 44 83 42 Z"
        />
        {/* 身体与肚皮 */}
        <ellipse className="pet-body" cx="60" cy="70" rx="34" ry="36" fill="url(#petBody)" />
        <ellipse className="pet-belly" cx="60" cy="80" rx="20" ry="21" />
        {/* 脚 */}
        <ellipse className="pet-foot pet-foot--l" cx="45" cy="103" rx="11" ry="7" />
        <ellipse className="pet-foot pet-foot--r" cx="75" cy="103" rx="11" ry="7" />
        {/* 常规眼睛：圆瞳 + 眨眼 */}
        <g className="pet-eyes-normal">
          <g className="pet-eye pet-eye--l">
            <ellipse className="pet-eye-white" cx="49" cy="60" rx="9" ry="11" />
            <circle className="pet-pupil" cx="49" cy="62" r="5" />
            <circle className="pet-glint" cx="46.5" cy="58.5" r="2" />
          </g>
          <g className="pet-eye pet-eye--r">
            <ellipse className="pet-eye-white" cx="71" cy="60" rx="9" ry="11" />
            <circle className="pet-pupil" cx="71" cy="62" r="5" />
            <circle className="pet-glint" cx="68.5" cy="58.5" r="2" />
          </g>
        </g>
        {/* 思考眼：> < */}
        <g className="pet-eyes-busy">
          <path className="pet-busy" d="M43 70 Q50 62 43 54" />
          <path className="pet-busy" d="M77 54 Q70 62 77 70" />
        </g>
        {/* 拖拽眼：- - */}
        <g className="pet-eyes-line">
          <path className="pet-line" d="M42 62 L56 62" />
          <path className="pet-line" d="M64 62 L78 62" />
        </g>
        {/* 腮红 */}
        <ellipse className="pet-blush pet-blush--l" cx="38" cy="75" rx="5.5" ry="3.4" />
        <ellipse className="pet-blush pet-blush--r" cx="82" cy="75" rx="5.5" ry="3.4" />
        {/* 嘴巴：微笑 / 思考小圆 */}
        <path className="pet-mouth" d="M55 76 Q60 81 65 76" />
        <circle className="pet-mouth-o" cx="60" cy="78" r="4" />
      </svg>
    </div>
  )
}

export default AIFloatingChat
