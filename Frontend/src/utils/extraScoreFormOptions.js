const hasText = (value) => String(value || '').trim().length > 0

const isVisibleTemplateItem = (item) =>
  item &&
  item.status !== 'inactive' &&
  hasText(item.itemName) &&
  (item.levelRules || []).some(
    (rule) =>
      hasText(rule?.levelName) &&
      (rule.authorRules || []).some((authorRule) => hasText(authorRule?.authorLabel)),
  )

const cloneGroupWithItems = (group, items) => ({
  ...group,
  items: items.map((item) => ({
    ...item,
    levelRules: (item.levelRules || []).map((rule) => ({
      ...rule,
      authorRules: [...(rule.authorRules || [])],
    })),
  })),
})

const findRecordItem = (groups, record) => {
  if (!record?.itemId) {
    return null
  }
  for (const group of groups || []) {
    const item = (group.items || []).find((candidate) => Number(candidate.id) === Number(record.itemId))
    if (item) {
      return { group, item }
    }
  }
  return null
}

const buildLegacyAuthorRule = (record) => ({
  id: record.authorRuleId,
  authorLabel: `${record.authorLabel || '原作者规则'}（原记录）`,
  mode: 'fixed',
  fixedScore: Number(record.expectedScore || 0),
  ratio: 100,
})

const buildLegacyLevelRule = (record) => ({
  id: record.levelRuleId,
  levelName: `${record.levelName || '原级别规则'}（原记录）`,
  score: Number(record.baseScore || record.expectedScore || 0),
  authorRules: record.authorRuleId ? [buildLegacyAuthorRule(record)] : [],
})

const buildLegacyItem = (record, sourceItem = null) => {
  const item = sourceItem ? cloneGroupWithItems({}, [sourceItem]).items[0] : {
    id: record.itemId,
    itemName: `${record.groupName || '原大类'} / ${record.itemName || '原小类'}（原记录）`,
    scoreDirection: record.scoreDirection || 'add',
    status: 'legacy',
    levelRules: [],
  }
  const levelRules = [...(item.levelRules || [])]
  const level = levelRules.find((rule) => Number(rule.id) === Number(record.levelRuleId))

  if (!level && record.levelRuleId) {
    levelRules.push(buildLegacyLevelRule(record))
  } else if (level && record.authorRuleId) {
    const authorRules = [...(level.authorRules || [])]
    if (!authorRules.some((rule) => Number(rule.id) === Number(record.authorRuleId))) {
      authorRules.push(buildLegacyAuthorRule(record))
    }
    level.authorRules = authorRules
  }

  return {
    ...item,
    itemName: sourceItem
      ? `${record.groupName || '原大类'} / ${sourceItem.itemName || record.itemName || '原小类'}（原记录）`
      : item.itemName,
    levelRules,
    fromOriginalRecord: true,
    ruleUnavailable: !sourceItem,
  }
}

export const normalizeVisibleTemplateGroups = (groups = []) =>
  groups
    .map((group) => cloneGroupWithItems(group, (group.items || []).filter(isVisibleTemplateItem)))
    .filter((group) => hasText(group.groupName) && group.items.length)

export const buildTemplateGroupsForApplication = (groups = [], editingRecord = null) => {
  const visibleGroups = normalizeVisibleTemplateGroups(groups)
  if (!editingRecord?.itemId) {
    return visibleGroups
  }

  const visibleHasRecordItem = visibleGroups.some((group) =>
    (group.items || []).some((item) => Number(item.id) === Number(editingRecord.itemId)),
  )
  if (visibleHasRecordItem) {
    return visibleGroups
  }

  const recordItem = findRecordItem(groups, editingRecord)
  const legacyItem = buildLegacyItem(editingRecord, recordItem?.item || null)
  return [
    ...visibleGroups,
    {
      id: `legacy-${editingRecord.id || editingRecord.itemId}`,
      groupName: '原申请记录',
      items: [legacyItem],
    },
  ]
}
