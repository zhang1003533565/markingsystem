import assert from 'node:assert/strict'

import {
  buildClassConfigSummaryText,
  getClassConfigStatusMeta,
  mergeClassOptionsWithConfigStatus,
} from './compositeSubjectConfigStatus.js'

assert.deepEqual(getClassConfigStatusMeta('configured'), {
  label: '已配置',
  className: 'configured',
})
assert.deepEqual(getClassConfigStatusMeta('partial'), {
  label: '部分配置',
  className: 'partial',
})
assert.deepEqual(getClassConfigStatusMeta('unconfigured'), {
  label: '未配置',
  className: 'unconfigured',
})
assert.deepEqual(getClassConfigStatusMeta('no_subject'), {
  label: '无成绩科目',
  className: 'no-subject',
})

assert.equal(
  buildClassConfigSummaryText({
    totalCount: 4,
    configuredCount: 1,
    partialCount: 1,
    unconfiguredCount: 1,
    noSubjectCount: 1,
  }),
  '共 4 个班，已配置 1 个，部分配置 1 个，未配置 1 个，无成绩科目 1 个',
)

assert.equal(buildClassConfigSummaryText(null), '请选择学年查看班级配置状态')

assert.deepEqual(
  mergeClassOptionsWithConfigStatus(
    [
      { id: 1, name: '数据241', grade: '2024', majorId: 2, status: 'active' },
      { id: 2, name: '数据242', grade: '2024', majorId: 2, status: 'inactive' },
    ],
    [
      { classId: 1, className: '数据241', grade: '2024', majorId: 2, status: 'configured' },
      { classId: 2, className: '数据242', grade: '2024', majorId: 2, status: 'unconfigured' },
    ],
  ),
  [
    { id: 1, name: '数据241', grade: '2024', majorId: 2, status: 'active' },
    { id: 2, name: '数据242', grade: '2024', majorId: 2, status: 'active' },
  ],
)
