import assert from 'node:assert/strict'

import {
  buildExtraScoreResultSummary,
  resolveExtraScoreReplacement,
  splitExtraScoreResultRows,
} from './extraScoreResultSummary.js'

const rows = [
  {
    id: 1,
    scoreType: 'MORAL',
    counted: true,
    originalSignedScore: 1.5,
    effectiveSignedScore: 1.5,
  },
  {
    id: 2,
    scoreType: 'MORAL',
    counted: false,
    originalSignedScore: 1,
    effectiveSignedScore: 0,
    excludeReason: '同组已有更高分项目计入',
  },
  {
    id: 3,
    scoreType: 'INTELLECTUAL',
    counted: true,
    originalSignedScore: -0.5,
    effectiveSignedScore: -0.5,
  },
]

assert.deepEqual(buildExtraScoreResultSummary(rows), {
  totalCount: 3,
  countedCount: 2,
  excludedCount: 1,
  originalScore: 2,
  countedScore: 1,
  excludedOriginalScore: 1,
})

assert.deepEqual(splitExtraScoreResultRows(rows), {
  countedRows: [rows[0], rows[2]],
  excludedRows: [rows[1]],
})

assert.equal(resolveExtraScoreReplacement(rows, { countedApplicationId: '1' }), rows[0])
assert.equal(resolveExtraScoreReplacement(rows, { countedApplicationId: 99 }), null)
assert.equal(resolveExtraScoreReplacement(null, { countedApplicationId: 1 }), null)
assert.equal(resolveExtraScoreReplacement(rows, null), null)

assert.deepEqual(buildExtraScoreResultSummary(null), {
  totalCount: 0,
  countedCount: 0,
  excludedCount: 0,
  originalScore: 0,
  countedScore: 0,
  excludedOriginalScore: 0,
})
