import { useEffect, useMemo, useState } from 'react';
import './ScoreQuery.css';
import request from '../../utils/request';
import { getActiveAcademicYearList } from '../../api/admin/academicYear';
import { getClassOptions } from '../../api/admin/class';
import { confirmAction } from '../../components/confirmDialogService';

const getScoreLevelClass = (value) => {
  const score = Number(value);
  if (!Number.isFinite(score)) return '';
  if (score < 60) return 'fail';
  if (score < 80) return 'pass';
  if (score < 90) return 'good';
  return 'excellent';
};

function ScoreQuery() {
  const [selectedAcademicYear, setSelectedAcademicYear] = useState('');
  const [defaultAcademicYear, setDefaultAcademicYear] = useState('');
  const [selectedClass, setSelectedClass] = useState('');

  const [academicYears, setAcademicYears] = useState([]);
  const [classes, setClasses] = useState([]);
  const [visibleRows, setVisibleRows] = useState([]);
  const [visibleSubjects, setVisibleSubjects] = useState([]);
  const [hasSearched, setHasSearched] = useState(false);

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [cleanupSubject, setCleanupSubject] = useState('');
  const [cleanupPreview, setCleanupPreview] = useState(null);
  const [cleanupLoading, setCleanupLoading] = useState(false);
  const [cleanupMessage, setCleanupMessage] = useState(null);

  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize] = useState(10);
  const [totalElements, setTotalElements] = useState(0);
  const [autoLoaded, setAutoLoaded] = useState(false);

  const fetchScoreSummary = async ({ academicYear, className, page = 1, showEmptyError = true }) => {
    try {
      if (!academicYear && !className) {
        if (showEmptyError) {
          setError('请至少选择一个查询条件');
        }
        setVisibleRows([]);
        setVisibleSubjects([]);
        setTotalElements(0);
        return;
      }

      const data = await request({
        url: '/subject-scores/rain/visible',
        method: 'get',
        params: {
          aggregate: true,
          academicYear: academicYear || undefined,
          className: className || undefined,
          page: page - 1,
          size: pageSize
        }
      });
      setVisibleRows(Array.isArray(data?.content) ? data.content : []);
      setVisibleSubjects(Array.isArray(data?.subjects) ? data.subjects : []);
      setTotalElements(Number(data?.totalElements || 0));
      setCleanupSubject((current) => (data?.subjects?.includes(current) ? current : ''));
      setCleanupPreview(null);
      setCleanupMessage(null);
    } catch (fetchError) {
      console.error('获取成绩数据失败:', fetchError);
      setError('获取成绩数据失败');
    }
  };

  const fetchAcademicYears = async () => {
    try {
      const data = await getActiveAcademicYearList();
      const list = Array.isArray(data) ? data : (data?.data || []);
      setAcademicYears(list);
      const currentYearName = list.find((item) => item?.current)?.name || '';
      const fallbackYearName = currentYearName || list[0]?.name || '';
      setDefaultAcademicYear(fallbackYearName);
      if (fallbackYearName) {
        setSelectedAcademicYear((current) => current || fallbackYearName);
      }
    } catch (fetchError) {
      console.error('获取学年列表失败:', fetchError);
    }
  };

  const fetchClasses = async () => {
    try {
      const data = await getClassOptions();
      const classList = Array.isArray(data) ? data.filter((item) => item.status !== 'inactive') : [];
      setClasses(classList);
    } catch (fetchError) {
      console.error('获取班级列表失败:', fetchError);
    }
  };

  useEffect(() => {
    fetchAcademicYears();
    fetchClasses();
  }, []);

  useEffect(() => {
    if (hasSearched) {
      runSearch({ page: currentPage });
    }
  }, [currentPage]);

  useEffect(() => {
    setCleanupPreview(null);
    setCleanupMessage(null);
    if (cleanupSubject && !visibleSubjects.includes(cleanupSubject)) {
      setCleanupSubject('');
    }
  }, [cleanupSubject, selectedAcademicYear, selectedClass, visibleSubjects]);

  const runSearch = async ({
    academicYear = selectedAcademicYear,
    className = selectedClass,
    page = 1,
    showEmptyError = true
  } = {}) => {
    setLoading(true);
    setError('');
    setHasSearched(true);

    try {
      await fetchScoreSummary({ academicYear, className, page, showEmptyError });
      setCurrentPage(page);
    } catch (searchError) {
      console.error('查询失败:', searchError);
      setError('查询失败，请稍后重试');
    } finally {
      setLoading(false);
    }
  };

  const handleSearch = () => {
    runSearch();
  };

  const handleReset = () => {
    setSelectedAcademicYear(defaultAcademicYear || '');
    setSelectedClass('');
    setVisibleRows([]);
    setVisibleSubjects([]);
    setTotalElements(0);
    setCurrentPage(1);
    setError('');
    setHasSearched(false);
    setCleanupSubject('');
    setCleanupPreview(null);
    setCleanupMessage(null);
  };

  const buildCleanupPayload = () => ({
    className: selectedClass,
    semester: selectedAcademicYear,
    subjectNames: cleanupSubject ? [cleanupSubject] : []
  });

  const validateCleanupInput = () => {
    if (!selectedAcademicYear) return '请先选择学年并查询';
    if (!selectedClass) return '请先选择具体班级，避免清理范围不明确';
    if (!cleanupSubject) return '请选择要清理的科目';
    return '';
  };

  const previewCleanup = async () => {
    const errorMessage = validateCleanupInput();
    if (errorMessage) {
      setCleanupMessage({ type: 'error', text: errorMessage });
      return;
    }

    setCleanupLoading(true);
    setCleanupMessage(null);
    try {
      const data = await request({
        url: '/subject-scores/rain/cleanup/preview',
        method: 'post',
        data: buildCleanupPayload()
      });
      const preview = data?.data || data;
      setCleanupPreview(preview);
      setCleanupMessage({
        type: 'info',
        text: `将匹配 ${preview?.matchedCount || 0} 条成绩记录`
      });
    } catch (previewError) {
      setCleanupPreview(null);
      setCleanupMessage({
        type: 'error',
        text: previewError?.response?.data?.message || '预览清理失败'
      });
    } finally {
      setCleanupLoading(false);
    }
  };

  const cleanupScores = async () => {
    const errorMessage = validateCleanupInput();
    if (errorMessage) {
      setCleanupMessage({ type: 'error', text: errorMessage });
      return;
    }
    if (!cleanupPreview) {
      setCleanupMessage({ type: 'error', text: '请先预览影响范围，再确认清理' });
      return;
    }

    const matchedCount = cleanupPreview.matchedCount ?? 0;
    if (!await confirmAction({
      title: '删除科目成绩？',
      message: `将删除 ${selectedClass} 在 ${selectedAcademicYear} 的“${cleanupSubject}”成绩。`,
      detail: `当前匹配成绩：${matchedCount} 条`,
      confirmText: '确认删除',
      tone: 'danger'
    })) {
      return;
    }

    setCleanupLoading(true);
    setCleanupMessage(null);
    try {
      const data = await request({
        url: '/subject-scores/rain/cleanup',
        method: 'delete',
        data: buildCleanupPayload()
      });
      const result = data?.data || data;
      const deletedCount = result?.deletedCount || 0;
      await runSearch({ page: 1, showEmptyError: false });
      setCleanupPreview(null);
      setCleanupSubject('');
      setCleanupMessage({ type: 'success', text: `清理完成，已删除 ${deletedCount} 条成绩` });
    } catch (cleanupError) {
      setCleanupMessage({
        type: 'error',
        text: cleanupError?.response?.data?.message || '清理成绩失败'
      });
    } finally {
      setCleanupLoading(false);
    }
  };

  const totalPages = useMemo(() => Math.ceil(totalElements / pageSize) || 1, [totalElements, pageSize]);

  useEffect(() => {
    if (!defaultAcademicYear || autoLoaded) {
      return;
    }
    runSearch({ academicYear: defaultAcademicYear, className: '', showEmptyError: false });
    setAutoLoaded(true);
  }, [defaultAcademicYear, autoLoaded]);

  return (
    <div className="score-query">
      <div className="search-form">
        <div className="form-content">
          <div className="form-item">
            <label>学年</label>
            <select
              value={selectedAcademicYear}
              onChange={(e) => setSelectedAcademicYear(e.target.value)}
            >
              <option value="">请选择学年</option>
              {academicYears.map((year) => (
                <option key={year.id || year.name} value={year.name}>
                  {year.name}
                </option>
              ))}
            </select>
          </div>

          <div className="form-item">
            <label>班级</label>
            <select
              value={selectedClass}
              onChange={(e) => setSelectedClass(e.target.value)}
            >
              <option value="">请选择班级</option>
              {classes.map((cls) => (
                <option key={cls.id} value={cls.name}>
                  {cls.name}
                </option>
              ))}
            </select>
          </div>
          <div className="form-actions">
            <button
              className="btn btn-primary"
              onClick={handleSearch}
              disabled={loading}
            >
              {loading ? '查询中...' : '查询'}
            </button>
            <button
              className="btn btn-default"
              onClick={handleReset}
            >
              重置
            </button>
          </div>
        </div>

        {error ? (
          <div className="error-message">{error}</div>
        ) : null}
      </div>

      <div className="result-section">
        <div className="result-header">
          <div>
            <h3>学年成绩总表</h3>
            <p>清理成绩时请先筛选到具体班级，再选择当前结果中的科目。</p>
          </div>
          <div className="score-cleanup-box">
            <label>
              <span>清理科目</span>
              <select
                value={cleanupSubject}
                onChange={(e) => {
                  setCleanupSubject(e.target.value);
                  setCleanupPreview(null);
                  setCleanupMessage(null);
                }}
                disabled={!hasSearched || visibleSubjects.length === 0}
              >
                <option value="">请选择科目</option>
                {visibleSubjects.map((subject) => (
                  <option key={subject} value={subject}>{subject}</option>
                ))}
              </select>
            </label>
            <button
              className="btn btn-default"
              onClick={previewCleanup}
              disabled={cleanupLoading || !hasSearched}
            >
              预览影响
            </button>
            <button
              className="btn btn-danger"
              onClick={cleanupScores}
              disabled={cleanupLoading}
            >
              确认清理
            </button>
          </div>
        </div>

        {cleanupPreview ? (
          <div className="cleanup-preview-bar">
            <span>班级：{cleanupPreview.className}</span>
            <span>学年：{cleanupPreview.semester}</span>
            <span>科目：{cleanupPreview.subjectNames?.join('、')}</span>
            <strong>将删除 {cleanupPreview.matchedCount || 0} 条</strong>
          </div>
        ) : null}

        {cleanupMessage ? (
          <div className={`cleanup-message ${cleanupMessage.type}`}>{cleanupMessage.text}</div>
        ) : null}

        <div className="score-table-shell">
          <div className="data-table-wrapper">
            <table className="data-table score-summary-table">
              <thead>
                <tr>
                  <th>学号</th>
                  <th className="student-name-sticky-column">学生</th>
                  <th>班级</th>
                  {visibleSubjects.map((subject) => (
                    <th key={subject}>{subject}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {loading ? (
                  <tr>
                    <td colSpan={3 + visibleSubjects.length} className="loading-cell">查询中...</td>
                  </tr>
                ) : !hasSearched ? (
                  <tr>
                    <td colSpan={Math.max(4, 3 + visibleSubjects.length)} className="empty-cell">请选择学年或班级后点击查询按钮</td>
                  </tr>
                ) : visibleRows.length === 0 ? (
                  <tr>
                    <td colSpan={Math.max(4, 3 + visibleSubjects.length)} className="empty-cell">暂无符合条件的数据</td>
                  </tr>
                ) : (
                  visibleRows.map((row) => (
                    <tr key={row.studentNo}>
                      <td>{row.studentNo}</td>
                      <td className="student-name-sticky-column">{row.studentName}</td>
                      <td>{row.className}</td>
                      {visibleSubjects.map((subject) => (
                        <td key={`${row.studentNo}-${subject}`}>
                          {row.scoresBySubject[subject] !== undefined && row.scoresBySubject[subject] !== null ? (
                            <span className={`score-badge ${getScoreLevelClass(row.scoresBySubject[subject])}`}>
                              {row.scoresBySubject[subject]}
                            </span>
                          ) : '-'}
                        </td>
                      ))}
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </div>

        {hasSearched && totalElements > 0 ? (
          <div className="pagination">
            <span className="pagination-info">
              共 {totalElements} 名学生，第 {currentPage}/{totalPages} 页
            </span>
            <div className="pagination-btns">
              <button
                className="btn btn-sm"
                disabled={currentPage === 1}
                onClick={() => setCurrentPage((prev) => prev - 1)}
              >
                上一页
              </button>
              <div className="page-jump">
                <input
                  type="number"
                  className="page-input"
                  min="1"
                  max={totalPages}
                  value={currentPage}
                  onChange={(e) => {
                    const page = parseInt(e.target.value, 10);
                    if (!Number.isNaN(page) && page >= 1 && page <= totalPages) {
                      setCurrentPage(page);
                    }
                  }}
                />
                <button
                  className="btn btn-sm"
                  onClick={() => {
                    const input = document.querySelector('.page-input');
                    const page = parseInt(input?.value, 10);
                    if (!Number.isNaN(page) && page >= 1 && page <= totalPages) {
                      setCurrentPage(page);
                    }
                  }}
                >
                  跳转
                </button>
              </div>
              <button
                className="btn btn-sm"
                disabled={currentPage === totalPages || totalElements === 0}
                onClick={() => setCurrentPage((prev) => prev + 1)}
              >
                下一页
              </button>
            </div>
          </div>
        ) : null}
      </div>
    </div>
  );
}

export default ScoreQuery;
