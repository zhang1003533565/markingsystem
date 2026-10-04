const STATUS_SUMMARY_OPTIONS = [
  { key: 'draft', label: '草稿' },
  { key: 'pending', label: '待审核' },
  { key: 'approved', label: '已通过' },
  { key: 'returned', label: '已打回' },
  { key: 'rejected', label: '已拒绝' },
]

const REVIEW_SECTION_OPTIONS = [
  {
    key: 'needs_action',
    title: '需要我处理',
    hint: '草稿、被打回或被拒绝的记录，需要修改后再提交。',
    className: 'needs-action',
  },
  {
    key: 'pending',
    title: '审核中',
    hint: '这些记录已提交审核，暂不能编辑。',
    className: 'pending',
  },
  {
    key: 'approved',
    title: '已计入',
    hint: '这些记录已经计入结果，只能查看详情，不能再编辑。',
    className: 'approved',
    defaultCollapsed: true,
  },
  {
    key: 'other',
    title: '其他记录',
    hint: '这些记录当前不属于常规审核状态。',
    className: 'default',
  },
]

const PERIOD_BUCKET_OPTIONS = [
  {
    key: 'current',
    title: '当前周期申请',
    hint: '本周期内的申请按处理状态归类，优先看需要处理的记录。',
    className: 'current',
  },
  {
    key: 'history',
    title: '历史周期记录',
    hint: '已结束周期只保留结果摘要和明细查看，避免和当前任务混在一起。',
    className: 'history',
  },
  {
    key: 'imported',
    title: '教师导入记录',
    hint: '这些记录不属于学生提交窗口，通常只需要查看结果。',
    className: 'imported',
  },
]

const periodKeyOf = (record = {}) =>
  record.submissionBatchCode ||
  `${record.academicYear || 'unknown'}-${record.submissionStartTime || record.uploadTime || 'unknown'}`

const getPeriodLabel = (record = {}) =>
  record.submissionPeriodLabel || record.periodLabel || '提交周期'

const normalizeTime = (value) => {
  if (!value) {
    return ''
  }
  const timestamp = new Date(value).getTime()
  return Number.isNaN(timestamp) ? String(value) : String(timestamp)
}

const isTeacherImportGroup = (group) =>
  String(group.key || '').startsWith('teacher-import') ||
  String(group.label || '').includes('教师导入')

const isCurrentGroup = (group, currentWindow = {}) => {
  if (!currentWindow?.open) {
    return false
  }
  if (group.batchCode && currentWindow.batchCode && group.batchCode === currentWindow.batchCode) {
    return true
  }
  return (
    Boolean(group.startTime || group.endTime) &&
    normalizeTime(group.startTime) === normalizeTime(currentWindow.startTime) &&
    normalizeTime(group.endTime) === normalizeTime(currentWindow.endTime)
  )
}

const resolveGroupPhase = (group, currentWindow) => {
  if (isCurrentGroup(group, currentWindow)) {
    return { phaseLabel: '当前提交周期', phaseClass: 'current', sortWeight: 0 }
  }
  if (isTeacherImportGroup(group)) {
    return { phaseLabel: '导入记录', phaseClass: 'imported', sortWeight: 2 }
  }
  return { phaseLabel: '历史提交周期', phaseClass: 'history', sortWeight: 1 }
}

const buildStatusSummary = (records) =>
  STATUS_SUMMARY_OPTIONS.map(({ key, label }) => {
    const count = records.filter((record) => record.status === key).length
    return count ? `${label} ${count} 条` : ''
  }).filter(Boolean)

const getSummaryEntry = (summaryMap, key) =>
  summaryMap && typeof summaryMap === 'object' ? summaryMap[key] : null

const getSummaryStatus = (summary, records) =>
  Array.isArray(summary?.statusSummary) && summary.statusSummary.length
    ? summary.statusSummary
    : buildStatusSummary(records)

const buildSummaryTotalLabel = (pageCount, summary, fallbackLabel) => {
  const total = Number(summary?.total)
  if (!Number.isFinite(total) || total < pageCount) {
    return fallbackLabel
  }
  return total === pageCount ? `共 ${total} 条` : `共 ${total} 条，本页 ${pageCount} 条`
}

const resolveReviewSectionKey = (record = {}) => {
  if (record.recordReviewResult === 'approved') {
    return 'approved'
  }
  if (record.status === 'approved') {
    return 'approved'
  }
  if (record.status === 'pending') {
    return 'pending'
  }
  if (['returned', 'rejected'].includes(record.status)) {
    return 'needs_action'
  }
  if (record.status === 'draft') {
    return 'needs_action'
  }
  return 'other'
}

const buildReviewSections = (records) => {
  const recordsBySection = records.reduce((result, record) => {
    const key = resolveReviewSectionKey(record)
    const current = result.get(key) || []
    current.push(record)
    result.set(key, current)
    return result
  }, new Map())

  return REVIEW_SECTION_OPTIONS.map((option) => ({
    ...option,
    records: recordsBySection.get(option.key) || [],
    totalLabel: `${recordsBySection.get(option.key)?.length || 0} 条`,
  })).filter((section) => section.records.length)
}

const buildWindowLabel = (group, formatDateTime) => {
  if (isTeacherImportGroup(group)) {
    return '不属于学生提交窗口'
  }
  if (group.startTime || group.endTime) {
    return `提交窗口：${formatDateTime(group.startTime)} 至 ${formatDateTime(group.endTime)}`
  }
  return '提交窗口：未记录'
}

const resolveBucketKey = (group) => {
  if (group.phaseClass === 'current') {
    return 'current'
  }
  if (group.phaseClass === 'imported') {
    return 'imported'
  }
  return 'history'
}

export const buildExtraScorePeriodGroups = (
  records = [],
  currentWindow = {},
  formatDateTime = (value) => value || '-',
  periodSummary = {},
) =>
  Array.from(
    records
      .reduce((result, record) => {
        const key = periodKeyOf(record)
        const current = result.get(key) || {
          key,
          batchCode: record.submissionBatchCode || '',
          label: getPeriodLabel(record),
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
      const phase = resolveGroupPhase(group, currentWindow)
      const summary = getSummaryEntry(periodSummary?.groups, group.key)
      return {
        ...group,
        ...phase,
        originalIndex: index,
        totalLabel: buildSummaryTotalLabel(
          group.records.length,
          summary,
          `本页 ${group.records.length} 条`,
        ),
        statusSummary: getSummaryStatus(summary, group.records),
        reviewSections: buildReviewSections(group.records),
        windowLabel: buildWindowLabel(group, formatDateTime),
        sortTime: normalizeTime(group.startTime),
      }
    })
    .sort((left, right) => {
      if (left.sortWeight !== right.sortWeight) {
        return left.sortWeight - right.sortWeight
      }
      if (left.sortTime !== right.sortTime) {
        return Number(right.sortTime || 0) - Number(left.sortTime || 0)
      }
      return left.originalIndex - right.originalIndex
    })

export const buildExtraScorePeriodBuckets = (
  records = [],
  currentWindow = {},
  formatDateTime = (value) => value || '-',
  periodSummary = {},
) => {
  const groups = buildExtraScorePeriodGroups(records, currentWindow, formatDateTime, periodSummary)
  const groupsByBucket = groups.reduce((result, group) => {
    const key = resolveBucketKey(group)
    const current = result.get(key) || []
    current.push(group)
    result.set(key, current)
    return result
  }, new Map())

  return PERIOD_BUCKET_OPTIONS.map((option) => {
    const bucketGroups = groupsByBucket.get(option.key) || []
    const bucketRecords = bucketGroups.flatMap((group) => group.records)
    const summary = getSummaryEntry(periodSummary?.buckets, option.key)
    return {
      ...option,
      groups: bucketGroups,
      totalLabel: buildSummaryTotalLabel(
        bucketRecords.length,
        summary,
        `本页 ${bucketRecords.length} 条`,
      ),
      statusSummary: getSummaryStatus(summary, bucketRecords),
    }
  }).filter((bucket) => bucket.groups.length)
}
