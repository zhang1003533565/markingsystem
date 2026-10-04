export const COMPOSITE_CATEGORY_LABELS = {
  MORAL: '德育',
  INTELLECTUAL: '智育',
  PHYSICAL: '体育',
}

export const getCompositeCategoryLabel = (category) =>
  COMPOSITE_CATEGORY_LABELS[category] || '综测'

export const buildCompositeDetailTitle = (type, category) =>
  `${getCompositeCategoryLabel(category)}${type === 'base' ? '基础分明细' : '附加分明细'}`

export const buildCompositeDetailEmptyText = (type, category) =>
  `当前学年没有${getCompositeCategoryLabel(category) === '综测' ? '' : getCompositeCategoryLabel(category)}${type === 'base' ? '基础分' : '附加分'}记录`

export const normalizeCompositeScoreDirection = (value) =>
  String(value || 'add').toLowerCase() === 'deduct' ? 'deduct' : 'add'

export const formatCompositeSignedScore = (value) => {
  const numericValue = Number(value || 0)
  return `${numericValue >= 0 ? '+' : ''}${numericValue.toFixed(2)}`
}

export const buildCompositeDetailParams = (academicYear, category, studentNo) => {
  const params = {
    academicYear: academicYear === '-' ? undefined : academicYear,
    category,
  }
  if (studentNo) {
    params.studentNo = studentNo
  }
  return params
}
