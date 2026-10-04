import { useCallback, useEffect, useState } from 'react'
import { Alert, Empty, Spin } from 'antd'
import './ClassAnswerSummary.css'
import './KnowledgeBase.css'
import {
  getTeacherSummaryClasses,
  generateClassAnswerSummary,
  listClassAnswerSummaryHistory,
  getClassAnswerSummaryHistory,
} from '../../api/teacher/classAnswerSummary'
import renderMarkdown from '../../utils/markdown'

const formatTime = (value) => {
  if (!value || value === '不限') return '不限'
  return String(value).replace('T', ' ').slice(0, 16)
}

const ISODate = (date) => {
  if (!date) return ''
  const d = new Date(date)
  if (Number.isNaN(d.getTime())) return ''
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

function ClassAnswerSummary() {
  const [classes, setClasses] = useState([])
  const [loadingClasses, setLoadingClasses] = useState(true)
  const [className, setClassName] = useState('')
  const [startDate, setStartDate] = useState('')
  const [endDate, setEndDate] = useState('')
  const [generating, setGenerating] = useState(false)
  const [result, setResult] = useState(null)
  const [error, setError] = useState('')
  const [history, setHistory] = useState([])
  const [loadingHistory, setLoadingHistory] = useState(false)
  const [activeHistoryId, setActiveHistoryId] = useState(null)
  const [loadingDetail, setLoadingDetail] = useState(false)

  const loadClasses = useCallback(async () => {
    setLoadingClasses(true)
    try {
      const res = await getTeacherSummaryClasses()
      const data = res?.data || []
      setClasses(Array.isArray(data) ? data : [])
      if (Array.isArray(data) && data.length > 0) {
        setClassName(data[0].name)
      }
    } catch (_e) {
      // 拦截器已提示
    } finally {
      setLoadingClasses(false)
    }
  }, [])

  const loadHistory = useCallback(async (selectedClass) => {
    setLoadingHistory(true)
    try {
      const res = await listClassAnswerSummaryHistory(
        selectedClass ? { className: selectedClass } : {}
      )
      const data = res?.data || []
      setHistory(Array.isArray(data) ? data : [])
    } catch (_e) {
      setHistory([])
    } finally {
      setLoadingHistory(false)
    }
  }, [])

  useEffect(() => {
    loadClasses()
  }, [loadClasses])

  useEffect(() => {
    if (!className) {
      setHistory([])
      return
    }
    loadHistory(className)
  }, [className, loadHistory])

  const applyResult = (payload, historyId) => {
    setResult({
      conversationCount: payload.conversationCount ?? 0,
      summary: payload.summary || '',
      meta: {
        className: payload.className || payload.meta?.className,
        startTime: payload.startTime ?? payload.meta?.startTime,
        endTime: payload.endTime ?? payload.meta?.endTime,
        generatedAt: payload.generatedAt ?? payload.meta?.generatedAt,
        id: historyId ?? payload.id ?? payload.meta?.id,
      },
    })
    setActiveHistoryId(historyId ?? payload.id ?? payload.meta?.id ?? null)
  }

  const handleGenerate = async () => {
    if (!className) {
      setError('请先选择班级')
      return
    }
    if (startDate && endDate && startDate > endDate) {
      setError('开始日期不能晚于结束日期')
      return
    }
    setGenerating(true)
    setError('')
    setResult(null)
    setActiveHistoryId(null)
    try {
      let endParam = endDate || ''
      if (endDate) {
        endParam = `${endDate}T23:59:59`
      }
      const res = await generateClassAnswerSummary({
        className,
        startTime: startDate ? `${startDate}T00:00:00` : '',
        endTime: endParam,
      })
      const data = res?.data || {}
      applyResult({
        conversationCount: data.conversationCount ?? 0,
        summary: data.summary || '',
        meta: data.meta || {},
      }, data.meta?.id)
      await loadHistory(className)
    } catch (e) {
      const msg = e?.response?.data?.message || e?.message || '生成失败，请稍后重试'
      setError(msg)
    } finally {
      setGenerating(false)
    }
  }

  const handleOpenHistory = async (item) => {
    if (!item?.id) return
    if (activeHistoryId === item.id && result) return
    setLoadingDetail(true)
    setError('')
    try {
      const res = await getClassAnswerSummaryHistory(item.id)
      const data = res?.data || {}
      applyResult(data, data.id)
    } catch (e) {
      const msg = e?.response?.data?.message || e?.message || '加载历史失败'
      setError(msg)
    } finally {
      setLoadingDetail(false)
    }
  }

  const resultSummary = result?.summary || ''

  return (
    <div className="class-answer-summary-page">
      <header className="knowledge-base-header">
        <div>
          <h1>班级答疑总结</h1>
          <p className="knowledge-base-desc">
            选择你带教的班级和时间段，AI 将根据本班学生与 AI 的对话记录，总结班级疑惑、常见问题、学情，并给出教学建议。生成结果会自动保存，可随时回看。
          </p>
        </div>
      </header>

      <div className="class-summary-form">
        <div className="class-summary-field">
          <label className="class-summary-label">班级</label>
          <select
            className="kb-input class-summary-select"
            value={className}
            onChange={(e) => {
              setClassName(e.target.value)
              setResult(null)
              setActiveHistoryId(null)
            }}
          >
            {classes.length === 0 ? (
              <option value="">暂无班级</option>
            ) : (
              classes.map((c) => (
                <option key={c.id} value={c.name}>
                  {c.name}
                </option>
              ))
            )}
          </select>
        </div>

        <div className="class-summary-field">
          <label className="class-summary-label">开始日期</label>
          <input
            type="date"
            className="kb-input"
            value={startDate}
            max={ISODate(new Date())}
            onChange={(e) => setStartDate(e.target.value)}
          />
        </div>

        <div className="class-summary-field">
          <label className="class-summary-label">结束日期</label>
          <input
            type="date"
            className="kb-input"
            value={endDate}
            max={ISODate(new Date())}
            onChange={(e) => setEndDate(e.target.value)}
          />
        </div>

        <div className="class-summary-actions">
          <button
            type="button"
            className="btn btn-primary"
            disabled={generating || classes.length === 0}
            onClick={handleGenerate}
          >
            {generating ? '正在分析对话…' : '生成班情总结'}
          </button>
        </div>
      </div>

      {error && (
        <Alert
          type="error"
          showIcon
          message="操作失败"
          description={error}
          closable
          onClose={() => setError('')}
          style={{ marginBottom: 16 }}
        />
      )}

      {loadingClasses ? (
        <div className="kb-table-loading">
          <Spin tip="加载班级…" size="large" />
        </div>
      ) : (
        <div className="class-summary-layout">
          <aside className="class-summary-history">
            <div className="class-summary-history-head">
              <h2>历史记录</h2>
              <span>{className || '未选班级'}</span>
            </div>
            {loadingHistory ? (
              <div className="class-summary-history-loading">
                <Spin size="small" />
              </div>
            ) : history.length === 0 ? (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description="暂无历史，生成后会出现在这里"
              />
            ) : (
              <ul className="class-summary-history-list">
                {history.map((item) => (
                  <li key={item.id}>
                    <button
                      type="button"
                      className={`class-summary-history-item${
                        activeHistoryId === item.id ? ' is-active' : ''
                      }`}
                      onClick={() => handleOpenHistory(item)}
                      disabled={loadingDetail}
                    >
                      <div className="class-summary-history-time">
                        {formatTime(item.generatedAt)}
                      </div>
                      <div className="class-summary-history-range">
                        {formatTime(item.startTime)} ~ {formatTime(item.endTime)}
                      </div>
                      <div className="class-summary-history-meta">
                        会话 {item.conversationCount ?? 0} 条
                      </div>
                      {item.summaryPreview ? (
                        <div className="class-summary-history-preview">{item.summaryPreview}</div>
                      ) : null}
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </aside>

          <section className="class-summary-main">
            {loadingDetail ? (
              <div className="class-summary-empty">
                <Spin tip="加载历史…" size="large" />
              </div>
            ) : !result ? (
              <div className="class-summary-empty">
                <Empty description="选择班级生成总结，或从左侧打开历史记录" />
              </div>
            ) : (
              <div className="class-summary-report">
                <div className="class-summary-report-meta">
                  <div className="class-summary-report-meta-item">
                    <span className="class-summary-report-meta-label">班级</span>
                    <span>{result.meta.className}</span>
                  </div>
                  <div className="class-summary-report-meta-item">
                    <span className="class-summary-report-meta-label">时间范围</span>
                    <span>
                      {formatTime(result.meta.startTime)} ~ {formatTime(result.meta.endTime)}
                    </span>
                  </div>
                  <div className="class-summary-report-meta-item">
                    <span className="class-summary-report-meta-label">会话条数</span>
                    <span>{result.conversationCount}</span>
                  </div>
                  <div className="class-summary-report-meta-item">
                    <span className="class-summary-report-meta-label">生成时间</span>
                    <span>{formatTime(result.meta.generatedAt)}</span>
                  </div>
                </div>

                <div className="class-summary-report-body">
                  <h3 className="class-summary-report-title">班级答疑分析</h3>
                  {result.conversationCount > 0 ? (
                    <div className="class-summary-md">{renderMarkdown(resultSummary)}</div>
                  ) : null}
                </div>

                {result.conversationCount === 0 && (
                  <Alert
                    type="warning"
                    showIcon
                    message="无对话记录"
                    description={
                      resultSummary
                      || '该时间段内没有可归集到本班的 AI 对话，请调整时间范围或等学生使用 AI 后再试。'
                    }
                    style={{ marginTop: 16 }}
                  />
                )}
              </div>
            )}
          </section>
        </div>
      )}
    </div>
  )
}

export default ClassAnswerSummary
