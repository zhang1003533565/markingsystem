import { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { Empty, Spin, Button, Tag, Tooltip, message, Progress, Divider } from 'antd';
import {
  ExperimentOutlined,
  StarOutlined,
} from '@ant-design/icons';
import {
  ReloadOutlined,
  CheckCircleOutlined,
  CheckOutlined,
} from '../../components/AcademicIcons';
import StudentLayout from '../../components/StudentLayout';
import JobSkillMindMap from '../../components/JobSkillMindMap';
import MicroCourseReader from '../../components/MicroCourseReader';
import AiGenerationProgress from '../../components/AiGenerationProgress';
import { getStudentPortalInfo, getStudentWeeklyJobRecommendations } from '../../api/student/portal';
import { getFavorites, addFavorite } from '../../api/student/jobFavorite';
import { getJobTaskCard, getJobTaskCardQuota, refreshJobTaskCard, requestJobResources, requestRefreshJobResources } from '../../api/student/growthPath';
import {
  getJobProgress,
  markProgressCompleted,
  unmarkProgressCompleted,
  unlockLearningResource,
} from '../../api/student/learningProgress';
import {
  DIMENSION_TAGS,
  parseSkills,
  parseLines,
  resolveJobSearchLink,
  resolveResourceOpenLink,
  isHttpUrl,
} from '../../utils/jobAssistant';
import DailyCheckinCard from '../../components/DailyCheckinCard';
import './GrowthPath.css';

/** 链路节点（与页面区块一一对应） */
const CHAIN_STEPS = [
  { key: 'profile', label: '我的画像', desc: '专业 · 年级 · 学情' },
  { key: 'jobs', label: '热门岗位', desc: '收藏与热招匹配' },
  { key: 'map', label: '能力图谱', desc: '岗位 → 技能 → 知识' },
  { key: 'task', label: '学习任务卡', desc: '企业任务 → 课堂实训' },
  { key: 'resource', label: '学习资源', desc: '六维定制资源' },
  { key: 'path', label: '个性化路径', desc: '微课 → 仿真 → 实训' },
];

/** 学习任务卡 AI 生成阶段文案（模拟进度，非后端真实百分比） */
const TASK_CARD_STAGES = [
  { max: 15, label: '排队等待 AI 通道…' },
  { max: 40, label: '结合学情分析岗位要求…' },
  { max: 65, label: '生成企业真实任务…' },
  { max: 88, label: '拆解课堂实训步骤…' },
  { max: 100, label: '即将完成…' },
];

/** 节点状态色（与卡片底色绑定） */
const ROADMAP_STATUS_META = {
  done: { label: '已完成', color: '#52c41a' },
  active: { label: '进行中', color: '#1677ff' },
  idle: { label: '待开始', color: '#8a9ab5' },
};

/** 学习资源六维标签与解析工具已统一到 utils/jobAssistant */

const SKILL_COLOR_SETS = [
  { bg: 'transparent', color: '#161a22', border: '#dcd8cd' },
  { bg: 'transparent', color: '#161a22', border: '#dcd8cd' },
  { bg: 'transparent', color: '#161a22', border: '#dcd8cd' },
  { bg: 'transparent', color: '#161a22', border: '#dcd8cd' },
  { bg: 'transparent', color: '#161a22', border: '#dcd8cd' },
  { bg: 'transparent', color: '#161a22', border: '#dcd8cd' },
  { bg: 'transparent', color: '#161a22', border: '#dcd8cd' },
  { bg: 'transparent', color: '#161a22', border: '#dcd8cd' },
];

/** 校内实训资源行：名称（类型）｜获取方式 */
function parseCampusResource(line) {
  const parts = String(line || '').split(/[｜|]/).map((p) => p.trim()).filter(Boolean);
  if (parts.length >= 2) {
    return { name: parts[0], link: parts.slice(1).join('｜') };
  }
  return { name: line || '', link: '' };
}

function GrowthPath({
  embedded = false,
  onNavigateToView,
  onProgressChange,
  onTaskCardRefreshed,
  initialActiveId = null,
  favoritesRevision = 0,
  onFavoritesChange,
}) {
  const navigate = useNavigate();
  const [profile, setProfile] = useState(null);
  const [profileLoading, setProfileLoading] = useState(true);
  const [favorites, setFavorites] = useState([]);
  const [favoritesLoading, setFavoritesLoading] = useState(true);
  const [hotJobs, setHotJobs] = useState([]);
  const [hotJobsLoading, setHotJobsLoading] = useState(true);
  const [activeId, setActiveId] = useState(null);
  // { [recommendationId]: { card, error, loading } }
  const [taskCards, setTaskCards] = useState({});
  const [taskQuota, setTaskQuota] = useState(null);
  const [taskRefreshing, setTaskRefreshing] = useState(false);
  // { [recommendationId]: { resources, error, loading } }
  const [jobResources, setJobResources] = useState({});
  const [resourceQuota, setResourceQuota] = useState(null);
  const [refreshing, setRefreshing] = useState(false);
  const [mindMapVisible, setMindMapVisible] = useState(false);
  const [mindMapFocusSkill, setMindMapFocusSkill] = useState(null);
  // { [recommendationId]: { taskSteps: Set<key>, pathSteps: Set<key>, resources: Set<key> } }
  const [progress, setProgress] = useState({});
  // 已解锁的资源：{ [recommendationId]: Set<resourceId> }
  // 打开过 MicroCourseReader 即视为解锁；取消「已完成」后回到该集合而非清空
  const [unlockedResources, setUnlockedResources] = useState({});
  // { [recommendationId]: boolean } 是否正在提交完成标记
  const [progressSubmitting, setProgressSubmitting] = useState({});
  // 内嵌图文微课阅读器
  const [readerResource, setReaderResource] = useState(null);
  const [readerSubmitting, setReaderSubmitting] = useState(false);
  // 高亮的节点（点击时间线节点滚动定位）
  const [highlightedSection, setHighlightedSection] = useState(null);
  const highlightTimerRef = useRef(null);

  const favoritedIds = useMemo(
    () => new Set(favorites.map((job) => job.id)),
    [favorites]
  );

  const favoriteSkillSet = useMemo(() => {
    const set = new Set();
    favorites.forEach((job) => {
      parseSkills(job.skills).forEach((skill) => set.add(skill.toLowerCase()));
    });
    return set;
  }, [favorites]);

  /** 与收藏岗位技能重叠、但尚未收藏的本周热招岗位 */
  const relatedHotJobs = useMemo(() => {
    if (favoriteSkillSet.size === 0) return [];
    return hotJobs.filter((job) => {
      if (favoritedIds.has(job.id)) return false;
      return parseSkills(job.skills).some((skill) => favoriteSkillSet.has(skill.toLowerCase()));
    });
  }, [hotJobs, favoriteSkillSet, favoritedIds]);

  const activeJob = favorites.find((job) => job.id === activeId) || null;
  const taskState = activeId != null ? taskCards[activeId] : null;
  const resourceState = activeId != null ? jobResources[activeId] : null;

  // ── 初始化：画像 + 收藏 + 热招 + 任务卡额度（彼此无依赖，并行拉取） ──────────
  useEffect(() => {
    const init = async () => {
      setFavoritesLoading(true);
      setHotJobsLoading(true);
      setProfileLoading(true);

      const [favRes, jobsRes, infoRes, quotaRes] = await Promise.allSettled([
        getFavorites(),
        getStudentWeeklyJobRecommendations(),
        getStudentPortalInfo(),
        getJobTaskCardQuota(),
      ]);
      if (favRes.status === 'fulfilled') {
        const list = Array.isArray(favRes.value?.data) ? favRes.value.data : [];
        setFavorites(list);
        if (list.length > 0) {
          const preferred =
            initialActiveId != null && list.some((job) => job.id === initialActiveId)
              ? initialActiveId
              : list[0].id;
          setActiveId(preferred);
        }
      } else {
        message.error('加载收藏岗位失败');
      }
      setFavoritesLoading(false);

      if (jobsRes.status === 'fulfilled') {
        const data = Array.isArray(jobsRes.value?.data) ? jobsRes.value.data : [];
        // 真实数据直接使用；接口无数据时保持空数组，由空态提示引导，不再用示例岗位兜底
        setHotJobs(data.filter((job) => String(job?.jobTitle || '').trim()));
      }
      setHotJobsLoading(false);

      if (infoRes.status === 'fulfilled' && infoRes.value?.data) {
        setProfile(infoRes.value.data);
      }
      setProfileLoading(false);

      if (quotaRes.status === 'fulfilled' && quotaRes.value?.data) {
        setTaskQuota(quotaRes.value.data);
      }
    };
    init();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // 发展助手内嵌：热门岗位等处收藏变更后重拉列表，避免切视图仍显示旧收藏
  useEffect(() => {
    if (!embedded || favoritesRevision < 1) return;
    let cancelled = false;
    (async () => {
      try {
        const favRes = await getFavorites();
        if (cancelled) return;
        const list = Array.isArray(favRes?.data) ? favRes.data : [];
        setFavorites(list);
        setActiveId((prev) => {
          if (prev != null && list.some((job) => job.id === prev)) return prev;
          return list[0]?.id ?? null;
        });
      } catch {
        // 同步失败不打断当前页
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [embedded, favoritesRevision]);

  // 父级从学习记录跳转时切换到对应岗位
  useEffect(() => {
    if (initialActiveId == null || !favorites.length) return;
    if (favorites.some((job) => job.id === initialActiveId)) {
      setActiveId(initialActiveId);
    }
  }, [initialActiveId, favorites]);

  // ── 切换岗位：读缓存加载进度/任务卡/资源（generate=false，无缓存不触发生成） ──
  useEffect(() => {
    if (activeId == null) return;
    loadProgress(activeId);
    loadTaskCard(activeId);
    loadResources(activeId);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeId]);

  const loadTaskCard = useCallback(
    async (recommendationId, force = false) => {
      if (!recommendationId) return;
      // 使用函数式 setState 读取，避免依赖外部 taskCards 触发重建
      setTaskCards((prev) => {
        if (!force && prev[recommendationId]?.card) return prev;
        return { ...prev, [recommendationId]: { ...(prev[recommendationId] || {}), loading: true, error: null } };
      });
      try {
        const res = await getJobTaskCard(recommendationId, force);
        setTaskCards((prev) => ({
          ...prev,
          [recommendationId]: { card: res?.data || null, loading: false, error: null },
        }));
        if (res?.quota) setTaskQuota(res.quota);
        // 首次生成成功时同步父级概览（刷新走 handleRefreshTaskCard）
        if (force && res?.data) {
          onTaskCardRefreshed?.(recommendationId);
        }
      } catch (err) {
        setTaskCards((prev) => ({
          ...prev,
          [recommendationId]: {
            ...(prev[recommendationId] || {}),
            loading: false,
            error: err?.message || '生成失败',
          },
        }));
      }
    },
    [onTaskCardRefreshed]
  );

  // ── 刷新当前岗位学习任务卡：需先完成全部步骤，每个收藏岗位每日 3 次 ──
  const handleRefreshTaskCard = async () => {
    if (activeId == null || taskRefreshing) return;
    const stepLines = taskState?.card ? parseLines(taskState.card.steps) : [];
    const doneCount = stepLines.filter((_, index) =>
      (progress[activeId]?.taskSteps || new Set()).has(String(index + 1))
    ).length;
    if (!stepLines.length || doneCount < stepLines.length) {
      message.warning('请先完成当前任务卡的全部步骤后再刷新');
      return;
    }
    setTaskRefreshing(true);
    try {
      const res = await refreshJobTaskCard(activeId);
      setTaskCards((prev) => ({
        ...prev,
        [activeId]: { card: res?.data || null, loading: false, error: null },
      }));
      if (res?.quota) setTaskQuota(res.quota);
      // 刷新后新任务卡步骤重新编号，本地 TASK_STEP / PATH_STEP 一并清空
      setProgress((prev) => {
        const job = prev[activeId] || { taskSteps: new Set(), pathSteps: new Set(), resources: new Set() };
        return { ...prev, [activeId]: { ...job, taskSteps: new Set(), pathSteps: new Set() } };
      });
      loadProgress(activeId);
      // 通知父级（发展助手首页概览）任务卡已刷新，以便同步 taskCard 与 jobProgress
      onTaskCardRefreshed?.(activeId);
      message.success('任务卡已刷新');
    } catch {
      // 完成度不足、额度不足等错误已由拦截器提示
    } finally {
      setTaskRefreshing(false);
    }
  };

  const loadResources = useCallback(
    async (recommendationId, force = false) => {
      if (!recommendationId) return;
      // 使用函数式 setState 读取，避免依赖外部 jobResources 触发重建
      setJobResources((prev) => {
        if (!force && prev[recommendationId]?.resources?.length) return prev;
        return { ...prev, [recommendationId]: { ...(prev[recommendationId] || {}), loading: true, error: null } };
      });
      try {
        // force=false：只读缓存；若后台已在生成则轮询等待
        // force=true：无缓存时异步启动生成并轮询，避免同步 HTTP 超时
        const res = await requestJobResources(recommendationId, force);
        setJobResources((prev) => ({
          ...prev,
          [recommendationId]: {
            resources: Array.isArray(res?.data) ? res.data : [],
            loading: false,
            error: null,
          },
        }));
        if (res?.quota) setResourceQuota(res.quota);
      } catch (err) {
        setJobResources((prev) => ({
          ...prev,
          [recommendationId]: {
            ...(prev[recommendationId] || {}),
            loading: false,
            error: err?.message || '加载失败',
          },
        }));
      }
    },
    []
  );

  const handleRefreshResources = async () => {
    if (activeId == null) return;
    setRefreshing(true);
    try {
      const res = await requestRefreshJobResources(activeId);
      setJobResources((prev) => ({
        ...prev,
        [activeId]: { resources: Array.isArray(res?.data) ? res.data : [], loading: false, error: null },
      }));
      if (res?.quota) setResourceQuota(res.quota);
      message.success('资源推荐已刷新');
    } catch {
      // 额度不足等错误已由拦截器提示
    } finally {
      setRefreshing(false);
    }
  };

  // ── 加载某岗位的学习进度（已完成资源/任务步骤/路径步骤 + 已解锁资源） ──────────────
  const loadProgress = useCallback(async (recommendationId) => {
    if (!recommendationId) return;
    try {
      const res = await getJobProgress(recommendationId);
      const data = res?.data || {};
      const toKeySet = (items) => new Set((Array.isArray(items) ? items : []).map((item) => String(item.key)));
      setProgress((prev) => ({
        ...prev,
        [recommendationId]: {
          taskSteps: toKeySet(data.taskSteps),
          pathSteps: toKeySet(data.pathSteps),
          resources: toKeySet(data.resources),
        },
      }));
      setUnlockedResources((prev) => ({
        ...prev,
        [recommendationId]: toKeySet(data.unlockedResources),
      }));
    } catch {
      // 进度加载失败不阻塞页面
    }
  }, []);

  // ── 标记完成 / 取消完成 ────────────────────────────────────────────────
  // 后端 itemType（TASK_STEP/PATH_STEP/RESOURCE）与 progress 状态字段（taskSteps/pathSteps/resources）
  // 命名不一致，读取与乐观更新必须先映射到状态字段，否则写入无效字段、界面不即时更新。
  const PROGRESS_STATE_KEY = {
    TASK_STEP: 'taskSteps',
    PATH_STEP: 'pathSteps',
    RESOURCE: 'resources',
  };
  const handleToggleProgress = async (itemType, itemKey, itemTitle) => {
    if (activeId == null) return;
    const key = String(itemKey);
    const stateKey = PROGRESS_STATE_KEY[itemType] || itemType;

    // 学习资源（RESOURCE）必须先解锁（打开过 MicroCourseReader）才能标记完成，
    // 防止学生不学就刷完成；取消完成走原逻辑。
    if (itemType === 'RESOURCE') {
      const unlocked = unlockedResources[activeId] || new Set();
      const done = (progress[activeId]?.resources || new Set()).has(key);
      if (!done && !unlocked.has(key)) {
        message.warning('请先点击「开始学习」解锁本资源');
        return;
      }
    }

    const current = progress[activeId] || { taskSteps: new Set(), pathSteps: new Set(), resources: new Set() };
    const done = current[stateKey]?.has(key) || false;
    const payload = { recommendationId: activeId, itemType, itemKey: key, itemTitle };
    setProgressSubmitting((prev) => ({ ...prev, [`${activeId}-${itemType}-${key}`]: true }));
    try {
      if (done) {
        await unmarkProgressCompleted(payload);
        setProgress((prev) => {
          const job = prev[activeId] || { taskSteps: new Set(), pathSteps: new Set(), resources: new Set() };
          const next = new Set(job[stateKey] || []);
          next.delete(key);
          return { ...prev, [activeId]: { ...job, [stateKey]: next } };
        });
        onProgressChange?.(stateKey, key, false);
        message.success('已取消完成标记');
      } else {
        await markProgressCompleted(payload);
        setProgress((prev) => {
          const job = prev[activeId] || { taskSteps: new Set(), pathSteps: new Set(), resources: new Set() };
          const next = new Set(job[stateKey] || []);
          next.add(key);
          return { ...prev, [activeId]: { ...job, [stateKey]: next } };
        });
        onProgressChange?.(stateKey, key, true);
        message.success('已完成，继续保持！');
      }
    } catch {
      // 错误已由拦截器提示
    } finally {
      setProgressSubmitting((prev) => ({ ...prev, [`${activeId}-${itemType}-${key}`]: false }));
    }
  };

  // 阅读器打开即解锁资源（本地 + 服务端）；同一资源多次打开不重复写入 Set
  const handleReaderUnlock = useCallback((resource) => {
    if (!resource || activeId == null || resource.id == null) return;
    const key = String(resource.id);
    setUnlockedResources((prev) => {
      const set = new Set(prev[activeId] || []);
      if (set.has(key)) return prev;
      const next = new Set(set);
      next.add(key);
      return { ...prev, [activeId]: next };
    });
    unlockLearningResource({
      recommendationId: activeId,
      itemKey: key,
      itemTitle: resource.title,
    }).catch(() => {
      // 解锁失败时标记完成会被后端拒绝；错误已由拦截器提示
    });
  }, [activeId]);

  // ── 图文微课阅读器：完成本章 / 取消完成（复用 RESOURCE 完成标记逻辑） ────
  const handleReaderToggleDone = async () => {
    if (!readerResource || activeId == null || readerSubmitting) return;
    setReaderSubmitting(true);
    try {
      await handleToggleProgress('RESOURCE', readerResource.id, readerResource.title);
    } finally {
      setReaderSubmitting(false);
    }
  };

  const handleToggleRelatedFavorite = async (job, event) => {
    event.stopPropagation();
    try {
      await addFavorite(job.id);
      message.success('收藏成功，已加入成长链路');
      const favRes = await getFavorites();
      const list = Array.isArray(favRes?.data) ? favRes.data : [];
      setFavorites(list);
      if (list.length === 1) {
        setActiveId(list[0].id);
      }
      onFavoritesChange?.();
    } catch {
      // 超过 6 个收藏等错误已由拦截器提示
    }
  };

  // ── 时间线：节点状态计算 ────────────────────────────────────────────────
  // 仅统计当前选中岗位；画像/岗位/能力图谱为静态信息，完成与否取决于数据是否就绪
  const roadmapStatus = useMemo(() => {
    const taskCardData = activeId != null ? taskCards[activeId] : null;
    const jobProgressData = activeId != null ? progress[activeId] : null;
    const unlockedSet = activeId != null ? unlockedResources[activeId] : null;

    const resourceTotal = (jobResources[activeId]?.resources?.length) || 0;
    const resourceDone = jobProgressData?.resources?.size || 0;
    const unlocked = unlockedSet?.size || 0;

    const stepLines = taskCardData?.card ? parseLines(taskCardData.card.steps) : [];
    const pathLines = taskCardData?.card ? parseLines(taskCardData.card.learningPath) : [];
    const taskDone = jobProgressData?.taskSteps?.size || 0;
    const pathDone = jobProgressData?.pathSteps?.size || 0;

    const hasProfile = Boolean(profile?.name);
    const hasFavorites = favorites.length > 0;
    const hasSkillMap = Boolean(activeJob && parseSkills(activeJob.skills).length > 0);
    const hasTaskCard = Boolean(taskCardData?.card);

    const isAllDone = (done, total) => total > 0 && done >= total;

    return {
      profile: { status: hasProfile ? 'done' : 'idle', value: hasProfile ? '画像已就绪' : '加载中' },
      jobs: { status: hasFavorites ? 'done' : 'idle', value: `${favorites.length} 个收藏` },
      map: {
        status: hasSkillMap ? 'active' : 'idle',
        value: hasSkillMap ? `${parseSkills(activeJob?.skills || '').length} 项技能` : '请先选岗位',
      },
      task: {
        status: !hasTaskCard ? 'idle' : isAllDone(taskDone, stepLines.length) ? 'done' : 'active',
        value: !hasTaskCard
          ? '未生成'
          : `${taskDone}/${stepLines.length} 步`,
      },
      resource: {
        status:
          resourceTotal === 0
            ? 'idle'
            : isAllDone(resourceDone, resourceTotal)
              ? 'done'
              : unlocked > 0
                ? 'active'
                : 'idle',
        value:
          resourceTotal === 0
            ? '未生成'
            : `${resourceDone}/${resourceTotal} 已完成 · ${unlocked} 已解锁`,
      },
      path: {
        status: !hasTaskCard
          ? 'idle'
          : pathLines.length === 0
            ? 'idle'
            : isAllDone(pathDone, pathLines.length)
              ? 'done'
              : 'active',
        value: !hasTaskCard || pathLines.length === 0
          ? '未生成'
          : `${pathDone}/${pathLines.length} 步`,
      },
    };
  }, [activeId, jobResources, taskCards, progress, unlockedResources, profile, favorites, activeJob]);

  // ── 节点点击：滚动到对应区块并高亮 1.5 秒 ────────────────────────────────
  const handleRoadmapNodeClick = useCallback((key) => {
    const target = document.getElementById(`gpath-section-${key}`);
    if (target) {
      target.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
    setHighlightedSection(key);
    if (highlightTimerRef.current) clearTimeout(highlightTimerRef.current);
    highlightTimerRef.current = setTimeout(() => setHighlightedSection(null), 1500);
  }, []);

  useEffect(() => () => {
    if (highlightTimerRef.current) clearTimeout(highlightTimerRef.current);
  }, []);

  // ── 渲染：链路总览（时间线卡片） ──────────────────────────────────────
  const renderRoadmapTimeline = () => (
    <div className="gpath-roadmap">
      {CHAIN_STEPS.map((step, index) => {
        const meta = roadmapStatus[step.key] || { status: 'idle', value: '' };
        const statusLabel = ROADMAP_STATUS_META[meta.status]?.label || ROADMAP_STATUS_META.idle.label;
        return (
          <button
            type="button"
            key={step.key}
            className={`gpath-roadmap-node status-${meta.status}`}
            onClick={() => handleRoadmapNodeClick(step.key)}
          >
            <span className="gpath-roadmap-node-num">{String(index + 1).padStart(2, '0')}</span>
            <span className="gpath-roadmap-node-index">{index + 1}</span>
            <span className="gpath-roadmap-node-text">
              <span className="gpath-roadmap-node-label">{step.label}</span>
              <span className="gpath-roadmap-node-desc">{step.desc}</span>
              <span className="gpath-roadmap-node-meta">
                <span className="gpath-roadmap-node-value">{meta.value}</span>
                <span className="gpath-roadmap-node-status">{statusLabel}</span>
              </span>
            </span>
          </button>
        );
      })}
    </div>
  );

  // ── 渲染：① 我的画像 主体（外层 <section> 由 pageContent 渲染） ──────────
  const renderProfileBody = () => {
    const generatedCount = Object.values(taskCards).filter((s) => s?.card).length;
    const resourceCount = Object.values(jobResources).filter((s) => s?.resources?.length).length;
    const stats = [
      { label: '收藏岗位', value: favorites.length },
      { label: '本周热招', value: hotJobs.length },
      { label: '已生成任务卡', value: generatedCount },
      { label: '已获取资源组', value: resourceCount },
    ];
    return (
      <>
        <div className="gpath-section-head">
          <span className="gpath-section-num">01</span>
          <h2 className="gpath-section-title">我的画像</h2>
          <span className="gpath-section-tag">推送依据</span>
        </div>
        <div className="gpath-profile">
          <div className="gpath-profile-info">
            {profileLoading ? (
              <Spin size="small" />
            ) : (
              <>
                <div className="gpath-profile-row">
                  <span className="gpath-profile-label">姓名</span>
                  <span className="gpath-profile-value">{profile?.name || '—'}</span>
                  <span className="gpath-profile-label">学号</span>
                  <span className="gpath-profile-value">{profile?.studentNo || '—'}</span>
                </div>
                <div className="gpath-profile-row">
                  <span className="gpath-profile-label">专业</span>
                  <span className="gpath-profile-value">{profile?.majorName || profile?.major || '—'}</span>
                  <span className="gpath-profile-label">年级</span>
                  <span className="gpath-profile-value">{profile?.grade || '—'}</span>
                </div>
                <div className="gpath-profile-row">
                  <span className="gpath-profile-label">班级</span>
                  <span className="gpath-profile-value">{profile?.className || '—'}</span>
                  <span className="gpath-profile-label">学院</span>
                  <span className="gpath-profile-value">{profile?.collegeName || profile?.college || '—'}</span>
                </div>
              </>
            )}
          </div>
          <div className="gpath-profile-stats">
            {stats.map((stat) => (
              <div className="gpath-profile-stat" key={stat.label}>
                <span className="gpath-profile-stat-value">{stat.value}</span>
                <span className="gpath-profile-stat-label">{stat.label}</span>
              </div>
            ))}
          </div>
        </div>
      </>
    );
  };

  // ── 渲染：② 热门岗位 ──────────────────────────────────────────────────
  const renderJobs = () => {
    if (favoritesLoading) {
      return (
        <div className="gpath-loading">
          <Spin size="large" tip="加载收藏岗位中…" />
        </div>
      );
    }

    if (favorites.length === 0) {
      return (
        <div className="gpath-empty">
          <Empty description="暂无收藏的岗位，无法生成成长链路" />
          <p className="gpath-empty-hint">
            请先在「热门岗位推荐」页面收藏感兴趣的岗位（最多 6 个），系统将据此生成专属资源推送链路。
          </p>
          {onNavigateToView ? (
            <Button type="primary" onClick={() => onNavigateToView('hotJobs')}>
              去收藏岗位
            </Button>
          ) : (
            <Button type="primary" onClick={() => navigate('/student/assistant')}>
              去发展助手
            </Button>
          )}
        </div>
      );
    }

    return (
      <>
        <div className="gpath-sub-title">已收藏岗位（点击查看完整链路）</div>
        <div className="gpath-fav-grid">
          {favorites.map((job, index) => {
            const colorSet = SKILL_COLOR_SETS[index % SKILL_COLOR_SETS.length];
            const selected = job.id === activeId;
            return (
              <button
                type="button"
                key={job.id}
                className={`gpath-fav-card ${selected ? 'selected' : ''}`}
                onClick={() => setActiveId(job.id)}
              >
                <div className="gpath-fav-card-head">
                  <h3 className="gpath-fav-title">{job.jobTitle || '软件工程师'}</h3>
                  {selected && <span className="gpath-fav-badge">当前链路</span>}
                </div>
                <div className="gpath-fav-salary">
                  <span>{job.salary || '薪资面议'}</span>
                </div>
                <div className="gpath-fav-skills">
                  {parseSkills(job.skills).slice(0, 4).map((skill) => (
                    <Tag
                      key={skill}
                      style={{
                        background: colorSet.bg,
                        color: colorSet.color,
                        borderColor: colorSet.border,
                        marginBottom: 4,
                      }}
                    >
                      {skill}
                    </Tag>
                  ))}
                </div>
              </button>
            );
          })}
        </div>

        {hotJobsLoading ? (
          <div className="gpath-loading">
            <Spin size="large" tip="加载本周热招岗位中…" />
          </div>
        ) : (
          relatedHotJobs.length > 0 && (
            <>
              <Divider className="gpath-divider" />
              <div className="gpath-sub-title">
                为你匹配的同技能热招岗位
                <span className="gpath-sub-title-hint">（与收藏岗位技能重叠，收藏后即可加入链路）</span>
              </div>
              <div className="gpath-related-grid">
                {relatedHotJobs.map((job) => {
                  const matchedSkills = parseSkills(job.skills).filter((skill) =>
                    favoriteSkillSet.has(skill.toLowerCase())
                  );
                  return (
                    <div className="gpath-related-card" key={job.id || job.jobTitle}>
                      <div className="gpath-related-head">
                        <h3 className="gpath-related-title">{job.jobTitle || '软件工程师'}</h3>
                        <Tooltip title="收藏此岗位">
                          <button
                            type="button"
                            className="gpath-related-fav"
                            onClick={(e) => handleToggleRelatedFavorite(job, e)}
                          >
                            <StarOutlined />
                          </button>
                        </Tooltip>
                      </div>
                      <div className="gpath-related-salary">{job.salary || '薪资面议'}</div>
                      <div className="gpath-related-skills">
                        {matchedSkills.length > 0 && (
                          <span className="gpath-related-match-label">技能匹配：</span>
                        )}
                        {matchedSkills.map((skill) => (
                          <Tag key={skill} color="green" style={{ marginBottom: 4 }}>{skill}</Tag>
                        ))}
                      </div>
                      <div className="gpath-related-footer">
                        <a
                          className="gpath-related-link"
                          href={resolveJobSearchLink(job)}
                          target="_blank"
                          rel="noreferrer"
                        >
                          <span>在 BOSS 直聘查看</span>
                        </a>
                        <span className="gpath-related-source">
                          {job.source === '示例数据' ? '示例数据' : 'AI 生成'}
                        </span>
                      </div>
                    </div>
                  );
                })}
              </div>
            </>
          )
        )}
      </>
    );
  };

  // ── 渲染：③ 能力图谱 ──────────────────────────────────────────────────
  const renderSkillMap = () => {
    if (!activeJob) return null;
    const skills = parseSkills(activeJob.skills);
    return (
      <>
        <div className="gpath-section-head">
          <span className="gpath-section-num">03</span>
          <h2 className="gpath-section-title">岗位能力图谱</h2>
          <span className="gpath-section-tag">
            {activeJob.jobTitle || ''} · 点击技能查看学习建议
          </span>
          <button
            type="button"
            className="gpath-mindmap-btn"
            onClick={() => {
              setMindMapFocusSkill(null);
              setMindMapVisible(true);
            }}
            disabled={!skills.length}
          >
            <span>完整思维导图</span>
          </button>
        </div>
        <div className="gpath-skillmap">
          {skills.length === 0 ? (
            <Empty description="暂无技能数据" image={Empty.PRESENTED_IMAGE_SIMPLE} />
          ) : (
            skills.map((skill, index) => {
              const colorSet = SKILL_COLOR_SETS[index % SKILL_COLOR_SETS.length];
              return (
                <Tag
                  key={skill}
                  className="gpath-skillmap-tag"
                  style={{
                    background: colorSet.bg,
                    color: colorSet.color,
                    borderColor: colorSet.border,
                  }}
                  onClick={() => {
                    setMindMapFocusSkill(skill);
                    setMindMapVisible(true);
                  }}
                >
                  <span>{skill}</span>
                </Tag>
              );
            })
          )}
        </div>
      </>
    );
  };

  // ── 渲染：④ 学习任务卡 ────────────────────────────────────────────────
  const renderTaskCard = () => {
    if (!activeJob) return null;
    const taskCard = taskState?.card;
    const loading = Boolean(taskState?.loading);
    const error = taskState?.error;
    const stepLines = taskCard ? parseLines(taskCard.steps) : [];
    const doneSteps = stepLines.filter((_, index) =>
      (progress[activeId]?.taskSteps || new Set()).has(String(index + 1))
    ).length;
    const allStepsDone = stepLines.length > 0 && doneSteps >= stepLines.length;
    // 任务卡板块池：生成 + 刷新合计（板块 5 次）；「刷新」操作类型独立 3 次
    const boardRemaining = taskQuota?.boardRemaining ?? taskQuota?.remaining ?? null;
    const refreshRemaining = taskQuota?.refreshRemaining ?? taskQuota?.jobRefreshRemaining ?? null;
    const refreshEnabled = taskCard && allStepsDone && refreshRemaining !== 0 && boardRemaining !== 0;

    return (
      <>
        <div className="gpath-section-head">
          <span className="gpath-section-num">04</span>
          <h2 className="gpath-section-title">学习任务卡</h2>
          <span className="gpath-section-tag">以岗定教 · 企业任务 → 课堂实训</span>
          <div className="gpath-section-actions">
            {taskQuota && (
              <Tooltip title="任务卡板块每日共 5 次（生成+刷新），每日 00:00 重置，与学习资源板块额度相互独立">
                <span className="gpath-quota">板块剩余 {boardRemaining}/{taskQuota.boardTotal} 次</span>
              </Tooltip>
            )}
            {taskCard && refreshRemaining !== null && (
              <Tooltip
                title={!allStepsDone
                  ? '完成当前任务卡的全部步骤后才能刷新'
                  : refreshRemaining === 0
                    ? '任务卡刷新今日最多 3 次，已用完'
                    : boardRemaining === 0
                      ? '任务卡板块次数已用完'
                      : '刷新后由 AI 重新生成任务卡，需重新完成各步骤'}
              >
                <Button
                  type="text"
                  icon={<ReloadOutlined className={taskRefreshing ? 'spin' : ''} />}
                  onClick={handleRefreshTaskCard}
                  loading={taskRefreshing}
                  disabled={!refreshEnabled}
                >
                  刷新剩余 {refreshRemaining}/{taskQuota?.refreshTotal}
                </Button>
              </Tooltip>
            )}
          </div>
        </div>

        {loading ? (
          <div className="gpath-loading">
            <AiGenerationProgress
              active
              title="AI 正在生成学习任务卡"
              hint="后台异步处理中，通常约 1–2 分钟，请保持本页打开"
              stages={TASK_CARD_STAGES}
            />
          </div>
        ) : error && !taskCard ? (
          <div className="gpath-empty">
            <Empty description="学习任务卡生成失败" />
            <p className="gpath-empty-hint">{error}</p>
            <Button type="primary" onClick={() => loadTaskCard(activeId, true)}>
              重新生成
            </Button>
          </div>
        ) : !taskCard ? (
          <div className="gpath-empty">
            <Empty description="尚未加载本岗位的学习任务卡" />
            <p className="gpath-empty-hint">
              点击后先获取已有任务卡；没有缓存时再由 AI 生成，避免收藏岗位时立即触发长请求。
            </p>
            <Button
              type="primary"
              icon={<ExperimentOutlined />}
              onClick={() => loadTaskCard(activeId, true)}
              disabled={taskQuota ? taskQuota.remaining <= 0 : false}
            >
              生成/查看任务卡
            </Button>
          </div>
        ) : (
          <>
            {taskRefreshing && (
              <AiGenerationProgress
                active
                variant="inline"
                title="AI 正在刷新学习任务卡"
                hint="生成完成前仍展示旧任务卡，成功后会自动替换"
                stages={TASK_CARD_STAGES}
              />
            )}
          <div className="gpath-taskcard">
            <div className="gpath-taskcard-title-row">
              <h3 className="gpath-taskcard-title">
                {taskCard.taskName || '学习任务'}
              </h3>
              <div className="gpath-taskcard-meta">
                <span>{taskCard.source === '示例数据' ? '示例数据' : 'AI 生成'}</span>
                {taskCard.generatedAt && (
                  <span>生成于 {String(taskCard.generatedAt).slice(0, 10)}</span>
                )}
              </div>
            </div>

            {taskCard.workScenario && (
              <div className="gpath-taskcard-block">
                <div className="gpath-taskcard-block-label">工作情境</div>
                <p className="gpath-taskcard-scenario">{taskCard.workScenario}</p>
              </div>
            )}

            {taskCard.steps && (
              <div className="gpath-taskcard-block">
                <div className="gpath-taskcard-block-label">
                  分步操作指南
                  {(() => {
                    const stepLines = parseLines(taskCard.steps);
                    const doneSteps = stepLines.filter((_, index) =>
                      (progress[activeId]?.taskSteps || new Set()).has(String(index + 1))
                    ).length;
                    return (
                      <span className="gpath-taskcard-step-progress">
                        已完成 {doneSteps}/{stepLines.length}
                      </span>
                    );
                  })()}
                </div>
                <ol className="gpath-taskcard-steps">
                  {parseLines(taskCard.steps).map((step, index) => {
                    const stepKey = String(index + 1);
                    const stepDone = (progress[activeId]?.taskSteps || new Set()).has(stepKey);
                    const submitting = Boolean(progressSubmitting[`${activeId}-TASK_STEP-${stepKey}`]);
                    return (
                      <li
                        key={index}
                        className={`gpath-taskcard-step ${stepDone ? 'done' : ''}`}
                      >
                        <span className="gpath-taskcard-step-num">{index + 1}</span>
                        <span>{step}</span>
                        <button
                          type="button"
                          className={`gpath-taskcard-step-check ${stepDone ? 'checked' : ''}`}
                          onClick={() => handleToggleProgress('TASK_STEP', stepKey, step)}
                          disabled={submitting}
                          title={stepDone ? '点击取消该步完成标记' : '点击标记该步骤完成'}
                        >
                          {stepDone ? <CheckCircleOutlined /> : <CheckOutlined />}
                          <span>{stepDone ? '已完成' : '完成'}</span>
                        </button>
                      </li>
                    );
                  })}
                </ol>
              </div>
            )}

            <div className="gpath-taskcard-aside">
              {taskCard.safetyPoints && (
                <details className="gpath-taskcard-fold">
                  <summary className="gpath-taskcard-fold-summary safety">
                    安全要点
                    <span className="gpath-taskcard-fold-count">
                      {parseLines(taskCard.safetyPoints).length} 条
                    </span>
                  </summary>
                  <ul className="gpath-taskcard-safety">
                    {parseLines(taskCard.safetyPoints).map((point, index) => (
                      <li key={index}>{point}</li>
                    ))}
                  </ul>
                </details>
              )}

              {(taskCard.knowledgePoints || taskCard.skillPoints) && (
                <details className="gpath-taskcard-fold">
                  <summary className="gpath-taskcard-fold-summary">
                    知识与技能
                    <span className="gpath-taskcard-fold-count">
                      {parseLines(taskCard.knowledgePoints).length
                        + parseLines(taskCard.skillPoints).length} 项
                    </span>
                  </summary>
                  <div className="gpath-taskcard-pair">
                    {taskCard.knowledgePoints && (
                      <div className="gpath-taskcard-block">
                        <div className="gpath-taskcard-block-label muted">知识点</div>
                        <ul className="gpath-taskcard-points knowledge">
                          {parseLines(taskCard.knowledgePoints).map((point, index) => (
                            <li key={index}>{point}</li>
                          ))}
                        </ul>
                      </div>
                    )}
                    {taskCard.skillPoints && (
                      <div className="gpath-taskcard-block">
                        <div className="gpath-taskcard-block-label muted">技能点</div>
                        <ul className="gpath-taskcard-points skill">
                          {parseLines(taskCard.skillPoints).map((point, index) => (
                            <li key={index}>{point}</li>
                          ))}
                        </ul>
                      </div>
                    )}
                  </div>
                </details>
              )}

              {taskCard.campusResources && (
                <details className="gpath-taskcard-fold">
                  <summary className="gpath-taskcard-fold-summary">
                    实训资源
                    <span className="gpath-taskcard-fold-count">
                      {parseLines(taskCard.campusResources).length} 项
                    </span>
                  </summary>
                  <div className="gpath-taskcard-resources">
                    {parseLines(taskCard.campusResources).map((line, index) => {
                      const { name, link } = parseCampusResource(line);
                      return (
                        <div className="gpath-taskcard-resource" key={index}>
                          <span className="gpath-taskcard-resource-num">{String(index + 1).padStart(2, '0')}</span>
                          <div className="gpath-taskcard-resource-body">
                            <span className="gpath-taskcard-resource-name">{name}</span>
                            {link && (
                              <span className="gpath-taskcard-resource-link">
                                {link}
                              </span>
                            )}
                          </div>
                        </div>
                      );
                    })}
                  </div>
                </details>
              )}
            </div>
          </div>
          </>
        )}
      </>
    );
  };

  // ── 渲染：⑥ 个性化学习路径 ────────────────────────────────────────────
  const renderLearningPath = () => {
    if (!activeJob) return null;
    const taskCard = taskState?.card;
    const pathLines = taskCard ? parseLines(taskCard.learningPath) : [];
    const donePathSteps = pathLines.filter((_, index) =>
      (progress[activeId]?.pathSteps || new Set()).has(String(index + 1))
    ).length;
    const pathPercent = pathLines.length
      ? Math.round((donePathSteps / pathLines.length) * 100)
      : 0;
    return (
      <>
        <div className="gpath-section-head">
          <span className="gpath-section-num">06</span>
          <h2 className="gpath-section-title">个性化学习路径</h2>
          <span className="gpath-section-tag">基于专业学情 · 微课 → 仿真 → 实训</span>
        </div>
        {pathLines.length === 0 ? (
          <div className="gpath-empty">
            <Empty
              description={taskCard ? '任务卡未返回学习路径' : '生成学习任务卡后自动展示个性化路径'}
              image={Empty.PRESENTED_IMAGE_SIMPLE}
            />
          </div>
        ) : (
          <div className="gpath-path">
            {pathLines.map((line, index) => {
              const isLast = index === pathLines.length - 1;
              const stepKey = String(index + 1);
              const stepDone = (progress[activeId]?.pathSteps || new Set()).has(stepKey);
              const submitting = Boolean(progressSubmitting[`${activeId}-PATH_STEP-${stepKey}`]);
              return (
                <div className={`gpath-path-step ${stepDone ? 'done' : ''}`} key={index}>
                  <div className={`gpath-path-num ${stepDone ? 'done' : ''}`}>{String(index + 1).padStart(2, '0')}</div>
                  {!isLast && <span className="gpath-path-line" />}
                  <div className="gpath-path-body">
                    <span className="gpath-path-step-text">{line}</span>
                    <button
                      type="button"
                      className={`gpath-path-step-check ${stepDone ? 'checked' : ''}`}
                      onClick={() => handleToggleProgress('PATH_STEP', stepKey, line)}
                      disabled={submitting}
                    >
                      {stepDone ? <CheckCircleOutlined /> : <CheckOutlined />}
                      <span>{stepDone ? '已完成' : '标记完成'}</span>
                    </button>
                  </div>
                </div>
              );
            })}
          </div>
        )}
        {pathLines.length > 0 && (
          <div className={`gpath-path-progress ${pathPercent === 100 ? 'is-complete' : ''}`}>
            <div className="gpath-path-ring">
              <Progress
                type="circle"
                percent={pathPercent}
                size={92}
                strokeColor={
                  pathPercent === 100
                    ? '#161a22'
                    : '#1a41e6'
                }
                strokeWidth={8}
                format={(p) => (
                  <div className="gpath-path-ring-text">
                    <span className="gpath-path-ring-num">{p}</span>
                    <span className="gpath-path-ring-unit">%</span>
                  </div>
                )}
              />
            </div>
            <div className="gpath-path-progress-body">
              <div className="gpath-path-progress-head">
                <span className="gpath-path-progress-title">
                  {pathPercent === 100 ? '路径已全部完成' : '路径完成度'}
                </span>
                {pathPercent === 100 ? (
                  <span className="gpath-path-progress-done">
                    <CheckCircleOutlined /> 全部完成
                  </span>
                ) : (
                  <span className="gpath-path-progress-tip">
                    坚持每天完成 1 步，{Math.max(1, pathLines.length - donePathSteps)} 天可达标
                  </span>
                )}
              </div>
              <div className="gpath-path-progress-stats">
                <div className="gpath-path-progress-stat done">
                  <span className="gpath-path-progress-stat-value">{donePathSteps}</span>
                  <span className="gpath-path-progress-stat-label">已完成</span>
                </div>
                <div className="gpath-path-progress-stat active">
                  <span className="gpath-path-progress-stat-value">{Math.min(1, pathLines.length - donePathSteps) && donePathSteps < pathLines.length ? 1 : 0}</span>
                  <span className="gpath-path-progress-stat-label">进行中</span>
                </div>
                <div className="gpath-path-progress-stat pending">
                  <span className="gpath-path-progress-stat-value">{Math.max(0, pathLines.length - donePathSteps - (donePathSteps < pathLines.length ? 1 : 0))}</span>
                  <span className="gpath-path-progress-stat-label">待完成</span>
                </div>
                <div className="gpath-path-progress-stat total">
                  <span className="gpath-path-progress-stat-value">{pathLines.length}</span>
                  <span className="gpath-path-progress-stat-label">总步数</span>
                </div>
              </div>
              <div className="gpath-path-progress-bar">
                <span className="gpath-path-progress-bar-done" style={{ width: `${pathPercent}%` }} />
                <span
                  className="gpath-path-progress-bar-active"
                  style={{
                    width: `${pathPercent >= 100 ? 0 : Math.min(100 - pathPercent, (100 / pathLines.length))}%`,
                    left: `${pathPercent}%`,
                  }}
                />
              </div>
            </div>
          </div>
        )}
      </>
    );
  };

  // ── 渲染：⑤ 学习资源 ──────────────────────────────────────────────────
  const renderResources = () => {
    if (!activeJob) return null;
    const resources = resourceState?.resources || [];
    const loading = Boolean(resourceState?.loading);
    const error = resourceState?.error;
    const manualRemaining = resourceQuota?.manualRemaining ?? null;
    const boardRemaining = resourceQuota?.boardRemaining ?? resourceQuota?.autoRemaining ?? null;
    const refreshDisabled = refreshing || manualRemaining === 0 || boardRemaining === 0;

    return (
      <>
        <div className="gpath-section-head">
          <span className="gpath-section-num">05</span>
          <h2 className="gpath-section-title">岗位学习资源</h2>
          <span className="gpath-section-tag">理论 · 实战 · 技能 · 项目 · 行业 · 学习路径</span>
          <div className="gpath-section-actions">
            {resourceQuota && (
              <Tooltip title="资源板块每日共 5 次（生成+刷新）；刷新按钮今日最多 3 次，每日 00:00 重置。">
                <span className="gpath-quota">
                  板块剩余 {boardRemaining}/{resourceQuota.boardTotal} · 刷新剩余{' '}
                  {manualRemaining}/{resourceQuota.manualTotal}
                </span>
              </Tooltip>
            )}
            <Button
              type="text"
              icon={<ReloadOutlined className={refreshing ? 'spin' : ''} />}
              onClick={handleRefreshResources}
              loading={refreshing}
              disabled={refreshDisabled}
            >
              {manualRemaining === 0 ? '今日刷新次数已用完' : '刷新资源'}
            </Button>
          </div>
        </div>

        {loading ? (
          <div className="gpath-loading">
            <AiGenerationProgress
              active
              title="AI 正在生成学习资源"
              hint="后台异步处理中，通常 1–3 分钟，请保持本页打开"
            />
          </div>
        ) : error && !resources.length ? (
          <div className="gpath-empty">
            <Empty description="获取学习资源失败" />
            <p className="gpath-empty-hint">{error}</p>
            <Button type="primary" onClick={() => loadResources(activeId, true)}>
              重新获取
            </Button>
          </div>
        ) : resources.length === 0 ? (
          <div className="gpath-empty">
            <Empty description="尚未加载学习资源" />
            <p className="gpath-empty-hint">
              点击后先获取已有资源；没有缓存时再由 AI 生成六维学习资源。
            </p>
            <Button type="primary" onClick={() => loadResources(activeId, true)}>
              生成/查看资源
            </Button>
          </div>
        ) : (
          <>
            {refreshing && (
              <AiGenerationProgress
                active
                variant="inline"
                title="AI 正在刷新学习资源"
                hint="生成完成前仍展示旧资源，成功后会自动替换"
              />
            )}
            {resources.length > 0 && (() => {
              const doneResourceCount = resources.filter((r) =>
                (progress[activeId]?.resources || new Set()).has(String(r.id))
              ).length;
              const resourcePercent = Math.round((doneResourceCount / resources.length) * 100);
              return (
                <div className="gpath-resources-progress">
                  <span className="gpath-resources-progress-label">
                    资源学习进度
                  </span>
                  <Progress
                    percent={resourcePercent}
                    size="small"
                    strokeColor="#161a22"
                    format={() => `${doneResourceCount}/${resources.length} 项`}
                  />
                  {resourcePercent === 100 && (
                    <span className="gpath-resources-progress-done">
                      <CheckCircleOutlined /> 全部完成
                    </span>
                  )}
                </div>
              );
            })()}
            <div className="gpath-resources-grid">
              {resources.map((resource, index) => {
                const tag =
                  DIMENSION_TAGS[resource.sortOrder] ||
                  DIMENSION_TAGS[index + 1] ||
                  { label: '推荐', color: 'default' };
                const resourceDone = (progress[activeId]?.resources || new Set()).has(String(resource.id));
                const resourceUnlocked = (unlockedResources[activeId] || new Set()).has(String(resource.id));
                const submitting = Boolean(progressSubmitting[`${activeId}-RESOURCE-${resource.id}`]);
                return (
                  <div className={`gpath-resource-card ${resourceDone ? 'done' : ''} ${!resourceDone && !resourceUnlocked ? 'locked' : ''}`} key={resource.id || index}>
                    <div className="gpath-resource-card-head">
                      <Tag color={tag.color}>{tag.label}</Tag>
                      {resourceDone ? (
                        <Tag color="green" icon={<CheckCircleOutlined />}>已完成</Tag>
                      ) : resourceUnlocked ? (
                        <Tag color="blue">已解锁</Tag>
                      ) : (
                        <Tag>未解锁</Tag>
                      )}
                    </div>
                    <h4 className="gpath-resource-title">
                      {resource.title}
                    </h4>
                    <p className="gpath-resource-desc">{resource.description}</p>
                    <div className="gpath-resource-guide">
                      <div className="gpath-resource-guide-label">资源来源</div>
                      <p>{resource.guide}</p>
                    </div>
                    <div className="gpath-resource-card-actions">
                      <div className="gpath-resource-card-actions-left">
                        <Button
                          type="primary"
                          size="small"
                          onClick={() => setReaderResource(resource)}
                        >
                          开始学习
                        </Button>
                        <Tooltip
                          title={
                            isHttpUrl(resource.link)
                              ? '点击前往资源详情页'
                              : '详情链暂缺，已跳转到来源平台搜索'
                          }
                        >
                          <a
                            className="gpath-resource-open-btn"
                            href={resolveResourceOpenLink(resource)}
                            target="_blank"
                            rel="noreferrer"
                          >
                            原资源
                          </a>
                        </Tooltip>
                      </div>
                      <Tooltip
                        title={
                          resourceDone
                            ? '点击取消该资源完成标记'
                            : resourceUnlocked
                              ? '已学习，点击标记完成'
                              : '请先点击「开始学习」解锁本资源'
                        }
                      >
                        <Button
                          type={resourceDone ? 'default' : 'primary'}
                          size="small"
                          icon={resourceDone ? <CheckCircleOutlined /> : <CheckOutlined />}
                          onClick={() => handleToggleProgress('RESOURCE', resource.id, resource.title)}
                          disabled={submitting || (!resourceDone && !resourceUnlocked)}
                        >
                          {resourceDone ? '已完成' : resourceUnlocked ? '标记完成' : '未解锁'}
                        </Button>
                      </Tooltip>
                    </div>
                  </div>
                );
              })}
            </div>
          </>
        )}
      </>
    );
  };

  const pageContent = (
    <div className="gpath-page">
      {!embedded && (
        <div className="gpath-hero-row">
          <section className="gpath-hero">
            <div className="gpath-hero-content">
              <div className="gpath-hero-text">
                <span className="gpath-kicker">JOB-DRIVEN LEARNING · 以岗定教</span>
                <h1 className="gpath-title">岗位成长链路</h1>
                <p className="gpath-subtitle">
                  依据你收藏的岗位与自身学情，自动推送一条「画像 → 热门岗位 → 能力图谱 →
                  学习任务卡 → 学习资源 → 个性化路径」的完整资源链路，实现企业任务到课堂实训的精准转化。
                </p>
              </div>
              <div className="gpath-hero-badge">
                <span>每周更新热招数据</span>
              </div>
            </div>
            {renderRoadmapTimeline()}
          </section>
          <DailyCheckinCard
            variant="square"
            className="gpath-checkin-board"
            userName={profile?.name || '同学'}
          />
        </div>
      )}

      {/* ① 我的画像 */}
      <section
        id="gpath-section-profile"
        className={`gpath-section ${highlightedSection === 'profile' ? 'is-highlight' : ''}`}
      >
        {renderProfileBody()}
      </section>

      {/* ② 热门岗位 */}
      <section
        id="gpath-section-jobs"
        className={`gpath-section ${highlightedSection === 'jobs' ? 'is-highlight' : ''}`}
      >
        <div className="gpath-section-head">
          <span className="gpath-section-num">02</span>
          <h2 className="gpath-section-title">热门岗位</h2>
          <span className="gpath-section-tag">收藏岗位 + 同技能热招匹配</span>
        </div>
        {renderJobs()}
      </section>

      {/* ③④⑤⑥ 选中岗位的链路详情 */}
      {activeJob ? (
        <>
          <section
            id="gpath-section-map"
            className={`gpath-section ${highlightedSection === 'map' ? 'is-highlight' : ''}`}
          >
            {renderSkillMap()}
          </section>
          <section
            id="gpath-section-task"
            className={`gpath-section ${highlightedSection === 'task' ? 'is-highlight' : ''}`}
          >
            {renderTaskCard()}
          </section>
          <section
            id="gpath-section-resource"
            className={`gpath-section ${highlightedSection === 'resource' ? 'is-highlight' : ''}`}
          >
            {renderResources()}
          </section>
          <section
            id="gpath-section-path"
            className={`gpath-section ${highlightedSection === 'path' ? 'is-highlight' : ''}`}
          >
            {renderLearningPath()}
          </section>
        </>
      ) : (
        favorites.length === 0 && (
          <div className="gpath-chain-pending">
            <CheckCircleOutlined className="gpath-chain-pending-icon" />
            <p>收藏岗位后，这里将展开完整链路：能力图谱 → 学习任务卡 → 学习资源 → 个性化路径</p>
          </div>
        )
      )}

      {/* 岗位技能思维导图 */}
      <JobSkillMindMap
        jobs={hotJobs}
        visible={mindMapVisible}
        focusSkill={mindMapFocusSkill}
        onClose={() => {
          setMindMapVisible(false);
          setMindMapFocusSkill(null);
        }}
      />

      {/* 图文微课内嵌阅读器 */}
      <MicroCourseReader
        resource={readerResource}
        open={Boolean(readerResource)}
        onClose={() => setReaderResource(null)}
        done={Boolean(readerResource && (progress[activeId]?.resources || new Set()).has(String(readerResource.id)))}
        onToggleDone={handleReaderToggleDone}
        onUnlock={handleReaderUnlock}
        submitting={readerSubmitting}
      />
    </div>
  );

  if (embedded) {
    return pageContent;
  }

  return (
    <StudentLayout activePath="/student/growth-path">
      {pageContent}
    </StudentLayout>
  );
}

export default GrowthPath;
