import assert from 'node:assert/strict'
import { buildCompositeSubjectConfigIssue } from './compositeSubjectConfigIssue.js'

const rawMessage =
  '存在未配置的综测科目，请先在“综测配置”页面完成配置后再刷新。 网络工程 2024 缺少配置: 面向对象程序设计、电子实习; 软件工程 2024 缺少配置: 大学英语'

const issue = buildCompositeSubjectConfigIssue({
  response: {
    data: {
      message: rawMessage,
    },
  },
})

assert.equal(issue.title, '综测科目配置未完成')
assert.equal(
  issue.description,
  '检测到 2 个范围、3 个课程项还没有综测归类。请到“综测配置”选择对应班级，确认每门课属于智育基础分、体育基础分还是不计入综测。',
)
assert.deepEqual(issue.details, [
  {
    scope: '网络工程 2024',
    majorName: '网络工程',
    grade: '2024',
    subjects: ['面向对象程序设计', '电子实习'],
    subjectCount: 2,
    preview: '网络工程 2024：面向对象程序设计、电子实习',
  },
  {
    scope: '软件工程 2024',
    majorName: '软件工程',
    grade: '2024',
    subjects: ['大学英语'],
    subjectCount: 1,
    preview: '软件工程 2024：大学英语',
  },
])
assert.equal(issue.moreScopeCount, 0)
assert.equal(issue.rawMessage, rawMessage)
assert.equal(
  buildCompositeSubjectConfigIssue({ response: { data: { message: '服务器错误' } } }),
  null,
)
