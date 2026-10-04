import { useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { message } from 'antd'
import { confirmAction } from '../../components/confirmDialogService'
import {
  createExtraScoreTemplate,
  getExtraScoreTemplateDetail,
  updateExtraScoreTemplate,
} from '../../api/admin/extraScoreTemplate'
import { getStoredUser } from '../../utils/authStorage'
import {
  canEditExtraScoreTemplate,
  getExtraScoreTemplateEditDeniedMessage,
} from '../../utils/extraScoreTemplatePermissions'
import './CollegeManagement.css'
import './ExtraScoreTemplateEditor.css'

const typeLabelMap = {
  MORAL: '德育',
  INTELLECTUAL: '智育',
  PHYSICAL: '体育',
}

const defaultTemplateName = (scoreType) => `${typeLabelMap[scoreType] || '附加分'}附加分模板`

const defaultLevelNames = [
  '国家一等奖',
  '国家二等奖',
  '国家三等奖',
  '省级一等奖',
  '省级二等奖',
  '省级三等奖',
  '校级一等奖',
  '校级二等奖',
  '校级三等奖',
]
const chineseNums = ['一', '二', '三', '四', '五', '六', '七', '八', '九', '十']
const defaultAuthorLabels = chineseNums.map((n) => `第${n}作者`)

const createPresetLevelRules = () =>
  defaultLevelNames.map((levelName, index) => ({
    levelName,
    score: '',
    sortOrder: index,
    authorRules: [createAuthorRule(0), createAuthorRule(1)],
  }))

const createAuthorRule = (index = 0) => ({
  authorLabel: defaultAuthorLabels[index] || `第${index + 1}作者`,
  ratio: 100,
  fixedScore: '',
  sortOrder: index,
})

const createLevelRule = () => ({
  levelName: '',
  score: '',
  sortOrder: 0,
  authorRules: [createAuthorRule(0), createAuthorRule(1)],
})

const createItem = () => ({
  itemName: '',
  description: '',
  maxAuthors: 1,
  sameEventMaxCount: null,
  scoreDirection: 'add',
  authorScoreMode: 'ratio',
  sortOrder: 0,
  status: 'active',
  levelRules: createPresetLevelRules(),
})

const createGroup = () => ({
  groupName: '',
  maxScore: null,
  sameEventBestOnly: false,
  sortOrder: 0,
  items: [createItem()],
})

const createEmptyForm = (scoreType) => ({
  name: '',
  scoreType,
  maxScore: 0,
  description: '',
  proofRequirement: '',
  status: 'active',
  groups: [],
})

const getDraftStorageKey = (scoreType) => `extra-score-template-draft:${scoreType}`

const readDraftForm = (scoreType) => {
  if (typeof window === 'undefined') {
    return createEmptyForm(scoreType)
  }

  try {
    const rawDraft = window.sessionStorage.getItem(getDraftStorageKey(scoreType))
    return rawDraft ? JSON.parse(rawDraft) : createEmptyForm(scoreType)
  } catch {
    return createEmptyForm(scoreType)
  }
}

const writeDraftForm = (scoreType, formState) => {
  if (typeof window === 'undefined') {
    return
  }

  try {
    window.sessionStorage.setItem(getDraftStorageKey(scoreType), JSON.stringify(formState))
  } catch {
    // Ignore storage quota/privacy mode failures; in-memory state still works while mounted.
  }
}

const clearDraftForm = (scoreType) => {
  if (typeof window === 'undefined') {
    return
  }

  window.sessionStorage.removeItem(getDraftStorageKey(scoreType))
}

const editFormCache = new Map()
const editSavedSnapshotCache = new Map()

const getEditCacheKey = (scoreType, templateId) => `${scoreType}:${templateId}`

const cloneFormState = (formState) => {
  if (!formState) {
    return null
  }

  if (typeof structuredClone === 'function') {
    return structuredClone(formState)
  }

  return JSON.parse(JSON.stringify(formState))
}

const readEditFormCache = (scoreType, templateId) =>
  cloneFormState(editFormCache.get(getEditCacheKey(scoreType, templateId)))

const writeEditFormCache = (scoreType, templateId, formState) => {
  editFormCache.set(getEditCacheKey(scoreType, templateId), cloneFormState(formState))
}

const readEditSavedSnapshot = (scoreType, templateId) =>
  editSavedSnapshotCache.get(getEditCacheKey(scoreType, templateId)) || ''

const writeEditSavedSnapshot = (scoreType, templateId, formState) => {
  editSavedSnapshotCache.set(getEditCacheKey(scoreType, templateId), JSON.stringify(formState))
}

const clearEditFormCache = (scoreType, templateId) => {
  editFormCache.delete(getEditCacheKey(scoreType, templateId))
  editSavedSnapshotCache.delete(getEditCacheKey(scoreType, templateId))
}

const safeNumber = (value, fallback = 0) => {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : fallback
}

// 数字步进器组件
const NumberStepper = ({
  value,
  onChange,
  min = 0,
  max = Infinity,
  step = 1,
  width = 100,
  disabled = false,
  readOnly = false,
}) => {
  const blocked = disabled || readOnly
  const allowDecimal = !Number.isInteger(step) || !Number.isInteger(min)
  const [inputText, setInputText] = useState('')
  const [editing, setEditing] = useState(false)
  const displayValue = editing ? inputText : (value ?? '')

  const normalizeManualValue = (rawValue) => {
    const parsed = Number(rawValue)
    if (!Number.isFinite(parsed)) {
      return safeNumber(value, min)
    }
    const bounded = Math.min(Math.max(parsed, min), max)
    return allowDecimal ? Math.round(bounded * 100) / 100 : Math.round(bounded)
  }

  const commitInputValue = () => {
    setEditing(false)
    if (String(inputText).trim() === '') {
      return
    }
    const normalizedValue = normalizeManualValue(inputText)
    setInputText(normalizedValue)
    onChange(normalizedValue)
  }

  const normalizeStepValue = (nextValue) => {
    const bounded = Math.min(Math.max(nextValue, min), max)
    return allowDecimal ? Math.round(bounded * 100) / 100 : Math.round(bounded)
  }

  const handleDecrement = () => {
    if (blocked) return
    const newValue = normalizeStepValue(safeNumber(value, 0) - step)
    if (newValue >= min) {
      onChange(newValue)
    }
  }

  const handleIncrement = () => {
    if (blocked) return
    const newValue = normalizeStepValue(safeNumber(value, 0) + step)
    if (newValue <= max) {
      onChange(newValue)
    }
  }

  const handleInputChange = (e) => {
    if (blocked) return
    const val = e.target.value
    const validPattern = allowDecimal ? /^\d*(\.\d*)?$/ : /^\d*$/
    if (!validPattern.test(val)) {
      return
    }
    setInputText(val)
    if (val === '' || val === '.') {
      return
    }
    const num = Number(val)
    if (Number.isFinite(num) && num >= min && num <= max) {
      onChange(allowDecimal ? num : Math.round(num))
    }
  }

  return (
    <div
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        width: width,
        minWidth: width,
        height: 36,
        border: '1px solid #d9d9d9',
        borderRadius: 6,
        background: disabled ? '#f5f5f5' : '#fff',
        overflow: 'hidden',
        boxSizing: 'border-box',
        flexShrink: 0,
      }}
    >
      <button
        type="button"
        onClick={handleDecrement}
        disabled={blocked || safeNumber(value, 0) <= min}
        style={{
          width: 32,
          minWidth: 32,
          flex: '0 0 32px',
          height: '100%',
          border: 'none',
          borderRight: '1px solid #d9d9d9',
          background: '#fafafa',
          cursor: blocked || safeNumber(value, 0) <= min ? 'not-allowed' : 'pointer',
          color: blocked || safeNumber(value, 0) <= min ? '#bfbfbf' : '#666',
          fontSize: 16,
          fontWeight: 600,
          lineHeight: 1,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          transition: 'all 0.2s',
          padding: 0,
          boxSizing: 'border-box',
        }}
      >
        -
      </button>
      <input
        type="number"
        value={displayValue}
        onChange={handleInputChange}
        onFocus={() => {
          setInputText(value ?? '')
          setEditing(true)
        }}
        onBlur={commitInputValue}
        onKeyDown={(e) => {
          if (e.key === 'Enter') {
            e.currentTarget.blur()
          }
        }}
        step={allowDecimal ? 'any' : step}
        disabled={disabled}
        readOnly={readOnly}
        className="no-spinners"
        style={{
          flex: '1 1 auto',
          minWidth: 0,
          height: '100%',
          border: 'none',
          textAlign: 'center',
          fontSize: 14,
          color: '#262626',
          background: disabled ? '#f5f5f5' : '#fff',
          outline: 'none',
          appearance: 'textfield',
          MozAppearance: 'textfield',
          opacity: 1,
          WebkitTextFillColor: '#262626',
        }}
      />
      <button
        type="button"
        onClick={handleIncrement}
        disabled={blocked || safeNumber(value, 0) >= max}
        style={{
          width: 32,
          minWidth: 32,
          flex: '0 0 32px',
          height: '100%',
          border: 'none',
          borderLeft: '1px solid #d9d9d9',
          background: '#fafafa',
          cursor: blocked || safeNumber(value, 0) >= max ? 'not-allowed' : 'pointer',
          color: blocked || safeNumber(value, 0) >= max ? '#bfbfbf' : '#666',
          fontSize: 16,
          fontWeight: 600,
          lineHeight: 1,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          transition: 'all 0.2s',
          padding: 0,
          boxSizing: 'border-box',
        }}
      >
        +
      </button>
    </div>
  )
}

const computeTemplateMaxScore = (groups = []) => {
  let maxScore = 0
  groups.forEach((group) => {
    ;(group.items || []).forEach((item) => {
      ;(item.levelRules || []).forEach((rule) => {
        maxScore = Math.max(maxScore, computeLevelRuleMaxScore(item, rule))
      })
    })
  })
  return maxScore
}

const computeLevelRuleMaxScore = (item, rule) => {
  if (!rule) {
    return 0
  }
  if ((item?.authorScoreMode || 'ratio') === 'fixed') {
    return Math.max(
      ...(rule.authorRules || []).map((authorRule) => safeNumber(authorRule.fixedScore, 0)),
      0,
    )
  }
  const baseScore = safeNumber(rule.score, 0)
  return Math.max(
    ...(rule.authorRules || []).map(
      (authorRule) => (baseScore * safeNumber(authorRule.ratio, 0)) / 100,
    ),
    0,
  )
}

const hasValidLevelRuleScore = (item, rule) => {
  if (!rule?.levelName?.trim()) {
    return false
  }
  if ((item?.authorScoreMode || 'ratio') === 'fixed') {
    return (rule.authorRules || []).some(
      (authorRule) => authorRule.authorLabel?.trim() && safeNumber(authorRule.fixedScore, 0) > 0,
    )
  }
  return (
    safeNumber(rule.score, 0) > 0 &&
    (rule.authorRules || []).some(
      (authorRule) => authorRule.authorLabel?.trim() && safeNumber(authorRule.ratio, 0) > 0,
    )
  )
}

const normalizeTemplateToForm = (template, scoreType) => {
  const groups = (template?.groups || []).map((group) => ({
    id: group.id,
    groupName: group.groupName || '',
    maxScore: group.maxScore ?? null,
    sameEventBestOnly: Boolean(group.sameEventBestOnly),
    sortOrder: safeNumber(group.sortOrder),
    items: (group.items || []).map((item) => ({
      id: item.id,
      itemName: item.itemName || '',
      description: item.description || '',
      maxAuthors: safeNumber(item.maxAuthors, 1),
      sameEventMaxCount: item.sameEventMaxCount ?? null,
      scoreDirection: item.scoreDirection || 'add',
      sortOrder: safeNumber(item.sortOrder),
      status: item.status || 'active',
      authorScoreMode:
        item.authorScoreMode || item.levelRules?.[0]?.authorRules?.[0]?.mode || 'ratio',
      levelRules: (item.levelRules || []).length
        ? item.levelRules.map((rule) => ({
            id: rule.id,
            levelName: rule.levelName || '',
            score: rule.score ?? '',
            sortOrder: safeNumber(rule.sortOrder),
            authorRules: (rule.authorRules || []).length
              ? rule.authorRules.map((ar) => ({
                  id: ar.id,
                  authorLabel: ar.authorLabel || '',
                  ratio: ar.ratio ?? 100,
                  fixedScore: ar.fixedScore ?? '',
                  sortOrder: safeNumber(ar.sortOrder),
                }))
              : [createAuthorRule(0), createAuthorRule(1)],
          }))
        : createPresetLevelRules(),
    })),
  }))

  return {
    name: template?.name || '',
    scoreType: template?.scoreType || scoreType,
    maxScore: template?.maxScore ?? computeTemplateMaxScore(groups),
    description: template?.description || '',
    proofRequirement: template?.proofRequirement || '',
    status: template?.status || 'active',
    groups,
  }
}

function ExtraScoreTemplateEditor({ forcedType = 'MORAL' }) {
  const navigate = useNavigate()
  const { templateId, groupIndex: groupIndexParam, itemIndex: itemIndexParam } = useParams()
  const isCreateMode = !templateId || templateId === 'new'
  const user = useMemo(() => getStoredUser(), [])
  const initialCachedForm = useMemo(
    () => (isCreateMode ? null : readEditFormCache(forcedType, templateId)),
    [forcedType, isCreateMode, templateId],
  )

  const [formState, setFormState] = useState(() =>
    isCreateMode
      ? readDraftForm(forcedType)
      : initialCachedForm || createEmptyForm(forcedType),
  )
  const [loading, setLoading] = useState(!isCreateMode)
  const [dataLoaded, setDataLoaded] = useState(isCreateMode)
  const [saving, setSaving] = useState(false)
  const [feedback, setFeedback] = useState('')
  const [feedbackType, setFeedbackType] = useState('info')
  const [selectedLevelIndex, setSelectedLevelIndex] = useState(null)
  const [levelRuleUndoStack, setLevelRuleUndoStack] = useState([])
  const [levelRuleRedoStack, setLevelRuleRedoStack] = useState([])
  const [showSameEventHelp, setShowSameEventHelp] = useState(false)
  const sameEventHelpRef = useRef(null)
  const savedSnapshotRef = useRef(
    isCreateMode ? JSON.stringify(createEmptyForm(forcedType)) : '',
  )

  const currentGroupIndex = groupIndexParam === undefined ? null : safeNumber(groupIndexParam, 0)
  const currentItemIndex = itemIndexParam === undefined ? null : safeNumber(itemIndexParam, 0)
  const currentView =
    currentItemIndex !== null ? 'item' : currentGroupIndex !== null ? 'group' : 'template'

  const showFeedback = (type, text) => {
    setFeedback(text)
    setFeedbackType(type)
    if (type === 'success') {
      message.success(text)
    } else if (type === 'warning') {
      message.warning(text)
    } else if (type === 'error') {
      message.error(text)
    }
  }

  useEffect(() => {
    if (isCreateMode) {
      const draftForm = readDraftForm(forcedType)
      setFormState(draftForm)
      savedSnapshotRef.current = JSON.stringify(createEmptyForm(forcedType))
      setDataLoaded(true)
      return
    }

    let cancelled = false
    const fetchTemplate = async () => {
      setLoading(true)
      setDataLoaded(false)
      try {
        const res = await getExtraScoreTemplateDetail(templateId)
        if (cancelled) {
          return
        }
        const template = res?.data
        if (!template) {
          showFeedback('error', '模板不存在')
          return
        }
        if (!canEditExtraScoreTemplate(template, user)) {
          clearEditFormCache(forcedType, templateId)
          showFeedback('warning', getExtraScoreTemplateEditDeniedMessage(template))
          navigate(`/admin/extra-score-templates/${forcedType.toLowerCase()}`, { replace: true })
          return
        }
        const cachedForm = readEditFormCache(forcedType, templateId)
        const normalizedForm = cachedForm || normalizeTemplateToForm(template, forcedType)
        const cachedSavedSnapshot = readEditSavedSnapshot(forcedType, templateId)
        setFormState(normalizedForm)
        savedSnapshotRef.current = cachedSavedSnapshot || JSON.stringify(normalizedForm)
        if (!cachedSavedSnapshot) {
          writeEditSavedSnapshot(forcedType, templateId, normalizedForm)
        }
        setDataLoaded(true)
      } catch (error) {
        if (!cancelled) {
          showFeedback('error', error?.response?.data?.message || '获取模板详情失败')
        }
      } finally {
        if (!cancelled) {
          setLoading(false)
        }
      }
    }

    fetchTemplate()
    return () => {
      cancelled = true
    }
  }, [forcedType, isCreateMode, navigate, templateId, user])

  useEffect(() => {
    if (isCreateMode) {
      writeDraftForm(forcedType, formState)
      return
    }
    if (dataLoaded && templateId) {
      writeEditFormCache(forcedType, templateId, formState)
    }
  }, [forcedType, formState, isCreateMode, dataLoaded, templateId])

  useEffect(() => {
    if (currentGroupIndex === null) {
      return
    }
    if (!dataLoaded) {
      return
    }
    const group = formState.groups[currentGroupIndex]
    if (!group) {
      navigate(
        `/admin/extra-score-templates/${forcedType.toLowerCase()}/${isCreateMode ? 'new' : templateId}`,
        { replace: true },
      )
      return
    }
    if (currentItemIndex !== null && !group.items?.[currentItemIndex]) {
      navigate(
        `/admin/extra-score-templates/${forcedType.toLowerCase()}/${isCreateMode ? 'new' : templateId}/groups/${currentGroupIndex}`,
        { replace: true },
      )
    }
  }, [
    currentGroupIndex,
    currentItemIndex,
    formState.groups,
    forcedType,
    isCreateMode,
    dataLoaded,
    navigate,
    templateId,
  ])

  useEffect(() => {
    if (!showSameEventHelp) {
      return
    }
    const onDocMouseDown = (event) => {
      if (sameEventHelpRef.current && !sameEventHelpRef.current.contains(event.target)) {
        setShowSameEventHelp(false)
      }
    }
    document.addEventListener('mousedown', onDocMouseDown)
    return () => document.removeEventListener('mousedown', onDocMouseDown)
  }, [showSameEventHelp])

  const pageTitle = `${typeLabelMap[forcedType]}模板`
  const derivedMaxScore = useMemo(
    () => computeTemplateMaxScore(formState.groups),
    [formState.groups],
  )
  const sortedGroups = useMemo(
    () =>
      formState.groups
        .map((group, index) => ({ group, index }))
        .sort((a, b) => {
          const orderDiff =
            safeNumber(a.group.sortOrder, a.index) - safeNumber(b.group.sortOrder, b.index)
          return orderDiff !== 0 ? orderDiff : a.index - b.index
        }),
    [formState.groups],
  )

  const updateFormField = (field, value) => {
    setFormState((prev) => ({ ...prev, [field]: value }))
  }

  const updateGroup = (groupIndex, updater) => {
    setFormState((prev) => ({
      ...prev,
      groups: prev.groups.map((group, index) => (index === groupIndex ? updater(group) : group)),
    }))
  }

  const updateItem = (groupIndex, itemIndex, updater) => {
    updateGroup(groupIndex, (group) => ({
      ...group,
      items: group.items.map((item, index) => (index === itemIndex ? updater(item) : item)),
    }))
  }

  const addGroup = () => {
    setFormState((prev) => ({
      ...prev,
      groups: [...prev.groups, { ...createGroup(), sortOrder: prev.groups.length }],
    }))
  }

  const removeGroup = (groupIndex) => {
    setFormState((prev) => ({
      ...prev,
      groups: prev.groups.filter((_, index) => index !== groupIndex),
    }))
  }

  const addItem = (groupIndex) => {
    updateGroup(groupIndex, (group) => ({
      ...group,
      items: [...group.items, { ...createItem(), sortOrder: group.items.length }],
    }))
  }

  const removeItem = (groupIndex, itemIndex) => {
    updateGroup(groupIndex, (group) => ({
      ...group,
      items: group.items.filter((_, index) => index !== itemIndex),
    }))
    if (
      currentView === 'item' &&
      currentGroupIndex === groupIndex &&
      currentItemIndex === itemIndex
    ) {
      navigate(
        `/admin/extra-score-templates/${forcedType.toLowerCase()}/${isCreateMode ? 'new' : templateId}/groups/${groupIndex}`,
      )
    }
    setSelectedLevelIndex(null)
  }

  const addLevelRule = (groupIndex, itemIndex) => {
    updateItem(groupIndex, itemIndex, (item) => ({
      ...item,
      levelRules: [...item.levelRules, { ...createLevelRule(), sortOrder: item.levelRules.length }],
    }))
  }

  const removeLevelRule = (groupIndex, itemIndex, ruleIndex) => {
    const currentRules = formState.groups[groupIndex]?.items?.[itemIndex]?.levelRules || []
    setLevelRuleUndoStack((prev) => [...prev, currentRules.map((rule) => ({ ...rule }))])
    setLevelRuleRedoStack([])
    updateItem(groupIndex, itemIndex, (item) => ({
      ...item,
      levelRules: item.levelRules.filter((_, index) => index !== ruleIndex),
    }))
  }

  const moveLevelRule = (groupIndex, itemIndex, ruleIndex, direction) => {
    const targetIndex = ruleIndex + direction
    updateItem(groupIndex, itemIndex, (item) => {
      const rules = [...item.levelRules]
      if (targetIndex < 0 || targetIndex >= rules.length) return item
      ;[rules[ruleIndex], rules[targetIndex]] = [rules[targetIndex], rules[ruleIndex]]
      return { ...item, levelRules: rules }
    })
    setSelectedLevelIndex(targetIndex)
  }

  const undoLevelRuleDeletion = () => {
    if (!levelRuleUndoStack.length || currentGroupIndex === null || currentItemIndex === null) {
      return
    }
    const currentRules =
      formState.groups[currentGroupIndex]?.items?.[currentItemIndex]?.levelRules || []
    const previousRules = levelRuleUndoStack[levelRuleUndoStack.length - 1]
    setLevelRuleUndoStack((prev) => prev.slice(0, -1))
    setLevelRuleRedoStack((prev) => [...prev, currentRules.map((rule) => ({ ...rule }))])
    updateItem(currentGroupIndex, currentItemIndex, (item) => ({
      ...item,
      levelRules: previousRules.map((rule) => ({ ...rule })),
    }))
    setSelectedLevelIndex(null)
  }

  const redoLevelRuleDeletion = () => {
    if (!levelRuleRedoStack.length || currentGroupIndex === null || currentItemIndex === null) {
      return
    }
    const currentRules =
      formState.groups[currentGroupIndex]?.items?.[currentItemIndex]?.levelRules || []
    const nextRules = levelRuleRedoStack[levelRuleRedoStack.length - 1]
    setLevelRuleRedoStack((prev) => prev.slice(0, -1))
    setLevelRuleUndoStack((prev) => [...prev, currentRules.map((rule) => ({ ...rule }))])
    updateItem(currentGroupIndex, currentItemIndex, (item) => ({
      ...item,
      levelRules: nextRules.map((rule) => ({ ...rule })),
    }))
    setSelectedLevelIndex(null)
  }

  const addAuthorRule = (groupIndex, itemIndex, levelRuleIndex) => {
    updateItem(groupIndex, itemIndex, (item) => ({
      ...item,
      levelRules: item.levelRules.map((lr, lrIdx) =>
        lrIdx !== levelRuleIndex
          ? lr
          : {
              ...lr,
              authorRules: [
                ...(lr.authorRules || []),
                {
                  ...createAuthorRule((lr.authorRules || []).length),
                  sortOrder: (lr.authorRules || []).length,
                },
              ],
            },
      ),
    }))
  }

  const removeAuthorRule = (groupIndex, itemIndex, levelRuleIndex, ruleIndex) => {
    updateItem(groupIndex, itemIndex, (item) => ({
      ...item,
      levelRules: item.levelRules.map((lr, lrIdx) =>
        lrIdx !== levelRuleIndex
          ? lr
          : {
              ...lr,
              authorRules: lr.authorRules.filter((_, index) => index !== ruleIndex),
            },
      ),
    }))
  }

  const sanitizeGroupsForSave = () =>
    formState.groups
      .map((group, groupIndex) => {
        const groupName = group.groupName?.trim()
        if (!groupName) {
          return null
        }
        const items = (group.items || [])
          .map((item, itemIndex) => {
            const itemName = item.itemName?.trim()
            if (!itemName || (item.status || 'active') !== 'active') {
              return null
            }
            const levelRules = (item.levelRules || [])
              .map((rule, ruleIndex) => {
                if (!hasValidLevelRuleScore(item, rule)) {
                  return null
                }
                return {
                  ...rule,
                  levelName: rule.levelName.trim(),
                  score:
                    item.authorScoreMode === 'fixed'
                      ? computeLevelRuleMaxScore(item, rule)
                      : safeNumber(rule.score, 0),
                  sortOrder: rule.sortOrder ?? ruleIndex,
                  authorRules: (rule.authorRules || [])
                    .filter((ar) => ar.authorLabel?.trim())
                    .map((ar, arIndex) => ({
                      ...ar,
                      authorLabel: ar.authorLabel.trim(),
                      mode: item.authorScoreMode || 'ratio',
                      ratio: item.authorScoreMode === 'fixed' ? null : safeNumber(ar.ratio, 0),
                      fixedScore:
                        item.authorScoreMode === 'fixed' ? safeNumber(ar.fixedScore, 0) : null,
                      sortOrder: ar.sortOrder ?? arIndex,
                    }))
                    .filter((ar) =>
                      ar.mode === 'fixed'
                        ? safeNumber(ar.fixedScore, 0) > 0
                        : safeNumber(ar.ratio, 0) > 0,
                    ),
                }
              })
              .filter(Boolean)
            if (!levelRules.length) {
              return null
            }
            return {
              ...item,
              itemName,
              maxAuthors: Math.max(1, safeNumber(item.maxAuthors, 1)),
              sameEventMaxCount:
                item.sameEventMaxCount === null ||
                item.sameEventMaxCount === undefined ||
                item.sameEventMaxCount === ''
                  ? null
                  : safeNumber(item.sameEventMaxCount, 1),
              scoreDirection: item.scoreDirection || 'add',
              sortOrder: item.sortOrder ?? itemIndex,
              levelRules,
            }
          })
          .filter(Boolean)
        if (!items.length) {
          return null
        }
        return {
          ...group,
          groupName,
          maxScore:
            group.maxScore === null || group.maxScore === undefined || group.maxScore === ''
              ? null
              : safeNumber(group.maxScore, 0),
          sameEventBestOnly: Boolean(group.sameEventBestOnly),
          sortOrder: group.sortOrder ?? groupIndex,
          items,
        }
      })
      .filter(Boolean)

  const validateTemplate = () => {
    setFeedback('')
    setFeedbackType('info')
    return true
  }

  const buildPayload = () => {
    const groups = sanitizeGroupsForSave()
    return {
      ...formState,
      name: formState.name.trim() || defaultTemplateName(forcedType),
      scoreType: forcedType,
      maxScore: computeTemplateMaxScore(groups) || safeNumber(formState.maxScore, 0),
      groups,
    }
  }

  const handleSave = async ({ stayAfterSave = false } = {}) => {
    if (!validateTemplate()) {
      return
    }
    setSaving(true)
    try {
      const payload = buildPayload()
      let savedTemplateId = templateId
      let savedTemplate = null
      if (isCreateMode) {
        const response = await createExtraScoreTemplate(payload)
        savedTemplate = response?.data || response || null
        savedTemplateId = response?.data?.id || response?.id || null
        clearDraftForm(forcedType)
      } else {
        const response = await updateExtraScoreTemplate(templateId, payload)
        savedTemplate = response?.data || response || null
      }

      const normalizedSavedForm = savedTemplate
        ? normalizeTemplateToForm(savedTemplate, forcedType)
        : normalizeTemplateToForm(payload, forcedType)
      savedSnapshotRef.current = JSON.stringify(normalizedSavedForm)

      if (stayAfterSave) {
        setFormState(normalizedSavedForm)
        if (savedTemplateId) {
          writeEditFormCache(forcedType, savedTemplateId, normalizedSavedForm)
          writeEditSavedSnapshot(forcedType, savedTemplateId, normalizedSavedForm)
        }
        if (isCreateMode && savedTemplateId) {
          navigate(`/admin/extra-score-templates/${forcedType.toLowerCase()}/${savedTemplateId}`, {
            replace: true,
          })
        }
        showFeedback('success', '保存成功，空白大类或小类已自动隐藏')
      } else {
        if (!isCreateMode && templateId) {
          clearEditFormCache(forcedType, templateId)
        }
        message.success('保存成功，正在返回列表')
        navigate(`/admin/extra-score-templates/${forcedType.toLowerCase()}`)
      }
    } catch (error) {
      showFeedback('error', error?.response?.data?.message || '保存模板失败')
    } finally {
      setSaving(false)
    }
  }

  const currentGroup =
    currentGroupIndex !== null ? formState.groups[currentGroupIndex] || null : null
  const currentItem =
    currentGroup && currentItemIndex !== null
      ? currentGroup.items?.[currentItemIndex] || null
      : null
  const sortedCurrentItems = useMemo(
    () =>
      (currentGroup?.items || [])
        .map((item, index) => ({ item, index }))
        .sort((a, b) => {
          const orderDiff =
            safeNumber(a.item.sortOrder, a.index) - safeNumber(b.item.sortOrder, b.index)
          return orderDiff !== 0 ? orderDiff : a.index - b.index
        }),
    [currentGroup],
  )

  const hasUnsavedChanges =
    dataLoaded &&
    Boolean(savedSnapshotRef.current) &&
    JSON.stringify(formState) !== savedSnapshotRef.current

  useEffect(() => {
    if (!hasUnsavedChanges || typeof window === 'undefined') {
      return undefined
    }
    const handleBeforeUnload = (event) => {
      event.preventDefault()
      event.returnValue = ''
    }
    window.addEventListener('beforeunload', handleBeforeUnload)
    return () => window.removeEventListener('beforeunload', handleBeforeUnload)
  }, [hasUnsavedChanges])

  if (loading) {
    return <div className="template-editor-loading">模板加载中...</div>
  }

  const sectionCardStyle = {
    background: '#fff',
    borderRadius: 8,
    padding: 20,
    boxShadow: 'none',
    border: '1px solid #e2e8f0',
  }

  const sectionHeaderStyle = {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 14,
  }

  const templatePathSegment = isCreateMode ? 'new' : templateId
  const templateListPath = `/admin/extra-score-templates/${forcedType.toLowerCase()}`
  const baseEditorPath = `/admin/extra-score-templates/${forcedType.toLowerCase()}/${templatePathSegment}`
  const openTemplateView = () => navigate(baseEditorPath)
  const openGroupView = (groupIndex) => navigate(`${baseEditorPath}/groups/${groupIndex}`)
  const openItemView = (groupIndex, itemIndex) => {
    setSelectedLevelIndex(null)
    navigate(`${baseEditorPath}/groups/${groupIndex}/items/${itemIndex}`)
  }

  const clearCurrentDraft = () => {
    if (isCreateMode) {
      clearDraftForm(forcedType)
    } else if (templateId) {
      clearEditFormCache(forcedType, templateId)
    }
  }

  const handleReturnToList = async () => {
    if (
      hasUnsavedChanges &&
      !await confirmAction({
        title: '放弃未保存修改？',
        message: '当前修改尚未保存，返回模板列表后这些内容将丢失。',
        confirmText: '放弃并返回',
        tone: 'warning',
      })
    ) {
      return
    }
    clearCurrentDraft()
    navigate(templateListPath)
  }

  const handleReturnToPreviousLevel = async () => {
    if (currentView === 'item' && currentGroupIndex !== null) {
      openGroupView(currentGroupIndex)
      return
    }
    if (currentView === 'group') {
      openTemplateView()
      return
    }
    await handleReturnToList()
  }

  const handleDiscardAndReturn = async () => {
    if (!await confirmAction({
      title: '放弃当前修改？',
      message: '当前编辑内容不会保存，确定返回模板列表吗？',
      confirmText: '放弃并返回',
      tone: 'warning',
    })) {
      return
    }
    clearCurrentDraft()
    navigate(templateListPath)
  }

  const editorTitle =
    currentView === 'item'
      ? currentItem?.itemName || '未命名小类'
      : currentView === 'group'
        ? currentGroup?.groupName || '未命名大类'
        : formState.name || (isCreateMode ? `新建${pageTitle}` : pageTitle)

  return (
    <div className="college-management template-editor-page">
      <div className="template-editor-shell">
        <header className="template-editor-header">
          <div className="template-editor-heading">
            <div className="template-editor-context">
              <button
                type="button"
                className="template-editor-back"
                onClick={handleReturnToPreviousLevel}
              >
                <span aria-hidden="true">←</span>
                返回上一步
              </button>
              <nav className="template-editor-breadcrumb" aria-label="模板配置层级">
                <button type="button" onClick={handleReturnToList}>
                  {typeLabelMap[forcedType]}模板
                </button>
                {currentView !== 'template' ? (
                  <>
                    <span>/</span>
                    <button type="button" onClick={openTemplateView}>
                      {formState.name || (isCreateMode ? '新建模板' : '未命名模板')}
                    </button>
                  </>
                ) : null}
                {currentView === 'item' && currentGroup ? (
                  <>
                    <span>/</span>
                    <button type="button" onClick={() => openGroupView(currentGroupIndex)}>
                      {currentGroup.groupName || '未命名大类'}
                    </button>
                  </>
                ) : null}
              </nav>
            </div>
            <h1>{editorTitle}</h1>
          </div>

          <div className="template-editor-actions">
            <span className={`template-editor-save-state ${hasUnsavedChanges ? 'dirty' : 'saved'}`}>
              {saving ? '保存中...' : hasUnsavedChanges ? '有未保存修改' : '已保存'}
            </span>
            <button
              type="button"
              className="template-editor-save"
              onClick={() => handleSave({ stayAfterSave: true })}
              disabled={saving || !hasUnsavedChanges}
            >
              {saving ? '保存中...' : '保存'}
            </button>
            <details className="template-editor-more">
              <summary>更多</summary>
              <div>
                <button
                  type="button"
                  onClick={() => handleSave({ stayAfterSave: false })}
                  disabled={saving}
                >
                  保存并返回模板列表
                </button>
                <button
                  type="button"
                  className="danger"
                  onClick={handleDiscardAndReturn}
                  disabled={!hasUnsavedChanges}
                >
                  放弃修改并返回
                </button>
              </div>
            </details>
          </div>
        </header>

        <div className="template-editor-layout">
          <aside className="template-editor-tree" aria-label="模板结构">
            <div className="template-editor-tree-title">
              <strong>模板结构</strong>
              <span>{formState.groups.length} 个大类</span>
            </div>
            <button
              type="button"
              className={`template-editor-tree-root ${currentView === 'template' ? 'active' : ''}`}
              onClick={openTemplateView}
              aria-current={currentView === 'template' ? 'page' : undefined}
            >
              <span>模板基本信息</span>
              <small>{derivedMaxScore || 0} 分</small>
            </button>
            <div className="template-editor-tree-groups">
              {sortedGroups.map(({ group, index: groupIndex }, groupDisplayIndex) => {
                const sortedItems = (group.items || [])
                  .map((item, index) => ({ item, index }))
                  .sort((a, b) => {
                    const orderDiff =
                      safeNumber(a.item.sortOrder, a.index) -
                      safeNumber(b.item.sortOrder, b.index)
                    return orderDiff !== 0 ? orderDiff : a.index - b.index
                  })
                const groupSelected = currentGroupIndex === groupIndex
                return (
                  <div key={`tree-group-${groupIndex}`} className="template-editor-tree-group">
                    <button
                      type="button"
                      className={`template-editor-tree-group-button ${groupSelected && currentView === 'group' ? 'active' : ''}`}
                      onClick={() => openGroupView(groupIndex)}
                    >
                      <span>{group.groupName || `未命名大类 ${groupDisplayIndex + 1}`}</span>
                      <small>{sortedItems.length}</small>
                    </button>
                    <div className="template-editor-tree-items">
                      {sortedItems.map(({ item, index: itemIndex }, itemDisplayIndex) => {
                        const itemSelected =
                          currentView === 'item' &&
                          currentGroupIndex === groupIndex &&
                          currentItemIndex === itemIndex
                        return (
                          <button
                            type="button"
                            key={`tree-item-${groupIndex}-${itemIndex}`}
                            className={itemSelected ? 'active' : ''}
                            onClick={() => openItemView(groupIndex, itemIndex)}
                            aria-current={itemSelected ? 'page' : undefined}
                          >
                            {item.itemName || `未命名小类 ${itemDisplayIndex + 1}`}
                          </button>
                        )
                      })}
                    </div>
                  </div>
                )
              })}
            </div>
            <button
              type="button"
              className="template-editor-tree-add"
              onClick={() => {
                const nextGroupIndex = formState.groups.length
                addGroup()
                openGroupView(nextGroupIndex)
              }}
            >
              + 新增大类
            </button>
          </aside>

          <main className="template-editor-content">
            {feedback ? (
              <div className={`template-editor-feedback ${feedbackType}`}>{feedback}</div>
            ) : null}

        {currentView === 'template' ? (
          <div style={{ display: 'block' }}>
            <div
              style={{
                ...sectionCardStyle,
                position: 'relative',
                display: 'flex',
                flexDirection: 'column',
              }}
            >
              <div style={{ ...sectionHeaderStyle, alignItems: 'flex-start' }}>
                <div>
                  <h3 style={{ margin: 0, color: '#0f172a', fontSize: 17, fontWeight: 600 }}>
                    模板基本信息
                  </h3>
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                  {/* 状态切换按钮 */}
                  <button
                    type="button"
                    className={`template-editor-status-toggle ${formState.status === 'active' ? 'active' : ''}`}
                    onClick={() =>
                      updateFormField(
                        'status',
                        formState.status === 'active' ? 'inactive' : 'active',
                      )
                    }
                  >
                    {formState.status === 'active' ? '✓ 启用' : '✗ 停用'}
                  </button>
                </div>
              </div>

              <div style={{ display: 'flex', flexDirection: 'column', gap: 12, flex: 1 }}>
                {/* 第一行：模板名称 */}
                <div style={{ display: 'grid', gridTemplateColumns: '1fr', gap: 12 }}>
                  <div
                    className="form-group"
                    style={{ display: 'flex', flexDirection: 'column', gap: 8 }}
                  >
                    <label style={{ fontSize: 14, fontWeight: 500, color: '#374151' }}>
                      模板名称 <span style={{ color: '#ef4444' }}>*</span>
                    </label>
                    <input
                      value={formState.name}
                      onChange={(e) => updateFormField('name', e.target.value)}
                      style={{
                        padding: '10px 12px',
                        borderRadius: 8,
                        border: '1px solid #d1d5db',
                        fontSize: 15,
                        height: 42,
                        boxSizing: 'border-box',
                        transition: 'all 0.2s ease',
                        outline: 'none',
                        width: '100%',
                      }}
                      onFocus={(e) => {
                        e.target.style.borderColor = '#3b82f6'
                        e.target.style.boxShadow = '0 0 0 3px rgba(59, 130, 246, 0.1)'
                      }}
                      onBlur={(e) => {
                        e.target.style.borderColor = '#d1d5db'
                        e.target.style.boxShadow = 'none'
                      }}
                    />
                  </div>
                </div>

                {/* 第二行：模板说明 */}
                <div
                  className="form-group"
                  style={{ display: 'flex', flexDirection: 'column', gap: 8 }}
                >
                  <label style={{ fontSize: 14, fontWeight: 500, color: '#374151' }}>
                    模板说明
                  </label>
                  <textarea
                    rows={3}
                    value={formState.description}
                    onChange={(e) => updateFormField('description', e.target.value)}
                    style={{
                      padding: '10px 12px',
                      borderRadius: 8,
                      border: '1px solid #d1d5db',
                      fontSize: 15,
                      resize: 'vertical',
                      minHeight: 56,
                      boxSizing: 'border-box',
                      transition: 'all 0.2s ease',
                      outline: 'none',
                      width: '100%',
                    }}
                    onFocus={(e) => {
                      e.target.style.borderColor = '#3b82f6'
                      e.target.style.boxShadow = '0 0 0 3px rgba(59, 130, 246, 0.1)'
                    }}
                    onBlur={(e) => {
                      e.target.style.borderColor = '#d1d5db'
                      e.target.style.boxShadow = 'none'
                    }}
                  />
                </div>

                {/* 第四行：提交材料说明 */}
                <div
                  className="form-group"
                  style={{ display: 'flex', flexDirection: 'column', gap: 8 }}
                >
                  <label style={{ fontSize: 14, fontWeight: 500, color: '#374151' }}>
                    提交材料说明
                  </label>
                  <textarea
                    rows={3}
                    value={formState.proofRequirement}
                    onChange={(e) => updateFormField('proofRequirement', e.target.value)}
                    placeholder="例如：需上传获奖证书、活动照片或成绩单。未填写则学生端显示为未配置。"
                    style={{
                      padding: '10px 12px',
                      borderRadius: 8,
                      border: '1px solid #d1d5db',
                      fontSize: 15,
                      resize: 'vertical',
                      minHeight: 56,
                      boxSizing: 'border-box',
                      transition: 'all 0.2s ease',
                      outline: 'none',
                      width: '100%',
                    }}
                    onFocus={(e) => {
                      e.target.style.borderColor = '#3b82f6'
                      e.target.style.boxShadow = '0 0 0 3px rgba(59, 130, 246, 0.1)'
                    }}
                    onBlur={(e) => {
                      e.target.style.borderColor = '#d1d5db'
                      e.target.style.boxShadow = 'none'
                    }}
                  />
                </div>
              </div>
            </div>
          </div>
        ) : null}

        {currentView === 'group' && currentGroup ? (
          <div
            className="template-editor-group-overview"
            style={{
              border: '1px solid #e2e8f0',
              borderRadius: 8,
              background: '#fff',
              padding: 16,
              display: 'grid',
              gridTemplateColumns: 'minmax(0, 1fr) 180px',
              gap: 14,
              alignItems: 'end',
            }}
          >
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              <label style={{ fontSize: 14, fontWeight: 500, color: '#374151' }}>
                大类名称（留空不展示）
              </label>
              <input
                value={currentGroup.groupName}
                onChange={(e) =>
                  updateGroup(currentGroupIndex, (current) => ({
                    ...current,
                    groupName: e.target.value,
                  }))
                }
                placeholder="请输入大类名称"
                style={{
                  padding: '10px 12px',
                  borderRadius: 8,
                  border: '1px solid #d1d5db',
                  fontSize: 15,
                  height: 40,
                  boxSizing: 'border-box',
                  width: '100%',
                }}
              />
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              <label style={{ fontSize: 14, fontWeight: 500, color: '#374151' }}>排序</label>
              <NumberStepper
                value={currentGroup.sortOrder}
                onChange={(val) =>
                  updateGroup(currentGroupIndex, (current) => ({ ...current, sortOrder: val }))
                }
                min={0}
                width={160}
              />
            </div>
          </div>
        ) : null}

        <div style={{ ...sectionCardStyle }}>
          {currentView !== 'item' ? (
            <div style={sectionHeaderStyle}>
              <h3 style={{ margin: 0, color: '#0f172a', fontSize: 20, fontWeight: 600 }}>
                {currentView === 'template' ? '大类列表' : '小类列表'}
              </h3>
              <div className="action-btns" style={{ display: 'flex', gap: 10 }}>
              {currentView === 'group' && currentGroupIndex !== null && (
                <button
                  type="button"
                  className="btn btn-default"
                  onClick={() => addItem(currentGroupIndex)}
                  style={{
                    padding: '9px 16px',
                    borderRadius: 8,
                    border: '1px solid #3b82f6',
                    background: '#fff',
                    color: '#3b82f6',
                    fontWeight: 600,
                    cursor: 'pointer',
                    transition: 'all 0.2s ease',
                  }}
                  onMouseEnter={(e) => {
                    e.target.style.background = '#3b82f6'
                    e.target.style.color = '#fff'
                  }}
                  onMouseLeave={(e) => {
                    e.target.style.background = '#fff'
                    e.target.style.color = '#3b82f6'
                  }}
                >
                  + 新增小类
                </button>
              )}
              </div>
            </div>
          ) : null}
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 10,
              flexWrap: 'wrap',
              marginBottom: 14,
            }}
          >
            <span
              style={{
                padding: '4px 10px',
                borderRadius: 999,
                border: '1px solid #dbe2ea',
                background: '#f8fafc',
                fontSize: 12,
                color: '#475569',
              }}
            >
              模板最高分 {derivedMaxScore || 0}
            </span>
          </div>

          {currentView === 'template' ? (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              <div style={{ display: 'grid', gap: 12 }}>
                {!sortedGroups.length ? (
                  <div
                    style={{
                      padding: 18,
                      borderRadius: 12,
                      border: '1px dashed #cbd5e1',
                      background: '#f8fafc',
                      color: '#64748b',
                      textAlign: 'center',
                    }}
                  >
                    暂无大类。可以先保存空模板，也可以点击“新增大类”继续配置。
                  </div>
                ) : null}
                {sortedGroups.map(({ group, index: groupIndex }, displayIndex) => {
                  const itemCount = (group.items || []).length
                  const maxSingleScore = Math.max(
                    ...(group.items || []).flatMap((item) =>
                      (item.levelRules || []).map((rule) => computeLevelRuleMaxScore(item, rule)),
                    ),
                    0,
                  )
                  return (
                    <div
                      key={`group-${groupIndex}`}
                      style={{
                        border: '1px solid #e5e7eb',
                        borderRadius: 12,
                        background: '#fff',
                        padding: '14px 16px',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        gap: 14,
                        transition: 'all 0.2s ease',
                        boxShadow: '0 1px 3px rgba(0, 0, 0, 0.04)',
                      }}
                      onMouseEnter={(e) => {
                        e.currentTarget.style.boxShadow = '0 4px 12px rgba(0, 0, 0, 0.08)'
                        e.currentTarget.style.borderColor = '#3b82f6'
                      }}
                      onMouseLeave={(e) => {
                        e.currentTarget.style.boxShadow = '0 1px 3px rgba(0, 0, 0, 0.04)'
                        e.currentTarget.style.borderColor = '#e5e7eb'
                      }}
                    >
                      {/* 左侧：大类信息 */}
                      <div style={{ flex: 1, minWidth: 0 }}>
                        <div style={{ display: 'grid', gap: 8, marginBottom: 6 }}>
                          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                            <span
                              style={{
                                padding: '2px 8px',
                                minWidth: 40,
                                borderRadius: 999,
                                background: '#f0f7ff',
                                color: '#3b82f6',
                                fontSize: 11,
                                fontWeight: 600,
                                textAlign: 'center',
                                lineHeight: 1.4,
                                flexShrink: 0,
                              }}
                            >
                              #{displayIndex + 1}
                            </span>
                            <input
                              value={group.groupName}
                              onChange={(e) =>
                                updateGroup(groupIndex, (current) => ({
                                  ...current,
                                  groupName: e.target.value,
                                }))
                              }
                              placeholder={`请输入大类 ${groupIndex + 1} 名称`}
                              style={{
                                padding: '9px 10px',
                                borderRadius: 8,
                                border: '1px solid #d1d5db',
                                fontSize: 14,
                                fontWeight: 600,
                                color: '#0f172a',
                                width: '100%',
                                boxSizing: 'border-box',
                              }}
                            />
                          </div>
                        </div>
                      </div>

                      {/* 中间：统计信息 */}
                      <div style={{ display: 'flex', gap: 20, alignItems: 'center' }}>
                        <div style={{ textAlign: 'center' }}>
                          <div style={{ fontSize: 12, color: '#94a3b8', marginBottom: 4 }}>
                            排序
                          </div>
                          <div style={{ fontSize: 14, fontWeight: 600, color: '#0f172a' }}>
                            {group.sortOrder}
                          </div>
                        </div>
                        <div style={{ width: 1, height: 24, background: '#e5e7eb' }}></div>
                        <div style={{ textAlign: 'center' }}>
                          <div style={{ fontSize: 12, color: '#94a3b8', marginBottom: 4 }}>
                            小类
                          </div>
                          <div style={{ fontSize: 14, fontWeight: 600, color: '#0f172a' }}>
                            {itemCount}
                          </div>
                        </div>
                        <div style={{ width: 1, height: 24, background: '#e5e7eb' }}></div>
                        <div style={{ textAlign: 'center' }}>
                          <div style={{ fontSize: 12, color: '#94a3b8', marginBottom: 4 }}>
                            最高计分
                          </div>
                          <div style={{ fontSize: 14, fontWeight: 600, color: '#0f172a' }}>
                            {maxSingleScore}
                          </div>
                        </div>
                        <div style={{ width: 1, height: 24, background: '#e5e7eb' }}></div>
                        <div style={{ textAlign: 'center' }}>
                          <div style={{ fontSize: 12, color: '#94a3b8', marginBottom: 4 }}>
                            大类封顶
                          </div>
                          <div style={{ fontSize: 14, fontWeight: 600, color: '#0f172a' }}>
                            {group.maxScore ?? '不限'}
                          </div>
                        </div>
                        <div style={{ width: 1, height: 24, background: '#e5e7eb' }}></div>
                        <div style={{ textAlign: 'center' }}>
                          <div style={{ fontSize: 12, color: '#94a3b8', marginBottom: 4 }}>
                            同赛事
                          </div>
                          <div
                            style={{
                              fontSize: 14,
                              fontWeight: 600,
                              color: group.sameEventBestOnly ? '#2563eb' : '#0f172a',
                            }}
                          >
                            {group.sameEventBestOnly ? '取最高' : '累加'}
                          </div>
                        </div>
                      </div>

                      {/* 右侧：操作按钮 */}
                      <div
                        style={{ display: 'flex', gap: 10, alignItems: 'center', flexShrink: 0 }}
                      >
                        <button
                          type="button"
                          onClick={() => openGroupView(groupIndex)}
                          style={{
                            padding: '8px 14px',
                            borderRadius: 8,
                            border: '1px solid #3b82f6',
                            background: '#fff',
                            color: '#3b82f6',
                            fontSize: 14,
                            fontWeight: 600,
                            cursor: 'pointer',
                            transition: 'all 0.2s ease',
                            whiteSpace: 'nowrap',
                          }}
                          onMouseEnter={(e) => {
                            e.target.style.background = '#3b82f6'
                            e.target.style.color = '#fff'
                          }}
                          onMouseLeave={(e) => {
                            e.target.style.background = '#fff'
                            e.target.style.color = '#3b82f6'
                          }}
                        >
                          编辑大类
                        </button>
                        <button
                          type="button"
                          className="btn-link danger"
                          onClick={() => removeGroup(groupIndex)}
                          disabled={formState.groups.length === 1}
                          style={{
                            padding: '8px 14px',
                            borderRadius: 8,
                            border: '1px solid #fecaca',
                            background: formState.groups.length === 1 ? '#f9fafb' : '#fff',
                            color: formState.groups.length === 1 ? '#d1d5db' : '#ef4444',
                            fontSize: 14,
                            fontWeight: 600,
                            cursor: formState.groups.length === 1 ? 'not-allowed' : 'pointer',
                            transition: 'all 0.2s ease',
                            whiteSpace: 'nowrap',
                          }}
                          onMouseEnter={(e) => {
                            if (formState.groups.length > 1) {
                              e.target.style.background = '#ef4444'
                              e.target.style.color = '#fff'
                              e.target.style.borderColor = '#ef4444'
                            }
                          }}
                          onMouseLeave={(e) => {
                            if (formState.groups.length > 1) {
                              e.target.style.background = '#fff'
                              e.target.style.color = '#ef4444'
                              e.target.style.borderColor = '#fecaca'
                            }
                          }}
                        >
                          删除
                        </button>
                      </div>
                    </div>
                  )
                })}
              </div>
            </div>
          ) : null}

          {currentView === 'group' && currentGroup ? (
            <div>
              <div style={{ display: 'grid', gap: 12 }}>
                <div
                  className="template-editor-policy-bar"
                  style={{
                    border: '1px solid #e2e8f0',
                    borderRadius: 8,
                    padding: 14,
                    background: '#fff',
                  }}
                >
                  <div
                    style={{
                      display: 'grid',
                      gridTemplateColumns: '140px minmax(0, 1fr)',
                      gap: 16,
                      alignItems: 'center',
                    }}
                  >
                    <strong style={{ color: '#334155', fontSize: 14 }}>大类计分规则</strong>
                    <div
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: 10,
                        flexWrap: 'wrap',
                        justifyContent: 'flex-start',
                      }}
                    >
                      <label
                        style={{
                          display: 'flex',
                          alignItems: 'center',
                          gap: 6,
                          margin: 0,
                          cursor: 'pointer',
                          fontSize: 13,
                          color: '#475569',
                          whiteSpace: 'nowrap',
                        }}
                      >
                        <input
                          type="checkbox"
                          checked={Boolean(currentGroup.sameEventBestOnly)}
                          onChange={(e) =>
                            updateGroup(currentGroupIndex, (current) => ({
                              ...current,
                              sameEventBestOnly: e.target.checked,
                            }))
                          }
                          style={{
                            width: 14,
                            height: 14,
                            cursor: 'pointer',
                            accentColor: '#3b82f6',
                          }}
                        />
                        同赛事仅取最高分
                      </label>
                      <label
                        style={{
                          display: 'flex',
                          alignItems: 'center',
                          gap: 6,
                          margin: 0,
                          cursor: 'pointer',
                          fontSize: 13,
                          color: '#475569',
                          whiteSpace: 'nowrap',
                        }}
                      >
                        <input
                          type="checkbox"
                          checked={
                            currentGroup.maxScore !== null && currentGroup.maxScore !== undefined
                          }
                          onChange={(e) =>
                            updateGroup(currentGroupIndex, (current) => ({
                              ...current,
                              maxScore: e.target.checked ? current.maxScore || 1 : null,
                            }))
                          }
                          style={{
                            width: 14,
                            height: 14,
                            cursor: 'pointer',
                            accentColor: '#3b82f6',
                          }}
                        />
                        启用大类封顶
                      </label>
                      <div style={{ display: 'inline-flex', alignItems: 'center', flexShrink: 0 }}>
                        <NumberStepper
                          value={currentGroup.maxScore ?? 1}
                          onChange={(val) =>
                            updateGroup(currentGroupIndex, (current) => ({
                              ...current,
                              maxScore: val <= 0 ? null : val,
                            }))
                          }
                          min={0}
                          width={130}
                          readOnly={
                            currentGroup.maxScore === null || currentGroup.maxScore === undefined
                          }
                        />
                      </div>
                      <span style={{ fontSize: 12, color: '#64748b', whiteSpace: 'nowrap' }}>
                        分
                      </span>
                    </div>
                  </div>
                  <div style={{ marginTop: 8, fontSize: 12, color: '#64748b', lineHeight: 1.6 }}>
                    同赛事仅取最高分：同一学生同一学年内，当前大类下证书名称相同的多个小类只计入最高分。大类封顶：该大类下所有有效
                    {forcedType === 'PHYSICAL' ? '分值' : '加分/扣分'}不能超过设定值。
                  </div>
                </div>

                <div style={{ display: 'grid', gap: 10 }}>
                  {!sortedCurrentItems.length ? (
                    <div
                      style={{
                        padding: 18,
                        borderRadius: 12,
                        border: '1px dashed #cbd5e1',
                        background: '#f8fafc',
                        color: '#64748b',
                        textAlign: 'center',
                      }}
                    >
                      暂无小类。空大类保存后不会展示给学生。
                    </div>
                  ) : null}
                  {sortedCurrentItems.map(({ item, index: itemIndex }, displayIndex) => {
                    const highestScore = Math.max(
                      ...(item.levelRules || []).map((rule) =>
                        computeLevelRuleMaxScore(item, rule),
                      ),
                      0,
                    )
                    return (
                      <div
                        key={`item-${itemIndex}`}
                        className="template-editor-item-row"
                        style={{
                          border: '1px solid #e2e8f0',
                          borderRadius: 12,
                          background: '#fff',
                          padding: 14,
                          gap: 10,
                          alignItems: 'center',
                        }}
                      >
                        <div>
                          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                            <span
                              style={{
                                padding: '2px 8px',
                                minWidth: 40,
                                borderRadius: 999,
                                background: '#f0f7ff',
                                color: '#3b82f6',
                                fontSize: 11,
                                fontWeight: 600,
                                textAlign: 'center',
                                lineHeight: 1.4,
                                flexShrink: 0,
                              }}
                            >
                              #{displayIndex + 1}
                            </span>
                            <input
                              value={item.itemName}
                              onChange={(e) =>
                                updateItem(currentGroupIndex, itemIndex, (current) => ({
                                  ...current,
                                  itemName: e.target.value,
                                }))
                              }
                              placeholder={`未命名小类 ${displayIndex + 1}`}
                              style={{
                                padding: '7px 10px',
                                borderRadius: 8,
                                border: '1px solid #d1d5db',
                                fontSize: 14,
                                fontWeight: 600,
                                color: '#0f172a',
                                width: 260,
                                maxWidth: '100%',
                              }}
                            />
                            <span
                              style={{
                                padding: '3px 10px',
                                borderRadius: 999,
                                background: item.status === 'active' ? '#dcfce7' : '#e5e7eb',
                                color: item.status === 'active' ? '#166534' : '#475569',
                                fontSize: 12,
                                fontWeight: 600,
                              }}
                            >
                              {item.status === 'active' ? '启用' : '停用'}
                            </span>
                          </div>
                          <div style={{ marginTop: 6, fontSize: 13, color: '#64748b' }}>
                            {item.description || '未填写说明'}
                          </div>
                        </div>
                        <div className="template-editor-item-row-stat" style={{ fontSize: 13, color: '#475569' }}>
                          级别 {item.levelRules?.length || 0}
                        </div>
                        <div className="template-editor-item-row-stat" style={{ fontSize: 13, color: '#475569' }}>
                          作者{' '}
                          {(item.levelRules || []).reduce(
                            (total, rule) => total + (rule.authorRules || []).length,
                            0,
                          )}
                        </div>
                        <div className="template-editor-item-row-stat" style={{ fontSize: 13, color: '#475569' }}>
                          最高计分 {highestScore}
                        </div>
                        <div className="template-editor-item-row-stat" style={{ fontSize: 13, color: '#475569' }}>
                          {item.sameEventMaxCount
                            ? `赛事限次 ${item.sameEventMaxCount}`
                            : '不限赛事次数'}
                        </div>
                        <div className="template-editor-item-row-actions">
                          <button
                            type="button"
                            onClick={() => openItemView(currentGroupIndex, itemIndex)}
                            style={{
                              padding: '8px 14px',
                              borderRadius: 8,
                              border: '1px solid #3b82f6',
                              background: '#fff',
                              color: '#2563eb',
                              fontSize: 13,
                              fontWeight: 600,
                              cursor: 'pointer',
                              whiteSpace: 'nowrap',
                            }}
                          >
                            编辑小类
                          </button>
                          <button
                            type="button"
                            className="btn-link danger"
                            onClick={() => removeItem(currentGroupIndex, itemIndex)}
                            disabled={(currentGroup.items || []).length === 1}
                            style={{
                              padding: '8px 14px',
                              borderRadius: 8,
                              border: '1px solid #fecaca',
                              background:
                                (currentGroup.items || []).length === 1 ? '#f3f4f6' : '#fef2f2',
                              color: (currentGroup.items || []).length === 1 ? '#9ca3af' : '#dc2626',
                              fontSize: 13,
                              cursor:
                                (currentGroup.items || []).length === 1 ? 'not-allowed' : 'pointer',
                              whiteSpace: 'nowrap',
                            }}
                          >
                            删除
                          </button>
                        </div>
                      </div>
                    )
                  })}
                </div>
              </div>
            </div>
          ) : null}

          {currentView === 'item' && currentGroup && currentItem ? (
            <>
              <div style={{ display: 'grid', gap: 10 }}>
                <div
                  className="template-editor-item-overview"
                  style={{
                    border: '1px solid #e2e8f0',
                    borderRadius: 8,
                    padding: 14,
                    background: '#fff',
                  }}
                >
                  <div
                    style={{
                      display: 'grid',
                      gridTemplateColumns: 'minmax(0, 1fr) 150px max-content',
                      gap: 10,
                      alignItems: 'end',
                    }}
                  >
                    <div
                      className="form-group"
                      style={{ display: 'flex', flexDirection: 'column', gap: 6 }}
                    >
                      <label style={{ fontSize: 14, fontWeight: 500, color: '#374151' }}>
                        小类名称（留空不展示）
                      </label>
                      <input
                        value={currentItem.itemName}
                        onChange={(e) =>
                          updateItem(currentGroupIndex, currentItemIndex, (current) => ({
                            ...current,
                            itemName: e.target.value,
                          }))
                        }
                        placeholder="例如：蓝桥杯、挑战杯"
                        style={{
                          padding: '8px 10px',
                          borderRadius: 8,
                          border: '1px solid #d1d5db',
                          fontSize: 14,
                          height: 36,
                          boxSizing: 'border-box',
                          width: '100%',
                        }}
                      />
                    </div>
                    <div
                      className="form-group"
                      style={{ display: 'flex', flexDirection: 'column', gap: 6 }}
                    >
                      <label style={{ fontSize: 14, fontWeight: 500, color: '#374151' }}>
                        状态
                      </label>
                      <select
                        value={currentItem.status}
                        onChange={(e) =>
                          updateItem(currentGroupIndex, currentItemIndex, (current) => ({
                            ...current,
                            status: e.target.value,
                          }))
                        }
                        style={{
                          padding: '8px 10px',
                          borderRadius: 8,
                          border: '1px solid #d1d5db',
                          fontSize: 14,
                          height: 36,
                          boxSizing: 'border-box',
                          background: '#fff',
                          width: '100%',
                        }}
                      >
                        <option value="active">启用</option>
                        <option value="inactive">停用</option>
                      </select>
                    </div>
                    <div
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: 8,
                        height: 36,
                        whiteSpace: 'nowrap',
                        justifySelf: 'end',
                      }}
                    >
                      <div
                        style={{
                          display: 'inline-flex',
                          alignItems: 'center',
                          borderRadius: 10,
                          overflow: 'hidden',
                          border: `1px solid ${(currentItem.scoreDirection || 'add') === 'deduct' ? '#fca5a5' : '#86efac'}`,
                          boxShadow:
                            (currentItem.scoreDirection || 'add') === 'deduct'
                              ? '0 0 0 2px rgba(239, 68, 68, 0.08)'
                              : '0 0 0 2px rgba(34, 197, 94, 0.08)',
                        }}
                      >
                        <button
                          type="button"
                          onClick={() =>
                            updateItem(currentGroupIndex, currentItemIndex, (current) => ({
                              ...current,
                              scoreDirection: 'add',
                            }))
                          }
                          style={{
                            height: 36,
                            padding: '0 14px',
                            border: 'none',
                            background:
                              (currentItem.scoreDirection || 'add') === 'add' ? '#22c55e' : '#fff',
                            color:
                              (currentItem.scoreDirection || 'add') === 'add' ? '#fff' : '#166534',
                            fontSize: 13,
                            fontWeight: 700,
                            cursor: 'pointer',
                          }}
                        >
                          加分项
                        </button>
                        <button
                          type="button"
                          onClick={() =>
                            updateItem(currentGroupIndex, currentItemIndex, (current) => ({
                              ...current,
                              scoreDirection: 'deduct',
                            }))
                          }
                          style={{
                            height: 36,
                            padding: '0 14px',
                            border: 'none',
                            borderLeft: '1px solid #e5e7eb',
                            background:
                              (currentItem.scoreDirection || 'add') === 'deduct'
                                ? '#ef4444'
                                : '#fff',
                            color:
                              (currentItem.scoreDirection || 'add') === 'deduct'
                                ? '#fff'
                                : '#991b1b',
                            fontSize: 13,
                            fontWeight: 700,
                            cursor: 'pointer',
                          }}
                        >
                          减分项
                        </button>
                      </div>
                      <label
                        style={{
                          display: 'flex',
                          alignItems: 'center',
                          gap: 6,
                          margin: 0,
                          cursor: 'pointer',
                          fontSize: 13,
                          color: '#475569',
                          whiteSpace: 'nowrap',
                        }}
                      >
                        <input
                          type="checkbox"
                          checked={
                            currentItem.sameEventMaxCount !== null &&
                            currentItem.sameEventMaxCount !== undefined
                          }
                          onChange={(e) =>
                            updateItem(currentGroupIndex, currentItemIndex, (current) => ({
                              ...current,
                              sameEventMaxCount: e.target.checked
                                ? current.sameEventMaxCount || 1
                                : null,
                            }))
                          }
                          style={{
                            width: 14,
                            height: 14,
                            cursor: 'pointer',
                            accentColor: '#3b82f6',
                          }}
                        />
                        赛事限制
                      </label>
                      <div style={{ display: 'inline-flex', alignItems: 'center', flexShrink: 0 }}>
                        <NumberStepper
                          value={currentItem.sameEventMaxCount ?? 1}
                          onChange={(val) =>
                            updateItem(currentGroupIndex, currentItemIndex, (current) => ({
                              ...current,
                              sameEventMaxCount: val <= 0 ? null : val,
                            }))
                          }
                          min={0}
                          width={130}
                          readOnly={
                            currentItem.sameEventMaxCount === null ||
                            currentItem.sameEventMaxCount === undefined
                          }
                        />
                      </div>
                      <span
                        style={{
                          fontSize: 12,
                          color: '#64748b',
                          whiteSpace: 'nowrap',
                          marginLeft: 2,
                        }}
                      >
                        次
                      </span>
                      <div
                        ref={sameEventHelpRef}
                        style={{
                          position: 'relative',
                          display: 'inline-flex',
                          alignItems: 'center',
                        }}
                      >
                        <button
                          type="button"
                          onClick={() => setShowSameEventHelp((prev) => !prev)}
                          style={{
                            display: 'inline-flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            width: 16,
                            height: 16,
                            borderRadius: '50%',
                            border: '1px solid #94a3b8',
                            color: '#64748b',
                            fontSize: 11,
                            fontWeight: 700,
                            lineHeight: 1,
                            userSelect: 'none',
                            background: '#fff',
                            cursor: 'pointer',
                            padding: 0,
                          }}
                        >
                          ?
                        </button>
                        {showSameEventHelp ? (
                          <div
                            style={{
                              position: 'absolute',
                              top: 24,
                              right: 0,
                              width: 280,
                              padding: '8px 10px',
                              borderRadius: 8,
                              border: '1px solid #dbeafe',
                              background: '#fff',
                              color: '#334155',
                              fontSize: 12,
                              lineHeight: 1.5,
                              whiteSpace: 'normal',
                              wordBreak: 'break-word',
                              boxShadow: '0 8px 20px rgba(15, 23, 42, 0.12)',
                              zIndex: 30,
                            }}
                          >
                            启用后：同一赛事在当前小类最多只计入设定次数。未启用：不限制次数。
                          </div>
                        ) : null}
                      </div>
                    </div>
                  </div>
                  <div style={{ marginTop: 10 }}>
                    <div
                      className="form-group full-width"
                      style={{ display: 'flex', flexDirection: 'column', gap: 6 }}
                    >
                      <label style={{ fontSize: 14, fontWeight: 500, color: '#374151' }}>
                        小类说明
                      </label>
                      <textarea
                        rows={3}
                        value={currentItem.description}
                        onChange={(e) =>
                          updateItem(currentGroupIndex, currentItemIndex, (current) => ({
                            ...current,
                            description: e.target.value,
                          }))
                        }
                        placeholder="补充这个小类适用的赛事范围、说明或备注"
                        style={{
                          padding: '8px 10px',
                          borderRadius: 8,
                          border: '1px solid #d1d5db',
                          fontSize: 14,
                          resize: 'vertical',
                          minHeight: 56,
                          boxSizing: 'border-box',
                          width: '100%',
                        }}
                      />
                    </div>
                  </div>
                </div>

                <div
                  className="template-editor-rule-grid"
                  style={{
                    display: 'grid',
                    gap: 12,
                  }}
                >
                  <div
                    className="template-editor-rule-panel"
                    style={{
                      border: '1px solid #e2e8f0',
                      borderRadius: 8,
                      padding: 12,
                      background: '#fff',
                    }}
                  >
                    <div
                      style={{
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'center',
                        marginBottom: 8,
                      }}
                    >
                      <div>
                        <strong style={{ color: '#1e293b', fontSize: 14 }}>级别规则</strong>
                        <div style={{ marginTop: 6, fontSize: 13, color: '#64748b' }}>
                          {currentItem.authorScoreMode === 'fixed'
                            ? '填写赛事等级名称，分值由右侧作者计分规则决定'
                            : '填写赛事等级名称和比例计算基础分'}
                        </div>
                      </div>
                      <div
                        style={{
                          display: 'flex',
                          gap: 6,
                          flexWrap: 'wrap',
                          justifyContent: 'flex-end',
                        }}
                      >
                        <button
                          type="button"
                          onClick={() => {
                            const currentRules =
                              formState.groups[currentGroupIndex]?.items?.[currentItemIndex]
                                ?.levelRules || []
                            setLevelRuleUndoStack((prev) => [
                              ...prev,
                              currentRules.map((rule) => ({ ...rule })),
                            ])
                            setLevelRuleRedoStack([])
                            updateItem(currentGroupIndex, currentItemIndex, (item) => ({
                              ...item,
                              levelRules: createPresetLevelRules(),
                            }))
                            setSelectedLevelIndex(null)
                          }}
                          style={{
                            padding: '5px 9px',
                            borderRadius: 6,
                            border: '1px solid #cbd5e1',
                            background: '#fff',
                            color: '#475569',
                            fontSize: 12,
                            fontWeight: 600,
                            cursor: 'pointer',
                          }}
                        >
                          初始化
                        </button>
                        <button
                          type="button"
                          onClick={() => {
                            const currentRules =
                              formState.groups[currentGroupIndex]?.items?.[currentItemIndex]
                                ?.levelRules || []
                            setLevelRuleUndoStack((prev) => [
                              ...prev,
                              currentRules.map((rule) => ({ ...rule })),
                            ])
                            setLevelRuleRedoStack([])
                            updateItem(currentGroupIndex, currentItemIndex, (item) => ({
                              ...item,
                              levelRules: [{ ...createLevelRule(), sortOrder: 0 }],
                            }))
                            setSelectedLevelIndex(null)
                          }}
                          style={{
                            padding: '5px 9px',
                            borderRadius: 6,
                            border: '1px solid #fecaca',
                            background: '#fff',
                            color: '#dc2626',
                            fontSize: 12,
                            fontWeight: 600,
                            cursor: 'pointer',
                          }}
                        >
                          一键删除
                        </button>
                        <button
                          type="button"
                          onClick={undoLevelRuleDeletion}
                          disabled={!levelRuleUndoStack.length}
                          style={{
                            padding: '5px 9px',
                            borderRadius: 6,
                            border: '1px solid #d1d5db',
                            background: '#fff',
                            color: '#475569',
                            fontSize: 12,
                            fontWeight: 600,
                            cursor: !levelRuleUndoStack.length ? 'not-allowed' : 'pointer',
                            opacity: !levelRuleUndoStack.length ? 0.6 : 1,
                          }}
                        >
                          上一步
                        </button>
                        <button
                          type="button"
                          onClick={redoLevelRuleDeletion}
                          disabled={!levelRuleRedoStack.length}
                          style={{
                            padding: '5px 9px',
                            borderRadius: 6,
                            border: '1px solid #3b82f6',
                            background: '#fff',
                            color: '#2563eb',
                            fontSize: 12,
                            fontWeight: 600,
                            cursor: !levelRuleRedoStack.length ? 'not-allowed' : 'pointer',
                            opacity: !levelRuleRedoStack.length ? 0.6 : 1,
                          }}
                        >
                          下一步
                        </button>
                        <button
                          type="button"
                          className="btn btn-default"
                          onClick={() => addLevelRule(currentGroupIndex, currentItemIndex)}
                          style={{
                            padding: '5px 9px',
                            borderRadius: 6,
                            border: '1px solid #3b82f6',
                            background: '#fff',
                            color: '#3b82f6',
                            fontSize: 12,
                            fontWeight: 600,
                            cursor: 'pointer',
                          }}
                        >
                          + 新增分类
                        </button>
                      </div>
                    </div>
                    <div style={{ display: 'grid', gap: 6 }}>
                      {currentItem.levelRules.map((rule, ruleIndex) => (
                        <div
                          key={`level-${ruleIndex}`}
                          onClick={() => setSelectedLevelIndex(ruleIndex)}
                          style={{
                            display: 'grid',
                            gridTemplateColumns:
                              currentItem.authorScoreMode === 'fixed'
                                ? '1fr auto auto'
                                : '1fr 130px auto auto',
                            gap: 8,
                            alignItems: 'end',
                            padding: 8,
                            borderRadius: 10,
                            background: selectedLevelIndex === ruleIndex ? '#eff6ff' : '#fff',
                            border:
                              selectedLevelIndex === ruleIndex
                                ? '2px solid #3b82f6'
                                : '1px solid #e2e8f0',
                            cursor: 'pointer',
                          }}
                        >
                          <div
                            className="form-group"
                            style={{ margin: 0, display: 'flex', flexDirection: 'column', gap: 6 }}
                          >
                            <label style={{ fontSize: 13, fontWeight: 500, color: '#4b5563' }}>
                              级别名称
                            </label>
                            <input
                              value={rule.levelName}
                              onChange={(e) =>
                                updateItem(currentGroupIndex, currentItemIndex, (current) => ({
                                  ...current,
                                  levelRules: current.levelRules.map((currentRule, index) =>
                                    index === ruleIndex
                                      ? { ...currentRule, levelName: e.target.value }
                                      : currentRule,
                                  ),
                                }))
                              }
                              placeholder="例如：国家级一等奖"
                              style={{
                                padding: '10px 12px',
                                borderRadius: 8,
                                border: '1px solid #d1d5db',
                                fontSize: 14,
                              }}
                            />
                          </div>
                          {currentItem.authorScoreMode !== 'fixed' ? (
                            <div
                              className="form-group"
                              style={{
                                margin: 0,
                                display: 'flex',
                                flexDirection: 'column',
                                gap: 6,
                              }}
                            >
                              <label style={{ fontSize: 13, fontWeight: 500, color: '#4b5563' }}>
                                基础分
                              </label>
                              <NumberStepper
                                value={rule.score}
                                onChange={(val) =>
                                  updateItem(currentGroupIndex, currentItemIndex, (current) => ({
                                    ...current,
                                    levelRules: current.levelRules.map((currentRule, index) =>
                                      index === ruleIndex
                                        ? { ...currentRule, score: val }
                                        : currentRule,
                                    ),
                                  }))
                                }
                                min={0.1}
                                step={0.5}
                                width={130}
                              />
                            </div>
                          ) : null}
                          <div
                            style={{
                              display: 'flex',
                              flexDirection: 'column',
                              gap: 4,
                              height: 36 + 6 + 19,
                              justifyContent: 'flex-end',
                            }}
                          >
                            <button
                              type="button"
                              onClick={(e) => {
                                e.stopPropagation()
                                moveLevelRule(currentGroupIndex, currentItemIndex, ruleIndex, -1)
                              }}
                              disabled={ruleIndex === 0}
                              style={{
                                width: 32,
                                height: 15,
                                border: '1px solid #d1d5db',
                                borderRadius: 4,
                                background: ruleIndex === 0 ? '#f5f5f5' : '#fff',
                                color: ruleIndex === 0 ? '#d1d5db' : '#6b7280',
                                fontSize: 10,
                                cursor: ruleIndex === 0 ? 'not-allowed' : 'pointer',
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'center',
                                padding: 0,
                              }}
                            >
                              ▲
                            </button>
                            <button
                              type="button"
                              onClick={(e) => {
                                e.stopPropagation()
                                moveLevelRule(currentGroupIndex, currentItemIndex, ruleIndex, 1)
                              }}
                              disabled={ruleIndex === currentItem.levelRules.length - 1}
                              style={{
                                width: 32,
                                height: 15,
                                border: '1px solid #d1d5db',
                                borderRadius: 4,
                                background:
                                  ruleIndex === currentItem.levelRules.length - 1
                                    ? '#f5f5f5'
                                    : '#fff',
                                color:
                                  ruleIndex === currentItem.levelRules.length - 1
                                    ? '#d1d5db'
                                    : '#6b7280',
                                fontSize: 10,
                                cursor:
                                  ruleIndex === currentItem.levelRules.length - 1
                                    ? 'not-allowed'
                                    : 'pointer',
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'center',
                                padding: 0,
                              }}
                            >
                              ▼
                            </button>
                          </div>
                          <button
                            type="button"
                            className="btn-link danger"
                            onClick={() =>
                              removeLevelRule(currentGroupIndex, currentItemIndex, ruleIndex)
                            }
                            disabled={currentItem.levelRules.length === 1}
                            style={{
                              padding: '10px 12px',
                              borderRadius: 8,
                              border: '1px solid #fecaca',
                              background:
                                currentItem.levelRules.length === 1 ? '#f3f4f6' : '#fef2f2',
                              color: currentItem.levelRules.length === 1 ? '#9ca3af' : '#dc2626',
                              fontSize: 13,
                              cursor:
                                currentItem.levelRules.length === 1 ? 'not-allowed' : 'pointer',
                              height: 36,
                            }}
                          >
                            删除
                          </button>
                        </div>
                      ))}
                    </div>
                  </div>

                  <div
                    className="template-editor-rule-panel"
                    style={{
                      border: '1px solid #e2e8f0',
                      borderRadius: 8,
                      padding: 12,
                      background: '#fff',
                    }}
                  >
                    {selectedLevelIndex === null ? (
                      <div
                        style={{
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          height: 120,
                          color: '#94a3b8',
                          fontSize: 13,
                        }}
                      >
                        ← 点击左侧级别，配置该级别的作者计分规则
                      </div>
                    ) : (
                      (() => {
                        const selectedLevel = currentItem.levelRules[selectedLevelIndex]
                        if (!selectedLevel) return null
                        const currentAuthorRules = selectedLevel.authorRules || []
                        return (
                          <>
                            <div
                              style={{
                                display: 'flex',
                                justifyContent: 'space-between',
                                alignItems: 'center',
                                marginBottom: 8,
                              }}
                            >
                              <div>
                                <strong style={{ color: '#1e293b', fontSize: 14 }}>
                                  作者计分规则
                                </strong>
                                <div
                                  style={{
                                    marginTop: 4,
                                    fontSize: 12,
                                    color: '#3b82f6',
                                    fontWeight: 500,
                                  }}
                                >
                                  当前级别：{selectedLevel.levelName || '未命名'}
                                </div>
                                <div style={{ marginTop: 4, fontSize: 12, color: '#64748b' }}>
                                  {currentItem.authorScoreMode === 'fixed'
                                    ? '当前模式下，分值直接输入固定分值'
                                    : '比例按左侧基础分折算'}
                                </div>
                              </div>
                              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                                <select
                                  value={currentItem.authorScoreMode || 'ratio'}
                                  onChange={(e) =>
                                    updateItem(currentGroupIndex, currentItemIndex, (current) => ({
                                      ...current,
                                      authorScoreMode: e.target.value,
                                    }))
                                  }
                                  style={{
                                    padding: '8px 10px',
                                    borderRadius: 8,
                                    border: '1px solid #d1d5db',
                                    fontSize: 13,
                                    height: 36,
                                    background: '#fff',
                                  }}
                                >
                                  <option value="ratio">比例</option>
                                  <option value="fixed">分值</option>
                                </select>
                                <button
                                  type="button"
                                  className="btn btn-default"
                                  onClick={() =>
                                    addAuthorRule(
                                      currentGroupIndex,
                                      currentItemIndex,
                                      selectedLevelIndex,
                                    )
                                  }
                                  style={{
                                    padding: '7px 12px',
                                    borderRadius: 8,
                                    border: '1px solid #22c55e',
                                    background: '#fff',
                                    color: '#22c55e',
                                    fontSize: 12,
                                    fontWeight: 500,
                                    cursor: 'pointer',
                                  }}
                                >
                                  + 新增作者规则
                                </button>
                              </div>
                            </div>
                            <div style={{ display: 'grid', gap: 6 }}>
                              {currentAuthorRules.map((rule, ruleIndex) => {
                                const calculatedScore =
                                  currentItem.authorScoreMode === 'fixed'
                                    ? rule.fixedScore !== '' &&
                                      rule.fixedScore !== null &&
                                      rule.fixedScore !== undefined
                                      ? safeNumber(rule.fixedScore, 0).toFixed(2)
                                      : null
                                    : selectedLevel.score && rule.ratio
                                      ? (
                                          (safeNumber(selectedLevel.score, 0) *
                                            safeNumber(rule.ratio, 0)) /
                                          100
                                        ).toFixed(2)
                                      : null
                                const isDeduct = (currentItem.scoreDirection || 'add') === 'deduct'
                                const signedScoreText =
                                  calculatedScore === null
                                    ? '-'
                                    : `${isDeduct ? '-' : '+'}${calculatedScore}`
                                return (
                                  <div
                                    key={`author-${ruleIndex}`}
                                    style={{
                                      display: 'grid',
                                      gridTemplateColumns: '160px 130px 174px 58px',
                                      gap: 8,
                                      alignItems: 'end',
                                      padding: 8,
                                      borderRadius: 10,
                                      background: '#fff',
                                      border: '1px solid #e2e8f0',
                                    }}
                                  >
                                    <div
                                      className="form-group"
                                      style={{
                                        margin: 0,
                                        display: 'flex',
                                        flexDirection: 'column',
                                        gap: 6,
                                      }}
                                    >
                                      <label
                                        style={{ fontSize: 13, fontWeight: 500, color: '#4b5563' }}
                                      >
                                        作者名称
                                      </label>
                                      <input
                                        value={rule.authorLabel}
                                        onChange={(e) =>
                                          updateItem(
                                            currentGroupIndex,
                                            currentItemIndex,
                                            (current) => ({
                                              ...current,
                                              levelRules: current.levelRules.map((lr, lrIdx) =>
                                                lrIdx !== selectedLevelIndex
                                                  ? lr
                                                  : {
                                                      ...lr,
                                                      authorRules: lr.authorRules.map(
                                                        (ar, arIdx) =>
                                                          arIdx === ruleIndex
                                                            ? { ...ar, authorLabel: e.target.value }
                                                            : ar,
                                                      ),
                                                    },
                                              ),
                                            }),
                                          )
                                        }
                                        placeholder="例如：第一作者"
                                        style={{
                                          padding: '10px 12px',
                                          borderRadius: 8,
                                          border: '1px solid #d1d5db',
                                          fontSize: 14,
                                        }}
                                      />
                                    </div>
                                    <div
                                      className="form-group"
                                      style={{
                                        margin: 0,
                                        display: 'flex',
                                        flexDirection: 'column',
                                        gap: 6,
                                      }}
                                    >
                                      <label
                                        style={{ fontSize: 13, fontWeight: 500, color: '#4b5563' }}
                                      >
                                        比例(%)
                                      </label>
                                      {currentItem.authorScoreMode === 'ratio' ? (
                                        <NumberStepper
                                          value={rule.ratio}
                                          onChange={(val) =>
                                            updateItem(
                                              currentGroupIndex,
                                              currentItemIndex,
                                              (current) => ({
                                                ...current,
                                                levelRules: current.levelRules.map((lr, lrIdx) =>
                                                  lrIdx !== selectedLevelIndex
                                                    ? lr
                                                    : {
                                                        ...lr,
                                                        authorRules: lr.authorRules.map(
                                                          (ar, arIdx) =>
                                                            arIdx === ruleIndex
                                                              ? { ...ar, ratio: val }
                                                              : ar,
                                                        ),
                                                      },
                                                ),
                                              }),
                                            )
                                          }
                                          min={1}
                                          max={100}
                                          step={5}
                                          width={130}
                                        />
                                      ) : (
                                        <div
                                          style={{
                                            width: 130,
                                            height: 36,
                                            borderRadius: 6,
                                            background: '#f5f5f5',
                                            border: '1px solid #d9d9d9',
                                            display: 'flex',
                                            alignItems: 'center',
                                            justifyContent: 'center',
                                            color: '#bfbfbf',
                                            fontSize: 14,
                                            boxSizing: 'border-box',
                                          }}
                                        >
                                          —
                                        </div>
                                      )}
                                    </div>
                                    <div
                                      className="form-group"
                                      style={{
                                        margin: 0,
                                        display: 'flex',
                                        flexDirection: 'column',
                                        gap: 6,
                                      }}
                                    >
                                      <label
                                        style={{ fontSize: 13, fontWeight: 500, color: '#4b5563' }}
                                      >
                                        分值
                                      </label>
                                      {currentItem.authorScoreMode === 'fixed' ? (
                                        <NumberStepper
                                          value={rule.fixedScore}
                                          onChange={(val) =>
                                            updateItem(
                                              currentGroupIndex,
                                              currentItemIndex,
                                              (current) => ({
                                                ...current,
                                                levelRules: current.levelRules.map((lr, lrIdx) =>
                                                  lrIdx !== selectedLevelIndex
                                                    ? lr
                                                    : {
                                                        ...lr,
                                                        authorRules: lr.authorRules.map(
                                                          (ar, arIdx) =>
                                                            arIdx === ruleIndex
                                                              ? { ...ar, fixedScore: val }
                                                              : ar,
                                                        ),
                                                      },
                                                ),
                                              }),
                                            )
                                          }
                                          min={0}
                                          max={100}
                                          step={0.5}
                                          width={130}
                                        />
                                      ) : (
                                        <div
                                          style={{
                                            padding: '0 8px',
                                            borderRadius: 8,
                                            background: isDeduct ? '#fee2e2' : '#d1fae5',
                                            border: `1px solid ${isDeduct ? '#fca5a5' : '#6ee7b7'}`,
                                            fontSize: 13,
                                            fontWeight: 600,
                                            color: isDeduct ? '#dc2626' : '#059669',
                                            textAlign: 'center',
                                            height: 36,
                                            boxSizing: 'border-box',
                                            display: 'flex',
                                            alignItems: 'center',
                                            justifyContent: 'center',
                                            minWidth: 96,
                                          }}
                                        >
                                          {signedScoreText}
                                        </div>
                                      )}
                                    </div>
                                    <button
                                      type="button"
                                      className="btn-link danger"
                                      onClick={() =>
                                        removeAuthorRule(
                                          currentGroupIndex,
                                          currentItemIndex,
                                          selectedLevelIndex,
                                          ruleIndex,
                                        )
                                      }
                                      disabled={currentAuthorRules.length === 1}
                                      style={{
                                        padding: '10px 12px',
                                        borderRadius: 8,
                                        border: '1px solid #fecaca',
                                        background:
                                          currentAuthorRules.length === 1 ? '#f3f4f6' : '#fef2f2',
                                        color:
                                          currentAuthorRules.length === 1 ? '#9ca3af' : '#dc2626',
                                        fontSize: 13,
                                        cursor:
                                          currentAuthorRules.length === 1
                                            ? 'not-allowed'
                                            : 'pointer',
                                        height: 36,
                                      }}
                                    >
                                      删除
                                    </button>
                                  </div>
                                )
                              })}
                            </div>
                          </>
                        )
                      })()
                    )}
                  </div>
                </div>
              </div>
            </>
          ) : null}
        </div>
          </main>
        </div>
      </div>
    </div>
  )
}

export default ExtraScoreTemplateEditor
