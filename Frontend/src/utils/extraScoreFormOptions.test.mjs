import assert from 'node:assert/strict'
import { buildTemplateGroupsForApplication } from './extraScoreFormOptions.js'

const groups = buildTemplateGroupsForApplication(
  [
    {
      id: 1,
      groupName: '当前大类',
      items: [
        {
          id: 11,
          itemName: '当前小类',
          status: 'active',
          scoreDirection: 'add',
          levelRules: [
            {
              id: 111,
              levelName: '当前级别',
              score: 1,
              authorRules: [{ id: 1111, authorLabel: '当前作者', mode: 'fixed', fixedScore: 1 }],
            },
          ],
        },
      ],
    },
  ],
  {
    id: 4,
    groupName: '测试',
    itemId: 44,
    itemName: '测试小类',
    levelRuleId: 444,
    levelName: '测试级别',
    authorRuleId: 4444,
    authorLabel: '第一作者',
    expectedScore: 0.5,
    scoreDirection: 'add',
  },
)

assert.equal(groups.length, 2)
assert.equal(groups[1].groupName, '原申请记录')
assert.equal(groups[1].items[0].id, 44)
assert.equal(groups[1].items[0].itemName, '测试 / 测试小类（原记录）')
assert.equal(groups[1].items[0].levelRules[0].id, 444)
assert.equal(groups[1].items[0].levelRules[0].authorRules[0].id, 4444)
assert.equal(groups[1].items[0].levelRules[0].authorRules[0].fixedScore, 0.5)
assert.equal(groups[1].items[0].ruleUnavailable, true)

const inactiveGroups = buildTemplateGroupsForApplication(
  [
    {
      id: 2,
      groupName: '已隐藏大类',
      items: [
        {
          id: 22,
          itemName: '已隐藏小类',
          status: 'inactive',
          scoreDirection: 'add',
          levelRules: [
            {
              id: 222,
              levelName: '隐藏级别',
              score: 2,
              authorRules: [{ id: 2222, authorLabel: '隐藏作者', mode: 'ratio', ratio: 50 }],
            },
          ],
        },
      ],
    },
  ],
  {
    id: 5,
    groupName: '已隐藏大类',
    itemId: 22,
    itemName: '已隐藏小类',
    levelRuleId: 222,
    levelName: '隐藏级别',
    authorRuleId: 2222,
    authorLabel: '隐藏作者',
    expectedScore: 1,
  },
)

assert.equal(inactiveGroups.length, 1)
assert.equal(inactiveGroups[0].groupName, '原申请记录')
assert.equal(inactiveGroups[0].items[0].id, 22)
assert.equal(inactiveGroups[0].items[0].levelRules[0].authorRules[0].ratio, 50)
assert.equal(inactiveGroups[0].items[0].ruleUnavailable, false)
