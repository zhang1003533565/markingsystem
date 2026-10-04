import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  Button,
  Card,
  Dropdown,
  Drawer,
  Empty,
  Image,
  Input,
  Modal,
  Pagination,
  Segmented,
  Select,
  Skeleton,
  Space,
  Typography,
  Upload,
  message,
} from 'antd'
import { withAuthToken } from '../utils/authStorage'
import {
  CalendarOutlined,
  CheckCircleOutlined,
  DeleteOutlined,
  EditOutlined,
  FileTextOutlined,
  InfoCircleOutlined,
  PlusCircleOutlined,
  RollbackOutlined,
  SearchOutlined,
  UploadOutlined,
} from './AcademicIcons'
import { useLocation, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import StudentLayout from './StudentLayout'
import {
  createExtraScoreApplication,
  deleteExtraScoreApplication,
  getExtraScoreApplicationDetail,
  getExtraScoreApplicationEditContext,
  getExtraScoreApplicationListContext,
  getExtraScoreSubmissionWindowHistory,
  searchExtraScoreTemplateItems,
  uploadExtraScoreProofImages,
  updateExtraScoreApplication,
} from '../api/student/extraScore'
import { getCreateApplicationIssue } from '../utils/extraScoreCreateGuard'
import { buildTemplateGroupsForApplication } from '../utils/extraScoreFormOptions'
import {
  canDeleteExtraScoreRecord,
  canEditExtraScoreRecord,
  canSupplementExtraScoreRecord,
} from '../utils/extraScoreRecordActions'
import { confirmAction } from './confirmDialogService'
import './StudentExtraScorePage.css'

const { Title, Text } = Typography
const { TextArea } = Input

const STATUS_MAP = {
  draft: { label: '草稿', className: 'default' },
  pending: { label: '待审核', className: 'pending' },
  approved: { label: '已通过', className: 'approved' },
  rejected: { label: '已拒绝', className: 'rejected' },
  returned: { label: '已打回', className: 'returned' },
}

const SCORE_TYPE_OPTIONS = [
  { value: 'MORAL', label: '德育' },
  { value: 'INTELLECTUAL', label: '智育' },
  { value: 'PHYSICAL', label: '体育' },
]

const PAGE_THEME = {
  INTELLECTUAL: {
    title: '智育附加分申请',
    subtitle: '根据学校配置的智育附加分模板，在规定的金额、证书、科研等加分申请',
    accent: '#2563eb',
  },
  MORAL: {
    title: '德育附加分申请',
    subtitle: '根据学校配置的德育附加分模板，进行志愿服务、社会实践、德育表现等申请',
    accent: '#16a34a',
  },
  PHYSICAL: {
    title: '体育附加分申请',
    subtitle: '根据学校配置的体育附加分模板，进行赛事成绩、技能证书、体能活动等申请',
    accent: '#f97316',
  },
}

const emptyForm = {
  itemId: null,
  levelRuleId: null,
  authorRuleId: null,
  certFileList: [],
  certName: '',
  detailDesc: '',
  awardTime: '',
  proofText: '',
  proofImageList: [],
}

const formatNumber = (value, digits = 2) => Number(value || 0).toFixed(digits)

const formatDateTime = (value) => {
  if (!value) {
    return '-'
  }
  return String(value).replace('T', ' ').slice(0, 16)
}

const RequiredLabel = ({ children }) => (
  <span className="extra-score-required-label">{children}</span>
)

const getUploadPreviewUrl = (value) => {
  const text = String(value || '')
  return text.startsWith('/uploads/') ? `/api${text}` : text
}

const getRecordCertificatePreview = (record) => {
  const previews = getRecordCertificatePreviews(record)
  return previews[0] || ''
}

const toStoredList = (value) => {
  if (Array.isArray(value)) {
    return value.filter(Boolean)
  }
  const text = String(value || '')
  return text ? text.split('||').filter(Boolean) : []
}

const getRecordCertificatePreviews = (record) => {
  const urls = [
    ...toStoredList(record?.thumbnailUrls),
    ...toStoredList(record?.certFileUrls),
    ...toStoredList(record?.certFileUrl),
  ]
  if (!urls.length && Array.isArray(record?.proofImages)) {
    urls.push(...record.proofImages)
  }
  return Array.from(new Set(urls.map(getUploadPreviewUrl).filter(Boolean))).filter((url) =>
    isImageUploadFile({ url, name: url }),
  )
}

const getRecordDetailMediaUrls = (record) => {
  const certUrls = getRecordCertificatePreviews(record)
  const proofUrls = Array.isArray(record?.proofImages)
    ? record.proofImages.map(getUploadPreviewUrl).filter(Boolean)
    : []
  return Array.from(new Set([...certUrls, ...proofUrls])).filter((url) =>
    isImageUploadFile({ url, name: url }),
  )
}

const isImageUploadFile = (file) =>
  String(file?.type || file?.originFileObj?.type || '').startsWith('image/') ||
  /\.(png|jpe?g|gif|webp|bmp)$/i.test(String(file?.url || file?.name || ''))

const readImagePreview = (file) =>
  new Promise((resolve) => {
    if (!file?.originFileObj || !isImageUploadFile(file)) {
      resolve(file)
      return
    }
    const reader = new FileReader()
    reader.onload = () => {
      resolve({
        ...file,
        previewUrl: reader.result,
        thumbUrl: reader.result,
      })
    }
    reader.onerror = () => resolve(file)
    reader.readAsDataURL(file.originFileObj)
  })

const withUploadPreviews = (fileList) =>
  fileList.map((file) => {
    if (file.previewUrl || file.thumbUrl) {
      return file
    }
    if (file.url && isImageUploadFile(file)) {
      const previewUrl = getUploadPreviewUrl(file.url)
      return {
        ...file,
        previewUrl,
        thumbUrl: previewUrl,
      }
    }
    return file
  })

const renderUploadItem = (_, file, __, actions) => {
  const previewUrl =
    file.previewUrl || file.thumbUrl || (file.url ? getUploadPreviewUrl(file.url) : '')
  const isImage = Boolean(previewUrl) && isImageUploadFile(file)

  return (
    <div className="extra-score-upload-preview-card">
      <button
        type="button"
        className="extra-score-upload-remove"
        aria-label="删除文件"
        onClick={(event) => {
          event.stopPropagation()
          actions.remove?.()
        }}
      >
        ×
      </button>
      {isImage ? (
        <img src={withAuthToken(previewUrl)} alt={file.name || '凭证'} />
      ) : (
        <div className="extra-score-upload-file-icon">
          <FileTextOutlined />
        </div>
      )}
      <span title={file.name}>{file.name || '凭证文件'}</span>
    </div>
  )
}

function getStatusMeta(record) {
  const fallback = STATUS_MAP[record?.status] || {
    label: record?.status || '-',
    className: 'default',
  }
  if (record?.status === 'pending' && record?.reviewStageLabel) {
    return { ...fallback, label: record.reviewStageLabel }
  }
  return fallback
}

function getWindowState(window) {
  if (!window) {
    return { label: '未配置', className: 'closed' }
  }
  if (window.open) {
    return { label: '可提交', className: 'open' }
  }
  return { label: '未开放', className: 'closed' }
}

function getRemainingDays(window) {
  if (!window?.endTime || !window?.open) {
    return null
  }
  const now = new Date()
  const end = new Date(window.endTime)
  const diff = end.getTime() - now.getTime()
  if (Number.isNaN(diff) || diff <= 0) {
    return 0
  }
  return Math.ceil(diff / (1000 * 60 * 60 * 24))
}

function getPeriodLabel(record) {
  return record?.submissionPeriodLabel || record?.periodLabel || '提交周期'
}

function resolveWindowStage(record, currentWindow) {
  const now = new Date()
  const start = record?.startTime ? new Date(record.startTime) : null
  const end = record?.endTime ? new Date(record.endTime) : null
  const currentKey = `${currentWindow?.academicYear || ''}-${currentWindow?.startTime || ''}-${currentWindow?.endTime || ''}`
  const recordKey = `${record?.academicYear || ''}-${record?.startTime || ''}-${record?.endTime || ''}`

  if (currentWindow?.open && currentKey === recordKey) {
    return { label: '进行中', className: 'open' }
  }
  if (start && now < start) {
    return { label: '待开始', className: 'pending' }
  }
  if (end && now > end) {
    return { label: '已结束', className: 'closed' }
  }
  if (record?.enabled) {
    return { label: '待生效', className: 'pending' }
  }
  return { label: '未开启', className: 'closed' }
}

function StudentExtraScorePage({
  scoreType: initialScoreType,
  title,
  color,
  activePath = '/student/extra',
}) {
  const navigate = useNavigate()
  const location = useLocation()
  const { applicationId } = useParams()
  const [searchParams] = useSearchParams()
  const queryScoreType = String(searchParams.get('type') || '').toUpperCase()
  const scoreType = SCORE_TYPE_OPTIONS.some((item) => item.value === queryScoreType)
    ? queryScoreType
    : initialScoreType || 'INTELLECTUAL'
  const theme = PAGE_THEME[scoreType] || {
    title,
    subtitle: '根据学校配置的附加分模板进行申请',
    accent: color || '#2563eb',
  }

  const [loading, setLoading] = useState(false)
  const [dataReady, setDataReady] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [templateInfo, setTemplateInfo] = useState(null)
  const [records, setRecords] = useState([])
  const [listStats, setListStats] = useState({})
  const [pagination, setPagination] = useState({ page: 1, size: 10, total: 0 })
  const [submissionWindow, setSubmissionWindow] = useState(null)
  const [submissionWindowHistory, setSubmissionWindowHistory] = useState([])
  const [historyModalOpen, setHistoryModalOpen] = useState(false)
  const [historyLoading, setHistoryLoading] = useState(false)
  const [historyStageFilter, setHistoryStageFilter] = useState('all')
  const [detailDrawerOpen, setDetailDrawerOpen] = useState(false)
  const [detailLoading, setDetailLoading] = useState(false)
  const [detailRecord, setDetailRecord] = useState(null)
  const [editingRecord, setEditingRecord] = useState(null)
  const [formState, setFormState] = useState(emptyForm)
  const [itemSearchKeyword, setItemSearchKeyword] = useState('')
  const [searchedTemplateGroups, setSearchedTemplateGroups] = useState(null)
  const [itemSearchLoading, setItemSearchLoading] = useState(false)
  const [itemSearchFailed, setItemSearchFailed] = useState(false)
  const [statusFilter, setStatusFilter] = useState('all')
  const [keyword, setKeyword] = useState('')
  const [sortMode, setSortMode] = useState('upload-desc')
  const withScoreType = (path) => `${path}?type=${scoreType}`
  const isCreatePage = location.pathname === `${activePath}/apply`
  const isEditPage = location.pathname.includes('/edit/')
  const isFormPage = isCreatePage || isEditPage
  const formReturnPath = location.state?.returnTo

  const fetchData = useCallback(async () => {
    setLoading(true)
    setDataReady(false)
    try {
      if (isEditPage && applicationId) {
        const res = await getExtraScoreApplicationEditContext(applicationId, scoreType)
        const data = res?.data || {}
        setTemplateInfo(data.templateInfo || null)
        setRecords(data.application ? [data.application] : [])
        setSubmissionWindow(data.submissionWindow || null)
        return
      }
      const res = await getExtraScoreApplicationListContext(scoreType, {
        status: statusFilter,
        keyword: keyword.trim() || undefined,
        sort: sortMode,
        page: pagination.page - 1,
        size: pagination.size,
      })
      const data = res?.data || {}
      setTemplateInfo(data.templateInfo || null)
      setRecords(Array.isArray(data.records) ? data.records : [])
      setListStats(data.stats || {})
      setPagination((prev) => ({
        ...prev,
        page: Number(data.page ?? prev.page - 1) + 1,
        size: Number(data.size || prev.size),
        total: Number(data.total || 0),
      }))
      setSubmissionWindow(data.submissionWindow || null)
    } catch (error) {
      message.error(error?.response?.data?.message || '获取附加分数据失败')
    } finally {
      setLoading(false)
      setDataReady(true)
    }
  }, [
    applicationId,
    isEditPage,
    keyword,
    pagination.page,
    pagination.size,
    scoreType,
    sortMode,
    statusFilter,
  ])

  useEffect(() => {
    fetchData()
  }, [fetchData])

  useEffect(() => {
    setPagination((prev) => ({ ...prev, page: 1 }))
  }, [keyword, scoreType, sortMode, statusFilter])

  useEffect(() => {
    const normalizedKeyword = itemSearchKeyword.trim()
    if (!isFormPage || !normalizedKeyword) {
      setSearchedTemplateGroups(null)
      setItemSearchLoading(false)
      setItemSearchFailed(false)
      return undefined
    }

    let cancelled = false
    const timer = window.setTimeout(async () => {
      setItemSearchLoading(true)
      setItemSearchFailed(false)
      try {
        const response = await searchExtraScoreTemplateItems(scoreType, normalizedKeyword)
        if (!cancelled) {
          setSearchedTemplateGroups(
            Array.isArray(response?.data?.groups) ? response.data.groups : [],
          )
        }
      } catch {
        if (!cancelled) {
          setSearchedTemplateGroups([])
          setItemSearchFailed(true)
        }
      } finally {
        if (!cancelled) {
          setItemSearchLoading(false)
        }
      }
    }, 300)

    return () => {
      cancelled = true
      window.clearTimeout(timer)
    }
  }, [isFormPage, itemSearchKeyword, scoreType])

  const openHistoryModal = async () => {
    setHistoryModalOpen(true)
    if (submissionWindowHistory.length) {
      return
    }
    setHistoryLoading(true)
    try {
      const historyRes = await getExtraScoreSubmissionWindowHistory()
      setSubmissionWindowHistory(Array.isArray(historyRes?.data) ? historyRes.data : [])
    } catch (error) {
      setSubmissionWindowHistory([])
      message.error(error?.response?.data?.message || '获取提交周期历史失败')
    } finally {
      setHistoryLoading(false)
    }
  }

  const submissionHistoryEntries = useMemo(
    () =>
      submissionWindowHistory
        .map((record) => ({
          ...record,
          windowStage: resolveWindowStage(record, submissionWindow),
        }))
        .sort((left, right) => {
          const stageRank = { open: 0, pending: 1, closed: 2 }
          const rankDiff =
            (stageRank[left.windowStage.className] ?? 3) -
            (stageRank[right.windowStage.className] ?? 3)
          if (rankDiff !== 0) {
            return rankDiff
          }
          return String(right.startTime || '').localeCompare(String(left.startTime || ''))
        }),
    [submissionWindow, submissionWindowHistory],
  )
  const submissionHistoryCounts = useMemo(
    () => ({
      all: submissionHistoryEntries.length,
      active: submissionHistoryEntries.filter((record) => record.windowStage.className !== 'closed')
        .length,
      closed: submissionHistoryEntries.filter((record) => record.windowStage.className === 'closed')
        .length,
    }),
    [submissionHistoryEntries],
  )
  const groupedSubmissionHistory = useMemo(() => {
    const groups = new Map()
    submissionHistoryEntries
      .filter((record) => {
        if (historyStageFilter === 'active') {
          return record.windowStage.className !== 'closed'
        }
        if (historyStageFilter === 'closed') {
          return record.windowStage.className === 'closed'
        }
        return true
      })
      .forEach((record) => {
        const academicYear = record.academicYear || '未标记学年'
        if (!groups.has(academicYear)) {
          groups.set(academicYear, [])
        }
        groups.get(academicYear).push(record)
      })
    return Array.from(groups, ([academicYear, records]) => ({ academicYear, records }))
  }, [historyStageFilter, submissionHistoryEntries])
  const submissionHistoryClassNames = useMemo(
    () =>
      Array.from(
        new Set(
          submissionWindowHistory.flatMap((record) => record.classNames || []).filter(Boolean),
        ),
      ),
    [submissionWindowHistory],
  )

  const currentTemplate = templateInfo?.template || null
  const templateGroups = useMemo(
    () => buildTemplateGroupsForApplication(currentTemplate?.groups || [], editingRecord),
    [currentTemplate, editingRecord],
  )
  const originalRecordRuleGroup = useMemo(
    () => templateGroups.find((group) => String(group.id || '').startsWith('legacy-')) || null,
    [templateGroups],
  )
  const originalRecordRuleUnavailable = Boolean(
    originalRecordRuleGroup?.items?.some((item) => item.ruleUnavailable),
  )
  const visibleTemplateGroups = searchedTemplateGroups ?? templateGroups
  const itemSelectOptions = useMemo(
    () =>
      visibleTemplateGroups
        .map((group) => ({
          label: group.groupName,
          options: (group.items || [])
            .filter((item) => item.status !== 'inactive')
            .map((item) => ({
              value: String(item.id),
              label: `${group.groupName} / ${item.itemName}`,
              searchText: `${group.groupName} ${item.itemName}`,
            })),
        }))
        .filter((group) => group.options.length > 0),
    [visibleTemplateGroups],
  )
  const selectedItem = useMemo(() => {
    if (!formState.itemId) {
      return null
    }
    for (const group of templateGroups) {
      const match = (group.items || []).find((item) => Number(item.id) === Number(formState.itemId))
      if (match) {
        return match
      }
    }
    return null
  }, [formState.itemId, templateGroups])
  const levelOptions = selectedItem?.levelRules || []
  const selectedLevelRule =
    levelOptions.find((rule) => Number(rule.id) === Number(formState.levelRuleId)) || null
  const authorOptions = selectedLevelRule?.authorRules || []
  const selectedAuthorRule =
    authorOptions.find((rule) => Number(rule.id) === Number(formState.authorRuleId)) || null
  const selectedDirection = selectedItem?.scoreDirection || 'add'
  const calculatedScore =
    selectedLevelRule && selectedAuthorRule
      ? Number(
          ((selectedAuthorRule.mode || 'ratio') === 'fixed'
            ? Number(selectedAuthorRule.fixedScore || 0)
            : (Number(selectedLevelRule.score || 0) * Number(selectedAuthorRule.ratio || 0)) / 100
          ).toFixed(2),
        )
      : null
  const calculatedScoreDescription =
    selectedLevelRule && selectedAuthorRule
      ? (selectedAuthorRule.mode || 'ratio') === 'fixed'
        ? `固定分值 ${selectedAuthorRule.fixedScore || 0}`
        : `基础分 ${selectedLevelRule.score} × 作者比例 ${selectedAuthorRule.ratio || 0}%`
      : '系统会按模板规则自动计算，不需要手动填写分值。'
  const draftCount = Number(listStats.draftCount || 0)
  const returnedCount = Number(listStats.returnedCount || 0)
  const canSubmitNow = Boolean(submissionWindow?.open)
  const hasConfiguredGroups = templateGroups.length > 0
  const windowState = getWindowState(submissionWindow)
  const remainingDays = getRemainingDays(submissionWindow)
  const submissionPeriodText = submissionWindow
    ? `${formatDateTime(submissionWindow.startTime)} 至 ${formatDateTime(submissionWindow.endTime)}`
    : '暂未配置提交周期'

  const filteredRecords = records
  const actionableRecords = useMemo(
    () => filteredRecords.filter((record) => canEditExtraScoreRecord(record, canSubmitNow)),
    [canSubmitNow, filteredRecords],
  )
  const otherRecords = useMemo(
    () => filteredRecords.filter((record) => !canEditExtraScoreRecord(record, canSubmitNow)),
    [canSubmitNow, filteredRecords],
  )
  const showRecordTools =
    pagination.total > 5 || Boolean(keyword) || statusFilter !== 'all' || sortMode !== 'upload-desc'
  const deadlineLabel =
    remainingDays === 0
      ? '今天截止'
      : remainingDays !== null
        ? `距离截止还有 ${remainingDays} 天`
        : '当前可以提交申请'
  const formStatus = editingRecord
    ? getStatusMeta(editingRecord)
    : isEditPage
      ? { label: '加载中', className: 'default' }
      : { label: '新申请', className: 'default' }
  const detailStatus = detailRecord ? getStatusMeta(detailRecord) : null
  const detailCanEdit = Boolean(detailRecord && canEditExtraScoreRecord(detailRecord, canSubmitNow))
  const detailCanViewProgress = detailRecord?.status === 'pending'
  const detailPrimaryLabel = detailRecord
    ? canSupplementExtraScoreRecord(detailRecord, canSubmitNow)
      ? '补充材料'
      : detailRecord.status === 'draft'
        ? '继续填写'
        : '修改申请'
    : ''
  const detailMediaCount = getRecordDetailMediaUrls(detailRecord).length

  const openCreatePage = () => {
    const issue = getCreateApplicationIssue({
      currentTemplate,
      hasConfiguredGroups,
      canSubmitNow,
    })
    if (issue) {
      message.warning(issue)
      return
    }
    navigate(withScoreType(`${activePath}/apply`))
  }

  const openEditPage = (record) => {
    navigate(withScoreType(`${activePath}/edit/${record.id}`))
  }

  const closeFormPage = () => {
    navigate(formReturnPath || withScoreType(activePath))
  }

  useEffect(() => {
    if (!isFormPage) {
      setEditingRecord(null)
      setFormState(emptyForm)
      return
    }

    if (isCreatePage) {
      setEditingRecord(null)
      setFormState(emptyForm)
      return
    }

    const targetRecord = records.find((record) => String(record.id) === String(applicationId))
    if (!targetRecord) {
      return
    }

    setEditingRecord(targetRecord)
    const proofFiles = Array.isArray(targetRecord.proofImages)
      ? targetRecord.proofImages.map((item, index) => ({
          uid: `${targetRecord.id}-${index}`,
          name: String(item).split('/').pop() || item,
          url: item,
          status: 'done',
        }))
      : []
    const certFileUrls = toStoredList(
      targetRecord.certFileUrls?.length ? targetRecord.certFileUrls : targetRecord.certFileUrl,
    )
    const certFileNames = toStoredList(
      targetRecord.certFileNames?.length ? targetRecord.certFileNames : targetRecord.certFileName,
    )
    setFormState({
      itemId: targetRecord.itemId || null,
      levelRuleId: targetRecord.levelRuleId || null,
      authorRuleId: targetRecord.authorRuleId || null,
      certFileList: withUploadPreviews(
        certFileUrls.map((url, index) => ({
          uid: `cert-${targetRecord.id}-${index}`,
          name: certFileNames[index] || String(url).split('/').pop() || '证书文件',
          url,
          status: 'done',
        })),
      ),
      certName: targetRecord.certName || '',
      detailDesc: targetRecord.detailDesc || '',
      awardTime: targetRecord.awardTime || '',
      proofText: targetRecord.proofText || '',
      proofImageList: withUploadPreviews(proofFiles),
    })
  }, [applicationId, isCreatePage, isFormPage, records])

  useEffect(() => {
    if (
      isEditPage &&
      !loading &&
      records.length &&
      !records.some((record) => String(record.id) === String(applicationId))
    ) {
      message.warning('未找到对应申请记录')
      navigate(activePath, { replace: true })
    }
  }, [activePath, applicationId, isEditPage, loading, navigate, records])

  useEffect(() => {
    if (isFormPage && dataReady && !currentTemplate && !loading) {
      navigate(activePath, { replace: true })
    }
  }, [activePath, currentTemplate, dataReady, isFormPage, loading, navigate])

  useEffect(() => {
    if (isFormPage && dataReady && !canSubmitNow && !loading) {
      message.warning('当前提交窗口未开放，暂不能提交申请')
      navigate(activePath, { replace: true })
    }
  }, [activePath, canSubmitNow, dataReady, isFormPage, loading, navigate])

  const validateForm = () => {
    if (!currentTemplate) {
      message.error('当前没有可用模板')
      return false
    }
    if (!formState.itemId) {
      message.error('请选择小类')
      return false
    }
    if (!formState.levelRuleId) {
      message.error('请选择级别规则')
      return false
    }
    if (!formState.authorRuleId) {
      message.error('请选择作者规则')
      return false
    }
    if (!formState.certName.trim()) {
      message.error('请填写证书名称')
      return false
    }
    if (!formState.awardTime) {
      message.error('请选择获得时间')
      return false
    }
    if (!formState.certFileList.length) {
      message.error('请上传证书文件')
      return false
    }
    if (calculatedScore === null || calculatedScore <= 0) {
      message.error('计算后的分值必须大于 0')
      return false
    }
    return true
  }

  const handleProofUploadChange = async (field, fileList, { single = false } = {}) => {
    const normalizedList = withUploadPreviews(single ? fileList.slice(-1) : fileList)
    const nextList = await Promise.all(normalizedList.map(readImagePreview))
    setFormState((prev) => ({ ...prev, [field]: nextList }))

    const pendingFiles = nextList.filter(
      (item) => item.originFileObj && !item.url && item.status !== 'uploading',
    )
    if (!pendingFiles.length) {
      return
    }

    setFormState((prev) => ({
      ...prev,
      [field]: prev[field].map((item) =>
        pendingFiles.some((pending) => pending.uid === item.uid)
          ? { ...item, status: 'uploading' }
          : item,
      ),
    }))

    try {
      const formData = new FormData()
      pendingFiles.forEach((item) => {
        formData.append('files', item.originFileObj)
      })
      const uploadRes = await uploadExtraScoreProofImages(formData)
      const uploadedUrls = Array.isArray(uploadRes?.data) ? uploadRes.data : []
      setFormState((prev) => {
        let urlIndex = 0
        return {
          ...prev,
          [field]: prev[field].map((item) => {
            if (!pendingFiles.some((pending) => pending.uid === item.uid)) {
              return item
            }
            const url = uploadedUrls[urlIndex++]
            return url
              ? {
                  uid: item.uid,
                  name: item.name,
                  url,
                  previewUrl:
                    item.previewUrl ||
                    item.thumbUrl ||
                    (isImageUploadFile({ ...item, url }) ? getUploadPreviewUrl(url) : undefined),
                  thumbUrl:
                    item.previewUrl ||
                    item.thumbUrl ||
                    (isImageUploadFile({ ...item, url }) ? getUploadPreviewUrl(url) : undefined),
                  status: 'done',
                }
              : { ...item, status: 'error' }
          }),
        }
      })
    } catch (error) {
      setFormState((prev) => ({
        ...prev,
        [field]: prev[field].map((item) =>
          pendingFiles.some((pending) => pending.uid === item.uid)
            ? { ...item, status: 'error' }
            : item,
        ),
      }))
      message.error(error?.response?.data?.message || '上传凭证失败')
    }
  }

  const handleSubmit = async () => {
    if (!canSubmitNow) {
      message.warning('当前提交窗口未开放，暂不能保存申请')
      return
    }
    if (!validateForm()) {
      return
    }

    setSubmitting(true)
    try {
      const pendingUploads = [...formState.proofImageList, ...formState.certFileList].filter(
        (item) => item.originFileObj && !item.url,
      )
      if (pendingUploads.length) {
        message.warning('凭证文件仍在上传，请稍后再保存')
        return
      }
      const certFiles = formState.certFileList.filter((item) => item.url || item.name)
      const proofPaths = formState.proofImageList
        .map((item) => item.url || item.name)
        .filter(Boolean)

      const payload = {
        scoreType,
        templateId: currentTemplate.id,
        itemId: Number(formState.itemId),
        levelRuleId: Number(formState.levelRuleId),
        authorRuleId: Number(formState.authorRuleId),
        certFileName: certFiles[0]?.name || '',
        certFileUrl: certFiles[0]?.url || '',
        certFileNames: certFiles.map((item) => item.name || '').filter(Boolean),
        certFileUrls: certFiles.map((item) => item.url || '').filter(Boolean),
        certName: formState.certName.trim(),
        detailDesc: formState.detailDesc.trim(),
        awardTime: formState.awardTime,
        proofText: formState.proofText.trim(),
        proofImages: proofPaths,
      }

      if (editingRecord) {
        await updateExtraScoreApplication(editingRecord.id, payload)
        message.success('申请已保存，请到审核流程页确认提交')
      } else {
        await createExtraScoreApplication(payload)
        message.success('申请已保存，请到审核流程页确认提交')
      }
      closeFormPage()
      if (!formReturnPath) {
        fetchData()
      }
    } catch (error) {
      message.error(error?.response?.data?.message || '提交申请失败')
    } finally {
      setSubmitting(false)
    }
  }

  const handleDelete = async (record) => {
    const confirmed = await confirmAction({
      title: '确认删除',
      message: '确定删除这条附加分申请吗？',
      detail: '删除后不可恢复。',
      confirmText: '确认删除',
      tone: 'danger',
    })
    if (!confirmed) return

    try {
      await deleteExtraScoreApplication(record.id)
      message.success('删除成功')
      fetchData()
    } catch (error) {
      message.error(error?.response?.data?.message || '删除失败')
    }
  }

  const handleOpenDetail = async (record) => {
    setDetailRecord(record)
    setDetailDrawerOpen(true)
    setDetailLoading(true)
    try {
      const response = await getExtraScoreApplicationDetail(record.id)
      setDetailRecord(response?.data || record)
    } catch (error) {
      message.error(error?.response?.data?.message || '获取申请详情失败')
    } finally {
      setDetailLoading(false)
    }
  }

  const closeDetailDrawer = () => {
    setDetailDrawerOpen(false)
    setDetailRecord(null)
    setDetailLoading(false)
  }

  const handleDetailPrimaryAction = () => {
    if (!detailRecord) {
      return
    }
    if (canEditExtraScoreRecord(detailRecord, canSubmitNow)) {
      closeDetailDrawer()
      openEditPage(detailRecord)
      return
    }
    if (detailRecord.status === 'pending') {
      closeDetailDrawer()
      navigate('/student/extra/process')
    }
  }

  const renderDetailMedia = (record) => {
    const imageUrls = getRecordDetailMediaUrls(record)
    if (!imageUrls.length) {
      return <Text type="secondary">暂无图片材料</Text>
    }
    return (
      <div className="extra-score-detail-media">
        <Image.PreviewGroup items={imageUrls}>
          {imageUrls.map((url, index) => (
            <Image
              key={`${url}-${index}`}
              width={72}
              height={72}
              src={withAuthToken(url)}
              alt={`材料${index + 1}`}
              className="extra-score-detail-thumb"
              fallback=""
            />
          ))}
        </Image.PreviewGroup>
      </div>
    )
  }

  const renderPagination = () =>
    pagination.total > pagination.size ? (
      <div className="extra-score-pagination">
        <Pagination
          current={pagination.page}
          pageSize={pagination.size}
          total={pagination.total}
          showSizeChanger
          showTotal={(total) => `共 ${total} 条`}
          onChange={(page, size) => {
            setPagination((prev) => ({
              ...prev,
              page,
              size,
            }))
          }}
        />
      </div>
    ) : null

  const renderRecordCard = (record) => {
    const status = getStatusMeta(record)
    const canEditRecord = canEditExtraScoreRecord(record, canSubmitNow)
    const canDeleteRecord = canDeleteExtraScoreRecord(record, canSubmitNow)
    const canSupplementRecord = canSupplementExtraScoreRecord(record, canSubmitNow)
    const previewImages = getRecordCertificatePreviews(record)
    const previewUrl = previewImages[0] || getRecordCertificatePreview(record)
    const previewIsImage =
      Boolean(previewUrl) && isImageUploadFile({ url: previewUrl, name: record.certFileName })
    const scoreSign = String(record.scoreDirection || 'add').toLowerCase() === 'deduct' ? '-' : '+'
    const pendingHint =
      record.reviewStage === 'committee_pending'
        ? '班委初审中'
        : record.reviewStage === 'teacher_pending'
          ? '教师审核中'
          : '审核中'
    const recordResultClass =
      record.recordReviewResult === 'rejected'
        ? 'rejected'
        : record.recordReviewResult === 'approved'
          ? 'approved'
          : record.recordReviewResult === 'committee_approved'
            ? 'pending'
            : 'default'
    const shouldShowRecordReviewResult =
      Boolean(record.recordReviewResultLabel) &&
      record.recordReviewResultLabel !== '-' &&
      record.recordReviewResultLabel !== status.label &&
      !['draft', 'pending'].includes(record.recordReviewResult)
    const primaryActionLabel = canSupplementRecord
      ? '补充材料'
      : record.status === 'draft'
        ? '继续填写'
        : '修改申请'

    return (
      <div key={record.id} className="extra-score-record-card">
        <div className="extra-score-record-leading">
          <div className="extra-score-record-thumb">
            {previewIsImage ? (
              <Image.PreviewGroup items={previewImages}>
                <Image
                  src={withAuthToken(previewUrl)}
                  alt={record.certName || '证书缩略图'}
                  onClick={(event) => event.stopPropagation()}
                />
                {previewImages.length > 1 ? (
                  <span className="extra-score-thumb-count">{previewImages.length} 张</span>
                ) : null}
              </Image.PreviewGroup>
            ) : (
              <FileTextOutlined />
            )}
          </div>
          <div className="extra-score-record-main compact">
            <div className="extra-score-record-row compact">
              <div className="extra-score-record-title-block">
                <div className="extra-score-record-title compact">
                  {record.certName || record.itemName || '未命名申请'}
                </div>
                <div className="extra-score-record-subtitle compact">
                  {record.groupName || '未分类'} / {record.itemName || '未命名小类'}
                </div>
              </div>

              <div className="extra-score-record-score">
                <strong>
                  {scoreSign}
                  {formatNumber(record.expectedScore, 2)} 分
                </strong>
                <span>
                  {record.levelName || '未设置级别'} · {record.authorLabel || '未设置作者'}
                </span>
              </div>

              <div className="extra-score-record-status-block">
                <div className="extra-score-record-status-row">
                  <span className={`extra-score-status-pill ${status.className}`}>
                    {status.label}
                  </span>
                  {record.packageReturned ? (
                    <span className="extra-score-status-pill returned">整包退回</span>
                  ) : null}
                  {shouldShowRecordReviewResult ? (
                    <span className={`extra-score-status-pill ${recordResultClass}`}>
                      {record.recordReviewResultLabel}
                    </span>
                  ) : null}
                </div>
                <div className="extra-score-record-period">
                  {getPeriodLabel(record)}
                  {record.status === 'pending' ? ` · ${pendingHint}` : ''}
                </div>
                {record.withdrawalStatus === 'pending' ? (
                  <div className="extra-score-record-hint warning">撤回待班委处理</div>
                ) : null}
              </div>

              <div className="extra-score-record-actions compact">
                {canEditRecord ? (
                  <Button
                    size="small"
                    type="primary"
                    onClick={() => openEditPage(record)}
                    icon={<EditOutlined />}
                  >
                    {primaryActionLabel}
                  </Button>
                ) : (
                  <Button
                    size="small"
                    onClick={() => handleOpenDetail(record)}
                    icon={<FileTextOutlined />}
                  >
                    查看详情
                  </Button>
                )}
                {canEditRecord ? (
                  <Button type="text" size="small" onClick={() => handleOpenDetail(record)}>
                    详情
                  </Button>
                ) : null}
                {canDeleteRecord ? (
                  <Dropdown
                    trigger={['click']}
                    placement="bottomRight"
                    menu={{
                      items: [
                        {
                          key: 'delete',
                          danger: true,
                          icon: <DeleteOutlined />,
                          label: '删除申请',
                        },
                      ],
                      onClick: ({ key }) => {
                        if (key === 'delete') {
                          handleDelete(record)
                        }
                      },
                    }}
                  >
                    <Button type="text" size="small">
                      更多
                    </Button>
                  </Dropdown>
                ) : null}
              </div>
            </div>

            {record.returnReason ? (
              <div className="extra-score-record-reason compact">
                {record.recordReviewResult === 'approved' ? '整包退回说明' : '单条问题'}：
                {record.returnReason}
              </div>
            ) : null}
          </div>
        </div>
      </div>
    )
  }

  const renderClosedState = () => (
    <div className="extra-score-closed-wrap">
      <Card className="extra-score-closed-card">
        <div className="extra-score-closed-head">
          <div className="extra-score-closed-copy">
            <div className="extra-score-closed-eyebrow">当前状态</div>
            <Title level={2} className="extra-score-closed-title">
              暂未开放申请
            </Title>
            <Text className="extra-score-closed-desc">
              当前不能新建或提交附加分。你可以先查看提交周期、已保存记录和模板情况，开放后再按规则填写申请。
            </Text>
          </div>
          <div className={`extra-score-window-status ${windowState.className}`}>
            <CheckCircleOutlined />
            <span>{windowState.label}</span>
          </div>
        </div>

        <div className="extra-score-closed-meta">
          <div className="extra-score-closed-meta-item">
            <span>提交周期</span>
            <strong>{submissionPeriodText}</strong>
          </div>
          <div className="extra-score-closed-meta-item">
            <span>当前模板</span>
            <strong>{currentTemplate?.name || '暂未配置'}</strong>
          </div>
          <div className="extra-score-closed-meta-item">
            <span>已有记录</span>
            <strong>{pagination.total || records.length} 条</strong>
          </div>
        </div>

        <div className="extra-score-closed-actions">
          <Button
            className="extra-score-header-button"
            onClick={openHistoryModal}
            icon={<CalendarOutlined />}
          >
            查看提交周期历史
          </Button>
          <Text type="secondary">右上角“新建申请”可点击，若暂不能申请会提示具体原因。</Text>
        </div>
      </Card>

      <Card className="extra-score-panel extra-score-closed-records">
        <div className="extra-score-records-header">
          <Title level={3} style={{ margin: 0 }}>
            历史申请记录
          </Title>
          <Text type="secondary">提交窗口关闭期间仅支持查看。</Text>
        </div>
        {records.length ? (
          <>
            <div className="extra-score-record-list">
              {records.map((record) => renderRecordCard(record))}
            </div>
            {renderPagination()}
          </>
        ) : (
          <div className="extra-score-closed-empty">
            <Empty description="暂无历史申请记录" />
          </div>
        )}
      </Card>
    </div>
  )

  const renderFormPage = () => (
    <div className="extra-score-form-page">
      <Card className="extra-score-form-card">
        {originalRecordRuleGroup ? (
          <div
            className={`extra-score-form-hint ${originalRecordRuleUnavailable ? 'warning' : 'soft'}`}
          >
            <InfoCircleOutlined />
            <span>
              {originalRecordRuleUnavailable
                ? '原申请的小类已不在当前模板中，已临时回显；保存前请改选当前模板中的小类、级别和作者规则。'
                : '原申请的小类当前已隐藏，已放在“原申请记录”分组中便于查看和修改。'}
            </span>
          </div>
        ) : null}

        <div className="extra-score-form-sections">
          <section className="extra-score-form-section">
            <div className="extra-score-form-section-heading">
              <h2>申请项目</h2>
            </div>
            <div className="extra-score-form-grid project">
              <div className="extra-score-form-field extra-score-form-field-full">
                <label>
                  <RequiredLabel>加分项目</RequiredLabel>
                </label>
                <Select
                  className="extra-score-item-select"
                  value={formState.itemId ? String(formState.itemId) : undefined}
                  options={itemSelectOptions}
                  placeholder="请选择或搜索加分项目"
                  showSearch
                  allowClear
                  filterOption={false}
                  loading={itemSearchLoading}
                  onSearch={setItemSearchKeyword}
                  notFoundContent={
                    itemSearchLoading ? (
                      <div className="extra-score-item-search-state">正在搜索...</div>
                    ) : (
                      <Empty
                        image={Empty.PRESENTED_IMAGE_SIMPLE}
                        description={itemSearchFailed ? '搜索失败，请重试' : '未找到匹配项目'}
                      />
                    )
                  }
                  onChange={(value) => {
                    const nextItemId = value || null
                    setItemSearchKeyword('')
                    setSearchedTemplateGroups(null)
                    setFormState((prev) => ({
                      ...prev,
                      itemId: nextItemId,
                      levelRuleId: null,
                      authorRuleId: null,
                    }))
                  }}
                />
              </div>

              <div className="extra-score-form-field">
                <label>
                  <RequiredLabel>获奖级别</RequiredLabel>
                </label>
                <select
                  value={formState.levelRuleId || ''}
                  onChange={(event) =>
                    setFormState((prev) => ({
                      ...prev,
                      levelRuleId: event.target.value || null,
                      authorRuleId: null,
                    }))
                  }
                  disabled={!selectedItem}
                >
                  <option value="">请选择获奖级别</option>
                  {levelOptions.map((rule) => (
                    <option key={rule.id} value={rule.id}>
                      {rule.levelName}
                    </option>
                  ))}
                </select>
              </div>

              <div className="extra-score-form-field">
                <label>
                  <RequiredLabel>参与身份</RequiredLabel>
                </label>
                <select
                  value={formState.authorRuleId || ''}
                  onChange={(event) =>
                    setFormState((prev) => ({ ...prev, authorRuleId: event.target.value || null }))
                  }
                  disabled={!selectedLevelRule}
                >
                  <option value="">请选择参与身份</option>
                  {authorOptions.map((rule) => (
                    <option key={rule.id} value={rule.id}>
                      {rule.authorLabel} /{' '}
                      {(rule.mode || 'ratio') === 'fixed'
                        ? `${rule.fixedScore || 0} 分`
                        : `${rule.ratio || 0}%`}
                    </option>
                  ))}
                </select>
              </div>

              <div className="extra-score-form-score">
                <div className="extra-score-form-score-title">自动分值</div>
                <div className="extra-score-form-score-value">
                  {calculatedScore !== null
                    ? `${selectedDirection === 'deduct' ? '-' : '+'}${formatNumber(calculatedScore, 2)} 分`
                    : '--'}
                </div>
                <div className="extra-score-form-score-desc">{calculatedScoreDescription}</div>
              </div>
            </div>
          </section>

          <section className="extra-score-form-section">
            <div className="extra-score-form-section-heading">
              <h2>申请信息</h2>
            </div>
            <div className="extra-score-form-grid information">
              <div className="extra-score-form-field">
                <label>
                  <RequiredLabel>证书或项目名称</RequiredLabel>
                </label>
                <Input
                  value={formState.certName}
                  onChange={(event) =>
                    setFormState((prev) => ({ ...prev, certName: event.target.value }))
                  }
                  placeholder="例如：全国大学生数学建模竞赛二等奖"
                />
              </div>

              <div className="extra-score-form-field">
                <label>
                  <RequiredLabel>获得日期</RequiredLabel>
                </label>
                <Input
                  type="date"
                  value={formState.awardTime}
                  onChange={(event) =>
                    setFormState((prev) => ({ ...prev, awardTime: event.target.value }))
                  }
                />
              </div>

              <div className="extra-score-form-field extra-score-form-field-full">
                <label>申请说明（选填）</label>
                <TextArea
                  rows={3}
                  value={formState.detailDesc}
                  onChange={(event) =>
                    setFormState((prev) => ({ ...prev, detailDesc: event.target.value }))
                  }
                  placeholder="补充说明获奖、竞赛或项目情况"
                />
              </div>
            </div>
          </section>

          <section className="extra-score-form-section materials">
            <div className="extra-score-form-section-heading">
              <h2>证明材料</h2>
            </div>
            <div className="extra-score-form-material-grid">
              <div className="extra-score-form-field">
                <label>
                  <RequiredLabel>证书文件</RequiredLabel>
                </label>
                <Upload
                  listType="picture-card"
                  accept="image/*,video/*"
                  beforeUpload={() => false}
                  fileList={formState.certFileList}
                  onChange={({ fileList }) => handleProofUploadChange('certFileList', fileList)}
                  multiple
                  className="extra-score-proof-upload"
                  itemRender={renderUploadItem}
                >
                  <div className="extra-score-upload-card">
                    <UploadOutlined />
                    <span>添加文件</span>
                  </div>
                </Upload>
              </div>

              <div className="extra-score-form-field">
                <label>其他证明材料（选填）</label>
                <Upload
                  listType="picture-card"
                  accept="image/*,video/*"
                  beforeUpload={() => false}
                  fileList={formState.proofImageList}
                  onChange={({ fileList }) => handleProofUploadChange('proofImageList', fileList)}
                  multiple
                  className="extra-score-proof-upload"
                  itemRender={renderUploadItem}
                >
                  <div className="extra-score-upload-card">
                    <UploadOutlined />
                    <span>添加文件</span>
                  </div>
                </Upload>
              </div>

              <div className="extra-score-form-field extra-score-form-field-full">
                <label>材料补充说明（选填）</label>
                <TextArea
                  rows={2}
                  value={formState.proofText}
                  onChange={(event) =>
                    setFormState((prev) => ({ ...prev, proofText: event.target.value }))
                  }
                  placeholder="仅在材料需要补充说明时填写"
                />
              </div>
            </div>
          </section>
        </div>

        <div className="extra-score-form-actions">
          <div className="extra-score-form-save-state">
            <span />
            <div>
              <strong>保存后仍为草稿</strong>
              <small>请在审核流程中确认提交</small>
            </div>
          </div>
          <div className="extra-score-form-action-buttons">
            <Button onClick={closeFormPage}>取消</Button>
            <Button type="primary" loading={submitting} onClick={handleSubmit}>
              {editingRecord ? '保存修改' : '保存申请'}
            </Button>
          </div>
        </div>
      </Card>
    </div>
  )

  return (
    <StudentLayout activePath={activePath}>
      <div className="extra-score-shell" style={{ '--extra-score-accent': theme.accent }}>
        {isFormPage ? (
          <div className="extra-score-form-page-header">
            <Button
              type="text"
              className="extra-score-form-back"
              icon={<RollbackOutlined />}
              onClick={closeFormPage}
            >
              {formReturnPath === '/student/extra/history' ? '返回申报历史' : '返回申请列表'}
            </Button>
            <div className="extra-score-form-page-title">
              <Title level={1} className="extra-score-title">
                {isEditPage ? '编辑' : '新建'}
                {theme.title}
              </Title>
              <span className={`extra-score-status-pill ${formStatus.className}`}>
                {formStatus.label}
              </span>
            </div>
          </div>
        ) : (
          <div className="extra-score-header">
            <div className="extra-score-title-copy">
              <Title level={1} className="extra-score-title">
                附加分申请
              </Title>
            </div>
            <Space size={12} wrap className="extra-score-header-actions">
              <Segmented
                className="extra-score-mode-switch"
                value={scoreType}
                options={SCORE_TYPE_OPTIONS}
                onChange={(value) => {
                  navigate(`${activePath}?type=${value}`)
                }}
              />
              <Button
                type="primary"
                className="extra-score-header-button primary"
                onClick={openCreatePage}
                icon={<PlusCircleOutlined />}
              >
                新建申请
              </Button>
            </Space>
          </div>
        )}

        {!isFormPage ? (
          loading && !dataReady && !records.length ? (
            <Card className="extra-score-page-loading">
              <Skeleton active paragraph={{ rows: 5 }} />
            </Card>
          ) : canSubmitNow ? (
            <>
              <div
                className={`extra-score-window-banner ${
                  remainingDays !== null && remainingDays <= 2 ? 'urgent' : ''
                }`}
              >
                <div className="extra-score-window-main">
                  <div className="extra-score-window-icon">
                    <CalendarOutlined style={{ fontSize: 22, color: theme.accent }} />
                  </div>
                  <div className="extra-score-window-copy">
                    <div className="extra-score-window-title">{deadlineLabel}</div>
                    <div className="extra-score-window-range">提交窗口：{submissionPeriodText}</div>
                  </div>
                </div>
                <div className="extra-score-window-actions">
                  <Button type="text" onClick={openHistoryModal} icon={<CalendarOutlined />}>
                    周期记录
                  </Button>
                </div>
              </div>

              <div className="extra-score-content-grid single">
                <div className="extra-score-main-column">
                  <Card className="extra-score-panel">
                    <div className="extra-score-records-header">
                      <div>
                        <Title level={3} style={{ margin: 0 }}>
                          申请记录
                        </Title>
                        <Text type="secondary">共 {pagination.total} 条</Text>
                      </div>
                      {showRecordTools ? (
                        <div className="extra-score-record-controls">
                          <Select
                            value={statusFilter}
                            onChange={setStatusFilter}
                            style={{ width: 130 }}
                            options={[
                              { value: 'all', label: '全部状态' },
                              { value: 'draft', label: '草稿' },
                              { value: 'pending', label: '审核中' },
                              { value: 'approved', label: '已通过' },
                              { value: 'returned', label: '已打回' },
                              { value: 'rejected', label: '已拒绝' },
                            ]}
                          />
                          <Input
                            value={keyword}
                            onChange={(event) => setKeyword(event.target.value)}
                            placeholder="搜索申请"
                            prefix={<SearchOutlined />}
                            style={{ width: 210 }}
                          />
                          <Select
                            value={sortMode}
                            onChange={setSortMode}
                            style={{ width: 150 }}
                            options={[
                              { value: 'upload-desc', label: '最近提交' },
                              { value: 'award-desc', label: '获奖时间：新到旧' },
                              { value: 'award-asc', label: '获奖时间：旧到新' },
                              { value: 'score-desc', label: '分值：高到低' },
                              { value: 'score-asc', label: '分值：低到高' },
                            ]}
                          />
                        </div>
                      ) : null}
                    </div>

                    {statusFilter === 'all' ? (
                      <div
                        className={`extra-score-task-strip ${draftCount > 0 ? 'attention' : 'clear'}`}
                      >
                        <div>
                          <strong>
                            {draftCount > 0
                              ? `需要处理 ${draftCount} 条申请`
                              : '当前没有需要处理的申请'}
                          </strong>
                          {draftCount > 0 ? (
                            <span>
                              {returnedCount > 0
                                ? `其中 ${returnedCount} 条被退回，请修改后重新提交。`
                                : '草稿或需要修改的记录尚未提交。'}
                            </span>
                          ) : (
                            <span>已提交的申请会在这里持续更新审核状态。</span>
                          )}
                        </div>
                      </div>
                    ) : null}

                    {!filteredRecords.length ? (
                      <Empty description="当前筛选下暂无申请" />
                    ) : (
                      <div className="extra-score-flat-list">
                        {actionableRecords.length ? (
                          <section className="extra-score-list-section actionable">
                            {statusFilter !== 'all' ? (
                              <div className="extra-score-list-section-heading">
                                <strong>需要处理</strong>
                                <span>{actionableRecords.length} 条</span>
                              </div>
                            ) : null}
                            <div className="extra-score-record-list">
                              {actionableRecords.map((record) => renderRecordCard(record))}
                            </div>
                          </section>
                        ) : null}

                        {otherRecords.length ? (
                          <section className="extra-score-list-section">
                            {actionableRecords.length ? (
                              <div className="extra-score-list-section-heading">
                                <strong>其他申请</strong>
                                <span>{otherRecords.length} 条</span>
                              </div>
                            ) : null}
                            <div className="extra-score-record-list">
                              {otherRecords.map((record) => renderRecordCard(record))}
                            </div>
                          </section>
                        ) : null}
                      </div>
                    )}
                    {renderPagination()}
                  </Card>
                </div>
              </div>
            </>
          ) : (
            renderClosedState()
          )
        ) : (
          renderFormPage()
        )}
      </div>

      <Modal
        title="提交周期"
        open={historyModalOpen}
        onCancel={() => setHistoryModalOpen(false)}
        footer={null}
        width={760}
        className="extra-score-history-modal"
      >
        {historyLoading ? (
          <div className="extra-score-history-loading">
            <Skeleton active paragraph={{ rows: 6 }} />
          </div>
        ) : !submissionWindowHistory.length ? (
          <Empty description="当前学年暂无提交周期记录" />
        ) : (
          <div className="extra-score-history-content">
            <div className="extra-score-history-summary">
              <div>
                <strong>{submissionHistoryCounts.all} 个周期</strong>
                <span>{submissionHistoryClassNames.join('、') || '当前班级'}</span>
              </div>
              <Segmented
                size="small"
                value={historyStageFilter}
                onChange={setHistoryStageFilter}
                options={[
                  { label: `全部 ${submissionHistoryCounts.all}`, value: 'all' },
                  { label: `未结束 ${submissionHistoryCounts.active}`, value: 'active' },
                  { label: `已结束 ${submissionHistoryCounts.closed}`, value: 'closed' },
                ]}
              />
            </div>

            {groupedSubmissionHistory.length ? (
              <div className="extra-score-history-list">
                {groupedSubmissionHistory.map((group) => (
                  <section key={group.academicYear} className="extra-score-history-group">
                    <div className="extra-score-history-group-head">
                      <strong>{group.academicYear}</strong>
                      <span>{group.records.length} 个周期</span>
                    </div>
                    <div className="extra-score-history-rows">
                      {group.records.map((record) => (
                        <div
                          key={record.batchCode || record.id}
                          className={`extra-score-history-row ${record.windowStage.className}`}
                        >
                          <span className="extra-score-history-marker" aria-hidden="true" />
                          <div className="extra-score-history-main">
                            <strong>{record.periodLabel || '提交周期'}</strong>
                            <span>
                              {formatDateTime(record.startTime)} 至 {formatDateTime(record.endTime)}
                            </span>
                          </div>
                          <span
                            className={`extra-score-status-pill ${record.windowStage.className}`}
                          >
                            {record.windowStage.label}
                          </span>
                        </div>
                      ))}
                    </div>
                  </section>
                ))}
              </div>
            ) : (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无符合条件的周期" />
            )}
          </div>
        )}
      </Modal>

      <Drawer
        title={
          <div className="extra-score-detail-title">
            <span>申请详情</span>
            {detailStatus ? (
              <span className={`extra-score-status-pill ${detailStatus.className}`}>
                {detailStatus.label}
              </span>
            ) : null}
          </div>
        }
        open={detailDrawerOpen}
        onClose={closeDetailDrawer}
        placement="right"
        width={620}
        className="extra-score-detail-drawer"
        destroyOnHidden
        footer={
          <div className="extra-score-detail-footer">
            <Button onClick={closeDetailDrawer}>关闭</Button>
            {detailCanEdit || detailCanViewProgress ? (
              <Button type="primary" onClick={handleDetailPrimaryAction}>
                {detailCanEdit ? detailPrimaryLabel : '查看审核进度'}
              </Button>
            ) : null}
          </div>
        }
      >
        {detailLoading ? (
          <Skeleton active paragraph={{ rows: 8 }} />
        ) : detailRecord ? (
          <div className="extra-score-detail-content">
            <div className="extra-score-detail-summary">
              <div>
                <h2>{detailRecord.certName || detailRecord.itemName || '未命名申请'}</h2>
                <p>
                  {detailRecord.groupName || '未分类'} / {detailRecord.itemName || '未命名项目'}
                </p>
              </div>
              <strong>
                {String(detailRecord.scoreDirection || 'add').toLowerCase() === 'deduct'
                  ? '-'
                  : '+'}
                {formatNumber(detailRecord.expectedScore, 2)} 分
              </strong>
            </div>

            <section className="extra-score-detail-section">
              <h3>申请信息</h3>
              <dl className="extra-score-detail-grid">
                <div>
                  <dt>获奖级别</dt>
                  <dd>{detailRecord.levelName || '-'}</dd>
                </div>
                <div>
                  <dt>参与身份</dt>
                  <dd>{detailRecord.authorLabel || '-'}</dd>
                </div>
                <div>
                  <dt>获得日期</dt>
                  <dd>{detailRecord.awardTime || '-'}</dd>
                </div>
                <div>
                  <dt>所属周期</dt>
                  <dd>{getPeriodLabel(detailRecord)}</dd>
                </div>
                <div className="full">
                  <dt>最近更新</dt>
                  <dd>{formatDateTime(detailRecord.uploadTime)}</dd>
                </div>
              </dl>
            </section>

            {detailRecord.detailDesc ? (
              <section className="extra-score-detail-section">
                <h3>申请说明</h3>
                <p className="extra-score-detail-text">{detailRecord.detailDesc}</p>
              </section>
            ) : null}

            {detailRecord.proofText ? (
              <section className="extra-score-detail-section">
                <h3>材料补充说明</h3>
                <p className="extra-score-detail-text">{detailRecord.proofText}</p>
              </section>
            ) : null}

            {detailRecord.returnReason ? (
              <section className="extra-score-detail-section feedback">
                <h3>审核反馈</h3>
                <p className="extra-score-detail-text">{detailRecord.returnReason}</p>
              </section>
            ) : null}

            {detailMediaCount ? (
              <section className="extra-score-detail-section">
                <h3>证明材料 · {detailMediaCount}</h3>
                {renderDetailMedia(detailRecord)}
              </section>
            ) : null}
          </div>
        ) : (
          <Empty description="暂无详情" />
        )}
      </Drawer>
    </StudentLayout>
  )
}

export default StudentExtraScorePage
