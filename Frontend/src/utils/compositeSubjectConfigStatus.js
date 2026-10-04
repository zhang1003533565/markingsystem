const STATUS_META = {
  configured: {
    label: '已配置',
    className: 'configured',
  },
  partial: {
    label: '部分配置',
    className: 'partial',
  },
  unconfigured: {
    label: '未配置',
    className: 'unconfigured',
  },
  no_subject: {
    label: '无成绩科目',
    className: 'no-subject',
  },
}

const toCount = (value) => Number(value || 0)

export const getClassConfigStatusMeta = (status) => STATUS_META[status] || STATUS_META.unconfigured

export const mergeClassOptionsWithConfigStatus = (classOptions = [], statusRows = []) => {
  const classMap = new Map(
    classOptions
      .filter((item) => item?.name && item.status !== 'inactive')
      .map((item) => [String(item.name).trim(), item]),
  )

  statusRows.forEach((item) => {
    const className = String(item?.className || '').trim()
    if (!className || classMap.has(className)) return
    classMap.set(className, {
      id: item.classId ?? `config-status:${className}`,
      name: className,
      grade: item.grade || '',
      majorId: item.majorId ?? null,
      status: 'active',
    })
  })

  return Array.from(classMap.values())
}

export const buildClassConfigSummaryText = (summary) => {
  if (!summary) {
    return '请选择学年查看班级配置状态'
  }
  return [
    `共 ${toCount(summary.totalCount)} 个班`,
    `已配置 ${toCount(summary.configuredCount)} 个`,
    `部分配置 ${toCount(summary.partialCount)} 个`,
    `未配置 ${toCount(summary.unconfiguredCount)} 个`,
    `无成绩科目 ${toCount(summary.noSubjectCount)} 个`,
  ].join('，')
}
