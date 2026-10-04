import { useState, useEffect, useCallback } from 'react';
import { ReloadOutlined } from '../../components/AcademicIcons';
import { StarOutlined, StarFilled } from '@ant-design/icons';
import { message, Spin, Empty, Button, Tooltip } from 'antd';
import StudentLayout from '../../components/StudentLayout';
import JobSkillMindMap from '../../components/JobSkillMindMap';
import { getFavoriteIds, addFavorite, removeFavorite } from '../../api/student/jobFavorite';
import {
  getStudentWeeklyJobRecommendations,
  refreshStudentWeeklyJobRecommendations
} from '../../api/student/portal';
import { getLangPopRankings } from '../../api/student/langPop';
import { parseSkills, resolveJobSearchLink } from '../../utils/jobAssistant';
import './HotJobs.css';

// AI 无法实时访问招聘平台时返回的占位/说明文本，命中即视为无效岗位数据
const PLACEHOLDER_PHRASES = [
  '无法提供精确排名数据',
  '无法实时访问',
  '无法访问招聘平台',
  '无法提供',
  '不能提供',
  '无法获取',
  '暂时无法',
  '需人工检索',
  '人工检索',
  '暂无',
];

function isPlaceholderJob(job) {
  const text = [job?.jobTitle, job?.salary, job?.skills].filter(Boolean).join(' ');
  return PLACEHOLDER_PHRASES.some((phrase) => text.includes(phrase));
}

function filterValidJobs(data) {
  // 真实数据直接返回；无有效数据时返回空数组，由页面空态提示引导，不再用示例岗位兜底
  return Array.isArray(data) ? data.filter((job) => !isPlaceholderJob(job)) : [];
}

function formatDate(value) {
  if (!value) return '';
  return String(value).slice(0, 10);
}

function formatWeekRange(firstJob) {
  if (!firstJob?.weekStartDate || !firstJob?.weekEndDate) return '';
  return `${formatDate(firstJob.weekStartDate)} — ${formatDate(firstJob.weekEndDate)}`;
}

const SKILL_ABBREV = {
  python: 'Py',
  javascript: 'JS',
  typescript: 'TS',
  java: 'JV',
  kotlin: 'Kt',
  golang: 'Go',
  go: 'Go',
  rust: 'Rs',
  react: 'RE',
  vue: 'Vue',
  php: 'PHP',
  ruby: 'Rb',
  swift: 'Sw',
  r: 'R',
  'c++': 'C++',
  cpp: 'C++',
  'c#': 'C#',
  csharp: 'C#',
  sql: 'SQL',
};

function skillAbbrev(skill) {
  const raw = String(skill || '').trim();
  if (!raw) return '?';
  const known = SKILL_ABBREV[raw.toLowerCase()];
  if (known) return known;
  const alnum = raw.replace(/[^A-Za-z0-9+#]/g, '');
  if (alnum.length <= 4 && alnum.length > 0) return alnum;
  return (alnum.slice(0, 3) || raw.slice(0, 2)).toUpperCase();
}

function formatLangPopScore(score) {
  const n = Number(score);
  if (!Number.isFinite(n)) return '—';
  return n.toFixed(1);
}

function langPopScoreRatio(score, maxScore) {
  const n = Number(score);
  if (!Number.isFinite(n) || maxScore <= 0) return 0;
  return Math.max(0, Math.min(1, n / maxScore));
}

function formatMonthLabel(monthKey) {
  if (!monthKey || !/^\d{4}-\d{2}$/.test(monthKey)) return '';
  const [y, m] = monthKey.split('-');
  return `${y}年${Number(m)}月`;
}

function HotJobs({ embedded = false, onFavoritesChange }) {
  const [jobs, setJobs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [mindMapVisible, setMindMapVisible] = useState(false);
  const [mindMapFocusSkill, setMindMapFocusSkill] = useState(null);
  const [favoritedIds, setFavoritedIds] = useState(new Set());
  const [error, setError] = useState(null);
  const [langPop, setLangPop] = useState({ loading: true, month: '', weekStart: '', data: [] });

  const fetchJobs = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await getStudentWeeklyJobRecommendations();
      setJobs(filterValidJobs(res?.data));
    } catch (err) {
      console.warn('获取热门岗位推荐失败:', err);
      setJobs([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchJobs();
  }, [fetchJobs]);

  useEffect(() => {
    let cancelled = false;
    setLangPop((prev) => ({ ...prev, loading: true }));
    getLangPopRankings({ limit: 16 })
      .then((payload) => {
        if (cancelled) return;
        setLangPop({
          loading: false,
          month: payload.month || '',
          weekStart: payload.weekStart || '',
          data: Array.isArray(payload.data) ? payload.data : [],
        });
      })
      .catch((err) => {
        console.warn('获取 LangPop 排行失败:', err);
        if (!cancelled) {
          setLangPop({ loading: false, month: '', weekStart: '', data: [] });
        }
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const refreshJobs = useCallback(async () => {
    try {
      const res = await getStudentWeeklyJobRecommendations();
      setJobs(filterValidJobs(res?.data));
      setError(null);
    } catch (err) {
      console.warn('自动刷新热门岗位推荐失败:', err);
    }
  }, []);

  useEffect(() => {
    const intervalId = window.setInterval(refreshJobs, 30 * 60 * 1000);
    const handleVisibilityChange = () => {
      if (document.visibilityState === 'visible') {
        refreshJobs();
      }
    };
    window.addEventListener('focus', refreshJobs);
    document.addEventListener('visibilitychange', handleVisibilityChange);

    return () => {
      window.clearInterval(intervalId);
      window.removeEventListener('focus', refreshJobs);
      document.removeEventListener('visibilitychange', handleVisibilityChange);
    };
  }, [refreshJobs]);

  useEffect(() => {
    getFavoriteIds()
      .then((res) => {
        if (res?.data) {
          setFavoritedIds(new Set(res.data));
        }
      })
      .catch(() => {});
  }, [jobs.length > 0]);

  const handleToggleFavorite = async (recommendationId, event) => {
    event.stopPropagation();
    const isFav = favoritedIds.has(recommendationId);
    try {
      if (isFav) {
        await removeFavorite(recommendationId);
        setFavoritedIds((prev) => {
          const next = new Set(prev);
          next.delete(recommendationId);
          return next;
        });
        message.success('已取消收藏');
      } else {
        await addFavorite(recommendationId);
        setFavoritedIds((prev) => new Set(prev).add(recommendationId));
        message.success('收藏成功');
      }
      onFavoritesChange?.();
    } catch {
      // 错误已由拦截器处理
    }
  };

  const handleRefresh = async () => {
    setRefreshing(true);
    try {
      const res = await refreshStudentWeeklyJobRecommendations();
      setJobs(filterValidJobs(res?.data));
      setError(null);
      message.success('热门岗位推荐已刷新');
    } catch (err) {
      message.error(err?.response?.data?.message || '刷新失败，请稍后重试');
    } finally {
      setRefreshing(false);
    }
  };

  const weekLabel = jobs.length ? formatWeekRange(jobs[0]) : '';
  const coveredSkillCount = new Set(jobs.flatMap((job) => parseSkills(job.skills))).size;
  const favoriteCount = jobs.filter((job) => favoritedIds.has(job.id)).length;

  const langPopLanguages = langPop.data;
  const langPopMaxScore = langPopLanguages.reduce((max, item) => {
    const n = Number(item.composite_score);
    return Number.isFinite(n) && n > max ? n : max;
  }, 0);

  const renderLangPop = () => {
    if (!langPop.loading && langPopLanguages.length === 0) return null;
    const monthLabel = formatMonthLabel(langPop.month) || '本月';
    const featured = langPopLanguages.slice(0, 3);
    const rest = langPopLanguages.slice(3);

    return (
      <section className="hotjobs-lang" aria-label="热门编程语言排行">
        <header className="hotjobs-lang-head">
          <div>
            <span className="hotjobs-lang-kicker">LangPop Index</span>
            <h2 className="hotjobs-lang-title">热门编程语言</h2>
            <p className="hotjobs-lang-sub">综合指数月度快照，与岗位推荐相互独立</p>
          </div>
          <div className="hotjobs-lang-pills">
            <span className="hotjobs-lang-pill">{monthLabel}</span>
            {langPop.weekStart ? (
              <span className="hotjobs-lang-pill">快照 {langPop.weekStart}</span>
            ) : null}
            <span className="hotjobs-lang-pill is-mute">每月更新</span>
          </div>
        </header>

        {langPop.loading ? (
          <div className="hotjobs-lang-skel" aria-hidden="true">
            <div className="hotjobs-lang-podium">
              <div className="hotjobs-lang-lead is-skel" />
              <div className="hotjobs-lang-lead is-skel" />
              <div className="hotjobs-lang-lead is-skel" />
            </div>
            <div className="hotjobs-lang-skel-rows">
              <div className="hotjobs-lang-row is-skel" />
              <div className="hotjobs-lang-row is-skel" />
              <div className="hotjobs-lang-row is-skel" />
              <div className="hotjobs-lang-row is-skel" />
            </div>
          </div>
        ) : (
          <>
            <div className="hotjobs-lang-podium">
              {featured.map((item) => {
                const rank = Number(item.rank) || 0;
                const score = formatLangPopScore(item.composite_score);
                const ratio = langPopScoreRatio(item.composite_score, langPopMaxScore);
                return (
                  <article
                    key={item.slug || item.language}
                    className={`hotjobs-lang-lead is-rank-${rank}`}
                  >
                    <span className="hotjobs-lang-lead-rank">#{rank}</span>
                    <div className="hotjobs-lang-lead-body">
                      <span className="hotjobs-lang-mono" aria-hidden="true">
                        {skillAbbrev(item.language)}
                      </span>
                      <h3 className="hotjobs-lang-lead-name">{item.language}</h3>
                    </div>
                    <div className="hotjobs-lang-lead-score">
                      <span className="hotjobs-lang-score-num">{score}</span>
                      <span className="hotjobs-lang-score-unit">分</span>
                    </div>
                    <div
                      className="hotjobs-lang-bar"
                      role="meter"
                      aria-label={`${item.language} 得分 ${score}`}
                      aria-valuemin={0}
                      aria-valuemax={Number(langPopMaxScore.toFixed(1))}
                      aria-valuenow={Number(item.composite_score) || 0}
                    >
                      <span style={{ transform: `scaleX(${ratio})` }} />
                    </div>
                  </article>
                );
              })}
            </div>

            {rest.length > 0 && (
              <ol className="hotjobs-lang-list" start={4}>
                {rest.map((item) => {
                  const rank = Number(item.rank) || 0;
                  const score = formatLangPopScore(item.composite_score);
                  const ratio = langPopScoreRatio(item.composite_score, langPopMaxScore);
                  return (
                    <li key={item.slug || item.language} className="hotjobs-lang-row">
                      <span className="hotjobs-lang-row-rank">{String(rank).padStart(2, '0')}</span>
                      <span className="hotjobs-lang-row-name">{item.language}</span>
                      <div
                        className="hotjobs-lang-bar is-row"
                        role="meter"
                        aria-label={`${item.language} 得分 ${score}`}
                        aria-valuemin={0}
                        aria-valuemax={Number(langPopMaxScore.toFixed(1))}
                        aria-valuenow={Number(item.composite_score) || 0}
                      >
                        <span style={{ transform: `scaleX(${ratio})` }} />
                      </div>
                      <span className="hotjobs-lang-row-score">{score}</span>
                    </li>
                  );
                })}
              </ol>
            )}
          </>
        )}

        <p className="hotjobs-lang-footnote">
          数据来源{' '}
          <a href="https://langpop.com/" target="_blank" rel="noreferrer">
            LangPop
          </a>
          ，综合 GitHub、招聘、Stack Overflow 等 7 项信号。本页按自然月缓存。
        </p>
      </section>
    );
  };

  const renderStats = () => {
    if (!jobs.length) return null;
    return (
      <section className="hotjobs-stats">
        <div className="hotjobs-stat">
          <span className="hotjobs-stat-label">本周推荐</span>
          <span className="hotjobs-stat-value is-blue">
            {jobs.length}
            <i>个岗位</i>
          </span>
        </div>
        <div className="hotjobs-stat">
          <span className="hotjobs-stat-label">覆盖技能</span>
          <span className="hotjobs-stat-value">
            {coveredSkillCount}
            <i>项</i>
          </span>
        </div>
        <div className="hotjobs-stat">
          <span className="hotjobs-stat-label">已收藏</span>
          <span className="hotjobs-stat-value is-amber">
            {favoriteCount}
            <i>个</i>
          </span>
        </div>
        <div className="hotjobs-stat">
          <span className="hotjobs-stat-label">数据周期</span>
          <span className="hotjobs-stat-value is-text">每周更新</span>
        </div>
      </section>
    );
  };

  const renderContent = () => {
    if (loading) {
      return (
        <div className="hotjobs-loading">
          <Spin size="large" tip="加载热门岗位中…" />
        </div>
      );
    }

    if (error === 'empty' || jobs.length === 0) {
      return (
        <div className="hotjobs-empty">
          <Empty description="暂无岗位推荐数据" />
          <p className="hotjobs-empty-hint">后端每周自动刷新，页面停留时也会自动检查最新推荐。</p>
          <Button
            type="primary"
            icon={<ReloadOutlined className={refreshing ? 'spin' : ''} />}
            loading={refreshing}
            onClick={handleRefresh}
          >
            立即生成
          </Button>
        </div>
      );
    }

    if (error) {
      return (
        <div className="hotjobs-empty">
          <Empty description="加载岗位推荐失败" />
          <p className="hotjobs-empty-hint">{error}</p>
          <Button type="primary" icon={<ReloadOutlined />} onClick={fetchJobs}>
            重试
          </Button>
        </div>
      );
    }

    return (
      <section className="hotjobs-jobs" aria-label="本周热招岗位">
        <header className="hotjobs-jobs-head">
          <div>
            <span className="hotjobs-jobs-kicker">Weekly Jobs</span>
            <h2 className="hotjobs-jobs-title">本周热招岗位</h2>
            <p className="hotjobs-jobs-sub">按推荐顺序排列，可收藏或跳转招聘平台查看</p>
          </div>
          <span className="hotjobs-jobs-count">{jobs.length} 个岗位</span>
        </header>
        <div className="hotjobs-grid">
          {jobs.map((job, originalIndex) => {
            const rank = originalIndex + 1;
            const skillList = parseSkills(job.skills);
            const isFavorited = favoritedIds.has(job.id);

            return (
                <article key={job.id || `${job.jobTitle}-${rank}`} className="hotjobs-card">
                  <div className="hotjobs-card-head">
                    <div className="hotjobs-rank-wrap">
                      <span className="hotjobs-rank-num">{String(rank).padStart(2, '0')}</span>
                      {rank === 1 && <span className="hotjobs-rank-badge">Hot</span>}
                    </div>
                    <Tooltip title={isFavorited ? '取消收藏' : '收藏岗位'}>
                      <button
                        type="button"
                        className={`hotjobs-fav-btn ${isFavorited ? 'favorited' : ''}`}
                        onClick={(e) => handleToggleFavorite(job.id, e)}
                        aria-label={isFavorited ? '取消收藏' : '收藏岗位'}
                      >
                        {isFavorited ? (
                          <StarFilled style={{ color: 'inherit', fontSize: 15 }} />
                        ) : (
                          <StarOutlined style={{ fontSize: 15 }} />
                        )}
                      </button>
                    </Tooltip>
                  </div>

                  <h3 className="hotjobs-job-title">{job.jobTitle || '软件工程师'}</h3>

                  <div className="hotjobs-salary">
                    <span className="hotjobs-salary-text">{job.salary || '薪资面议'}</span>
                  </div>

                  {skillList.length > 0 && (
                    <div className="hotjobs-skills">
                      {skillList.map((skill, skillIndex) => (
                        <button
                          type="button"
                          key={`${skill}-${skillIndex}`}
                          className="hotjobs-skill-tag"
                          onClick={() => {
                            setMindMapFocusSkill(skill);
                            setMindMapVisible(true);
                          }}
                        >
                          {skill}
                        </button>
                      ))}
                    </div>
                  )}

                  <div className="hotjobs-card-footer">
                    <a
                      className="hotjobs-link"
                      href={resolveJobSearchLink(job)}
                      target="_blank"
                      rel="noreferrer"
                    >
                      <span>BOSS 直聘</span>
                      <span className="hotjobs-arrow">→</span>
                    </a>
                    {job.source && (
                      <span className="hotjobs-source">
                        {job.source === '示例数据' ? '示例' : 'AI'}
                      </span>
                    )}
                  </div>
                </article>
              );
            })}
          </div>
      </section>
    );
  };

  const pageContent = (
    <div className="hotjobs-page">
      {!embedded && (
        <section className="hotjobs-hero">
          <div className="hotjobs-hero-inner">
            <div className="hotjobs-hero-text">
              <span className="hotjobs-kicker">Career Signal</span>
              <h1 className="hotjobs-title">热门岗位推荐</h1>
              <p className="hotjobs-subtitle">
                聚合近一周软件工程方向热招机会，关注岗位技能与薪资区间。
              </p>
              <div className="hotjobs-hero-actions">
                {weekLabel && <span className="hotjobs-week-label">{weekLabel}</span>}
                <button
                  type="button"
                  className="hotjobs-refresh-btn"
                  onClick={handleRefresh}
                  disabled={refreshing}
                >
                  <ReloadOutlined className={refreshing ? 'spin' : ''} />
                  <span>{refreshing ? '刷新中…' : '刷新推荐'}</span>
                </button>
                <button
                  type="button"
                  className="hotjobs-mindmap-btn"
                  onClick={() => {
                    setMindMapFocusSkill(null);
                    setMindMapVisible(true);
                  }}
                  disabled={!jobs.length}
                >
                  <span>技能导图</span>
                </button>
              </div>
            </div>
            <div className="hotjobs-hero-badge" aria-hidden="true">
              <span className="badge-num">{jobs.length || '—'}</span>
              <span className="badge-label">本周热招</span>
            </div>
          </div>
        </section>
      )}

      {renderStats()}

      {renderContent()}

      {renderLangPop()}

      <JobSkillMindMap
        jobs={jobs}
        visible={mindMapVisible}
        focusSkill={mindMapFocusSkill}
        onClose={() => {
          setMindMapVisible(false);
          setMindMapFocusSkill(null);
        }}
      />
    </div>
  );

  if (embedded) {
    return pageContent;
  }

  return (
    <StudentLayout activePath="/student/jobs">
      {pageContent}
    </StudentLayout>
  );
}

export default HotJobs;
