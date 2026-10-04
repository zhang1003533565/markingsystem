import { useEffect, useMemo, useState } from 'react';
import './ClassManagement.css';
import {
  getClassList,
  createClass,
  updateClass
} from '../../api/admin/class';
import { getMajorOptions } from '../../api/admin/major';
import { getTeacherList } from '../../api/admin/user';
import { getActiveGradeList } from '../../api/admin/grade';
import { getStoredUser } from '../../utils/authStorage';

function ClassManagement() {
  const user = getStoredUser();
  const isTeacher = user.role === 'TEACHER';
  const [classes, setClasses] = useState([]);
  const [majors, setMajors] = useState([]);
  const [majorsLoaded, setMajorsLoaded] = useState(false);
  const [majorsLoading, setMajorsLoading] = useState(false);
  const [teachers, setTeachers] = useState([]);
  const [grades, setGrades] = useState([]);
  const [loading, setLoading] = useState(false);
  const [filters, setFilters] = useState({
    collegeId: '',
    majorId: '',
    grade: '',
    teacherId: ''
  });
  const [filterSearch, setFilterSearch] = useState({
    college: '',
    major: '',
    teacher: ''
  });
  const [formState, setFormState] = useState({
    name: '',
    majorCollegeId: '',
    majorId: '',
    grade: '',
    teacherId: '',
    status: 'active'
  });
  const [newClassNames, setNewClassNames] = useState(['']);
  const [selectorSearch, setSelectorSearch] = useState({
    teacher: ''
  });
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingClass, setEditingClass] = useState(null);
  const [isDetailOpen, setIsDetailOpen] = useState(false);
  const [detailClass, setDetailClass] = useState(null);
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);

  useEffect(() => {
    fetchClasses();
    if (!isTeacher) {
      fetchTeachers();
    }
    fetchGrades();
  }, []);

  useEffect(() => {
    setCurrentPage(1);
  }, [filters]);

  const handleReset = () => {
    setFilters({
      collegeId: '',
      majorId: '',
      grade: '',
      teacherId: ''
    });
    setFilterSearch({
      college: '',
      major: '',
      teacher: ''
    });
  };

  const fetchMajors = async () => {
    if (majorsLoaded || majorsLoading) return;
    setMajorsLoading(true);
    try {
      const res = await getMajorOptions();
      setMajors(Array.isArray(res) ? res : (res.data || []));
      setMajorsLoaded(true);
    } catch (error) {
      console.error('获取专业列表失败:', error);
    } finally {
      setMajorsLoading(false);
    }
  };

  const ensureMajorOptions = () => {
    if (!majorsLoaded && !majorsLoading) {
      fetchMajors();
    }
  };

  const fetchTeachers = async () => {
    try {
      const res = await getTeacherList();
      setTeachers(Array.isArray(res) ? res : (res.data || []));
    } catch (error) {
      console.error('获取老师列表失败:', error);
    }
  };

  const fetchGrades = async () => {
    try {
      const res = await getActiveGradeList();
      setGrades(Array.isArray(res) ? res : (res.data || []));
    } catch (error) {
      console.error('获取年级列表失败:', error);
    }
  };

  const fetchClasses = async () => {
    setLoading(true);
    try {
      const res = await getClassList();
      setClasses(Array.isArray(res) ? res : (res.data || []));
    } catch (error) {
      console.error('获取班级列表失败:', error);
    } finally {
      setLoading(false);
    }
  };

  const colleges = useMemo(() => {
    const map = new Map();
    majors.forEach((major) => {
      if (major.collegeId == null) return;
      map.set(major.collegeId, {
        id: major.collegeId,
        name: major.collegeName || `学院 ${major.collegeId}`
      });
    });
    return Array.from(map.values()).sort((a, b) => String(a.name).localeCompare(String(b.name), 'zh-CN'));
  }, [majors]);

  const getCollegeName = (collegeId) => majors.find((major) => String(major.collegeId) === String(collegeId))?.collegeName || '-';

  const getMajorName = (majorId) => majors.find((m) => m.id === majorId)?.name || '-';

  const getClassCollegeName = (cls) => (
    cls?.major?.college?.name
    || cls?.major?.collegeName
    || getCollegeName(cls?.major?.collegeId || cls?.collegeId)
  );

  const getClassMajorName = (cls) => (
    cls?.major?.name
    || getMajorName(cls?.majorId)
  );

  const getTeacherName = (teacher) => {
    if (!teacher) return '-';
    return `${teacher.nickname || teacher.username || '-'}`;
  };

  const openModal = (cls = null) => {
    ensureMajorOptions();
    const currentTeacherId = String(user.teacherId || user.id || '');
    setEditingClass(cls);
    setFormState({
      name: cls?.name || '',
      majorCollegeId: cls?.major?.collegeId ? String(cls.major.collegeId) : (cls?.collegeId ? String(cls.collegeId) : ''),
      majorId: cls?.majorId ? String(cls.majorId) : '',
      grade: cls?.grade || '',
      teacherId: cls?.teacherId ? String(cls.teacherId) : (isTeacher ? currentTeacherId : ''),
      status: cls?.status || 'active'
    });
    setNewClassNames(['']);
    setSelectorSearch({ teacher: '' });
    setIsModalOpen(true);
  };

  const closeModal = () => {
    const currentTeacherId = String(user.teacherId || user.id || '');
    setIsModalOpen(false);
    setEditingClass(null);
    setFormState({
      name: '',
      majorCollegeId: '',
      majorId: '',
      grade: '',
      teacherId: isTeacher ? currentTeacherId : '',
      status: 'active'
    });
    setNewClassNames(['']);
  };

  const openDetail = (cls) => {
    setDetailClass(cls);
    setIsDetailOpen(true);
  };

  const closeDetail = () => {
    setIsDetailOpen(false);
    setDetailClass(null);
  };

  const formatDateTime = (value) => {
    if (!value) return '-';
    try {
      const date = new Date(value);
      if (Number.isNaN(date.getTime())) return value;
      const pad = (n) => String(n).padStart(2, '0');
      return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
    } catch {
      return value;
    }
  };

  const handleSave = async (e) => {
    e.preventDefault();
    const baseClassData = {
      majorId: parseInt(formState.majorId, 10),
      grade: formState.grade.trim(),
      teacherId: parseInt(formState.teacherId, 10),
      status: formState.status
    };

    try {
      if (editingClass) {
        await updateClass(editingClass.id, {
          ...baseClassData,
          name: formState.name.trim()
        });
      } else {
        const names = newClassNames.map((name) => name.trim()).filter(Boolean);
        if (names.length === 0) {
          window.alert('请至少填写一个班级名称');
          return;
        }
        for (const name of names) {
          await createClass({
            ...baseClassData,
            name
          });
        }
      }
      fetchClasses();
      closeModal();
    } catch (error) {
      console.error('保存班级失败:', error);
      window.alert(error?.response?.data?.message || '保存班级失败');
    }
  };

  const handleClassNameRowChange = (index, value) => {
    setNewClassNames((prev) => prev.map((item, idx) => (idx === index ? value : item)));
  };

  const availableMajors = useMemo(() => {
    return formState.majorCollegeId
      ? majors.filter((major) => String(major.collegeId) === formState.majorCollegeId)
      : majors;
  }, [majors, formState.majorCollegeId]);

  const filteredColleges = colleges;

  const filteredTeachers = useMemo(() => {
    const keyword = selectorSearch.teacher.trim().toLowerCase();
    if (!keyword) return teachers;
    return teachers.filter((teacher) => {
      const label = `${teacher.id} ${teacher.nickname || ''} ${teacher.username || ''}`.toLowerCase();
      return label.includes(keyword);
    });
  }, [teachers, selectorSearch.teacher]);

  const updateFormValue = (field, value) => {
    setFormState((prev) => {
      if (field === 'majorCollegeId') {
        const nextMajorId = value && majors.some((major) => String(major.id) === prev.majorId && String(major.collegeId) === value)
          ? prev.majorId
          : '';
        return { ...prev, majorCollegeId: value, majorId: nextMajorId };
      }
      return { ...prev, [field]: value };
    });
  };

  const classMajorOptions = useMemo(() => {
    const map = new Map();
    classes.forEach((cls) => {
      if (!cls.majorId) return;
      const collegeId = cls.major?.collegeId || cls.collegeId;
      map.set(cls.majorId, {
        id: cls.majorId,
        name: getClassMajorName(cls),
        collegeId,
        collegeName: getClassCollegeName(cls)
      });
    });
    return Array.from(map.values());
  }, [classes, majors]);

  const listMajors = useMemo(() => {
    const source = majorsLoaded ? majors : classMajorOptions;
    const byCollege = filters.collegeId
      ? source.filter((major) => String(major.collegeId) === filters.collegeId)
      : source;
    const keyword = filterSearch.major.trim().toLowerCase();
    if (!keyword) return byCollege;
    return byCollege.filter((major) => {
      const collegeName = (major.collegeName || getCollegeName(major.collegeId)).toLowerCase();
      return major.name.toLowerCase().includes(keyword) || collegeName.includes(keyword);
    });
  }, [majors, majorsLoaded, classMajorOptions, filters.collegeId, filterSearch.major, colleges]);

  const listTeachers = useMemo(() => {
    const keyword = filterSearch.teacher.trim().toLowerCase();
    if (!keyword) return teachers;
    return teachers.filter((teacher) => getTeacherName(teacher).toLowerCase().includes(keyword));
  }, [teachers, filterSearch.teacher]);

  const filteredClasses = useMemo(() => classes.filter((cls) => {
    const collegeId = cls.major?.collegeId ? String(cls.major.collegeId) : (cls.collegeId ? String(cls.collegeId) : '');
    if (filters.collegeId && collegeId !== filters.collegeId) return false;
    if (filters.majorId && String(cls.majorId) !== filters.majorId) return false;
    if (filters.grade && String(cls.grade || '') !== filters.grade.trim()) return false;
    if (filters.teacherId && String(cls.teacherId) !== filters.teacherId) return false;
    return true;
  }), [classes, filters]);

  const totalPages = Math.max(1, Math.ceil(filteredClasses.length / pageSize));
  const safeCurrentPage = Math.min(currentPage, totalPages);
  const pagedClasses = filteredClasses.slice((safeCurrentPage - 1) * pageSize, safeCurrentPage * pageSize);
  return (
    <div className="class-management">
      <div className="filter-section">
        <div className="filter-item full-width">
          <label>年级筛选</label>
          <select
            value={filters.grade}
            onChange={(e) => setFilters((prev) => ({ ...prev, grade: e.target.value }))}
          >
            <option value="">全部年级</option>
            {grades.map((grade) => (
              <option key={grade.id} value={grade.name}>{grade.name}级</option>
            ))}
          </select>
        </div>
        {!isTeacher ? (
          <>
            <div className="filter-item half-width">
              <label>专业筛选</label>
              <div className="filter-row">
                <div className="search-input">
                  <input
                    value={filterSearch.major}
                    onFocus={ensureMajorOptions}
                    onChange={(e) => {
                      ensureMajorOptions();
                      setFilterSearch((prev) => ({ ...prev, major: e.target.value }));
                    }}
                    placeholder="搜索专业"
                  />
                </div>
                <select
                  value={filters.majorId}
                  onFocus={ensureMajorOptions}
                  onChange={(e) => setFilters((prev) => ({ ...prev, majorId: e.target.value }))}
                >
                  <option value="">全部专业</option>
                  {listMajors.map((major) => (
                    <option key={major.id} value={major.id}>{major.name}</option>
                  ))}
                </select>
              </div>
            </div>
            <div className="filter-item half-width">
              <label>老师筛选</label>
              <div className="filter-row">
                <div className="search-input">
                  <input
                    value={filterSearch.teacher}
                    onChange={(e) => setFilterSearch((prev) => ({ ...prev, teacher: e.target.value }))}
                    placeholder="搜索老师"
                  />
                </div>
                <select
                  value={filters.teacherId}
                  onChange={(e) => setFilters((prev) => ({ ...prev, teacherId: e.target.value }))}
                >
                  <option value="">全部老师</option>
                  {listTeachers.map((teacher) => (
                    <option key={teacher.id} value={teacher.id}>{getTeacherName(teacher)}</option>
                  ))}
                </select>
              </div>
            </div>
          </>
        ) : null}

        <div className="filter-buttons">
          <button className="btn btn-default class-filter-reset" onClick={handleReset}>
            {'\u91cd\u7f6e'}
          </button>
          <button className="btn btn-primary class-filter-search" type="button">
            {'\u67e5\u8be2'}
          </button>
        </div>
      </div>

      <div className="data-table-wrapper">
        <div className="class-table-toolbar">
          <button type="button" className="btn btn-primary" onClick={() => openModal()}>
            <span>{'+ \u65b0\u589e\u73ed\u7ea7'}</span>
          </button>
        </div>
        <table className="data-table">
          <thead>
            <tr>
              <th>班级名称</th>
              <th>所属学院</th>
              <th>所属专业</th>
              <th>年级</th>
              <th>所属老师</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan="7" className="loading-cell">
                  <div className="loading-spinner">加载中...</div>
                </td>
              </tr>
            ) : filteredClasses.length === 0 ? (
              <tr>
                <td colSpan="7" className="empty-cell">
                  <div className="empty-state">
                    <p>暂无班级数据</p>
                  </div>
                </td>
              </tr>
            ) : (
              pagedClasses.map((cls) => (
                <tr key={cls.id}>
                  <td>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                      <strong className="class-name">{cls.name}</strong>
                    </div>
                  </td>
                  <td>
                    <span className="info-tag college-tag">{getClassCollegeName(cls)}</span>
                  </td>
                  <td>
                    <span className="info-tag major-tag" style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}>
                      {getClassMajorName(cls)}
                    </span>
                  </td>
                  <td className="grade-cell">{cls.grade}级</td>
                  <td>
                    <span className="teacher-name">{getTeacherName(cls.teacher)}</span>
                  </td>
                  <td>
                    <span className={`status-badge ${cls.status}`}>
                      {cls.status === 'active' ? '启用' : '停用'}
                    </span>
                  </td>
                  <td>
                    <div className="action-btns">
                      <button className="action-btn edit" onClick={() => openModal(cls)} title="edit">
                        <span>{'\u7f16\u8f91'}</span>
                      </button>
                      <button className="action-btn detail" type="button" title="detail" onClick={() => openDetail(cls)}>
                        <span>{'\u8be6\u60c5'}</span>
                      </button>
                    </div>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
        <div className="class-pagination">
          <span>{'\u5171 '}{filteredClasses.length}{' \u6761'}</span>
          <div className="class-pagination-controls">
            <select value={pageSize} onChange={(e) => { setPageSize(Number(e.target.value)); setCurrentPage(1); }}>
              {[10, 20, 50].map((size) => (
                <option key={size} value={size}>{size}{'\u6761/\u9875'}</option>
              ))}
            </select>
            <button type="button" disabled={safeCurrentPage === 1} onClick={() => setCurrentPage((prev) => Math.max(1, prev - 1))}>{'<'}</button>
            <button type="button" className="active">{safeCurrentPage}</button>
            <button type="button" disabled={safeCurrentPage === totalPages} onClick={() => setCurrentPage((prev) => Math.min(totalPages, prev + 1))}>{'>'}</button>
            <span>{'\u524d\u5f80'}</span>
            <input
              value={safeCurrentPage}
              onChange={(e) => {
                const nextPage = Number(e.target.value);
                if (!Number.isNaN(nextPage)) {
                  setCurrentPage(Math.min(totalPages, Math.max(1, nextPage || 1)));
                }
              }}
            />
            <span>{'\u9875'}</span>
          </div>
        </div>
      </div>

      {isModalOpen && (
        <div className="modal-overlay" onClick={closeModal}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3>{editingClass ? '编辑班级' : '新增班级'}</h3>
              <button className="modal-close" onClick={closeModal}>
                x
              </button>
            </div>
            <form onSubmit={handleSave}>
              <div className="modal-body">
                <div className="form-row">
                  <div className="form-group">
                    <label>{'\u73ed\u7ea7\u540d\u79f0'} <span className="required">*</span></label>
                    {editingClass ? (
                      <input
                        name="name"
                        value={formState.name}
                        onChange={(e) => updateFormValue('name', e.target.value)}
                        placeholder={'\u5982\uff1a\u8f6f\u5de52401'}
                        required
                      />
                    ) : (
                      <input
                        value={newClassNames[0] || ''}
                        onChange={(e) => handleClassNameRowChange(0, e.target.value)}
                        placeholder={'\u5982\uff1a\u8f6f\u5de52401'}
                        required
                      />
                    )}
                  </div>

                  <div className="form-group">
                    <label>年级 <span className="required">*</span></label>
                    <select
                      value={formState.grade}
                      onChange={(e) => updateFormValue('grade', e.target.value)}
                      required
                    >
                      <option value="">请选择年级</option>
                      {grades.map((grade) => (
                        <option key={grade.id} value={grade.name}>{grade.name}级</option>
                      ))}
                    </select>
                  </div>
                </div>

                <div className="form-row">
                  <div className="form-group">
                    <label>学院筛选</label>
                    <select
                      value={formState.majorCollegeId}
                      onFocus={ensureMajorOptions}
                      onChange={(e) => updateFormValue('majorCollegeId', e.target.value)}
                    >
                      <option value="">全部学院</option>
                      {filteredColleges.map((college) => (
                        <option key={college.id} value={college.id}>{college.name}</option>
                      ))}
                    </select>
                  </div>
                  <div className="form-group">
                    <label>所属专业 <span className="required">*</span></label>
                    <select
                      name="majorId"
                      value={formState.majorId}
                      onFocus={ensureMajorOptions}
                      onChange={(e) => updateFormValue('majorId', e.target.value)}
                      required
                    >
                      <option value="">请选择专业</option>
                      {availableMajors.map((major) => (
                        <option key={major.id} value={major.id}>{major.name} · {getCollegeName(major.collegeId)}</option>
                      ))}
                    </select>
                  </div>
                </div>

                <div className="form-row">
                  <div className="form-group">
                    <label>所属老师 <span className="required">*</span></label>
                    {isTeacher ? (
                      <input
                        value={getTeacherName({ nickname: user.nickname, username: user.username })}
                        readOnly
                      />
                    ) : (
                      <>
                        <div className="input-with-icon">
                          <input
                            className="selector-search"
                            value={selectorSearch.teacher}
                            onChange={(e) => setSelectorSearch((prev) => ({ ...prev, teacher: e.target.value }))}
                            placeholder="搜索老师ID、姓名或账号"
                          />
                        </div>
                        <select
                          name="teacherId"
                          value={formState.teacherId}
                          onChange={(e) => updateFormValue('teacherId', e.target.value)}
                          required
                        >
                          <option value="">请选择老师</option>
                          {filteredTeachers.map((teacher) => (
                            <option key={teacher.id} value={teacher.id}>
                              {getTeacherName(teacher)}
                            </option>
                          ))}
                        </select>
                      </>
                    )}
                  </div>
                  <div className="form-group">
                    <label>状态</label>
                    <select
                      name="status"
                      value={formState.status}
                      onChange={(e) => updateFormValue('status', e.target.value)}
                    >
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

      {isDetailOpen && detailClass && (
        <div className="modal-overlay" onClick={closeDetail}>
          <div className="modal class-detail-modal" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3>{'\u73ed\u7ea7\u8be6\u60c5'}</h3>
              <button className="modal-close" onClick={closeDetail}>
                x
              </button>
            </div>
            <div className="modal-body">
              <div className="detail-grid">
                <div className="detail-item">
                  <span className="detail-label">{'\u73ed\u7ea7\u540d\u79f0'}</span>
                  <span className="detail-value">{detailClass.name || '-'}</span>
                </div>
                <div className="detail-item">
                  <span className="detail-label">{'\u73ed\u7ea7 ID'}</span>
                  <span className="detail-value">{detailClass.id ?? '-'}</span>
                </div>
                <div className="detail-item">
                  <span className="detail-label">{'\u6240\u5c5e\u5b66\u9662'}</span>
                  <span className="detail-value">{getClassCollegeName(detailClass)}</span>
                </div>
                <div className="detail-item">
                  <span className="detail-label">{'\u6240\u5c5e\u4e13\u4e1a'}</span>
                  <span className="detail-value">{getClassMajorName(detailClass)}</span>
                </div>
                <div className="detail-item">
                  <span className="detail-label">{'\u5e74\u7ea7'}</span>
                  <span className="detail-value">{detailClass.grade ? `${detailClass.grade}\u7ea7` : '-'}</span>
                </div>
                <div className="detail-item">
                  <span className="detail-label">{'\u6240\u5c5e\u8001\u5e08'}</span>
                  <span className="detail-value">{getTeacherName(detailClass.teacher)}</span>
                </div>
                <div className="detail-item">
                  <span className="detail-label">{'\u72b6\u6001'}</span>
                  <span className="detail-value">
                    <span className={`status-badge ${detailClass.status}`}>
                      {detailClass.status === 'active' ? '\u542f\u7528' : '\u505c\u7528'}
                    </span>
                  </span>
                </div>
                <div className="detail-item">
                  <span className="detail-label">{'\u521b\u5efa\u65f6\u95f4'}</span>
                  <span className="detail-value">{formatDateTime(detailClass.createdAt)}</span>
                </div>
                <div className="detail-item detail-item-full">
                  <span className="detail-label">{'\u66f4\u65b0\u65f6\u95f4'}</span>
                  <span className="detail-value">{formatDateTime(detailClass.updatedAt)}</span>
                </div>
              </div>
            </div>
            <div className="modal-footer">
              <button type="button" className="btn btn-default" onClick={closeDetail}>{'\u5173\u95ed'}</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

export default ClassManagement;
