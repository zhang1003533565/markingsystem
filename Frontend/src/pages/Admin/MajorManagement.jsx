import { useState, useEffect } from 'react';
import { Pagination } from 'antd';
import './MajorManagement.css';
import {
  getMajorList,
  createMajor,
  updateMajor,
  deleteMajor,
  getMajorDeleteImpact
} from '../../api/admin/major';
import { getCollegeList } from '../../api/admin/college';
import { confirmAction } from '../../components/confirmDialogService';

function MajorManagement() {
  const [majors, setMajors] = useState([]);
  const [colleges, setColleges] = useState([]);
  const [loading, setLoading] = useState(false);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingMajor, setEditingMajor] = useState(null);
  const [selectedCollegeFilter, setSelectedCollegeFilter] = useState('');
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 10,
    total: 0
  });

  useEffect(() => {
    fetchMajors({ current: 1, pageSize: 10 }, '');
    fetchColleges();
  }, []);

  const fetchMajors = async (
    nextPagination = pagination,
    collegeFilter = selectedCollegeFilter
  ) => {
    setLoading(true);
    try {
      const current = nextPagination.current || 1;
      const pageSize = nextPagination.pageSize || 10;
      const res = await getMajorList({
        page: current - 1,
        size: pageSize,
        collegeId: collegeFilter || undefined
      });
      const payload = res?.data || res || {};
      const data = Array.isArray(payload)
        ? payload
        : (Array.isArray(payload.content) ? payload.content : []);
      setMajors(data);
      setPagination({
        current,
        pageSize,
        total: Array.isArray(payload) ? payload.length : Number(payload.totalElements || 0)
      });
    } catch (error) {
      console.error('获取专业列表失败:', error);
    } finally {
      setLoading(false);
    }
  };

  const fetchColleges = async () => {
    try {
      const res = await getCollegeList();
      setColleges(Array.isArray(res) ? res : (res.data || []));
    } catch (error) {
      console.error('获取学院列表失败:', error);
    }
  };

  const openModal = (major = null) => {
    setEditingMajor(major);
    setIsModalOpen(true);
  };

  const closeModal = () => {
    setIsModalOpen(false);
    setEditingMajor(null);
  };

  const handleSave = async (e) => {
    e.preventDefault();
    const formData = new FormData(e.target);
    const majorData = {
      name: formData.get('name'),
      collegeId: parseInt(formData.get('collegeId')),
      status: formData.get('status')
    };

    try {
      if (editingMajor) {
        await updateMajor(editingMajor.id, majorData);
      } else {
        await createMajor(majorData);
      }
      fetchMajors();
      closeModal();
    } catch (error) {
      console.error('保存专业失败:', error);
    }
  };

  const handleDelete = async (id) => {
    try {
      const impactRes = await getMajorDeleteImpact(id);
      const impact = impactRes?.data || impactRes || {};
      const classes = Array.isArray(impact.classes) ? impact.classes : [];
      const users = Array.isArray(impact.users) ? impact.users : [];

      if (!classes.length) {
        if (!await confirmAction({
          title: '删除专业？',
          message: '该专业没有关联班级，删除后不可恢复。',
          confirmText: '删除专业',
          tone: 'danger'
        })) {
          return;
        }
        await deleteMajor(id);
        fetchMajors();
        return;
      }

      const classPreview = classes.slice(0, 8).map((item) => `${item.name}（${item.grade}级）`).join('\n');
      if (!await confirmAction({
        title: '删除专业及关联班级？',
        message: `该专业下有 ${impact.classCount} 个班级，删除专业时会一并删除。`,
        detail: `${classPreview}${classes.length > 8 ? '\n…' : ''}`,
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

      await deleteMajor(id, {
        force: true,
        deleteUsers
      });
      fetchMajors();
    } catch (error) {
      console.error('删除专业失败:', error);
      window.alert(error?.response?.data?.message || '删除专业失败');
    }
  };

  const activeCount = majors.filter((item) => item.status === 'active').length;

  return (
    <div className="major-management">
      <div className="major-top-actions">
        <button className="btn btn-primary" onClick={() => openModal()}><span>新增专业</span></button>
      </div>

      <div className="major-summary">
        <div className="major-stat-card"><span>专业总数</span><strong>{pagination.total}</strong></div>
        <div className="major-stat-card"><span>本页启用</span><strong>{activeCount}</strong></div>
        <div className="major-stat-card"><span>所属学院</span><strong>{colleges.length}</strong></div>
      </div>

      <div className="filter-bar">
        <div className="filter-item">
          <label>所属学院筛选</label>
            <select
              value={selectedCollegeFilter}
              onChange={(e) => {
                const nextFilter = e.target.value;
                const nextPagination = { ...pagination, current: 1 };
                setSelectedCollegeFilter(nextFilter);
                setPagination(nextPagination);
                fetchMajors(nextPagination, nextFilter);
              }}
            >
            <option value="">全部学院</option>
            {colleges.map(college => (
              <option key={college.id} value={college.id}>
                {college.name}
              </option>
            ))}
          </select>
        </div>
      </div>

      <div className="data-table-wrapper">
        <table className="data-table">
          <thead>
            <tr>
              <th>专业名称</th>
              <th>所属学院</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan="4" className="loading-cell">加载中...</td>
              </tr>
            ) : majors.length === 0 ? (
              <tr>
                <td colSpan="4" className="empty-cell">
                  <div className="empty-state">
                    <div className="empty-text">暂无专业数据</div>
                  </div>
                </td>
              </tr>
            ) : (
              majors.map(major => (
                <tr key={major.id}>
                  <td>
                    <strong className="major-name">{major.name}</strong>
                  </td>
                  <td>
                    <span className="college-tag">{major.college?.name || '-'}</span>
                  </td>
                  <td>
                    <span className={`status-badge ${major.status}`}>
                      {major.status === 'active' ? '启用' : '停用'}
                    </span>
                  </td>
                  <td>
                    <div className="action-btns">
                      <button className="action-btn edit" onClick={() => openModal(major)} title="编辑">
                        <span>编辑</span>
                      </button>
                      <button className="action-btn delete" onClick={() => handleDelete(major.id)} title="删除">
                        <span>删除</span>
                      </button>
                    </div>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
        <div className="major-pagination">
          <Pagination
            current={pagination.current}
            pageSize={pagination.pageSize}
            total={pagination.total}
            showSizeChanger
            showQuickJumper
            showTotal={(total) => `共 ${total} 个专业`}
            onChange={(current, pageSize) => {
              const nextPagination = { current, pageSize, total: pagination.total };
              setPagination(nextPagination);
              fetchMajors(nextPagination);
            }}
          />
        </div>
      </div>

      {isModalOpen && (
        <div className="modal-overlay" onClick={closeModal}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3>{editingMajor ? '编辑专业' : '新增专业'}</h3>
              <button className="modal-close" onClick={closeModal}>关闭</button>
            </div>
            <form onSubmit={handleSave}>
              <div className="modal-body">
                <div className="form-row">
                  <div className="form-group">
                    <label>专业名称 <span className="required">*</span></label>
                    <input
                      name="name"
                      defaultValue={editingMajor?.name}
                      placeholder="如：软件工程"
                      required
                    />
                  </div>
                </div>
                <div className="form-row">
                  <div className="form-group">
                    <label>所属学院 <span className="required">*</span></label>
                    <select name="collegeId" defaultValue={editingMajor?.collegeId || ''} required>
                      <option value="">请选择学院</option>
                      {colleges.map(college => (
                        <option key={college.id} value={college.id}>{college.name}</option>
                      ))}
                    </select>
                  </div>
                </div>
                <div className="form-row">
                  <div className="form-group">
                    <label>状态</label>
                    <select name="status" defaultValue={editingMajor?.status || 'active'}>
                      <option value="active">启用</option>
                      <option value="inactive">停用</option>
                    </select>
                  </div>
                </div>
              </div>
              <div className="modal-footer">
                <button type="button" className="btn btn-default" onClick={closeModal}>取消</button>
                <button type="submit" className="btn btn-primary">保存</button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}

export default MajorManagement;
