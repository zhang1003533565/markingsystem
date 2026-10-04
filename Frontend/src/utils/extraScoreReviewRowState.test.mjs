import assert from 'node:assert/strict'
import {
  canHandleWithdrawalReview,
  getWithdrawalReviewHint,
  isWithdrawalPending,
} from './extraScoreReviewRowState.js'

assert.equal(isWithdrawalPending({ withdrawalStatus: 'pending' }), true)
assert.equal(isWithdrawalPending({ withdrawalStatus: 'approved' }), false)

assert.equal(
  canHandleWithdrawalReview(
    { withdrawalStatus: 'pending', canReviewWithdrawal: true },
    { isCommittee: true },
  ),
  true,
)

assert.equal(
  canHandleWithdrawalReview(
    { withdrawalStatus: 'pending', canReviewWithdrawal: true },
    { isCommittee: false, isTeacher: false },
  ),
  false,
)

assert.equal(
  canHandleWithdrawalReview(
    { withdrawalStatus: 'pending', canReviewWithdrawal: true },
    { isTeacher: true },
  ),
  true,
)

assert.equal(
  getWithdrawalReviewHint(
    { withdrawalStatus: 'pending', canReviewWithdrawal: false },
    { isCommittee: true },
  ),
  '需其他班委处理',
)

assert.equal(
  getWithdrawalReviewHint(
    { withdrawalStatus: 'pending', canReviewWithdrawal: false },
    { isCommittee: false },
  ),
  '等待班委处理撤回',
)

assert.equal(
  getWithdrawalReviewHint(
    { withdrawalStatus: 'pending', canReviewWithdrawal: true },
    { isTeacher: true },
  ),
  '',
)
