import assert from 'node:assert/strict'

import { buildExtraScoreHistoryPeriodGroups } from './extraScoreHistoryGroups.js'

const groups = buildExtraScoreHistoryPeriodGroups([
  {
    id: 1,
    academicYear: '2025-2026',
    submissionPeriodLabel: '补充周期 4',
    status: 'approved',
    expectedScore: 0.5,
    uploadTime: '2026-07-08T21:01:00',
  },
  {
    id: 2,
    academicYear: '2025-2026',
    submissionPeriodLabel: '补充周期 4',
    status: 'returned',
    expectedScore: 0.5,
    uploadTime: '2026-07-08T20:01:00',
  },
  {
    id: 3,
    academicYear: '2025-2026',
    submissionPeriodLabel: '年度主周期',
    status: 'approved',
    expectedScore: 2,
    uploadTime: '2026-06-03T16:54:00',
  },
  {
    id: 4,
    academicYear: '2025-2026',
    submissionTypeLabel: '教师导入',
    status: 'approved',
    expectedScore: 2,
    uploadTime: '2026-07-05T23:46:00',
  },
])

assert.deepEqual(
  groups.map((group) => group.label),
  ['补充周期 4', '教师导入', '年度主周期'],
  '历史明细应该按提交周期分组，并按最近提交时间排序',
)

assert.equal(groups[0].totalLabel, '本周期 2 条')
assert.deepEqual(groups[0].statusSummary, ['已通过 1 条', '已打回 1 条'])
assert.equal(groups[0].scoreLabel, '+1.0 分')
assert.deepEqual(
  groups[0].records.map((record) => record.id),
  [1, 2],
  '同一提交周期下的记录应该保留在同一组内',
)

assert.equal(groups[1].windowLabel, '不属于学生提交窗口')
assert.equal(groups[2].academicYear, '2025-2026')
