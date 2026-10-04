import { useState, useEffect } from 'react';
import './SubjectManagement.css';
import {
  getSubjectList, 
  createSubject, 
  updateSubject, 
  deleteSubject,
  batchDeleteSubjects,
  updateSubjectStatus
} from '../../api/admin/subject';
import { confirmAction } from '../../components/confirmDialogService';

function SubjectManagement() {
  const [subjects, setSubjects] = useState([]);
  const [loading, setLoading] = useState(false);
  const [searchForm, setSearchForm] = useState({
    keyword: '',
    subjectType: '',
    status: ''
  });
  const [selectedSubjects, setSelectedSubjects] = useState([]);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingSubject, setEditingSubject] = useState(null);

  const subjectTypes = ['德育', '智育', '体育'];

  useEffect(() => {
    fetchSubjects();
  }, []);

  const fetchSubjects = async () => {
    setLoading(true);
    try {
      const params = {
        keyword: searchForm.keyword || undefined,
        subjectType: searchForm.subjectType || undefined,
        status: searchForm.status || undefined
      };
      const res = await getSubjectList(params);
      setSubjects(Array.isArray(res) ? res : (res.data || []));
    } catch (error) {
      console.error('获取科目列表失败:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleSearch = () => {
    fetchSubjects();
  };

  const handleReset = () => {
    setSearchForm({ keyword: '', subjectType: '', status: '' });
    fetchSubjects();
  };

  const handleSelectSubject = (id) => {
    setSelectedSubjects(prev => 
      prev.includes(id) 
        ? prev.filter(sid => sid !== id)
        : [...prev, id]
    );
  };

  const handleSelectAll = () => {
    if (selectedSubjects.length === subjects.length) {
      setSelectedSubjects([]);
    } else {
      setSelectedSubjects(subjects.map(s => s.id));
    }
  };

  const openModal = (subject = null) => {
    setEditingSubject(subject);
    setIsModalOpen(true);
  };

  const closeModal = () => {
    setIsModalOpen(false);
    setEditingSubject(null);
  };

  const handleSave = async (e) => {
    e.preventDefault();
    const formData = new FormData(e.target);
    const subjectData = {
      subjectCode: formData.get('subjectCode'),
      subjectName: formData.get('subjectName'),
      subjectType: formData.get('subjectType'),
      maxScore: parseFloat(formData.get('maxScore')),
      isRequired: formData.get('isRequired') === 'true',
      displayOrder: parseInt(formData.get('displayOrder')),
      description: formData.get('description'),
      status: formData.get('status') || 'active'
    };

    try {
      if (editingSubject) {
        await updateSubject(editingSubject.id, subjectData);
      } else {
        await createSubject(subjectData);
      }
      fetchSubjects();
      closeModal();
    } catch (error) {
      console.error('保存科目失败:', error);
      alert('保存失败：' + (error.message || '未知错误'));
    }
  };

  const handleDelete = async (id) => {
    if (await confirmAction({
      title: '删除科目？',
      message: '删除后该科目将从科目列表中移除，此操作不可撤销。',
      confirmText: '删除科目',
      tone: 'danger'
    })) {
      try {
        await deleteSubject(id);
        fetchSubjects();
        setSelectedSubjects(prev => prev.filter(sid => sid !== id));
      } catch (error) {
        console.error('删除科目失败:', error);
        alert('删除失败：' + (error.message || '未知错误'));
      }
    }
  };

  const handleBatchDelete = async () => {
    if (selectedSubjects.length === 0) {
      alert('请先选择要删除的科目');
      return;
    }
    if (await confirmAction({
      title: `删除 ${selectedSubjects.length} 个科目？`,
      message: '选中的科目将被批量删除，此操作不可撤销。',
      confirmText: '批量删除',
      tone: 'danger'
    })) {
      try {
        await batchDeleteSubjects(selectedSubjects);
        fetchSubjects();
        setSelectedSubjects([]);
      } catch (error) {
        console.error('批量删除科目失败:', error);
        alert('批量删除失败：' + (error.message || '未知错误'));
      }
    }
  };

  const handleStatusChange = async (id, newStatus) => {
    try {
      await updateSubjectStatus(id, newStatus);
      fetchSubjects();
    } catch (error) {
      console.error('更新科目状态失败:', error);
      alert('更新状态失败：' + (error.message || '未知错误'));
    }
  };

  return (
    <div className="subject-management">
      <div className="page-header">
        <h2>科目列表</h2>
        <div className="header-actions">
          <button className="btn btn-primary" onClick={() => openModal()}>
            + 新增科目
          </button>
          <button className="btn btn-danger" onClick={handleBatchDelete}>
            批量删除
          </button>
        </div>
      </div>

      <div className="search-area">
        <div className="search-form">
          <div className="form-item">
            <input
              type="text"
              placeholder="科目名称/代码"
              value={searchForm.keyword}
              onChange={(e) => setSearchForm({...searchForm, keyword: e.target.value})}
            />
          </div>
          <div className="form-item">
            <select
              value={searchForm.subjectType}
              onChange={(e) => setSearchForm({...searchForm, subjectType: e.target.value})}
            >
              <option value="">全部类型</option>
              {subjectTypes.map(type => (
                <option key={type} value={type}>{type}</option>
              ))}
            </select>
          </div>
          <div className="form-item">
            <select
              value={searchForm.status}
              onChange={(e) => setSearchForm({...searchForm, status: e.target.value})}
            >
              <option value="">全部状态</option>
              <option value="active">启用</option>
              <option value="inactive">禁用</option>
            </select>
          </div>
          <div className="form-actions">
            <button className="btn btn-primary" onClick={handleSearch}>搜索</button>
            <button className="btn btn-default" onClick={handleReset}>重置</button>
          </div>
        </div>
      </div>

      <div className="data-table-wrapper">
        <table className="data-table">
          <thead>
            <tr>
              <th>
                <input 
                  type="checkbox" 
                  checked={selectedSubjects.length === subjects.length && subjects.length > 0}
                  onChange={handleSelectAll}
                />
              </th>
              <th>科目代码</th>
              <th>科目名称</th>
              <th>科目类型</th>
              <th>最大分数</th>
              <th>是否必修</th>
              <th>显示顺序</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan="9" className="loading-cell">加载中...</td>
              </tr>
            ) : subjects.length === 0 ? (
              <tr>
                <td colSpan="9" className="empty-cell">暂无数据</td>
              </tr>
            ) : (
              subjects.map(subject => (
                <tr key={subject.id}>
                  <td>
                    <input 
                      type="checkbox" 
                      checked={selectedSubjects.includes(subject.id)}
                      onChange={() => handleSelectSubject(subject.id)}
                    />
                  </td>
                  <td>{subject.subjectCode}</td>
                  <td>{subject.subjectName}</td>
                  <td>{subject.subjectType}</td>
                  <td>{subject.maxScore}</td>
                  <td>{subject.isRequired ? '是' : '否'}</td>
                  <td>{subject.displayOrder}</td>
                  <td>
                    <span className={`status-badge ${subject.status}`}>
                      {subject.status === 'active' ? '启用' : '禁用'}
                    </span>
                  </td>
                  <td>
                    <div className="action-btns">
                      <button className="btn-link" onClick={() => openModal(subject)}>编辑</button>
                      <button 
                        className="btn-link" 
                        onClick={() => handleStatusChange(subject.id, subject.status === 'active' ? 'inactive' : 'active')}
                      >
                        {subject.status === 'active' ? '禁用' : '启用'}
                      </button>
                      <button className="btn-link danger" onClick={() => handleDelete(subject.id)}>删除</button>
                    </div>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {isModalOpen && (
        <div className="modal-overlay" onClick={closeModal}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3>{editingSubject ? '编辑科目' : '新增科目'}</h3>
              <button className="modal-close" onClick={closeModal}>×</button>
            </div>
            <form onSubmit={handleSave}>
              <div className="modal-body">
                <div className="form-row">
                  <div className="form-group">
                    <label>科目代码 <span className="required">*</span></label>
                    <input 
                      name="subjectCode" 
                      defaultValue={editingSubject?.subjectCode} 
                      required 
                      disabled={!!editingSubject}
                    />
                  </div>
                  <div className="form-group">
                    <label>科目名称 <span className="required">*</span></label>
                    <input name="subjectName" defaultValue={editingSubject?.subjectName} required />
                  </div>
                </div>
                <div className="form-row">
                  <div className="form-group">
                    <label>科目类型 <span className="required">*</span></label>
                    <select name="subjectType" defaultValue={editingSubject?.subjectType || '智育'} required>
                      {subjectTypes.map(type => (
                        <option key={type} value={type}>{type}</option>
                      ))}
                    </select>
                  </div>
                  <div className="form-group">
                    <label>最大分数 <span className="required">*</span></label>
                    <input 
                      type="number" 
                      name="maxScore" 
                      defaultValue={editingSubject?.maxScore || 100} 
                      required 
                      step="0.1"
                    />
                  </div>
                </div>
                <div className="form-row">
                  <div className="form-group">
                    <label>是否必修</label>
                    <select name="isRequired" defaultValue={editingSubject?.isRequired !== false ? 'true' : 'false'}>
                      <option value="true">是</option>
                      <option value="false">否</option>
                    </select>
                  </div>
                  <div className="form-group">
                    <label>显示顺序</label>
                    <input 
                      type="number" 
                      name="displayOrder" 
                      defaultValue={editingSubject?.displayOrder || 0} 
                    />
                  </div>
                </div>
                <div className="form-row">
                  <div className="form-group full-width">
                    <label>状态</label>
                    <select name="status" defaultValue={editingSubject?.status || 'active'}>
                      <option value="active">启用</option>
                      <option value="inactive">禁用</option>
                    </select>
                  </div>
                </div>
                <div className="form-row">
                  <div className="form-group full-width">
                    <label>描述</label>
                    <textarea 
                      name="description" 
                      defaultValue={editingSubject?.description || ''} 
                      rows="3"
                    />
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

export default SubjectManagement;
