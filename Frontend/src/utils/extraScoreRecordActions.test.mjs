import assert from 'node:assert/strict'
import {
  canDeleteExtraScoreRecord,
  canEditExtraScoreRecord,
  canSupplementExtraScoreRecord,
  isApprovedReviewRecord,
} from './extraScoreRecordActions.js'

const packageApprovedRecord = {
  status: 'returned',
  packageReturned: true,
  recordReviewResult: 'approved',
}

assert.equal(isApprovedReviewRecord(packageApprovedRecord), true)
assert.equal(canEditExtraScoreRecord(packageApprovedRecord, true), true)
assert.equal(canDeleteExtraScoreRecord(packageApprovedRecord, true), false)
assert.equal(canSupplementExtraScoreRecord(packageApprovedRecord, true), false)

assert.equal(
  canEditExtraScoreRecord({ status: 'returned', recordReviewResult: 'rejected' }, true),
  true,
)
assert.equal(
  canDeleteExtraScoreRecord({ status: 'returned', recordReviewResult: 'rejected' }, true),
  true,
)
assert.equal(
  canSupplementExtraScoreRecord({ status: 'returned', recordReviewResult: 'rejected' }, true),
  true,
)
assert.equal(canEditExtraScoreRecord({ status: 'draft' }, true), true)
assert.equal(canDeleteExtraScoreRecord({ status: 'draft' }, true), true)
assert.equal(canEditExtraScoreRecord({ status: 'draft' }, false), false)
