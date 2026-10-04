import assert from 'node:assert/strict'
import {
  filterReviewWorkbenchClasses,
  getReviewStatusForWorkbenchView,
  summarizeReviewWorkbenchClasses,
} from './extraScoreReviewWorkbench.js'

const classes = [
  {
    className: '数据241',
    submittedStudentCount: 2,
    totalRecords: 4,
    actionablePending: 1,
    pending: 2,
    waitingCommittee: 1,
    approved: 1,
    returned: 1,
  },
  {
    className: '数据242',
    submittedStudentCount: 1,
    totalRecords: 2,
    actionablePending: 0,
    pending: 0,
    waitingCommittee: 0,
    approved: 2,
    returned: 0,
  },
]

assert.deepEqual(summarizeReviewWorkbenchClasses(classes), {
  classCount: 2,
  studentCount: 3,
  totalRecords: 6,
  actionablePending: 1,
  pending: 2,
  waitingCommittee: 1,
  approved: 3,
  returned: 1,
})
assert.deepEqual(
  filterReviewWorkbenchClasses(classes, 'actionable').map((row) => row.className),
  ['数据241'],
)
assert.deepEqual(
  filterReviewWorkbenchClasses(classes, 'approved').map((row) => row.className),
  ['数据241', '数据242'],
)
assert.equal(getReviewStatusForWorkbenchView('waiting'), 'pending')
assert.equal(getReviewStatusForWorkbenchView('approved'), 'approved')
assert.equal(getReviewStatusForWorkbenchView('all'), 'all')

console.log('extraScoreReviewWorkbench tests passed')
