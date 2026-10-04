import { useCallback, useEffect, useMemo, useState } from 'react'
import { message, Modal, Segmented } from 'antd'
import { useLocation } from 'react-router-dom'
import PageHeader from '../../components/PageHeader'
import { confirmAction } from '../../components/confirmDialogService'
import { getActiveAcademicYearList } from '../../api/admin/academicYear'
import {
  getCompositePhysicalFullScoreRules,
  getCompositeScoreWeights,
  updateCompositePhysicalFullScoreRules,
  updateCompositeScoreWeights,
} from '../../api/admin/compositeScore'
import {
  getCompositeSubjectConfigs,
  getCompositeSubjectConfigStatus,
  updateCompositeSubjectConfigs,
} from '../../api/admin/compositeSubjectConfig'
import { getMajorList } from '../../api/admin/major'
import { getClassOptions } from '../../api/admin/class'
import { readCompositeSubjectConfigIssue } from '../../utils/compositeSubjectConfigIssue'
import {
  buildClassConfigSummaryText,
  getClassConfigStatusMeta,
  mergeClassOptionsWithConfigStatus,
} from '../../utils/compositeSubjectConfigStatus'
import './CompositeScoreConfig.css'

const DEFAULT_WEIGHTS = {
  moralBaseWeight: 0,
  moralExtraWeight: 0,
  intellectualBaseWeight: 70,
  intellectualExtraWeight: 0,
  physicalBaseWeight: 0,
  physicalExtraWeight: 0,
}

const WEIGHT_ROWS = [
  {
    key: 'moral',
    label: '德育',
    description: '二课成绩换算后的德育分',
    baseField: 'moralBaseWeight',
    extraField: 'moralExtraWeight',
  },
  {
    key: 'intellectual',
    label: '智育',
    description: '智育科目成绩平均分',
    baseField: 'intellectualBaseWeight',
    extraField: 'intellectualExtraWeight',
  },
  {
    key: 'physical',
    label: '体育',
    description: '体育科目成绩平均分',
    baseField: 'physicalBaseWeight',
    extraField: 'physicalExtraWeight',
  },
]

const WEIGHT_FIELDS = WEIGHT_ROWS.flatMap((item) => [item.baseField, item.extraField])

const SUBJECT_GROUPS = [
  { title: '智育基础分', category: 'INTELLECTUAL', tone: 'blue' },
  { title: '体育基础分', category: 'PHYSICAL', tone: 'green' },
  { title: '不计入综测', category: 'EXCLUDED', tone: 'gray' },
]

const CLASS_STATUS_FILTERS = [
  { value: 'all', label: '全部' },
  { value: 'configured', label: '已配置' },
  { value: 'partial', label: '部分配置' },
  { value: 'unconfigured', label: '未配置' },
  { value: 'no_subject', label: '无科目' },
]

const normalizeWeights = (source = {}) => ({
  moralBaseWeight: Number(source.moralBaseWeight ?? DEFAULT_WEIGHTS.moralBaseWeight),
  moralExtraWeight: Number(source.moralExtraWeight ?? DEFAULT_WEIGHTS.moralExtraWeight),
  intellectualBaseWeight: Number(
    source.intellectualBaseWeight ?? DEFAULT_WEIGHTS.intellectualBaseWeight,
  ),
  intellectualExtraWeight: Number(
    source.intellectualExtraWeight ?? DEFAULT_WEIGHTS.intellectualExtraWeight,
  ),
  physicalBaseWeight: Number(source.physicalBaseWeight ?? DEFAULT_WEIGHTS.physicalBaseWeight),
  physicalExtraWeight: Number(source.physicalExtraWeight ?? DEFAULT_WEIGHTS.physicalExtraWeight),
})

const normalizePhysicalRules = (items = []) =>
  items.map((item) => ({
    className: String(item.className || '').trim(),
    grade: normalizeGrade(item.grade),
    majorId: item.majorId ?? null,
    fullScoreEnabled: Boolean(item.fullScoreEnabled),
    configured: Boolean(item.configured),
    updatedAt: item.updatedAt || null,
  }))

const buildSubjectCategoryMap = (items) =>
  Object.fromEntries(items.map((item) => [String(item.subjectId), item.category]))

const normalizeGrade = (grade) =>
  String(grade || '')
    .replace(/级$/, '')
    .trim()

const findIssueMatchedClass = (classList, majorList, issue) => {
  const firstDetail = issue?.details?.find((item) => item.majorName && item.grade)
  if (!firstDetail) return null

  const targetMajor = majorList.find((item) => item.name === firstDetail.majorName)
  if (!targetMajor) return null

  const targetGrade = normalizeGrade(firstDetail.grade)
  return (
    classList.find(
      (item) =>
        item.status !== 'inactive' &&
        String(item.majorId) === String(targetMajor.id) &&
        [normalizeGrade(item.grade), normalizeGrade(item.grade).slice(-2)].includes(
          targetGrade.slice(-2),
        ),
    ) || null
  )
}

function CompositeScoreConfig() {
  const location = useLocation()
  const initialIssue =
    location.state?.compositeSubjectConfigIssue || readCompositeSubjectConfigIssue()
  const [activeWorkspace, setActiveWorkspace] = useState(initialIssue ? 'subjects' : 'rules')
  const [academicYears, setAcademicYears] = useState([])
  const [majors, setMajors] = useState([])
  const [classes, setClasses] = useState([])
  const [selectedYear, setSelectedYear] = useState('')
  const [selectedMajorId, setSelectedMajorId] = useState('')
  const [selectedGrade, setSelectedGrade] = useState('')
  const [selectedClassName, setSelectedClassName] = useState('')
  const [weights, setWeights] = useState(DEFAULT_WEIGHTS)
  const [savedWeights, setSavedWeights] = useState(DEFAULT_WEIGHTS)
  const [weightSaving, setWeightSaving] = useState(false)
  const [physicalRules, setPhysicalRules] = useState([])
  const [savedPhysicalRules, setSavedPhysicalRules] = useState([])
  const [physicalRuleLoading, setPhysicalRuleLoading] = useState(false)
  const [subjects, setSubjects] = useState([])
  const [savedSubjectCategories, setSavedSubjectCategories] = useState({})
  const [subjectLoading, setSubjectLoading] = useState(false)
  const [subjectSaving, setSubjectSaving] = useState(false)
  const [subjectSearch, setSubjectSearch] = useState('')
  const [classSearch, setClassSearch] = useState('')
  const [classStatusFilter, setClassStatusFilter] = useState('all')
  const [mobileSubjectCategory, setMobileSubjectCategory] = useState('INTELLECTUAL')
  const [copyModalOpen, setCopyModalOpen] = useState(false)
  const [copySourceClassName, setCopySourceClassName] = useState('')
  const [copyLoading, setCopyLoading] = useState(false)
  const [classConfigStatus, setClassConfigStatus] = useState({ summary: null, classes: [] })
  const [classConfigStatusLoading, setClassConfigStatusLoading] = useState(false)
  const [compositeConfigIssue, setCompositeConfigIssue] = useState(initialIssue)

  useEffect(() => {
    const nextIssue =
      location.state?.compositeSubjectConfigIssue || readCompositeSubjectConfigIssue()
    setCompositeConfigIssue(nextIssue || null)
    if (nextIssue) setActiveWorkspace('subjects')
  }, [location.state])

  const visibleMajors = useMemo(
    () =>
      majors
        .filter((item) => item.status !== 'inactive')
        .sort((a, b) => String(a.name || '').localeCompare(String(b.name || ''), 'zh-CN')),
    [majors],
  )

  const visibleClasses = useMemo(
    () =>
      mergeClassOptionsWithConfigStatus(classes, classConfigStatus.classes)
        .filter((item) => item.status !== 'inactive')
        .sort((a, b) => {
          const gradeCompare = String(b.grade || '').localeCompare(String(a.grade || ''), 'zh-CN', {
            numeric: true,
          })
          if (gradeCompare !== 0) return gradeCompare
          return String(a.name || '').localeCompare(String(b.name || ''), 'zh-CN', {
            numeric: true,
          })
        }),
    [classConfigStatus.classes, classes],
  )

  const gradeOptions = useMemo(
    () =>
      Array.from(
        new Set(
          visibleClasses
            .filter((item) => !selectedMajorId || String(item.majorId) === String(selectedMajorId))
            .map((item) => item.grade)
            .filter(Boolean),
        ),
      ).sort((a, b) => String(b).localeCompare(String(a), 'zh-CN', { numeric: true })),
    [selectedMajorId, visibleClasses],
  )

  const selectedClass = useMemo(
    () => visibleClasses.find((item) => item.name === selectedClassName) || null,
    [visibleClasses, selectedClassName],
  )

  const selectedMajorName = useMemo(
    () =>
      visibleMajors.find((item) => String(item.id) === String(selectedClass?.majorId))?.name || '-',
    [visibleMajors, selectedClass],
  )

  const classConfigStatusMap = useMemo(
    () => new Map((classConfigStatus.classes || []).map((item) => [item.className, item])),
    [classConfigStatus.classes],
  )

  const scopedClasses = useMemo(
    () =>
      visibleClasses.filter(
        (item) =>
          (!selectedMajorId || String(item.majorId) === String(selectedMajorId)) &&
          (!selectedGrade || String(item.grade) === String(selectedGrade)),
      ),
    [selectedGrade, selectedMajorId, visibleClasses],
  )

  const classStatusCounts = useMemo(
    () =>
      scopedClasses.reduce(
        (counts, item) => {
          const status = classConfigStatusMap.get(item.name)?.status || 'unconfigured'
          counts.all += 1
          counts[status] = (counts[status] || 0) + 1
          return counts
        },
        {
          all: 0,
          configured: 0,
          partial: 0,
          unconfigured: 0,
          no_subject: 0,
        },
      ),
    [classConfigStatusMap, scopedClasses],
  )

  const classConfigSummaryText = useMemo(
    () =>
      buildClassConfigSummaryText(
        selectedYear
          ? {
              totalCount: classStatusCounts.all,
              configuredCount: classStatusCounts.configured,
              partialCount: classStatusCounts.partial,
              unconfiguredCount: classStatusCounts.unconfigured,
              noSubjectCount: classStatusCounts.no_subject,
            }
          : null,
      ),
    [classStatusCounts, selectedYear],
  )

  const filteredClasses = useMemo(() => {
    const keyword = classSearch.trim().toLocaleLowerCase('zh-CN')
    return scopedClasses.filter((item) => {
      const status = classConfigStatusMap.get(item.name)?.status || 'unconfigured'
      const statusMatched = classStatusFilter === 'all' || status === classStatusFilter
      const searchMatched =
        !keyword ||
        String(item.name || '')
          .toLocaleLowerCase('zh-CN')
          .includes(keyword)
      return statusMatched && searchMatched
    })
  }, [classConfigStatusMap, classSearch, classStatusFilter, scopedClasses])

  const copySourceClasses = useMemo(
    () =>
      visibleClasses.filter(
        (item) =>
          item.name !== selectedClassName &&
          classConfigStatusMap.get(item.name)?.status === 'configured',
      ),
    [classConfigStatusMap, selectedClassName, visibleClasses],
  )

  const groupedSubjects = useMemo(() => {
    const keyword = subjectSearch.trim().toLocaleLowerCase('zh-CN')
    const visibleSubjects = subjects.filter(
      (item) =>
        !keyword ||
        String(item.subjectName || '')
          .toLocaleLowerCase('zh-CN')
          .includes(keyword),
    )
    return {
      INTELLECTUAL: visibleSubjects.filter((item) => item.category === 'INTELLECTUAL'),
      PHYSICAL: visibleSubjects.filter((item) => item.category === 'PHYSICAL'),
      EXCLUDED: visibleSubjects.filter((item) => item.category === 'EXCLUDED'),
    }
  }, [subjectSearch, subjects])

  const weightDirty = useMemo(
    () =>
      WEIGHT_FIELDS.some(
        (field) => weights[field] === '' || Number(weights[field]) !== Number(savedWeights[field]),
      ),
    [savedWeights, weights],
  )

  const selectedPhysicalRule = useMemo(
    () => physicalRules.find((item) => item.className === selectedClassName) || null,
    [physicalRules, selectedClassName],
  )

  const savedSelectedPhysicalRule = useMemo(
    () => savedPhysicalRules.find((item) => item.className === selectedClassName) || null,
    [savedPhysicalRules, selectedClassName],
  )

  const selectedPhysicalFullScoreEnabled = Boolean(selectedPhysicalRule?.fullScoreEnabled)
  const selectedPhysicalRuleDirty =
    Boolean(selectedClassName) &&
    selectedPhysicalFullScoreEnabled !== Boolean(savedSelectedPhysicalRule?.fullScoreEnabled)

  const changedSubjectCount = useMemo(
    () =>
      subjects.reduce(
        (count, item) =>
          savedSubjectCategories[String(item.subjectId)] !== item.category ? count + 1 : count,
        0,
      ),
    [savedSubjectCategories, subjects],
  )

  const selectedClassConfigStatus = classConfigStatusMap.get(selectedClassName)
  const subjectNeedsConfirmation = Boolean(
    subjects.length > 0 &&
      selectedClassConfigStatus &&
      !['configured', 'no_subject'].includes(selectedClassConfigStatus.status),
  )
  const pendingSubjectCount =
    changedSubjectCount ||
    Math.max(
      Number(selectedClassConfigStatus?.subjectCount || subjects.length) -
        Number(selectedClassConfigStatus?.configuredCount || 0),
      0,
    )
  const subjectDirty = changedSubjectCount > 0 || subjectNeedsConfirmation
  const classConfigDirty = subjectDirty || selectedPhysicalRuleDirty

  useEffect(() => {
    if (!weightDirty && !selectedPhysicalRuleDirty && changedSubjectCount === 0) return undefined
    const handleBeforeUnload = (event) => {
      event.preventDefault()
      event.returnValue = ''
    }
    window.addEventListener('beforeunload', handleBeforeUnload)
    return () => window.removeEventListener('beforeunload', handleBeforeUnload)
  }, [changedSubjectCount, selectedPhysicalRuleDirty, weightDirty])

  const fetchBaseData = useCallback(async () => {
    try {
      const [academicYearData, weightResponse, majorData, classData] = await Promise.all([
        getActiveAcademicYearList(),
        getCompositeScoreWeights(),
        getMajorList(),
        getClassOptions(),
      ])
      const yearList = Array.isArray(academicYearData)
        ? academicYearData
        : academicYearData?.data || []
      const defaultYear = yearList.find((item) => item?.current)?.name || yearList[0]?.name || ''
      const weightData = weightResponse?.data || {}
      const majorList = Array.isArray(majorData) ? majorData : majorData?.data || []
      const classList = Array.isArray(classData) ? classData : classData?.data || []
      const defaultClass =
        findIssueMatchedClass(classList, majorList, compositeConfigIssue) ||
        classList.find((item) => item.status !== 'inactive') ||
        null
      setAcademicYears(yearList)
      setMajors(majorList)
      setClasses(classList)
      setSelectedYear(defaultYear)
      setSelectedMajorId('')
      setSelectedGrade('')
      setSelectedClassName(defaultClass?.name || '')
      const nextWeights = normalizeWeights(weightData)
      setWeights(nextWeights)
      setSavedWeights(nextWeights)
    } catch (error) {
      console.error('获取综测配置基础数据失败:', error)
    }
  }, [compositeConfigIssue])

  useEffect(() => {
    fetchBaseData()
  }, [fetchBaseData])

  const fetchClassConfigStatus = useCallback(async (academicYear) => {
    if (!academicYear) {
      setClassConfigStatus({ summary: null, classes: [] })
      return
    }
    setClassConfigStatusLoading(true)
    try {
      const response = await getCompositeSubjectConfigStatus({ academicYear })
      const data = response?.data || {}
      setClassConfigStatus({
        summary: data.summary || null,
        classes: Array.isArray(data.classes) ? data.classes : [],
      })
    } catch (error) {
      console.error('获取班级配置状态失败:', error)
      setClassConfigStatus({ summary: null, classes: [] })
    } finally {
      setClassConfigStatusLoading(false)
    }
  }, [])

  useEffect(() => {
    fetchClassConfigStatus(selectedYear)
  }, [fetchClassConfigStatus, selectedYear])

  const fetchPhysicalRules = useCallback(async (academicYear) => {
    if (!academicYear) {
      setPhysicalRules([])
      setSavedPhysicalRules([])
      return
    }
    setPhysicalRuleLoading(true)
    try {
      const response = await getCompositePhysicalFullScoreRules({ academicYear })
      const nextRules = normalizePhysicalRules(response?.data?.items)
      setPhysicalRules(nextRules)
      setSavedPhysicalRules(nextRules)
    } catch (error) {
      console.error('获取体育满分规则失败:', error)
      setPhysicalRules([])
      setSavedPhysicalRules([])
    } finally {
      setPhysicalRuleLoading(false)
    }
  }, [])

  useEffect(() => {
    fetchPhysicalRules(selectedYear)
  }, [fetchPhysicalRules, selectedYear])

  const fetchSubjectConfigs = useCallback(async (academicYear, className) => {
    setSubjectLoading(true)
    try {
      const response = await getCompositeSubjectConfigs({ academicYear, className })
      const nextSubjects = Array.isArray(response?.data) ? response.data : []
      setSubjects(nextSubjects)
      setSavedSubjectCategories(buildSubjectCategoryMap(nextSubjects))
    } catch (error) {
      console.error('获取综测科目配置失败:', error)
      setSubjects([])
      setSavedSubjectCategories({})
    } finally {
      setSubjectLoading(false)
    }
  }, [])

  useEffect(() => {
    if (!selectedYear || !selectedClassName) {
      setSubjects([])
      setSavedSubjectCategories({})
      return
    }
    fetchSubjectConfigs(selectedYear, selectedClassName)
  }, [fetchSubjectConfigs, selectedClassName, selectedYear])

  const handleWeightChange = (field, value) => {
    setWeights((prev) => ({
      ...prev,
      [field]: value === '' ? '' : Number(value),
    }))
  }

  const handleSaveWeights = async () => {
    const invalidField = WEIGHT_FIELDS.find((field) => {
      const value = Number(weights[field])
      return weights[field] === '' || !Number.isFinite(value) || value < 0 || value > 100
    })
    if (invalidField) {
      message.error('计入比例必须是 0 到 100 之间的数字')
      return
    }

    setWeightSaving(true)
    try {
      const nextWeights = normalizeWeights(weights)
      await updateCompositeScoreWeights(nextWeights)
      setWeights(nextWeights)
      setSavedWeights(nextWeights)
      message.success('计入比例已保存')
    } catch (error) {
      console.error('保存综测权重失败:', error)
    } finally {
      setWeightSaving(false)
    }
  }

  const handleResetWeights = () => {
    setWeights(savedWeights)
  }

  const handlePhysicalRuleChange = (mode) => {
    if (!selectedClassName) return
    setPhysicalRules((prev) => {
      const nextEnabled = mode === 'full'
      const hasCurrentClass = prev.some((item) => item.className === selectedClassName)
      if (!hasCurrentClass) {
        return [
          ...prev,
          {
            className: selectedClassName,
            grade: normalizeGrade(selectedClass?.grade),
            majorId: selectedClass?.majorId ?? null,
            fullScoreEnabled: nextEnabled,
            configured: false,
            updatedAt: null,
          },
        ]
      }
      return prev.map((item) =>
        item.className === selectedClassName ? { ...item, fullScoreEnabled: nextEnabled } : item,
      )
    })
  }

  const resetSelectedPhysicalRule = () => {
    const savedEnabled = Boolean(savedSelectedPhysicalRule?.fullScoreEnabled)
    setPhysicalRules((prev) =>
      prev.map((item) =>
        item.className === selectedClassName ? { ...item, fullScoreEnabled: savedEnabled } : item,
      ),
    )
  }

  const handleSubjectCategoryChange = (subjectId, category) => {
    setSubjects((prev) =>
      prev.map((item) => (item.subjectId === subjectId ? { ...item, category } : item)),
    )
  }

  const confirmDiscardClassChanges = async () =>
    (changedSubjectCount === 0 && !selectedPhysicalRuleDirty) ||
    confirmAction({
      title: '放弃配置修改？',
      message: '当前班级有尚未保存的配置修改，切换后这些内容将丢失。',
      confirmText: '放弃修改',
      tone: 'warning',
    })

  const selectClass = async (nextClassName) => {
    if (nextClassName === selectedClassName) return
    if (!await confirmDiscardClassChanges()) return
    resetSelectedPhysicalRule()
    setSelectedClassName(nextClassName)
    setSubjectSearch('')
  }

  const handleYearChange = async (nextYear) => {
    if (nextYear === selectedYear || !await confirmDiscardClassChanges()) return
    resetSelectedPhysicalRule()
    setPhysicalRules([])
    setSavedPhysicalRules([])
    setSelectedYear(nextYear)
    setSubjectSearch('')
  }

  const handleMajorChange = (nextMajorId) => {
    if (String(nextMajorId) === String(selectedMajorId) || !confirmDiscardClassChanges()) return
    resetSelectedPhysicalRule()
    const nextMajorClasses = visibleClasses.filter(
      (item) => !nextMajorId || String(item.majorId) === String(nextMajorId),
    )
    const nextGrade =
      !selectedGrade ||
      nextMajorClasses.some((item) => String(item.grade) === String(selectedGrade))
        ? selectedGrade
        : ''
    const nextClasses = nextMajorClasses.filter(
      (item) => !nextGrade || String(item.grade) === String(nextGrade),
    )
    const selectedClassStillVisible = nextClasses.some((item) => item.name === selectedClassName)
    setSelectedMajorId(nextMajorId)
    setSelectedGrade(nextGrade)
    setSelectedClassName(selectedClassStillVisible ? selectedClassName : nextClasses[0]?.name || '')
    setClassSearch('')
    setSubjectSearch('')
  }

  const handleGradeChange = (nextGrade) => {
    if (String(nextGrade) === String(selectedGrade) || !confirmDiscardClassChanges()) return
    resetSelectedPhysicalRule()
    const nextClasses = visibleClasses.filter(
      (item) =>
        (!selectedMajorId || String(item.majorId) === String(selectedMajorId)) &&
        (!nextGrade || String(item.grade) === String(nextGrade)),
    )
    const selectedClassStillVisible = nextClasses.some((item) => item.name === selectedClassName)
    setSelectedGrade(nextGrade)
    setSelectedClassName(selectedClassStillVisible ? selectedClassName : nextClasses[0]?.name || '')
    setClassSearch('')
    setSubjectSearch('')
  }

  const handleOpenCopyModal = () => {
    setCopySourceClassName(copySourceClasses[0]?.name || '')
    setCopyModalOpen(true)
  }

  const handleCopySubjectConfig = async () => {
    if (!copySourceClassName) {
      message.warning('请选择来源班级')
      return
    }
    setCopyLoading(true)
    try {
      const response = await getCompositeSubjectConfigs({
        academicYear: selectedYear,
        className: copySourceClassName,
      })
      const sourceSubjects = Array.isArray(response?.data) ? response.data : []
      if (sourceSubjects.length === 0) {
        message.warning('来源班级没有可复制的科目配置')
        return
      }
      const categoryById = new Map(
        sourceSubjects.map((item) => [String(item.subjectId), item.category]),
      )
      const categoryByName = new Map(
        sourceSubjects.map((item) => [String(item.subjectName || '').trim(), item.category]),
      )
      let matchedCount = 0
      const nextSubjects = subjects.map((item) => {
        const nextCategory =
          categoryById.get(String(item.subjectId)) ||
          categoryByName.get(String(item.subjectName || '').trim())
        if (!nextCategory) return item
        matchedCount += 1
        return { ...item, category: nextCategory }
      })
      setSubjects(nextSubjects)
      setCopyModalOpen(false)
      message.success(`已复制 ${matchedCount} 门匹配科目，保存后生效`)
    } catch (error) {
      console.error('复制班级科目配置失败:', error)
    } finally {
      setCopyLoading(false)
    }
  }

  const handleResetSubjects = () => {
    setSubjects((prev) =>
      prev.map((item) => ({
        ...item,
        category: savedSubjectCategories[String(item.subjectId)] || item.category,
      })),
    )
  }

  const handleResetClassConfig = () => {
    handleResetSubjects()
    resetSelectedPhysicalRule()
  }

  const handleSaveClassConfig = async () => {
    if (!classConfigDirty) {
      message.info('当前没有需要保存的班级配置')
      return
    }
    setSubjectSaving(true)
    try {
      const operations = []
      if (subjectDirty) {
        operations.push(
          updateCompositeSubjectConfigs({
            academicYear: selectedYear,
            majorId: selectedClass?.majorId
              ? Number(selectedClass.majorId)
              : Number(selectedMajorId),
            grade: selectedClass?.grade || selectedGrade,
            className: selectedClassName,
            items: subjects.map((item) => ({
              subjectId: item.subjectId,
              category: item.category,
            })),
          }),
        )
      }
      if (selectedPhysicalRuleDirty) {
        operations.push(
          updateCompositePhysicalFullScoreRules({
            academicYear: selectedYear,
            items: [
              {
                className: selectedClassName,
                fullScoreEnabled: selectedPhysicalFullScoreEnabled,
              },
            ],
          }),
        )
      }
      await Promise.all(operations)
      await Promise.all([
        fetchSubjectConfigs(selectedYear, selectedClassName),
        fetchClassConfigStatus(selectedYear),
        fetchPhysicalRules(selectedYear),
      ])
      message.success('班级综测配置已保存')
    } catch (error) {
      console.error('保存班级综测配置失败:', error)
      await Promise.allSettled([
        fetchSubjectConfigs(selectedYear, selectedClassName),
        fetchClassConfigStatus(selectedYear),
        fetchPhysicalRules(selectedYear),
      ])
    } finally {
      setSubjectSaving(false)
    }
  }

  return (
    <div className="composite-config-page">
      <PageHeader title="综测配置" className="composite-config-page-header" />

      {compositeConfigIssue ? (
        <div className="composite-config-issue-alert" role="status">
          <div className="composite-config-issue-main">
            <strong>{compositeConfigIssue.title}</strong>
            <p>{compositeConfigIssue.description}</p>
            {compositeConfigIssue.details?.length ? (
              <div className="composite-config-issue-details">
                {compositeConfigIssue.details.map((item) => (
                  <span key={item.preview}>{item.preview}</span>
                ))}
                {compositeConfigIssue.moreScopeCount > 0 ? (
                  <span>还有 {compositeConfigIssue.moreScopeCount} 个范围未展示</span>
                ) : null}
              </div>
            ) : null}
          </div>
          <button type="button" onClick={() => setActiveWorkspace('subjects')}>
            查看科目归类
          </button>
        </div>
      ) : null}

      <div className="composite-config-workspace-tabs" role="tablist" aria-label="综测配置工作区">
        <button
          type="button"
          role="tab"
          aria-selected={activeWorkspace === 'rules'}
          className={activeWorkspace === 'rules' ? 'active' : ''}
          onClick={() => setActiveWorkspace('rules')}
        >
          计入比例
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={activeWorkspace === 'subjects'}
          className={activeWorkspace === 'subjects' ? 'active' : ''}
          onClick={() => setActiveWorkspace('subjects')}
        >
          科目归类
        </button>
      </div>

      {activeWorkspace === 'rules' ? (
        <section className="composite-config-workbench" role="tabpanel">
          <header className="composite-config-workbench-header">
            <div className="composite-config-title-line">
              <h3>计入比例</h3>
              <span className="composite-config-scope-label">全局</span>
            </div>
            {weightDirty ? (
              <span className="composite-config-save-state is-dirty">未保存</span>
            ) : null}
          </header>

          <div className="composite-config-rule-table" role="table" aria-label="综测计分规则">
            <div className="composite-config-rule-table-header" role="row">
              <span role="columnheader">维度</span>
              <span role="columnheader">基础分</span>
              <span role="columnheader">附加分</span>
            </div>
            {WEIGHT_ROWS.map((item) => (
              <div className={`composite-config-rule-row ${item.key}`} role="row" key={item.key}>
                <div className="composite-config-rule-name" role="cell">
                  <strong>{item.label}</strong>
                </div>
                <div className="composite-config-percent-input" role="cell">
                  <input
                    type="number"
                    min="0"
                    max="100"
                    step="1"
                    aria-label={`${item.label}基础分计入比例`}
                    value={weights[item.baseField]}
                    onChange={(event) => handleWeightChange(item.baseField, event.target.value)}
                  />
                  <span>%</span>
                </div>
                <div className="composite-config-percent-input" role="cell">
                  <input
                    type="number"
                    min="0"
                    max="100"
                    step="1"
                    aria-label={`${item.label}附加分计入比例`}
                    value={weights[item.extraField]}
                    onChange={(event) => handleWeightChange(item.extraField, event.target.value)}
                  />
                  <span>%</span>
                </div>
              </div>
            ))}
          </div>

          <footer className="composite-config-action-bar">
            <span className="composite-config-action-scope">全局生效</span>
            <div className="composite-config-actions">
              <button
                type="button"
                className="composite-config-btn default"
                onClick={handleResetWeights}
                disabled={!weightDirty || weightSaving}
              >
                撤销修改
              </button>
              <button
                type="button"
                className="composite-config-btn primary"
                onClick={handleSaveWeights}
                disabled={!weightDirty || weightSaving}
              >
                {weightSaving ? '保存中...' : '保存比例'}
              </button>
            </div>
          </footer>
        </section>
      ) : null}

      {activeWorkspace === 'subjects' ? (
        <section
          className="composite-config-workbench composite-config-subject-workbench"
          role="tabpanel"
        >
          <header className="composite-config-workbench-header">
            <div className="composite-config-title-line">
              <h3>科目归类</h3>
              <span className="composite-config-scope-label">学年 + 班级</span>
            </div>
            {classConfigDirty ? (
              <span className="composite-config-save-state is-dirty">
                {changedSubjectCount > 0
                  ? `${changedSubjectCount} 门未保存`
                  : selectedPhysicalRuleDirty
                    ? '体育规则未保存'
                    : `${pendingSubjectCount} 门待确认`}
              </span>
            ) : null}
          </header>

          <div className="composite-config-scope-toolbar">
            <label>
              <span>学年</span>
              <select
                value={selectedYear}
                onChange={(event) => handleYearChange(event.target.value)}
              >
                <option value="">请选择学年</option>
                {academicYears.map((item) => (
                  <option key={item.id} value={item.name}>
                    {item.name}
                  </option>
                ))}
              </select>
            </label>
            <label>
              <span>专业</span>
              <select
                value={selectedMajorId}
                onChange={(event) => handleMajorChange(event.target.value)}
              >
                <option value="">全部专业</option>
                {visibleMajors.map((item) => (
                  <option key={item.id} value={String(item.id)}>
                    {item.name}
                  </option>
                ))}
              </select>
            </label>
            <label>
              <span>年级</span>
              <select
                value={selectedGrade}
                onChange={(event) => handleGradeChange(event.target.value)}
              >
                <option value="">全部年级</option>
                {gradeOptions.map((grade) => (
                  <option key={grade} value={grade}>
                    {grade}级
                  </option>
                ))}
              </select>
            </label>
            <label className="composite-config-mobile-class-field">
              <span>班级</span>
              <select
                value={selectedClassName}
                onChange={(event) => selectClass(event.target.value)}
              >
                <option value="">请选择班级</option>
                {scopedClasses.map((item) => (
                  <option key={item.id} value={item.name}>
                    {item.name}
                  </option>
                ))}
              </select>
            </label>
            <div className="composite-config-scope-summary">
              <span>{classConfigStatusLoading ? '状态加载中...' : classConfigSummaryText}</span>
            </div>
          </div>

          <div className="composite-config-subject-layout">
            <aside className="composite-config-class-sidebar">
              <div className="composite-config-class-sidebar-header">
                <div>
                  <strong>班级</strong>
                  <span>{scopedClasses.length} 个</span>
                </div>
                <input
                  type="search"
                  value={classSearch}
                  placeholder="搜索班级"
                  aria-label="搜索班级"
                  onChange={(event) => setClassSearch(event.target.value)}
                />
              </div>
              <div
                className="composite-config-status-filters"
                role="group"
                aria-label="班级配置状态筛选"
              >
                {CLASS_STATUS_FILTERS.map((item) => (
                  <button
                    type="button"
                    key={item.value}
                    className={classStatusFilter === item.value ? 'active' : ''}
                    onClick={() => setClassStatusFilter(item.value)}
                  >
                    <span>{item.label}</span>
                    <small>{classStatusCounts[item.value] || 0}</small>
                  </button>
                ))}
              </div>
              <div className="composite-config-class-list">
                {filteredClasses.length === 0 ? (
                  <div className="composite-config-list-empty">没有匹配的班级</div>
                ) : (
                  filteredClasses.map((item) => {
                    const status = classConfigStatusMap.get(item.name)
                    const statusMeta = getClassConfigStatusMeta(status?.status)
                    return (
                      <button
                        key={item.id}
                        type="button"
                        className={`${statusMeta.className}${item.name === selectedClassName ? ' active' : ''}`}
                        onClick={() => selectClass(item.name)}
                      >
                        <span>
                          <strong>{item.name}</strong>
                          <small>{statusMeta.label}</small>
                        </span>
                        <em>
                          {status?.subjectCount
                            ? `${status.configuredCount || 0}/${status.subjectCount}`
                            : '-'}
                        </em>
                      </button>
                    )
                  })
                )}
              </div>
            </aside>

            <div className="composite-config-subject-editor">
              <div className="composite-config-subject-editor-header">
                <div>
                  <h4>{selectedClassName || '请选择班级'}</h4>
                  <p>
                    {selectedClass
                      ? `${selectedMajorName} · ${selectedClass.grade || '-'}级`
                      : '选择班级后配置成绩科目'}
                  </p>
                </div>
                <div className="composite-config-subject-editor-tools">
                  <input
                    type="search"
                    value={subjectSearch}
                    placeholder="搜索科目"
                    aria-label="搜索科目"
                    onChange={(event) => setSubjectSearch(event.target.value)}
                  />
                  <button
                    type="button"
                    className="composite-config-btn default"
                    onClick={handleOpenCopyModal}
                    disabled={!selectedYear || !selectedClassName || copySourceClasses.length === 0}
                  >
                    复制归类
                  </button>
                </div>
              </div>

              <div
                className="composite-config-mobile-category-tabs"
                role="tablist"
                aria-label="科目分类"
              >
                {SUBJECT_GROUPS.map((group) => (
                  <button
                    type="button"
                    role="tab"
                    aria-selected={mobileSubjectCategory === group.category}
                    key={group.category}
                    className={mobileSubjectCategory === group.category ? 'active' : ''}
                    onClick={() => setMobileSubjectCategory(group.category)}
                  >
                    {group.title}
                    <small>{groupedSubjects[group.category].length}</small>
                  </button>
                ))}
              </div>

              {subjectLoading ? (
                <div className="composite-config-empty">正在加载科目...</div>
              ) : !selectedYear || !selectedClassName ? (
                <div className="composite-config-empty">请先选择学年和班级。</div>
              ) : (
                <div className="composite-config-subject-grid">
                  {SUBJECT_GROUPS.map((group) => {
                    const list = groupedSubjects[group.category]
                    const isPhysicalGroup = group.category === 'PHYSICAL'
                    const isPhysicalFullScore = isPhysicalGroup && selectedPhysicalFullScoreEnabled
                    return (
                      <div
                        key={group.category}
                        className={`composite-config-subject-panel ${group.tone}${mobileSubjectCategory === group.category ? ' is-mobile-active' : ''}${isPhysicalFullScore ? ' is-full-score' : ''}`}
                      >
                        <div className="composite-config-subject-panel-header">
                          <div className="composite-config-subject-panel-title">
                            <h4>{group.title}</h4>
                            {isPhysicalGroup ? (
                              <Segmented
                                aria-label={`${selectedClassName || '当前班级'}体育基础分规则`}
                                value={selectedPhysicalFullScoreEnabled ? 'full' : 'actual'}
                                options={[
                                  { label: '按实分', value: 'actual' },
                                  { label: '按满分', value: 'full' },
                                ]}
                                onChange={handlePhysicalRuleChange}
                                disabled={!selectedClassName || physicalRuleLoading}
                              />
                            ) : null}
                          </div>
                          <span>{list.length} 门</span>
                        </div>
                        <div
                          className="composite-config-subject-panel-body"
                          aria-disabled={isPhysicalFullScore}
                        >
                          <div className="composite-config-subject-list">
                            {list.length === 0 ? (
                              <div className="composite-config-panel-empty">
                                {subjectSearch ? '没有匹配科目' : '暂无科目'}
                              </div>
                            ) : (
                              list.map((item) => {
                                const isChanged =
                                  savedSubjectCategories[String(item.subjectId)] !== item.category
                                return (
                                  <div
                                    key={item.subjectId}
                                    className={`composite-config-subject-row${isChanged ? ' is-changed' : ''}`}
                                  >
                                    <span>
                                      <strong>{item.subjectName}</strong>
                                      {isChanged ? <small>已修改</small> : null}
                                    </span>
                                    <select
                                      value={item.category}
                                      aria-label={`${item.subjectName}的综测归类`}
                                      disabled={isPhysicalFullScore}
                                      onChange={(event) =>
                                        handleSubjectCategoryChange(
                                          item.subjectId,
                                          event.target.value,
                                        )
                                      }
                                    >
                                      <option value="INTELLECTUAL">智育基础分</option>
                                      <option value="PHYSICAL">体育基础分</option>
                                      <option value="EXCLUDED">不计入综测</option>
                                    </select>
                                  </div>
                                )
                              })
                            )}
                          </div>
                          {isPhysicalFullScore ? (
                            <div
                              className="composite-config-physical-full-score-mask"
                              role="status"
                            >
                              体育成绩记为满分
                            </div>
                          ) : null}
                        </div>
                      </div>
                    )
                  })}
                </div>
              )}
            </div>
          </div>

          <footer className="composite-config-action-bar">
            <span className="composite-config-action-scope">
              {selectedClassName ? `${selectedYear || '-'} · ${selectedClassName}` : '请选择班级'}
            </span>
            <div className="composite-config-actions">
              <button
                type="button"
                className="composite-config-btn default"
                onClick={handleResetClassConfig}
                disabled={
                  (changedSubjectCount === 0 && !selectedPhysicalRuleDirty) || subjectSaving
                }
              >
                撤销修改
              </button>
              <button
                type="button"
                className="composite-config-btn primary"
                onClick={handleSaveClassConfig}
                disabled={
                  !classConfigDirty ||
                  !selectedYear ||
                  !selectedClassName ||
                  subjectSaving ||
                  subjectLoading ||
                  (selectedPhysicalRuleDirty && physicalRuleLoading)
                }
              >
                {subjectSaving ? '保存中...' : '保存班级配置'}
              </button>
            </div>
          </footer>
        </section>
      ) : null}

      <Modal
        open={copyModalOpen}
        title="复制班级科目配置"
        okText="复制到当前班级"
        cancelText="取消"
        confirmLoading={copyLoading}
        onOk={handleCopySubjectConfig}
        onCancel={() => setCopyModalOpen(false)}
        destroyOnHidden
      >
        <div className="composite-config-copy-dialog">
          <p>
            将来源班级中名称或编号相同的科目归类复制到
            <strong>{selectedClassName || '当前班级'}</strong>。复制后需要再次保存才会生效。
          </p>
          <label>
            <span>来源班级</span>
            <select
              value={copySourceClassName}
              onChange={(event) => setCopySourceClassName(event.target.value)}
            >
              <option value="">请选择来源班级</option>
              {copySourceClasses.map((item) => {
                const statusMeta = getClassConfigStatusMeta(
                  classConfigStatusMap.get(item.name)?.status,
                )
                return (
                  <option key={item.id} value={item.name}>
                    {item.name} · {item.grade || '-'}级 · {statusMeta.label}
                  </option>
                )
              })}
            </select>
          </label>
        </div>
      </Modal>
    </div>
  )
}

export default CompositeScoreConfig
