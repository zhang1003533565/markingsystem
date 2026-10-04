import { useEffect, useState } from 'react';
import {
  getSystemEmailList,
  addSystemEmail,
  removeSystemEmail,
  setDefaultSystemEmail,
  getEmailBindingPolicy,
  updateEmailBindingPolicy
} from '../../api/admin/email';
import PageHeader from '../../components/PageHeader';
import { confirmAction } from '../../components/confirmDialogService';
import './CollegeManagement.css';
import './EmailConfig.css';

function EmailConfig() {
  const [emails, setEmails] = useState([]);
  const [loading, setLoading] = useState(false);
  const [form, setForm] = useState({ email: '', password: '' });
  const [feedback, setFeedback] = useState('');
  const [feedbackType, setFeedbackType] = useState('info');
  const [policyLoading, setPolicyLoading] = useState(false);
  const [policySaving, setPolicySaving] = useState(false);
  const [forceEmailBindingRequired, setForceEmailBindingRequired] = useState(true);

  const fetchEmails = async () => {
    setLoading(true);
    setFeedback('');
    try {
      const res = await getSystemEmailList();
      setEmails(res?.data || []);
    } catch (error) {
      console.error('获取邮箱列表失败:', error);
      setFeedback(error?.response?.data?.message || '获取邮箱列表失败');
      setFeedbackType('error');
    } finally {
      setLoading(false);
    }
  };

  const fetchEmailBindingPolicy = async () => {
    setPolicyLoading(true);
    try {
      const res = await getEmailBindingPolicy();
      setForceEmailBindingRequired(res?.data?.forceEmailBindingRequired !== false);
    } catch (error) {
      console.error('获取邮箱绑定策略失败:', error);
      setFeedback(error?.response?.data?.message || '获取邮箱绑定策略失败');
      setFeedbackType('error');
    } finally {
      setPolicyLoading(false);
    }
  };

  useEffect(() => {
    fetchEmails();
    fetchEmailBindingPolicy();
  }, []);

  const handleAdd = async (e) => {
    e.preventDefault();
    if (!form.email.trim() || !form.password.trim()) {
      setFeedback('请填写邮箱地址和密码');
      setFeedbackType('error');
      return;
    }
    try {
      await addSystemEmail(form.email.trim(), form.password.trim());
      setFeedback('添加成功');
      setFeedbackType('success');
      setForm({ email: '', password: '' });
      fetchEmails();
    } catch (error) {
      console.error('添加失败:', error);
      setFeedback(error?.response?.data?.message || '添加失败');
      setFeedbackType('error');
    }
  };

  const handleRemove = async (email) => {
    if (!await confirmAction({
      title: '删除系统邮箱？',
      message: `邮箱 ${email.email} 将从系统发件账号中移除。`,
      confirmText: '删除邮箱',
      tone: 'danger'
    })) return;
    try {
      await removeSystemEmail(email.id);
      setFeedback('删除成功');
      setFeedbackType('success');
      fetchEmails();
    } catch (error) {
      console.error('删除失败:', error);
      setFeedback(error?.response?.data?.message || '删除失败');
      setFeedbackType('error');
    }
  };

  const handleSetDefault = async (email) => {
    try {
      await setDefaultSystemEmail(email.id);
      setFeedback('设置默认成功');
      setFeedbackType('success');
      fetchEmails();
    } catch (error) {
      console.error('设置失败:', error);
      setFeedback(error?.response?.data?.message || '设置失败');
      setFeedbackType('error');
    }
  };

  const handleToggleEmailBindingPolicy = async () => {
    const nextRequired = !forceEmailBindingRequired;
    setPolicySaving(true);
    try {
      const res = await updateEmailBindingPolicy(nextRequired);
      const savedRequired = res?.data?.forceEmailBindingRequired !== false;
      setForceEmailBindingRequired(savedRequired);
      setFeedback(savedRequired ? '已开启强制绑定邮箱' : '已关闭强制绑定邮箱');
      setFeedbackType('success');
    } catch (error) {
      console.error('保存邮箱绑定策略失败:', error);
      setFeedback(error?.response?.data?.message || '保存邮箱绑定策略失败');
      setFeedbackType('error');
    } finally {
      setPolicySaving(false);
    }
  };

  return (
    <div className="college-management email-config-page">
      <PageHeader title="系统邮箱管理" />

      <div className="preview-panel">
        <div className="email-policy-card">
          <div className="email-policy-content">
            <span className={`email-policy-status ${forceEmailBindingRequired ? 'active' : 'inactive'}`}>
              {forceEmailBindingRequired ? '已开启' : '已关闭'}
            </span>
            <div>
              <h2>账号邮箱绑定要求</h2>
              <p>
                开启后，未绑定邮箱的学生和班委登录时需要先完成邮箱验证；关闭后可直接进入系统。
              </p>
            </div>
          </div>
          <button
            type="button"
            className={`email-policy-toggle ${forceEmailBindingRequired ? 'is-on' : 'is-off'}`}
            onClick={handleToggleEmailBindingPolicy}
            disabled={policyLoading || policySaving}
          >
            {policySaving ? '保存中...' : forceEmailBindingRequired ? '关闭强制绑定' : '开启强制绑定'}
          </button>
        </div>

        <div className="email-config-intro">
          <p>
            系统邮箱用于以「系统默认邮箱」模式发送公告邮件。管理员可配置多个邮箱，并设置其中一个为默认发送邮箱。
          </p>
          <button type="submit" form="system-email-form" className="btn btn-primary">添加邮箱</button>
        </div>

        <form id="system-email-form" onSubmit={handleAdd} className="mapping-grid email-config-form">
          <div className="form-item">
            <label>邮箱地址</label>
            <input
              type="email"
              value={form.email}
              onChange={(e) => setForm((p) => ({ ...p, email: e.target.value }))}
              placeholder="例如：notify@school.edu.cn"
            />
          </div>
          <div className="form-item">
            <label>密码/授权码</label>
            <input
              type="password"
              value={form.password}
              onChange={(e) => setForm((p) => ({ ...p, password: e.target.value }))}
              placeholder="STMP授权码"
            />
          </div>
        </form>

        {feedback && (
          <div
            style={{
              marginTop: 16,
              padding: '10px 16px',
              borderRadius: 4,
              background: feedbackType === 'error' ? '#fff2f0' : '#f6ffed',
              border: `1px solid ${feedbackType === 'error' ? '#ffccc7' : '#b7eb8f'}`,
              color: feedbackType === 'error' ? '#cf1322' : '#52c41a'
            }}
          >
            {feedback}
          </div>
        )}
      </div>

      <div className="data-table-wrapper">
        <table className="data-table">
          <thead>
            <tr>
              <th>邮箱地址</th>
              <th>是否默认</th>
              <th>添加时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan="4" className="loading-cell">加载中...</td>
              </tr>
            ) : emails.length === 0 ? (
              <tr>
                <td colSpan="4" className="empty-cell">暂无邮箱配置</td>
              </tr>
            ) : (
              emails.map((email) => (
                <tr key={email.id}>
                  <td>
                    <span style={{ fontFamily: 'monospace' }}>{email.email}</span>
                  </td>
                  <td>
                    {email.isDefault ? (
                      <span className="status-badge active">默认</span>
                    ) : (
                      <span className="status-badge inactive">-</span>
                    )}
                  </td>
                  <td>{email.createdAt ? String(email.createdAt).replace('T', ' ').slice(0, 19) : '-'}</td>
                  <td>
                    <div className="action-btns">
                      {!email.isDefault && (
                        <button className="btn-link" onClick={() => handleSetDefault(email)}>
                          设为默认
                        </button>
                      )}
                      {email.isDefault && (
                        <button className="btn-link email-default-action" disabled>
                          当前默认
                        </button>
                      )}
                      <button className="btn-link danger" onClick={() => handleRemove(email)}>
                        删除
                      </button>
                    </div>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}

export default EmailConfig;
