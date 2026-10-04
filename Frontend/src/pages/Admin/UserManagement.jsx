import { useEffect, useState } from 'react';
import { createUser, getUserList, resetUserPassword } from '../../api/admin/user';
import { confirmAction } from '../../components/confirmDialogService';
import './CollegeManagement.css';
import './UserManagement.css';

const roleLabelMap = {
  ADMIN: '管理员',
  TEACHER: '教师',
  STUDENT: '学生',
  CLASS_COMMITTEE: '班委'
};

const initialCreateForm = {
  username: '',
  password: '',
  role: 'TEACHER'
};

function UserManagement() {
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [roleFilter, setRoleFilter] = useState('ALL');
  const [feedback, setFeedback] = useState('');
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [total, setTotal] = useState(0);
  const [changedPasswordCount, setChangedPasswordCount] = useState(0);
  const [pendingPasswordCount, setPendingPasswordCount] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [createForm, setCreateForm] = useState(initialCreateForm);
  const [creating, setCreating] = useState(false);

  const fetchUsers = async ({ clearFeedback = true } = {}) => {
    setLoading(true);
    if (clearFeedback) {
      setFeedback('');
    }
    try {
      const res = await getUserList({
        page,
        pageSize,
        keyword: keyword.trim() || undefined,
        role: roleFilter === 'ALL' ? undefined : roleFilter
      });
      const data = res?.data || res || {};
      setUsers(Array.isArray(data.records) ? data.records : []);
      setTotal(Number(data.total || 0));
      setChangedPasswordCount(Number(data.changedPasswordCount || 0));
      setPendingPasswordCount(Number(data.pendingPasswordCount || 0));
      setTotalPages(Math.max(1, Number(data.totalPages || 1)));
    } catch (error) {
      console.error('获取用户列表失败:', error);
      setFeedback(error?.response?.data?.message || '获取用户列表失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    const timer = window.setTimeout(fetchUsers, keyword.trim() ? 300 : 0);
    return () => window.clearTimeout(timer);
  }, [page, pageSize, keyword, roleFilter]);

  const handleResetPassword = async (user) => {
    if (!await confirmAction({
      title: '重置用户密码？',
      message: `账号 ${user.username} 的密码将被重置为该账号名。`,
      confirmText: '确认重置',
      tone: 'warning'
    })) {
      return;
    }
    try {
      await resetUserPassword(user.id);
      await fetchUsers();
      setFeedback(`已将账号 ${user.username} 的密码重置为该账号。`);
    } catch (error) {
      console.error('重置密码失败:', error);
      setFeedback(error?.response?.data?.message || '重置密码失败');
    }
  };

  const updateCreateForm = (field, value) => {
    setCreateForm((prev) => ({ ...prev, [field]: value }));
  };

  const closeCreateModal = () => {
    setCreateModalOpen(false);
    setCreateForm(initialCreateForm);
  };

  const handleCreateUser = async (event) => {
    event.preventDefault();
    setCreating(true);
    setFeedback('');
    try {
      await createUser({
        ...createForm,
        username: createForm.username.trim(),
        password: createForm.password.trim(),
        role: 'TEACHER'
      });
      closeCreateModal();
      setPage(1);
      await fetchUsers({ clearFeedback: false });
      setFeedback(`教师账号 ${createForm.username.trim()} 已创建。`);
    } catch (error) {
      console.error('新增用户失败:', error);
      setFeedback(error?.response?.data?.message || '新增用户失败');
    } finally {
      setCreating(false);
    }
  };

  return (
    <div className="college-management user-management">
      <div className="college-top-actions">
        <button className="btn btn-primary" onClick={() => setCreateModalOpen(true)}>新增教师</button>
      </div>

      <div className="user-summary">
        <div className="user-stat-card"><span>用户总数</span><strong>{total}</strong></div>
        <div className="user-stat-card"><span>已改密</span><strong>{changedPasswordCount}</strong></div>
        <div className="user-stat-card"><span>未改密</span><strong>{pendingPasswordCount}</strong></div>
      </div>

      <div className="preview-panel">
        <div className="mapping-grid">
          <div className="form-item">
            <label>账号搜索</label>
            <input
              value={keyword}
              onChange={(e) => {
                setKeyword(e.target.value);
                setPage(1);
              }}
              placeholder="输入账号、姓名、学号、班级或角色"
            />
          </div>
          <div className="form-item">
            <label>角色筛选</label>
            <select value={roleFilter} onChange={(e) => {
              setRoleFilter(e.target.value);
              setPage(1);
            }}>
              <option value="ALL">全部角色</option>
              <option value="ADMIN">管理员</option>
              <option value="TEACHER">教师</option>
              <option value="STUDENT">学生</option>
              <option value="CLASS_COMMITTEE">班委</option>
            </select>
          </div>
        </div>
        <div className="action-btns" style={{ marginTop: 16 }}>
          <button className="btn btn-default" onClick={() => {
            setKeyword('');
            setRoleFilter('ALL');
            setPage(1);
            setFeedback('');
          }}>
            重置筛选
          </button>
          <button className="btn btn-primary" onClick={fetchUsers}>
            刷新列表
          </button>
        </div>
        {feedback ? (
          <div className="selection-summary" style={{ marginTop: 16 }}>
            {feedback}
          </div>
        ) : null}
      </div>

      <div className="data-table-wrapper">
        <table className="data-table">
          <thead>
            <tr>
              <th>ID</th>
              <th>账号</th>
              <th>姓名</th>
              <th>角色</th>
              <th>学号</th>
              <th>班级</th>
              <th>所属老师ID</th>
              <th>密码状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan="9" className="loading-cell">加载中...</td>
              </tr>
            ) : users.length === 0 ? (
              <tr>
                <td colSpan="9" className="empty-cell">暂无数据</td>
              </tr>
            ) : (
              users.map((user) => (
                <tr key={user.id}>
                  <td>{user.id}</td>
                  <td><strong>{user.username}</strong></td>
                  <td>{user.nickname || '-'}</td>
                  <td>{roleLabelMap[user.role] || user.role || '-'}</td>
                  <td>{user.studentNo || '-'}</td>
                  <td>{user.className || '-'}</td>
                  <td>{user.teacherId || '-'}</td>
                  <td>
                    <span className={`status-badge ${user.passwordResetRequired ? 'inactive' : 'active'}`}>
                      {user.passwordResetRequired ? '待改密' : '正常'}
                    </span>
                  </td>
                  <td>
                    <div className="action-btns">
                      <button className="btn-link" onClick={() => handleResetPassword(user)}>
                        重置密码
                      </button>
                    </div>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
        <div className="pagination-bar">
          <div className="pagination-info">
            共 {total} 条，每页
            <select
              value={pageSize}
              onChange={(e) => {
                setPageSize(Number(e.target.value));
                setPage(1);
              }}
            >
              <option value={10}>10</option>
              <option value={20}>20</option>
              <option value={50}>50</option>
              <option value={100}>100</option>
            </select>
            条
          </div>
          <div className="pagination-actions">
            <button className="btn btn-default" onClick={() => setPage(1)}>
              首页
            </button>
            <button className="btn btn-default" onClick={() => {
              if (page <= 1) {
                setFeedback('已经是第一页');
                return;
              }
              setPage(page - 1);
            }}>
              上一页
            </button>
            <span>{page} / {totalPages}</span>
            <button className="btn btn-default" onClick={() => {
              if (page >= totalPages) {
                setFeedback('已经是最后一页');
                return;
              }
              setPage(page + 1);
            }}>
              下一页
            </button>
            <button className="btn btn-default" onClick={() => setPage(totalPages)}>
              末页
            </button>
          </div>
        </div>
      </div>

      {createModalOpen ? (
        <div className="college-modal-overlay" onClick={closeCreateModal}>
          <div className="college-modal" onClick={(event) => event.stopPropagation()}>
            <div className="college-modal-header">
              <h3>新增教师</h3>
              <button type="button" className="college-modal-close" onClick={closeCreateModal}>关闭</button>
            </div>
            <form onSubmit={handleCreateUser}>
              <div className="college-modal-body">
                <div className="college-form-row">
                  <div className="college-form-group">
                    <label>账号 <span className="required">*</span></label>
                    <input
                      value={createForm.username}
                      onChange={(event) => updateCreateForm('username', event.target.value)}
                      placeholder="如 teacher2"
                      required
                    />
                  </div>
                </div>
                <div className="college-form-row">
                  <div className="college-form-group">
                    <label>初始密码</label>
                    <input
                      type="password"
                      value={createForm.password}
                      onChange={(event) => updateCreateForm('password', event.target.value)}
                      placeholder="不填则默认等于账号"
                    />
                  </div>
                </div>
                <div className="college-form-row">
                  <div className="college-form-group">
                    <label>角色 <span className="required">*</span></label>
                    <select
                      value={createForm.role}
                      disabled
                      required
                    >
                      <option value="TEACHER">教师</option>
                    </select>
                  </div>
                </div>
                <div className="selection-summary">
                  管理员仅可在此新增教师账号。初始密码不填时默认等于账号，创建后账号状态为待改密。
                </div>
              </div>
              <div className="college-modal-footer">
                <button type="button" className="btn btn-default" onClick={closeCreateModal}>取消</button>
                <button type="submit" className="btn btn-primary" disabled={creating}>
                  {creating ? '保存中...' : '保存'}
                </button>
              </div>
            </form>
          </div>
        </div>
      ) : null}
    </div>
  );
}

export default UserManagement;
