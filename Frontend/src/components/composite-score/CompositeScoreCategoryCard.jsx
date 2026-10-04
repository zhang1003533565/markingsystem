import { getCompositeCategoryLabel } from '../../utils/compositeScoreDetails'
import './CompositeScoreDetail.css'

const formatScore = (value, digits) => {
  const numericValue = Number(value)
  return Number.isFinite(numericValue) ? numericValue.toFixed(digits) : '-'
}

function CompositeScoreCategoryCard({
  category,
  label,
  baseScore,
  extraScore,
  totalScore,
  onOpenBase,
  onOpenExtra,
}) {
  const categoryLabel = label || getCompositeCategoryLabel(category)
  const normalizedCategory = String(category || 'unknown').toLowerCase()
  const resolvedTotal = totalScore ?? Number(baseScore || 0) + Number(extraScore || 0)

  return (
    <article className={`composite-category-card composite-category-card--${normalizedCategory}`}>
      <div className="composite-category-card__header">
        <span>{categoryLabel}</span>
        <div>
          <small>小计</small>
          <strong>{formatScore(resolvedTotal, 1)}</strong>
        </div>
      </div>

      <div className="composite-category-card__actions">
        <button type="button" onClick={onOpenBase} aria-label={`查看${categoryLabel}基础分明细`}>
          <span>基础分</span>
          <strong>{formatScore(baseScore, 2)}</strong>
        </button>
        <button type="button" onClick={onOpenExtra} aria-label={`查看${categoryLabel}附加分明细`}>
          <span>附加分</span>
          <strong>{formatScore(extraScore, 2)}</strong>
        </button>
      </div>
    </article>
  )
}

export default CompositeScoreCategoryCard
