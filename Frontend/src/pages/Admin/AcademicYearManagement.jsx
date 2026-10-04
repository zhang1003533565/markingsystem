import { useEffect, useState } from 'react';
import './CollegeManagement.css';
import './AcademicYearManagement.css';
import {
  createAcademicYear,
  deleteAcademicYear,
  getAcademicYearList,
  setCurrentAcademicYear,
  toggleAcademicYearStatus,
  updateAcademicYear
} from '../../api/admin/academicYear';
import { confirmAction } from '../../components/confirmDialogService';

function AcademicYearManagement() {
  const [academicYears, setAcademicYears] = useState([]);
  const [loading, setLoading] = useState(false);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingAcademicYear, setEditingAcademicYear] = useState(null);
  const [startYear, setStartYear] = useState(new Date().getFullYear());
  const [endYear, setEndYear] = useState(new Date().getFullYear() + 1);

  useEffect(() => {
    fetchAcademicYears();
  }, []);

  const fetchAcademicYears = async () => {
    setLoading(true);
    try {
      const res = await getAcademicYearList();
      const list = Array.isArray(res) ? res : (res.data || []);
      setAcademicYears(list);
    } catch (error) {
      console.error('获取学年列表失败:', error);
    } finally {
      setLoading(false);
    }
  };

  const resolveNextAcademicYearStart = () => {
    const latestEndYear = academicYears.reduce((maxYear, item) => {
      const [, end] = String(item?.name || '').split('-').map((value) => Number(value));
      return Number.isFinite(end) && end > maxYear ? end : maxYear;
    }, new Date().getFullYear());
    return latestEndYear;
  };

  const openModal = (item = null) => {
    setEditingAcademicYear(item);
    const [start, end] = String(item?.name || '').split('-').map((value) => Number(value));
    const nextStartYear = resolveNextAcademicYearStart();
    setStartYear(Number.isFinite(start) && start > 0 ? start : nextStartYear);
    setEndYear(Number.isFinite(end) && end > 0 ? end : nextStartYear + 1);
    setIsModalOpen(true);
  };

  const closeModal = () => {
    setEditingAcademicYear(null);
    setIsModalOpen(false);
  };

  const handleBlockedCurrentAction = (actionText) => {
    alert(`当前学年不能${actionText}。请先将其他学年设为当前学年。`);
  };

  const handleSave = async (e) => {
    e.preventDefault();
    const formData = new FormData(e.target);
    const start = Number(formData.get('startYear'));
    const end = Number(formData.get('endYear'));
    if (!Number.isInteger(start) || !Number.isInteger(end) || start < 2000 || end > 2101 || end !== start + 1) {
      alert('结束年份必须是开始年份的下一年');
      return;
    }
    const nextName = `${start}-${end}`;
    const duplicated = academicYears.some((item) => item.name === nextName && item.id !== editingAcademicYear?.id);
    if (duplicated) {
      alert(`学年 ${nextName} 已存在，请勿重复新增`);
      return;
    }
    const payload = {
      name: nextName,
      status: formData.get('status')
    };

    try {
      if (editingAcademicYear) {
        await updateAcademicYear(editingAcademicYear.id, payload);
      } else {
        await createAcademicYear(payload);
      }
      closeModal();
      fetchAcademicYears();
    } catch (error) {
      console.error('保存学年失败:', error);
      alert(error?.response?.data?.message || '保存学年失败');
    }
  };

  const handleDelete = async (id) => {
    if (!await confirmAction({
      title: '删除学年？',
      message: '删除后该学年将不再可用，请确认没有关联数据需要保留。',
      confirmText: '删除学年',
      tone: 'danger'
    })) {
      return;
    }
    try {
      await deleteAcademicYear(id);
      fetchAcademicYears();
    } catch (error) {
      console.error('删除学年失败:', error);
      alert(error?.response?.data?.message || '删除学年失败');
    }
  };

  const handleToggleStatus = async (id) => {
    try {
      await toggleAcademicYearStatus(id);
      fetchAcademicYears();
    } catch (error) {
      console.error('切换学年状态失败:', error);
      alert(error?.response?.data?.message || '切换学年状态失败');
    }
  };

  const handleSetCurrent = async (id) => {
    try {
      await setCurrentAcademicYear(id);
      fetchAcademicYears();
    } catch (error) {
      console.error('设置当前学年失败:', error);
      alert(error?.response?.data?.message || '设置当前学年失败');
    }
  };

  const currentAcademicYear = academicYears.find((item) => item.current);
  const activeCount = academicYears.filter((item) => item.status === 'active').length;

  return (
    <div className="academic-year-management">
      <div className="academic-year-top-actions">
        <button className="academic-year-primary-btn" onClick={() => openModal()}>新增学年</button>
      </div>

      <div className="academic-year-shell">
        <section className="academic-year-overview">
          <div className="academic-year-stat">
            <span>学年总数</span>
            <strong>{academicYears.length}</strong>
          </div>
          <div className="academic-year-stat">
            <span>当前学年</span>
            <strong>{currentAcademicYear?.name || '未设置'}</strong>
          </div>
          <div className="academic-year-stat">
            <span>启用学年</span>
            <strong>{activeCount}</strong>
          </div>
        </section>

        <section className="academic-year-table-card">
          <table className="academic-year-table">
            <thead>
              <tr>
                <th>学年名称</th>
                <th>当前学年</th>
                <th>状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr>
                  <td colSpan="4" className="academic-year-loading-cell">加载中...</td>
                </tr>
              ) : academicYears.length === 0 ? (
                <tr>
                  <td colSpan="4" className="academic-year-empty-cell">
                    <div className="academic-year-empty-state">
                      <strong>暂无学年</strong>
                      <p>新增一个学年后，再将其设置为当前学年。</p>
                      <button type="button" className="academic-year-primary-btn small" onClick={() => openModal()}>
                        新增学年
                      </button>
                    </div>
                  </td>
                </tr>
              ) : (
                academicYears.map((item) => (
                  <tr key={item.id}>
                    <td>
                      <div className="academic-year-name-cell">
                        <div>
                          <strong>{item.name}</strong>
                          <span>{item.current ? '成绩与申报默认学年' : '普通学年'}</span>
                        </div>
                      </div>
                    </td>
                    <td>
                      {item.current ? (
                        <span className="academic-year-badge current">当前学年</span>
                      ) : (
                        <button className="academic-year-link-btn" onClick={() => handleSetCurrent(item.id)}>
                          设为当前
                        </button>
                      )}
                    </td>
                    <td>
                      <span className={`academic-year-badge ${item.status}`}>
                        {item.status === 'active' ? '启用' : '停用'}
                      </span>
                    </td>
                    <td>
                      <div className="academic-year-actions">
                        <button className="academic-year-action edit" onClick={() => openModal(item)}>
                          编辑
                        </button>
                        <button
                          className="academic-year-action toggle"
                          onClick={() => item.current ? handleBlockedCurrentAction('停用') : handleToggleStatus(item.id)}
                        >
                          {item.status === 'active' ? '停用' : '启用'}
                        </button>
                        <button
                          className="academic-year-action delete"
                          onClick={() => item.current ? handleBlockedCurrentAction('删除') : handleDelete(item.id)}
                        >
                          删除
                        </button>
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </section>
      </div>

      {isModalOpen && (
        <div className="academic-year-modal-overlay" onClick={closeModal}>
          <div className="academic-year-modal" onClick={(e) => e.stopPropagation()}>
            <div className="academic-year-modal-header">
              <h3>{editingAcademicYear ? '编辑学年' : '新增学年'}</h3>
              <button className="academic-year-modal-close" onClick={closeModal}>关闭</button>
            </div>
            <form onSubmit={handleSave}>
              <div className="academic-year-modal-body">
                <div className="academic-year-form-row">
                  <div className="academic-year-form-group">
                    <label>学年名称 <span className="required">*</span></label>
                    <div className="academic-year-picker">
                      <div className="academic-year-range-inputs">
                        <div className="academic-year-year-input">
                          <label>开始年份</label>
                          <input
                            name="startYear"
                            type="number"
                            value={startYear}
                            onChange={(e) => {
                              const nextYear = Number(e.target.value) || new Date().getFullYear();
                              setStartYear(nextYear);
                              setEndYear(nextYear + 1);
                            }}
                            min="2000"
                            max="2100"
                            required
                          />
                        </div>
                        <span className="academic-year-range-separator">-</span>
                        <div className="academic-year-year-input">
                          <label>结束年份</label>
                          <input
                            name="endYear"
                            type="number"
                            value={endYear}
                            onChange={(e) => setEndYear(Number(e.target.value) || startYear + 1)}
                            min="2001"
                            max="2101"
                            required
                          />
                        </div>
                      </div>
                      <div className="academic-year-preview">保存名称：{startYear || '-'}-{endYear || '-'}</div>
                      {!editingAcademicYear ? (
                        <div className="field-hint">新增时会根据已有学年自动推荐下一个学年。</div>
                      ) : null}
                    </div>
                  </div>
                </div>
                <div className="academic-year-form-row">
                  <div className="academic-year-form-group">
                    <label>启用状态</label>
                    <select name="status" defaultValue={editingAcademicYear?.status || 'active'} disabled={Boolean(editingAcademicYear?.current)}>
                      <option value="active">启用</option>
                      <option value="inactive">停用</option>
                    </select>
                    {editingAcademicYear?.current ? (
                      <div className="field-hint">当前学年不能停用，请先将其他学年设为当前学年。</div>
                    ) : null}
                  </div>
                </div>
              </div>
              <div className="academic-year-modal-footer">
                <button type="button" className="academic-year-secondary-btn" onClick={closeModal}>取消</button>
                <button type="submit" className="academic-year-primary-btn">保存</button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}

export default AcademicYearManagement;
