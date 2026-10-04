const STATUS_SUMMARY_OPTIONS = [
  { key: 'draft', label: '草稿' },
  { key: 'pending', label: '待审核' },
  { key: 'approved', label: '已通过' },
  { key: 'returned', label: '已打回' },
  { key: 'rejected', label: '已拒绝' },
]

const defaultFormatDateTime = (value) => String(value || '-')

const normalizeTime = (value) => {
  if (!value) {
    return ''
  }
  const timestamp = new Date(value).getTime()
  return Number.isNaN(timestamp) ? String(value) : String(timestamp)
}

const signedScoreOf = (record = {}) => {
  const score = Number(record.expectedScore || 0)
  return String(record.scoreDirection || 'add').toLowerCase() === 'deduct' ? -score : score
}

const buildStatusSummary = (records) =>
  STATUS_SUMMARY_OPTIONS.map(({ key, label }) => {
    const count = records.filter((record) => record.status === key).length
    return count ? `${label} ${count} 条` : ''
  }).filter(Boolean)

const resolvePeriodLabel = (record = {}) =>
  record.submissionPeriodLabel ||
  record.periodLabel ||
  record.submissionTypeLabel ||
  '未归属提交周期'

const buildPeriodKey = (record = {}) =>
  [
    record.academicYear || 'unknown',
    record.submissionBatchCode || '',
    resolvePeriodLabel(record),
    record.submissionStartTime || '',
    record.submissionEndTime || '',
  ].join('|')

const resolveWindowLabel = (group, formatDateTime) => {
  if (group.label.includes('教师导入')) {
    return '不属于学生提交窗口'
  }
  if (group.startTime || group.endTime) {
    return `提交窗口：${formatDateTime(group.startTime)} 至 ${formatDateTime(group.endTime)}`
  }
  return '提交窗口：未记录'
}

export const buildExtraScoreHistoryPeriodGroups = (
  records = [],
  formatDateTime = defaultFormatDateTime,
) =>
  Array.from(
    records
      .reduce((result, record) => {
        const key = buildPeriodKey(record)
        const current = result.get(key) || {
          key,
          academicYear: record.academicYear || '-',
          label: resolvePeriodLabel(record),
          startTime: record.submissionStartTime,
          endTime: record.submissionEndTime,
          records: [],
        }
        current.records.push(record)
        result.set(key, current)
        return result
      }, new Map())
      .values(),
  )
    .map((group, index) => {
      const totalScore = group.records.reduce((sum, record) => sum + signedScoreOf(record), 0)
      const latestTime = group.records
        .map((record) => normalizeTime(record.uploadTime || record.createdAt || record.awardTime))
        .sort((left, right) => Number(right || 0) - Number(left || 0))[0]

      return {
        ...group,
        originalIndex: index,
        totalLabel: `本周期 ${group.records.length} 条`,
        statusSummary: buildStatusSummary(group.records),
        scoreLabel: `${totalScore >= 0 ? '+' : ''}${totalScore.toFixed(1)} 分`,
        windowLabel: resolveWindowLabel(group, formatDateTime),
        sortTime: latestTime || normalizeTime(group.startTime),
      }
    })
    .sort((left, right) => {
      if (left.sortTime !== right.sortTime) {
        return Number(right.sortTime || 0) - Number(left.sortTime || 0)
      }
      return left.originalIndex - right.originalIndex
    })
