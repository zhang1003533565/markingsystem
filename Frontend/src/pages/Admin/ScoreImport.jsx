import { useEffect, useMemo, useState } from 'react';
import './ScoreImport.css';
import {
  uploadScoreFile,
  getImportHistory,
  deleteImportRecord,
  batchDeleteImportRecords,
  downloadTemplate
} from '../../api/admin/score';
import PageHeader from '../../components/PageHeader';
import AppIcon from '../../components/AppIcon';
import { confirmAction } from '../../components/confirmDialogService';

const importSteps = [
  { key: 'subject', title: '选择科目', description: '确定导入范围' },
  { key: 'upload', title: '上传文件与字段识别', description: '选择成绩文件' },
  { key: 'mapping', title: '基础映射', description: '确认字段关系' },
  { key: 'confirm', title: '校验并导入', description: '执行导入' }
];

const subjectOptions = [
  { value: 'intellectual', label: '智育成绩', note: '课程成绩、考试成绩等常规科目' },
  { value: 'physical', label: '体育成绩', note: '体测、体育课程或体育基础分相关成绩' },
  { value: 'custom', label: '其他成绩', note: '需要单独归档或后续再配置的成绩文件' }
];

function ScoreImport() {
  const [importHistory, setImportHistory] = useState([]);
  const [loading, setLoading] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [selectedRecords, setSelectedRecords] = useState([]);
  const [file, setFile] = useState(null);
  const [uploadProgress, setUploadProgress] = useState(0);
  const [currentStep, setCurrentStep] = useState(0);
  const [selectedSubjectType, setSelectedSubjectType] = useState(subjectOptions[0].value);
  const [fieldMapping, setFieldMapping] = useState({
    studentNo: '学号',
    studentName: '姓名',
    score: '成绩'
  });
  const [isDragging, setIsDragging] = useState(false);
  const [feedback, setFeedback] = useState(null);

  useEffect(() => {
    fetchImportHistory();
  }, []);

  const fetchImportHistory = async () => {
    setLoading(true);
    try {
      const res = await getImportHistory();
      setImportHistory(Array.isArray(res) ? res : (res.data || []));
    } catch (error) {
      console.error('获取导入历史失败:', error);
    } finally {
      setLoading(false);
    }
  };

  const selectedSubject = useMemo(
    () => subjectOptions.find((item) => item.value === selectedSubjectType) || subjectOptions[0],
    [selectedSubjectType]
  );

  const canEnterStep = (stepIndex) => {
    if (stepIndex <= 0) return true;
    if (stepIndex === 1) return Boolean(selectedSubjectType);
    if (stepIndex === 2) return Boolean(selectedSubjectType && file);
    return Boolean(selectedSubjectType && file && fieldMapping.studentNo && fieldMapping.studentName && fieldMapping.score);
  };

  const goToStep = (stepIndex) => {
    if (!canEnterStep(stepIndex)) return;
    setCurrentStep(stepIndex);
  };

  const handleFileChange = (selectedFile) => {
    if (!selectedFile) return;
    const validTypes = [
      'application/vnd.ms-excel',
      'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      'text/csv'
    ];
    const validExtensions = /\.(xlsx|xls|csv)$/i.test(selectedFile.name || '');
    if (!validTypes.includes(selectedFile.type) && !validExtensions) {
      alert('请上传 Excel 或 CSV 文件');
      return;
    }
    setFile(selectedFile);
    setCurrentStep(2);
    setFeedback({
      type: 'success',
      title: '文件已就绪',
      message: `已选择 ${selectedFile.name}，请继续确认基础字段映射。`
    });
  };

  const handleInputChange = (e) => {
    handleFileChange(e.target.files[0]);
  };

  const handleDragOver = (e) => {
    e.preventDefault();
    setIsDragging(true);
  };

  const handleDragLeave = (e) => {
    e.preventDefault();
    setIsDragging(false);
  };

  const handleDrop = (e) => {
    e.preventDefault();
    setIsDragging(false);
    handleFileChange(e.dataTransfer.files[0]);
  };

  const handleUpload = async () => {
    if (!file) {
      alert('请选择要上传的文件');
      setCurrentStep(1);
      return;
    }
    if (!fieldMapping.studentNo || !fieldMapping.studentName || !fieldMapping.score) {
      alert('请先确认学号、姓名和成绩字段');
      setCurrentStep(2);
      return;
    }

    setUploading(true);
    setUploadProgress(15);
    setFeedback({
      type: 'progress',
      title: '正在导入',
      message: '系统正在上传并处理成绩文件，请稍候。'
    });

    const timer = window.setInterval(() => {
      setUploadProgress((prev) => (prev >= 90 ? prev : prev + 8));
    }, 200);

    const formData = new FormData();
    formData.append('file', file);
    formData.append('subjectType', selectedSubjectType);
    formData.append('studentNoColumn', fieldMapping.studentNo);
    formData.append('studentNameColumn', fieldMapping.studentName);
    formData.append('scoreColumn', fieldMapping.score);

    try {
      await uploadScoreFile(formData);
      setUploadProgress(100);
      setFeedback({
        type: 'success',
        title: '导入完成',
        message: `${file.name} 上传成功，历史记录已刷新。`
      });
      setFile(null);
      setCurrentStep(3);
      fetchImportHistory();
    } catch (error) {
      console.error('文件上传失败:', error);
      setFeedback({
        type: 'error',
        title: '上传失败',
        message: '文件上传失败，请检查文件格式或后端服务。'
      });
    } finally {
      window.clearInterval(timer);
      window.setTimeout(() => setUploadProgress(0), 500);
      setUploading(false);
    }
  };

  const handleDownloadTemplate = async () => {
    try {
      const response = await downloadTemplate();
      const url = window.URL.createObjectURL(new Blob([response]));
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', '成绩导入模板.xlsx');
      document.body.appendChild(link);
      link.click();
      link.remove();
    } catch (error) {
      console.error('下载模板失败:', error);
      alert('下载模板失败');
    }
  };

  const handleDelete = async (id) => {
    if (!await confirmAction({
      title: '删除导入记录？',
      message: '这条导入记录将被删除，请确认后继续。',
      confirmText: '删除记录',
      tone: 'danger'
    })) return;
    try {
      await deleteImportRecord(id);
      setFeedback({
        type: 'success',
        title: '删除成功',
        message: '导入记录已删除。'
      });
      fetchImportHistory();
    } catch (error) {
      console.error('删除失败:', error);
      alert('删除失败');
    }
  };

  const handleBatchDelete = async () => {
    if (selectedRecords.length === 0) {
      alert('请选择要删除的记录');
      return;
    }
    if (!await confirmAction({
      title: `删除 ${selectedRecords.length} 条记录？`,
      message: '选中的导入记录将被批量删除。',
      confirmText: '批量删除',
      tone: 'danger'
    })) return;

    try {
      await batchDeleteImportRecords(selectedRecords);
      setSelectedRecords([]);
      setFeedback({
        type: 'success',
        title: '批量删除成功',
        message: `已删除 ${selectedRecords.length} 条记录。`
      });
      fetchImportHistory();
    } catch (error) {
      console.error('批量删除失败:', error);
      alert('批量删除失败');
    }
  };

  const handleSelectAll = (e) => {
    if (e.target.checked) {
      setSelectedRecords(importHistory.map((record) => record.id));
    } else {
      setSelectedRecords([]);
    }
  };

  const handleSelectRecord = (id) => {
    if (selectedRecords.includes(id)) {
      setSelectedRecords(selectedRecords.filter((recordId) => recordId !== id));
    } else {
      setSelectedRecords([...selectedRecords, id]);
    }
  };

  const getStatusText = (status) => {
    const statusMap = {
      SUCCESS: '成功',
      FAILED: '失败',
      PROCESSING: '处理中'
    };
    return statusMap[status] || status;
  };

  const summaryCards = useMemo(() => {
    const total = importHistory.length;
    const success = importHistory.filter((item) => item.status === 'SUCCESS').length;
    const processing = importHistory.filter((item) => item.status === 'PROCESSING').length;
    return [
      {
        key: 'subject',
        title: '导入范围',
        value: selectedSubject.label,
        note: selectedSubject.note,
        icon: 'scores',
        accent: 'blue'
      },
      {
        key: 'file',
        title: '文件',
        value: file?.name || '未选择',
        note: file ? `${(file.size / 1024).toFixed(2)} KB` : '请上传文件',
        icon: 'import',
        accent: 'purple'
      },
      {
        key: 'success',
        title: '成功记录',
        value: `${success}`,
        note: `累计 ${total} 条历史记录`,
        icon: 'review',
        accent: 'green'
      },
      {
        key: 'progress',
        title: '处理中',
        value: `${processing}`,
        note: '等待后端完成',
        icon: 'calendar',
        accent: 'orange'
      }
    ];
  }, [file, importHistory, selectedSubject]);

  const renderStepContent = () => {
    if (currentStep === 0) {
      return (
        <section className="score-import-panel">
          <div className="panel-title">选择导入科目范围</div>
          <div className="subject-choice-grid">
            {subjectOptions.map((item) => (
              <button
                key={item.value}
                type="button"
                className={`subject-choice ${selectedSubjectType === item.value ? 'selected' : ''}`}
                onClick={() => setSelectedSubjectType(item.value)}
              >
                <span className="subject-choice-check">{selectedSubjectType === item.value ? '✓' : ''}</span>
                <strong>{item.label}</strong>
                <small>{item.note}</small>
              </button>
            ))}
          </div>
          <div className="step-actions">
            <button type="button" className="secondary-btn" onClick={handleDownloadTemplate}>下载模板</button>
            <button type="button" className="primary-btn" onClick={() => setCurrentStep(1)}>下一步：上传文件</button>
          </div>
        </section>
      );
    }

    if (currentStep === 1) {
      return (
        <section className="score-import-panel">
          <div className="panel-title with-actions">
            <span>上传文件与字段识别</span>
            <button type="button" className="secondary-btn" onClick={handleDownloadTemplate}>下载模板</button>
          </div>
          <div
            className={`dropzone ${isDragging ? 'drag-over' : ''} ${file ? 'has-file' : ''}`}
            onDragOver={handleDragOver}
            onDragLeave={handleDragLeave}
            onDrop={handleDrop}
            onClick={() => document.getElementById('scoreImportFileInput').click()}
          >
            <input
              id="scoreImportFileInput"
              type="file"
              className="hidden-input"
              accept=".xlsx,.xls,.csv"
              onChange={handleInputChange}
            />
            {file ? (
              <div className="chosen-file">
                <div className="chosen-file-copy">
                  <strong>{file.name}</strong>
                  <span>{(file.size / 1024).toFixed(2)} KB</span>
                </div>
                <button
                  type="button"
                  className="ghost-icon-btn"
                  onClick={(e) => {
                    e.stopPropagation();
                    setFile(null);
                  }}
                >
                  x
                </button>
              </div>
            ) : (
              <>
                <span className="dropzone-icon">↑</span>
                <strong>点击或拖拽文件到此处上传</strong>
                <span>支持 .xlsx、.xls、.csv 格式</span>
              </>
            )}
          </div>
          <div className="step-actions">
            <button type="button" className="secondary-btn" onClick={() => setCurrentStep(0)}>上一步</button>
            <button type="button" className="primary-btn" onClick={() => setCurrentStep(2)} disabled={!file}>下一步：基础映射</button>
          </div>
        </section>
      );
    }

    if (currentStep === 2) {
      return (
        <section className="score-import-panel">
          <div className="panel-title">基础映射</div>
          <div className="mapping-form-grid">
            <label>
              <span>学号字段</span>
              <input
                value={fieldMapping.studentNo}
                onChange={(e) => setFieldMapping((prev) => ({ ...prev, studentNo: e.target.value }))}
                placeholder="例如：学号"
              />
            </label>
            <label>
              <span>姓名字段</span>
              <input
                value={fieldMapping.studentName}
                onChange={(e) => setFieldMapping((prev) => ({ ...prev, studentName: e.target.value }))}
                placeholder="例如：姓名"
              />
            </label>
            <label>
              <span>成绩字段</span>
              <input
                value={fieldMapping.score}
                onChange={(e) => setFieldMapping((prev) => ({ ...prev, score: e.target.value }))}
                placeholder="例如：成绩"
              />
            </label>
          </div>
          <div className="mapping-preview">
            <span>当前文件：{file?.name || '未选择'}</span>
            <span>导入范围：{selectedSubject.label}</span>
            <span>字段：{fieldMapping.studentNo || '-'} / {fieldMapping.studentName || '-'} / {fieldMapping.score || '-'}</span>
          </div>
          <div className="step-actions">
            <button type="button" className="secondary-btn" onClick={() => setCurrentStep(1)}>上一步</button>
            <button
              type="button"
              className="primary-btn"
              onClick={() => setCurrentStep(3)}
              disabled={!fieldMapping.studentNo || !fieldMapping.studentName || !fieldMapping.score}
            >
              下一步：校验并导入
            </button>
          </div>
        </section>
      );
    }

    return (
      <>
        <section className="score-import-panel">
          <div className="panel-title">校验并导入</div>
          <div className="confirm-list">
            <div><span>导入范围</span><strong>{selectedSubject.label}</strong></div>
            <div><span>成绩文件</span><strong>{file?.name || '未选择文件'}</strong></div>
            <div><span>字段映射</span><strong>{fieldMapping.studentNo} / {fieldMapping.studentName} / {fieldMapping.score}</strong></div>
          </div>
          {uploadProgress > 0 && (
            <div className="progress-block">
              <div className="progress-meta">
                <span>{uploading ? '处理中' : '已完成'}</span>
                <strong>{uploadProgress}%</strong>
              </div>
              <div className="progress-track">
                <div className="progress-fill" style={{ width: `${uploadProgress}%` }} />
              </div>
            </div>
          )}
          <div className="step-actions">
            <button type="button" className="secondary-btn" onClick={() => setCurrentStep(2)} disabled={uploading}>上一步</button>
            <button type="button" className="primary-btn" onClick={handleUpload} disabled={!file || uploading}>
              {uploading ? '导入中...' : '开始导入'}
            </button>
          </div>
        </section>
        {renderHistoryPanel()}
      </>
    );
  };

  const renderHistoryPanel = () => (
    <section className="score-import-panel">
      <div className="panel-title with-actions">
        <span>导入历史记录</span>
        {selectedRecords.length > 0 && (
          <button type="button" className="danger-btn" onClick={handleBatchDelete}>批量删除 ({selectedRecords.length})</button>
        )}
      </div>

      {loading ? (
        <div className="empty-inline">加载中...</div>
      ) : importHistory.length === 0 ? (
        <div className="empty-inline">暂无导入记录</div>
      ) : (
        <div className="detail-table-wrap">
          <table className="detail-table">
            <thead>
              <tr>
                <th>
                  <input
                    type="checkbox"
                    checked={selectedRecords.length === importHistory.length && importHistory.length > 0}
                    onChange={handleSelectAll}
                  />
                </th>
                <th>序号</th>
                <th>文件名</th>
                <th>导入时间</th>
                <th>导入人数</th>
                <th>成功人数</th>
                <th>失败人数</th>
                <th>状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {importHistory.map((record, index) => (
                <tr key={record.id}>
                  <td>
                    <input
                      type="checkbox"
                      checked={selectedRecords.includes(record.id)}
                      onChange={() => handleSelectRecord(record.id)}
                    />
                  </td>
                  <td>{index + 1}</td>
                  <td>{record.fileName}</td>
                  <td>{new Date(record.importTime).toLocaleString()}</td>
                  <td>{record.totalCount || 0}</td>
                  <td>{record.successCount || 0}</td>
                  <td>{record.failedCount || 0}</td>
                  <td><span className={`status-tag ${String(record.status || '').toLowerCase()}`}>{getStatusText(record.status)}</span></td>
                  <td><button type="button" className="danger-link" onClick={() => handleDelete(record.id)}>删除</button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );

  return (
    <div className="score-import-workbench">
      <PageHeader title="成绩导入" />
      <div className="score-import-headline">
        <div>
          <h2>成绩导入</h2>
          <p>按导入流程逐步完成科目选择、文件上传、字段映射和最终导入。</p>
        </div>
      </div>

      <nav className="score-import-steps" aria-label="成绩导入流程">
        {importSteps.map((step, index) => {
          const status = index < currentStep ? 'finish' : index === currentStep ? 'process' : 'wait';
          return (
            <button
              key={step.key}
              type="button"
              className={`score-import-step ${status}`}
              onClick={() => goToStep(index)}
              disabled={!canEnterStep(index)}
            >
              <span className="score-import-step-dot">{status === 'finish' ? '✓' : index + 1}</span>
              <span className="score-import-step-copy">
                <strong>{step.title}</strong>
                <small>{step.description}</small>
              </span>
            </button>
          );
        })}
      </nav>

      {feedback && (
        <div className={`score-import-alert ${feedback.type}`}>
          <div className="score-import-alert-copy">
            <span className="score-import-alert-icon">{feedback.type === 'error' ? '!' : '✓'}</span>
            <div>
              <strong>{feedback.title}</strong>
              <p>{feedback.message}</p>
            </div>
          </div>
          <div className="score-import-alert-actions">
            <button type="button" className="secondary-btn" onClick={handleDownloadTemplate}>下载模板</button>
            <button type="button" className="primary-btn" onClick={() => setCurrentStep(3)} disabled={!file || uploading}>查看确认</button>
          </div>
        </div>
      )}

      <section className="score-import-summary-grid">
        {summaryCards.map((item) => (
          <article key={item.key} className={`score-import-summary-card accent-${item.accent}`}>
            <span className="summary-icon"><AppIcon name={item.icon} size={22} /></span>
            <div className="summary-copy">
              <span>{item.title}</span>
              <strong>{item.value}</strong>
              <small>{item.note}</small>
            </div>
          </article>
        ))}
      </section>

      {renderStepContent()}
    </div>
  );
}

export default ScoreImport;
