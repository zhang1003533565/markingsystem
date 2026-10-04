import { Button, Empty } from 'antd'
import { DownloadOutlined, ReloadOutlined } from '../AcademicIcons'

const scoreTypeOptions = [
  { value: 'all', label: '全部类型' },
  { value: 'MORAL', label: '德育附加分' },
  { value: 'INTELLECTUAL', label: '智育附加分' },
  { value: 'PHYSICAL', label: '体育附加分' },
]

const formatTime = (value) => (value ? String(value).replace('T', ' ').slice(0, 16) : '暂无更新')

function ReviewWorkbenchOverview({
  readOnly = false,
  loading = false,
  loaded = false,
  exporting = false,
  context = {},
  filters = {},
  summary = {},
  classes = [],
  scoreType = 'all',
  classFilter = 'all',
  view = 'all',
  academicYear = 'all',
  selectedClass,
  selectedStudentName,
  selectedRowCount = 0,
  onScoreTypeChange,
  onClassFilterChange,
  onViewChange,
  onAcademicYearChange,
  onOpenClass,
  onReturnToClasses,
  onRefresh,
  onReset,
  onExport,
}) {
  const actionableCount = readOnly
    ? Number(summary.pending || 0)
    : Number(summary.actionablePending || 0)
  const showInitialLoading = loading && !loaded
  const hasActiveFilters =
    scoreType !== 'all' || (!selectedClass && classFilter !== 'all') || view !== 'all'
  const tabs = [
    { key: 'all', label: '全部', count: summary.totalRecords || 0 },
    {
      key: readOnly ? 'pending' : 'actionable',
      label: readOnly ? '待审核' : '需要处理',
      count: actionableCount,
    },
    { key: 'waiting', label: '等待班委', count: summary.waitingCommittee || 0 },
    { key: 'approved', label: '已通过', count: summary.approved || 0 },
    { key: 'returned', label: '已打回', count: summary.returned || 0 },
  ]

  return (
    <>
      <section className="review-workbench-head">
        <div className="review-workbench-titlebar">
          <div>
            <h1>附加分审核</h1>
            <p>
              {showInitialLoading
                ? '正在加载当前周期审核数据...'
                : readOnly
                  ? `查看当前周期 ${summary.classCount || 0} 个班级的审核进度`
                  : actionableCount > 0
                    ? `当前有 ${actionableCount} 项审核任务需要处理`
                    : '当前没有需要你处理的审核任务'}
            </p>
          </div>
          <div className="review-workbench-actions">
            <Button icon={<ReloadOutlined />} onClick={onRefresh} loading={loading}>
              刷新
            </Button>
            <details className="review-export-menu">
              <summary>
                <DownloadOutlined />
                导出
              </summary>
              <div className="review-export-popover">
                <label>
                  导出学年
                  <select
                    value={academicYear}
                    onChange={(event) => onAcademicYearChange(event.target.value)}
                  >
                    <option value="all">全部学年</option>
                    {(filters.academicYears || []).map((year) => (
                      <option key={year} value={year}>
                        {year}
                      </option>
                    ))}
                  </select>
                </label>
                {selectedRowCount > 0 ? (
                  <button type="button" disabled={exporting} onClick={() => onExport('selected')}>
                    导出已选记录
                  </button>
                ) : null}
                {selectedClass || classFilter !== 'all' ? (
                  <button type="button" disabled={exporting} onClick={() => onExport('class')}>
                    导出当前班级
                  </button>
                ) : null}
                <button type="button" disabled={exporting} onClick={() => onExport('all')}>
                  导出全部负责班级
                </button>
              </div>
            </details>
          </div>
        </div>

        {selectedClass ? (
          <nav className="review-workbench-path" aria-label="审核层级">
            <button type="button" onClick={onReturnToClasses}>
              班级概览
            </button>
            <span>/</span>
            <strong>{selectedClass}</strong>
            {selectedStudentName ? (
              <>
                <span>/</span>
                <strong>{selectedStudentName}</strong>
              </>
            ) : null}
          </nav>
        ) : null}

        <div className="review-workbench-filters">
          <label>
            附加分类
            <select value={scoreType} onChange={(event) => onScoreTypeChange(event.target.value)}>
              {scoreTypeOptions.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </label>
          {!selectedClass ? (
            <label>
              班级
              <select
                value={classFilter}
                onChange={(event) => onClassFilterChange(event.target.value)}
              >
                <option value="all">全部班级</option>
                {(filters.classes || []).map((className) => (
                  <option key={className} value={className}>
                    {className}
                  </option>
                ))}
              </select>
            </label>
          ) : null}
          <span className="review-workbench-period">
            当前周期：{context.currentAcademicYear || '当前学年'}
          </span>
          {hasActiveFilters ? (
            <button type="button" className="review-filter-reset" onClick={onReset}>
              重置筛选
            </button>
          ) : null}
        </div>

        <div className="review-workbench-tabs" role="tablist" aria-label="审核状态">
          {tabs.map((tab) => (
            <button
              key={tab.key}
              type="button"
              role="tab"
              aria-selected={view === tab.key}
              className={view === tab.key ? 'active' : ''}
              onClick={() => onViewChange(tab.key)}
            >
              {tab.label}
              <strong>{showInitialLoading ? '—' : tab.count}</strong>
            </button>
          ))}
        </div>
      </section>

      {!selectedClass ? (
        <section className="review-workbench-list">
          <header>
            <div>
              <h2>{view === 'actionable' ? '需要你处理' : '班级审核进度'}</h2>
              <p>当前周期班级申请状态与审核完成情况</p>
            </div>
            <span>{showInitialLoading ? '正在加载' : `共 ${classes.length} 个班级`}</span>
          </header>

          {showInitialLoading ? (
            <div
              className="review-workbench-skeleton"
              role="status"
              aria-label="正在加载班级审核进度"
            >
              {[0, 1, 2].map((row) => (
                <div className="review-workbench-skeleton-row" key={row}>
                  <div className="review-skeleton-primary">
                    <span />
                    <span />
                  </div>
                  <span />
                  <span />
                  <div className="review-skeleton-progress">
                    <span />
                    <span />
                  </div>
                  <span />
                  <span />
                </div>
              ))}
            </div>
          ) : classes.length ? (
            <div className="review-workbench-rows">
              {classes.map((row) => {
                const actionable = Number(row.actionablePending || 0)
                const pending = Number(row.pending || 0)
                const waiting = Number(row.waitingCommittee || 0)
                const processed = Number(row.processed || 0)
                const total = Number(row.totalRecords || 0)
                const progress = Number(row.progressPercent || 0)
                const actionLabel = readOnly
                  ? '查看进度'
                  : actionable > 0
                    ? '开始审核'
                    : pending > 0
                      ? '查看进度'
                      : total > 0
                        ? '查看记录'
                        : '查看班级'
                return (
                  <article key={row.className} className={actionable > 0 ? 'needs-action' : ''}>
                    <div className="review-class-primary">
                      <div>
                        <h3>{row.className}</h3>
                        <p>
                          {row.submittedStudentCount || 0} 名学生 · 共 {total} 条申请
                        </p>
                      </div>
                      {actionable > 0 ? <span>需要处理</span> : null}
                    </div>
                    <div className="review-class-metric important">
                      <span>{readOnly ? '待审核' : '需要处理'}</span>
                      <strong>{readOnly ? pending : actionable}</strong>
                    </div>
                    <div className="review-class-metric">
                      <span>等待班委</span>
                      <strong>{waiting}</strong>
                    </div>
                    <div className="review-class-progress">
                      <div>
                        <span>
                          已处理 {processed} / {total}
                        </span>
                        <strong>{progress}%</strong>
                      </div>
                      <div className="review-progress-track">
                        <span style={{ width: `${Math.min(Math.max(progress, 0), 100)}%` }} />
                      </div>
                    </div>
                    <div className="review-class-update">
                      <span>最近更新</span>
                      <strong>{formatTime(row.latestUpdatedAt)}</strong>
                    </div>
                    <Button
                      type={actionable > 0 && !readOnly ? 'primary' : 'default'}
                      onClick={() => onOpenClass(row)}
                    >
                      {actionLabel}
                    </Button>
                  </article>
                )
              })}
            </div>
          ) : (
            <Empty
              className="review-workbench-empty"
              description={
                view === 'actionable'
                  ? '当前没有需要处理的审核任务'
                  : '当前筛选条件下没有班级审核记录'
              }
            />
          )}
        </section>
      ) : null}
    </>
  )
}

export default ReviewWorkbenchOverview
