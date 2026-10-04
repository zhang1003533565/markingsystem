export const COMPOSITE_SUBJECT_CONFIG_ISSUE_STORAGE_KEY = 'admin_composite_subject_config_issue'

const ISSUE_MARKERS = ['存在未配置的综测科目', '未配置的综测科目']
const LEADING_MESSAGE_PATTERN = /^.*?后再刷新。\s*/
const MAX_DETAIL_COUNT = 3
const MAX_SUBJECT_PREVIEW_COUNT = 4

const getMessageText = (input) => {
  if (!input) return ''
  if (typeof input === 'string') return input
  return input?.response?.data?.message || input?.message || ''
}

const parseScope = (scope) => {
  const normalized = String(scope || '').trim()
  const match = normalized.match(/^(.+?)\s+(\d{2,4})$/)

  if (!match) {
    return {
      majorName: '',
      grade: '',
    }
  }

  return {
    majorName: match[1].trim(),
    grade: match[2].trim(),
  }
}

const buildDetail = (part) => {
  const [scopeText, subjectText = ''] = String(part || '').split(/\s+缺少配置:\s*/)
  const scope = scopeText.trim()
  const subjects = subjectText
    .split('、')
    .map((item) => item.trim())
    .filter(Boolean)

  if (!scope && subjects.length === 0) return null

  const previewSubjects = subjects.slice(0, MAX_SUBJECT_PREVIEW_COUNT).join('、')
  const hiddenSubjectCount = Math.max(subjects.length - MAX_SUBJECT_PREVIEW_COUNT, 0)
  const previewSuffix = hiddenSubjectCount > 0 ? ` 等 ${subjects.length} 门` : ''
  const parsedScope = parseScope(scope)

  return {
    scope,
    majorName: parsedScope.majorName,
    grade: parsedScope.grade,
    subjects,
    subjectCount: subjects.length,
    preview: `${scope}${previewSubjects ? `：${previewSubjects}${previewSuffix}` : ''}`,
  }
}

export const buildCompositeSubjectConfigIssue = (input) => {
  const rawMessage = String(getMessageText(input) || '').trim()
  if (!rawMessage || !ISSUE_MARKERS.some((marker) => rawMessage.includes(marker))) {
    return null
  }

  const detailText = rawMessage.replace(LEADING_MESSAGE_PATTERN, '').trim()
  const details = detailText.split(';').map(buildDetail).filter(Boolean)
  const visibleDetails = details.slice(0, MAX_DETAIL_COUNT)
  const scopeCount = details.length || 1
  const subjectCount = details.reduce((sum, item) => sum + item.subjectCount, 0)
  const countText =
    subjectCount > 0 ? `${scopeCount} 个范围、${subjectCount} 个课程项` : `${scopeCount} 个范围`

  return {
    title: '综测科目配置未完成',
    description: `检测到 ${countText}还没有综测归类。请到“综测配置”选择对应班级，确认每门课属于智育基础分、体育基础分还是不计入综测。`,
    details: visibleDetails,
    moreScopeCount: Math.max(details.length - visibleDetails.length, 0),
    rawMessage,
  }
}

export const saveCompositeSubjectConfigIssue = (issue) => {
  if (!issue || typeof window === 'undefined') return

  try {
    window.sessionStorage.setItem(COMPOSITE_SUBJECT_CONFIG_ISSUE_STORAGE_KEY, JSON.stringify(issue))
  } catch {
    // 浏览器可能禁用 sessionStorage，提示仍可通过路由 state 传递。
  }
}

export const readCompositeSubjectConfigIssue = () => {
  if (typeof window === 'undefined') return null

  try {
    const raw = window.sessionStorage.getItem(COMPOSITE_SUBJECT_CONFIG_ISSUE_STORAGE_KEY)
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}
