import { useCallback, useEffect, useState } from 'react'
import { Empty, message, Spin, Tooltip } from 'antd'
import './KnowledgeLogs.css'
import './KnowledgeBase.css'
import { getKnowledgeChatLogs, deleteKnowledgeChatLog } from '../../api/admin/knowledgeChatLogs'
import {
  getTeacherKnowledgeChatLogs,
  deleteTeacherKnowledgeChatLog,
} from '../../api/teacher/knowledgeChatLogs'
import { getTeacherSummaryClasses } from '../../api/teacher/classAnswerSummary'
import { confirmAction } from '../../components/confirmDialogService'
import { getStoredUser } from '../../utils/authStorage'

const formatTime = (value) => (value ? String(value).replace('T', ' ').slice(0, 16) : '—')

const snippetOf = (text, max = 140) =>
  String(text || '')
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, max) +
  (String(text || '')
    .replace(/\s+/g, ' ')
    .trim().length > max
    ? '…'
    : '')

function KnowledgeLogs() {
  const user = getStoredUser()
  const isTeacher = user.role === 'TEACHER'

  const [logs, setLogs] = useState([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [size] = useState(20)
  const [loading, setLoading] = useState(false)

  const [keyword, setKeyword] = useState('')
  const [className, setClassName] = useState('')
  const [classes, setClasses] = useState([])

  useEffect(() => {
    if (!isTeacher) return
    let cancelled = false
    ;(async () => {
      try {
        const res = await getTeacherSummaryClasses()
        if (cancelled) return
        const data = Array.isArray(res?.data) ? res.data : []
        setClasses(data)
      } catch (_e) {
        // 拦截器已提示
      }
    })()
    return () => {
      cancelled = true
    }
  }, [isTeacher])

  const fetchLogs = useCallback(
    async (targetPage = 1) => {
      setLoading(true)
      try {
        const fetcher = isTeacher ? getTeacherKnowledgeChatLogs : getKnowledgeChatLogs
        const res = await fetcher({
          keyword: keyword.trim() || undefined,
          className: className || undefined,
          page: targetPage,
          size,
        })
        const data = res?.data || {}
        setLogs(Array.isArray(data.data) ? data.data : [])
        setTotal(data.total ?? 0)
        setPage(data.page ?? targetPage)
      } catch (_e) {
        // 拦截器已提示
      } finally {
        setLoading(false)
      }
    },
    [keyword, className, size, isTeacher],
  )

  useEffect(() => {
    fetchLogs(1)
  }, [fetchLogs])

  const handleDelete = async (log) => {
    const ok = await confirmAction({
      title: '删除该条对话记录？',
      message: '删除后不可恢复。',
      confirmText: '删除',
      tone: 'danger',
    })
    if (!ok) return
    try {
      const remover = isTeacher ? deleteTeacherKnowledgeChatLog : deleteKnowledgeChatLog
      await remover(log.id)
      message.success('已删除')
      fetchLogs(page)
    } catch (_e) {
      // 拦截器已提示
    }
  }

  const pageCount = Math.max(1, Math.ceil(total / size))
  const pages = []
  for (let i = 1; i <= pageCount; i++) pages.push(i)

  return (
    <div className="knowledge-logs-page">
      <header className="knowledge-base-header">
        <div>
          <h1>AI 对话日志</h1>
          <p className="knowledge-base-desc">
            {isTeacher
              ? '查看本人带教班级学生与 AI 的对话记录，可筛选班级或删除。'
              : '记录学生页面聊天，可搜索提问与回复内容。'}
          </p>
        </div>
      </header>

      <div className="knowledge-base-toolbar">
        {isTeacher ? (
          <select
            className="kb-input kb-logs-filter"
            value={className}
            onChange={(e) => setClassName(e.target.value)}
          >
            <option value="">全部带教班级</option>
            {classes.map((c) => (
              <option key={c.id || c.name} value={c.name}>
                {c.name}
              </option>
            ))}
          </select>
        ) : null}
        <input
          type="text"
          className="kb-input kb-input-search kb-logs-keyword"
          placeholder="搜索用户提问 / AI 回复内容"
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') fetchLogs(1)
          }}
        />
        <button type="button" className="btn btn-default" onClick={() => fetchLogs(1)}>
          查询
        </button>
        <div className="kb-toolbar-spacer" />
        <span className="kb-logs-total-label">共 {total} 条记录</span>
      </div>

      {loading ? (
        <div className="kb-table-loading">
          <Spin tip="加载中…" size="large" />
        </div>
      ) : logs.length === 0 ? (
        <div className="kb-table-empty">
          <Empty
            description={
              isTeacher
                ? '暂无本班对话记录（学生使用 AI 聊天后会自动记录）'
                : '暂无对话记录（学生使用 AI 聊天后会自动记录）'
            }
          />
        </div>
      ) : (
        <>
          <div className="kb-logs-table-wrap">
            <table className="kb-logs-table">
              <thead>
                <tr>
                  <th style={{ width: 150 }}>时间</th>
                  <th style={{ width: 140 }}>来源</th>
                  {isTeacher ? <th style={{ width: 120 }}>班级</th> : null}
                  <th style={{ width: 110 }}>用户</th>
                  <th>用户提问</th>
                  <th>AI 回复</th>
                  <th style={{ width: 60 }}>操作</th>
                </tr>
              </thead>
              <tbody>
                {logs.map((log) => (
                    <tr key={log.id}>
                      <td className="kb-logs-cell-muted">{formatTime(log.createdAt)}</td>
                      <td>
                        <span className={`kb-logs-app-badge ${log.appId ? '' : 'kb-logs-app-page'}`}>
                          {log.appName || (log.appId ? `应用#${log.appId}` : '学生页面聊天')}
                        </span>
                      </td>
                      {isTeacher ? <td>{log.className || '—'}</td> : null}
                      <td>{log.userName || '—'}</td>
                      <td>
                        <Tooltip title={log.userMessage} placement="topLeft">
                          <span className="kb-logs-cell-text">{snippetOf(log.userMessage, 90)}</span>
                        </Tooltip>
                      </td>
                      <td>
                        <Tooltip title={log.assistantMessage} placement="topLeft">
                          <span className="kb-logs-cell-text">{snippetOf(log.assistantMessage, 140)}</span>
                        </Tooltip>
                      </td>
                      <td>
                        <button
                          type="button"
                          className="btn btn-sm btn-text btn-text-danger"
                          onClick={() => handleDelete(log)}
                        >
                          删除
                        </button>
                      </td>
                    </tr>
                  ))}
              </tbody>
            </table>
          </div>
          <div className="kb-pagination">
            <div className="kb-logs-pager">
              <button
                type="button"
                className="btn btn-sm btn-default"
                disabled={page <= 1}
                onClick={() => fetchLogs(page - 1)}
              >
                上一页
              </button>
              {pages.map((p) => (
                <button
                  key={p}
                  type="button"
                  className={`btn btn-sm ${p === page ? 'btn-primary' : 'btn-default'}`}
                  onClick={() => fetchLogs(p)}
                >
                  {p}
                </button>
              ))}
              <button
                type="button"
                className="btn btn-sm btn-default"
                disabled={page >= pageCount}
                onClick={() => fetchLogs(page + 1)}
              >
                下一页
              </button>
            </div>
          </div>
        </>
      )}
    </div>
  )
}

export default KnowledgeLogs
