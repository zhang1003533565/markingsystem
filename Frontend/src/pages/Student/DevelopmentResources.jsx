import { useState, useEffect, useCallback, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { Card, Spin, Empty, Button, message, Tag, Tabs, Tooltip, Progress } from 'antd';
import {
  ReloadOutlined,
  BookOutlined,
  BulbOutlined,
  StarOutlined,
  CheckOutlined,
  FireOutlined,
} from '@ant-design/icons';
import StudentLayout from '../../components/StudentLayout';
import { getFavorites } from '../../api/student/jobFavorite';
import {
  getJobResources,
  requestJobResources,
  requestRefreshJobResources,
  requestRegenerateJobResources,
  getResourceQuota,
} from '../../api/student/developmentResource';
import {
  getJobProgress,
  markProgressCompleted,
  unmarkProgressCompleted,
  unlockLearningResource,
} from '../../api/student/learningProgress';
import {
  DIMENSION_TAGS,
  getResourceSourceMeta,
  resolveResourceOpenLink,
} from '../../utils/jobAssistant';
import MicroCourseReader from '../../components/MicroCourseReader';
import AiGenerationProgress from '../../components/AiGenerationProgress';
import './DevelopmentResources.css';

/**
 * 发展助手 - 个性化学习资源页面
 * 按用户收藏的岗位分 Tab 展示，切换 Tab 时向后端获取 / 生成该岗位的学习资源。
 * 每个资源支持系统内嵌图文微课、外部链接跳转，并可标记完成，形成岗位学习进度。
 */
function DevelopmentResources({
  embedded = false,
  onNavigateToView,
  onProgressChange,
  onResourcesChange,
  initialActiveId = null,
  favoritesRevision = 0,
}) {
  const navigate = useNavigate();
  const [favorites, setFavorites] = useState([]);
  const [favoritesLoading, setFavoritesLoading] = useState(true);
  const [activeId, setActiveId] = useState(null);
  // { [recommendationId]: { resources: [], error: string|null } }
  const [jobData, setJobData] = useState({});
  const [jobLoading, setJobLoading] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [regenerating, setRegenerating] = useState(false);
  const [quota, setQuota] = useState(null);
  // { [recommendationId]: Set(已完成资源 id) }
  const [completedResources, setCompletedResources] = useState({});
  // { [recommendationId]: Set(已解锁资源 id) }
  const [unlockedResources, setUnlockedResources] = useState({});
  // 内嵌微课阅读器状态
  const [readerResource, setReaderResource] = useState(null);
  const [readerSubmitting, setReaderSubmitting] = useState(false);
  const loadSeqRef = useRef(0);

  // 页面加载：收藏列表 + 今日额度（两者无依赖，并行拉取，避免串行等待）
  useEffect(() => {
    const init = async () => {
      setFavoritesLoading(true);
      const [favRes, quotaRes] = await Promise.allSettled([getFavorites(), getResourceQuota()]);
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
      if (quotaRes.status === 'fulfilled' && quotaRes.value?.data) {
        setQuota(quotaRes.value.data);
      }
    };
    init();
    // 仅首屏用 initialActiveId；之后由父级 focus 再驱动
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // 发展助手内嵌：收藏变更后重拉岗位 Tab，避免仍显示旧列表
  useEffect(() => {
    if (!embedded || favoritesRevision < 1) return;
    let cancelled = false;
    (async () => {
      try {
        const res = await getFavorites();
        if (cancelled) return;
        const list = Array.isArray(res?.data) ? res.data : [];
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

  // 加载某岗位的完成记录与已解锁资源
  const loadCompleted = useCallback(async (recommendationId) => {
    if (!recommendationId) return;
    try {
      const res = await getJobProgress(recommendationId);
      const data = res?.data || {};
      const keys = new Set(
        (Array.isArray(data.resources) ? data.resources : []).map((item) => String(item.key))
      );
      const unlocked = new Set(
        (Array.isArray(data.unlockedResources) ? data.unlockedResources : []).map((item) =>
          String(item.key)
        )
      );
      setCompletedResources((prev) => ({ ...prev, [recommendationId]: keys }));
      setUnlockedResources((prev) => ({ ...prev, [recommendationId]: unlocked }));
    } catch {
      // 进度加载失败不阻塞资源展示
    }
  }, []);

  // 获取当前岗位资源（前端已缓存则跳过；点击生成时后端有缓存也不消耗额度）
  const loadResources = useCallback(
    async (recommendationId, force = false) => {
      if (!recommendationId) return;
      if (!force && jobData[recommendationId]?.resources?.length) return;
      const seq = ++loadSeqRef.current;
      setJobLoading(true);
      try {
        // 自动加载（切 Tab）传 generate=false：仅读缓存/状态；
        // 显式点击「生成/查看资源」传 force=true：无缓存则异步生成并轮询。
        const res = force
          ? await requestJobResources(recommendationId, true)
          : await getJobResources(recommendationId, false);
        if (seq !== loadSeqRef.current) return;
        if (res?.status === 'generating') {
          // 切 Tab 读缓存时若后台已在生成，转入轮询等待
          const polled = await requestJobResources(recommendationId, false).catch(() => null);
          if (seq !== loadSeqRef.current) return;
          if (polled) {
            const data = Array.isArray(polled?.data) ? polled.data : [];
            setJobData((prev) => ({
              ...prev,
              [recommendationId]: { resources: data, error: null },
            }));
            if (polled?.quota) setQuota(polled.quota);
            // 自动加载（非 force）时 [activeId] effect 已调 loadCompleted，
            // 这里仅 force 显式操作再重拉进度，避免同批次重复请求 getJobProgress
            if (force) loadCompleted(recommendationId);
            return;
          }
        }
        if (res?.status === 'failed') {
          setJobData((prev) => ({
            ...prev,
            [recommendationId]: {
              resources: Array.isArray(res?.data) ? res.data : [],
              error: res?.message || '生成失败',
            },
          }));
          return;
        }
        const data = Array.isArray(res?.data) ? res.data : [];
        setJobData((prev) => ({
          ...prev,
          [recommendationId]: { resources: data, error: null },
        }));
        if (res?.quota) setQuota(res.quota);
        // 自动加载（非 force）时 [activeId] effect 已调 loadCompleted，
        // 这里仅 force 显式操作再重拉进度，避免同批次重复请求 getJobProgress
        if (force) loadCompleted(recommendationId);
        if (force) onResourcesChange?.(recommendationId, data);
      } catch (err) {
        if (seq !== loadSeqRef.current) return;
        setJobData((prev) => ({
          ...prev,
          [recommendationId]: {
            resources: [],
            error: err?.message || '加载失败',
          },
        }));
      } finally {
        if (seq === loadSeqRef.current) setJobLoading(false);
      }
    },
    [jobData, loadCompleted, onResourcesChange]
  );

  useEffect(() => {
    loadCompleted(activeId);
    // Tab 切换时自动拉取：后端有缓存时直接返回（不消耗额度），与首页概览行为一致
    if (activeId) {
      loadResources(activeId);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeId]);

  const handleRefresh = async () => {
    if (!activeId) return;
    setRefreshing(true);
    try {
      const res = await requestRefreshJobResources(activeId);
      const data = Array.isArray(res?.data) ? res.data : [];
      setJobData((prev) => ({
        ...prev,
        [activeId]: { resources: data, error: null },
      }));
      if (res?.quota) setQuota(res.quota);
      loadCompleted(activeId);
      onResourcesChange?.(activeId, data);
      message.success('资源推荐已刷新');
    } catch {
      // 额度不足等错误已由拦截器提示
    } finally {
      setRefreshing(false);
    }
  };

  /** 原子重新生成（仅扣一次 REGENERATE，失败不丢旧数据） */
  const handleRegenerate = async () => {
    if (!activeId) return;
    setRegenerating(true);
    try {
      const res = await requestRegenerateJobResources(activeId);
      const data = Array.isArray(res?.data) ? res.data : [];
      setJobData((prev) => ({
        ...prev,
        [activeId]: { resources: data, error: null },
      }));
      if (res?.quota) setQuota(res.quota);
      loadCompleted(activeId);
      onResourcesChange?.(activeId, data);
      message.success('已重新生成资源');
    } catch {
      // 错误已由拦截器提示
    } finally {
      setRegenerating(false);
    }
  };

  /** 打开阅读器即解锁（本地 + 服务端） */
  const handleReaderUnlock = useCallback(
    async (resource) => {
      if (!resource || activeId == null || resource.id == null) return;
      const key = String(resource.id);
      setUnlockedResources((prev) => {
        const set = new Set(prev[activeId] || []);
        if (set.has(key)) return prev;
        const next = new Set(set);
        next.add(key);
        return { ...prev, [activeId]: next };
      });
      try {
        await unlockLearningResource({
          recommendationId: activeId,
          itemKey: key,
          itemTitle: resource.title,
        });
      } catch {
        // 解锁失败时标记完成会被后端拒绝；错误已由拦截器提示
      }
    },
    [activeId]
  );

  // 标记完成 / 取消完成某个资源
  // 采用「乐观提交」：点击后立即翻转本地状态让界面即时反馈，再异步同步后端；
  // 若后端提交失败则回滚本地状态（否则会出现“刷新后才生效”的延迟感知）。
  const handleToggleResource = async (resource) => {
    if (!activeId) return;
    const key = String(resource.id);
    const done = completedResources[activeId]?.has(key) || false;
    const unlocked = unlockedResources[activeId]?.has(key) || false;
    if (!done && !unlocked) {
      message.warning('请先点击「开始学习」解锁本资源');
      return;
    }
    const payload = {
      recommendationId: activeId,
      itemType: 'RESOURCE',
      itemKey: key,
      itemTitle: resource.title,
    };
    const prevKey = key;
    const wasDone = done;
    // 1) 立即乐观翻转本地状态
    setCompletedResources((prev) => {
      const next = new Set(prev[activeId] || []);
      if (wasDone) {
        next.delete(prevKey);
      } else {
        next.add(prevKey);
      }
      return { ...prev, [activeId]: next };
    });
    try {
      // 2) 异步同步到后端
      if (wasDone) {
        await unmarkProgressCompleted(payload);
        onProgressChange?.('resources', key, false);
        message.success('已取消完成标记');
      } else {
        await markProgressCompleted(payload);
        onProgressChange?.('resources', key, true);
        message.success('已完成，继续保持！');
      }
    } catch {
      // 3) 后端失败：回滚本地状态，避免界面与服务器不一致
      setCompletedResources((prev) => {
        const next = new Set(prev[activeId] || []);
        if (wasDone) {
          next.add(prevKey);
        } else {
          next.delete(prevKey);
        }
        return { ...prev, [activeId]: next };
      });
      // 错误已由拦截器提示
    }
  };

  // 阅读器内「完成本章」切换（与卡片标记完成共用同一进度记录）
  const handleReaderToggleDone = async () => {
    if (!readerResource) return;
    setReaderSubmitting(true);
    try {
      await handleToggleResource(readerResource);
    } finally {
      setReaderSubmitting(false);
    }
  };

  const activeJob = favorites.find((job) => job.id === activeId);
  const current = jobData[activeId] || { resources: [], error: null };
  const manualRemaining = quota?.manualRemaining ?? null;
  const boardRemaining = quota?.boardRemaining ?? quota?.autoRemaining ?? null;
  const regenerateRemaining = quota?.regenerateRemaining ?? null;
  // 刷新按钮：受「刷新操作类型 3 次」与「板块池 5 次」双重限制
  const refreshDisabled =
    refreshing || manualRemaining === 0 || boardRemaining === 0;
  // 重新生成：受「重新生成操作类型 3 次」与「板块池 5 次」双重限制
  const regenerateDisabled =
    regenerating || !activeId || regenerateRemaining === 0 || boardRemaining === 0;

  const completedSet = completedResources[activeId] || new Set();
  const unlockedSet = unlockedResources[activeId] || new Set();
  const totalResources = current.resources.length;
  const completedCount = current.resources.filter((r) => completedSet.has(String(r.id))).length;
  const progressPercent = totalResources
    ? Math.round((completedCount / totalResources) * 100)
    : 0;

  const renderQuotaActions = () => (
    <div className="dev-resources-actions">
      {quota && (
        <Tooltip title="资源板块每日共 5 次（生成+刷新+重新生成合计）；刷新按钮最多 3 次、重新生成最多 3 次，每日 00:00 重置。">
          <span className="dev-resources-quota">
            板块剩余 {boardRemaining}/{quota.boardTotal} · 刷新剩余{' '}
            {manualRemaining}/{quota.manualTotal} · 重新生成剩余{' '}
            {regenerateRemaining}/{quota.regenerateTotal}
          </span>
        </Tooltip>
      )}
      <Button
        type="text"
        icon={<ReloadOutlined className={refreshing ? 'spin' : ''} />}
        onClick={handleRefresh}
        loading={refreshing}
        disabled={refreshDisabled || !activeId}
      >
        {manualRemaining === 0 ? '今日刷新次数已用完' : '刷新资源'}
      </Button>
      <Button
        type="link"
        icon={<BulbOutlined />}
        onClick={handleRegenerate}
        loading={regenerating}
        disabled={regenerateDisabled}
        title="清空当前岗位的资源缓存并由 AI 重新生成（最多 3 次，计入板块总额度）"
      >
        {regenerateRemaining === 0 ? '今日重新生成次数已用完' : '重新生成'}
      </Button>
    </div>
  );

  const renderJobContent = () => {
    if (jobLoading) {
      return (
        <div className="dev-resources-loading">
          <AiGenerationProgress
            active
            title="AI 正在生成学习资源"
            hint="后台异步处理中，通常 1–3 分钟，请保持本页打开"
          />
        </div>
      );
    }

    if (current.error) {
      return (
        <div className="dev-resources-empty">
          <Empty description="获取学习资源失败" />
          <p className="dev-resources-empty-hint">{current.error}</p>
          <Button
            type="primary"
            onClick={() => loadResources(activeId, true)}
          >
            重新获取
          </Button>
        </div>
      );
    }

    if (!current.resources.length) {
      return (
        <div className="dev-resources-empty">
          <Empty description="尚未加载学习资源" />
          <p className="dev-resources-empty-hint">
            点击后先获取已有资源；没有缓存时再由 AI 生成，避免收藏岗位时立即触发长时间请求。
          </p>
          <Button
            type="primary"
            onClick={() => loadResources(activeId, true)}
          >
            生成/查看资源
          </Button>
        </div>
      );
    }

    return (
      <>
        {(refreshing || regenerating) && (
          <AiGenerationProgress
            active
            variant="inline"
            title={regenerating ? 'AI 正在重新生成学习资源' : 'AI 正在刷新学习资源'}
            hint="生成完成前仍展示旧资源，成功后会自动替换"
          />
        )}
        {totalResources > 0 && (
          <div className="dev-resources-progress">
            <span className="dev-resources-progress-label">
              <FireOutlined />
              岗位学习进度
            </span>
            <Progress
              percent={progressPercent}
              size="small"
              strokeColor="#158FED"
              format={() => `${completedCount}/${totalResources} 项`}
            />
            {progressPercent === 100 && (
              <span className="dev-resources-progress-done">
                <CheckOutlined /> 全部完成，太棒了！
              </span>
            )}
          </div>
        )}
        <div className="dev-resources-grid">
          {current.resources.map((resource, index) => {
            const tag =
              DIMENSION_TAGS[resource.sortOrder] ||
              DIMENSION_TAGS[index + 1] ||
              { label: '推荐', color: 'default' };
            const done = completedSet.has(String(resource.id));
            const unlocked = unlockedSet.has(String(resource.id));
            const link = resolveResourceOpenLink(resource);
            const sourceMeta = getResourceSourceMeta(link);
            const GuideIcon = sourceMeta.guideIcon;
            const LinkIcon = sourceMeta.linkIcon;
            return (
              <Card
                key={resource.id || index}
                className={`dev-resource-card ${done ? 'dev-resource-card-done' : ''}`}
                hoverable
              >
                <div className="dev-resource-card-header">
                  <Tag color={tag.color} className="dev-resource-tag">
                    {tag.label}
                  </Tag>
                  {done && (
                    <Tag color="green" icon={<CheckOutlined />} className="dev-resource-tag">
                      已完成
                    </Tag>
                  )}
                </div>
                <h3 className="dev-resource-title">
                  <BookOutlined className="dev-resource-title-icon" />
                  <span className="dev-resource-title-text">{resource.title}</span>
                </h3>
                <div className="dev-resource-section">
                  <div className="dev-resource-section-label">资源简介</div>
                  <p className="dev-resource-description">
                    {resource.description}
                  </p>
                </div>
                <div className="dev-resource-section">
                  <div className="dev-resource-section-label">
                    <GuideIcon className="dev-resource-guide-icon" />
                    {sourceMeta.guideLabel}
                  </div>
                  <p className="dev-resource-guide">{resource.guide}</p>
                </div>
                <div className="dev-resource-card-actions">
                  <div className="dev-resource-card-actions-left">
                    <Button
                      type="primary"
                      size="small"
                      icon={<BookOutlined />}
                      onClick={() => setReaderResource(resource)}
                    >
                      开始学习
                    </Button>
                    <Tooltip title={sourceMeta.linkTitle || '点击打开外链'}>
                      <a
                        className="dev-resource-open-btn"
                        href={link || resolveResourceOpenLink(resource)}
                        target="_blank"
                        rel="noreferrer"
                      >
                        <LinkIcon />
                        {sourceMeta.linkLabel || '原资源'}
                      </a>
                    </Tooltip>
                  </div>
                  <Button
                    type={done ? 'default' : 'primary'}
                    size="small"
                    icon={done ? <CheckOutlined /> : <FireOutlined />}
                    disabled={!done && !unlocked}
                    onClick={() => handleToggleResource(resource)}
                  >
                    {done ? '已完成' : unlocked ? '标记完成' : '先解锁'}
                  </Button>
                </div>
              </Card>
            );
          })}
        </div>
      </>
    );
  };

  const renderContent = () => {
    if (favoritesLoading) {
      return (
        <div className="dev-resources-loading">
          <Spin size="large" tip="加载收藏岗位中…" />
        </div>
      );
    }

    if (favorites.length === 0) {
      return (
        <div className="dev-resources-empty">
          <Empty description="暂无收藏的岗位" />
          <p className="dev-resources-empty-hint">
            请先在「热门岗位推荐」页面收藏感兴趣的岗位（最多 6 个），
            即可在这里获取专属学习资源。
          </p>
          {onNavigateToView ? (
            <Button type="primary" icon={<StarOutlined />} onClick={() => onNavigateToView('hotJobs')}>
              去收藏岗位
            </Button>
          ) : (
            <Button type="primary" icon={<StarOutlined />} onClick={() => navigate('/student/assistant')}>
              去发展助手
            </Button>
          )}
        </div>
      );
    }

    return (
      <>
        <Tabs
          type="card"
          className="dev-resources-tabs"
          activeKey={String(activeId)}
          onChange={(key) => setActiveId(Number(key))}
          items={favorites.map((job) => ({
            key: String(job.id),
            label: job.jobTitle || '软件工程师',
          }))}
        />
        {renderJobContent()}
      </>
    );
  };

  const pageContent = (
    <>
      <div className="dev-resources-page">
        <div className="dev-resources-header">
          {!embedded ? (
            <div className="dev-resources-title-section">
              <h2 className="dev-resources-title">
                <BulbOutlined className="dev-resources-title-icon" />
                岗位学习资源
              </h2>
              <p className="dev-resources-subtitle">
                基于你收藏的岗位，由 AI 定制掘金、CSDN、博客园、B 站等多平台学习资源（链接失效时保留系统内图文微课），
                覆盖理论、实战、技能、项目、行业与学习路径六个维度，
                点击「开始学习」在系统内阅读图文微课，完成任务后标记完成，积累学习进度
                {activeJob ? `｜当前岗位：${activeJob.jobTitle || ''}` : ''}
              </p>
            </div>
          ) : (
            <div className="dev-resources-title-section">
              <p className="dev-resources-subtitle">
                点击「开始学习」解锁后可标记完成
                {activeJob ? `｜当前岗位：${activeJob.jobTitle || ''}` : ''}
              </p>
            </div>
          )}
          {renderQuotaActions()}
        </div>
        {renderContent()}
      </div>
      <MicroCourseReader
        resource={readerResource}
        open={Boolean(readerResource)}
        onClose={() => setReaderResource(null)}
        done={Boolean(readerResource && completedResources[activeId]?.has(String(readerResource.id)))}
        onToggleDone={handleReaderToggleDone}
        submitting={readerSubmitting}
        onUnlock={handleReaderUnlock}
      />
    </>
  );

  if (embedded) {
    return pageContent;
  }

  return (
    <StudentLayout activePath="/student/development-resources">
      {pageContent}
    </StudentLayout>
  );
}

export default DevelopmentResources;
