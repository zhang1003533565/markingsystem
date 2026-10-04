import assert from 'node:assert/strict'

import {
  buildCompositeDetailParams,
  buildCompositeDetailEmptyText,
  buildCompositeDetailTitle,
  formatCompositeSignedScore,
  normalizeCompositeScoreDirection,
} from './compositeScoreDetails.js'

assert.equal(buildCompositeDetailTitle('base', 'MORAL'), '德育基础分明细')
assert.equal(buildCompositeDetailTitle('extra', 'PHYSICAL'), '体育附加分明细')
assert.equal(buildCompositeDetailTitle('extra', 'UNKNOWN'), '综测附加分明细')

assert.equal(buildCompositeDetailEmptyText('base', 'INTELLECTUAL'), '当前学年没有智育基础分记录')
assert.equal(buildCompositeDetailEmptyText('extra', 'MORAL'), '当前学年没有德育附加分记录')

assert.equal(normalizeCompositeScoreDirection('deduct'), 'deduct')
assert.equal(normalizeCompositeScoreDirection('DEDUCT'), 'deduct')
assert.equal(normalizeCompositeScoreDirection('add'), 'add')
assert.equal(normalizeCompositeScoreDirection(undefined), 'add')

assert.equal(formatCompositeSignedScore(1.256), '+1.26')
assert.equal(formatCompositeSignedScore(-0.5), '-0.50')
assert.equal(formatCompositeSignedScore(undefined), '+0.00')

assert.deepEqual(buildCompositeDetailParams('2026-2027', 'MORAL', '20203090128'), {
  academicYear: '2026-2027',
  category: 'MORAL',
  studentNo: '20203090128',
})
assert.deepEqual(buildCompositeDetailParams('-', 'PHYSICAL'), {
  academicYear: undefined,
  category: 'PHYSICAL',
})
