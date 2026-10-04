import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { StarFilled, PlusOutlined, StarOutlined } from '@ant-design/icons';
import { LinkOutlined, ThunderboltOutlined } from '../../components/AcademicIcons';
import { Empty, Spin, message, Tag, Tooltip, Progress } from 'antd';
import StudentLayout from '../../components/StudentLayout';
import JobSkillMindMap from '../../components/JobSkillMindMap';
import { getFavorites, removeFavorite } from '../../api/student/jobFavorite';
import { getLearningProgressOverview } from '../../api/student/learningProgress';
import { parseSkills, resolveJobSearchLink } from '../../utils/jobAssistant';
import './MyJobFavorites.css';

const FAVORITE_LIMIT = 6;

const SKILL_COLOR_SETS = [
  { bg: '#f0f7ff', color: '#0d2b47', border: '#c9e0f9' },
  { bg: '#e8f3ff', color: '#0d2b47', border: '#9dc6f2' },
  { bg: '#ffffff', color: '#158FED', border: '#c9e0f9' },
  { bg: '#f0f7ff', color: '#31506e', border: '#c9e0f9' },
  { bg: '#e8f3ff', color: '#0f6fc0', border: '#9dc6f2' },
  { bg: '#ffffff', color: '#0d2b47', border: '#c9e0f9' },
  { bg: '#f0f7ff', color: '#158FED', border: '#c9e0f9' },
  { bg: '#e8f3ff', color: '#31506e', border: '#9dc6f2' },
];

function MyJobFavorites({ embedded = false, onNavigateToView, favoritesRevision = 0, onFavoritesChange }) {
  const navigate = useNavigate();
  const [favorites, setFavorites] = useState([]);
  const [loading, setLoading] = useState(true);
  const [mindMapVisible, setMindMapVisible] = useState(false);
  const [mindMapFocusSkill, setMindMapFocusSkill] = useState(null);
  // { [recommendationId]: { percent, done, total } }
  const [progressMap, setProgressMap] = useState({});

  const fetchFavorites = useCallback(async () => {
    setLoading(true);
    try {
      const res = await getFavorites();
      setFavorites(Array.isArray(res?.data) ? res.data : []);
    } catch {
      message.error('加载收藏失败');
      setFavorites([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchFavorites();
  }, [fetchFavorites]);

  // 发展助手内嵌：热门岗位等处收藏变更后，父级会递增 revision，此处重拉列表
  useEffect(() => {
    if (!embedded || favoritesRevision < 1) return;
    fetchFavorites();
  }, [embedded, favoritesRevision, fetchFavorites]);

  // 加载各岗位学习进度，展示收藏岗位的学习完成度
  useEffect(() => {
    getLearningProgressOverview()
      .then((res) => {
        const map = {};
        (Array.isArray(res?.data) ? res.data : []).forEach((job) => {
          const total =
            Number(job.resourceTotal || 0) +
            Number(job.taskStepTotal || 0) +
            Number(job.pathStepTotal || 0);
          const done =
            Number(job.resourceCompleted || 0) +
            Number(job.taskStepCompleted || 0) +
            Number(job.pathStepCompleted || 0);
          map[job.recommendationId] = {
            percent: total ? Math.round((done / total) * 100) : 0,
            done,
            total,
          };
        });
        setProgressMap(map);
      })
      .catch(() => {
        // 进度加载失败不阻塞收藏列表
      });
  }, []);

  const handleRemoveFavorite = async (recommendationId, event) => {
    event.stopPropagation();
    try {
      await removeFavorite(recommendationId);
      setFavorites((prev) => prev.filter((job) => job.id !== recommendationId));
      message.success('已取消收藏');
      onFavoritesChange?.();
    } catch {
      // 错误由拦截器处理
    }
  };

  const renderContent = () => {
    if (loading) {
      return (
        <div className="myfav-loading">
          <Spin size="large" tip="加载收藏中…" />
        </div>
      );
    }

    if (favorites.length === 0) {
      return (
        <div className="myfav-empty">
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description="暂无收藏的岗位"
          />
          <p className="myfav-empty-hint">
            在「热门岗位推荐」页面点击⭐收藏岗位，收藏的岗位会显示在这里。
          </p>
        </div>
      );
    }

    return (
      <div className="myfav-grid">
        {favorites.map((job, index) => {
          const skillList = parseSkills(job.skills);
          const colorSet = SKILL_COLOR_SETS[index % SKILL_COLOR_SETS.length];
          const progress = progressMap[job.id];

          return (
            <article key={job.id || `${job.jobTitle}-${index}`} className="myfav-card">
              <div className="myfav-card-body">
                <div className="myfav-card-header">
                  <h3 className="myfav-job-title">{job.jobTitle || '软件工程师'}</h3>
                  <Tooltip title="取消收藏">
                    <button
                      type="button"
                      className="myfav-fav-btn favorited"
                      onClick={(e) => handleRemoveFavorite(job.id, e)}
                    >
                      <StarFilled style={{ color: '#fadb14', fontSize: 18 }} />
                    </button>
                  </Tooltip>
                </div>

                <div className="myfav-salary">
                  <span>{job.salary || '薪资面议'}</span>
                </div>

                {skillList.length > 0 && (
                  <div className="myfav-skills">
                    <ThunderboltOutlined className="myfav-skills-icon" />
                    <div className="myfav-skills-list">
                      {skillList.map((skill) => (
                        <Tag
                          key={skill}
                          style={{
                            background: colorSet.bg,
                            color: colorSet.color,
                            borderColor: colorSet.border,
                            marginBottom: 4,
                            cursor: 'pointer',
                          }}
                          onClick={() => {
                            setMindMapFocusSkill(skill);
                            setMindMapVisible(true);
                          }}
                        >
                          {skill}
                        </Tag>
                      ))}
                    </div>
                  </div>
                )}

                <div className="myfav-progress">
                  <span className="myfav-progress-label">
                    学习进度
                    <em>{progress ? `${progress.done}/${progress.total} 项` : '待开始'}</em>
                  </span>
                  <Progress
                    percent={progress?.percent || 0}
                    size="small"
                    strokeColor="#158FED"
                  />
                </div>

                <div className="myfav-card-footer">
                  <a
                    className="myfav-link"
                    href={resolveJobSearchLink(job)}
                    target="_blank"
                    rel="noreferrer"
                  >
                    <LinkOutlined />
                    <span>在 BOSS 直聘查看</span>
                  </a>
                </div>
              </div>
            </article>
          );
        })}

        {favorites.length < FAVORITE_LIMIT && (
          <button
            type="button"
            className="myfav-add-card"
            onClick={() => {
              if (onNavigateToView) {
                onNavigateToView('hotJobs');
              } else {
                navigate('/student/assistant');
              }
            }}
          >
            <span className="myfav-add-icon">
              <PlusOutlined />
            </span>
            <strong>收藏更多岗位</strong>
            <em>
              还可收藏 {FAVORITE_LIMIT - favorites.length} 个
              <br />
              去热门岗位推荐看看
            </em>
            <span className="myfav-add-cta">
              <StarOutlined /> 去逛热门岗位
            </span>
          </button>
        )}
      </div>
    );
  };

  const pageContent = (
    <div className="myfav-page">
      {!embedded && (
        <section className="myfav-hero">
          <div className="myfav-hero-content">
            <div className="myfav-hero-text">
              <span className="myfav-kicker">MY COLLECTION</span>
              <h1 className="myfav-title">我的收藏</h1>
              <p className="myfav-subtitle">
                收藏感兴趣的热门岗位，随时查看，不错过好机会。
              </p>
            </div>
            <div className="myfav-hero-actions">
              <span className="myfav-count">
                {favorites.length} / {FAVORITE_LIMIT} 个收藏岗位
              </span>
            </div>
          </div>
        </section>
      )}

      {renderContent()}

      <JobSkillMindMap
        jobs={favorites}
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
    <StudentLayout activePath="/student/my-favorites">
      {pageContent}
    </StudentLayout>
  );
}

export default MyJobFavorites;
