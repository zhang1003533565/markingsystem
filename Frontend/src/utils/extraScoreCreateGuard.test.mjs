import assert from 'node:assert/strict'
import { getCreateApplicationIssue } from './extraScoreCreateGuard.js'

assert.equal(
  getCreateApplicationIssue({
    currentTemplate: null,
    hasConfiguredGroups: false,
    canSubmitNow: false,
  }),
  '当前老师尚未为该附加分类配置模板',
)

assert.equal(
  getCreateApplicationIssue({
    currentTemplate: { id: 1, groups: [] },
    hasConfiguredGroups: false,
    canSubmitNow: true,
  }),
  '当前模板还没有配置可申报大类',
)

assert.equal(
  getCreateApplicationIssue({
    currentTemplate: {
      id: 1,
      groups: [
        {
          groupName: '测试大类',
          items: [
            {
              itemName: '测试小类',
              status: 'active',
              levelRules: [{ levelName: '测试级别', score: 1, authorRules: [] }],
            },
          ],
        },
      ],
    },
    hasConfiguredGroups: false,
    canSubmitNow: true,
  }),
  '当前模板已有小类和级别规则，但缺少可用作者规则，请到模板中为小类补充作者计分规则',
)

assert.equal(
  getCreateApplicationIssue({
    currentTemplate: { id: 1 },
    hasConfiguredGroups: true,
    canSubmitNow: false,
  }),
  '当前不在可提交周期内',
)

assert.equal(
  getCreateApplicationIssue({
    currentTemplate: { id: 1 },
    hasConfiguredGroups: true,
    canSubmitNow: true,
  }),
  ''
)
