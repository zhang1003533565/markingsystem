import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import {
  Button,
  Card,
  DatePicker,
  Descriptions,
  Drawer,
  Empty,
  Image,
  Input,
  Modal,
  Spin,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
  message,
} from 'antd'
import {
  BulbOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  CloseCircleOutlined,
  DeleteOutlined,
  DownloadOutlined,
  EyeOutlined,
  FileTextOutlined,
  ReloadOutlined,
  RollbackOutlined,
  WarningOutlined,
} from './AcademicIcons'
import { getStoredUser, withAuthToken } from '../utils/authStorage'
import {
  aiAssistExtraScoreApplication,
  batchApproveExtraScoreApplications,
  committeeReviewExtraScoreApplication,
  deleteExtraScoreTeacherImportRecord,
  exportExtraScoreProofImages,
  forceCurrentClassToTeacherStage,
  getExtraScoreReviewClassList,
  getExtraScoreReviewWorkbench,
  getExtraScoreReviewClassStudents,
  getExtraScoreReviewApplicationDetail,
  getExtraScoreReviewStudentApplications,
  reviewExtraScoreWithdrawal,
  teacherReviewExtraScoreApplication,
} from '../api/extraScoreReview'
import PageHeader from './PageHeader'
import {
  canHandleWithdrawalReview,
  getWithdrawalReviewHint,
  isWithdrawalPending,
} from '../utils/extraScoreReviewRowState'
import ReviewWorkbenchOverview from './extra-score-review/ReviewWorkbenchOverview'
import { confirmAction } from './confirmDialogService'
import {
  filterReviewWorkbenchClasses,
  getReviewStatusForWorkbenchView,
  summarizeReviewWorkbenchClasses,
} from '../utils/extraScoreReviewWorkbench'
import './ExtraScoreReviewPanel.css'

const { Text } = Typography
const { TextArea } = Input

const scoreTypeOptions = [
  { value: 'all', label: '全部类型' },
  { value: 'MORAL', label: '德育附加分' },
  { value: 'INTELLECTUAL', label: '智育附加分' },
  { value: 'PHYSICAL', label: '体育附加分' },
]

const statusOptions = [
  { value: 'all', label: '全部状态' },
  { value: 'pending', label: '待审核' },
  { value: 'approved', label: '已通过' },
  { value: 'returned', label: '已驳回' },
]

const normalizeOptionValue = (value, options, fallback) => {
  const normalized = String(value || '').trim()
  return options.some((option) => option.value === normalized) ? normalized : fallback
}
const readClassFilterFromParams = (params) =>
  String(params.get('classId') || params.get('className') || '').trim()
const shouldOpenClassFromParams = (params) =>
  ['1', 'true', 'yes'].includes(String(params.get('openClass') || '').toLowerCase())
const normalizeScoreDirection = (value) =>
  String(value || 'add').toLowerCase() === 'deduct' ? 'deduct' : 'add'
const formatSignedScore = (value, direction) =>
  `${normalizeScoreDirection(direction) === 'deduct' ? '-' : '+'}${Number(value || 0).toFixed(1)} 分`
const formatDateTime = (value) => (value ? String(value).replace('T', ' ').slice(0, 16) : '-')
const getPeriodLabel = (record) => record?.submissionPeriodLabel || '提交周期'
const parseAiSuggestion = (json) => {
  if (!json) return null
  try {
    return JSON.parse(json)
  } catch {
    return { raw: String(json), confidence: '低' }
  }
}
const getProofImageUrl = (value) =>
  String(value || '').startsWith('/uploads/') ? `/api${value}` : value
const isImageUrl = (value) => /\.(png|jpe?g|webp|gif|bmp|svg)(\?.*)?$/i.test(String(value || ''))
const toStoredList = (value) => {
  if (Array.isArray(value)) {
    return value.filter(Boolean)
  }
  const text = String(value || '')
  return text ? text.split('||').filter(Boolean) : []
}
const getCertificateMediaList = (record) => {
  const urls = toStoredList(record?.thumbnailUrls).length
    ? toStoredList(record.thumbnailUrls)
    : [...toStoredList(record?.certFileUrls), ...toStoredList(record?.certFileUrl)]
  const names = toStoredList(record?.certFileNames).length
    ? toStoredList(record.certFileNames)
    : toStoredList(record?.certFileName)
  return Array.from(new Set(urls.filter(Boolean))).map((url, index) => ({
    url,
    type: isImageUrl(url) ? 'image' : 'file',
    name: names[index] || record?.certName || `证书${index + 1}`,
  }))
}
const getProofMediaList = (record) => {
  const certificates = getCertificateMediaList(record)
  const proofMedia =
    Array.isArray(record?.proofMedia) && record.proofMedia.length
      ? record.proofMedia.filter((item) => item?.url)
      : (Array.isArray(record?.proofImages) ? record.proofImages : [])
          .filter(Boolean)
          .map((url) => {
            const text = String(url)
            return {
              url,
              type: /\.(mp4|webm|mov|m4v|avi)$/i.test(text) ? 'video' : 'image',
              name: text.split('/').pop() || '凭证',
            }
          })
  return [...certificates, ...proofMedia]
}
const renderCertificateThumbs = (record) => {
  const media = getCertificateMediaList(record)
  if (!media.length) {
    return <Text type="secondary">暂无证书</Text>
  }
  const imageItems = media.map((item) => getProofImageUrl(item.url)).filter(isImageUrl)
  return (
    <div className="review-proof-thumbs">
      <Image.PreviewGroup items={imageItems}>
        {media.slice(0, 3).map((item, index) => {
          const url = getProofImageUrl(item.url)
          return item.type !== 'image' ? (
            <a
              key={`${item.url}-${index}`}
              href={url}
              target="_blank"
              rel="noreferrer"
              className="review-proof-video"
            >
              文件{index + 1}
            </a>
          ) : (
            <Image
              key={`${item.url}-${index}`}
              width={44}
              height={44}
              src={withAuthToken(url)}
              alt={item.name || `证书${index + 1}`}
              className="review-proof-thumb"
              fallback=""
            />
          )
        })}
      </Image.PreviewGroup>
      {media.length > 3 ? <Tag>+{media.length - 3}</Tag> : null}
      {media.length > 1 ? <Tag color="blue">{media.length}张</Tag> : null}
    </div>
  )
}
const renderMediaThumbs = (record) => {
  const media = getProofMediaList(record)
  if (!media.length) {
    return <Text type="secondary">暂无材料</Text>
  }
  const imageItems = media
    .filter((item) => item.type === 'image')
    .map((item) => getProofImageUrl(item.url))
    .filter(isImageUrl)
  return (
    <div className="review-proof-thumbs">
      <Image.PreviewGroup items={imageItems}>
        {media.map((item, index) => {
          const url = getProofImageUrl(item.url)
          return item.type === 'video' || item.type === 'file' ? (
            <a
              key={`${item.url}-${index}`}
              href={url}
              target="_blank"
              rel="noreferrer"
              className="review-proof-video"
            >
              {item.type === 'video' ? '视频' : '文件'}
              {index + 1}
            </a>
          ) : (
            <Image
              key={`${item.url}-${index}`}
              width={52}
              height={52}
              src={withAuthToken(url)}
              alt={item.name || `材料${index + 1}`}
              className="review-proof-thumb"
              fallback=""
            />
          )
        })}
      </Image.PreviewGroup>
    </div>
  )
}

function ExtraScoreReviewPanel({
  title,
  description,
  showForceTeacherStageAction = true,
  showPageHeader = true,
  workbenchMode = false,
} = {}) {
  const [searchParams, setSearchParams] = useSearchParams()
  const searchParamKey = searchParams.toString()
  const queryReviewFilters = useMemo(() => {
    const params = new URLSearchParams(searchParamKey)
    const className = readClassFilterFromParams(params)
    return {
      scoreType: normalizeOptionValue(params.get('scoreType'), scoreTypeOptions, 'all'),
      status: normalizeOptionValue(params.get('status'), statusOptions, 'all'),
      className,
      selectedClass: shouldOpenClassFromParams(params) && className ? className : null,
      studentNo: String(params.get('studentNo') || '').trim(),
    }
  }, [searchParamKey])
  const user = getStoredUser()
  const isCommittee = user.role === 'CLASS_COMMITTEE'
  const isTeacher = user.role === 'TEACHER'
  const isAdmin = user.role === 'ADMIN'
  const isWorkbench = workbenchMode && (isTeacher || isAdmin)

  // Filter states
  const [scoreType, setScoreType] = useState(queryReviewFilters.scoreType)
  const [statusFilter, setStatusFilter] = useState(queryReviewFilters.status)
  const [classFilter, setClassFilter] = useState(queryReviewFilters.className || 'all')
  const [academicYearFilter, setAcademicYearFilter] = useState('all')
  const [dateRange, setDateRange] = useState(null)
  const [searchText, setSearchText] = useState('')

  const [records, setRecords] = useState([])
  const [classGroups, setClassGroups] = useState([])
  const [workbenchData, setWorkbenchData] = useState({
    context: {},
    summary: {},
    filters: { academicYears: [], classes: [] },
    classes: [],
  })
  const [loadingWorkbench, setLoadingWorkbench] = useState(isWorkbench)
  const [workbenchLoaded, setWorkbenchLoaded] = useState(false)
  const [workbenchView, setWorkbenchView] = useState('all')
  const [classStudents, setClassStudents] = useState([])
  const [classStudentsPagination, setClassStudentsPagination] = useState({
    current: 1,
    pageSize: 10,
    total: 0,
  })
  const [loadingClassStudents, setLoadingClassStudents] = useState(
    Boolean(queryReviewFilters.selectedClass),
  )
  const [studentApplications, setStudentApplications] = useState([])
  const [studentApplicationsPagination, setStudentApplicationsPagination] = useState({
    current: 1,
    pageSize: 10,
    total: 0,
  })
  const [loadingStudentApplications, setLoadingStudentApplications] = useState(false)
  const [selectedRecord, setSelectedRecord] = useState(null)
  const [selectedRecordLoading, setSelectedRecordLoading] = useState(false)
  const [aiAssistLoading, setAiAssistLoading] = useState(false)
  const [aiAssistResult, setAiAssistResult] = useState(null)
  const [selectedStudentGroup, setSelectedStudentGroup] = useState(null)
  const [selectedClass, setSelectedClass] = useState(queryReviewFilters.selectedClass)
  const [reviewModal, setReviewModal] = useState({
    open: false,
    action: 'approve',
    comment: '',
    reviewScore: null,
    record: null,
  })
  const [submitting, setSubmitting] = useState(false)
  const [exporting, setExporting] = useState(false)
  const [forcingTeacherStage, setForcingTeacherStage] = useState(false)

  const pageTitle =
    title || (isCommittee ? '班委附加分初审' : isTeacher ? '附加分审核' : '附加分审核查看')
  const helperText =
    description ||
    (isCommittee
      ? '班委先完成本班初审，初审通过后申请会自动进入老师审核。'
      : isTeacher
        ? '老师处理已完成班委初审的申请；若班级未设置班委，老师可直接处理待审记录。'
        : '管理员仅查看审核进度和结果，不参与审核操作。')

  useEffect(() => {
    setScoreType(queryReviewFilters.scoreType)
    setStatusFilter(queryReviewFilters.status)
    setClassFilter(queryReviewFilters.className || 'all')
    setSelectedClass(queryReviewFilters.selectedClass)
    if (!queryReviewFilters.studentNo) {
      setSelectedStudentGroup(null)
      setStudentApplications([])
    }
    setClassStudentsPagination((prev) => ({ ...prev, current: 1 }))
    setStudentApplicationsPagination((prev) => ({ ...prev, current: 1, total: 0 }))
  }, [queryReviewFilters])

  const updateReviewSearchParams = (updates) => {
    const nextParams = new URLSearchParams(searchParamKey)
    Object.entries(updates).forEach(([key, value]) => {
      if (value === null || value === undefined || value === '' || value === 'all') {
        nextParams.delete(key)
      } else {
        nextParams.set(key, String(value))
      }
    })
    setSearchParams(nextParams)
  }

  const buildReviewParams = () => {
    const params = {
      scoreType: scoreType === 'all' ? undefined : scoreType,
      status: statusFilter,
      currentPeriodOnly: true,
    }
    if (classFilter !== 'all') {
      params.classId = classFilter
    }
    if (dateRange && dateRange.length === 2) {
      params.startDate = dateRange[0].format('YYYY-MM-DD')
      params.endDate = dateRange[1].format('YYYY-MM-DD')
    }
    if (searchText.trim()) {
      params.keyword = searchText.trim()
    }
    return params
  }

  const fetchRecords = async () => {
    if (isWorkbench) {
      setLoadingWorkbench(true)
      try {
        const res = await getExtraScoreReviewWorkbench({
          scoreType: scoreType === 'all' ? undefined : scoreType,
          currentPeriodOnly: true,
        })
        const data = res?.data || {}
        const classes = Array.isArray(data.classes) ? data.classes : []
        setWorkbenchData({
          context: data.context || {},
          summary: data.summary || {},
          filters: {
            academicYears: Array.isArray(data.filters?.academicYears)
              ? data.filters.academicYears
              : [],
            classes: Array.isArray(data.filters?.classes) ? data.filters.classes : [],
          },
          classes,
        })
        setClassGroups(classes)
      } catch (error) {
        console.error('获取审核工作台失败:', error)
        setWorkbenchData({
          context: {},
          summary: {},
          filters: { academicYears: [], classes: [] },
          classes: [],
        })
        setClassGroups([])
        message.error(error?.response?.data?.message || '获取审核工作台失败')
      } finally {
        setLoadingWorkbench(false)
        setWorkbenchLoaded(true)
      }
      return
    }
    try {
      const res = await getExtraScoreReviewClassList(buildReviewParams())
      const data = Array.isArray(res?.data) ? res.data : []
      setClassGroups(data)
    } catch (error) {
      console.error('获取审核班级列表失败:', error)
      setClassGroups([])
      message.error(error?.response?.data?.message || '获取审核班级列表失败')
    }
  }

  const fetchClassStudents = async (className, pagination = classStudentsPagination) => {
    setLoadingClassStudents(true)
    try {
      const current = pagination.current || 1
      const pageSize = pagination.pageSize || 10
      const res = await getExtraScoreReviewClassStudents(className, {
        ...buildReviewParams(),
        page: current - 1,
        size: pageSize,
      })
      const pageData = res?.data
      const data = Array.isArray(pageData)
        ? pageData
        : Array.isArray(pageData?.content)
          ? pageData.content
          : []
      setClassStudents(data)
      setRecords(data)
      setClassStudentsPagination({
        current,
        pageSize,
        total: Array.isArray(pageData) ? pageData.length : Number(pageData?.totalElements || 0),
      })
    } catch (error) {
      console.error('获取班级学生失败:', error)
      setClassStudents([])
      setRecords([])
      setClassStudentsPagination((prev) => ({ ...prev, total: 0 }))
      message.error(error?.response?.data?.message || '获取班级学生列表失败')
    } finally {
      setLoadingClassStudents(false)
    }
  }

  const fetchStudentApplications = async (group, pagination = studentApplicationsPagination) => {
    if (!group?.studentNo) {
      setStudentApplications([])
      setStudentApplicationsPagination((prev) => ({ ...prev, current: 1, total: 0 }))
      return
    }
    setLoadingStudentApplications(true)
    try {
      const current = pagination.current || 1
      const pageSize = pagination.pageSize || 10
      const res = await getExtraScoreReviewStudentApplications(group.studentNo, {
        ...buildReviewParams(),
        page: current - 1,
        size: pageSize,
      })
      const pageData = res?.data
      setStudentApplications(
        Array.isArray(pageData)
          ? pageData
          : Array.isArray(pageData?.content)
            ? pageData.content
            : [],
      )
      setStudentApplicationsPagination({
        current,
        pageSize,
        total: Array.isArray(pageData) ? pageData.length : Number(pageData?.totalElements || 0),
      })
    } catch (error) {
      setStudentApplications([])
      setStudentApplicationsPagination((prev) => ({ ...prev, total: 0 }))
      message.error(error?.response?.data?.message || '获取学生附加分明细失败')
    } finally {
      setLoadingStudentApplications(false)
    }
  }

  const handleOpenRecordDetail = async (record) => {
    setSelectedRecord(record)
    setSelectedRecordLoading(true)
    setAiAssistResult(parseAiSuggestion(record?.aiSuggestionJson))
    try {
      const response = await getExtraScoreReviewApplicationDetail(record.id)
      setSelectedRecord(response?.data || record)
      setAiAssistResult(parseAiSuggestion(response?.data?.aiSuggestionJson))
    } catch (error) {
      message.error(error?.response?.data?.message || '获取申请详情失败')
    } finally {
      setSelectedRecordLoading(false)
    }
  }

  const handleAiAssist = async () => {
    if (!selectedRecord?.id) return
    setAiAssistLoading(true)
    try {
      const response = await aiAssistExtraScoreApplication(selectedRecord.id)
      const result = response?.data || null
      setAiAssistResult(result)
      setSelectedRecord((prev) => (prev ? { ...prev, aiSuggestionJson: JSON.stringify(result) } : prev))
      message.success('AI 审核建议已生成')
    } catch (error) {
      message.error(error?.response?.data?.message || '生成 AI 审核建议失败')
    } finally {
      setAiAssistLoading(false)
    }
  }

  const renderAiAssistCard = () => {
    const result = aiAssistResult
    const isFallback = result?.mode === 'fallback'
    const decision = result?.suggestion?.decision
    const decisionColor = decision === '建议通过' ? 'green' : decision === '建议驳回' ? 'red' : 'orange'
    const ocr = result?.ocr || {}
    const hasOcr = Object.values(ocr).some((value) => value)
    const suspects = Array.isArray(result?.suspects) ? result.suspects : []

    return (
      <Card
        size="small"
        title={
          <Space>
            <BulbOutlined />
            <span>AI 审核助手</span>
            <Text type="secondary" style={{ fontWeight: 'normal' }}>
              （仅作参考，最终以人工审核为准）
            </Text>
          </Space>
        }
        extra={
          <Button size="small" type="primary" ghost loading={aiAssistLoading} onClick={handleAiAssist}>
            {result ? '重新生成' : '生成审核建议'}
          </Button>
        }
      >
        {!result ? (
          <Text type="secondary">点击「生成审核建议」，AI 将识别证书图片并给出结构化参考意见。</Text>
        ) : (
          <Space direction="vertical" size={8} style={{ width: '100%' }}>
            {isFallback ? <Tag>{result?.note || '规则审核（未调用视觉模型）'}</Tag> : null}
            {hasOcr ? (
              <Descriptions column={2} size="small" bordered>
                <Descriptions.Item label="证书名称">{ocr.certName || '-'}</Descriptions.Item>
                <Descriptions.Item label="证书编号">{ocr.certNo || '-'}</Descriptions.Item>
                <Descriptions.Item label="奖项级别">{ocr.levelName || '-'}</Descriptions.Item>
                <Descriptions.Item label="获奖时间">{ocr.awardTime || '-'}</Descriptions.Item>
                <Descriptions.Item label="作者/排名">{ocr.authorLabel || '-'}</Descriptions.Item>
              </Descriptions>
            ) : null}
            <div>
              <Tag color={decisionColor}>{decision || '未给出结论'}</Tag>
              <Tag color={result?.confidence === '高' ? 'green' : result?.confidence === '中' ? 'orange' : 'default'}>
                置信度：{result?.confidence || '未知'}
              </Tag>
            </div>
            {result?.suggestion?.reason ? <Text>{result.suggestion.reason}</Text> : null}
            {suspects.length ? (
              <div>
                <Space size={4} style={{ marginBottom: 4 }}>
                  <WarningOutlined style={{ color: '#faad14' }} />
                  <Text strong>疑点（{suspects.length}）</Text>
                </Space>
                {suspects.map((item, index) => (
                  <div key={index} style={{ marginBottom: 4 }}>
                    <Tag color={item.level === '高' ? 'red' : item.level === '中' ? 'orange' : 'default'}>
                      {item.type}
                    </Tag>
                    <Text type="secondary">{item.detail}</Text>
                  </div>
                ))}
              </div>
            ) : (
              <Text type="secondary">未发现明显疑点。</Text>
            )}
          </Space>
        )}
      </Card>
    )
  }

  // Debounced search
  useEffect(() => {
    const timer = setTimeout(() => {
      fetchRecords()
      if (selectedClass) {
        fetchClassStudents(selectedClass, { ...classStudentsPagination, current: 1 })
      }
    }, 500)

    return () => clearTimeout(timer)
  }, [scoreType, statusFilter, classFilter, dateRange, searchText, selectedClass])

  const summary = useMemo(
    () => ({
      total: classGroups.reduce((sum, item) => sum + Number(item.studentCount || 0), 0),
      pending: classGroups.reduce((sum, item) => sum + Number(item.pending || 0), 0),
      approved: classGroups.reduce((sum, item) => sum + Number(item.approved || 0), 0),
      returned: classGroups.reduce((sum, item) => sum + Number(item.returned || 0), 0),
    }),
    [classGroups],
  )

  const classOptions = useMemo(() => {
    if (isWorkbench) {
      return workbenchData.filters.classes
    }
    return Array.from(new Set(classGroups.map((item) => item.className).filter(Boolean))).sort()
  }, [classGroups, isWorkbench, workbenchData.filters.classes])

  const academicYearOptions = useMemo(
    () => {
      if (isWorkbench) {
        return workbenchData.filters.academicYears
      }
      return Array.from(new Set(records.map((item) => item.academicYear).filter(Boolean))).sort(
        (a, b) => String(b).localeCompare(String(a)),
      )
    },
    [isWorkbench, records, workbenchData.filters.academicYears],
  )

  const scopedWorkbenchClasses = useMemo(
    () =>
      classFilter === 'all'
        ? workbenchData.classes
        : workbenchData.classes.filter((item) => item.className === classFilter),
    [classFilter, workbenchData.classes],
  )
  const workbenchSummary = useMemo(
    () => summarizeReviewWorkbenchClasses(scopedWorkbenchClasses),
    [scopedWorkbenchClasses],
  )
  const visibleWorkbenchClasses = useMemo(
    () => filterReviewWorkbenchClasses(scopedWorkbenchClasses, workbenchView),
    [scopedWorkbenchClasses, workbenchView],
  )
  const selectedWorkbenchClass = useMemo(
    () => workbenchData.classes.find((item) => item.className === selectedClass),
    [selectedClass, workbenchData.classes],
  )
  const selectedClassActionableCount = Number(selectedWorkbenchClass?.actionablePending || 0)
  const showSelectedClassActionable =
    !isAdmin && ['all', 'actionable'].includes(workbenchView) && selectedClassActionableCount > 0

  const studentGroups = selectedClass ? classStudents : []

  useEffect(() => {
    if (!selectedStudentGroup) {
      return
    }
    const latestGroup = studentGroups.find((group) => group.key === selectedStudentGroup.key)
    if (!latestGroup) {
      setSelectedStudentGroup(null)
      setStudentApplications([])
      setStudentApplicationsPagination((prev) => ({ ...prev, current: 1, total: 0 }))
      return
    }
    setSelectedStudentGroup(latestGroup)
  }, [studentGroups, selectedStudentGroup])

  const renderReviewStatus = (value) => {
    const statusConfig = {
      draft: { icon: <FileTextOutlined />, text: '草稿' },
      pending: { icon: <ClockCircleOutlined />, text: '待审核' },
      approved: { icon: <CheckCircleOutlined />, text: '已通过' },
      returned: { icon: <CloseCircleOutlined />, text: '已驳回' },
      rejected: { icon: <CloseCircleOutlined />, text: '已驳回' },
    }
    const config = statusConfig[value]

    if (!config) {
      return value || '-'
    }

    return (
      <>
        {config.icon}
        {config.text}
      </>
    )
  }

  const getCommitteeStudentReviewMeta = (group) => {
    const summary = String(group?.committeeReviewSummary || '').toLowerCase()
    const total = Number(group?.recordsCount || 0)
    const pendingCount = Number(group?.committeePending || 0)
    const approvedCount = Number(group?.committeeApproved || 0)
    const returnedCount = Number(group?.committeeReturned || 0)
    const reviewedCount = Number(group?.committeeReviewed ?? approvedCount + returnedCount)

    if (summary) {
      return {
        summary,
        label: group.committeeReviewSummaryLabel || '班委审核',
        total,
        pendingCount,
        approvedCount,
        returnedCount,
        reviewedCount,
      }
    }
    if (pendingCount > 0) {
      return {
        summary: reviewedCount > 0 ? 'partial' : 'pending',
        label: reviewedCount > 0 ? '班委部分完成' : '班委待审',
        total,
        pendingCount,
        approvedCount,
        returnedCount,
        reviewedCount,
      }
    }
    if (returnedCount > 0 || Number(group?.returned || 0) > 0) {
      return {
        summary: approvedCount > 0 ? 'mixed' : 'returned',
        label: approvedCount > 0 ? '班委部分驳回' : '班委已驳回',
        total,
        pendingCount,
        approvedCount,
        returnedCount,
        reviewedCount,
      }
    }
    if (
      total > 0 &&
      (reviewedCount === total ||
        Number(group?.teacherPending || 0) > 0 ||
        Number(group?.approved || 0) > 0)
    ) {
      return {
        summary: 'approved',
        label: '班委已通过',
        total,
        pendingCount,
        approvedCount,
        returnedCount,
        reviewedCount: reviewedCount || total,
      }
    }
    return {
      summary: 'pending',
      label: '班委待审',
      total,
      pendingCount,
      approvedCount,
      returnedCount,
      reviewedCount,
    }
  }

  const renderCommitteeStudentReviewStatus = (group) => {
    const meta = getCommitteeStudentReviewMeta(group)
    const statusClass = ['approved', 'returned', 'mixed', 'partial', 'pending'].includes(
      meta.summary,
    )
      ? meta.summary
      : 'pending'
    return (
      <div className={`committee-review-summary ${statusClass}`}>
        <Tag className="committee-review-summary-tag">{meta.label}</Tag>
        <div className="committee-review-summary-detail">
          已审 {meta.reviewedCount}/{meta.total}
          {meta.approvedCount > 0 ? ` · 通过 ${meta.approvedCount}` : ''}
          {meta.returnedCount > 0 ? ` · 驳回 ${meta.returnedCount}` : ''}
          {meta.pendingCount > 0 ? ` · 待审 ${meta.pendingCount}` : ''}
        </div>
      </div>
    )
  }

  const canDeleteTeacherImportRecord = (record) =>
    isTeacher && String(record?.submissionType || '') === 'teacher_import'

  const getScoreTypeLabel = (value) => {
    const labels = {
      MORAL: '德育',
      INTELLECTUAL: '智育',
      PHYSICAL: '体育',
    }
    return labels[String(value || '').toUpperCase()] || '附加分'
  }

  const getApplicationWorkbenchState = (record) => {
    const status = String(record?.status || '').toLowerCase()
    const stage = String(record?.reviewStage || '').toLowerCase()
    const withdrawalPending = isWithdrawalPending(record)
    const withdrawalActionable = withdrawalPending && Boolean(record?.canReviewWithdrawal)
    const reviewActionable = Boolean(
      (isTeacher && record?.canTeacherReview) ||
        (isCommittee && record?.canCommitteeReview),
    )

    if (withdrawalPending) {
      return withdrawalActionable
        ? {
            key: 'withdrawal-actionable',
            label: '撤回待处理',
            hint: '需要处理学生的撤回申请',
          }
        : {
            key: 'waiting',
            label: '等待处理',
            hint: getWithdrawalReviewHint(record, { isCommittee, isTeacher }),
          }
    }
    if (status === 'approved') {
      return { key: 'approved', label: '已通过', hint: '审核流程已完成' }
    }
    if (status === 'returned' || status === 'rejected') {
      return { key: 'returned', label: '已打回', hint: record?.returnReason || '等待学生补充' }
    }
    if (reviewActionable) {
      return {
        key: 'actionable',
        label: '需要你处理',
        hint: isTeacher ? '可以进行教师审核' : '可以进行班委初审',
      }
    }
    if (stage === 'committee_pending') {
      return { key: 'waiting', label: '等待班委', hint: '等待班委完成初审' }
    }
    if (stage === 'teacher_pending') {
      return { key: 'waiting-teacher', label: '等待教师', hint: '等待教师完成审核' }
    }
    return {
      key: 'pending',
      label: isAdmin ? '审核中' : '等待处理',
      hint: record?.reviewStageLabel || '正在审核',
    }
  }

  // 表格列定义
  const applicationColumns = [
    {
      title: '姓名',
      dataIndex: 'studentName',
      key: 'studentName',
      width: 80,
      render: (value) => <Text strong>{value || '-'}</Text>,
    },
    {
      title: '学号',
      dataIndex: 'studentNo',
      key: 'studentNo',
      width: 110,
      render: (value) => (
        <Text type="secondary" style={{ fontSize: 13 }}>
          {value || '-'}
        </Text>
      ),
    },
    {
      title: '班级',
      dataIndex: 'className',
      key: 'className',
      width: 100,
      render: (value) => <Tag>{value || '-'}</Tag>,
    },
    {
      title: '申请分类',
      key: 'category',
      width: 150,
      render: (_, record) => (
        <div className="category-info">
          <span
            className={`category-tag ${
              record.scoreType === 'MORAL'
                ? 'moral'
                : record.scoreType === 'INTELLECTUAL'
                  ? 'intellectual'
                  : record.scoreType === 'PHYSICAL'
                    ? 'physical'
                    : ''
            }`}
          >
            {record.scoreType === 'MORAL'
              ? '德育'
              : record.scoreType === 'INTELLECTUAL'
                ? '智育'
                : record.scoreType === 'PHYSICAL'
                  ? '体育'
                  : '-'}
          </span>
          <span className="category-name">{record.groupName || '-'}</span>
          <span className="student-detail">{record.itemName || '-'}</span>
        </div>
      ),
    },
    {
      title: '申请内容',
      key: 'project',
      width: 180,
      render: (_, record) => (
        <div className="student-info">
          <span className="student-name">{record.itemName || '-'}</span>
          <span
            className="student-detail"
            style={{
              color:
                normalizeScoreDirection(record.scoreDirection) === 'deduct' ? '#dc2626' : '#16a34a',
            }}
          >
            {normalizeScoreDirection(record.scoreDirection) === 'deduct' ? '减分项' : '加分项'}
          </span>
          {record.levelName && <span className="student-detail">级别: {record.levelName}</span>}
          {record.authorLabel && <span className="student-detail">作者: {record.authorLabel}</span>}
        </div>
      ),
    },
    {
      title: '分数',
      key: 'score',
      width: 140,
      render: (_, record) => (
        <div className="score-display">
          <div className="score-item">
            <span className="score-label">自评:</span>
            <span className="score-value self">
              {formatSignedScore(record.expectedScore, record.scoreDirection)}
            </span>
          </div>
          <div className="score-item">
            <span className="score-label">审核:</span>
            <span
              className={`score-value ${record.finalScore !== undefined && record.finalScore !== null ? 'reviewed' : 'unreviewed'}`}
            >
              {record.finalScore !== undefined && record.finalScore !== null
                ? `${Number(record.finalScore).toFixed(1)} 分`
                : '未核定'}
            </span>
          </div>
        </div>
      ),
    },
    {
      title: '快捷处理',
      key: 'quickAction',
      width: 120,
      render: (_, record) => {
        const reason = explainCannotApprove(record)
        if (reason) {
          return (
            <Text type="secondary" style={{ fontSize: 12 }}>
              {isAdmin ? '只读' : '待其他阶段'}
            </Text>
          )
        }
        return (
          <Button
            type="primary"
            size="small"
            icon={<CheckCircleOutlined />}
            loading={submitting && reviewModal.record?.id === record.id}
            onClick={() => handleQuickApprove(record)}
          >
            通过
          </Button>
        )
      },
    },
    {
      title: '申请时间',
      dataIndex: 'uploadTime',
      key: 'uploadTime',
      width: 140,
      render: (value) => (value ? String(value).replace('T', ' ').slice(0, 16) : '-'),
      sorter: (a, b) => {
        if (!a.uploadTime) return 1
        if (!b.uploadTime) return -1
        return new Date(a.uploadTime) - new Date(b.uploadTime)
      },
      defaultSortOrder: 'descend',
    },
    {
      title: '提交周期',
      key: 'submissionPeriod',
      width: 150,
      render: (_, record) => (
        <Space orientation="vertical" size={2}>
          <Tag color={String(getPeriodLabel(record)).startsWith('补充') ? 'orange' : 'blue'}>
            {getPeriodLabel(record)}
          </Tag>
          <Text type="secondary" style={{ fontSize: 12 }}>
            {record.submissionStartTime || record.submissionEndTime
              ? `${formatDateTime(record.submissionStartTime)} 至 ${formatDateTime(record.submissionEndTime)}`
              : '-'}
          </Text>
        </Space>
      ),
    },
    {
      title: '证书',
      key: 'certificateMedia',
      width: 170,
      render: (_, record) => renderCertificateThumbs(record),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (value, record) => (
        <Space direction="vertical" size={4}>
          <span className={`status-tag ${value}`}>{renderReviewStatus(value)}</span>
          {record.packageReturned ? <Tag color="red">整包退回</Tag> : null}
          {record.recordReviewResultLabel && record.recordReviewResultLabel !== '-' ? (
            <Tag
              color={
                record.recordReviewResult === 'rejected'
                  ? 'red'
                  : record.recordReviewResult === 'approved'
                    ? 'green'
                    : 'blue'
              }
            >
              {record.recordReviewResultLabel}
            </Tag>
          ) : null}
          {isWithdrawalPending(record) ? <Tag color="orange">撤回待处理</Tag> : null}
        </Space>
      ),
      filters: [
        { text: '待审核', value: 'pending' },
        { text: '已通过', value: 'approved' },
        { text: '已驳回', value: 'returned' },
      ],
      onFilter: (value, record) => record.status === value,
    },
    {
      title: '操作',
      key: 'action',
      width: 180,
      fixed: 'right',
      render: (_, record) => (
        <div className="action-btns">
          <button className="btn-link" onClick={() => handleOpenRecordDetail(record)}>
            <EyeOutlined /> 查看详情
          </button>
          {isWithdrawalPending(record) ? (
            canReviewWithdrawal(record) ? (
              <div className="review-withdrawal-actions">
                <button
                  className="btn-link"
                  style={{ color: '#16a34a' }}
                  onClick={() => handleWithdrawalReview(record, 'approve')}
                >
                  <CheckCircleOutlined /> 同意撤回
                </button>
                <button
                  className="btn-link"
                  style={{ color: '#ef4444' }}
                  onClick={() => handleWithdrawalReview(record, 'return')}
                >
                  <RollbackOutlined /> 拒绝撤回
                </button>
              </div>
            ) : (
              <Text type="secondary" className="review-action-hint">
                {getWithdrawalReviewHint(record, { isCommittee, isTeacher })}
              </Text>
            )
          ) : null}
          {canCurrentRoleApprove(record) && (
            <button
              className="btn-link"
              style={{ color: '#ef4444' }}
              onClick={() => openReviewModal(record, 'return')}
            >
              <RollbackOutlined /> 驳回
            </button>
          )}
          {canDeleteTeacherImportRecord(record) && (
            <button
              className="btn-link"
              style={{ color: '#ef4444' }}
              onClick={() => handleDeleteTeacherImportRecord(record)}
            >
              <DeleteOutlined /> 删除
            </button>
          )}
        </div>
      ),
    },
  ]

  const workbenchApplicationColumns = [
    {
      title: '申请项目',
      key: 'application',
      width: 430,
      render: (_, record) => (
        <div className="review-queue-application">
          <strong>{record.itemName || record.certName || '附加分申请'}</strong>
          <span>
            {getScoreTypeLabel(record.scoreType)} / {record.groupName || '未分类'}
          </span>
          {record.levelName || record.authorLabel ? (
            <small>
              {[record.levelName ? `级别：${record.levelName}` : '', record.authorLabel]
                .filter(Boolean)
                .join(' · ')}
            </small>
          ) : null}
        </div>
      ),
    },
    {
      title: '分值与材料',
      key: 'scoreAndMaterial',
      width: 180,
      render: (_, record) => {
        const materialCount = getCertificateMediaList(record).length
        return (
          <div className="review-queue-score">
            <strong>{formatSignedScore(record.expectedScore, record.scoreDirection)}</strong>
            {materialCount > 0 ? (
              <button type="button" onClick={() => handleOpenRecordDetail(record)}>
                <FileTextOutlined /> {materialCount} 份材料
              </button>
            ) : (
              <span>暂无材料</span>
            )}
          </div>
        )
      },
    },
    {
      title: '当前状态',
      key: 'workbenchState',
      width: 240,
      render: (_, record) => {
        const state = getApplicationWorkbenchState(record)
        return (
          <div className="review-queue-state">
            <span className={`review-queue-status ${state.key}`}>{state.label}</span>
            <strong>{state.hint}</strong>
            <small>
              {getPeriodLabel(record)} · {formatDateTime(record.uploadTime)}
            </small>
          </div>
        )
      },
    },
    {
      title: '操作',
      key: 'workbenchActions',
      width: 260,
      fixed: 'right',
      render: (_, record) => {
        const canApprove = canCurrentRoleApprove(record)
        const canHandleWithdrawal = canReviewWithdrawal(record)
        return (
          <div className="review-queue-actions">
            {canApprove ? (
              <>
                <Button
                  type="primary"
                  size="small"
                  loading={submitting}
                  onClick={() => handleQuickApprove(record)}
                >
                  通过
                </Button>
                <Button danger type="text" size="small" onClick={() => openReviewModal(record, 'return')}>
                  驳回
                </Button>
              </>
            ) : null}
            {isWithdrawalPending(record) && canHandleWithdrawal ? (
              <>
                <Button size="small" onClick={() => handleWithdrawalReview(record, 'approve')}>
                  同意撤回
                </Button>
                <Button
                  danger
                  type="text"
                  size="small"
                  onClick={() => handleWithdrawalReview(record, 'return')}
                >
                  拒绝
                </Button>
              </>
            ) : null}
            <Button
              type="text"
              size="small"
              icon={<EyeOutlined />}
              onClick={() => handleOpenRecordDetail(record)}
            >
              详情
            </Button>
            {canDeleteTeacherImportRecord(record) ? (
              <Button
                danger
                type="text"
                size="small"
                icon={<DeleteOutlined />}
                onClick={() => handleDeleteTeacherImportRecord(record)}
              >
                删除
              </Button>
            ) : null}
          </div>
        )
      },
    },
  ]

  const studentColumns = [
    {
      title: '姓名',
      dataIndex: 'studentName',
      key: 'studentName',
      width: 80,
      render: (value) => <Text strong>{value || '-'}</Text>,
    },
    {
      title: '学号',
      dataIndex: 'studentNo',
      key: 'studentNo',
      width: 140,
      render: (value) => (
        <Text type="secondary" style={{ fontSize: 13 }}>
          {value || '-'}
        </Text>
      ),
    },
    {
      title: '班级',
      dataIndex: 'className',
      key: 'className',
      width: 100,
      render: (value) => <Tag>{value || '-'}</Tag>,
    },
    {
      title: '总计',
      key: 'totalCount',
      width: 60,
      align: 'center',
      render: (_, group) => <Text strong>{group.recordsCount || 0}</Text>,
    },
    {
      title: '待审',
      key: 'pendingCount',
      width: 60,
      align: 'center',
      render: (_, group) => (
        <Text
          style={{
            color: group.pending > 0 ? '#d97706' : undefined,
            fontWeight: group.pending > 0 ? 600 : undefined,
          }}
        >
          {group.pending || 0}
        </Text>
      ),
    },
    {
      title: '通过',
      key: 'approvedCount',
      width: 60,
      align: 'center',
      render: (_, group) => (
        <Text style={{ color: group.approved > 0 ? '#16a34a' : undefined }}>
          {group.approved || 0}
        </Text>
      ),
    },
    {
      title: '打回',
      key: 'returnedCount',
      width: 60,
      align: 'center',
      render: (_, group) => (
        <Text style={{ color: group.returned > 0 ? '#dc2626' : undefined }}>
          {group.returned || 0}
        </Text>
      ),
    },
    {
      title: '班委待审',
      key: 'committeePending',
      width: 80,
      align: 'center',
      render: (_, group) => (
        <Text
          style={{
            color: group.committeePending > 0 ? '#d97706' : undefined,
            fontWeight: group.committeePending > 0 ? 600 : undefined,
          }}
        >
          {group.committeePending || 0}
        </Text>
      ),
    },
    {
      title: '班委审核结果',
      key: 'committeeReviewSummary',
      width: 180,
      render: (_, group) => renderCommitteeStudentReviewStatus(group),
    },
    {
      title: '老师待审',
      key: 'teacherPending',
      width: 80,
      align: 'center',
      render: (_, group) => (
        <Text
          style={{
            color: group.teacherPending > 0 ? '#2563eb' : undefined,
            fontWeight: group.teacherPending > 0 ? 600 : undefined,
          }}
        >
          {group.teacherPending || 0}
        </Text>
      ),
    },
    {
      title: '最近提交',
      key: 'latest',
      width: 150,
      render: (_, group) => (
        <Text type="secondary" style={{ fontSize: 13 }}>
          {group.latestUploadTime ? formatDateTime(group.latestUploadTime) : '-'}
        </Text>
      ),
    },
    {
      title: '操作',
      key: 'actions',
      width: 140,
      fixed: 'right',
      render: (_, group) => (
        <Button
          type="link"
          onClick={() => handleOpenStudentGroup(group)}
        >
          查看该学生
        </Button>
      ),
    },
  ]

  const workbenchStudentColumns = [
    {
      title: '学生',
      key: 'student',
      width: 220,
      render: (_, group) => (
        <div className="review-student-identity">
          <strong>{group.studentName || '-'}</strong>
          <span>{group.studentNo || '-'}</span>
        </div>
      ),
    },
    {
      title: '申请概况',
      key: 'overview',
      width: 360,
      render: (_, group) => {
        const total = Number(group.recordsCount || 0)
        const actionable = Number(group.actionablePending || 0)
        const pending = Number(group.pending || 0)
        const approved = Number(group.approved || 0)
        const returned = Number(group.returned || 0)
        const progress = Number(group.progressPercent || 0)
        return (
          <div className="review-student-overview">
            <div className="review-student-summary">
              <strong>共 {total} 条</strong>
              <span className={actionable > 0 && !isAdmin ? 'attention' : ''}>
                {isAdmin ? '待审核' : '需处理'} {isAdmin ? pending : actionable}
              </span>
              <span>通过 {approved}</span>
              <span>打回 {returned}</span>
            </div>
            <div className="review-student-progress-track" aria-label={`已处理 ${progress}%`}>
              <span style={{ width: `${Math.min(Math.max(progress, 0), 100)}%` }} />
            </div>
          </div>
        )
      },
    },
    {
      title: '当前状态',
      key: 'stage',
      width: 230,
      render: (_, group) => {
        const stageKey = group.stageKey || 'pending'
        return (
          <div className="review-student-stage">
            <span className={`review-student-stage-label ${stageKey}`}>
              {group.stageLabel || '审核中'}
            </span>
            <span>{group.stageHint || '查看当前审核进度'}</span>
          </div>
        )
      },
    },
    {
      title: '最近提交',
      key: 'latest',
      width: 160,
      render: (_, group) => (
        <Text type="secondary" className="review-student-latest">
          {group.latestUploadTime ? formatDateTime(group.latestUploadTime) : '暂无更新'}
        </Text>
      ),
    },
    {
      title: '操作',
      key: 'actions',
      width: 120,
      fixed: 'right',
      render: (_, group) => {
        const actionable = Number(group.actionablePending || 0)
        const actionLabel = isAdmin
          ? Number(group.pending || 0) > 0
            ? '查看进度'
            : '查看记录'
          : group.actionLabel || '查看进度'
        return (
          <Button
            type={actionable > 0 && !isAdmin ? 'primary' : 'default'}
            onClick={(event) => {
              event.stopPropagation()
              handleOpenStudentGroup(group)
            }}
          >
            {actionLabel}
          </Button>
        )
      },
    },
  ]

  const [selectedRowKeys, setSelectedRowKeys] = useState([])

  const handleOpenReviewClass = (row) => {
    const className = typeof row === 'string' ? row : row?.className
    if (!className) return
    const nextStatus = isWorkbench ? getReviewStatusForWorkbenchView(workbenchView) : statusFilter
    setStatusFilter(nextStatus)
    setClassFilter(className)
    setSelectedClass(className)
    setSelectedStudentGroup(null)
    setClassStudents([])
    setLoadingClassStudents(true)
    setStudentApplications([])
    setSelectedRowKeys([])
    setClassStudentsPagination((prev) => ({ ...prev, current: 1, total: 0 }))
    setStudentApplicationsPagination((prev) => ({ ...prev, current: 1, total: 0 }))
    updateReviewSearchParams({
      scoreType,
      status: nextStatus,
      classId: className,
      openClass: '1',
      studentNo: null,
    })
  }

  const handleOpenStudentGroup = (group) => {
    if (!group?.studentNo) return
    setSelectedStudentGroup(group)
    setSelectedRowKeys([])
    setStudentApplicationsPagination((prev) => ({ ...prev, current: 1 }))
    updateReviewSearchParams({ studentNo: group.studentNo })
    fetchStudentApplications(group, { ...studentApplicationsPagination, current: 1 })
  }

  const handleReturnToClasses = () => {
    setSelectedRowKeys([])
    updateReviewSearchParams({ classId: null, className: null, openClass: null, studentNo: null })
  }

  const handleReturnToStudents = () => {
    setSelectedRowKeys([])
    updateReviewSearchParams({ studentNo: null })
  }

  const handleWorkbenchViewChange = (view) => {
    const nextStatus = getReviewStatusForWorkbenchView(view)
    setWorkbenchView(view)
    setStatusFilter(nextStatus)
    updateReviewSearchParams({ status: nextStatus })
  }

  const handleWorkbenchScoreTypeChange = (value) => {
    setScoreType(value)
    updateReviewSearchParams({ scoreType: value })
  }

  const handleWorkbenchClassFilterChange = (value) => {
    setClassFilter(value)
    updateReviewSearchParams({ classId: value, openClass: null, studentNo: null })
  }

  const handleResetFilters = () => {
    setScoreType('all')
    setStatusFilter('all')
    setClassFilter('all')
    setAcademicYearFilter('all')
    setDateRange(null)
    setSearchText('')
    setWorkbenchView('all')
    setSelectedClass(null)
    setSelectedStudentGroup(null)
    setClassStudents([])
    setStudentApplications([])
    setSelectedRowKeys([])
    setSearchParams({})
  }

  // Reconcile a student deep link after the selected class page has loaded.
  useEffect(() => {
    const studentNo = queryReviewFilters.studentNo
    if (!studentNo || selectedStudentGroup?.studentNo === studentNo || !classStudents.length) {
      return
    }
    const group = classStudents.find((item) => String(item.studentNo) === studentNo)
    if (group) {
      handleOpenStudentGroup(group)
    }
    // The URL is the source of truth; this effect only runs after the paged student list arrives.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [classStudents, queryReviewFilters.studentNo, selectedStudentGroup?.studentNo])

  const canCurrentRoleApprove = (record) => {
    return !explainCannotApprove(record)
  }

  const canReviewWithdrawal = (record) => {
    return canHandleWithdrawalReview(record, { isCommittee, isTeacher })
  }

  const explainCannotApprove = (record) => {
    const normalizedStatus = String(record?.status || '').toLowerCase()
    const normalizedStage = String(record?.reviewStage || '').toLowerCase()
    if (isWithdrawalPending(record)) {
      return (
        getWithdrawalReviewHint(record, { isCommittee, isTeacher }) ||
        '学生已申请撤回，请先处理撤回申请。'
      )
    }
    if (normalizedStatus !== 'pending') {
      if (normalizedStatus === 'approved') {
        return '这条申请已经审核通过，不能重复通过。'
      }
      if (normalizedStatus === 'returned' || normalizedStatus === 'rejected') {
        return '这条申请已被打回，需学生补充后重新提交。'
      }
      return '这条申请当前不是待审核状态，不能通过。'
    }
    if (isCommittee) {
      return record.canCommitteeReview
        ? ''
        : normalizedStage === 'committee_pending'
          ? '当前申请不能由你初审，若班级有多个班委，请由其他班委处理。'
          : '当前记录不在班委待审阶段，班委不能通过。'
    }
    if (isTeacher) {
      return record.canTeacherReview
        ? ''
        : normalizedStage === 'committee_pending'
          ? '当前记录还在班委初审阶段；只有未设置班委时老师可直接处理。'
          : '当前记录不在老师可处理阶段，不能通过。'
    }
    if (isAdmin) {
      return '管理员仅可查看审核进度，不能执行审核操作。'
    }
    return '当前角色没有附加分审核权限。'
  }

  const handleQuickApprove = async (record) => {
    const reason = explainCannotApprove(record)
    if (reason) {
      message.info(reason)
      return
    }
    setSubmitting(true)
    try {
      const payload = {
        action: 'approve',
        comment: '',
        reviewScore: Number(record.expectedScore || 0),
      }
      if (isCommittee) {
        await committeeReviewExtraScoreApplication(record.id, payload)
      } else {
        await teacherReviewExtraScoreApplication(record.id, payload)
      }
      message.success('已通过该申请')
      setSelectedRowKeys((keys) => keys.filter((key) => String(key) !== String(record.id)))
      await fetchRecords()
      if (selectedClass) {
        await fetchClassStudents(selectedClass)
      }
      if (selectedStudentGroup) {
        await fetchStudentApplications(selectedStudentGroup)
      }
    } catch (error) {
      message.error(error?.response?.data?.message || '快速通过失败')
    } finally {
      setSubmitting(false)
    }
  }

  const refreshReviewData = async () => {
    await fetchRecords()
    if (selectedClass) {
      await fetchClassStudents(selectedClass)
    }
    if (selectedStudentGroup) {
      await fetchStudentApplications(selectedStudentGroup)
    }
  }

  const handleDeleteTeacherImportRecord = async (record) => {
    if (!canDeleteTeacherImportRecord(record)) {
      message.info('只有老师导入的记录可以删除，学生提交的申请请通过驳回处理')
      return
    }
    const confirmed = await confirmAction({
      title: '删除老师导入记录',
      message: `确认删除 ${record.studentName || '该学生'} 的这条老师导入加分记录？`,
      detail: '删除后该条加分不再计入成绩，且无法恢复。',
      confirmText: '删除记录',
      tone: 'danger',
    })
    if (!confirmed) return

    setSubmitting(true)
    try {
      await deleteExtraScoreTeacherImportRecord(record.id)
      message.success('老师导入记录已删除')
      setSelectedRowKeys((keys) => keys.filter((key) => String(key) !== String(record.id)))
      if (selectedRecord?.id === record.id) {
        setSelectedRecord(null)
        setSelectedRecordLoading(false)
      }
      await refreshReviewData()
    } catch (error) {
      message.error(error?.response?.data?.message || '删除老师导入记录失败')
    } finally {
      setSubmitting(false)
    }
  }

  const handleWithdrawalReview = async (record, action) => {
    const approve = action === 'approve'
    const confirmed = await confirmAction({
      title: approve ? '同意撤回申请' : '拒绝撤回申请',
      message: approve
        ? `确认同意 ${record.studentName || '该学生'} 撤回这条附加分申请？同意后记录会退回草稿。`
        : `确认拒绝 ${record.studentName || '该学生'} 的撤回申请？`,
      confirmText: approve ? '同意撤回' : '拒绝撤回',
      tone: approve ? 'primary' : 'danger',
    })
    if (!confirmed) return

    setSubmitting(true)
    try {
      await reviewExtraScoreWithdrawal(record.id, {
        action: approve ? 'approve' : 'return',
        comment: approve ? '' : '班委拒绝撤回申请',
      })
      message.success(approve ? '已同意撤回' : '已拒绝撤回')
      await refreshReviewData()
    } catch (error) {
      message.error(error?.response?.data?.message || '处理撤回申请失败')
    } finally {
      setSubmitting(false)
    }
  }

  const handleBatchAction = async (action) => {
    if (selectedRowKeys.length === 0) {
      message.info('请先选择要操作的申请')
      return
    }
    if (isAdmin) {
      message.info('管理员仅可查看审核进度，不能批量审核')
      return
    }
    if (action !== 'approve') {
      message.info('批量驳回需要逐条填写原因，请单条处理')
      return
    }
    const approvableIds = studentApplications
      .filter((record) => selectedRowKeys.includes(record.id) && canCurrentRoleApprove(record))
      .map((record) => record.id)
    if (!approvableIds.length) {
      const selectedRecords = studentApplications.filter((record) =>
        selectedRowKeys.includes(record.id),
      )
      const firstReason = selectedRecords.map(explainCannotApprove).find(Boolean)
      message.info(firstReason || '选中的记录当前不能批量通过')
      return
    }
    setSubmitting(true)
    try {
      const response = await batchApproveExtraScoreApplications(approvableIds)
      const data = response?.data || {}
      if (data.failedCount > 0) {
        message.warning(
          `批量通过完成：成功 ${data.successCount || 0} 条，失败 ${data.failedCount} 条`,
        )
      } else {
        message.success(`批量通过成功：${data.successCount || 0} 条`)
      }
      setSelectedRowKeys([])
      await fetchRecords()
      if (selectedClass) {
        await fetchClassStudents(selectedClass)
      }
      if (selectedStudentGroup) {
        await fetchStudentApplications(selectedStudentGroup)
      }
    } catch (error) {
      message.error(error?.response?.data?.message || '批量通过失败')
    } finally {
      setSubmitting(false)
    }
  }

  const handleForceTeacherStage = async () => {
    const confirmed = await confirmAction({
      title: '确认进入老师审核阶段？',
      message:
        '确认后，本班当前周期已提交且仍在班委审核阶段的申请会直接进入老师审核；未提交的学生不再阻塞当前流程。',
      confirmText: '确认进入老师审核',
      tone: 'primary',
    })
    if (!confirmed) return

    setForcingTeacherStage(true)
    try {
      const response = await forceCurrentClassToTeacherStage()
      message.success(response?.data?.message || '已进入老师审核阶段')
      await fetchRecords()
      if (selectedClass) {
        await fetchClassStudents(selectedClass)
      }
      if (selectedStudentGroup) {
        await fetchStudentApplications(selectedStudentGroup)
      }
    } catch (error) {
      message.error(error?.response?.data?.message || '进入老师审核阶段失败')
    } finally {
      setForcingTeacherStage(false)
    }
  }

  const handleExportProofImages = async ({ scope = 'all', record = null } = {}) => {
    if (!isTeacher && !isAdmin) {
      message.warning('仅老师或管理员可以导出凭证图片')
      return
    }
    const params = {}
    if (academicYearFilter !== 'all') {
      params.academicYear = academicYearFilter
    }
    const exportClassName = selectedClass || (classFilter !== 'all' ? classFilter : '')
    if (scope === 'class' && exportClassName) {
      params.className = exportClassName
    }
    if (scope === 'class' && !exportClassName) {
      message.info('请先在筛选条件里选择一个班级。')
      return
    }
    if (scope === 'selected') {
      if (!selectedRowKeys.length) {
        message.warning('请先选择要导出的申请')
        return
      }
      params.applicationIds = selectedRowKeys.join(',')
    }
    if (scope === 'single' && record) {
      params.studentNo = record.studentNo
    }
    setExporting(true)
    try {
      const blob = await exportExtraScoreProofImages(params)
      const url = window.URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = `综测凭证图片-${academicYearFilter === 'all' ? '全部学年' : academicYearFilter}.zip`
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
      window.URL.revokeObjectURL(url)
      message.success('导出已开始')
    } catch (error) {
      message.error(error?.response?.data?.message || '导出失败')
    } finally {
      setExporting(false)
    }
  }

  const openReviewModal = (record, action) => {
    setReviewModal({
      open: true,
      action,
      comment: action === 'approve' ? '' : record.returnReason || '',
      reviewScore: record.expectedScore,
      record,
    })
  }

  const closeReviewModal = () => {
    setReviewModal({
      open: false,
      action: 'approve',
      comment: '',
      reviewScore: null,
      record: null,
    })
  }

  const handleSubmitReview = async () => {
    if (!reviewModal.record) {
      return
    }
    const reason = explainCannotApprove(reviewModal.record)
    if (reason) {
      message.info(reason)
      return
    }
    if (
      reviewModal.action === 'approve' &&
      (reviewModal.reviewScore === null || reviewModal.reviewScore === undefined)
    ) {
      message.warning('通过时请填写审核分数')
      return
    }

    setSubmitting(true)
    try {
      const payload = {
        action: reviewModal.action,
        comment: reviewModal.comment.trim(),
        reviewScore: reviewModal.action === 'approve' ? Number(reviewModal.reviewScore) : null,
      }
      if (isCommittee) {
        await committeeReviewExtraScoreApplication(reviewModal.record.id, payload)
      } else if (isTeacher) {
        await teacherReviewExtraScoreApplication(reviewModal.record.id, payload)
      }
      message.success(reviewModal.action === 'approve' ? '审核通过' : '已打回该申请')
      setSelectedRowKeys((keys) =>
        keys.filter((key) => String(key) !== String(reviewModal.record.id)),
      )
      closeReviewModal()
      await fetchRecords()
      if (selectedClass) {
        await fetchClassStudents(selectedClass)
      }
      if (selectedStudentGroup) {
        await fetchStudentApplications(selectedStudentGroup)
      }
    } catch (error) {
      message.error(error?.response?.data?.message || '审核失败')
    } finally {
      setSubmitting(false)
    }
  }

  const rowSelection = {
    selectedRowKeys,
    onChange: (selectedKeys) => {
      setSelectedRowKeys(selectedKeys)
    },
    getCheckboxProps: (record) => ({
      name: record.studentName,
      disabled: Boolean(explainCannotApprove(record)),
      title: explainCannotApprove(record),
    }),
    renderCell: (_, record, __, originNode) => {
      const reason = explainCannotApprove(record)
      return reason ? (
        <Tooltip title={reason}>
          <span className="review-checkbox-hint">{originNode}</span>
        </Tooltip>
      ) : (
        originNode
      )
    },
  }
  const selectionStatusText = selectedRowKeys.length
    ? `已选择 ${selectedRowKeys.length} 条可批量通过申请`
    : studentApplications.some(isWithdrawalPending)
      ? '撤回待处理记录不进入批量审核'
      : '已选择 0 条待处理申请'
  const selectedStudentActionableCount = Number(selectedStudentGroup?.actionablePending || 0)
  const selectedStudentRecordCount = Number(selectedStudentGroup?.recordsCount || 0)
  const selectedStudentReviewSummary = isAdmin
    ? `共 ${selectedStudentRecordCount} 条申请`
    : selectedStudentActionableCount > 0
      ? `当前有 ${selectedStudentActionableCount} 条申请需要处理`
      : '当前没有需要你处理的申请'

  const handleStudentApplicationsTableChange = (pagination) => {
    const nextPagination = {
      current: pagination.current || 1,
      pageSize: pagination.pageSize || 10,
      total: studentApplicationsPagination.total,
    }
    setStudentApplicationsPagination(nextPagination)
    setSelectedRowKeys([])
    fetchStudentApplications(selectedStudentGroup, nextPagination)
  }

  return (
    <div className={`extra-score-review ${isWorkbench ? 'workbench-active' : ''}`}>
      {showPageHeader ? (
        <PageHeader
          title={pageTitle}
          actions={[
            <Button
              key="refresh"
              icon={<ReloadOutlined />}
              onClick={() => {
                fetchRecords()
                if (selectedClass) {
                  fetchClassStudents(selectedClass)
                }
              }}
            >
              刷新列表
            </Button>,
          ]}
        />
      ) : null}

      {isWorkbench && !selectedStudentGroup ? (
        <ReviewWorkbenchOverview
          readOnly={isAdmin}
          loading={loadingWorkbench}
          loaded={workbenchLoaded}
          exporting={exporting}
          context={workbenchData.context}
          filters={workbenchData.filters}
          summary={workbenchSummary}
          classes={visibleWorkbenchClasses}
          scoreType={scoreType}
          classFilter={classFilter}
          view={workbenchView}
          academicYear={academicYearFilter}
          selectedClass={selectedClass}
          selectedStudentName={selectedStudentGroup?.studentName}
          selectedRowCount={selectedRowKeys.length}
          onScoreTypeChange={handleWorkbenchScoreTypeChange}
          onClassFilterChange={handleWorkbenchClassFilterChange}
          onViewChange={handleWorkbenchViewChange}
          onAcademicYearChange={setAcademicYearFilter}
          onOpenClass={handleOpenReviewClass}
          onReturnToClasses={handleReturnToClasses}
          onRefresh={refreshReviewData}
          onReset={handleResetFilters}
          onExport={(scope) => handleExportProofImages({ scope })}
        />
      ) : !isWorkbench ? (
      <Card variant="borderless" className="review-filter-card">
        <Space orientation="vertical" size={16} style={{ width: '100%' }}>
          <div className="review-topbar">
            <div className="review-filters">
              <div className="filter-group">
                <label className="filter-label">
                  <FileTextOutlined />
                  导出学年
                </label>
                <select
                  value={academicYearFilter}
                  onChange={(e) => setAcademicYearFilter(e.target.value)}
                  className="filter-select"
                >
                  <option value="all">全部学年</option>
                  {academicYearOptions.map((year) => (
                    <option key={year} value={year}>
                      {year}
                    </option>
                  ))}
                </select>
              </div>
              <div className="filter-group">
                <label className="filter-label">
                  <FileTextOutlined />
                  附加分类
                </label>
                <select
                  value={scoreType}
                  onChange={(e) => setScoreType(e.target.value)}
                  className="filter-select"
                >
                  {scoreTypeOptions.map((option) => (
                    <option key={option.value} value={option.value}>
                      {option.label}
                    </option>
                  ))}
                </select>
              </div>
              <div className="filter-group">
                <label className="filter-label">
                  <ClockCircleOutlined />
                  状态筛选
                </label>
                <select
                  value={statusFilter}
                  onChange={(e) => setStatusFilter(e.target.value)}
                  className="filter-select"
                >
                  {statusOptions.map((option) => (
                    <option key={option.value} value={option.value}>
                      {option.label}
                    </option>
                  ))}
                </select>
              </div>
              <div className="filter-group">
                <label className="filter-label">
                  <FileTextOutlined />
                  指定班级
                </label>
                <select
                  value={classFilter}
                  onChange={(e) => setClassFilter(e.target.value)}
                  className="filter-select"
                >
                  <option value="all">全部班级</option>
                  {classOptions.map((className) => (
                    <option key={className} value={className}>
                      {className}
                    </option>
                  ))}
                </select>
              </div>
            </div>
            <div className="review-summary-strip">
              <div className="summary-chip total">
                <span className="summary-label">当前学生</span>
                <strong>{selectedClass ? classStudentsPagination.total : summary.total}</strong>
                <span className="summary-unit">人</span>
              </div>
              <div className="summary-chip pending">
                <span className="summary-label">待处理</span>
                <strong>{summary.pending}</strong>
                <span className="summary-unit">条</span>
              </div>
              <div className="summary-chip approved">
                <span className="summary-label">已通过</span>
                <strong>{summary.approved}</strong>
                <span className="summary-unit">条</span>
              </div>
              <div className="summary-chip returned">
                <span className="summary-label">已打回</span>
                <strong>{summary.returned}</strong>
                <span className="summary-unit">条</span>
              </div>
            </div>
          </div>

          <div className="review-hint-inline">{helperText}</div>

          <div className="review-quick-actions">
            {!showPageHeader ? (
              <Button
                icon={<ReloadOutlined />}
                onClick={() => {
                  fetchRecords()
                  if (selectedClass) {
                    fetchClassStudents(selectedClass)
                  }
                }}
              >
                刷新列表
              </Button>
            ) : null}
            <Button onClick={handleResetFilters}>重置筛选</Button>
            {isCommittee && showForceTeacherStageAction ? (
              <Button
                type="primary"
                ghost
                loading={forcingTeacherStage}
                onClick={handleForceTeacherStage}
              >
                强制进入老师审核
              </Button>
            ) : null}
            {isTeacher || isAdmin ? (
              <>
                <Button
                  icon={<DownloadOutlined />}
                  loading={exporting}
                  onClick={() => handleExportProofImages({ scope: 'selected' })}
                >
                  导出选定
                </Button>
                <Button
                  icon={<DownloadOutlined />}
                  loading={exporting}
                  onClick={() => handleExportProofImages({ scope: 'class' })}
                >
                  导出指定班级
                </Button>
                <Button
                  type="primary"
                  icon={<DownloadOutlined />}
                  loading={exporting}
                  onClick={() => handleExportProofImages({ scope: 'all' })}
                >
                  导出全部负责班级
                </Button>
              </>
            ) : null}
          </div>
        </Space>
      </Card>
      ) : null}

      {/* 数据表格区域 */}
      {!selectedClass && !selectedStudentGroup ? (
        isWorkbench ? null : (
          /* 第一层：班级列表 */
          <Card variant="borderless" title="班级审核概览" className="review-table-card">
          {classGroups.length ? (
            <div className="review-class-grid">
              {classGroups.map((cls) => (
                <div
                  key={cls.className}
                  className="review-class-card"
                  onClick={() => handleOpenReviewClass(cls)}
                >
                  <div className="review-class-card-header">
                    <span className="review-class-name">{cls.className}</span>
                    <span className="review-class-student-count">{cls.studentCount} 名学生</span>
                  </div>
                  <div className="review-class-card-stats">
                    <div className="review-class-stat">
                      <span className="review-class-stat-value">{cls.totalRecords}</span>
                      <span className="review-class-stat-label">总提交</span>
                    </div>
                    <div className="review-class-stat pending">
                      <span className="review-class-stat-value">{cls.pending}</span>
                      <span className="review-class-stat-label">待审核</span>
                    </div>
                    <div className="review-class-stat approved">
                      <span className="review-class-stat-value">{cls.approved}</span>
                      <span className="review-class-stat-label">已通过</span>
                    </div>
                    <div className="review-class-stat returned">
                      <span className="review-class-stat-value">{cls.returned}</span>
                      <span className="review-class-stat-label">已打回</span>
                    </div>
                  </div>
                  {cls.committeePending > 0 || cls.teacherPending > 0 ? (
                    <div className="review-class-card-pending">
                      {cls.committeePending > 0 ? (
                        <span className="review-pending-tag committee">
                          班委待审 {cls.committeePending}
                        </span>
                      ) : null}
                      {cls.teacherPending > 0 ? (
                        <span className="review-pending-tag teacher">
                          老师待审 {cls.teacherPending}
                        </span>
                      ) : null}
                    </div>
                  ) : null}
                  <div className="review-class-card-footer">查看班级学生 →</div>
                </div>
              ))}
            </div>
          ) : (
            <Empty className="review-empty" description="当前周期暂无学生提交记录" />
          )}
          </Card>
        )
      ) : selectedClass && !selectedStudentGroup ? (
        /* 第二层：班级内学生列表 */
        <Card
          variant="borderless"
          className="review-table-card"
          title={
            isWorkbench ? (
              <div className="review-student-list-heading">
                <Button
                  type="text"
                  className="review-context-back"
                  icon={<RollbackOutlined />}
                  onClick={handleReturnToClasses}
                >
                  返回班级概览
                </Button>
                <h2>{selectedClass} · 学生审核</h2>
                <p>
                  {classStudentsPagination.total ||
                    selectedWorkbenchClass?.submittedStudentCount ||
                    0}{' '}
                  名学生 · 班级共 {selectedWorkbenchClass?.totalRecords || 0} 条申请
                  {showSelectedClassActionable
                    ? ` · ${selectedClassActionableCount} 条需要处理`
                    : ''}
                </p>
              </div>
            ) : (
              `${selectedClass} · 学生审核列表`
            )
          }
          extra={
            isWorkbench ? null : (
              <Button icon={<RollbackOutlined />} onClick={handleReturnToClasses}>
                返回上一步
              </Button>
            )
          }
        >
          {loadingClassStudents || classStudents.length ? (
            <Table
              rowKey="key"
              loading={loadingClassStudents}
              columns={isWorkbench ? workbenchStudentColumns : studentColumns}
              dataSource={classStudents}
              pagination={{
                current: classStudentsPagination.current,
                pageSize: classStudentsPagination.pageSize,
                total: classStudentsPagination.total,
                showSizeChanger: true,
                showTotal: (total) => `共 ${total} 名学生`,
                showQuickJumper: true,
              }}
              onChange={(pagination) => {
                const nextPagination = {
                  current: pagination.current || 1,
                  pageSize: pagination.pageSize || 10,
                  total: classStudentsPagination.total,
                }
                setClassStudentsPagination(nextPagination)
                fetchClassStudents(selectedClass, nextPagination)
              }}
              onRow={
                isWorkbench
                  ? (group) => ({
                      className: `review-student-workbench-row ${
                        Number(group.actionablePending || 0) > 0 && !isAdmin
                          ? 'needs-action'
                          : ''
                      }`,
                      onClick: () => handleOpenStudentGroup(group),
                    })
                  : undefined
              }
              scroll={{ x: isWorkbench ? 1090 : 1160 }}
              size="middle"
            />
          ) : (
            <Empty className="review-empty" description="该班级暂无提交记录" />
          )}
        </Card>
      ) : (
        <Card
          variant="borderless"
          className="review-table-card"
          title={
            isWorkbench ? (
              <div className="review-application-list-heading">
                <Button
                  type="text"
                  className="review-context-back"
                  icon={<RollbackOutlined />}
                  onClick={handleReturnToStudents}
                >
                  {selectedClass ? `返回 ${selectedClass} 学生列表` : '返回学生列表'}
                </Button>
                <div>
                  <h2>{selectedStudentGroup.studentName || '-'}</h2>
                  <strong>{selectedStudentReviewSummary}</strong>
                </div>
                <p>
                  {selectedStudentGroup.studentNo || '-'} · {selectedStudentGroup.className || '-'} ·{' '}
                  {workbenchData.context.currentAcademicYear || '当前学年'}
                </p>
              </div>
            ) : (
              `${selectedStudentGroup.studentName || '-'} 的提交明细`
            )
          }
          extra={
            isWorkbench ? null : (
              <Button icon={<RollbackOutlined />} onClick={handleReturnToStudents}>
                返回上一步
              </Button>
            )
          }
        >
          {isWorkbench ? (
            <div className="review-application-queue">
              {selectedRowKeys.length > 0 ? (
                <div className="review-queue-selection-toolbar">
                  <strong>{selectionStatusText}</strong>
                  <div>
                    <Button
                      type="primary"
                      loading={submitting}
                      onClick={() => handleBatchAction('approve')}
                    >
                      批量通过
                    </Button>
                    <Button onClick={() => setSelectedRowKeys([])}>取消选择</Button>
                  </div>
                </div>
              ) : null}
              <Table
                rowKey="id"
                columns={workbenchApplicationColumns}
                dataSource={studentApplications}
                loading={loadingStudentApplications}
                rowSelection={isAdmin ? undefined : rowSelection}
                pagination={{
                  current: studentApplicationsPagination.current,
                  pageSize: studentApplicationsPagination.pageSize,
                  total: studentApplicationsPagination.total,
                  showSizeChanger: true,
                  showTotal: (total) => `共 ${total} 条申请`,
                }}
                onChange={handleStudentApplicationsTableChange}
                scroll={{ x: 1110 }}
                size="middle"
              />
            </div>
          ) : (
            <Space orientation="vertical" size={16} style={{ width: '100%' }}>
            <Descriptions column={3} bordered size="small">
              <Descriptions.Item label="姓名">
                {selectedStudentGroup.studentName || '-'}
              </Descriptions.Item>
              <Descriptions.Item label="学号">
                {selectedStudentGroup.studentNo || '-'}
              </Descriptions.Item>
              <Descriptions.Item label="班级">
                {selectedStudentGroup.className || '-'}
              </Descriptions.Item>
              <Descriptions.Item label="提交数量">
                {selectedStudentGroup.recordsCount || 0}
              </Descriptions.Item>
              <Descriptions.Item label="班委审核结果">
                {renderCommitteeStudentReviewStatus(selectedStudentGroup)}
              </Descriptions.Item>
              <Descriptions.Item label="班委待审">
                {selectedStudentGroup.committeePending}
              </Descriptions.Item>
              <Descriptions.Item label="老师待审">
                {selectedStudentGroup.teacherPending}
              </Descriptions.Item>
            </Descriptions>

            <div className="review-table-toolbar">
              <div className="review-batch-actions">
                {!isAdmin ? (
                  <>
                    <Button
                      type="primary"
                      className="review-batch-btn approve"
                      loading={submitting}
                      onClick={() => handleBatchAction('approve')}
                    >
                      批量通过
                    </Button>
                    <Button
                      danger
                      className="review-batch-btn return"
                      onClick={() => handleBatchAction('return')}
                    >
                      批量驳回
                    </Button>
                    <Button
                      className="review-batch-btn neutral"
                      onClick={() => {
                        if (!selectedRowKeys.length) {
                          message.info('当前没有选择记录。')
                          return
                        }
                        setSelectedRowKeys([])
                      }}
                    >
                      取消选择
                    </Button>
                  </>
                ) : (
                  <Text type="secondary">管理员只读查看，不参与审核。</Text>
                )}
              </div>
              <Text type="secondary" className="review-selection-status">
                {selectionStatusText}
              </Text>
            </div>
            <Table
              rowKey="id"
              columns={applicationColumns}
              dataSource={studentApplications}
              loading={loadingStudentApplications}
              rowSelection={isAdmin ? undefined : rowSelection}
              pagination={{
                current: studentApplicationsPagination.current,
                pageSize: studentApplicationsPagination.pageSize,
                total: studentApplicationsPagination.total,
                showSizeChanger: true,
                showQuickJumper: true,
                showTotal: (total) => `共 ${total} 条记录`,
              }}
              onChange={handleStudentApplicationsTableChange}
              scroll={{ x: 1370 }}
              size="middle"
            />
            </Space>
          )}
        </Card>
      )}

      <Drawer
        className="review-drawer"
        title={selectedRecord ? `${selectedRecord.studentName || '-'} 的申请详情` : '申请详情'}
        size="large"
        open={Boolean(selectedRecord)}
        onClose={() => {
          setSelectedRecord(null)
          setSelectedRecordLoading(false)
        }}
      >
        {selectedRecordLoading ? (
          <Spin />
        ) : selectedRecord ? (
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            {renderAiAssistCard()}
            <Descriptions column={1} bordered size="small">
            <Descriptions.Item label="学号">{selectedRecord.studentNo || '-'}</Descriptions.Item>
            <Descriptions.Item label="班级">{selectedRecord.className || '-'}</Descriptions.Item>
            <Descriptions.Item label="模板">{selectedRecord.templateName || '-'}</Descriptions.Item>
            <Descriptions.Item label="提交周期">
              <Space orientation="vertical" size={2}>
                <Tag
                  color={
                    String(getPeriodLabel(selectedRecord)).startsWith('补充') ? 'orange' : 'blue'
                  }
                >
                  {getPeriodLabel(selectedRecord)}
                </Tag>
                <Text type="secondary">
                  {selectedRecord.submissionStartTime || selectedRecord.submissionEndTime
                    ? `${formatDateTime(selectedRecord.submissionStartTime)} 至 ${formatDateTime(selectedRecord.submissionEndTime)}`
                    : '-'}
                </Text>
              </Space>
            </Descriptions.Item>
            <Descriptions.Item label="大类 / 小类">{`${selectedRecord.groupName || '-'} / ${selectedRecord.itemName || '-'}`}</Descriptions.Item>
            <Descriptions.Item label="分值方向">
              {normalizeScoreDirection(selectedRecord.scoreDirection) === 'deduct'
                ? '减分'
                : '加分'}
            </Descriptions.Item>
            <Descriptions.Item label="级别规则">
              {selectedRecord.levelName || '-'}
            </Descriptions.Item>
            <Descriptions.Item label="作者规则">
              {selectedRecord.authorLabel || '-'}
            </Descriptions.Item>
            <Descriptions.Item label="申请分值">
              {formatSignedScore(selectedRecord.expectedScore, selectedRecord.scoreDirection)}
            </Descriptions.Item>
            <Descriptions.Item label="证书名称">{selectedRecord.certName || '-'}</Descriptions.Item>
            <Descriptions.Item label="获奖时间">
              {selectedRecord.awardTime || '-'}
            </Descriptions.Item>
            <Descriptions.Item label="具体说明">
              {selectedRecord.detailDesc || '-'}
            </Descriptions.Item>
            <Descriptions.Item label="文字凭证">
              {selectedRecord.proofText || '-'}
            </Descriptions.Item>
            <Descriptions.Item label="凭证媒体">
              {renderMediaThumbs(selectedRecord)}
            </Descriptions.Item>
            {isTeacher || isAdmin ? (
              <Descriptions.Item label="导出凭证">
                <Button
                  icon={<DownloadOutlined />}
                  loading={exporting}
                  onClick={() =>
                    handleExportProofImages({ scope: 'single', record: selectedRecord })
                  }
                >
                  导出该学生该申请
                </Button>
              </Descriptions.Item>
            ) : null}
            <Descriptions.Item label="当前阶段">
              {selectedRecord.reviewStageLabel || '-'}
            </Descriptions.Item>
            <Descriptions.Item label="单条结论">
              {selectedRecord.recordReviewResultLabel || '-'}
            </Descriptions.Item>
            <Descriptions.Item label="整包状态">
              {selectedRecord.packageReturned ? '本周期整包退回' : '-'}
            </Descriptions.Item>
            <Descriptions.Item label="撤回申请">
              {selectedRecord.withdrawalStatus === 'pending'
                ? '待班委处理'
                : selectedRecord.withdrawalStatus === 'approved'
                  ? '已同意'
                  : selectedRecord.withdrawalStatus === 'rejected'
                    ? '已拒绝'
                    : '-'}
              {selectedRecord.withdrawalRequestedAt
                ? ` / ${formatDateTime(selectedRecord.withdrawalRequestedAt)}`
                : ''}
              {selectedRecord.withdrawalComment ? ` / ${selectedRecord.withdrawalComment}` : ''}
            </Descriptions.Item>
            <Descriptions.Item label="班委审核">
              {selectedRecord.committeeStatusLabel || '-'}
              {selectedRecord.committeeReviewerName
                ? ` / ${selectedRecord.committeeReviewerName}`
                : ''}
              {selectedRecord.committeeComment ? ` / ${selectedRecord.committeeComment}` : ''}
            </Descriptions.Item>
            <Descriptions.Item label="老师审核">
              {selectedRecord.teacherStatusLabel || '-'}
              {selectedRecord.teacherReviewerName ? ` / ${selectedRecord.teacherReviewerName}` : ''}
              {selectedRecord.teacherComment ? ` / ${selectedRecord.teacherComment}` : ''}
            </Descriptions.Item>
            <Descriptions.Item label="问题说明">
              {selectedRecord.returnReason || '-'}
            </Descriptions.Item>
            </Descriptions>
          </Space>
        ) : null}
      </Drawer>

      <Modal
        className="review-modal"
        title={reviewModal.action === 'approve' ? '确认通过申请' : '打回申请'}
        open={reviewModal.open}
        confirmLoading={submitting}
        onCancel={closeReviewModal}
        onOk={handleSubmitReview}
        okText={reviewModal.action === 'approve' ? '确认通过' : '确认打回'}
        cancelText="取消"
        width={600}
      >
        <Space orientation="vertical" size={20} style={{ width: '100%' }}>
          {/* Application Info Card */}
          <Card size="small" style={{ background: '#f5f5f5' }}>
            <Descriptions column={2} size="small">
              <Descriptions.Item label="学生">
                {reviewModal.record?.studentName || '-'}
              </Descriptions.Item>
              <Descriptions.Item label="学号">
                {reviewModal.record?.studentNo || '-'}
              </Descriptions.Item>
              <Descriptions.Item label="提交周期">
                {getPeriodLabel(reviewModal.record)}
              </Descriptions.Item>
              <Descriptions.Item label="申请项目">
                {reviewModal.record?.itemName || '-'}
              </Descriptions.Item>
              <Descriptions.Item label="自评分">
                <Text strong style={{ color: '#1890ff' }}>
                  {formatSignedScore(
                    reviewModal.record?.expectedScore || 0,
                    reviewModal.record?.scoreDirection,
                  )}
                </Text>
              </Descriptions.Item>
            </Descriptions>
          </Card>

          {/* Score Editing Section - Only show for approve action */}
          {reviewModal.action === 'approve' && (
            <Card size="small" title="审核分数" style={{ borderColor: '#1890ff' }}>
              <Space orientation="vertical" size={12} style={{ width: '100%' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
                  <label style={{ fontWeight: 500, whiteSpace: 'nowrap' }}>
                    审核分数 <Text type="danger">*</Text>
                  </label>
                  <Input
                    type="number"
                    step="0.1"
                    min="0"
                    max="100"
                    value={reviewModal.reviewScore}
                    onChange={(e) => {
                      const value = e.target.value
                      setReviewModal((prev) => ({
                        ...prev,
                        reviewScore: value === '' ? '' : Number(value),
                      }))
                    }}
                    style={{ flex: 1 }}
                    placeholder="请输入审核分数"
                    addonAfter="分"
                  />
                </div>

                {/* Score Comparison */}
                {reviewModal.reviewScore !== null && reviewModal.reviewScore !== '' && (
                  <div style={{ padding: 12, background: '#fafafa', borderRadius: 4 }}>
                    <Space>
                      <Text type="secondary">评分对比：</Text>
                      <Text>
                        自评:{' '}
                        <Text strong style={{ color: '#1890ff' }}>
                          {formatSignedScore(
                            reviewModal.record?.expectedScore || 0,
                            reviewModal.record?.scoreDirection,
                          )}
                        </Text>
                      </Text>
                      <Text>→</Text>
                      <Text>
                        审核:{' '}
                        <Text
                          strong
                          style={{
                            color:
                              Number(reviewModal.reviewScore) === reviewModal.record?.expectedScore
                                ? '#52c41a'
                                : '#fa8c16',
                          }}
                        >
                          {formatSignedScore(
                            reviewModal.reviewScore,
                            reviewModal.record?.scoreDirection,
                          )}
                        </Text>
                      </Text>
                      {Number(reviewModal.reviewScore) !== reviewModal.record?.expectedScore && (
                        <Tag color="orange">已修改</Tag>
                      )}
                      {Number(reviewModal.reviewScore) === reviewModal.record?.expectedScore && (
                        <Tag color="green">与自评一致</Tag>
                      )}
                    </Space>
                  </div>
                )}
              </Space>
            </Card>
          )}

          {/* Comment Section */}
          <div>
            <label style={{ fontWeight: 500, display: 'block', marginBottom: 8 }}>
              {reviewModal.action === 'approve' ? (
                '审核备注 (可选)'
              ) : (
                <>
                  驳回原因 <Text type="danger">*</Text>
                </>
              )}
            </label>
            <TextArea
              rows={4}
              value={reviewModal.comment}
              placeholder={reviewModal.action === 'approve' ? '可选填写审核备注' : '请填写驳回原因'}
              onChange={(e) => setReviewModal((prev) => ({ ...prev, comment: e.target.value }))}
            />
          </div>
        </Space>
      </Modal>
    </div>
  )
}

export default ExtraScoreReviewPanel
