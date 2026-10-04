import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  assignExtraScoreTemplate,
  createExtraScoreTemplate,
  deleteExtraScoreTemplate,
  exportAssignedExtraScoreTemplate,
  getAssignedExtraScoreTemplate,
  getExtraScoreTemplateDetail,
  getExtraScoreTemplateList,
  importExtraScoreTemplateWorkbook,
  toggleExtraScoreTemplateStatus
} from '../../api/admin/extraScoreTemplate';
import AppIcon from '../../components/AppIcon';
import FeedbackNotice from '../../components/FeedbackNotice';
import { confirmAction, noticeAction } from '../../components/confirmDialogService';
import { getStoredUser } from '../../utils/authStorage';
import {
  canEditExtraScoreTemplate
} from '../../utils/extraScoreTemplatePermissions';
import './ExtraScoreTemplateManagement.css';

const typeLabelMap = {
  MORAL: '德育',
  INTELLECTUAL: '智育',
  PHYSICAL: '体育'
};

const formatTime = (value) => {
  if (!value) return '-';
  return String(value).replace('T', ' ').slice(0, 16);
};

const getTemplateGroupCount = (template) => template.groupCount ?? (template.groups || []).length;

const getTemplateItemCount = (template) => (
  template.itemCount ?? (template.groups || []).reduce((sum, group) => sum + ((group.items || []).length), 0)
);

const getTemplateScaleText = (template) => `${getTemplateGroupCount(template)} 个大类 · ${getTemplateItemCount(template)} 个小类`;

const getTemplateCreatorName = (template) => (
  String(template?.creatorName || '').trim() || '创建人未记录'
);

const getTemplateFeedbackNotice = (feedback) => {
  if (!feedback) return null;
  if (typeof feedback === 'object') return feedback;

  const message = String(feedback);
  if (message.includes('失败')) {
    return { type: 'error', title: '操作未完成', message };
  }
  if (message.includes('请') || message.includes('不能')) {
    return { type: 'warning', title: '需要先处理以下问题', message };
  }
  return { type: 'success', title: '操作成功', message };
};

const getPermissionFeedback = (template, action) => ({
  type: 'warning',
  title: `无法${action}该模板`,
  message: `当前账号没有“${template?.name || '该模板'}”的${action}权限。`,
  reason: template?.creatorName
    ? `该模板由“${template.creatorName}”创建。教师只能维护自己创建的模板，管理员可维护全部模板。`
    : '该模板不是由当前账号创建。教师只能维护自己创建的模板，管理员可维护全部模板。',
  suggestion: '请联系模板创建人处理，或者复制该模板后编辑自己的副本。',
});

function ExtraScoreTemplateManagement({ forcedType = 'MORAL' }) {
  const navigate = useNavigate();
  const templateImportFileInputRef = useRef(null);
  const user = getStoredUser();
  const isTeacher = user.role === 'TEACHER';

  const [templates, setTemplates] = useState([]);
  const [assignedTemplateId, setAssignedTemplateId] = useState(null);
  const [loading, setLoading] = useState(false);
  const [feedback, setFeedback] = useState('');
  const [exportingTemplate, setExportingTemplate] = useState(false);
  const [importingTemplate, setImportingTemplate] = useState(false);
  const [importFailures, setImportFailures] = useState([]);

  const fetchTemplates = useCallback(async () => {
    setLoading(true);
    try {
      const res = await getExtraScoreTemplateList();
      setTemplates(Array.isArray(res?.data) ? res.data : []);
    } catch (error) {
      setFeedback(error?.response?.data?.message || '获取模板列表失败');
    } finally {
      setLoading(false);
    }
  }, []);

  const fetchAssignedTemplate = useCallback(async () => {
    if (!isTeacher) {
      setAssignedTemplateId(null);
      return;
    }
    try {
      const res = await getAssignedExtraScoreTemplate(forcedType);
      setAssignedTemplateId(res?.data?.template?.id || null);
    } catch {
      setAssignedTemplateId(null);
    }
  }, [forcedType, isTeacher]);

  useEffect(() => {
    fetchTemplates();
    fetchAssignedTemplate();
  }, [fetchTemplates, fetchAssignedTemplate]);

  const filteredTemplates = useMemo(
    () => templates.filter((item) => item.scoreType === forcedType),
    [templates, forcedType]
  );

  const sortedTemplates = useMemo(() => {
    return [...filteredTemplates].sort((a, b) => {
      if (assignedTemplateId === a.id) return -1;
      if (assignedTemplateId === b.id) return 1;
      return String(b.updatedAt || b.createdAt || '').localeCompare(String(a.updatedAt || a.createdAt || ''));
    });
  }, [assignedTemplateId, filteredTemplates]);

  const currentTemplate = useMemo(
    () => sortedTemplates.find((item) => item.id === assignedTemplateId) || null,
    [assignedTemplateId, sortedTemplates]
  );

  const pageTitle = `${typeLabelMap[forcedType]}附加分模板`;
  const feedbackNotice = getTemplateFeedbackNotice(feedback);

  const handleDelete = async (template) => {
    if (!canEditExtraScoreTemplate(template, user)) {
      setFeedback('');
      const permissionChoice = await noticeAction({
        variant: 'permission',
        title: '无法删除该模板',
        message: `你没有删除模板「${template?.name || '未命名模板'}」的权限`,
        permission: {
          creatorName: template?.creatorName || '未记录',
          rule: '教师仅能删除自己创建的模板',
        },
        alternativeText: '复制为我的模板',
        confirmText: '我知道了',
        tone: 'primary',
      });
      if (permissionChoice === 'alternative') {
        await handleCopyTemplate(template);
      }
      return;
    }
    if (!await confirmAction({
      title: '删除附加分模板？',
      message: `模板“${template.name || '未命名模板'}”删除后不可恢复。`,
      detail: '只有模板创建人或管理员可以执行删除操作。',
      confirmText: '删除模板',
      tone: 'danger'
    })) {
      return;
    }
    try {
      await deleteExtraScoreTemplate(template.id);
      setFeedback('模板已删除');
      fetchTemplates();
    } catch (error) {
      setFeedback(error?.response?.data?.message || '删除模板失败');
    }
  };

  const handleToggleStatus = async (id) => {
    try {
      await toggleExtraScoreTemplateStatus(id);
      fetchTemplates();
      fetchAssignedTemplate();
    } catch (error) {
      setFeedback(error?.response?.data?.message || '切换模板状态失败');
    }
  };

  const handleAssignTemplate = async (item) => {
    if (item.status !== 'active') {
      setFeedback('请先启用模板，再设为当前使用');
      return;
    }
    try {
      await assignExtraScoreTemplate(item.id);
      setAssignedTemplateId(item.id);
      setFeedback(`已将“${item.name}”设为当前使用模板`);
    } catch (error) {
      setFeedback(error?.response?.data?.message || '设置当前使用模板失败');
    }
  };

  const openCreatePage = () => {
    navigate(`/admin/extra-score-templates/${forcedType.toLowerCase()}/new`);
  };

  const openEditPage = (template) => {
    if (!template?.id) return;
    if (!canEditExtraScoreTemplate(template, user)) {
      setFeedback(getPermissionFeedback(template, '修改'));
      return;
    }
    navigate(`/admin/extra-score-templates/${forcedType.toLowerCase()}/${template.id}`);
  };

  const handleExportCurrentTemplate = async () => {
    if (!assignedTemplateId) {
      setFeedback('导出当前模板失败：当前没有使用中的模板，请先选择一个模板');
      return;
    }
    setExportingTemplate(true);
    setFeedback('');
    try {
      const blob = await exportAssignedExtraScoreTemplate(forcedType);
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `${typeLabelMap[forcedType]}当前模板.xlsx`;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      window.URL.revokeObjectURL(url);
      setFeedback('当前模板已导出');
    } catch (error) {
      setFeedback(error?.response?.data?.message || '导出当前模板失败');
    } finally {
      setExportingTemplate(false);
    }
  };

  const handleCopyTemplate = async (item) => {
    if (!item?.id) return;
    try {
      const detailRes = await getExtraScoreTemplateDetail(item.id);
      const detail = detailRes?.data || item;
      await createExtraScoreTemplate({
        name: `${detail.name || item.name} 副本`,
        scoreType: detail.scoreType || forcedType,
        maxScore: detail.maxScore ?? item.maxScore ?? 0,
        description: detail.description || item.description || '',
        proofRequirement: detail.proofRequirement || item.proofRequirement || '',
        status: 'active',
        groups: detail.groups || []
      });
      await fetchTemplates();
      setFeedback('模板已复制');
    } catch (error) {
      setFeedback(error?.response?.data?.message || '复制模板失败');
    }
  };

  const handleTemplateImportClick = () => {
    templateImportFileInputRef.current?.click();
  };

  const handleTemplateImportFileChange = async (event) => {
    const file = event.target.files?.[0];
    event.target.value = '';
    if (!file) {
      return;
    }
    if (!file.name.toLowerCase().endsWith('.xlsx')) {
      setFeedback('请上传 .xlsx 格式的模板 Excel');
      return;
    }

    const formData = new FormData();
    formData.append('file', file);
    formData.append('scoreType', forcedType);

    setImportingTemplate(true);
    setFeedback('');
    setImportFailures([]);
    try {
      const response = await importExtraScoreTemplateWorkbook(formData);
      const data = response?.data || {};
      const template = data.template || {};
      await Promise.all([fetchTemplates(), fetchAssignedTemplate()]);
      setFeedback(`新模板导入完成：${template.name || `${typeLabelMap[forcedType]}模板`}，已包含 ${data.groupCount || 0} 个大类、${data.itemCount || 0} 个小类`);
    } catch (error) {
      setFeedback(error?.response?.data?.message || '导入新模板失败');
    } finally {
      setImportingTemplate(false);
    }
  };

  return (
    <div className="template-management">
      <section className="template-page-head">
        <div>
          <h1>{pageTitle}</h1>
          <p>管理{typeLabelMap[forcedType]}附加分规则</p>
        </div>
        <div className="template-page-actions">
          {isTeacher ? (
            <>
              <button
                className="template-btn template-btn-secondary"
                type="button"
                onClick={handleTemplateImportClick}
                disabled={importingTemplate}
              >
                <AppIcon name="import" size={17} />
                {importingTemplate ? '导入中...' : '导入模板'}
              </button>
              <input
                ref={templateImportFileInputRef}
                type="file"
                accept=".xlsx"
                className="template-import-input"
                onChange={handleTemplateImportFileChange}
              />
            </>
          ) : null}
          <button className="template-btn template-btn-primary" type="button" onClick={openCreatePage}>
            <span className="template-add-icon" aria-hidden="true">+</span>
            新建模板
          </button>
        </div>
      </section>

      <section className="template-current-card">
        <div className="template-current-icon" aria-hidden="true"><span>☆</span></div>
        {currentTemplate ? (
          <div className="template-current-main">
            <div className="template-current-kicker">
              <span>当前使用</span>
            </div>
            <h2>{currentTemplate.name}</h2>
            <p>
              {getTemplateScaleText(currentTemplate)}
              <span>·</span>
              最高 {currentTemplate.maxScore ?? '-'} 分
              <span>·</span>
              创建人 {getTemplateCreatorName(currentTemplate)}
              <span>·</span>
              最近更新 {formatTime(currentTemplate.updatedAt || currentTemplate.createdAt)}
            </p>
          </div>
        ) : (
          <div className="template-current-main">
            <div className="template-current-kicker">
              <span>当前使用</span>
            </div>
            <h2>尚未设置当前模板</h2>
            <p>请从下方模板列表中选择一个有效模板作为当前使用模板。</p>
          </div>
        )}
        <div className="template-current-actions">
          <button
            type="button"
            className="template-btn template-btn-secondary"
            onClick={() => currentTemplate && openEditPage(currentTemplate)}
            disabled={!currentTemplate}
          >
            <AppIcon name="edit" size={17} />
            编辑配置
          </button>
        </div>
      </section>

      {feedbackNotice ? (
        <FeedbackNotice
          {...feedbackNotice}
          onDismiss={() => setFeedback('')}
        />
      ) : null}
      {importFailures.length ? (
        <div className="template-import-errors">
          {importFailures.map((item) => (
            <div key={`${item.rowNumber}-${item.studentNo || ''}-${item.message}`} className="template-import-error-line">
              第 {item.rowNumber} 行 {item.studentNo || '-'} {item.studentName || '-'}：{item.message}
            </div>
          ))}
        </div>
      ) : null}

      <section className="template-table-card">
        <div className="table-header">
          <div>
            <div className="table-title">模板列表</div>
            <p className="table-subtitle">ⓘ 切换当前模板只影响后续填报，历史数据不会发生变化。</p>
          </div>
          <span className="table-count">共 {filteredTemplates.length} 个模板</span>
        </div>
        
        <div className="template-table-scroll">
        <table className="template-table">
          <colgroup>
            <col className="col-name" />
            <col className="col-rule" />
            <col className="col-creator" />
            <col className="col-updated" />
            <col className="col-status" />
            <col className="col-actions" />
          </colgroup>
          <thead>
            <tr>
              <th>模板名称</th>
              <th>规则信息</th>
              <th>创建人</th>
              <th>更新时间</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr><td colSpan="6" className="template-loading">加载中...</td></tr>
            ) : filteredTemplates.length === 0 ? (
              <tr>
                <td colSpan="6" className="template-empty">
                  <div className="template-empty-text">暂无模板</div>
                </td>
              </tr>
            ) : sortedTemplates.map((item) => {
              const groupCount = item.groupCount ?? (item.groups || []).length;
              const itemCount = item.itemCount ?? (item.groups || []).reduce((sum, group) => sum + ((group.items || []).length), 0);
              const isCurrent = isTeacher && assignedTemplateId === item.id;
              return (
                  <tr key={item.id} className={isCurrent ? 'template-current-row' : ''}>
                    <td className="cell-name">
                      <div className="template-name">
                        <div className="template-name-main">
                          <button
                            type="button"
                            className="template-name-button"
                            onClick={() => openEditPage(item)}
                          >
                            {item.name}
                          </button>
                          {isCurrent ? <span className="template-assigned-badge">当前使用</span> : null}
                        </div>
                        <span className="template-proof">{item.description || item.proofRequirement || '未填写说明'}</span>
                      </div>
                    </td>
                    <td className="cell-rule">
                      {groupCount} 个大类 · {itemCount} 个小类 · 最高 {item.maxScore ?? '-'} 分
                    </td>
                    <td>
                      <div className="template-creator">
                        <span className="template-creator-avatar" aria-hidden="true">
                          {getTemplateCreatorName(item).slice(0, 1)}
                        </span>
                        <span>{getTemplateCreatorName(item)}</span>
                      </div>
                    </td>
                    <td>{formatTime(item.updatedAt || item.createdAt)}</td>
                    <td className="cell-center">
                      <span className={`template-status ${isCurrent ? 'current' : 'inactive'}`}>
                        {isCurrent ? '当前使用' : '未使用'}
                      </span>
                    </td>
                    <td className="cell-actions">
                      <div className="template-action-btns">
                        {isTeacher && !isCurrent ? (
                          <button
                            className="template-action-btn assign"
                            disabled={item.status !== 'active'}
                            onClick={() => handleAssignTemplate(item)}
                            title={item.status !== 'active' ? '停用模板不能设为当前使用' : undefined}
                          >
                            设为当前
                          </button>
                        ) : null}
                        {isCurrent ? (
                          <button className="template-action-btn edit" onClick={() => openEditPage(item)}>
                            编辑
                          </button>
                        ) : null}
                        <details className="template-more-menu row">
                          <summary>更多</summary>
                          <div>
                            <button
                              type="button"
                              onClick={handleExportCurrentTemplate}
                              disabled={!isCurrent || exportingTemplate}
                              title={!isCurrent ? '仅当前模板支持导出' : undefined}
                            >
                              {isCurrent && exportingTemplate ? '导出中...' : '导出模板'}
                            </button>
                            <button type="button" onClick={() => handleCopyTemplate(item)}>复制模板</button>
                            <button
                              type="button"
                              onClick={() => handleToggleStatus(item.id)}
                              disabled={isCurrent}
                              title={isCurrent ? '请先切换至其他模板。' : undefined}
                            >
                              {item.status === 'active' ? '停用模板' : '启用模板'}
                            </button>
                            <button
                              type="button"
                              className="danger"
                              onClick={() => handleDelete(item)}
                              disabled={isCurrent}
                              title={isCurrent ? '请先切换至其他模板。' : undefined}
                            >
                              删除模板
                            </button>
                          </div>
                        </details>
                      </div>
                    </td>
                  </tr>
              );
            })}
          </tbody>
        </table>
        </div>
      </section>
    </div>
  );
}

export default ExtraScoreTemplateManagement;
