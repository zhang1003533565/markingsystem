import { useEffect, useState } from 'react';
import './CollegeManagement.css';
import './GradeManagement.css';
import {
  createGrade,
  deleteGrade,
  getGradeList,
  toggleGradeStatus,
  updateGrade
} from '../../api/admin/grade';
import { confirmAction } from '../../components/confirmDialogService';

function GradeManagement() {
  const [grades, setGrades] = useState([]);
  const [loading, setLoading] = useState(false);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingGrade, setEditingGrade] = useState(null);

  useEffect(() => {
    fetchGrades();
  }, []);

  const fetchGrades = async () => {
    setLoading(true);
    try {
      const res = await getGradeList();
      setGrades(Array.isArray(res) ? res : (res.data || []));
    } catch (error) {
      console.error('获取年级列表失败:', error);
    } finally {
      setLoading(false);
    }
  };

  const openModal = (item = null) => {
    setEditingGrade(item);
    setIsModalOpen(true);
  };

  const closeModal = () => {
    setEditingGrade(null);
    setIsModalOpen(false);
  };

  const handleSave = async (e) => {
    e.preventDefault();
    const formData = new FormData(e.target);
    const payload = {
      name: formData.get('name'),
      status: formData.get('status')
    };

    try {
      if (editingGrade) {
        await updateGrade(editingGrade.id, payload);
      } else {
        await createGrade(payload);
      }
      closeModal();
      fetchGrades();
    } catch (error) {
      console.error('保存年级失败:', error);
      alert(error?.response?.data?.message || '保存年级失败');
    }
  };

  const handleDelete = async (id) => {
    if (!await confirmAction({
      title: '删除年级？',
      message: '删除后该年级将无法继续用于学生和班级配置。',
      confirmText: '删除年级',
      tone: 'danger'
    })) {
      return;
    }
    try {
      await deleteGrade(id);
      fetchGrades();
    } catch (error) {
      console.error('删除年级失败:', error);
      alert(error?.response?.data?.message || '删除年级失败');
    }
  };

  const handleToggleStatus = async (id) => {
    try {
      await toggleGradeStatus(id);
      fetchGrades();
    } catch (error) {
      console.error('切换年级状态失败:', error);
      alert(error?.response?.data?.message || '切换年级状态失败');
    }
  };

  const activeCount = grades.filter((item) => item.status === 'active').length;
  const inactiveCount = grades.length - activeCount;

  return (
    <div className="grade-management">
      <div className="grade-top-actions">
        <button className="grade-primary-btn" onClick={() => openModal()}>新增年级</button>
      </div>

      <div className="grade-content">
        <section className="grade-summary">
          <div className="grade-stat-card">
            <span>全部年级</span>
            <strong>{grades.length}</strong>
          </div>
          <div className="grade-stat-card active">
            <span>启用中</span>
            <strong>{activeCount}</strong>
          </div>
          <div className="grade-stat-card inactive">
            <span>已停用</span>
            <strong>{inactiveCount}</strong>
          </div>
        </section>

        <section className="grade-table-card">
          <div className="grade-table-wrapper">
            <table className="grade-table">
              <thead>
                <tr>
                  <th>年级名称</th>
                  <th>状态</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                {loading ? (
                  <tr>
                    <td colSpan="3" className="grade-loading-cell">加载中...</td>
                  </tr>
                ) : grades.length === 0 ? (
                  <tr>
                    <td colSpan="3" className="grade-empty-cell">
                      <div className="grade-empty-state">
                        <strong>暂无年级数据</strong>
                        <span>点击右上角“新增年级”开始维护。</span>
                      </div>
                    </td>
                  </tr>
                ) : (
                  grades.map((item) => (
                    <tr key={item.id}>
                      <td>
                        <div className="grade-name-cell">
                          <div>
                            <strong>{item.name}</strong>
                            <span>{item.name} 级学生基础档案</span>
                          </div>
                        </div>
                      </td>
                      <td>
                        <span className={`grade-status ${item.status}`}>
                          {item.status === 'active' ? '启用' : '停用'}
                        </span>
                      </td>
                      <td>
                        <div className="grade-actions">
                          <button className="grade-action-btn edit" onClick={() => openModal(item)}>
                            编辑
                          </button>
                          <button className="grade-action-btn toggle" onClick={() => handleToggleStatus(item.id)}>
                            {item.status === 'active' ? '停用' : '启用'}
                          </button>
                          <button className="grade-action-btn delete" onClick={() => handleDelete(item.id)}>
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
        </section>
      </div>

      {isModalOpen ? (
        <div className="grade-modal-overlay" onClick={closeModal}>
          <div className="grade-modal" onClick={(e) => e.stopPropagation()}>
            <div className="grade-modal-header">
              <h3>{editingGrade ? '编辑年级' : '新增年级'}</h3>
              <button className="grade-modal-close" onClick={closeModal}>关闭</button>
            </div>
            <form onSubmit={handleSave}>
              <div className="grade-modal-body">
                <div className="grade-form-row">
                  <div className="grade-form-group">
                    <label>年级名称 <span className="required">*</span></label>
                    <input
                      name="name"
                      defaultValue={editingGrade?.name || ''}
                      placeholder="如：2026"
                      required
                    />
                    <span className="grade-field-hint">建议使用四位入学年份，例如 2026。</span>
                  </div>
                </div>
                <div className="grade-form-row">
                  <div className="grade-form-group">
                    <label>启用状态</label>
                    <select name="status" defaultValue={editingGrade?.status || 'active'}>
                      <option value="active">启用</option>
                      <option value="inactive">停用</option>
                    </select>
                  </div>
                </div>
              </div>
              <div className="grade-modal-footer">
                <button type="button" className="grade-secondary-btn" onClick={closeModal}>取消</button>
                <button type="submit" className="grade-primary-btn">保存</button>
              </div>
            </form>
          </div>
        </div>
      ) : null}
    </div>
  );
}

export default GradeManagement;
