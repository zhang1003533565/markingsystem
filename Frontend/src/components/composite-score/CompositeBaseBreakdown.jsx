const formatScore = (value, digits = 2) => {
  if (value === null || value === undefined || value === '') return '-'
  const numericValue = Number(value)
  return Number.isFinite(numericValue) ? numericValue.toFixed(digits) : '-'
}

const resolveCourseLevel = (value) => {
  const score = Number(value)
  if (score >= 90) return { label: '优秀', className: 'excellent' }
  if (score >= 80) return { label: '良好', className: 'good' }
  if (score >= 60) return { label: '达标', className: 'passed' }
  return { label: '未达标', className: 'failed' }
}

function IntellectualBaseBreakdown({ rows }) {
  const scores = rows.map((item) => Number(item.score)).filter((value) => Number.isFinite(value))
  const average = scores.length
    ? scores.reduce((total, value) => total + value, 0) / scores.length
    : 0
  const highest = scores.length ? Math.max(...scores) : 0

  return (
    <div className="composite-detail-section">
      <div className="composite-detail-summary composite-detail-summary--three">
        <div>
          <span>课程数</span>
          <strong>{rows.length}</strong>
        </div>
        <div>
          <span>平均分</span>
          <strong>{formatScore(average)}</strong>
        </div>
        <div>
          <span>最高分</span>
          <strong>{formatScore(highest)}</strong>
        </div>
      </div>

      <div className="composite-course-list" role="table" aria-label="智育基础分课程明细">
        <div className="composite-course-list__header" role="row">
          <span role="columnheader">课程</span>
          <span role="columnheader">分数</span>
          <span role="columnheader">等级</span>
        </div>
        {rows.map((item, index) => {
          const level = resolveCourseLevel(item.score)
          return (
            <div className="composite-course-list__row" role="row" key={item.id || index}>
              <strong role="cell">{item.subjectName || '未命名课程'}</strong>
              <span className="composite-course-list__score" role="cell">
                {formatScore(item.score)}
              </span>
              <span
                className={`composite-course-level composite-course-level--${level.className}`}
                role="cell"
              >
                {level.label}
              </span>
            </div>
          )
        })}
      </div>
    </div>
  )
}

function MoralBaseBreakdown({ rows }) {
  return (
    <div className="composite-formula-list">
      {rows.map((item, index) => (
        <article className="composite-formula-card" key={item.id || index}>
          <div className="composite-formula-card__source">
            <span>真实二课分</span>
            <strong>{formatScore(item.secondClassScore)}</strong>
            <small>按学校规则换算为德育基础分</small>
          </div>
          <div className="composite-formula-card__equation">
            <div>
              <span>基础分</span>
              <strong>{formatScore(item.activityScore)}</strong>
            </div>
            <b>+</b>
            <div>
              <span>附加分</span>
              <strong>{formatScore(item.bonusScore)}</strong>
            </div>
            <b>=</b>
            <div className="result">
              <span>德育总分</span>
              <strong>{formatScore(item.score)}</strong>
            </div>
          </div>
        </article>
      ))}
    </div>
  )
}

function PhysicalBaseBreakdown({ rows, finalScore, weight }) {
  const sourceRows = rows.filter((item) => !item.ruleOnly)
  const rawScores = sourceRows
    .map((item) => Number(item.rawScore))
    .filter((value) => Number.isFinite(value))
  const storedAverage = rows.find(
    (item) => item.physicalCourseAverage !== null && item.physicalCourseAverage !== undefined,
  )?.physicalCourseAverage
  const rawAverage =
    storedAverage ??
    (rawScores.length
      ? rawScores.reduce((total, value) => total + value, 0) / rawScores.length
      : null)
  const fullScoreEnabled = rows.some((item) => Boolean(item.fullScoreEnabled))
  const storedCountedScore = rows.find(
    (item) => item.score !== null && item.score !== undefined,
  )?.score
  const countedScore = storedCountedScore ?? (fullScoreEnabled ? 100 : rawAverage)
  const hasWeight = weight !== null && weight !== undefined && weight !== ''
  const numericWeight = hasWeight ? Number(weight) : Number.NaN
  const resolvedWeight = Number.isFinite(numericWeight) ? numericWeight : null
  const hasFinalScore = finalScore !== null && finalScore !== undefined && finalScore !== ''
  const numericFinalScore = hasFinalScore ? Number(finalScore) : Number.NaN
  const resolvedFinalScore = Number.isFinite(numericFinalScore)
    ? numericFinalScore
    : resolvedWeight === null
      ? null
      : (countedScore * resolvedWeight) / 100

  return (
    <div className="composite-detail-section composite-physical-base">
      <section className="composite-physical-base__overview" aria-label="体育基础分计算结果">
        <div className="composite-physical-base__headline">
          <div>
            <span>体育基础分</span>
            <small>{fullScoreEnabled ? '当前启用满分计入' : '按原始平均分计入'}</small>
          </div>
          <strong>{formatScore(resolvedFinalScore)}</strong>
        </div>

        <div className="composite-physical-base__calculation">
          <div>
            <span>原始成绩平均分</span>
            <strong>{formatScore(rawAverage)}</strong>
          </div>
          <b aria-hidden="true">→</b>
          <div>
            <span>计入成绩</span>
            <strong>{formatScore(countedScore)}</strong>
          </div>
          <b aria-hidden="true">×</b>
          <div>
            <span>基础分权重</span>
            <strong>{resolvedWeight === null ? '-' : `${formatScore(resolvedWeight)}%`}</strong>
          </div>
          <b aria-hidden="true">=</b>
          <div className="result">
            <span>最终基础分</span>
            <strong>{formatScore(resolvedFinalScore)}</strong>
          </div>
        </div>
      </section>

      <div className={`composite-physical-base__notice${fullScoreEnabled ? ' is-full-score' : ''}`}>
        <strong>{fullScoreEnabled ? '体育满分计入' : '按原始成绩计入'}</strong>
        <span>
          {fullScoreEnabled
            ? sourceRows.length
              ? '体育基础分按 100 分计入，原始成绩保留供查看。'
              : '该学年没有体育原始成绩，体育基础分按年级规则计为 100 分。'
            : '当前体育基础分按各科原始成绩的平均分计入。'}
        </span>
      </div>

      <section className="composite-physical-base__courses">
        <div className="composite-physical-base__section-title">
          <strong>原始成绩</strong>
          <span>共 {sourceRows.length} 门</span>
        </div>
        {sourceRows.length === 0 ? (
          <div className="composite-physical-base__no-source">本学年无体育原始成绩</div>
        ) : (
          <div
            className="composite-physical-course-list"
            role="table"
            aria-label="体育原始成绩明细"
          >
            <div className="composite-physical-course-list__header" role="row">
              <span role="columnheader">体育科目</span>
              <span className="semester" role="columnheader">
                学期
              </span>
              <span role="columnheader">成绩</span>
            </div>
            {sourceRows.map((item, index) => (
              <div
                className="composite-physical-course-list__row"
                role="row"
                key={item.id || index}
              >
                <strong role="cell">
                  {item.subjectName || '未命名课程'}
                  <small>{item.semester || '当前学年'}</small>
                </strong>
                <span className="semester" role="cell">
                  {item.semester || '-'}
                </span>
                <span className="score" role="cell">
                  {formatScore(item.rawScore)}
                </span>
              </div>
            ))}
          </div>
        )}
      </section>
    </div>
  )
}

function CompositeBaseBreakdown({ category, rows, finalScore, weight }) {
  if (category === 'INTELLECTUAL') {
    return <IntellectualBaseBreakdown rows={rows} />
  }
  if (category === 'MORAL') {
    return <MoralBaseBreakdown rows={rows} />
  }
  return <PhysicalBaseBreakdown rows={rows} finalScore={finalScore} weight={weight} />
}

export default CompositeBaseBreakdown
