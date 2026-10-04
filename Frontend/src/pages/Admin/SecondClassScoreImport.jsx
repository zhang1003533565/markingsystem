import { useEffect, useMemo, useRef, useState } from 'react';
import { getActiveAcademicYearList, getCurrentAcademicYear } from '../../api/admin/academicYear';
import { getSecondClassScoreRules, updateSecondClassScoreRules } from '../../api/admin/secondClassScoreRule';
import {
  getImportedSecondClassScores,
  previewSecondClassScoreFile,
  importSecondClassScores,
  updateImportedSecondClassScore
} from '../../api/admin/secondClassScoreImport';
import uploadFileIcon from '../../assets/index/wenjian.png';
import './SecondClassScoreImport.css';

const DEFAULT_COLUMNS = {
  studentNoColumn: '学号',
  nameColumn: '姓名',
  classNameColumn: '班级',
  scoreColumn: '积分总和'
};

const PAGE_SIZE_OPTIONS = [10, 20, 50];

const STATUS_META = {
  created: { label: '成功', tone: 'success' },
  updated: { label: '成功', tone: 'success' },
  skipped: { label: '跳过', tone: 'warning' },
  failed: { label: '失败', tone: 'danger' }
};

function SecondClassScoreImport() {
  const [activeTab, setActiveTab] = useState('import');
  const [rules, setRules] = useState([]);
  const [saving, setSaving] = useState(false);
  const [academicYears, setAcademicYears] = useState([]);
  const [currentAcademicYearName, setCurrentAcademicYearName] = useState('');
  const [selectedAcademicYear, setSelectedAcademicYear] = useState('');
  const [file, setFile] = useState(null);
  const [preview, setPreview] = useState(null);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [importing, setImporting] = useState(false);
  const [importProgress, setImportProgress] = useState(0);
  const [importResult, setImportResult] = useState(null);
  const [importedRows, setImportedRows] = useState([]);
  const [importedTotalElements, setImportedTotalElements] = useState(0);
  const [importedPage, setImportedPage] = useState(1);
  const [importedPageSize, setImportedPageSize] = useState(10);
  const [importedLoading, setImportedLoading] = useState(false);
  const [importedSearchInput, setImportedSearchInput] = useState('');
  const [importedKeyword, setImportedKeyword] = useState('');
  const [editingRecordId, setEditingRecordId] = useState(null);
  const [editingSecondClassScore, setEditingSecondClassScore] = useState('');
  const [savingRecordId, setSavingRecordId] = useState(null);
  const [recordUpdateMessage, setRecordUpdateMessage] = useState('');
  const [detailFilter, setDetailFilter] = useState('all');
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [isDragging, setIsDragging] = useState(false);
  const [isImportedRecordsOpen, setIsImportedRecordsOpen] = useState(false);
  const [columnMapping, setColumnMapping] = useState(DEFAULT_COLUMNS);
  const fileInputRef = useRef(null);

  useEffect(() => {
    fetchRules();
    fetchAcademicYears();
  }, []);

  useEffect(() => {
    if (importedPage !== 1) {
      setImportedPage(1);
      return;
    }
    fetchImportedScores(selectedAcademicYear, 1, importedPageSize);
  }, [selectedAcademicYear]);

  useEffect(() => {
    fetchImportedScores(selectedAcademicYear, importedPage, importedPageSize);
  }, [importedPage, importedPageSize, importedKeyword]);

  const fetchRules = async () => {
    try {
      const response = await getSecondClassScoreRules();
      const nextRules = Array.isArray(response?.data) ? response.data : [];
      setRules(
        nextRules.map((item) => ({
          ...item,
          operator: resolveOperator(item),
          thresholdValue: resolveThresholdValue(item)
        }))
      );
    } catch (error) {
      console.error('获取二课分换算规则失败:', error);
      setRules([]);
    }
  };

  const fetchAcademicYears = async () => {
    try {
      const [academicYearData, currentYearData] = await Promise.all([
        getActiveAcademicYearList(),
        getCurrentAcademicYear().catch(() => null)
      ]);
      const yearList = Array.isArray(academicYearData) ? academicYearData : academicYearData?.data || [];
      const currentYear = currentYearData?.data || currentYearData;
      const currentYearName = currentYear?.name || '';
      setAcademicYears(yearList);
      setCurrentAcademicYearName(currentYearName);
      setSelectedAcademicYear(currentYearName || yearList[0]?.name || '');
    } catch (error) {
      console.error('获取学年列表失败:', error);
      setAcademicYears([]);
      setCurrentAcademicYearName('');
    }
  };

  const fetchImportedScores = async (
    academicYear = selectedAcademicYear,
    page = importedPage,
    size = importedPageSize,
    keyword = importedKeyword
  ) => {
    if (!academicYear) {
      setImportedRows([]);
      setImportedTotalElements(0);
      return;
    }
    setImportedLoading(true);
    try {
      const response = await getImportedSecondClassScores({
        academicYear,
        page: page - 1,
        size,
        keyword: keyword || undefined
      });
      const pageData = response?.data;
      if (Array.isArray(pageData)) {
        setImportedRows(pageData);
        setImportedTotalElements(pageData.length);
      } else {
        setImportedRows(Array.isArray(pageData?.content) ? pageData.content : []);
        setImportedTotalElements(Number(pageData?.totalElements || 0));
        const totalPageCount = Number(pageData?.totalPages || 0);
        if (totalPageCount > 0 && page > totalPageCount) {
          setImportedPage(totalPageCount);
        }
      }
    } catch (error) {
      console.error('获取已入库二课成绩失败:', error);
      setImportedRows([]);
      setImportedTotalElements(0);
    } finally {
      setImportedLoading(false);
    }
  };

  const resolveOperator = (rule) => {
    const min = Number(rule?.minScore);
    const max = rule?.maxScore == null || rule?.maxScore === '' ? null : Number(rule.maxScore);
    if (Number.isFinite(min) && max == null) {
      return '>=';
    }
    return '=';
  };

  const resolveThresholdValue = (rule) => {
    const min = Number(rule?.minScore);
    if (!Number.isFinite(min)) {
      return 0;
    }
    return min;
  };

  const trimNumber = (value) => {
    if (!Number.isFinite(value)) {
      return '0';
    }
    if (Math.abs(value - Math.round(value)) < 0.000001) {
      return String(Math.round(value));
    }
    return String(value);
  };

  const buildRuleFromOperator = (operator, thresholdValue) => {
    const safeValue = Number(thresholdValue);
    const numericValue = Number.isFinite(safeValue) ? safeValue : 0;
    if (operator === '>=') {
      return {
        label: `>=${trimNumber(numericValue)}`,
        minScore: numericValue,
        maxScore: null
      };
    }
    return {
      label: `=${trimNumber(numericValue)}`,
      minScore: numericValue,
      maxScore: numericValue
    };
  };

  const formatFileSize = (size) => {
    if (!size) return '0 B';
    const units = ['B', 'KB', 'MB', 'GB'];
    let nextSize = size;
    let unitIndex = 0;
    while (nextSize >= 1024 && unitIndex < units.length - 1) {
      nextSize /= 1024;
      unitIndex += 1;
    }
    return `${nextSize.toFixed(nextSize >= 10 || unitIndex === 0 ? 0 : 1)} ${units[unitIndex]}`;
  };

  const previewHeaders = preview?.headers || [];
  const detailRows = useMemo(
    () => (Array.isArray(importResult?.importDetails) ? importResult.importDetails : []),
    [importResult]
  );
  const previewRows = useMemo(() => (Array.isArray(preview?.previewRows) ? preview.previewRows : []), [preview]);

  const mappedPreviewRows = useMemo(
    () =>
      previewRows.map((row, index) => ({
        id: `preview-row-${index}`,
        studentNo: row?.[columnMapping.studentNoColumn] ?? '',
        studentName: row?.[columnMapping.nameColumn] ?? '',
        className: row?.[columnMapping.classNameColumn] ?? '',
        score: row?.[columnMapping.scoreColumn] ?? ''
      })),
    [columnMapping, previewRows]
  );

  const filteredDetailRows = useMemo(
    () =>
      detailRows.filter((item) => {
        if (detailFilter === 'success') {
          return item.status === 'created' || item.status === 'updated';
        }
        if (detailFilter === 'failed') {
          return item.status === 'failed' || item.status === 'skipped';
        }
        return true;
      }),
    [detailFilter, detailRows]
  );

  const failedDetailCount = useMemo(
    () => detailRows.filter((item) => item.status === 'failed' || item.status === 'skipped').length,
    [detailRows]
  );

  const totalPages = Math.max(1, Math.ceil(filteredDetailRows.length / pageSize));
  const importedTotalPages = Math.max(1, Math.ceil(importedTotalElements / importedPageSize));

  const pagedDetailRows = useMemo(() => {
    const start = (currentPage - 1) * pageSize;
    return filteredDetailRows.slice(start, start + pageSize);
  }, [currentPage, filteredDetailRows, pageSize]);

  useEffect(() => {
    setCurrentPage(1);
  }, [detailFilter, pageSize, importResult]);

  const handleRuleChange = (index, field, value) => {
    setRules((prev) =>
      prev.map((item, itemIndex) => {
        if (itemIndex !== index) return item;
        if (field === 'operator') {
          return { ...item, operator: value };
        }
        return {
          ...item,
          [field]: value === '' ? '' : Number(value)
        };
      })
    );
  };

  const handleAddRule = () => {
    setRules((prev) => [
      {
        id: `new-${Date.now()}`,
        operator: '>=',
        thresholdValue: 0,
        moralScore: 0,
        sortOrder: 1
      },
      ...prev.map((item, index) => ({
        ...item,
        sortOrder: index + 2
      }))
    ]);
  };

  const handleDeleteRule = (index) => {
    setRules((prev) =>
      prev
        .filter((_, itemIndex) => itemIndex !== index)
        .map((item, itemIndex) => ({
          ...item,
          sortOrder: itemIndex + 1
        }))
    );
  };

  const handleSaveRules = async () => {
    setSaving(true);
    try {
      await updateSecondClassScoreRules(
        rules.map((item, index) => ({
          ...buildRuleFromOperator(item.operator, item.thresholdValue),
          moralScore: item.moralScore === '' ? null : Number(item.moralScore),
          sortOrder: index + 1
        }))
      );
      await fetchRules();
    } catch (error) {
      console.error('保存二课分换算规则失败:', error);
      alert('保存规则失败，请稍后重试');
    } finally {
      setSaving(false);
    }
  };

  const applyPreviewResponse = (nextPreview) => {
    if (nextPreview?.errorMessage) {
      alert(nextPreview.errorMessage);
      setPreview(null);
      return;
    }

    setPreview(nextPreview || null);
    setColumnMapping({
      studentNoColumn: nextPreview?.defaultColumns?.studentNoColumn || DEFAULT_COLUMNS.studentNoColumn,
      nameColumn: nextPreview?.defaultColumns?.nameColumn || DEFAULT_COLUMNS.nameColumn,
      classNameColumn: nextPreview?.defaultColumns?.classNameColumn || DEFAULT_COLUMNS.classNameColumn,
      scoreColumn: nextPreview?.defaultColumns?.scoreColumn || DEFAULT_COLUMNS.scoreColumn
    });
  };

  const previewFile = async (selectedFile) => {
    if (!selectedFile) return;

    setFile(selectedFile);
    setImportResult(null);
    setDetailFilter('all');
    setCurrentPage(1);
    setImportProgress(0);
    setPreviewLoading(true);

    try {
      const formData = new FormData();
      formData.append('file', selectedFile);
      const response = await previewSecondClassScoreFile(formData);
      applyPreviewResponse(response || null);
    } catch (error) {
      console.error('预览二课分文件失败:', error);
      setPreview(null);
      alert('文件预览失败，请检查模板格式后重试');
    } finally {
      setPreviewLoading(false);
    }
  };

  const handleFileSelect = async (event) => {
    const selectedFile = event.target.files?.[0];
    if (!selectedFile) return;
    await previewFile(selectedFile);
  };

  const handleDragOver = (event) => {
    event.preventDefault();
    setIsDragging(true);
  };

  const handleDragLeave = (event) => {
    event.preventDefault();
    setIsDragging(false);
  };

  const handleDrop = async (event) => {
    event.preventDefault();
    setIsDragging(false);
    const droppedFile = event.dataTransfer.files?.[0];
    if (droppedFile) {
      await previewFile(droppedFile);
    }
  };

  const handleImport = async () => {
    if (!file) {
      alert('请先上传二课分文件');
      return;
    }
    if (!selectedAcademicYear) {
      alert('请选择学年');
      return;
    }
    if (!columnMapping.studentNoColumn || !columnMapping.scoreColumn) {
      alert('请先确认学号列和分数字段列');
      return;
    }

    setImporting(true);
    setImportProgress(0);
    setImportResult(null);
    setDetailFilter('all');
    setCurrentPage(1);

    try {
      const formData = new FormData();
      formData.append('file', file);
      formData.append('academicYear', selectedAcademicYear);
      formData.append('studentNoColumn', columnMapping.studentNoColumn);
      formData.append('nameColumn', columnMapping.nameColumn || DEFAULT_COLUMNS.nameColumn);
      formData.append('classNameColumn', columnMapping.classNameColumn || DEFAULT_COLUMNS.classNameColumn);
      formData.append('scoreColumn', columnMapping.scoreColumn);
      const response = await importSecondClassScores(formData, (event) => {
        if (!event?.total) return;
        setImportProgress(Math.min(100, Math.round((event.loaded / event.total) * 100)));
      });
      if (response?.errorMessage) {
        alert(response.errorMessage);
        setImportProgress(0);
        return;
      }
      setImportResult(response || null);
      setImportProgress(100);
      if (importedPage === 1) {
        await fetchImportedScores(selectedAcademicYear, 1, importedPageSize);
      } else {
        setImportedPage(1);
      }
    } catch (error) {
      console.error('导入二课分失败:', error);
      setImportResult(null);
      setImportProgress(0);
      alert('导入失败，请稍后重试');
    } finally {
      setImporting(false);
    }
  };

  const handleExportDetails = () => {
    if (!filteredDetailRows.length) {
      alert('暂无可导出的导入明细');
      return;
    }

    const headers = ['学号', '姓名', '原始二课分', '换算后的德育分', '处理结果', '说明信息'];
    const rows = filteredDetailRows.map((item) => {
      const meta = STATUS_META[item.status] || STATUS_META.failed;
      return [
        item.studentNo || '',
        item.studentName || '',
        item.secondClassScore ?? '',
        item.moralScore ?? '',
        meta.label,
        item.message || ''
      ];
    });

    const csvContent = [headers, ...rows]
      .map((row) => row.map((cell) => `"${String(cell ?? '').replace(/"/g, '""')}"`).join(','))
      .join('\n');

    const blob = new Blob([`\uFEFF${csvContent}`], { type: 'text/csv;charset=utf-8;' });
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `二课分导入明细-${selectedAcademicYear || '未选择学年'}.csv`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    window.URL.revokeObjectURL(url);
  };

  const handleImportedSearch = () => {
    setRecordUpdateMessage('');
    setImportedKeyword(importedSearchInput.trim());
    setImportedPage(1);
  };

  const handleStartRecordEdit = (item) => {
    setRecordUpdateMessage('');
    setEditingRecordId(item.id);
    setEditingSecondClassScore(item.secondClassScore == null ? '' : String(item.secondClassScore));
  };

  const handleCancelRecordEdit = () => {
    setEditingRecordId(null);
    setEditingSecondClassScore('');
  };

  const handleSaveRecord = async (item) => {
    const nextScore = Number(editingSecondClassScore);
    if (editingSecondClassScore === '' || !Number.isFinite(nextScore) || nextScore < 0) {
      alert('请输入大于等于 0 的有效二课分');
      return;
    }

    setSavingRecordId(item.id);
    setRecordUpdateMessage('');
    try {
      const response = await updateImportedSecondClassScore(item.id, { secondClassScore: nextScore });
      const updatedRecord = response?.data;
      if (updatedRecord?.id) {
        setImportedRows((prev) => prev.map((row) => (row.id === updatedRecord.id ? updatedRecord : row)));
      } else {
        await fetchImportedScores(selectedAcademicYear, importedPage, importedPageSize);
      }
      setEditingRecordId(null);
      setEditingSecondClassScore('');
      setRecordUpdateMessage(`${item.studentName || item.studentNo} 的二课成绩已更新`);
    } catch (error) {
      console.error('修改单个学生二课成绩失败:', error);
      alert(error?.response?.data?.message || '修改失败，请稍后重试');
    } finally {
      setSavingRecordId(null);
    }
  };

  const formatScore = (value) => (value == null ? '-' : Number(value).toFixed(2));
  const formatDateTime = (value) => {
    if (!value) {
      return '-';
    }
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
      return String(value);
    }
    return date.toLocaleString('zh-CN', { hour12: false });
  };

  const renderPageNumbers = () => {
    const pages = [];
    const start = Math.max(1, currentPage - 2);
    const end = Math.min(totalPages, start + 4);
    for (let page = start; page <= end; page += 1) {
      pages.push(page);
    }
    return pages;
  };

  const renderImportedPageNumbers = () => {
    const pages = [];
    const start = Math.max(1, importedPage - 2);
    const end = Math.min(importedTotalPages, start + 4);
    for (let page = start; page <= end; page += 1) {
      pages.push(page);
    }
    return pages;
  };

  const renderRuleCard = () => (
    <section className="second-class-card rule-card rule-tab-card">
      <div className="rule-card-head">
        <div>
          <div className="second-class-section-title">二课分换算规则</div>
          <div className="detail-view-note">把导入的真实二课分，转换为最终计入综合测评的德育基础分。</div>
        </div>
        <div className="rule-card-actions">
          <button type="button" className="second-class-btn secondary compact" onClick={handleAddRule}>
            ＋ 新增规则
          </button>
          <button
            type="button"
            className="second-class-btn primary compact"
            onClick={handleSaveRules}
            disabled={saving}
          >
            {saving ? '保存中...' : '保存全部'}
          </button>
        </div>
      </div>

      <div className="rule-flow-guide" aria-label="换算流程">
        <div className="rule-flow-node">
          <span>1</span>
          <div>
            <strong>读取二课分</strong>
            <small>导入文件中的原始积分</small>
          </div>
        </div>
        <span className="rule-flow-separator" aria-hidden="true">›</span>
        <div className="rule-flow-node active">
          <span>2</span>
          <div>
            <strong>匹配条件</strong>
            <small>按下方编号依次判断</small>
          </div>
        </div>
        <span className="rule-flow-separator" aria-hidden="true">›</span>
        <div className="rule-flow-node">
          <span>3</span>
          <div>
            <strong>写入德育分</strong>
            <small>作为德育基础分参与计算</small>
          </div>
        </div>
      </div>

      <div className="rule-list-heading">
        <div>
          <strong>条件规则</strong>
          <span>{rules.length} 条</span>
        </div>
        <small>按编号顺序匹配，满足条件的第一条生效</small>
      </div>

      <div className="rule-card-grid">
        {rules.map((item, index) => (
          <article className="rule-item-card" key={item.id || `${item.minScore}-${index}`}>
            <div className="rule-item-index">
              <span>规则</span>
              <strong>{String(index + 1).padStart(2, '0')}</strong>
            </div>

            <div className="rule-expression">
              <div className="rule-expression-side">
                <span className="rule-expression-prefix">当</span>
                <span className="rule-operand-chip">二课分</span>
                <select
                  className="rule-operator-select"
                  aria-label={`规则 ${index + 1} 的比较方式`}
                  value={item.operator || '='}
                  onChange={(event) => handleRuleChange(index, 'operator', event.target.value)}
                >
                  <option value="=">等于（=）</option>
                  <option value=">=">大于等于（≥）</option>
                </select>
                <label className="rule-number-field">
                  <input
                    type="number"
                    step="0.1"
                    aria-label={`规则 ${index + 1} 的二课分阈值`}
                    value={item.thresholdValue ?? ''}
                    onChange={(event) => handleRuleChange(index, 'thresholdValue', event.target.value)}
                  />
                  <span>分</span>
                </label>
              </div>

              <span className="rule-expression-arrow" aria-hidden="true">→</span>

              <div className="rule-expression-side result">
                <span className="rule-expression-prefix">则</span>
                <span className="rule-operand-chip result">德育基础分</span>
                <label className="rule-number-field result">
                  <input
                    type="number"
                    step="0.1"
                    aria-label={`规则 ${index + 1} 的德育基础分`}
                    value={item.moralScore ?? ''}
                    onChange={(event) => handleRuleChange(index, 'moralScore', event.target.value)}
                  />
                  <span>分</span>
                </label>
              </div>
            </div>

            <button
              type="button"
              className="rule-delete-btn"
              aria-label={`删除规则 ${index + 1}`}
              onClick={() => handleDeleteRule(index)}
            >
              删除
            </button>
          </article>
        ))}
        {!rules.length ? <div className="rule-card-empty">还没有换算规则，请点击右上角“新增规则”开始配置。</div> : null}
      </div>

      <div className="rule-note">
        <span aria-hidden="true">i</span>
        精确档位请选择“等于”；最高档或封顶规则可使用“大于等于”。保存后，新导入和单人修改都会使用这里的规则。
      </div>
    </section>
  );

  return (
    <div className="second-class-import-page">
      <div className="second-class-page-tools">
        <div className="second-class-tabs" aria-label="二课成绩功能切换">
          <button
            type="button"
            className={activeTab === 'import' ? 'active' : ''}
            onClick={() => setActiveTab('import')}
          >
            成绩导入
          </button>
          <button type="button" className={activeTab === 'rules' ? 'active' : ''} onClick={() => setActiveTab('rules')}>
            换算规则
          </button>
        </div>
        <span className="second-class-page-hint">
          {activeTab === 'import' ? '选择学年，上传文件，核对后一次导入' : '用可读条件定义二课分与德育基础分的换算关系'}
        </span>
      </div>

      {activeTab === 'import' && (
        <div className="second-class-main-column">
          <section className="second-class-card second-class-import-workbench">
            <div className="second-class-workbench-head">
              <div>
                <div className="second-class-section-title">导入准备</div>
                <div className="detail-view-note">系统会自动识别常用列，仅需检查学年和字段对应关系。</div>
              </div>
              <span className={`workbench-status ${preview ? 'ready' : ''}`}>
                {previewLoading ? '正在识别' : preview ? '可以导入' : '等待文件'}
              </span>
            </div>

            <div className="second-class-setup-row">
              <label className="second-class-setup-field" htmlFor="second-class-year-select">
                <span>导入学年</span>
                <select
                  id="second-class-year-select"
                  value={selectedAcademicYear}
                  onChange={(event) => setSelectedAcademicYear(event.target.value)}
                >
                  <option value="">请选择学年</option>
                  {academicYears.map((item) => (
                    <option key={item.id} value={item.name}>
                      {item.name}
                      {item.name === currentAcademicYearName ? '（当前）' : ''}
                    </option>
                  ))}
                </select>
              </label>

              <div
                className={`second-class-file-picker ${isDragging ? 'dragging' : ''} ${file ? 'has-file' : ''}`}
                onDragOver={handleDragOver}
                onDragLeave={handleDragLeave}
                onDrop={handleDrop}
              >
                <img src={uploadFileIcon} alt="" className="second-class-file-icon" />
                <div className="second-class-file-copy">
                  <strong>{file?.name || '上传二课成绩文件'}</strong>
                  <span>
                    {file
                      ? `${formatFileSize(file.size)} · ${preview ? `${preview.rowCount || 0} 条数据` : previewLoading ? '正在解析' : '等待识别'}`
                      : '拖放到这里，支持 .xlsx / .xls / .csv'}
                  </span>
                </div>
                <button
                  type="button"
                  className="second-class-btn secondary compact"
                  onClick={() => fileInputRef.current?.click()}
                >
                  {file ? '更换文件' : '选择文件'}
                </button>
                <input
                  ref={fileInputRef}
                  type="file"
                  accept=".xlsx,.xls,.csv"
                  className="second-class-hidden-input"
                  onChange={handleFileSelect}
                />
              </div>
            </div>

            {previewLoading ? (
              <div className="second-class-inline-state">
                <span className="inline-state-spinner" />
                正在读取文件结构和样例数据…
              </div>
            ) : preview ? (
              <div className="second-class-preview-workspace">
                <div className="second-class-mapping-panel">
                  <div className="compact-panel-head">
                    <strong>字段对应</strong>
                    <span>已自动识别</span>
                  </div>
                  <div className="preview-field-grid">
                    <label>
                      <span>学号</span>
                      <select
                        value={columnMapping.studentNoColumn}
                        onChange={(event) =>
                          setColumnMapping((prev) => ({
                            ...prev,
                            studentNoColumn: event.target.value
                          }))
                        }
                      >
                        {previewHeaders.map((header) => (
                          <option key={`studentNo-${header}`} value={header}>
                            {header}
                          </option>
                        ))}
                      </select>
                    </label>
                    <label>
                      <span>姓名</span>
                      <select
                        value={columnMapping.nameColumn}
                        onChange={(event) => setColumnMapping((prev) => ({ ...prev, nameColumn: event.target.value }))}
                      >
                        {previewHeaders.map((header) => (
                          <option key={`name-${header}`} value={header}>
                            {header}
                          </option>
                        ))}
                      </select>
                    </label>
                    <label>
                      <span>班级</span>
                      <select
                        value={columnMapping.classNameColumn}
                        onChange={(event) =>
                          setColumnMapping((prev) => ({
                            ...prev,
                            classNameColumn: event.target.value
                          }))
                        }
                      >
                        {previewHeaders.map((header) => (
                          <option key={`class-${header}`} value={header}>
                            {header}
                          </option>
                        ))}
                      </select>
                    </label>
                    <label>
                      <span>二课分</span>
                      <select
                        value={columnMapping.scoreColumn}
                        onChange={(event) => setColumnMapping((prev) => ({ ...prev, scoreColumn: event.target.value }))}
                      >
                        {previewHeaders.map((header) => (
                          <option key={`score-${header}`} value={header}>
                            {header}
                          </option>
                        ))}
                      </select>
                    </label>
                  </div>
                </div>

                <div className="second-class-sample-panel">
                  <div className="compact-panel-head">
                    <strong>数据样例</strong>
                    <span>
                      前 {Math.min(5, mappedPreviewRows.length)} 条 · 共 {preview.rowCount || 0} 条
                    </span>
                  </div>
                  <div className="preview-mini-table-wrap">
                    <table className="preview-mini-table">
                      <thead>
                        <tr>
                          <th>学号</th>
                          <th>姓名</th>
                          <th>班级</th>
                          <th>二课分</th>
                        </tr>
                      </thead>
                      <tbody>
                        {mappedPreviewRows.length ? (
                          mappedPreviewRows.slice(0, 5).map((row) => (
                            <tr key={row.id}>
                              <td>{row.studentNo || '-'}</td>
                              <td>{row.studentName || '-'}</td>
                              <td>{row.className || '-'}</td>
                              <td>{row.score || '-'}</td>
                            </tr>
                          ))
                        ) : (
                          <tr>
                            <td colSpan="4" className="detail-empty">
                              暂无预览数据
                            </td>
                          </tr>
                        )}
                      </tbody>
                    </table>
                  </div>
                </div>
              </div>
            ) : null}

            <div className="second-class-import-bar">
              <div>
                <strong>{preview ? `${preview.rowCount || 0} 条数据已就绪` : '上传文件后可核对数据'}</strong>
                <span>
                  {selectedAcademicYear || '尚未选择学年'} · 同学年同学号再次导入会覆盖原成绩
                </span>
              </div>
              {importing ? (
                <div className="import-progress-compact" aria-label={`导入进度 ${importProgress}%`}>
                  <span style={{ width: `${importProgress}%` }} />
                </div>
              ) : null}
              <button
                type="button"
                className="second-class-btn primary"
                onClick={handleImport}
                disabled={importing || !preview || !selectedAcademicYear}
              >
                {importing ? `正在导入 ${importProgress}%` : '确认导入'}
              </button>
            </div>
          </section>

          {importResult ? (
            <section className="second-class-card second-class-result-card">
              <div className="detail-panel-head">
                <div>
                  <div className="second-class-section-title">本次导入结果</div>
                  <div className="result-summary-line">
                    <span>共 {detailRows.length} 条</span>
                    <span className="success">新增 {Number(importResult.createdCount || 0)}</span>
                    <span className="success">覆盖 {Number(importResult.updatedCount || 0)}</span>
                    <span className={failedDetailCount ? 'danger' : ''}>失败 {failedDetailCount}</span>
                  </div>
                  <div className="detail-filter-tabs">
                    <button
                      type="button"
                      className={detailFilter === 'all' ? 'active' : ''}
                      onClick={() => setDetailFilter('all')}
                    >
                      全部
                    </button>
                    <button
                      type="button"
                      className={detailFilter === 'success' ? 'active' : ''}
                      onClick={() => setDetailFilter('success')}
                    >
                      成功
                    </button>
                    <button
                      type="button"
                      className={detailFilter === 'failed' ? 'active' : ''}
                      onClick={() => setDetailFilter('failed')}
                    >
                      失败
                    </button>
                  </div>
                </div>
                <button type="button" className="second-class-btn secondary compact" onClick={handleExportDetails}>
                  导出明细
                </button>
              </div>

              <div className="detail-table-wrap">
                <table className="detail-table">
                  <thead>
                    <tr>
                      <th>学号</th>
                      <th>姓名</th>
                      <th>原始二课分</th>
                      <th>换算德育分</th>
                      <th>结果</th>
                      <th>说明</th>
                    </tr>
                  </thead>
                  <tbody>
                    {pagedDetailRows.length ? (
                      pagedDetailRows.map((item, index) => {
                        const meta = STATUS_META[item.status] || STATUS_META.failed;
                        return (
                          <tr key={`${item.studentNo || 'row'}-${index}`}>
                            <td>{item.studentNo || '-'}</td>
                            <td>{item.studentName || '-'}</td>
                            <td>{formatScore(item.secondClassScore)}</td>
                            <td>{formatScore(item.moralScore)}</td>
                            <td>
                              <span className={`status-chip ${meta.tone}`}>{meta.label}</span>
                            </td>
                            <td>{item.message || '-'}</td>
                          </tr>
                        );
                      })
                    ) : (
                      <tr>
                        <td colSpan="6" className="detail-empty">
                          当前筛选下没有记录
                        </td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>

              <div className="detail-pagination">
                <div className="pagination-meta">
                  <span>共 {filteredDetailRows.length} 条</span>
                  <select value={pageSize} onChange={(event) => setPageSize(Number(event.target.value))}>
                    {PAGE_SIZE_OPTIONS.map((option) => (
                      <option key={option} value={option}>
                        {option}条/页
                      </option>
                    ))}
                  </select>
                </div>
                <div className="pagination-controls">
                  <button
                    type="button"
                    onClick={() => setCurrentPage((prev) => Math.max(1, prev - 1))}
                    disabled={currentPage === 1}
                  >
                    ‹
                  </button>
                  {renderPageNumbers().map((page) => (
                    <button
                      key={page}
                      type="button"
                      className={page === currentPage ? 'active' : ''}
                      onClick={() => setCurrentPage(page)}
                    >
                      {page}
                    </button>
                  ))}
                  <button
                    type="button"
                    onClick={() => setCurrentPage((prev) => Math.min(totalPages, prev + 1))}
                    disabled={currentPage === totalPages}
                  >
                    ›
                  </button>
                </div>
              </div>
            </section>
          ) : null}

          <section className={`second-class-card second-class-stored-card ${isImportedRecordsOpen ? 'open' : ''}`}>
            <div className="stored-card-summary">
              <div>
                <div className="second-class-section-title">已入库成绩</div>
                <div className="detail-view-note">
                  {selectedAcademicYear || '未选择学年'} · {importedTotalElements} 条记录
                </div>
              </div>
              <div className="detail-actions">
                {isImportedRecordsOpen ? (
                  <button
                    type="button"
                    className="second-class-btn secondary compact"
                    onClick={() => fetchImportedScores(selectedAcademicYear, importedPage, importedPageSize)}
                    disabled={importedLoading || !selectedAcademicYear}
                  >
                    {importedLoading ? '刷新中…' : '刷新'}
                  </button>
                ) : null}
                <button
                  type="button"
                  className="second-class-btn secondary compact"
                  onClick={() => setIsImportedRecordsOpen((prev) => !prev)}
                  aria-expanded={isImportedRecordsOpen}
                >
                  {isImportedRecordsOpen ? '收起记录' : '查看记录'}
                </button>
              </div>
            </div>

            {isImportedRecordsOpen ? (
              <div className="stored-card-content">
                <div className="stored-record-toolbar">
                  <div className="stored-record-search">
                    <input
                      type="search"
                      value={importedSearchInput}
                      placeholder="搜索学号、姓名或班级"
                      onChange={(event) => setImportedSearchInput(event.target.value)}
                      onKeyDown={(event) => {
                        if (event.key === 'Enter') {
                          handleImportedSearch();
                        }
                      }}
                    />
                    <button type="button" className="second-class-btn secondary compact" onClick={handleImportedSearch}>
                      搜索
                    </button>
                    {importedKeyword ? (
                      <button
                        type="button"
                        className="stored-search-clear"
                        onClick={() => {
                          setImportedSearchInput('');
                          setImportedKeyword('');
                          setImportedPage(1);
                        }}
                      >
                        清除
                      </button>
                    ) : null}
                  </div>
                  <span className="stored-record-tip">修改真实二课分后，德育基础分会按当前规则重新计算</span>
                </div>
                {recordUpdateMessage ? <div className="stored-record-notice">{recordUpdateMessage}</div> : null}
                <div className="detail-table-wrap">
                  <table className="detail-table imported-score-table">
                    <thead>
                      <tr>
                        <th>学号</th>
                        <th>姓名</th>
                        <th>班级</th>
                        <th>真实二课分</th>
                        <th>德育基础分</th>
                        <th>德育附加分</th>
                        <th>德育总分</th>
                        <th>更新时间</th>
                        <th>操作</th>
                      </tr>
                    </thead>
                    <tbody>
                      {importedLoading ? (
                        <tr>
                          <td colSpan="9" className="detail-empty">
                            正在加载已入库成绩…
                          </td>
                        </tr>
                      ) : importedRows.length ? (
                        importedRows.map((item) => (
                          <tr key={item.id || `${item.studentNo}-${item.academicYear}`}>
                            <td>{item.studentNo || '-'}</td>
                            <td>{item.studentName || '-'}</td>
                            <td>{item.className || '-'}</td>
                            <td>
                              {editingRecordId === item.id ? (
                                <input
                                  type="number"
                                  min="0"
                                  step="0.1"
                                  className="stored-score-input"
                                  value={editingSecondClassScore}
                                  onChange={(event) => setEditingSecondClassScore(event.target.value)}
                                />
                              ) : (
                                formatScore(item.secondClassScore)
                              )}
                            </td>
                            <td>{formatScore(item.moralScore)}</td>
                            <td>{formatScore(item.bonusScore)}</td>
                            <td>{formatScore(item.totalScore)}</td>
                            <td>{formatDateTime(item.updatedAt)}</td>
                            <td>
                              {editingRecordId === item.id ? (
                                <div className="stored-row-actions">
                                  <button
                                    type="button"
                                    className="stored-row-save"
                                    onClick={() => handleSaveRecord(item)}
                                    disabled={savingRecordId === item.id}
                                  >
                                    {savingRecordId === item.id ? '保存中' : '保存'}
                                  </button>
                                  <button
                                    type="button"
                                    className="stored-row-cancel"
                                    onClick={handleCancelRecordEdit}
                                    disabled={savingRecordId === item.id}
                                  >
                                    取消
                                  </button>
                                </div>
                              ) : (
                                <button
                                  type="button"
                                  className="stored-row-edit"
                                  onClick={() => handleStartRecordEdit(item)}
                                >
                                  修改
                                </button>
                              )}
                            </td>
                          </tr>
                        ))
                      ) : (
                        <tr>
                          <td colSpan="9" className="detail-empty">
                            {importedKeyword ? '没有找到匹配的学生' : '当前学年还没有已入库的二课成绩'}
                          </td>
                        </tr>
                      )}
                    </tbody>
                  </table>
                </div>

                <div className="detail-pagination">
                  <div className="pagination-meta">
                    <span>共 {importedTotalElements} 条</span>
                    <select
                      value={importedPageSize}
                      onChange={(event) => {
                        setImportedPageSize(Number(event.target.value));
                        setImportedPage(1);
                      }}
                    >
                      {PAGE_SIZE_OPTIONS.map((option) => (
                        <option key={option} value={option}>
                          {option}条/页
                        </option>
                      ))}
                    </select>
                  </div>
                  <div className="pagination-controls">
                    <button
                      type="button"
                      onClick={() => setImportedPage((prev) => Math.max(1, prev - 1))}
                      disabled={importedPage === 1 || importedLoading}
                    >
                      ‹
                    </button>
                    {renderImportedPageNumbers().map((page) => (
                      <button
                        key={page}
                        type="button"
                        className={page === importedPage ? 'active' : ''}
                        onClick={() => setImportedPage(page)}
                        disabled={importedLoading}
                      >
                        {page}
                      </button>
                    ))}
                    <button
                      type="button"
                      onClick={() => setImportedPage((prev) => Math.min(importedTotalPages, prev + 1))}
                      disabled={importedPage === importedTotalPages || importedLoading}
                    >
                      ›
                    </button>
                  </div>
                </div>
              </div>
            ) : null}
          </section>
        </div>
      )}

      {activeTab === 'rules' && <div className="second-class-rule-tab-wrap">{renderRuleCard()}</div>}
    </div>
  );
}

export default SecondClassScoreImport;
