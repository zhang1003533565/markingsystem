import { useState, useEffect } from 'react';
import './CollegeManagement.css';
import {
  getCollegeList,
  createCollege,
  updateCollege,
  deleteCollege,
  getCollegeDeleteImpact
} from '../../api/admin/college';
import { confirmAction } from '../../components/confirmDialogService';

function CollegeManagement() {
  const [colleges, setColleges] = useState([]);
  const [loading, setLoading] = useState(false);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingCollege, setEditingCollege] = useState(null);

  useEffect(() => {
    fetchColleges();
  }, []);

  const fetchColleges = async () => {
    setLoading(true);
    try {
      const res = await getCollegeList();
      setColleges(Array.isArray(res) ? res : (res.data || []));
    } catch (error) {
      console.error('获取学院列表失败:', error);
    } finally {
      setLoading(false);
    }
  };

  const openModal = (college = null) => {
    setEditingCollege(college);
    setIsModalOpen(true);
  };

  const closeModal = () => {
    setIsModalOpen(false);
    setEditingCollege(null);
  };

  const handleSave = async (e) => {
    e.preventDefault();
    const formData = new FormData(e.target);
    const collegeData = {
      name: formData.get('name'),
      status: formData.get('status')
    };

    try {
      if (editingCollege) {
        await updateCollege(editingCollege.id, collegeData);
      } else {
        await createCollege(collegeData);
      }
      fetchColleges();
      closeModal();
    } catch (error) {
      console.error('保存学院失败:', error);
    }
  };

  const handleDelete = async (id) => {
    try {
      const impactRes = await getCollegeDeleteImpact(id);
      const impact = impactRes?.data || impactRes || {};
      const majors = Array.isArray(impact.majors) ? impact.majors : [];
      const users = Array.isArray(impact.users) ? impact.users : [];

      if (!majors.length) {
        if (!await confirmAction({
          title: '删除学院？',
          message: '该学院没有关联专业，删除后不可恢复。',
          confirmText: '删除学院',
          tone: 'danger'
        })) {
          return;
        }
        await deleteCollege(id);
        fetchColleges();
        return;
      }

      const majorPreview = majors.slice(0, 8)
        .map((item) => item.name)
        .join('\n');
      if (!await confirmAction({
        title: '删除学院及关联组织？',
        message: `该学院下有 ${impact.majorCount} 个专业、${impact.classCount} 个班级，删除学院时会一并删除。`,
        detail: `${majorPreview}${majors.length > 8 ? '\n…' : ''}`,
        confirmText: '继续处理',
        tone: 'danger'
      })) {
        return;
      }

      let deleteUsers = false;
      if (impact.userCount > 0) {
        const userPreview = users.slice(0, 8)
          .map((item) => `${item.nickname || item.username}${item.studentNo && item.studentNo !== '-' ? `（${item.studentNo}）` : ''}`)
          .join('\n');
        const userChoice = await confirmAction({
          title: '如何处理关联用户？',
          message: `还有 ${impact.userCount} 个用户账号、${impact.studentCount} 个学生档案。`,
          detail: `${userPreview}${users.length > 8 ? '\n…' : ''}`,
          confirmText: '同时删除账号',
          alternativeText: '保留账号并继续',
          cancelText: '取消操作',
          tone: 'danger'
        });
        if (!userChoice) return;
        deleteUsers = userChoice === true;
      }

      await deleteCollege(id, {
        force: true,
        deleteUsers
      });
      fetchColleges();
    } catch (error) {
      console.error('删除学院失败:', error);
      window.alert(error?.response?.data?.message || '删除学院失败');
    }
  };

  const activeCount = colleges.filter((college) => college.status === 'active').length;
  const inactiveCount = colleges.length - activeCount;

  return (
    <div className="college-management">
      <div className="college-top-actions">
        <button className="college-btn college-btn-primary" onClick={() => openModal()}>
          <span>新增学院</span>
        </button>
      </div>

      <div className="college-content-panel">
        <div className="college-overview">
          <div className="overview-item">
            <span className="overview-label">学院总数</span>
            <strong>{colleges.length}</strong>
          </div>
          <div className="overview-item">
            <span className="overview-label">启用学院</span>
            <strong>{activeCount}</strong>
          </div>
          <div className="overview-item">
            <span className="overview-label">停用学院</span>
            <strong>{inactiveCount}</strong>
          </div>
        </div>

        <div className="college-table-wrapper">
          <table className="college-table">
            <thead>
              <tr>
                <th>学院名称</th>
                <th>状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr>
                  <td colSpan="3" className="college-loading-cell">加载中...</td>
                </tr>
              ) : colleges.length === 0 ? (
                <tr>
                  <td colSpan="3" className="college-empty-cell">
                    <div className="college-empty-state">
                      <div className="college-empty-text">暂无学院数据</div>
                    </div>
                  </td>
                </tr>
              ) : (
                colleges.map((college) => (
                  <tr key={college.id}>
                    <td>
                      <strong>{college.name}</strong>
                    </td>
                    <td>
                      <span className={`college-status-badge ${college.status}`}>
                        {college.status === 'active' ? '启用' : '停用'}
                      </span>
                    </td>
                    <td>
                      <div className="college-action-btns">
                        <button className="college-action-btn edit" onClick={() => openModal(college)} title="编辑">
                          <span>编辑</span>
                        </button>
                        <button className="college-action-btn delete" onClick={() => handleDelete(college.id)} title="删除">
                          <span>删除</span>
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

      {isModalOpen && (
        <div className="college-modal-overlay" onClick={closeModal}>
          <div className="college-modal" onClick={(e) => e.stopPropagation()}>
            <div className="college-modal-header">
              <h3>{editingCollege ? '编辑学院' : '新增学院'}</h3>
              <button className="college-modal-close" onClick={closeModal}>关闭</button>
            </div>
            <form onSubmit={handleSave}>
              <div className="college-modal-body">
                <div className="college-form-row">
                  <div className="college-form-group">
                    <label>学院名称 <span className="required">*</span></label>
                    <input
                      name="name"
                      defaultValue={editingCollege?.name}
                      placeholder="如：计算机学院"
                      required
                    />
                  </div>
                </div>
                <div className="college-form-row">
                  <div className="college-form-group">
                    <label>启用状态</label>
                    <select name="status" defaultValue={editingCollege?.status || 'active'}>
                      <option value="active">启用</option>
                      <option value="inactive">停用</option>
                    </select>
                  </div>
                </div>
              </div>
              <div className="college-modal-footer">
                <button type="button" className="college-btn college-btn-default" onClick={closeModal}>取消</button>
                <button type="submit" className="college-btn college-btn-primary">保存</button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}

export default CollegeManagement;
