import assert from 'node:assert/strict'

import {
  buildExtraScorePeriodBuckets,
  buildExtraScorePeriodGroups,
} from './extraScorePeriodGroups.js'

const formatDateTime = (value) => String(value || '-')

const currentWindow = {
  open: true,
  batchCode: 'current-batch',
  startTime: '2026-07-07T00:00:00',
  endTime: '2026-07-23T00:00:00',
}

const records = [
  {
    id: 1,
    submissionBatchCode: 'current-batch',
    submissionPeriodLabel: '补充周期 4',
    submissionStartTime: currentWindow.startTime,
    submissionEndTime: currentWindow.endTime,
    status: 'draft',
  },
  {
    id: 2,
    submissionBatchCode: 'current-batch',
    submissionPeriodLabel: '补充周期 4',
    submissionStartTime: currentWindow.startTime,
    submissionEndTime: currentWindow.endTime,
    status: 'returned',
  },
  {
    id: 3,
    submissionBatchCode: 'current-batch',
    submissionPeriodLabel: '补充周期 4',
    submissionStartTime: currentWindow.startTime,
    submissionEndTime: currentWindow.endTime,
    status: 'pending',
  },
  {
    id: 4,
    submissionBatchCode: 'current-batch',
    submissionPeriodLabel: '补充周期 4',
    submissionStartTime: currentWindow.startTime,
    submissionEndTime: currentWindow.endTime,
    status: 'approved',
  },
  {
    id: 5,
    submissionBatchCode: 'history-batch',
    submissionPeriodLabel: '年度主周期',
    submissionStartTime: '2026-06-02T16:53:00',
    submissionEndTime: '2026-06-04T16:53:00',
    status: 'approved',
  },
  {
    id: 6,
    submissionBatchCode: 'teacher-import-1',
    submissionPeriodLabel: '教师导入',
    status: 'approved',
    submissionType: 'teacher_import',
  },
]

const buckets = buildExtraScorePeriodBuckets(records, currentWindow, formatDateTime)
const groups = buildExtraScorePeriodGroups(records, currentWindow, formatDateTime)

assert.equal(groups.length, 3)
assert.equal(groups[0].label, '补充周期 4')
assert.equal(groups[0].phaseLabel, '当前提交周期')
assert.equal(groups[0].phaseClass, 'current')
assert.equal(groups[0].totalLabel, '本页 4 条')
assert.deepEqual(groups[0].statusSummary, [
  '草稿 1 条',
  '待审核 1 条',
  '已通过 1 条',
  '已打回 1 条',
])
assert.equal(groups[0].windowLabel, '提交窗口：2026-07-07T00:00:00 至 2026-07-23T00:00:00')
assert.deepEqual(
  groups[0].reviewSections.map((section) => ({
    key: section.key,
    title: section.title,
    totalLabel: section.totalLabel,
    ids: section.records.map((record) => record.id),
  })),
  [
    { key: 'needs_action', title: '需要我处理', totalLabel: '2 条', ids: [1, 2] },
    { key: 'pending', title: '审核中', totalLabel: '1 条', ids: [3] },
    { key: 'approved', title: '已计入', totalLabel: '1 条', ids: [4] },
  ],
)

assert.equal(groups[1].label, '年度主周期')
assert.equal(groups[1].phaseLabel, '历史提交周期')
assert.equal(groups[1].reviewSections[0].key, 'approved')

assert.equal(groups[2].label, '教师导入')
assert.equal(groups[2].phaseLabel, '导入记录')
assert.equal(groups[2].windowLabel, '不属于学生提交窗口')

assert.deepEqual(
  buckets.map((bucket) => bucket.key),
  ['current', 'history', 'imported'],
  '申请记录应该先分当前周期、再分历史周期、最后分教师导入',
)
assert.equal(buckets[0].totalLabel, '本页 4 条')

const currentGroup = buckets[0].groups[0]

assert.deepEqual(
  currentGroup.reviewSections.map((section) => section.key),
  ['needs_action', 'pending', 'approved'],
  '当前周期内部应该优先按处理意义分组',
)

assert.deepEqual(
  currentGroup.reviewSections[0].records.map((record) => record.id),
  [1, 2],
  '草稿和打回记录应该合并到“需要我处理”',
)

assert.equal(currentGroup.reviewSections[2].defaultCollapsed, true, '已通过记录默认应该弱化折叠')

assert.equal(buckets[2].title, '教师导入记录', '教师导入记录应该从学生提交周期里单独拿出来')

const summarizedBuckets = buildExtraScorePeriodBuckets(
  records.slice(0, 2),
  currentWindow,
  formatDateTime,
  {
    buckets: {
      current: {
        total: 22,
        statusSummary: ['已通过 20 条', '已打回 2 条'],
      },
    },
    groups: {
      'current-batch': {
        total: 22,
        statusSummary: ['已通过 20 条', '已打回 2 条'],
      },
    },
  },
)

assert.equal(
  summarizedBuckets[0].totalLabel,
  '共 22 条，本页 2 条',
  '周期分区总数应该使用后端全量汇总，不能把当前页数量当总数',
)
assert.deepEqual(summarizedBuckets[0].statusSummary, ['已通过 20 条', '已打回 2 条'])
assert.equal(summarizedBuckets[0].groups[0].totalLabel, '共 22 条，本页 2 条')
