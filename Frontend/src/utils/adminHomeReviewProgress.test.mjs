import assert from 'node:assert/strict'
import { buildCurrentReviewProgressList } from './adminHomeReviewProgress.js'

const rows = [
  {
    className: '未开周期班级',
    studentCount: 41,
    reviewEnabled: false,
  },
  {
    className: '进行中但暂无申请',
    studentCount: 40,
    reviewEnabled: true,
  },
  {
    className: '待班委审核班级',
    studentCount: 42,
    appliedStudentCount: 3,
    committee: 2,
    reviewEnabled: true,
  },
]

const result = buildCurrentReviewProgressList(rows)

assert.deepEqual(
  result.map((item) => item.className),
  ['待班委审核班级', '进行中但暂无申请'],
)
assert.equal(result[0].reviewActionLabel, '进入审核')
assert.equal(result[0].reviewActionPath, '/admin/extra-score-review')
assert.equal(result[1].reviewActionLabel, '查看周期')
assert.equal(result[1].reviewActionPath, '/admin/extra-score-submission-windows')
assert.equal(result.some((item) => item.className === '未开周期班级'), false)
