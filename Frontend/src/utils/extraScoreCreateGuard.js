const hasText = (value) => String(value || '').trim().length > 0

const getTemplateConfigIssue = (template) => {
  const groups = Array.isArray(template?.groups) ? template.groups : []
  const namedGroups = groups.filter((group) => hasText(group?.groupName))
  if (!namedGroups.length) {
    return '当前模板还没有配置可申报大类'
  }

  const activeItems = namedGroups.flatMap((group) =>
    (group.items || []).filter(
      (item) => item && item.status !== 'inactive' && hasText(item.itemName),
    ),
  )
  if (!activeItems.length) {
    return '当前模板已有大类，但没有启用的小类，请到模板中启用或新增小类'
  }

  const itemsWithLevels = activeItems.filter((item) =>
    (item.levelRules || []).some((rule) => hasText(rule?.levelName)),
  )
  if (!itemsWithLevels.length) {
    return '当前模板已有小类，但缺少级别规则，请到模板中为小类配置级别和基础分'
  }

  const itemsWithAuthors = itemsWithLevels.filter((item) =>
    (item.levelRules || []).some((rule) =>
      hasText(rule?.levelName) &&
      (rule.authorRules || []).some((authorRule) => hasText(authorRule?.authorLabel)),
    ),
  )
  if (!itemsWithAuthors.length) {
    return '当前模板已有小类和级别规则，但缺少可用作者规则，请到模板中为小类补充作者计分规则'
  }

  return '当前模板还没有配置可申报大类'
}

export const getCreateApplicationIssue = ({
  currentTemplate,
  hasConfiguredGroups,
  canSubmitNow,
} = {}) => {
  if (!currentTemplate) {
    return '当前老师尚未为该附加分类配置模板'
  }
  if (!hasConfiguredGroups) {
    return getTemplateConfigIssue(currentTemplate)
  }
  if (!canSubmitNow) {
    return '当前不在可提交周期内'
  }
  return ''
}
