import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { getClassOptions } from '../../api/admin/class'
import { getExtraScoreReviewClassProgress } from '../../api/extraScoreReview'
import {
  buildCompositeSubjectConfigIssue,
  saveCompositeSubjectConfigIssue,
} from '../../utils/compositeSubjectConfigIssue'
import { buildCurrentReviewProgressList } from '../../utils/adminHomeReviewProgress'
import './Home.css'

const FILTERS = [
  { key: 'all', label: '全部' },
  { key: 'running', label: '进行中' },
  { key: 'attention', label: '需关注' },
  { key: 'completed', label: '已完成' },
]

const formatDateTime = (value) => {
  if (!value) return '暂无更新'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '暂无更新'

  const today = new Date()
  const sameDay = date.toDateString() === today.toDateString()
  const yesterday = new Date(today)
  yesterday.setDate(today.getDate() - 1)
  const isYesterday = date.toDateString() === yesterday.toDateString()
  const time = `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`

  if (sameDay) return `今天 ${time}`
  if (isYesterday) return `昨天 ${time}`
  return `${date.getMonth() + 1}月${date.getDate()}日 ${time}`
}

const resolveAcademicYearLabel = (rows) => {
  const academicYear = rows.find((item) => item.academicYear)?.academicYear
  return academicYear ? `${academicYear} 第一学期` : '当前学期'
}

const formatTermLabel = (academicYear) => (
  academicYear ? `${academicYear} 第一学期` : '当前学期'
)

const extractResponseArray = (response) => {
  if (Array.isArray(response)) return response
  if (Array.isArray(response?.data)) return response.data
  if (Array.isArray(response?.data?.data)) return response.data.data
  return []
}

const resolveActionPath = (record) => {
  const actionPath = record.reviewActionPath || '/admin/extra-score-submission-windows'
  if (!record?.className) return actionPath

  const params = new URLSearchParams()
  if (actionPath === '/admin/extra-score-review') {
    params.set('classId', record.className)
    params.set('status', record.needsTeacherAction ? 'pending' : 'all')
    params.set('openClass', '1')
  }
  if (actionPath === '/admin/extra-score-submission-windows') {
    params.set('presetClassName', record.className)
  }

  const queryString = params.toString()
  return queryString ? `${actionPath}?${queryString}` : actionPath
}

const getProgressPercent = (record) => {
  const total = Number(record.currentProgressTotal || record.studentCount || record.total || 1)
  const current = Number(record.currentProgress || record.appliedStudentCount || record.completed || 0)
  return Math.max(0, Math.min(100, Math.round((current / Math.max(total, 1)) * 100)))
}

const toggleExpandedClass = (expandedClassName, className) => (
  expandedClassName === className ? '' : className
)

const buildStageProgressItems = (record) => {
  const applicationComplete = record.studentCount
    ? record.appliedStudentCount >= record.studentCount
    : record.total > 0

  return [
    {
      key: 'apply',
      label: '学生申请',
      count: `${record.appliedStudentCount}/${record.studentCount || 0}`,
      status: applicationComplete ? 'finish' : 'process',
    },
    {
      key: 'committee',
      label: '班委审核',
      count: `${record.committee} 份`,
      status: !applicationComplete
        ? 'wait'
        : record.committee
          ? 'process'
          : record.teacher || record.completed
            ? 'finish'
            : 'wait',
    },
    {
      key: 'teacher',
      label: '教师审核',
      count: `${record.teacher} 份`,
      status: !applicationComplete
        ? 'wait'
        : record.teacher
          ? 'process'
          : record.completed
            ? 'finish'
            : 'wait',
    },
    {
      key: 'completed',
      label: '完成',
      count: `${record.completed}/${record.total}`,
      status: record.total > 0 && record.completed >= record.total ? 'finish' : 'wait',
    },
  ]
}

function AdminHome() {
  const navigate = useNavigate()
  const [loading, setLoading] = useState(true)
  const [classReviewProgressRows, setClassReviewProgressRows] = useState([])
  const [classOptions, setClassOptions] = useState(null)
  const [compositeConfigIssue, setCompositeConfigIssue] = useState(null)
  const [activeFilter, setActiveFilter] = useState('all')
  const [keyword, setKeyword] = useState('')
  const [expandedClassName, setExpandedClassName] = useState('')
  const [selectedAcademicYear, setSelectedAcademicYear] = useState('')

  useEffect(() => {
    const load = async () => {
      setLoading(true)
      try {
        const [reviewRes, classRes] = await Promise.all([
          getExtraScoreReviewClassProgress()
            .then((response) => ({ response }))
            .catch((error) => ({ error })),
          getClassOptions()
            .then((response) => ({ response }))
            .catch(() => ({ response: { data: [] } })),
        ])
        setClassOptions(extractResponseArray(classRes?.response))

        if (reviewRes?.error) {
          const issue = buildCompositeSubjectConfigIssue(reviewRes.error)
          if (issue) {
            saveCompositeSubjectConfigIssue(issue)
          }
          setCompositeConfigIssue(issue)
          setClassReviewProgressRows([])
          return
        }

        setCompositeConfigIssue(null)
        setClassReviewProgressRows(extractResponseArray(reviewRes?.response))
      } finally {
        setLoading(false)
      }
    }
    load()
  }, [])

  const classReviewProgressList = useMemo(
    () => buildCurrentReviewProgressList(classReviewProgressRows),
    [classReviewProgressRows],
  )

  const activeClassOptions = useMemo(
    () => (classOptions || []).filter((item) => item.status !== 'inactive'),
    [classOptions],
  )

  const inactiveClassCount = useMemo(
    () => (classOptions || []).filter((item) => item.status === 'inactive').length,
    [classOptions],
  )

  const activeClassNameSet = useMemo(
    () => new Set(activeClassOptions.map((item) => item.name).filter(Boolean)),
    [activeClassOptions],
  )

  const shouldFilterByActiveClasses = classOptions !== null && activeClassNameSet.size > 0

  const academicYearOptions = useMemo(() => {
    const sourceRows = shouldFilterByActiveClasses
      ? classReviewProgressList.filter((record) => activeClassNameSet.has(record.className))
      : classReviewProgressList
    const years = sourceRows
      .map((item) => item.academicYear)
      .filter((year, index, list) => year && list.indexOf(year) === index)
    return years.sort((a, b) => String(b).localeCompare(String(a), 'zh-CN'))
  }, [activeClassNameSet, classReviewProgressList, shouldFilterByActiveClasses])

  useEffect(() => {
    if (academicYearOptions.length === 0) {
      if (selectedAcademicYear) {
        setSelectedAcademicYear('')
      }
      return
    }
    if (!selectedAcademicYear || !academicYearOptions.includes(selectedAcademicYear)) {
      setSelectedAcademicYear(academicYearOptions[0])
    }
  }, [academicYearOptions, selectedAcademicYear])

  const visibleClassReviewProgressList = useMemo(() => {
    const rows = selectedAcademicYear
      ? classReviewProgressList.filter((record) => record.academicYear === selectedAcademicYear)
      : classReviewProgressList
    if (!shouldFilterByActiveClasses) {
      return rows
    }
    return rows.filter((record) => activeClassNameSet.has(record.className))
  }, [activeClassNameSet, classReviewProgressList, selectedAcademicYear, shouldFilterByActiveClasses])

  const academicYearLabel = useMemo(
    () => selectedAcademicYear ? formatTermLabel(selectedAcademicYear) : resolveAcademicYearLabel(classReviewProgressList),
    [classReviewProgressList, selectedAcademicYear],
  )

  const workItems = useMemo(() => {
    const items = []
    visibleClassReviewProgressList.forEach((record) => {
      if (record.needsTeacherAction) {
        items.push({
          key: `${record.className}-teacher`,
          className: record.className,
          title: `${record.teacher} 份材料等待教师审核`,
          meta: record.recentUpdatedAt ? `最近更新：${formatDateTime(record.recentUpdatedAt)}` : '等待处理',
          actionLabel: '开始审核',
          action: () => navigate(resolveActionPath(record)),
          tone: 'urgent',
        })
      }

      if (!record.needsTeacherAction && Number(record.unsubmittedCount || 0) > 0 && record.needsAttention) {
        items.push({
          key: `${record.className}-unsubmitted`,
          className: record.className,
          title: `${record.unsubmittedCount} 名学生尚未提交`,
          meta: record.daysUntilDeadline >= 0 ? `距离截止还有 ${record.daysUntilDeadline} 天` : '提交进度偏慢',
          actionLabel: '发送提醒',
          action: () => navigate('/admin/announcements', {
            state: {
              presetTitle: `请尽快完成 ${record.className} 附加分申报`,
              presetContent: `<p>${record.className} 当前仍有 <strong>${record.unsubmittedCount}</strong> 名学生尚未提交附加分申请，请尽快提醒。</p>`,
              focusClassName: record.className,
            },
          }),
          tone: 'attention',
        })
      }

      if (!record.needsTeacherAction && Number(record.committee || 0) > 0) {
        items.push({
          key: `${record.className}-committee`,
          className: record.className,
          title: `${record.committee} 份材料等待班委初审`,
          meta: record.recentUpdatedAt ? `最近更新：${formatDateTime(record.recentUpdatedAt)}` : '等待班委处理',
          actionLabel: '催办',
          action: () => navigate('/admin/announcements', {
            state: {
              presetTitle: `请尽快完成 ${record.className} 附加分初审`,
              presetContent: `<p>${record.className} 当前仍有 <strong>${record.committee}</strong> 份材料等待班委初审，请尽快处理。</p>`,
              focusClassName: record.className,
            },
          }),
          tone: 'attention',
        })
      }
    })
    return items
  }, [navigate, visibleClassReviewProgressList])

  const filteredClassList = useMemo(() => {
    const normalizedKeyword = keyword.trim().toLowerCase()
    return visibleClassReviewProgressList.filter((record) => {
      const matchesKeyword = !normalizedKeyword || String(record.className).toLowerCase().includes(normalizedKeyword)
      if (!matchesKeyword) return false
      if (activeFilter === 'attention') return record.needsAttention
      if (activeFilter === 'completed') return record.currentStageKey === 'completed'
      if (activeFilter === 'running') return record.currentStageKey !== 'completed'
      return true
    })
  }, [activeFilter, keyword, visibleClassReviewProgressList])

  const hasProgressRows = visibleClassReviewProgressList.length > 0
  const hasSearchOrFilter = keyword.trim() || activeFilter !== 'all'

  const managedClassOptions = useMemo(() => {
    if (classOptions !== null && classOptions.length > 0) {
      return activeClassOptions
    }
    const seen = new Set()
    return classReviewProgressList
      .filter((record) => {
        if (!record.className || seen.has(record.className)) {
          return false
        }
        seen.add(record.className)
        return true
      })
      .map((record) => ({
        name: record.className,
        grade: record.academicYear || '当前班级',
      }))
  }, [activeClassOptions, classOptions, classReviewProgressList])

  const activeClassGroups = useMemo(() => {
    return managedClassOptions.reduce((groups, item) => {
      const grade = item.grade || '未分年级'
      if (!groups[grade]) {
        groups[grade] = []
      }
      groups[grade].push(item)
      return groups
    }, {})
  }, [managedClassOptions])

  const openCompositeConfig = () => {
    if (compositeConfigIssue) {
      saveCompositeSubjectConfigIssue(compositeConfigIssue)
    }
    navigate('/admin/composite-score-config', {
      state: {
        compositeSubjectConfigIssue: compositeConfigIssue,
      },
    })
  }

  const renderClassActions = (record) => {
    if (record.needsTeacherAction) {
      return (
        <button type="button" className="workspace-primary-btn" onClick={() => navigate(resolveActionPath(record))}>
          开始审核
        </button>
      )
    }

    if (Number(record.unsubmittedCount || 0) > 0 && record.needsAttention) {
      return (
        <>
          <button type="button" className="workspace-secondary-btn" onClick={() => navigate(resolveActionPath(record))}>
            查看名单
          </button>
          <button
            type="button"
            className="workspace-primary-btn"
            onClick={() => navigate('/admin/announcements', {
              state: {
                presetTitle: `请尽快完成 ${record.className} 附加分申报`,
                presetContent: `<p>${record.className} 当前仍有 <strong>${record.unsubmittedCount}</strong> 名学生尚未提交附加分申请，请尽快提醒。</p>`,
                focusClassName: record.className,
              },
            })}
          >
            发送提醒
          </button>
        </>
      )
    }

    if (Number(record.committee || 0) > 0) {
      return (
        <button
          type="button"
          className="workspace-primary-btn"
          onClick={() => navigate('/admin/announcements', {
            state: {
              presetTitle: `请尽快完成 ${record.className} 附加分初审`,
              presetContent: `<p>${record.className} 当前仍有 <strong>${record.committee}</strong> 份材料等待班委初审，请尽快处理。</p>`,
              focusClassName: record.className,
            },
          })}
        >
          催办
        </button>
      )
    }

    return (
      <button type="button" className="workspace-secondary-btn" onClick={() => navigate(resolveActionPath(record))}>
        查看详情
      </button>
    )
  }

  return (
    <div className="admin-home-page">
      {loading ? (
        <div className="admin-home-loading">
          <div className="admin-home-spinner" />
          <p>正在加载审核工作台...</p>
        </div>
      ) : (
        <>
          <section className="workspace-header">
            <div>
              <div className="workspace-title-row">
                <h1>班级审核工作台</h1>
                <label className="workspace-term-select" aria-label="选择学期">
                  <select
                    value={selectedAcademicYear}
                    onChange={(event) => setSelectedAcademicYear(event.target.value)}
                    disabled={academicYearOptions.length === 0}
                  >
                    {academicYearOptions.length === 0 ? (
                      <option value="">{academicYearLabel}</option>
                    ) : academicYearOptions.map((year) => (
                      <option key={year} value={year}>{formatTermLabel(year)}</option>
                    ))}
                  </select>
                </label>
              </div>
            </div>
            <div className="workspace-header-actions">
              <input
                type="search"
                value={keyword}
                onChange={(event) => setKeyword(event.target.value)}
                placeholder="搜索班级"
                aria-label="搜索班级"
              />
              <button type="button" className="workspace-secondary-btn" onClick={() => navigate('/admin/announcements')}>
                发布通知
              </button>
            </div>
          </section>

          {compositeConfigIssue ? (
            <section className="admin-home-issue-alert" role="status">
              <div className="admin-home-issue-main">
                <span className="admin-home-issue-mark">!</span>
                <div className="admin-home-issue-copy">
                  <strong>{compositeConfigIssue.title}</strong>
                  <p>{compositeConfigIssue.description}</p>
                </div>
              </div>
              <button type="button" className="admin-home-issue-action" onClick={openCompositeConfig}>
                去综测配置
              </button>
            </section>
          ) : null}

          <section className="workspace-todo-section">
            <div className="workspace-section-heading">
              <h2>需要你处理</h2>
              <span>共 {workItems.length} 项</span>
            </div>
            {workItems.length ? (
              <div className="workspace-todo-list">
                {workItems.map((item) => (
                  <article key={item.key} className={`workspace-todo-item ${item.tone}`}>
                    <div>
                      <strong>{item.className}</strong>
                      <span>{item.title}</span>
                      <em>{item.meta}</em>
                    </div>
                    <button type="button" className="workspace-primary-btn" onClick={item.action}>
                      {item.actionLabel}
                    </button>
                  </article>
                ))}
              </div>
            ) : (
              <div className="workspace-calm-state">✓ 当前没有需要你处理的审核任务</div>
            )}
          </section>

          <section className="workspace-managed-section">
            <div className="workspace-section-heading">
              <h2>管理中的班级</h2>
              <span>
                启用 {managedClassOptions.length} 个
                {classOptions !== null && inactiveClassCount > 0 ? `，已隐藏停用 ${inactiveClassCount} 个` : ''}
              </span>
            </div>
            {managedClassOptions.length ? (
              <div className="workspace-managed-groups">
                {Object.entries(activeClassGroups).map(([grade, classes]) => (
                  <div key={grade} className="workspace-managed-group">
                    <span>{grade}</span>
                    <div>
                      {classes.map((item) => (
                        <button
                          key={item.id || item.name}
                          type="button"
                          className="workspace-managed-chip"
                          onClick={() => {
                            setKeyword(item.name)
                            setActiveFilter('all')
                          }}
                        >
                          {item.name}
                        </button>
                      ))}
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <div className="workspace-start-state compact">
                <div>
                  <strong>还没有启用的班级</strong>
                  <p>毕业班级可以停用，新学期只把当前需要管理的班级启用到工作台。</p>
                </div>
                <button type="button" className="workspace-primary-btn" onClick={() => navigate('/admin/classes')}>
                  去班级管理
                </button>
              </div>
            )}
          </section>

          <section className="workspace-progress-section">
            <div className="workspace-section-heading">
              <h2>班级进度</h2>
              <div className="workspace-filter-tabs">
                {FILTERS.map((filter) => (
                  <button
                    type="button"
                    key={filter.key}
                    className={activeFilter === filter.key ? 'active' : ''}
                    onClick={() => setActiveFilter(filter.key)}
                  >
                    {filter.label}
                  </button>
                ))}
              </div>
            </div>

            <div className="workspace-class-list">
              {filteredClassList.length ? (
                filteredClassList.map((record) => {
                  const progressPercent = getProgressPercent(record)
                  const expanded = expandedClassName === record.className
                  const stageProgressItems = buildStageProgressItems(record)
                  return (
                    <article
                      key={record.className}
                      className={`workspace-class-card ${record.attentionLevel} ${expanded ? 'expanded' : ''}`}
                    >
                      <div className="workspace-class-main">
                        <div className="workspace-class-title">
                          <strong>{record.className}</strong>
                          <span>{record.currentStage} · {record.currentProgress}/{record.currentProgressTotal}</span>
                        </div>
                        <div className="workspace-class-meta">
                          <span>最近更新：{formatDateTime(record.recentUpdatedAt)}</span>
                          <em>{record.attentionReason || record.statusLabel}</em>
                        </div>
                        <div className="workspace-progress-bar" aria-hidden="true">
                          <span style={{ width: `${progressPercent}%` }} />
                        </div>
                      </div>
                      <div className="workspace-class-side">
                        <span className={`workspace-status ${record.attentionLevel}`}>{record.statusLabel}</span>
                        <div className="workspace-class-actions">
                          {renderClassActions(record)}
                          <button
                            type="button"
                            className="workspace-expand-btn"
                            onClick={() => setExpandedClassName(toggleExpandedClass(expandedClassName, record.className))}
                            aria-expanded={expanded}
                            aria-label={`${expanded ? '收起' : '展开'} ${record.className} 审核流程`}
                          >
                            <span>{expanded ? '收起流程' : '展开流程'}</span>
                            <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">
                              <path d="M6 9l6 6 6-6" />
                            </svg>
                          </button>
                        </div>
                      </div>
                      {expanded ? (
                        <div className="workspace-flow-detail">
                          {stageProgressItems.map((stage) => (
                            <div key={stage.key} className={`workspace-flow-step ${stage.status}`}>
                              <span className="workspace-flow-dot" />
                              <div>
                                <strong>{stage.label}</strong>
                                <small>{stage.count}</small>
                              </div>
                            </div>
                          ))}
                        </div>
                      ) : null}
                    </article>
                  )
                })
              ) : (
                hasProgressRows || hasSearchOrFilter ? (
                  <div className="workspace-calm-state muted">没有匹配的班级进度，换个筛选条件试试</div>
                ) : (
                  <div className="workspace-start-state">
                    <div>
                      <strong>还没有班级审核进度</strong>
                      <p>新学期开始时，可以先建立申报窗口、确认学生名单，再发布一条启动通知。</p>
                    </div>
                    <div className="workspace-start-actions">
                      <button
                        type="button"
                        className="workspace-primary-btn"
                        onClick={() => navigate('/admin/extra-score-submission-windows')}
                      >
                        配置申报窗口
                      </button>
                      <button
                        type="button"
                        className="workspace-secondary-btn"
                        onClick={() => navigate('/admin/students')}
                      >
                        维护学生名单
                      </button>
                      <button
                        type="button"
                        className="workspace-secondary-btn"
                        onClick={() => navigate('/admin/announcements')}
                      >
                        发布启动通知
                      </button>
                    </div>
                  </div>
                )
              )}
            </div>
          </section>
        </>
      )}
    </div>
  )
}

export default AdminHome
