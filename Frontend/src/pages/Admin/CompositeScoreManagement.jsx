import { useEffect, useMemo, useState } from 'react';
import './CompositeScoreManagement.css';
import { Progress } from 'antd';
import { useNavigate } from 'react-router-dom';
import PageHeader from '../../components/PageHeader';
import CompositeScoreDetailDrawer from '../../components/composite-score/CompositeScoreDetailDrawer';
import { getClassOptions } from '../../api/admin/class';
import { getActiveAcademicYearList } from '../../api/admin/academicYear';
import {
  getCompositeBaseScoreDetails,
  getCompositeExtraScoreDetails,
  getCompositeScoreRefreshTask,
  getCompositeScoreHistory,
  getCompositeScoreList,
  getCompositeScoreMeta,
  refreshCompositeScoreList
} from '../../api/admin/compositeScore';

const COMPOSITE_REFRESH_TASK_STORAGE_KEY = 'admin_composite_score_refresh_task';

function CompositeScoreManagement() {
  const navigate = useNavigate();
  const [academicYears, setAcademicYears] = useState([]);
  const [classes, setClasses] = useState([]);
  const [defaultAcademicYear, setDefaultAcademicYear] = useState('');
  const [filters, setFilters] = useState({
    academicYear: '',
    className: '',
    keyword: ''
  });
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [snapshotMeta, setSnapshotMeta] = useState(null);
  const [refreshTask, setRefreshTask] = useState(null);
  const [historyOpen, setHistoryOpen] = useState(false);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyRows, setHistoryRows] = useState([]);
  const [currentPage, setCurrentPage] = useState(1);
  const pageSize = 10;
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [detailStudent, setDetailStudent] = useState(null);
  const [detailType, setDetailType] = useState('extra');
  const [detailCategory, setDetailCategory] = useState('INTELLECTUAL');
  const [detailRows, setDetailRows] = useState([]);
  const [detailLoading, setDetailLoading] = useState(false);

  const visibleClasses = useMemo(
    () => classes.filter((item) => item.status !== 'inactive').sort((a, b) => {
      const gradeCompare = String(b.grade || '').localeCompare(String(a.grade || ''), 'zh-CN', { numeric: true });
      if (gradeCompare !== 0) return gradeCompare;
      return String(a.name || '').localeCompare(String(b.name || ''), 'zh-CN', { numeric: true });
    }),
    [classes]
  );

  useEffect(() => {
    fetchBaseData();
    try {
      const savedTask = window.localStorage.getItem(COMPOSITE_REFRESH_TASK_STORAGE_KEY);
      if (!savedTask) return;
      const parsedTask = JSON.parse(savedTask);
      if (parsedTask?.taskId) {
        setRefreshTask(parsedTask);
      }
    } catch {
      window.localStorage.removeItem(COMPOSITE_REFRESH_TASK_STORAGE_KEY);
    }
  }, []);

  useEffect(() => {
    if (!defaultAcademicYear) return;
    setFilters((prev) => ({
      ...prev,
      academicYear: prev.academicYear || defaultAcademicYear
    }));
  }, [defaultAcademicYear]);

  useEffect(() => {
    if (!filters.academicYear) return;
    fetchRows(filters, 1);
    fetchSnapshotMeta(filters.academicYear);
  }, [filters.academicYear]);

  useEffect(() => {
    if (!filters.academicYear || currentPage === 1) return;
    fetchRows(filters, currentPage, false);
  }, [currentPage]);

  useEffect(() => {
    if (!refreshTask?.taskId || ['completed', 'failed'].includes(refreshTask.status)) {
      return undefined;
    }

    const timer = window.setInterval(async () => {
      try {
        const response = await getCompositeScoreRefreshTask(refreshTask.taskId);
        const nextTask = response?.data || null;
        setRefreshTask(nextTask);
        if (nextTask && ['completed', 'failed'].includes(nextTask.status)) {
          await fetchRows(filters, currentPage, false);
          await fetchSnapshotMeta(filters.academicYear);
        }
      } catch {
        setRefreshTask(null);
        window.localStorage.removeItem(COMPOSITE_REFRESH_TASK_STORAGE_KEY);
        window.clearInterval(timer);
      }
    }, 1000);

    return () => window.clearInterval(timer);
  }, [refreshTask?.taskId, refreshTask?.status, filters, currentPage]);

  useEffect(() => {
    if (!refreshTask?.taskId) {
      window.localStorage.removeItem(COMPOSITE_REFRESH_TASK_STORAGE_KEY);
      return;
    }
    window.localStorage.setItem(COMPOSITE_REFRESH_TASK_STORAGE_KEY, JSON.stringify(refreshTask));
    if (['completed', 'failed'].includes(refreshTask.status)) {
      window.localStorage.removeItem(COMPOSITE_REFRESH_TASK_STORAGE_KEY);
    }
  }, [refreshTask]);

  const fetchBaseData = async () => {
    try {
      const [classData, academicYearData] = await Promise.all([
        getClassOptions(),
        getActiveAcademicYearList()
      ]);
      const yearList = Array.isArray(academicYearData) ? academicYearData : (academicYearData?.data || []);
      const nextDefaultYear = yearList.find((item) => item?.current)?.name || yearList[0]?.name || '';

      setClasses(Array.isArray(classData) ? classData : (classData?.data || []));
      setAcademicYears(yearList);
      setDefaultAcademicYear(nextDefaultYear);
    } catch (error) {
      console.error('获取综测分基础数据失败:', error);
    }
  };

  const fetchRows = async (nextFilters = filters, page = 1, syncPage = true) => {
    setLoading(true);
    try {
      const response = await getCompositeScoreList({
        academicYear: nextFilters.academicYear || undefined,
        className: nextFilters.className || undefined,
        keyword: nextFilters.keyword?.trim() || undefined,
        page: page - 1,
        size: pageSize
      });
      const pageData = response?.data;
      if (Array.isArray(pageData)) {
        setRows(pageData.slice((page - 1) * pageSize, page * pageSize));
        setTotalElements(pageData.length);
        setTotalPages(Math.max(1, Math.ceil(pageData.length / pageSize)));
      } else {
        setRows(Array.isArray(pageData?.content) ? pageData.content : []);
        setTotalElements(Number(pageData?.totalElements || 0));
        setTotalPages(Math.max(1, Number(pageData?.totalPages || 0) || 1));
        if (Number(pageData?.totalPages || 0) > 0 && page > Number(pageData.totalPages)) {
          setCurrentPage(Number(pageData.totalPages));
        }
      }
      if (syncPage) {
        setCurrentPage(page);
      }
    } catch (error) {
      console.error('获取综测分列表失败:', error);
      setRows([]);
      setTotalElements(0);
      setTotalPages(1);
      if (syncPage) {
        setCurrentPage(1);
      }
    } finally {
      setLoading(false);
    }
  };

  const fetchSnapshotMeta = async (academicYear) => {
    if (!academicYear) {
      setSnapshotMeta(null);
      return;
    }
    try {
      const response = await getCompositeScoreMeta({ academicYear });
      setSnapshotMeta(response?.data || null);
    } catch (error) {
      console.error('获取综测快照信息失败:', error);
      setSnapshotMeta(null);
    }
  };

  const handleSearch = () => {
    fetchRows(filters, 1);
    fetchSnapshotMeta(filters.academicYear);
  };

  const handleReset = () => {
    const nextFilters = {
      academicYear: defaultAcademicYear || '',
      className: '',
      keyword: ''
    };
    setFilters(nextFilters);
    fetchRows(nextFilters, 1);
    fetchSnapshotMeta(nextFilters.academicYear);
  };

  const handleRefresh = async () => {
    if (!filters.academicYear) return;
    setRefreshing(true);
    try {
      const response = await refreshCompositeScoreList({
        academicYear: filters.academicYear,
        className: filters.className || undefined
      });
      setRefreshTask(response?.data || null);
    } catch (error) {
      console.error('更新综测快照失败:', error);
    } finally {
      setRefreshing(false);
    }
  };

  const handleOpenHistory = async () => {
    if (!filters.academicYear) return;
    setHistoryOpen(true);
    setHistoryLoading(true);
    try {
      const response = await getCompositeScoreHistory({ academicYear: filters.academicYear });
      setHistoryRows(Array.isArray(response?.data) ? response.data : []);
    } catch (error) {
      console.error('获取综测更新历史失败:', error);
      setHistoryRows([]);
    } finally {
      setHistoryLoading(false);
    }
  };

  const handleOpenDetails = async (row, type, category) => {
    setDetailStudent(row);
    setDetailType(type);
    setDetailCategory(category);
    setDetailLoading(true);
    setDetailRows([]);
    try {
      const response = type === 'base'
        ? await getCompositeBaseScoreDetails(row.studentNo, {
            academicYear: filters.academicYear || undefined,
            category
          })
        : await getCompositeExtraScoreDetails(row.studentNo, {
            academicYear: filters.academicYear || undefined,
            category
          });
      setDetailRows(Array.isArray(response?.data) ? response.data : []);
    } catch (error) {
      console.error(type === 'base' ? '获取基础成绩明细失败:' : '获取附加分明细失败:', error);
      setDetailRows([]);
    } finally {
      setDetailLoading(false);
    }
  };

  const resolveDisplayScore = (primaryValue, fallbackValue) => Number((primaryValue ?? fallbackValue) ?? 0).toFixed(2);
  const refreshProgress = refreshTask?.totalCount
    ? Math.round(((refreshTask.processedCount || 0) / refreshTask.totalCount) * 100)
    : 0;
  const refreshFailedBeforeProcessing = refreshTask?.status === 'failed' && !(refreshTask?.processedCount > 0);
  const refreshStatusText = refreshFailedBeforeProcessing
    ? '校验失败，未开始更新'
    : (refreshTask?.message || refreshTask?.status);
  const refreshCountText = refreshFailedBeforeProcessing
    ? `未开始更新，本次目标 ${refreshTask?.totalCount || 0} 条`
    : `已更新 ${refreshTask?.processedCount || 0} / ${refreshTask?.totalCount || 0} 条`;
  const refreshMessagePreview = refreshTask?.message
    ? (refreshTask.message.length > 160 ? `${refreshTask.message.slice(0, 160)}...` : refreshTask.message)
    : '';
  const refreshButtonLabel = filters.className ? `更新${filters.className}综测数据` : '更新综测数据';

  return (
    <div className="composite-score-management">
      <PageHeader
        title="综测分"
        subtitle="按学年查看德育、智育、体育的基础分、附加分和综测总分。权重与科目归类请在“综测配置”页面维护。"
      />

      <div className="composite-search-area">
        <div className="composite-search-form">
          <div className="composite-form-item">
            <label>学年</label>
            <select
              value={filters.academicYear}
              onChange={(e) => setFilters((prev) => ({ ...prev, academicYear: e.target.value }))}
            >
              <option value="">请选择学年</option>
              {academicYears.map((item) => (
                <option key={item.id} value={item.name}>{item.name}</option>
              ))}
            </select>
          </div>

          <div className="composite-form-item">
            <label>班级</label>
            <select
              value={filters.className}
              onChange={(e) => setFilters((prev) => ({ ...prev, className: e.target.value }))}
            >
              <option value="">全部班级</option>
              {visibleClasses.map((item) => (
                <option key={item.id} value={item.name}>{item.name}</option>
              ))}
            </select>
          </div>

          <div className="composite-form-item">
            <label>个人</label>
            <input
              type="search"
              value={filters.keyword}
              placeholder="姓名或学号"
              onChange={(e) => setFilters((prev) => ({ ...prev, keyword: e.target.value }))}
              onKeyDown={(e) => {
                if (e.key === 'Enter') {
                  handleSearch();
                }
              }}
            />
          </div>

          <div className="composite-form-actions">
            <button className="composite-btn primary" onClick={handleRefresh} disabled={!filters.academicYear || refreshing}>
              {refreshing ? '更新中...' : refreshButtonLabel}
            </button>
            <button className="composite-btn default" onClick={handleOpenHistory} disabled={!filters.academicYear}>
              历史更新记录
            </button>
            <button className="composite-btn primary" onClick={handleSearch}>查询</button>
            <button className="composite-btn default" onClick={handleReset}>重置</button>
          </div>
        </div>
      </div>

      <div className="composite-table-wrapper">
        <div className="composite-table-header">
          <h3>综测分总表</h3>
          <div className="composite-table-meta">
            当前学年：{filters.academicYear || '未选择'}，共 {totalElements} 人
            <br />
            最近更新时间：{snapshotMeta?.generatedAt ? String(snapshotMeta.generatedAt).replace('T', ' ').slice(0, 19) : '未生成'}
            {snapshotMeta?.updatedByName ? `，更新人：${snapshotMeta.updatedByName}` : ''}
            {snapshotMeta?.scopeLabel ? `（${snapshotMeta.scopeLabel}）` : ''}
            {refreshTask ? (
              <>
                <br />
                刷新状态：{refreshStatusText}
                {`，${refreshCountText}`}
              </>
            ) : null}
          </div>
        </div>

        {refreshTask ? (
          <div className={`composite-task-progress ${refreshTask.status === 'failed' ? 'failed' : refreshTask.status === 'completed' ? 'completed' : 'running'}`}>
            <div className="composite-task-progress-meta">
              <span className="composite-task-status">{refreshStatusText}</span>
              <span className="composite-task-count">{refreshCountText}</span>
            </div>
            <Progress
              percent={refreshProgress}
              status={refreshTask.status === 'failed' ? 'exception' : refreshTask.status === 'completed' ? 'success' : 'active'}
            />
            {refreshTask.status === 'failed' ? (
              <div className="composite-task-action-row">
                <span className="composite-task-hint">
                  {refreshFailedBeforeProcessing ? '检测到未配置科目，请先前往“综测配置”补齐后再重试。' : (refreshMessagePreview || '刷新失败，请检查后重试。')}
                </span>
                <button
                  type="button"
                  className="composite-btn primary"
                  onClick={() => navigate('/admin/composite-score-config')}
                >
                  去补综测配置
                </button>
              </div>
            ) : null}
          </div>
        ) : null}

        {loading ? (
          <div className="composite-empty">加载中...</div>
        ) : rows.length === 0 ? (
          <div className="composite-empty">当前筛选范围暂无综测分数据</div>
        ) : (
          <>
            <div className="composite-table-scroll">
              <table className="composite-table">
                <thead>
                  <tr className="composite-header-group-row">
                    <th rowSpan="2">排名</th>
                    <th rowSpan="2">学号</th>
                    <th rowSpan="2">姓名</th>
                    <th rowSpan="2">班级</th>
                    <th rowSpan="2">专业</th>
                    <th rowSpan="2">年级</th>
                    <th className="composite-group-header moral" colSpan="2">德育</th>
                    <th className="composite-group-header intellectual" colSpan="2">智育</th>
                    <th className="composite-group-header physical" colSpan="2">体育</th>
                    <th rowSpan="2">综测总分</th>
                  </tr>
                  <tr className="composite-header-sub-row">
                    <th>基础分</th>
                    <th>附加分</th>
                    <th>基础分</th>
                    <th>附加分</th>
                    <th>基础分</th>
                    <th>附加分</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((row) => (
                    <tr key={row.studentId}>
                      <td><span className="composite-rank">#{row.rank}</span></td>
                      <td>{row.studentNo}</td>
                      <td>{row.studentName}</td>
                      <td>{row.className || '-'}</td>
                      <td>{row.majorName || '-'}</td>
                      <td>{row.grade ? `${row.grade}级` : '-'}</td>
                      <td>
                        <button type="button" className="composite-score-button base moral" onClick={() => handleOpenDetails(row, 'base', 'MORAL')}>
                          {resolveDisplayScore(row.weightedMoralBaseScore, row.moralBaseScore)}
                        </button>
                      </td>
                      <td>
                        <button type="button" className="composite-score-button extra moral" onClick={() => handleOpenDetails(row, 'extra', 'MORAL')}>
                          {resolveDisplayScore(row.weightedMoralExtraScore, row.moralExtraScore)}
                        </button>
                      </td>
                      <td>
                        <button type="button" className="composite-score-button base intellectual" onClick={() => handleOpenDetails(row, 'base', 'INTELLECTUAL')}>
                          {resolveDisplayScore(row.weightedIntellectualBaseScore, row.intellectualBaseScore)}
                        </button>
                      </td>
                      <td>
                        <button type="button" className="composite-score-button extra intellectual" onClick={() => handleOpenDetails(row, 'extra', 'INTELLECTUAL')}>
                          {resolveDisplayScore(row.weightedIntellectualExtraScore, row.intellectualExtraScore)}
                        </button>
                      </td>
                      <td>
                        <button type="button" className="composite-score-button base physical" onClick={() => handleOpenDetails(row, 'base', 'PHYSICAL')}>
                          {resolveDisplayScore(row.weightedPhysicalBaseScore, row.physicalBaseScore)}
                        </button>
                      </td>
                      <td>
                        <button type="button" className="composite-score-button extra physical" onClick={() => handleOpenDetails(row, 'extra', 'PHYSICAL')}>
                          {resolveDisplayScore(row.weightedPhysicalExtraScore, row.physicalExtraScore)}
                        </button>
                      </td>
                      <td><span className="composite-score-chip total">{Number(row.totalScore || 0).toFixed(2)}</span></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <div className="composite-pagination">
              <div className="composite-pagination-meta">
                共 {totalElements} 条，第 {currentPage}/{totalPages} 页
              </div>
              <div className="composite-pagination-actions">
                <button
                  type="button"
                  className="composite-btn default"
                  disabled={currentPage <= 1}
                  onClick={() => setCurrentPage((prev) => Math.max(1, prev - 1))}
                >
                  上一页
                </button>
                <button
                  type="button"
                  className="composite-btn default"
                  disabled={currentPage >= totalPages}
                  onClick={() => setCurrentPage((prev) => Math.min(totalPages, prev + 1))}
                >
                  下一页
                </button>
              </div>
            </div>
          </>
        )}
      </div>

      <CompositeScoreDetailDrawer
        open={Boolean(detailStudent)}
        onClose={() => setDetailStudent(null)}
        studentName={detailStudent?.studentName}
        className={detailStudent?.className}
        academicYear={filters.academicYear}
        type={detailType}
        category={detailCategory}
        rows={detailRows}
        loading={detailLoading}
        summary={detailStudent}
        finalScore={detailCategory === 'PHYSICAL' && detailType === 'base'
          ? (detailStudent?.weightedPhysicalBaseScore ?? detailStudent?.physicalBaseScore)
          : null}
        weight={detailStudent?.weight?.physicalBaseWeight ?? null}
      />

      {historyOpen && (
        <div className="composite-detail-overlay" onClick={() => setHistoryOpen(false)}>
          <div className="composite-detail-panel composite-history-panel" onClick={(e) => e.stopPropagation()}>
            <div className="composite-detail-header">
              <div>
                <h4>历史更新记录</h4>
                <p>{filters.academicYear || '未选择学年'}{snapshotMeta?.scopeLabel ? ` · ${snapshotMeta.scopeLabel}` : ''}</p>
              </div>
              <button type="button" className="composite-btn default" onClick={() => setHistoryOpen(false)}>关闭</button>
            </div>

            {historyLoading ? (
              <div className="composite-empty">加载中...</div>
            ) : historyRows.length === 0 ? (
              <div className="composite-empty">暂无更新记录</div>
            ) : (
              <table className="composite-table composite-detail-table">
                <thead>
                  <tr>
                    <th>更新时间</th>
                    <th>更新人</th>
                    <th>角色</th>
                    <th>更新人数</th>
                    <th>范围</th>
                  </tr>
                </thead>
                <tbody>
                  {historyRows.map((item) => (
                    <tr key={item.id}>
                      <td>{item.createdAt ? String(item.createdAt).replace('T', ' ').slice(0, 19) : '-'}</td>
                      <td>{item.updatedByName || '-'}</td>
                      <td>{item.updatedByRole === 'TEACHER' ? '老师' : item.updatedByRole === 'ADMIN' ? '管理员' : (item.updatedByRole || '-')}</td>
                      <td>{item.refreshCount ?? 0}</td>
                      <td>{item.scopeLabel || '-'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

export default CompositeScoreManagement;
