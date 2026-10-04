import { useState, useEffect, useMemo, useCallback, useRef } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { message, Button, Tag, Progress, Spin } from 'antd'
import {
  MessageOutlined,
  FlagOutlined,
  ArrowRightOutlined,
  StarFilled,
  StarOutlined,
  LinkOutlined,
  DownOutlined,
  IdcardOutlined,
  FireOutlined,
  ClusterOutlined,
  CarryOutOutlined,
  ReadOutlined,
  CompassOutlined,
  NodeIndexOutlined,
  AimOutlined,
  CheckSquareOutlined,
  BulbOutlined,
  TrophyOutlined,
  CheckCircleOutlined,
} from '@ant-design/icons'
import JobSkillMindMap from '../../components/JobSkillMindMap'
import EChartPanel from '../../components/EChartPanel'
import DailyCheckinCard from '../../components/DailyCheckinCard'
import HotJobs from './HotJobs'
import MyJobFavorites from './MyJobFavorites'
import GrowthPath from './GrowthPath'
import DevelopmentResources from './DevelopmentResources'
import TimeMachine from './TimeMachine'
import AIChat from './AIChat'
import AIFloatingChat from '../../components/AIFloatingChat'
import { getStoredUser, withAuthToken } from '../../utils/authStorage'
import {
  getStudentPortalInfo,
  getStudentWeeklyJobRecommendations,
  getStudentProfileAnalysis,
} from '../../api/student/portal'
import {
  getFavorites,
  getFavoriteIds,
  addFavorite,
  removeFavorite,
} from '../../api/student/jobFavorite'
import { getResourceQuota, getJobResources } from '../../api/student/developmentResource'
import { getJobTaskCardQuota, getJobTaskCard } from '../../api/student/growthPath'
import { getLearningProgressOverview, getJobProgress } from '../../api/student/learningProgress'
import { getLangPopRankings } from '../../api/student/langPop'
import {
  getResourceTypeTag,
  parseSkills,
  parseLines,
  resolveJobSearchLink,
} from '../../utils/jobAssistant'
import './AssistantHome.css'

/** 学业画像雷达维度（与个人中心保持一致） */
const RADAR_KEYS = [
  '思想品德',
  '发展潜力',
  '综合素质',
  '技能素养',
  '文体素养',
  '实践服务',
  '科研创新',
  '学业成绩',
]

const CHAIN_STEPS = [
  { label: '我的画像', desc: '专业 · 年级 · 兴趣', icon: IdcardOutlined, tone: 'blue' },
  { label: '热门岗位', desc: '匹配就业分析', icon: FireOutlined, tone: 'deep' },
  { label: '能力图谱', desc: '技能 · 知识 · 素养', icon: ClusterOutlined, tone: 'mid' },
  { label: '学习任务卡', desc: '计划 · 课程 · 实践', icon: CarryOutOutlined, tone: 'soft' },
  { label: '学习资源', desc: '精选资源推荐', icon: ReadOutlined, tone: 'ink' },
  { label: '个性化路径', desc: '持续优化迭代', icon: CompassOutlined, tone: 'navy' },
]

/** 内部视图元信息：全部功能均为发展助手内部视图（含 AI 对话） */
const VIEW_META = {
  overview: { title: '首页概览', subtitle: '能力雷达与语言热度为主，资源与路径保留精简预览。' },
  records: { title: '学习记录', subtitle: '按收藏岗位查看资源、任务步骤与路径步骤的真实完成进度。' },
  hotJobs: { title: '热门岗位推荐', subtitle: '近一周热招岗位，可收藏关注感兴趣的方向。' },
  favorites: { title: '我的收藏', subtitle: '管理已收藏岗位；概览工作台默认关注列表第一个岗位。' },
  growthPath: { title: '岗位成长链路', subtitle: '串联画像、岗位、能力图谱、任务卡、资源和路径。' },
  resources: { title: '岗位学习资源', subtitle: '按收藏岗位分 Tab 的六维学习资源与图文微课。' },
  aiChat: { title: 'AI 对话', subtitle: '与独立 AI 对话页共用历史会话，可在发展助手内直接提问。' },
  timeMachine: { title: 'AI 时光机', subtitle: '基于真实画像推演考研、考公、竞赛与就业的走向。' },
}

/**
 * 侧边栏导航：全部为发展助手内部视图（页面内切换，不跳转）。
 */
const NAV_GLYPH_PATHS = {
  home: 'M4 11 L12 4 L20 11 V20 H14 V14 H10 V20 H4 Z',
  records: 'M5 6 H19 V18 H5 Z M8 9 H16 M8 12 H16 M8 15 H13',
  rocket: 'M12 4 C14 7 16 9 16 12 C16 15 14.2 17 12 19 C9.8 17 8 15 8 12 C8 9 10 7 12 4 Z M10.5 13 C10.5 13 11.2 14.5 12 14.5 C12.8 14.5 13.5 13 13.5 13',
  star: 'M12 3 L14.4 9.2 H21 L15.8 13.1 L17.9 19.5 L12 15.8 L6.1 19.5 L8.2 13.1 L3 9.2 H9.6 Z',
  flag: 'M6 4 V20 M6 4 H17 L14.5 8 L17 12 H6',
  book: 'M5 5 H12 V19 H6 C5.4 19 5 18.6 5 18 V5 Z M12 5 H19 V18 C19 18.6 18.6 19 18 19 H12',
  chat: 'M5 6 H19 V15 H10 L6 19 V15 H5 Z',
  clock: 'M12 4 A8 8 0 1 1 11.99 4 Z M12 8 V12.4 L15.4 14.6',
  radar: 'M12 4 A8 8 0 1 1 11.9 4 M12 8 A4 4 0 1 1 11.9 8 M12 4 V20 M4 12 H20',
  chain: 'M8 9 H11 L13 15 H16 M7 12 H10 M14 12 H17',
}

function AssistNavIcon({ name }) {
  const d = NAV_GLYPH_PATHS[name]
  return (
    <span className="assist-nav-icon" aria-hidden="true">
      <svg viewBox="0 0 24 24" focusable="false">
        <path d={d} />
      </svg>
    </span>
  )
}
const ASSISTANT_NAV_GROUPS = [
  {
    title: '我的工作台',
    items: [
      { label: '首页概览', view: 'overview', icon: 'home' },
      { label: '学习记录', view: 'records', icon: 'records' },
    ],
  },
  {
    title: '岗位探索',
    items: [
      { label: '热门岗位', view: 'hotJobs', icon: 'rocket' },
      { label: '我的收藏', view: 'favorites', icon: 'star' },
    ],
  },
  {
    title: '岗位成长',
    items: [
      { label: '岗位成长链路', view: 'growthPath', icon: 'chain' },
      { label: '岗位学习资源', view: 'resources', icon: 'book' },
    ],
  },
  {
    title: '智能助手',
    items: [
      { label: 'AI 对话', view: 'aiChat', icon: 'chat' },
      { label: 'AI 时光机', view: 'timeMachine', icon: 'clock' },
    ],
  },
]

const AI_SUGGESTIONS = ['推荐学习资源', '分析学习进度', '解答技术问题', '制定学习计划']

const clampRadarValue = (value) => Math.max(0, Math.min(100, Number(value || 0)))

function formatLangPopScore(score) {
  const n = Number(score)
  if (!Number.isFinite(n)) return '—'
  return n.toFixed(1)
}

function langPopScoreRatio(score, maxScore) {
  const n = Number(score)
  if (!Number.isFinite(n) || maxScore <= 0) return 0
  return Math.max(0, Math.min(1, n / maxScore))
}

function formatMonthLabel(monthKey) {
  if (!monthKey || !/^\d{4}-\d{2}$/.test(monthKey)) return ''
  const [y, m] = monthKey.split('-')
  return `${y}年${Number(m)}月`
}

function formatQuota(remaining, total) {
  if (remaining == null || total == null) return '—'
  return `${remaining}/${total}`
}

function formatStudentName(name) {
  const displayName = String(name || '同学').trim() || '同学'
  if (displayName === '同学' || displayName.endsWith('同学')) {
    return displayName
  }
  return `${displayName}同学`
}

/** 概览区统一空态：波普印记 + 短标题 + 一句指引 + 可选操作 */
function AssistEmptyState({ mark = '空', title, hint, action, compact = false }) {
  return (
    <div className={`assist-empty ${compact ? 'is-compact' : ''}`}>
      <span className="assist-empty-mark" aria-hidden="true">
        {mark}
      </span>
      <div className="assist-empty-copy">
        <strong>{title}</strong>
        {hint ? <p>{hint}</p> : null}
        {action ? <div className="assist-empty-action">{action}</div> : null}
      </div>
    </div>
  )
}

/**
 * 发展助手 - 轻量工作台
 * 全部功能以内部视图方式在页内切换（首页概览 / 学习记录 / 热门岗位 / 我的收藏 /
 * 岗位成长链路 / 岗位学习资源 / AI 对话 / AI 时光机），不再跳出发展助手壳层。
 */
function AssistantHome() {
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const storedUser = getStoredUser()
  const [activeView, setActiveView] = useState('overview')
  const [profile, setProfile] = useState(null)
  const [favorites, setFavorites] = useState([])
  const [hotJobs, setHotJobs] = useState([])
  const [favoritedIds, setFavoritedIds] = useState(new Set())
  const [quota, setQuota] = useState(null)
  const [taskQuota, setTaskQuota] = useState(null)
  const [analysis, setAnalysis] = useState(null)
  const [loading, setLoading] = useState(true)
  const [mindMapVisible, setMindMapVisible] = useState(false)

  const [activeJob, setActiveJob] = useState(null)
  const [resources, setResources] = useState([])
  const [resourcesError, setResourcesError] = useState(null)
  const [resourcesLoading, setResourcesLoading] = useState(false)
  const [taskCard, setTaskCard] = useState(null)
  const [taskCardLoading, setTaskCardLoading] = useState(false)
  // 学习进度概览（发展助手「学习记录」）
  const [progressOverview, setProgressOverview] = useState([])
  // 当前岗位真实完成状态：{ resources: Set, taskSteps: Set, pathSteps: Set }
  const [jobProgress, setJobProgress] = useState({ resources: new Set(), taskSteps: new Set(), pathSteps: new Set() })
  // LangPop 紧凑榜（首页摘要，完整榜在热门岗位页）
  const [langPop, setLangPop] = useState({ loading: true, month: '', weekStart: '', data: [] })
  // 概览「AI 学习助手」快捷提问：切到对话视图后自动发出
  const [aiPendingPrompt, setAiPendingPrompt] = useState(null)
  // 从学习记录跳转到子视图时指定岗位；子视图消费后由父级清空
  const [focusJobId, setFocusJobId] = useState(null)
  // 已访问过的内部视图：首次进入后保持挂载，避免切视图丢状态
  const [mountedViews, setMountedViews] = useState({ overview: true })
  // 内嵌子视图收藏列表同步：热门岗位收藏后递增，驱动我的收藏 / 成长链路 / 学习资源重拉
  const [favoritesRevision, setFavoritesRevision] = useState(0)

  const clearAiPendingPrompt = useCallback(() => {
    setAiPendingPrompt(null)
  }, [])

  const openAiChat = useCallback((prompt) => {
    const text = typeof prompt === 'string' ? prompt.trim() : ''
    setAiPendingPrompt(text || null)
    setActiveView('aiChat')
    setMountedViews((prev) => (prev.aiChat ? prev : { ...prev, aiChat: true }))
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }, [])

  const loadProgressOverview = useCallback(async () => {
    try {
      const res = await getLearningProgressOverview()
      if (Array.isArray(res?.data)) setProgressOverview(res.data)
    } catch {
      // 进度加载失败不阻塞页面
    }
  }, [])

  useEffect(() => {
    loadProgressOverview()
  }, [loadProgressOverview])

  // 跨视图同步收藏状态：内嵌视图（热门岗位 / 我的收藏）中的收藏操作不会通知本组件，
  // 切换视图时统一刷新，保证「收藏岗位」统计、星星标记与关注岗位一致
  const refreshFavorites = useCallback(async () => {
    const [favRes, idsRes] = await Promise.allSettled([getFavorites(), getFavoriteIds()])
    if (idsRes.status === 'fulfilled' && idsRes.value?.data) {
      setFavoritedIds(new Set(idsRes.value.data))
    }
    if (favRes.status === 'fulfilled' && Array.isArray(favRes.value?.data)) {
      const list = favRes.value.data
      setFavorites(list)
      setActiveJob((prev) => {
        if (prev && list.some((job) => job.id === prev.id)) return prev
        return list[0] || null
      })
    }
  }, [])

  /** 热门岗位 / 我的收藏 / 成长链路内收藏变更后：同步父级并通知已挂载子视图重拉 */
  const handleFavoritesChanged = useCallback(async () => {
    await refreshFavorites()
    setFavoritesRevision((n) => n + 1)
    loadProgressOverview()
  }, [refreshFavorites, loadProgressOverview])

  // 加载当前岗位的真实完成记录（资源 / 任务步骤 / 路径步骤）
  const loadJobProgress = useCallback(async (recommendationId) => {
    if (!recommendationId) return
    try {
      const res = await getJobProgress(recommendationId)
      const data = res?.data || {}
      const toKeySet = (items) =>
        new Set((Array.isArray(items) ? items : []).map((item) => String(item.key)))
      setJobProgress({
        resources: toKeySet(data.resources),
        taskSteps: toKeySet(data.taskSteps),
        pathSteps: toKeySet(data.pathSteps),
      })
    } catch {
      // 进度加载失败不阻塞页面
    }
  }, [])

  useEffect(() => {
    let cancelled = false
    setLangPop((prev) => ({ ...prev, loading: true }))
    getLangPopRankings({ limit: 8 })
      .then((payload) => {
        if (cancelled) return
        setLangPop({
          loading: false,
          month: payload.month || '',
          weekStart: payload.weekStart || '',
          data: Array.isArray(payload.data) ? payload.data : [],
        })
      })
      .catch(() => {
        if (cancelled) return
        setLangPop({ loading: false, month: '', weekStart: '', data: [] })
      })
    return () => {
      cancelled = true
    }
  }, [])

  // 切换内部视图时刷新数据；进入「学习记录」时重拉进度概览，回到「首页概览」时
  // 重拉真实完成记录 + 资源/任务卡缓存，这样在成长链路 / 学习资源内生成或勾选后能即时反映。
  // 仅当 activeView 真实变化时触发（pre 用 ref 判断，不依赖 activeJob），
  // 避免首挂载 init 就位 activeJob 时与 activeJob effect 双拉 resources/taskcard。
  // 收藏状态已由子视图 onFavoritesChange 显式驱动 refreshFavorites，这里不再每次切视图兜底刷新。
  const previousActiveViewRef = useRef(activeView)
  useEffect(() => {
    if (previousActiveViewRef.current === activeView) return
    previousActiveViewRef.current = activeView
    if (activeView === 'records') loadProgressOverview()
    if (activeView === 'overview' && activeJob?.id) {
      loadJobProgress(activeJob.id)
      // 子视图可能已生成资源/任务卡，回概览时仅读缓存同步空态
      getJobResources(activeJob.id, false)
        .then((res) => setResources(Array.isArray(res?.data) ? res.data : []))
        .catch(() => {})
      getJobTaskCard(activeJob.id, false)
        .then((res) => setTaskCard(res?.data || null))
        .catch(() => {})
    }
  }, [activeView, loadProgressOverview, loadJobProgress, activeJob])

  useEffect(() => {
    const init = async () => {
      setLoading(true)
      const [favRes, jobsRes, quotaRes, taskQuotaRes, profileRes, analysisRes, favIdsRes] =
        await Promise.allSettled([
          getFavorites(),
          getStudentWeeklyJobRecommendations(),
          getResourceQuota(),
          getJobTaskCardQuota(),
          getStudentPortalInfo(),
          getStudentProfileAnalysis({ studentNo: storedUser?.studentNo }),
          getFavoriteIds(),
        ])

      if (favRes.status === 'fulfilled') {
        const list = Array.isArray(favRes.value?.data) ? favRes.value.data : []
        setFavorites(list)
        if (list.length > 0) setActiveJob(list[0])
      }

      if (favIdsRes.status === 'fulfilled' && favIdsRes.value?.data) {
        setFavoritedIds(new Set(favIdsRes.value.data))
      }

      if (jobsRes.status === 'fulfilled') {
        const data = Array.isArray(jobsRes.value?.data) ? jobsRes.value.data : []
        // 真实数据直接使用；接口无数据时保持空数组，由空态提示引导，不再用示例岗位兜底
        setHotJobs(data.filter((job) => String(job?.jobTitle || '').trim()))
      }

      if (quotaRes.status === 'fulfilled' && quotaRes.value?.data) {
        setQuota(quotaRes.value.data)
      }
      if (taskQuotaRes.status === 'fulfilled' && taskQuotaRes.value?.data) {
        setTaskQuota(taskQuotaRes.value.data)
      }
      if (profileRes.status === 'fulfilled' && profileRes.value?.data) {
        setProfile(profileRes.value.data)
      }
      if (analysisRes.status === 'fulfilled' && analysisRes.value?.data) {
        setAnalysis(analysisRes.value.data)
      }
      setLoading(false)
    }
    init()
    // storedUser 每次渲染都是新对象引用，仅取 studentNo，忽略其依赖
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    setResources([])
    setResourcesError(null)
    setTaskCard(null)

    if (!activeJob) {
      setJobProgress({ resources: new Set(), taskSteps: new Set(), pathSteps: new Set() })
      return
    }

    loadJobProgress(activeJob.id)

    // 首页自动拉取资源与任务卡：仅读缓存（generate=false），
    // 打开首页不触发 AI 生成、不消耗额度；生成由对应页面显式操作触发。
    let cancelled = false
    setResourcesLoading(true)
    setTaskCardLoading(true)

    getJobResources(activeJob.id, false)
      .then((res) => {
        if (cancelled) return
        setResources(Array.isArray(res?.data) ? res.data : [])
      })
      .catch((err) => {
        if (cancelled) return
        setResourcesError(err?.message || '获取学习资源失败，请稍后再试')
      })
      .finally(() => {
        if (!cancelled) setResourcesLoading(false)
      })

    getJobTaskCard(activeJob.id, false)
      .then((res) => {
        if (cancelled) return
        setTaskCard(res?.data || null)
      })
      .catch(() => {
        // 任务卡获取失败不阻塞首页，保留空态引导
      })
      .finally(() => {
        if (!cancelled) setTaskCardLoading(false)
      })

    return () => {
      cancelled = true
    }
  }, [activeJob, loadJobProgress])

  const handleToggleFavorite = async (job, event) => {
    event.stopPropagation()
    const isFav = favoritedIds.has(job.id)
    try {
      if (isFav) {
        await removeFavorite(job.id)
        setFavoritedIds((prev) => {
          const next = new Set(prev)
          next.delete(job.id)
          return next
        })
        const nextFavorites = favorites.filter((item) => item.id !== job.id)
        setFavorites(nextFavorites)
        setFavoritesRevision((n) => n + 1)
        if (activeJob && activeJob.id === job.id) {
          setActiveJob(nextFavorites[0] || null)
        }
        message.success('已取消收藏')
      } else {
        await addFavorite(job.id)
        setFavoritedIds((prev) => new Set(prev).add(job.id))
        setFavorites((prev) => (prev.some((item) => item.id === job.id) ? prev : [...prev, job]))
        setActiveJob((prev) => prev || job)
        setFavoritesRevision((n) => n + 1)
        message.success('收藏成功，可到成长链路/学习资源按需生成 AI 内容')
      }
    } catch {
      // 错误已由拦截器处理
    }
  }

  /** 内嵌成长链路标记/取消完成时回调，即时同步概览 jobProgress，避免"刷新才生效" */
  const handleProgressChange = useCallback((stateKey, key, done) => {
    if (!activeJob) return
    setJobProgress((prev) => {
      const next = new Set(prev[stateKey] || [])
      if (done) next.add(String(key))
      else next.delete(String(key))
      return { ...prev, [stateKey]: next }
    })
    loadProgressOverview()
  }, [activeJob, loadProgressOverview])

  /** 成长链路刷新/生成任务卡后回调：同步概览的任务卡与真实完成记录 */
  const handleTaskCardRefreshed = useCallback((recommendationId) => {
    if (!recommendationId) return
    setTaskCardLoading(true)
    getJobTaskCard(recommendationId, false)
      .then((res) => {
        if (activeJob && activeJob.id === recommendationId) {
          setTaskCard(res?.data || null)
        }
      })
      .catch(() => {
        // 失败不阻塞，保留现有任务卡
      })
      .finally(() => {
        setTaskCardLoading(false)
      })
    loadJobProgress(recommendationId)
    loadProgressOverview()
  }, [activeJob, loadJobProgress, loadProgressOverview])

  /** 资源页生成/刷新后同步概览资源列表 */
  const handleResourcesChange = useCallback((recommendationId, data) => {
    if (activeJob && activeJob.id === recommendationId) {
      setResources(Array.isArray(data) ? data : [])
    }
    loadProgressOverview()
  }, [activeJob, loadProgressOverview])

  const handleSetView = useCallback((view, recommendationId) => {
    setActiveView(view)
    setMountedViews((prev) => (prev[view] ? prev : { ...prev, [view]: true }))
    if (recommendationId != null) {
      setFocusJobId(recommendationId)
      setActiveJob((prev) => {
        const found = favorites.find((job) => job.id === recommendationId)
        return found || prev
      })
    } else if (view === 'growthPath' || view === 'resources') {
      // 从侧栏进入时清空旧 focus，避免误切岗
      setFocusJobId(null)
    }
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }, [favorites])

  // AI 对话推卡 / 外链深链：?view=&jobId=
  useEffect(() => {
    const view = searchParams.get('view')
    if (!view || !VIEW_META[view]) return
    const rawJobId = searchParams.get('jobId')
    let jobId = null
    if (rawJobId != null && rawJobId !== '') {
      const parsed = Number(rawJobId)
      if (Number.isFinite(parsed)) jobId = parsed
    }
    handleSetView(view, jobId)
    const next = new URLSearchParams(searchParams)
    next.delete('view')
    next.delete('jobId')
    setSearchParams(next, { replace: true })
  }, [searchParams, setSearchParams, handleSetView])

  const handleOpenAssistantPageFromChat = useCallback(({ view, jobId }) => {
    if (!view || !VIEW_META[view]) return
    handleSetView(view, jobId != null ? jobId : null)
  }, [handleSetView])

  const handleExitAssistant = () => {
    navigate('/student/home')
  }

  const radarDimensions = useMemo(() => analysis?.radarDimensions || {}, [analysis])
  const hasRadarData = RADAR_KEYS.some((key) => radarDimensions[key] != null)
  const radarValues = RADAR_KEYS.map((key) => clampRadarValue(radarDimensions[key])).filter(
    (value) => Number.isFinite(value) && value > 0,
  )
  const averageRadarScore = radarValues.length
    ? Math.round(radarValues.reduce((sum, value) => sum + value, 0) / radarValues.length)
    : null
  const radarSorted = hasRadarData
    ? RADAR_KEYS.map((key) => ({ key, value: clampRadarValue(radarDimensions[key]) })).sort(
        (a, b) => b.value - a.value,
      )
    : []

  const radarOption = useMemo(
    () => ({
      tooltip: {
        trigger: 'item',
        formatter: (params) =>
          RADAR_KEYS.map((label, index) => `${label}: ${params.value[index]}`).join('<br/>'),
      },
      radar: {
        radius: '66%',
        center: ['50%', '50%'],
        startAngle: 90,
        splitNumber: 4,
        axisName: {
          color: '#52637f',
          fontSize: 12,
          padding: [0, 4],
          formatter: (name) => (name.length > 4 ? `${name.slice(0, 4)}…` : name),
        },
        axisLine: { lineStyle: { color: '#dbe7fb' } },
        splitLine: { lineStyle: { color: '#e8eef9' } },
        splitArea: {
          areaStyle: { color: ['rgba(37, 99, 235, 0.03)', 'rgba(37, 99, 235, 0.07)'] },
        },
        indicator: RADAR_KEYS.map((key) => ({ name: key, max: 100 })),
      },
      series: [
        {
          type: 'radar',
          data: [
            {
              value: RADAR_KEYS.map((key) => clampRadarValue(radarDimensions[key])),
              name: '当前水平',
              symbol: 'circle',
              symbolSize: 5,
              lineStyle: { color: '#2563eb', width: 2 },
              itemStyle: { color: '#2563eb' },
              areaStyle: { color: 'rgba(37, 99, 235, 0.18)' },
            },
          ],
        },
      ],
    }),
    [radarDimensions],
  )

  const taskLines = taskCard ? parseLines(taskCard.steps) : []
  const langPopLanguages = langPop.data
  const langPopMaxScore = langPopLanguages.reduce((max, item) => {
    const n = Number(item.composite_score)
    return Number.isFinite(n) && n > max ? n : max
  }, 0)

  /** 计算所有收藏岗位的总学习完成度百分比（真实数据） */
  const calcOverallPercent = () => {
    let total = 0
    let done = 0
    progressOverview.forEach((job) => {
      total += Number(job.resourceTotal || 0) + Number(job.taskStepTotal || 0) + Number(job.pathStepTotal || 0)
      done += Number(job.resourceCompleted || 0) + Number(job.taskStepCompleted || 0) + Number(job.pathStepCompleted || 0)
    })
    return total ? Math.round((done / total) * 100) : 0
  }

  const activeSkills = useMemo(() => {
    const skillSet = new Set([
      ...parseSkills(activeJob?.skills),
      ...hotJobs.slice(0, 3).flatMap((job) => parseSkills(job?.skills)),
    ])
    return Array.from(skillSet)
  }, [activeJob, hotJobs])

  const rawDisplayName = profile?.name || storedUser?.nickname || storedUser?.username || '同学'
  const displayName = formatStudentName(rawDisplayName)
  const avatarText =
    String(rawDisplayName || 'S')
      .trim()
      .charAt(0)
      .toUpperCase() || 'S'
  const avatarUrl = storedUser?.avatarUrl ? withAuthToken(storedUser.avatarUrl) : ''
  const activeJobTitle = activeJob?.jobTitle || '目标岗位'
  const autoRemaining = quota?.boardRemaining ?? quota?.autoRemaining ?? null
  const autoTotal = quota?.boardTotal ?? quota?.autoTotal ?? null
  const taskRemaining = taskQuota?.boardRemaining ?? taskQuota?.remaining ?? null
  const taskTotal = taskQuota?.boardTotal ?? taskQuota?.total ?? null
  const currentMeta = VIEW_META[activeView] || VIEW_META.overview

  // 当前岗位任务卡的真实完成度（任务步骤用于刷新门槛；路径步骤单独展示）
  const taskStepDone = jobProgress.taskSteps.size
  const taskStepTotal = taskLines.length
  // 与后端刷新门槛一致：仅看任务步骤是否全部完成
  const taskAllDone = taskStepTotal > 0 && taskStepDone === taskStepTotal

  const stats = [
    {
      label: '收藏岗位',
      value: favorites.length,
      suffix: '个',
      hint: activeJob ? `当前关注 ${activeJobTitle}` : '收藏岗位后生成链路',
      tone: 'blue',
    },
    {
      label: '本周热招',
      value: hotJobs.length,
      suffix: '个',
      hint: '近一周岗位推荐',
      tone: 'deep',
    },
    {
      label: '掌握技能',
      value: activeSkills.length || '—',
      suffix: activeSkills.length ? '项' : '',
      hint: activeSkills.slice(0, 2).join(' / ') || '选择岗位后统计',
      tone: 'mid',
    },
    {
      label: '成长指数',
      value: averageRadarScore ?? '—',
      suffix: averageRadarScore != null ? '分' : '',
      hint: analysis?.primaryType || '生成画像后展示',
      tone: 'ink',
    },
  ]

  const renderSidebarItem = (item) => {
    if (item.path) {
      return (
        <button
          type="button"
          className="assist-sidebar-link"
          key={item.label}
          onClick={() => navigate(item.path)}
        >
          <AssistNavIcon name={item.icon} />
          <span>{item.label}</span>
        </button>
      )
    }
    return (
      <button
        type="button"
        className={`assist-sidebar-link ${activeView === item.view ? 'active' : ''}`}
        key={item.label}
        onClick={() => handleSetView(item.view)}
      >
        <AssistNavIcon name={item.icon} />
        <span>{item.label}</span>
      </button>
    )
  }

  const renderStats = () => (
    <div className="assist-stat-strip">
      {stats.map((stat) => (
        <article className={`assist-stat assist-stat-${stat.tone}`} key={stat.label}>
          <div className="assist-stat-copy">
            <span className="assist-stat-label">{stat.label}</span>
            <strong>
              {stat.value}
              {stat.suffix && <small>{stat.suffix}</small>}
            </strong>
            <em>{stat.hint}</em>
          </div>
        </article>
      ))}
    </div>
  )

  const renderJobs = (limit = 5, variant = 'compact') => {
    if (hotJobs.length === 0) {
      return (
        <AssistEmptyState
          compact
          mark="岗"
          title="本周暂无热招岗位"
          hint="后台发布后会同步出现在这里，稍后再来看看。"
        />
      )
    }
    return (
      <div className={variant === 'full' ? 'assist-job-board' : 'assist-side-list'}>
        {hotJobs.slice(0, limit).map((job, index) => {
          const isFav = favoritedIds.has(job.id)
          const skills = parseSkills(job.skills).slice(0, variant === 'full' ? 5 : 2)
          return (
            <article
              className={variant === 'full' ? 'assist-job-card' : 'assist-job-compact'}
              key={job.id || `${job.jobTitle}-${index}`}
            >
              <div className="assist-job-main">
                <span
                  className={`assist-side-icon assist-rank-icon rank-${Math.min(index + 1, 3)}`}
                  aria-hidden="true"
                >
                  {index + 1}
                </span>
                <div className="assist-side-copy">
                  <strong>{job.jobTitle || '软件工程师'}</strong>
                  <em>平均薪资 {job.salary || '薪资面议'}</em>
                  {variant === 'full' && skills.length > 0 && (
                    <div className="assist-skill-tags">
                      {skills.map((skill) => (
                        <span key={`${job.id || job.jobTitle}-${skill}`}>{skill}</span>
                      ))}
                    </div>
                  )}
                </div>
                <span className="assist-hot-pill">热招中</span>
              </div>
              <div className="assist-job-actions">
                <a
                  className="assist-job-open"
                  href={resolveJobSearchLink(job)}
                  target="_blank"
                  rel="noreferrer"
                >
                  <LinkOutlined />
                  <span>查看</span>
                </a>
                <button
                  type="button"
                  className={`assist-job-fav ${isFav ? 'favorited' : ''}`}
                  onClick={(e) => handleToggleFavorite(job, e)}
                  aria-label={isFav ? '取消收藏' : '收藏岗位'}
                >
                  {isFav ? <StarFilled /> : <StarOutlined />}
                  <span>{isFav ? '已藏' : '收藏'}</span>
                </button>
              </div>
            </article>
          )
        })}
      </div>
    )
  }

  const renderTaskCard = () => {
    if (taskCardLoading) {
      return (
        <div className="assist-block-loading">
          <Spin size="small" />
          <p>正在加载学习任务卡…</p>
        </div>
      )
    }
    if (taskLines.length === 0) {
      return (
        <AssistEmptyState
          compact
          mark="任"
          title="还没有学习任务卡"
          hint="去成长链路一键获取，AI 会按岗位拆成可执行步骤。"
          action={
            <Button type="primary" icon={<FlagOutlined />} onClick={() => handleSetView('growthPath')}>
              去获取任务卡
            </Button>
          }
        />
      )
    }
    const percent = taskStepTotal
      ? Math.round((taskStepDone / taskStepTotal) * 100)
      : 0
    return (
      <div className="assist-task-card-progress">
        <div className="assist-taskcard-metric">
          <span>任务步骤完成度</span>
          <strong>
            {taskStepDone}/{taskStepTotal}
          </strong>
          <Progress percent={percent} size="small" />
        </div>
        <div className="assist-task-list">
          {taskLines.slice(0, 6).map((line, index) => {
            const stepKey = String(index + 1)
            const done = jobProgress.taskSteps.has(stepKey)
            return (
              <article
                className={`assist-task-preview ${done ? 'done' : ''}`}
                key={`${line}-${index}`}
              >
                <span className={`assist-side-icon assist-task-icon ${done ? 'is-done' : ''}`}>
                  {done ? <CheckCircleOutlined /> : <CheckSquareOutlined />}
                </span>
                <div className="assist-task-copy">
                  <strong>{line}</strong>
                  <em>{done ? '已完成' : '待完成'}</em>
                </div>
              </article>
            )
          })}
        </div>
        {taskAllDone && (
          <div className="assist-task-all-done">
            <TrophyOutlined /> 任务卡全部完成，太棒了！
          </div>
        )}
      </div>
    )
  }

  const renderChainStepper = () => (
    <div className="assist-chain is-compact">
      {CHAIN_STEPS.map((step, index) => {
        const Icon = step.icon
        return (
          <button
            type="button"
            className="assist-chain-node"
            key={step.label}
            onClick={() => handleSetView('growthPath')}
          >
            <span className={`assist-chain-icon ${step.tone}`}>
              <Icon />
            </span>
            <span className="assist-chain-copy">
              <strong>{step.label}</strong>
              <em>{step.desc}</em>
            </span>
            {index < CHAIN_STEPS.length - 1 && <span className="assist-chain-arrow">→</span>}
          </button>
        )
      })}
    </div>
  )

  const renderResourcesPreview = (limit = 2) => {
    if (resourcesLoading) {
      return (
        <div className="assist-block-loading">
          <Spin size="small" />
          <p>正在加载岗位学习资源…</p>
        </div>
      )
    }
    if (resourcesError && resources.length === 0) {
      return (
        <AssistEmptyState compact mark="!" title="学习资源暂时获取失败" hint={resourcesError} />
      )
    }
    if (resources.length === 0) {
      return (
        <AssistEmptyState
          compact
          mark="资"
          title={activeJob ? '尚未生成学习资源' : '先收藏一个目标岗位'}
          hint={activeJob ? '到学习资源页按需生成图文微课。' : '收藏后可按需生成专属资源。'}
          action={
            <Button
              type="primary"
              icon={activeJob ? <ReadOutlined /> : <FireOutlined />}
              onClick={() => handleSetView(activeJob ? 'resources' : 'hotJobs')}
            >
              {activeJob ? '去生成资源' : '去热门岗位'}
            </Button>
          }
        />
      )
    }
    return (
      <div className="assist-resource-grid is-preview">
        {resources.slice(0, limit).map((resource, index) => {
          const tag = getResourceTypeTag(resource, index)
          const done = jobProgress.resources.has(String(resource.id))
          return (
            <article
              className={`assist-resource-card is-preview ${done ? 'done' : ''}`}
              key={resource.id || index}
            >
              <div className="assist-resource-head">
                <Tag color={tag.color}>{tag.label}</Tag>
                {done && (
                  <Tag color="green" icon={<CheckCircleOutlined />}>
                    已完成
                  </Tag>
                )}
              </div>
              <h3>{resource.title}</h3>
              <p>{resource.description}</p>
              <div className="assist-resource-actions">
                <Button
                  type="primary"
                  size="small"
                  icon={<ReadOutlined />}
                  onClick={() => handleSetView('resources')}
                >
                  去学习
                </Button>
              </div>
            </article>
          )
        })}
      </div>
    )
  }

  const renderRadar = () => {
    if (!hasRadarData) {
      return (
        <AssistEmptyState
          compact
          mark="图"
          title="暂无能力评估数据"
          hint="生成学业画像后，这里会展示多维能力雷达。"
          action={
            <Button type="primary" onClick={() => navigate('/student/profile')}>
              去个人画像
            </Button>
          }
        />
      )
    }
    return (
      <div className="assist-radar-layout is-featured">
        <div className="assist-radar-chart">
          <EChartPanel option={radarOption} height={340} visible={activeView === 'overview'} />
        </div>
        <div className="assist-radar-aside">
          <div className="assist-radar-aside-head">
            <span className="assist-radar-legend">
              <i />
              当前水平
            </span>
            {averageRadarScore != null && (
              <span className="assist-radar-avg">综合 {averageRadarScore} 分</span>
            )}
          </div>
          <div className="assist-radar-dims">
            {radarSorted.map((dim) => (
              <div className="assist-radar-dim" key={dim.key}>
                <span className="assist-radar-dim-label">{dim.key}</span>
                <Progress
                  percent={dim.value}
                  showInfo={false}
                  size="small"
                  strokeColor={dim.value >= 60 ? '#2563eb' : '#f59e0b'}
                />
                <strong>{dim.value}</strong>
              </div>
            ))}
          </div>
        </div>
      </div>
    )
  }

  const renderLangPopCompact = () => {
    if (langPop.loading) {
      return (
        <div className="assist-langpop-skel" aria-hidden="true">
          <div className="assist-langpop-skel-row" />
          <div className="assist-langpop-skel-row" />
          <div className="assist-langpop-skel-row" />
          <div className="assist-langpop-skel-row" />
        </div>
      )
    }
    if (langPopLanguages.length === 0) {
      return (
        <AssistEmptyState
          compact
          mark="榜"
          title="暂无语言热度数据"
          hint="稍后再试，或到热门岗位页查看完整榜单。"
        />
      )
    }
    return (
      <ol className="assist-langpop-list">
        {langPopLanguages.slice(0, 8).map((item) => {
          const rank = Number(item.rank) || 0
          const score = formatLangPopScore(item.composite_score)
          const ratio = langPopScoreRatio(item.composite_score, langPopMaxScore)
          return (
            <li key={item.slug || item.language} className="assist-langpop-row">
              <span className="assist-langpop-rank">{String(rank).padStart(2, '0')}</span>
              <span className="assist-langpop-name">{item.language}</span>
              <div
                className="assist-langpop-bar"
                role="meter"
                aria-label={`${item.language} 得分 ${score}`}
                aria-valuemin={0}
                aria-valuemax={Number(langPopMaxScore.toFixed(1))}
                aria-valuenow={Number(item.composite_score) || 0}
              >
                <span style={{ transform: `scaleX(${ratio})` }} />
              </div>
              <span className="assist-langpop-score">{score}</span>
            </li>
          )
        })}
      </ol>
    )
  }

  const renderPanelHead = (icon, title, subtitle, actionLabel, actionView, tone = 'blue') => {
    const Icon = icon
    return (
      <div className="assist-panel-head">
        <div className="assist-panel-title">
          {Icon && (
            <span className={`assist-panel-glyph ${tone}`}>
              <Icon />
            </span>
          )}
          <div>
            <h2>{title}</h2>
            <p>{subtitle}</p>
          </div>
        </div>
        {actionLabel && actionView && (
          <button type="button" className="assist-more" onClick={() => handleSetView(actionView)}>
            {actionLabel} <ArrowRightOutlined />
          </button>
        )}
      </div>
    )
  }

  const renderOverview = () => {
    const monthLabel = formatMonthLabel(langPop.month) || '本月'
    return (
    <>
      <div className="assist-hero-row">
        <section className="assist-welcome-card">
          <div className="assist-welcome-deco" aria-hidden="true">
            <span className="assist-welcome-burst" />
            <span className="assist-welcome-slash" />
            <span className="assist-welcome-corner" />
          </div>
          <div className="assist-welcome-copy">
            <span className="assist-welcome-kicker">Career Lab · 今日工作台</span>
            <h1>
              你好，<em>{displayName}</em>
            </h1>
            <p>基于你的学习进度和兴趣，为你推荐最适合的学习资源和发展路径。</p>
            {renderStats()}
          </div>
        </section>

        <DailyCheckinCard
          variant="square"
          className="assist-checkin-board"
          userName={rawDisplayName}
        />
      </div>

      <section className="assist-panel assist-ai-panel assist-ai-featured" aria-label="AI 学习助手">
        <div className="assist-ai-featured-deco" aria-hidden="true">
          <span className="assist-ai-featured-burst" />
        </div>
        <div className="assist-ai-featured-main">
          <span className="assist-ai-kicker">AI Lab</span>
          <div className="assist-ai-head">
            <span className="assist-ai-avatar">
              <MessageOutlined />
            </span>
            <div>
              <h2>AI 学习助手</h2>
              <p>随时问进度、资源与计划，小智帮你把学习路径说清楚。</p>
            </div>
          </div>
          <button type="button" className="assist-ai-cta" onClick={() => openAiChat()}>
            <MessageOutlined />
            开始对话
          </button>
        </div>
        <div className="assist-ai-featured-side">
          <span className="assist-ai-side-label">快速提问</span>
          <div className="assist-ai-grid">
            {AI_SUGGESTIONS.map((item) => (
              <button
                type="button"
                className="assist-ai-suggestion"
                key={item}
                onClick={() => openAiChat(item)}
              >
                <BulbOutlined />
                <span>{item}</span>
              </button>
            ))}
          </div>
          <p className="assist-ai-powered">Powered by AI</p>
        </div>
      </section>

      <div className="assist-dashboard-grid">
        <div className="assist-main-column">
          <section className="assist-panel assist-radar-featured">
            <div className="assist-panel-head">
              <div className="assist-panel-title">
                <span className="assist-panel-glyph soft">
                  <AimOutlined />
                </span>
                <div>
                  <h2>技能雷达图</h2>
                  <p>
                    {analysis?.primaryType
                      ? `${analysis.primaryType} · 多维能力评估`
                      : '多维能力评估 · 首页主视觉'}
                  </p>
                </div>
              </div>
              <button
                type="button"
                className="assist-more"
                onClick={() => navigate('/student/profile')}
              >
                去个人画像 <ArrowRightOutlined />
              </button>
            </div>
            {renderRadar()}
          </section>

          <section className="assist-panel assist-langpop-compact" aria-label="热门编程语言">
            {renderPanelHead(
              FireOutlined,
              '热门编程语言',
              langPop.weekStart
                ? `${monthLabel} · 快照 ${langPop.weekStart}`
                : `${monthLabel} · LangPop Index`,
              '看完整榜',
              'hotJobs',
              'navy',
            )}
            {renderLangPopCompact()}
          </section>

          <section className="assist-panel assist-chain-panel">
            {renderPanelHead(
              NodeIndexOutlined,
              '岗位成长链路',
              activeJob ? `当前关注「${activeJobTitle}」` : '从入门到精通的完整学习路径',
              '去成长链路页',
              'growthPath',
              'deep',
            )}
            {renderChainStepper()}
          </section>

          <section className="assist-panel assist-resource-panel">
            {renderPanelHead(
              ReadOutlined,
              '岗位学习资源',
              activeJob
                ? `基于「${activeJobTitle}」· 预览 2 条 · 额度 ${formatQuota(autoRemaining, autoTotal)}`
                : `额度 ${formatQuota(autoRemaining, autoTotal)}，收藏后按需生成`,
              '去学习资源页',
              'resources',
              'blue',
            )}
            {renderResourcesPreview(2)}
          </section>
        </div>

        <aside className="assist-right-rail">
          <section className="assist-panel assist-side-panel">
            {renderPanelHead(
              CarryOutOutlined,
              '学习任务卡',
              `任务额度 ${formatQuota(taskRemaining, taskTotal)}`,
              '去生成',
              'growthPath',
              'ink',
            )}
            {renderTaskCard()}
          </section>

          <section className="assist-panel assist-side-panel">
            {renderPanelHead(
              FireOutlined,
              '热门岗位推荐',
              '近一周热招岗位 · 点星收藏加入工作台',
              '去热门岗位',
              'hotJobs',
              'navy',
            )}
            {renderJobs(5)}
          </section>
        </aside>
      </div>
    </>
    )
  }

  const renderRecords = () => (
    <section className="assist-records">
      <section className="assist-panel">
        {renderPanelHead(
          null,
          '岗位学习进度',
          '资源 · 任务步骤 · 路径步骤 逐项完成，进度条实时记录你的成长',
        )}
        {progressOverview.length === 0 ? (
          <AssistEmptyState
            compact
            mark="录"
            title="还没有学习进度记录"
            hint="收藏岗位后，在学习资源与成长链路中完成学习，进度会汇总到这里。"
            action={
              <Button type="primary" icon={<FireOutlined />} onClick={() => handleSetView('hotJobs')}>
                去收藏岗位
              </Button>
            }
          />
        ) : (
          <div className="assist-progress-grid">
            {progressOverview.map((job) => {
              const rTotal = Number(job.resourceTotal || 0)
              const rDone = Number(job.resourceCompleted || 0)
              const tTotal = Number(job.taskStepTotal || 0)
              const tDone = Number(job.taskStepCompleted || 0)
              const pTotal = Number(job.pathStepTotal || 0)
              const pDone = Number(job.pathStepCompleted || 0)
              const total = rTotal + tTotal + pTotal
              const done = rDone + tDone + pDone
              const percent = total ? Math.round((done / total) * 100) : 0
              const allDone = total > 0 && done === total
              return (
                <article
                  className={`assist-progress-card ${allDone ? 'all-done' : ''}`}
                  key={job.recommendationId}
                >
                  <div className="assist-progress-card-head">
                    <h3>{job.jobTitle || '软件工程师'}</h3>
                    {allDone && <span className="assist-progress-done-mark">已完成</span>}
                  </div>
                  {job.salary && <p className="assist-progress-salary">平均薪资 {job.salary}</p>}
                  <div className="assist-progress-bar-row">
                    <span>整体进度</span>
                    <Progress percent={percent} size="small" />
                  </div>
                  <div className="assist-progress-bar-row">
                    <span>图文微课资源</span>
                    <Progress
                      percent={rTotal ? Math.round((rDone / rTotal) * 100) : 0}
                      size="small"
                      format={() => `${rDone}/${rTotal}`}
                    />
                  </div>
                  <div className="assist-progress-bar-row">
                    <span>任务步骤</span>
                    <Progress
                      percent={tTotal ? Math.round((tDone / tTotal) * 100) : 0}
                      size="small"
                      format={() => `${tDone}/${tTotal}`}
                    />
                  </div>
                  <div className="assist-progress-bar-row">
                    <span>路径步骤</span>
                    <Progress
                      percent={pTotal ? Math.round((pDone / pTotal) * 100) : 0}
                      size="small"
                      format={() => `${pDone}/${pTotal}`}
                    />
                  </div>
                  <div className="assist-progress-card-actions">
                    <Button
                      size="small"
                      onClick={() => handleSetView('growthPath', job.recommendationId)}
                    >
                      去成长链路
                    </Button>
                    <Button
                      size="small"
                      onClick={() => handleSetView('resources', job.recommendationId)}
                    >
                      去学习资源
                    </Button>
                  </div>
                </article>
              )
            })}
          </div>
        )}
      </section>

      <section className="assist-panel assist-overall-card">
        <div className="assist-overall-head">
          <span className="assist-overall-mark" aria-hidden="true">
            <span className="assist-overall-mark-dot" />
          </span>
          <div>
            <h2>总学习完成度</h2>
            <p>全部收藏岗位的学习任务综合完成情况</p>
          </div>
          <strong>{calcOverallPercent()}%</strong>
        </div>
        <Progress percent={calcOverallPercent()} size="small" />
      </section>
    </section>
  )

  if (loading) {
    return (
      <div
        className="assist-loading-screen"
        role="status"
        aria-live="polite"
        aria-label="正在加载发展助手"
      >
        <div className="assist-loading-banner" aria-hidden="true">
          NOW LOADING
        </div>
        <div className="assist-loading-deco" aria-hidden="true">
          <span className="assist-loading-slash" />
          <span className="assist-loading-giant">发</span>
          <span className="assist-loading-burst" />
          <span className="assist-loading-burst is-sm" />
          <span className="assist-loading-block is-tl" />
          <span className="assist-loading-block is-br" />
          <span className="assist-loading-speed" />
        </div>
        <div className="assist-loading-card">
          <span className="assist-loading-kicker">Career Lab</span>
          <span className="assist-loading-mark">发</span>
          <div className="assist-loading-text">
            <h1>发展助手</h1>
            <p>Development Assistant</p>
          </div>
          <ul className="assist-loading-steps">
            <li>画像</li>
            <li>岗位</li>
            <li>路径</li>
          </ul>
          <div className="assist-loading-track">
            <div className="assist-loading-fill" />
          </div>
          <p className="assist-loading-tip">正在铺开蓝白成长工作台</p>
        </div>
      </div>
    )
  }

  return (
    <div className="assist-lab-shell assist-shell-enter">
      {activeView !== 'aiChat' ? (
        <AIFloatingChat role={storedUser?.role || 'STUDENT'} />
      ) : null}
      <aside className="assist-sidebar">
        <div className="assist-brand">
          <span className="assist-brand-mark">发</span>
          <span className="assist-brand-copy">
            <span className="assist-brand-kicker">Career Lab</span>
            <strong>发展助手</strong>
            <em>Development Assistant</em>
          </span>
        </div>
        <button type="button" className="assist-mindmap-btn" onClick={() => setMindMapVisible(true)}>
          <AssistNavIcon name="radar" />
          岗位思维导图
        </button>
        <nav className="assist-sidebar-nav">
          {ASSISTANT_NAV_GROUPS.map((group) => (
            <div className="assist-sidebar-group" key={group.title}>
              <h2>{group.title}</h2>
              {group.items.map(renderSidebarItem)}
            </div>
          ))}
        </nav>
      </aside>

      <section className="assist-workspace">
        <header className="assist-topbar">
          <div className="assist-topbar-title">
            <h1>{currentMeta.title}</h1>
            <p>{currentMeta.subtitle}</p>
          </div>
          <div className="assist-topbar-actions">
            <button type="button" className="assist-user-chip" onClick={() => navigate('/student/profile')}>
              <span className="assist-avatar">
                {avatarUrl ? <img src={avatarUrl} alt="" /> : avatarText}
              </span>
              <span>
                <strong>{displayName}</strong>
                <em>{profile?.majorName || storedUser?.className || '学生'}</em>
              </span>
              <DownOutlined />
            </button>
            <button type="button" className="assist-logout" onClick={handleExitAssistant}>
              ← 返回首页
            </button>
          </div>
        </header>

        <main className={`assist-page ${activeView === 'aiChat' ? 'is-ai-chat' : ''}`}>
          <div
            className="assist-view-pane"
            style={{ display: activeView === 'overview' ? undefined : 'none' }}
          >
            {renderOverview()}
          </div>
          <div
            className="assist-view-pane"
            style={{ display: activeView === 'records' ? undefined : 'none' }}
          >
            {activeView === 'records' || mountedViews.records ? renderRecords() : null}
          </div>
          {mountedViews.hotJobs ? (
            <div
              className="assist-view-pane"
              style={{ display: activeView === 'hotJobs' ? undefined : 'none' }}
            >
              <HotJobs embedded onFavoritesChange={handleFavoritesChanged} />
            </div>
          ) : null}
          {mountedViews.favorites ? (
            <div
              className="assist-view-pane"
              style={{ display: activeView === 'favorites' ? undefined : 'none' }}
            >
              <MyJobFavorites
                embedded
                onNavigateToView={handleSetView}
                favoritesRevision={favoritesRevision}
                onFavoritesChange={handleFavoritesChanged}
              />
            </div>
          ) : null}
          {mountedViews.growthPath ? (
            <div
              className="assist-view-pane"
              style={{ display: activeView === 'growthPath' ? undefined : 'none' }}
            >
              <GrowthPath
                embedded
                onNavigateToView={handleSetView}
                onProgressChange={handleProgressChange}
                onTaskCardRefreshed={handleTaskCardRefreshed}
                initialActiveId={focusJobId}
                favoritesRevision={favoritesRevision}
                onFavoritesChange={handleFavoritesChanged}
              />
            </div>
          ) : null}
          {mountedViews.resources ? (
            <div
              className="assist-view-pane"
              style={{ display: activeView === 'resources' ? undefined : 'none' }}
            >
              <DevelopmentResources
                embedded
                onNavigateToView={handleSetView}
                onProgressChange={handleProgressChange}
                onResourcesChange={handleResourcesChange}
                initialActiveId={focusJobId}
                favoritesRevision={favoritesRevision}
              />
            </div>
          ) : null}
          {mountedViews.aiChat ? (
            <div
              className="assist-view-pane"
              style={{ display: activeView === 'aiChat' ? undefined : 'none' }}
            >
              <AIChat
                embedded
                initialPrompt={aiPendingPrompt}
                onInitialPromptConsumed={clearAiPendingPrompt}
                onOpenAssistantPage={handleOpenAssistantPageFromChat}
              />
            </div>
          ) : null}
          {mountedViews.timeMachine ? (
            <div
              className="assist-view-pane"
              style={{ display: activeView === 'timeMachine' ? undefined : 'none' }}
            >
              <TimeMachine embedded active={activeView === 'timeMachine'} />
            </div>
          ) : null}
        </main>
      </section>

      <JobSkillMindMap
        jobs={hotJobs}
        visible={mindMapVisible}
        onClose={() => setMindMapVisible(false)}
      />
    </div>
  )
}

export default AssistantHome
