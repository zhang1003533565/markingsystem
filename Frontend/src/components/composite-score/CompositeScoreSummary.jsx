import { getCompositeCategoryLabel } from '../../utils/compositeScoreDetails'
import './CompositeScoreDetail.css'

const formatScore = (value) => {
  if (value === null || value === undefined || value === '') return '-'
  const numericValue = Number(value)
  return Number.isFinite(numericValue) ? numericValue.toFixed(2) : '-'
}

const CATEGORIES = [
  {
    key: 'MORAL',
    base: 'moralBaseScore',
    extra: 'moralExtraScore',
    weightedBase: 'weightedMoralBaseScore',
    weightedExtra: 'weightedMoralExtraScore',
  },
  {
    key: 'INTELLECTUAL',
    base: 'intellectualBaseScore',
    extra: 'intellectualExtraScore',
    weightedBase: 'weightedIntellectualBaseScore',
    weightedExtra: 'weightedIntellectualExtraScore',
  },
  {
    key: 'PHYSICAL',
    base: 'physicalBaseScore',
    extra: 'physicalExtraScore',
    weightedBase: 'weightedPhysicalBaseScore',
    weightedExtra: 'weightedPhysicalExtraScore',
  },
]

const WEIGHTS = [
  { key: 'moralBaseWeight', label: '德育基础权重' },
  { key: 'moralExtraWeight', label: '德育附加权重' },
  { key: 'intellectualBaseWeight', label: '智育基础权重' },
  { key: 'intellectualExtraWeight', label: '智育附加权重' },
  { key: 'physicalBaseWeight', label: '体育基础权重' },
  { key: 'physicalExtraWeight', label: '体育附加权重' },
]

const resolveSubjectCategoryLabel = (value) => {
  switch (String(value || '').toUpperCase()) {
    case 'INTELLECTUAL':
      return '智育'
    case 'PHYSICAL':
      return '体育'
    case 'MORAL':
      return '德育'
    case 'EXCLUDED':
    case 'NONE':
      return '不参与综测'
    default:
      return value || '-'
  }
}

function CompositeScoreSummary({ summary }) {
  if (!summary) {
    return null
  }

  const weight = summary.weight || {}
  const subjectCategories = Array.isArray(summary.subjectCategory) ? summary.subjectCategory : []
  const weightEntries = WEIGHTS.filter(
    (item) => weight[item.key] !== null && weight[item.key] !== undefined && weight[item.key] !== '',
  )
  const hasWeight = weightEntries.length > 0
  const weightVersion = weight.weightVersion
  const weightConfigUpdatedAt = weight.weightConfigUpdatedAt
  const ruleVersionLabel =
    weightVersion === null || weightVersion === undefined
      ? null
      : `规则版本 v${weightVersion}${
          weightConfigUpdatedAt ? ` · 更新于 ${String(weightConfigUpdatedAt).replace('T', ' ').slice(0, 19)}` : ''
        }`

  return (
    <section className="composite-score-summary">
      <div className="composite-score-summary__head">
        <div>
          <span>综测总分</span>
          <strong>{formatScore(summary.totalScore)}</strong>
        </div>
        <div className="composite-score-summary__meta">
          <span>{summary.academicYear || '-'}</span>
          {summary.generatedAt ? (
            <span>计算时间：{String(summary.generatedAt).replace('T', ' ').slice(0, 19)}</span>
          ) : null}
          {summary.updatedByName ? <span>更新人：{summary.updatedByName}</span> : null}
        </div>
      </div>

      <div className="composite-score-summary__categories">
        {CATEGORIES.map((item) => (
          <div
            key={item.key}
            className={`composite-score-summary__category composite-score-summary__category--${item.key.toLowerCase()}`}
          >
            <div className="composite-score-summary__category-title">
              {getCompositeCategoryLabel(item.key)}
            </div>
            <dl>
              <div>
                <dt>基础分</dt>
                <dd>{formatScore(summary[item.base])}</dd>
              </div>
              <div>
                <dt>附加分</dt>
                <dd>{formatScore(summary[item.extra])}</dd>
              </div>
              <div>
                <dt>加权基础分</dt>
                <dd>{formatScore(summary[item.weightedBase])}</dd>
              </div>
              <div>
                <dt>加权附加分</dt>
                <dd>{formatScore(summary[item.weightedExtra])}</dd>
              </div>
            </dl>
          </div>
        ))}
      </div>

      <div className="composite-score-summary__block">
        <div className="composite-score-summary__block-title">
          <h4>实际采用权重</h4>
          {ruleVersionLabel ? <span>{ruleVersionLabel}</span> : null}
        </div>
        {hasWeight ? (
          <div className="composite-score-summary__weights">
            {weightEntries.map((item) => (
              <div key={item.key}>
                <span>{item.label}</span>
                <strong>{formatScore(weight[item.key])}%</strong>
              </div>
            ))}
          </div>
        ) : (
          <div className="composite-score-summary__empty">当前快照未冻结权重数据</div>
        )}
      </div>

      <div className="composite-score-summary__block">
        <h4>当时科目分类</h4>
        {subjectCategories.length ? (
          <div className="composite-score-summary__subjects">
            {subjectCategories.map((item, index) => (
              <div key={item.subjectId ?? item.subjectName ?? index} className="composite-score-subject-chip">
                <span>{item.subjectName || '未命名科目'}</span>
                <strong className={`composite-score-subject-chip--${String(item.category || '').toLowerCase()}`}>
                  {resolveSubjectCategoryLabel(item.category)}
                </strong>
              </div>
            ))}
          </div>
        ) : (
          <div className="composite-score-summary__empty">当前快照未冻结科目分类数据</div>
        )}
      </div>
    </section>
  )
}

export default CompositeScoreSummary
