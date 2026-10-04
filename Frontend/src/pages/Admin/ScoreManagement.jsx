import { useEffect, useMemo, useRef, useState } from 'react';
import './ScoreManagement.css';
import { getClassOptions } from '../../api/admin/class';
import { getActiveAcademicYearList, getCurrentAcademicYear } from '../../api/admin/academicYear';
import { importRainSubjectScores, previewRainSubjectScores } from '../../api/admin/score';
import AppIcon from '../../components/AppIcon';

const scoreImportSteps = [
  { key: 'upload', title: '上传文件与字段识别', description: '解析表头与科目' },
  { key: 'fields', title: '基础字段配置', description: '确认学年与学生字段' },
  { key: 'mapping', title: '科目与班级映射', description: '选择科目并映射班级' },
  { key: 'confirm', title: '校验并导入', description: '检查后写入系统' }
];

function ScoreManagement() {
  const [classes, setClasses] = useState([]);
  const [academicYears, setAcademicYears] = useState([]);
  const [defaultAcademicYear, setDefaultAcademicYear] = useState('');
  const [loading, setLoading] = useState(false);
  const [file, setFile] = useState(null);
  const [previewSubjects, setPreviewSubjects] = useState([]);
  const [previewHeaders, setPreviewHeaders] = useState([]);
  const [isPreviewVisible, setIsPreviewVisible] = useState(false);
  const [selectedPreviewSubjects, setSelectedPreviewSubjects] = useState([]);
  const [selectedAcademicYear, setSelectedAcademicYear] = useState('');
  const [accountColumn, setAccountColumn] = useState('学号');
  const [passwordColumn, setPasswordColumn] = useState('学号');
  const [nameColumn, setNameColumn] = useState('姓名');
  const [classColumn, setClassColumn] = useState('');
  const [classValuesByHeader, setClassValuesByHeader] = useState({});
  const [classMappings, setClassMappings] = useState([]);
  const [classMappingError, setClassMappingError] = useState('');
  const [importProgress, setImportProgress] = useState(0);
  const [operationFeedback, setOperationFeedback] = useState(null);
  const [isDragging, setIsDragging] = useState(false);
  const [isImportModalVisible, setIsImportModalVisible] = useState(false);
  const [currentStep, setCurrentStep] = useState(0);
  const progressTimerRef = useRef(null);
  const configPanelRef = useRef(null);
  const subjectPanelRef = useRef(null);
  const mappingTableBodyRef = useRef(null);
  const [mappingTableBodyMaxHeight, setMappingTableBodyMaxHeight] = useState(null);

  const selectedSemester = selectedAcademicYear || '';
  const selectedHeaderSet = useMemo(() => new Set(selectedPreviewSubjects), [selectedPreviewSubjects]);
  const mappedClassCount = useMemo(
    () => classMappings.filter((item) => item.systemClassName).length,
    [classMappings]
  );
  const duplicateMappedClasses = useMemo(() => {
    const counts = classMappings.reduce((acc, item) => {
      if (!item.systemClassName) return acc;
      acc[item.systemClassName] = (acc[item.systemClassName] || 0) + 1;
      return acc;
    }, {});
    return Object.keys(counts).filter((className) => counts[className] > 1);
  }, [classMappings]);
  const isFieldConfigReady = Boolean(isPreviewVisible && selectedSemester && accountColumn && passwordColumn && nameColumn && classColumn);
  const isClassMappingReady = Boolean(classMappings.length && mappedClassCount === classMappings.length && !duplicateMappedClasses.length);
  const isImportReady = Boolean(isFieldConfigReady && selectedPreviewSubjects.length > 0 && isClassMappingReady);

  const canEnterStep = (stepIndex) => {
    if (stepIndex === 0) return true;
    if (stepIndex === 1) return isPreviewVisible;
    if (stepIndex === 2) return isFieldConfigReady;
    return isImportReady;
  };

  const goToStep = (stepIndex) => {
    if (canEnterStep(stepIndex)) {
      setCurrentStep(stepIndex);
    }
  };

  const formatFileSize = (bytes) => {
    if (!bytes) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return `${parseFloat((bytes / (k ** i)).toFixed(2))} ${sizes[i]}`;
  };

  const resetPreviewConfig = () => {
    setPreviewSubjects([]);
    setPreviewHeaders([]);
    setSelectedPreviewSubjects([]);
    setSelectedAcademicYear(defaultAcademicYear || academicYears[0]?.name || '');
    setAccountColumn('学号');
    setPasswordColumn('学号');
    setNameColumn('姓名');
    setClassColumn('');
    setClassValuesByHeader({});
    setClassMappings([]);
    setClassMappingError('');
    setIsPreviewVisible(false);
  };

  const startProgress = (startValue = 8) => {
    if (progressTimerRef.current) clearInterval(progressTimerRef.current);
    setImportProgress(startValue);
    progressTimerRef.current = setInterval(() => {
      setImportProgress((prev) => (prev >= 90 ? prev : prev + Math.max(1, Math.round((95 - prev) / 6))));
    }, 220);
  };

  const finishProgress = (callback) => {
    if (progressTimerRef.current) {
      clearInterval(progressTimerRef.current);
      progressTimerRef.current = null;
    }
    setImportProgress(100);
    window.setTimeout(() => {
      setImportProgress(0);
      callback?.();
    }, 500);
  };

  const fetchClasses = async () => {
    try {
      const data = await getClassOptions();
      setClasses(Array.isArray(data) ? data.filter((item) => item.status !== 'inactive') : []);
    } catch (error) {
      console.error('获取班级列表失败:', error);
    }
  };

  const fetchAcademicYears = async () => {
    try {
      let currentYearName = '';
      try {
        const currentRes = await getCurrentAcademicYear();
        const currentData = currentRes?.data || currentRes;
        currentYearName = currentData?.name || '';
      } catch {
        currentYearName = '';
      }
      const data = await getActiveAcademicYearList();
      const list = Array.isArray(data) ? data : (data?.data || []);
      setAcademicYears(list);
      const fallbackYearName = currentYearName || list[0]?.name || '';
      setDefaultAcademicYear(fallbackYearName);
      setSelectedAcademicYear((current) => current || fallbackYearName);
    } catch (error) {
      console.error('获取学年列表失败:', error);
    }
  };

  useEffect(() => {
    fetchClasses();
    fetchAcademicYears();
    return () => {
      if (progressTimerRef.current) clearInterval(progressTimerRef.current);
    };
  }, []);

  useEffect(() => {
    if (!classColumn) {
      setClassMappings([]);
      setClassMappingError('');
      return;
    }
    const values = classValuesByHeader[classColumn] || [];
    setClassMappings(values.map((value) => ({ classValue: value, systemClassName: '' })));
    setClassMappingError('');
  }, [classColumn, classValuesByHeader]);

  useEffect(() => {
    if (!isPreviewVisible || !classColumn) {
      setMappingTableBodyMaxHeight(null);
      return undefined;
    }

    const updateMappingTableBodyMaxHeight = () => {
      if (window.innerWidth <= 760) {
        setMappingTableBodyMaxHeight(null);
        return;
      }

      const configPanel = configPanelRef.current;
      const subjectPanel = subjectPanelRef.current;
      const mappingTableBody = mappingTableBodyRef.current;

      if (!configPanel || !subjectPanel || !mappingTableBody) {
        setMappingTableBodyMaxHeight(null);
        return;
      }

      const subjectHeight = subjectPanel.offsetHeight;
      const configHeight = configPanel.offsetHeight;
      const currentBodyHeight = mappingTableBody.clientHeight;
      const fullBodyHeight = mappingTableBody.scrollHeight;
      const overflowHeight = configHeight - subjectHeight;
      const nextHeight = Math.min(fullBodyHeight, Math.max(160, currentBodyHeight - overflowHeight));

      setMappingTableBodyMaxHeight(Number.isFinite(nextHeight) ? nextHeight : null);
    };

    updateMappingTableBodyMaxHeight();
    const timer = window.setTimeout(updateMappingTableBodyMaxHeight, 0);

    const resizeObserver = typeof ResizeObserver !== 'undefined'
      ? new ResizeObserver(() => updateMappingTableBodyMaxHeight())
      : null;

    if (resizeObserver) {
      if (configPanelRef.current) resizeObserver.observe(configPanelRef.current);
      if (subjectPanelRef.current) resizeObserver.observe(subjectPanelRef.current);
    } else {
      window.addEventListener('resize', updateMappingTableBodyMaxHeight);
    }

    return () => {
      window.clearTimeout(timer);
      if (resizeObserver) {
        resizeObserver.disconnect();
      } else {
        window.removeEventListener('resize', updateMappingTableBodyMaxHeight);
      }
    };
  }, [classColumn, classMappings.length, isPreviewVisible, previewSubjects.length]);

  const previewFile = async (selectedFile) => {
    if (!selectedFile) return;

    setLoading(true);
    try {
      const fileName = selectedFile.name;
      const fileType = fileName.endsWith('.csv') ? 'csv' : 'excel';
      const formData = new FormData();
      formData.append('file', selectedFile);
      formData.append('fileType', fileType);

      const data = await previewRainSubjectScores(formData);
      if (data.errorMessage) {
        alert(data.errorMessage);
        return;
      }
      if (!data.previewSubjects) return;

      const headers = data.headers || [];
      const orderedSubjects = [];
      headers.forEach((header) => {
        if (data.previewSubjects.includes(header)) orderedSubjects.push(header);
      });
      data.previewSubjects.forEach((subject) => {
        if (!orderedSubjects.includes(subject)) orderedSubjects.push(subject);
      });

      const hasStudentId = headers.some((header) => header.includes('学号') || header.includes('学生号') || header.includes('账号'));
      const hasName = headers.some((header) => header.includes('姓名') || header.includes('学生姓名'));

      setPreviewSubjects(orderedSubjects);
      setPreviewHeaders(headers);
      setSelectedPreviewSubjects(orderedSubjects);
      setSelectedAcademicYear(defaultAcademicYear || academicYears[0]?.name || '');
      setClassValuesByHeader(data.classValuesByHeader || {});
      setClassColumn('');
      setClassMappings([]);
      setAccountColumn(hasStudentId ? headers.find((header) => header.includes('学号') || header.includes('学生号') || header.includes('账号')) : '学号');
      setPasswordColumn(hasStudentId ? headers.find((header) => header.includes('学号') || header.includes('学生号') || header.includes('账号')) : '学号');
      setNameColumn(hasName ? headers.find((header) => header.includes('姓名') || header.includes('学生姓名')) : '姓名');
      setIsPreviewVisible(true);
      setCurrentStep(1);
    } catch (error) {
      console.error('预览成绩导入失败:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleDragOver = (e) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragging(true);
  };

  const handleDragLeave = (e) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragging(false);
  };

  const handleDrop = (e) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragging(false);
    const droppedFile = e.dataTransfer.files[0];
    if (droppedFile) {
      setFile(droppedFile);
      resetPreviewConfig();
      previewFile(droppedFile);
    }
  };

  const handleFileSelect = (e) => {
    const selectedFile = e.target.files[0];
    if (selectedFile) {
      setFile(selectedFile);
      resetPreviewConfig();
      previewFile(selectedFile);
    }
  };

  const handleFileDelete = () => {
    setFile(null);
    resetPreviewConfig();
    setCurrentStep(0);
  };

  const closePreviewPanel = () => {
    setIsPreviewVisible(false);
  };

  const togglePreviewSubject = (subject) => {
    setSelectedPreviewSubjects((prev) => (
      prev.includes(subject) ? prev.filter((item) => item !== subject) : [...prev, subject]
    ));
  };

  const updateClassMapping = (classValue, systemClassName) => {
    if (!systemClassName) {
      setClassMappingError('');
      setClassMappings((prev) => prev.map((item) => (
        item.classValue === classValue ? { ...item, systemClassName } : item
      )));
      return;
    }

    const duplicateItem = classMappings.find((item) => item.classValue !== classValue && item.systemClassName === systemClassName);
    if (duplicateItem) {
      setClassMappingError(`系统班级“${systemClassName}”已映射给“${duplicateItem.classValue}”，不能重复选择。`);
      return;
    }

    setClassMappingError('');
    setClassMappings((prev) => prev.map((item) => (
      item.classValue === classValue ? { ...item, systemClassName } : item
    )));
  };

  const handleImport = async () => {
    if (selectedPreviewSubjects.length === 0) {
      alert('请先选择要导入的科目');
      return;
    }
    if (!selectedSemester) {
      alert('请选择学年');
      return;
    }
    if (!classColumn) {
      alert('请选择班级字段');
      return;
    }
    if (classMappings.length === 0 || classMappings.some((item) => !item.classValue || !item.systemClassName)) {
      alert('请先为每个班级值选择系统班级');
      return;
    }
    if (duplicateMappedClasses.length) {
      const duplicateText = duplicateMappedClasses.join('、');
      setClassMappingError(`系统班级“${duplicateText}”存在重复映射，请调整后再导入。`);
      alert(`系统班级“${duplicateText}”存在重复映射，请调整后再导入。`);
      return;
    }
    if (!accountColumn || !passwordColumn || !nameColumn) {
      alert('请先明确选择账号、初始密码和姓名对应列');
      return;
    }

    setLoading(true);
    setOperationFeedback({
      type: 'progress',
      title: '正在导入',
      lines: ['正在上传文件并处理成绩，请稍候。']
    });
    setIsImportModalVisible(true);
    startProgress();

    try {
      const formData = new FormData();
      formData.append('file', file);
      formData.append('fileType', file.name.endsWith('.csv') ? 'csv' : 'excel');
      formData.append('semester', selectedSemester);
      formData.append('confirm', true);
      formData.append('accountColumn', accountColumn);
      formData.append('passwordColumn', passwordColumn);
      formData.append('nameColumn', nameColumn);
      formData.append('classColumn', classColumn);
      selectedPreviewSubjects.forEach((subject) => formData.append('subjects', subject));
      classMappings.forEach((mapping) => {
        formData.append('classValues', mapping.classValue);
        formData.append('classNames', mapping.systemClassName);
      });

      const data = await importRainSubjectScores(formData);
      closePreviewPanel();
      resetPreviewConfig();

      const lines = [];
      if (data.newSubjects?.length) {
        lines.push(`成功识别 ${data.newSubjects.length} 个新科目`);
        lines.push(`新增科目：${data.newSubjects.join('、')}`);
      }
      if (data.scoreImportInfo) {
        lines.push(`成绩导入：新增 ${data.scoreImportInfo.createdScoreCount || 0} 条，更新 ${data.scoreImportInfo.updatedScoreCount || 0} 条，未变更 ${data.scoreImportInfo.unchangedScoreCount || 0} 条`);
        lines.push(`本次新增：账号 ${data.scoreImportInfo.createdUserCount || 0} 个，学生 ${data.scoreImportInfo.createdStudentCount || 0} 个，科目 ${data.scoreImportInfo.createdSubjectCount || 0} 个`);
        if (data.scoreImportInfo.scoreErrors?.length) {
          lines.push(`失败 ${data.scoreImportInfo.scoreErrors.length} 条`);
          lines.push(...data.scoreImportInfo.scoreErrors.slice(0, 5));
        }
      }
      setOperationFeedback({
        type: 'success',
        title: '配置完成，准备导入',
        lines: lines.length ? lines : [data.errorMessage || '导入完成']
      });
    } catch (error) {
      console.error('导入成绩失败:', error);
      const errorData = error?.response?.data || {};
      const errorMessage = errorData.errorMessage || errorData.message || error.message;
      setOperationFeedback({
        type: 'error',
        title: '导入失败',
        lines: [`导入失败：${errorMessage}`]
      });
    } finally {
      finishProgress(() => setLoading(false));
    }
  };

  const summaryCards = useMemo(() => [
    {
      key: 'file',
      title: '文件',
      value: file?.name || '未选择',
      note: file ? formatFileSize(file.size) : '请上传文件',
      icon: 'import',
      accent: 'blue'
    },
    {
      key: 'subjects',
      title: '识别科目总数',
      value: `${previewSubjects.length}`,
      note: '个科目',
      icon: 'analysis',
      accent: 'purple'
    },
    {
      key: 'classes',
      title: '录入班级数',
      value: `${classMappings.length}`,
      note: '个班级值',
      icon: 'users',
      accent: 'orange'
    },
    {
      key: 'semester',
      title: '学年',
      value: selectedSemester || defaultAcademicYear || '-',
      note: '当前导入目标',
      icon: 'calendar',
      accent: 'green'
    }
  ], [classMappings.length, defaultAcademicYear, file, previewSubjects.length, selectedSemester]);

  const renderUploadStep = () => (
    <section className="upload-panel">
      <div className="panel-title">上传文件与字段识别</div>
      <p className="score-step-intro">先上传成绩文件，系统会自动识别表头、科目列和文件中的班级值。</p>
      <div
        className={`dropzone ${isDragging ? 'drag-over' : ''} ${file ? 'has-file' : ''}`}
        onDragOver={handleDragOver}
        onDragLeave={handleDragLeave}
        onDrop={handleDrop}
        onClick={() => document.getElementById('scoreFileInput').click()}
      >
        <input
          id="scoreFileInput"
          type="file"
          className="hidden-input"
          accept=".xlsx,.csv"
          onChange={handleFileSelect}
        />
        {file ? (
          <div className="chosen-file">
            <div className="chosen-file-copy">
              <strong>{file.name}</strong>
              <span>{formatFileSize(file.size)}</span>
            </div>
            <button type="button" className="ghost-icon-btn" onClick={(e) => { e.stopPropagation(); handleFileDelete(); }}>x</button>
          </div>
        ) : (
          <>
            <span className="dropzone-icon">↑</span>
            <strong>点击或拖拽文件到此处上传</strong>
            <span>支持 Excel (.xlsx) 和 CSV (.csv) 格式</span>
          </>
        )}
      </div>
    </section>
  );

  const renderFieldStep = () => (
    <div className="score-config-grid">
      <section className="score-panel config-panel" ref={configPanelRef}>
        <div className="panel-title with-actions">
          <span>基础字段配置</span>
          <button type="button" className="secondary-btn" onClick={() => { if (file) previewFile(file); }}>重新解析</button>
        </div>
        <div className="config-shell">
          <div className="config-grid">
            <label className="config-field">
              <span>学年</span>
              <select value={selectedAcademicYear} onChange={(e) => setSelectedAcademicYear(e.target.value)}>
                <option value="">请选择学年</option>
                {academicYears.map((academicYear) => (
                  <option key={academicYear.id} value={academicYear.name}>{academicYear.name}</option>
                ))}
              </select>
            </label>
            <label className="config-field">
              <span>账号字段</span>
              <select value={accountColumn} onChange={(e) => setAccountColumn(e.target.value)}>
                <option value="">请选择账号列</option>
                {previewHeaders.map((header) => <option key={header} value={header}>{header}</option>)}
              </select>
            </label>
            <label className="config-field">
              <span>初始密码字段</span>
              <select value={passwordColumn} onChange={(e) => setPasswordColumn(e.target.value)}>
                <option value="">请选择密码列</option>
                {previewHeaders.map((header) => <option key={header} value={header}>{header}</option>)}
              </select>
            </label>
            <label className="config-field">
              <span>姓名字段</span>
              <select value={nameColumn} onChange={(e) => setNameColumn(e.target.value)}>
                <option value="">请选择姓名列</option>
                {previewHeaders.map((header) => <option key={header} value={header}>{header}</option>)}
              </select>
            </label>
            <label className="config-field wide">
              <span>班级字段</span>
              <select value={classColumn} onChange={(e) => setClassColumn(e.target.value)}>
                <option value="">请选择班级列</option>
                {previewHeaders.map((header) => <option key={header} value={header}>{header}</option>)}
              </select>
            </label>
          </div>
        </div>
        <div className="config-hint">
          当前配置：导入到 <strong>{selectedSemester || '未选择'}</strong>，账号使用 <strong>{accountColumn || '未选择'}</strong>，初始密码使用 <strong>{passwordColumn || '未选择'}</strong>，姓名使用 <strong>{nameColumn || '未选择'}</strong>，班级字段使用 <strong>{classColumn || '未选择'}</strong>。
        </div>
        <div className="step-actions">
          <button type="button" className="secondary-btn" onClick={() => setCurrentStep(0)}>上一步</button>
          <button type="button" className="primary-btn" onClick={() => setCurrentStep(2)} disabled={!isFieldConfigReady}>下一步：科目与班级映射</button>
        </div>
      </section>

      <section className="score-panel">
        <div className="panel-title">文件表头预览</div>
        <div className="header-chip-list">
          {previewHeaders.map((header) => (
            <span key={header} className={`header-chip ${selectedHeaderSet.has(header) ? 'selected-subject' : ''}`}>{header}</span>
          ))}
        </div>
      </section>
    </div>
  );

  const renderMappingStep = () => (
    <section className="score-mapping-stack">
      <article className="score-panel mapping-panel" ref={configPanelRef}>
        <div className="panel-title">班级映射</div>
        {classMappingError ? <div className="config-hint error">{classMappingError}</div> : null}
        <div className="mapping-card">
          <div className="mapping-table-head">
            <span>文件中的班级值</span>
            <span className="mapping-arrow-spacer" />
            <span>系统班级（请选择）</span>
            <span>映射状态</span>
          </div>
          <div
            className="mapping-table-body"
            ref={mappingTableBodyRef}
            style={mappingTableBodyMaxHeight ? { maxHeight: `${mappingTableBodyMaxHeight}px` } : undefined}
          >
            {classMappings.length ? classMappings.map((mapping) => {
              const isMapped = Boolean(mapping.systemClassName);
              return (
                <div className="mapping-row" key={mapping.classValue}>
                  <span className="mapping-source">{mapping.classValue}</span>
                  <span className="mapping-arrow">→</span>
                  <select
                    className="mapping-select"
                    value={mapping.systemClassName}
                    onChange={(e) => updateClassMapping(mapping.classValue, e.target.value)}
                  >
                    <option value="">请选择系统班级</option>
                    {classes.map((item) => {
                      const selectedByOtherRow = classMappings.some(
                        (classMapping) => classMapping.classValue !== mapping.classValue && classMapping.systemClassName === item.name
                      );
                      return (
                        <option key={item.id} value={item.name} disabled={selectedByOtherRow}>
                          {item.name}{selectedByOtherRow ? '（已映射）' : ''}
                        </option>
                      );
                    })}
                  </select>
                  <span className={`mapping-status ${isMapped ? 'mapped' : 'pending'}`}>
                    <span className="mapping-status-dot">{isMapped ? '✓' : '!'}</span>
                    {isMapped ? '已映射' : '待映射'}
                  </span>
                </div>
              );
            }) : <div className="empty-inline">未识别到可用班级值，请回到上一步确认班级字段</div>}
          </div>
          {classMappings.length ? (
            <div className="mapping-summary">
              <span className="mapping-summary-dot">✓</span>
              共 {classMappings.length} 个班级值，已映射 {mappedClassCount} 个。
            </div>
          ) : null}
        </div>
        <div className="step-actions">
          <button type="button" className="secondary-btn" onClick={() => setClassMappings((classValuesByHeader[classColumn] || []).map((value) => ({ classValue: value, systemClassName: '' })))}>重置映射</button>
          <button type="button" className="secondary-btn" onClick={() => setCurrentStep(1)}>上一步</button>
          <button type="button" className="primary-btn" onClick={() => setCurrentStep(3)} disabled={!isImportReady}>下一步：校验并导入</button>
        </div>
      </article>

      <article className="score-panel subject-panel" ref={subjectPanelRef}>
        <div className="panel-title with-actions">
          <span>待导入科目</span>
          <div className="tiny-actions">
            <button type="button" className="secondary-btn small" onClick={() => setSelectedPreviewSubjects(previewSubjects)}>全选</button>
            <button type="button" className="secondary-btn small" onClick={() => setSelectedPreviewSubjects([])}>清空</button>
          </div>
        </div>
        <div className="subject-grid">
          {previewSubjects.map((subject) => {
            const checked = selectedPreviewSubjects.includes(subject);
            return (
              <label key={subject} className={`subject-chip ${checked ? 'checked' : ''}`}>
                <input
                  type="checkbox"
                  checked={checked}
                  onChange={() => togglePreviewSubject(subject)}
                />
                <span>{subject}</span>
              </label>
            );
          })}
        </div>
        <div className="subject-footer">已选择 {selectedPreviewSubjects.length} 个科目</div>
        <div className="config-hint score-blank-score-hint">
          空白成绩不会导入，也不会按 0 分处理；只有单元格明确填写 0，才表示该学生该科目成绩为 0 分。
        </div>
      </article>
    </section>
  );

  const renderConfirmStep = () => (
    <section className="score-panel">
      <div className="panel-title">校验并导入</div>
      <div className="score-confirm-list">
        <div><span>导入文件</span><strong>{file?.name || '未选择'}</strong></div>
        <div><span>目标学年</span><strong>{selectedSemester || '未选择'}</strong></div>
        <div><span>学生字段</span><strong>{accountColumn || '-'} / {nameColumn || '-'}</strong></div>
        <div><span>班级映射</span><strong>{mappedClassCount} / {classMappings.length} 个</strong></div>
        <div><span>导入科目</span><strong>{selectedPreviewSubjects.length} 个</strong></div>
      </div>
      <div className="step-actions">
        <button type="button" className="secondary-btn" onClick={() => setCurrentStep(2)} disabled={loading}>上一步</button>
        <button type="button" className="primary-btn" onClick={handleImport} disabled={!isImportReady || loading}>
          {loading ? '导入中...' : '确认导入'}
        </button>
      </div>
    </section>
  );

  const renderStepContent = () => {
    if (currentStep === 0) return renderUploadStep();
    if (currentStep === 1) return renderFieldStep();
    if (currentStep === 2) return renderMappingStep();
    return renderConfirmStep();
  };

  return (
    <div className="score-workbench">
      <nav className="score-import-steps" aria-label="成绩导入流程">
        {scoreImportSteps.map((step, index) => {
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

      {isPreviewVisible && (
        <div className="score-alert">
          <div className="score-alert-copy">
            <span className="score-alert-icon">✓</span>
            <span>文件解析成功，已识别 {previewHeaders.length} 个表头和 {previewSubjects.length} 个科目，请按流程继续配置。</span>
          </div>
          <div className="score-alert-actions">
            <button type="button" className="secondary-btn" onClick={() => setCurrentStep(1)}>字段配置</button>
            <button type="button" className="primary-btn" onClick={() => setCurrentStep(3)} disabled={!isImportReady}>校验导入</button>
          </div>
        </div>
      )}

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

      {renderStepContent()}

      {isImportModalVisible && (importProgress > 0 || operationFeedback) && (
        <div className="import-modal-overlay">
          <div className="import-modal-card">
            <div className="panel-title with-actions">
              <span>{operationFeedback?.title || '导入进度'}</span>
              {!loading && <button type="button" className="ghost-link" onClick={() => setIsImportModalVisible(false)}>关闭</button>}
            </div>
            {importProgress > 0 && (
              <div className="progress-block">
                <div className="progress-meta">
                  <span>{loading ? '处理中' : '已完成'}</span>
                  <strong>{importProgress}%</strong>
                </div>
                <div className="progress-track">
                  <div className="progress-fill" style={{ width: `${importProgress}%` }} />
                </div>
              </div>
            )}
            {operationFeedback?.lines?.length ? (
              <div className={`feedback-card ${operationFeedback.type || 'info'}`}>
                {operationFeedback.lines.map((line, index) => <p key={`${operationFeedback.title}-${index}`}>{line}</p>)}
              </div>
            ) : null}
          </div>
        </div>
      )}
    </div>
  );
}

export default ScoreManagement;
