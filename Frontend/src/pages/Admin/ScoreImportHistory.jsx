import { Fragment, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import AppIcon from '../../components/AppIcon';
import { confirmAction } from '../../components/confirmDialogService';
import {
  deleteSubjectScoreImportStudentRecord,
  getSubjectScoreImportBatches,
  getSubjectScoreImportStudentRecordsByBatch,
  undoSubjectScoreImportBatch
} from '../../api/admin/score';
import { getStoredUser } from '../../utils/authStorage';
import './ScoreManagement.css';

const STUDENT_RECORD_PAGE_SIZE = 20;
const EMPTY_STUDENT_RECORD_PAGE = {
  content: [],
  totalElements: 0,
  totalPages: 0,
  page: 0,
  size: STUDENT_RECORD_PAGE_SIZE
};

function formatDateTime(value) {
  if (!value) return '-';
  return String(value).replace('T', ' ').slice(0, 19);
}

function normalizeStudentRecordPage(value) {
  if (Array.isArray(value)) {
    return {
      content: value,
      totalElements: value.length,
      totalPages: value.length ? 1 : 0,
      page: 0,
      size: value.length || STUDENT_RECORD_PAGE_SIZE
    };
  }

  const content = Array.isArray(value?.content) ? value.content : [];
  const totalElements = Number(value?.totalElements ?? content.length);
  const size = Number(value?.size ?? STUDENT_RECORD_PAGE_SIZE);
  return {
    content,
    totalElements,
    totalPages: Number(value?.totalPages ?? (totalElements ? Math.ceil(totalElements / size) : 0)),
    page: Number(value?.page ?? 0),
    size
  };
}

function ScoreImportHistory() {
  const navigate = useNavigate();
  const user = getStoredUser();
  const isAdmin = user.role === 'ADMIN';
  const [batches, setBatches] = useState([]);
  const [studentRecordsByBatch, setStudentRecordsByBatch] = useState({});
  const [detailLoadingByBatch, setDetailLoadingByBatch] = useState({});
  const [loading, setLoading] = useState(false);
  const [deletingId, setDeletingId] = useState('');
  const [undoingBatchId, setUndoingBatchId] = useState('');
  const [expandedBatchIds, setExpandedBatchIds] = useState(() => new Set());
  const [searchText, setSearchText] = useState('');
  const [appliedSearchText, setAppliedSearchText] = useState('');
  const [feedback, setFeedback] = useState(null);

  const fetchBatches = async () => {
    setLoading(true);
    try {
      const response = await getSubjectScoreImportBatches();
      setBatches(Array.isArray(response?.data) ? response.data : []);
    } catch (error) {
      console.error('获取成绩导入历史失败:', error);
      setFeedback({
        type: 'error',
        lines: [error?.message || '获取成绩导入历史失败']
      });
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchBatches();
  }, []);

  const summaryCards = useMemo(() => {
    const total = batches.length;
    const active = batches.filter((item) => !item.undone).length;
    const loadedStudentRecords = Object.values(studentRecordsByBatch).reduce(
      (sum, pageData) => sum + Number(pageData?.totalElements || 0),
      0
    );
    const scoreChanges = batches.reduce(
      (sum, item) => sum + Number(item.createdScoreCount || 0) + Number(item.updatedScoreCount || 0),
      0
    );

    return [
      { key: 'total', title: '导入记录', value: `${total}`, note: '可展开查看', icon: 'import', accent: 'blue' },
      { key: 'active', title: '有效记录', value: `${active}`, note: '未撤销', icon: 'scores', accent: 'green' },
      { key: 'students', title: '已展开学生', value: `${loadedStudentRecords}`, note: '当前已加载', icon: 'review', accent: 'orange' },
      { key: 'changes', title: '成绩变更', value: `${scoreChanges}`, note: '新增与更新', icon: 'analysis', accent: 'purple' }
    ];
  }, [batches, studentRecordsByBatch]);

  const fetchStudentRecords = async (
    batchId,
    keyword = appliedSearchText,
    page = 0,
    size = STUDENT_RECORD_PAGE_SIZE
  ) => {
    if (!batchId) return;
    setDetailLoadingByBatch((prev) => ({ ...prev, [batchId]: true }));
    try {
      const response = await getSubjectScoreImportStudentRecordsByBatch(batchId, {
        keyword: keyword || undefined,
        page,
        size
      });
      setStudentRecordsByBatch((prev) => ({
        ...prev,
        [batchId]: normalizeStudentRecordPage(response?.data)
      }));
    } catch (error) {
      setFeedback({
        type: 'error',
        lines: [error?.message || '获取学生导入明细失败']
      });
    } finally {
      setDetailLoadingByBatch((prev) => ({ ...prev, [batchId]: false }));
    }
  };

  const toggleBatch = async (batchId) => {
    const shouldExpand = !expandedBatchIds.has(batchId);
    setExpandedBatchIds((prev) => {
      const next = new Set(prev);
      if (!shouldExpand) {
        next.delete(batchId);
      } else {
        next.add(batchId);
      }
      return next;
    });
    if (shouldExpand && !studentRecordsByBatch[batchId]) {
      await fetchStudentRecords(batchId);
    }
  };

  const handleSearch = async () => {
    const nextKeyword = searchText.trim();
    setAppliedSearchText(nextKeyword);
    setStudentRecordsByBatch({});
    await Promise.all(Array.from(expandedBatchIds).map((batchId) => fetchStudentRecords(batchId, nextKeyword, 0)));
  };

  const handleResetSearch = async () => {
    setSearchText('');
    setAppliedSearchText('');
    setStudentRecordsByBatch({});
    await Promise.all(Array.from(expandedBatchIds).map((batchId) => fetchStudentRecords(batchId, '', 0)));
  };

  const handlePageChange = async (batchId, nextPage) => {
    await fetchStudentRecords(batchId, appliedSearchText, nextPage);
  };

  const handleUndoBatch = async (batch) => {
    if (!batch?.id || batch.undone || !batch.undoAvailable) return;
    if (!await confirmAction({
      title: '回溯整条导入记录？',
      message: '系统会处理当前尚未单独删除的学生记录，已经删除过的学生不会重复计算。',
      detail: '新增的成绩会删除，更新过的成绩会恢复到导入前。',
      confirmText: '确认回溯',
      tone: 'warning'
    })) {
      return;
    }

    const batchId = batch.id;
    const currentPage = studentRecordsByBatch[batchId]?.page || 0;
    setUndoingBatchId(batchId);
    setFeedback({
      type: 'progress',
      lines: ['正在回溯整条导入记录，请稍候。']
    });
    try {
      const response = await undoSubjectScoreImportBatch(batchId);
      const undoInfo = response?.undoImportInfo || {};
      setFeedback({
        type: 'success',
        lines: [
          `回溯新增成绩 ${undoInfo.deletedCreatedScoreCount || 0} 条`,
          `恢复已更新成绩 ${undoInfo.restoredUpdatedScoreCount || 0} 条`,
          `删除新增账号 ${undoInfo.deletedCreatedUserCount || 0} 个`,
          `删除新增学生 ${undoInfo.deletedCreatedStudentCount || 0} 个`,
          `删除新增科目 ${undoInfo.deletedCreatedSubjectCount || 0} 个`
        ]
      });
      setStudentRecordsByBatch((prev) => {
        const next = { ...prev };
        delete next[batchId];
        return next;
      });
      await fetchBatches();
      if (expandedBatchIds.has(batchId)) {
        await fetchStudentRecords(batchId, appliedSearchText, currentPage);
      }
    } catch (error) {
      setFeedback({
        type: 'error',
        lines: [error?.response?.data?.errorMessage || error?.message || '回溯失败']
      });
    } finally {
      setUndoingBatchId('');
    }
  };

  const handleDelete = async (record) => {
    if (!record?.batchId || !record?.studentId || record.undone || !record.deleteAvailable) return;
    if (!await confirmAction({
      title: '删除学生导入记录？',
      message: `${record.studentName || record.studentNo || '该学生'} 的本次新增成绩会删除，更新过的成绩会恢复到导入前。`,
      confirmText: '删除并恢复',
      tone: 'danger'
    })) {
      return;
    }

    setDeletingId(record.id);
    const currentPage = studentRecordsByBatch[record.batchId]?.page || 0;
    const currentPageSize = studentRecordsByBatch[record.batchId]?.content?.length || 0;
    const nextPage = currentPage > 0 && currentPageSize <= 1 ? currentPage - 1 : currentPage;
    setFeedback({
      type: 'progress',
      lines: ['正在删除这名学生的导入记录，请稍候。']
    });
    try {
      const response = await deleteSubjectScoreImportStudentRecord(record.batchId, record.studentId);
      const undoInfo = response?.undoImportInfo || {};
      setFeedback({
        type: 'success',
        lines: [
          `删除新增成绩 ${undoInfo.deletedCreatedScoreCount || 0} 条`,
          `恢复已更新成绩 ${undoInfo.restoredUpdatedScoreCount || 0} 条`,
          `删除新增账号 ${undoInfo.deletedCreatedUserCount || 0} 个`,
          `删除新增学生 ${undoInfo.deletedCreatedStudentCount || 0} 个`,
          `删除新增科目 ${undoInfo.deletedCreatedSubjectCount || 0} 个`
        ]
      });
      setStudentRecordsByBatch((prev) => {
        const next = { ...prev };
        delete next[record.batchId];
        return next;
      });
      await fetchBatches();
      if (expandedBatchIds.has(record.batchId)) {
        await fetchStudentRecords(record.batchId, appliedSearchText, nextPage);
      }
    } catch (error) {
      setFeedback({
        type: 'error',
        lines: [error?.response?.data?.errorMessage || error?.message || '删除失败']
      });
    } finally {
      setDeletingId('');
    }
  };

  return (
    <div className="score-workbench">
      <div className="score-workbench-headline">
        <div>
          <h2>导入历史</h2>
          <p>按导入记录查看，展开后可以删除单个学生本次导入的数据。</p>
        </div>
        <div className="score-alert-actions">
          <button type="button" className="secondary-btn" onClick={fetchBatches} disabled={loading}>刷新</button>
          <button type="button" className="primary-btn" onClick={() => navigate('/admin/scores')}>去导入</button>
        </div>
      </div>

      <section className="score-history-search">
        <input
          value={searchText}
          onChange={(event) => setSearchText(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === 'Enter') {
              handleSearch();
            }
          }}
          placeholder="搜索学号、姓名、班级或科目，展开导入记录后显示匹配学生"
        />
        <button type="button" className="secondary-btn" onClick={handleSearch}>搜索</button>
        <button type="button" className="secondary-btn" onClick={handleResetSearch} disabled={!searchText && !appliedSearchText}>
          重置
        </button>
        {appliedSearchText ? <span>当前搜索：{appliedSearchText}</span> : null}
      </section>

      <section className="score-summary-grid">
        {summaryCards.map((item) => (
          <article key={item.key} className={`score-summary-card accent-${item.accent}`}>
            <span className="summary-icon"><AppIcon name={item.icon} size={22} /></span>
            <div className="summary-copy">
              <span>{item.title}</span>
              <strong>{item.value}</strong>
              <small>{item.note}</small>
            </div>
          </article>
        ))}
      </section>

      {feedback?.lines?.length ? (
        <div className={`feedback-card ${feedback.type || 'info'}`}>
          {feedback.lines.map((line, index) => <p key={`${feedback.type}-${index}`}>{line}</p>)}
        </div>
      ) : null}

      <section className="score-panel">
        <div className="panel-title with-actions">
          <span>导入记录</span>
          {loading ? <span className="score-history-muted">加载中...</span> : null}
        </div>
        {batches.length ? (
          <div className="detail-table-wrap">
            <table className="detail-table score-history-table">
              <thead>
                <tr>
                  <th>展开</th>
                  <th>导入时间</th>
                  {isAdmin ? <th>导入人</th> : null}
                  <th>学年</th>
                  <th>班级</th>
                  <th>学生数</th>
                  <th>新增</th>
                  <th>更新</th>
                  <th>状态</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                {batches.map((batch) => {
                  const batchId = batch.id;
                  const expanded = expandedBatchIds.has(batchId);
                  const detailPage = studentRecordsByBatch[batchId] || EMPTY_STUDENT_RECORD_PAGE;
                  const studentRecords = detailPage.content || [];
                  const totalStudentRecords = Number(detailPage.totalElements || 0);
                  const batchStudentCount = Number(batch.studentRecordCount ?? 0);
                  const totalPages = Number(detailPage.totalPages || 0);
                  const currentPage = Number(detailPage.page || 0);
                  const detailLoading = Boolean(detailLoadingByBatch[batchId]);
                  return (
                    <Fragment key={batchId}>
                      <tr className="score-history-batch-row">
                        <td>
                          <button
                            type="button"
                            className="secondary-btn small score-history-expand-btn"
                            onClick={() => toggleBatch(batchId)}
                          >
                            {expanded ? '收起' : '展开'}
                          </button>
                        </td>
                        <td>{formatDateTime(batch.createdAt)}</td>
                        {isAdmin ? <td>{batch.createdByName || '-'}</td> : null}
                        <td>{batch.semester || '-'}</td>
                        <td>{batch.className || '-'}</td>
                        <td>{batchStudentCount || '-'}</td>
                        <td>{batch.createdScoreCount || 0}</td>
                        <td>{batch.updatedScoreCount || 0}</td>
                        <td>
                          <span className={`score-history-status ${batch.undone ? 'undone' : 'active'}`}>
                            {batch.undone ? '已撤销' : '有效'}
                          </span>
                        </td>
                        <td>
                          {batch.undone ? (
                            <span className="score-history-action-muted">已撤销</span>
                          ) : !batch.undoAvailable ? (
                            <span className="score-history-action-muted">无可回溯</span>
                          ) : (
                            <button
                              type="button"
                              className="danger-btn small"
                              disabled={undoingBatchId === batchId}
                              onClick={() => handleUndoBatch(batch)}
                            >
                              {undoingBatchId === batchId ? '回溯中' : '回溯整条'}
                            </button>
                          )}
                        </td>
                      </tr>
                      {expanded ? (
                        <tr className="score-history-expanded-row">
                          <td colSpan={isAdmin ? 10 : 9} className="score-history-expanded-cell">
                            <div className="score-history-student-detail">
                              <div className="score-history-detail-title">
                                <span>学生明细</span>
                                <div className="score-history-detail-search">
                                  <input
                                    value={searchText}
                                    onChange={(event) => setSearchText(event.target.value)}
                                    onKeyDown={(event) => {
                                      if (event.key === 'Enter') {
                                        handleSearch();
                                      }
                                    }}
                                    placeholder="搜索学号、姓名、班级或科目"
                                  />
                                  <button type="button" className="secondary-btn small" onClick={handleSearch}>搜索</button>
                                  <button
                                    type="button"
                                    className="secondary-btn small"
                                    onClick={handleResetSearch}
                                    disabled={!searchText && !appliedSearchText}
                                  >
                                    重置
                                  </button>
                                  <small>{detailLoading ? '加载中...' : `共 ${totalStudentRecords} 条`}</small>
                                </div>
                              </div>
                              {detailLoading ? (
                                <div className="empty-inline">正在加载学生明细</div>
                              ) : studentRecords.length ? (
                                <>
                                  <table className="score-history-nested-table">
                                    <thead>
                                      <tr>
                                        <th>学号</th>
                                        <th>姓名</th>
                                        <th>班级</th>
                                        <th>科目</th>
                                        <th>新增</th>
                                        <th>更新</th>
                                        <th>状态</th>
                                        <th>操作</th>
                                      </tr>
                                    </thead>
                                    <tbody>
                                      {studentRecords.map((record) => (
                                        <tr key={record.id}>
                                          <td>{record.studentNo || '-'}</td>
                                          <td>{record.studentName || '-'}</td>
                                          <td>{record.className || '-'}</td>
                                          <td className="score-history-subjects">{record.subjectSummary || '-'}</td>
                                          <td>{record.createdScoreCount || 0}</td>
                                          <td>{record.updatedScoreCount || 0}</td>
                                          <td>
                                            <span className={`score-history-status ${record.undone ? 'undone' : 'active'}`}>
                                              {record.undone ? '已撤销' : '有效'}
                                            </span>
                                          </td>
                                          <td>
                                            {record.undone ? (
                                              <span className="score-history-action-muted">已撤销</span>
                                            ) : !record.deleteAvailable ? (
                                              <span className="score-history-action-muted">不可删除</span>
                                            ) : (
                                              <button
                                                type="button"
                                                className="danger-btn small"
                                                disabled={deletingId === record.id}
                                                onClick={() => handleDelete(record)}
                                              >
                                                {deletingId === record.id ? '删除中' : '删除'}
                                              </button>
                                            )}
                                          </td>
                                        </tr>
                                      ))}
                                    </tbody>
                                  </table>
                                  <div className="score-history-pagination">
                                    <span>
                                      第 {totalPages ? currentPage + 1 : 0} / {totalPages} 页，每页 {detailPage.size || STUDENT_RECORD_PAGE_SIZE} 条
                                    </span>
                                    <div>
                                      <button
                                        type="button"
                                        className="secondary-btn small"
                                        disabled={currentPage <= 0}
                                        onClick={() => handlePageChange(batchId, currentPage - 1)}
                                      >
                                        上一页
                                      </button>
                                      <button
                                        type="button"
                                        className="secondary-btn small"
                                        disabled={!totalPages || currentPage + 1 >= totalPages}
                                        onClick={() => handlePageChange(batchId, currentPage + 1)}
                                      >
                                        下一页
                                      </button>
                                    </div>
                                  </div>
                                </>
                              ) : (
                                <div className="empty-inline">{appliedSearchText ? '没有匹配的学生明细' : '暂无学生明细'}</div>
                              )}
                            </div>
                          </td>
                        </tr>
                      ) : null}
                    </Fragment>
                  );
                })}
              </tbody>
            </table>
          </div>
        ) : (
          <div className="empty-inline">{loading ? '正在加载导入历史' : '暂无导入历史'}</div>
        )}
      </section>
    </div>
  );
}

export default ScoreImportHistory;
