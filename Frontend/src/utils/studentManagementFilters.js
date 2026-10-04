export const committeeFilterOptions = [
  { value: 'ALL', label: '全部身份' },
  { value: 'CLASS_COMMITTEE', label: '班委' },
  { value: 'NORMAL_STUDENT', label: '普通学生' },
]

export const buildStudentListParams = ({ searchForm = {}, page = 1, pageSize = 10 } = {}) => ({
  page: page - 1,
  size: pageSize,
  keyword: searchForm.keyword || undefined,
  college: searchForm.college || undefined,
  major: searchForm.major || undefined,
  grade: searchForm.grade || undefined,
  className: searchForm.className || undefined,
  committeeStatus:
    searchForm.committeeFilter && searchForm.committeeFilter !== 'ALL'
      ? searchForm.committeeFilter
      : undefined,
})
