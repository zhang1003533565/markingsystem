import assert from 'node:assert/strict'
import { buildStudentListParams, committeeFilterOptions } from './studentManagementFilters.js'

assert.deepEqual(
  committeeFilterOptions.map((option) => option.value),
  ['ALL', 'CLASS_COMMITTEE', 'NORMAL_STUDENT'],
)

assert.equal(
  buildStudentListParams({
    searchForm: { committeeFilter: 'CLASS_COMMITTEE' },
    page: 2,
    pageSize: 20,
  }).committeeStatus,
  'CLASS_COMMITTEE',
)

assert.equal(
  buildStudentListParams({
    searchForm: { committeeFilter: 'NORMAL_STUDENT' },
  }).committeeStatus,
  'NORMAL_STUDENT',
)

assert.equal(
  buildStudentListParams({
    searchForm: { committeeFilter: 'ALL' },
  }).committeeStatus,
  undefined,
)
