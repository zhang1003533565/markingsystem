import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import './StudentManagement.css'
import {
  getStudentList,
  createStudent,
  updateStudent,
  deleteStudent,
  resetStudentPassword,
  batchDeleteStudents,
} from '../../api/admin/student'
import { getCollegeList } from '../../api/admin/college'
import { getMajorList } from '../../api/admin/major'
import { getClassOptions } from '../../api/admin/class'
import { getActiveGradeList } from '../../api/admin/grade'
import { getStoredUser } from '../../utils/authStorage'
import { confirmAction, noticeAction } from '../../components/confirmDialogService'
import {
  buildStudentListParams,
  committeeFilterOptions,
} from '../../utils/studentManagementFilters'

function StudentManagement() {
  const navigate = useNavigate()
  const user = getStoredUser()
  const isTeacher = user.role === 'TEACHER'
  const [students, setStudents] = useState([])
  const [colleges, setColleges] = useState([])
  const [majors, setMajors] = useState([])
  const [classes, setClasses] = useState([])
  const [grades, setGrades] = useState([])
  const [loading, setLoading] = useState(false)
  const [searchForm, setSearchForm] = useState({
    keyword: '',
    college: '',
    major: '',
    grade: '',
    className: '',
    committeeFilter: 'ALL',
  })
  const [appliedSearchForm, setAppliedSearchForm] = useState({
    keyword: '',
    college: '',
    major: '',
    grade: '',
    className: '',
    committeeFilter: 'ALL',
  })
  const [currentPage, setCurrentPage] = useState(1)
  const [pageSize, setPageSize] = useState(10)
  const [totalElements, setTotalElements] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [reloadToken, setReloadToken] = useState(0)
  const [selectedStudents, setSelectedStudents] = useState([])
  const [isDrawerOpen, setIsDrawerOpen] = useState(false)
  const [editingStudent, setEditingStudent] = useState(null)
  const [modalGrade, setModalGrade] = useState('')
  const [modalForm, setModalForm] = useState({
    studentNo: '',
    name: '',
    className: '',
    classCommittee: false,
  })

  useEffect(() => {
    fetchColleges()
    fetchMajors()
    fetchClasses()
    fetchGrades()
  }, [])

  useEffect(() => {
    const nextForm = { ...appliedSearchForm }
    fetchStudents(nextForm, currentPage)
  }, [currentPage, appliedSearchForm, pageSize, reloadToken])

  useEffect(() => {
    setSelectedStudents([])
  }, [students])

  const fetchColleges = async () => {
    try {
      const res = await getCollegeList()
      setColleges(Array.isArray(res) ? res : res.data || [])
    } catch (error) {
      console.error('获取学院列表失败:', error)
    }
  }

  const fetchMajors = async () => {
    try {
      const res = await getMajorList()
      setMajors(Array.isArray(res) ? res : res.data || [])
    } catch (error) {
      console.error('获取专业列表失败:', error)
    }
  }

  const fetchClasses = async () => {
    try {
      const res = await getClassOptions()
      setClasses(Array.isArray(res) ? res : res.data || [])
    } catch (error) {
      console.error('获取班级列表失败:', error)
    }
  }

  const fetchGrades = async () => {
    try {
      const res = await getActiveGradeList()
      setGrades(Array.isArray(res) ? res : res.data || [])
    } catch (error) {
      console.error('获取年级列表失败:', error)
    }
  }

  const fetchStudents = async (nextSearchForm = searchForm, nextPage = currentPage) => {
    setLoading(true)
    try {
      const params = buildStudentListParams({
        searchForm: nextSearchForm,
        page: nextPage,
        pageSize,
      })
      const res = await getStudentList(params)
      const pageData = res || { content: [], totalElements: 0, totalPages: 0 }
      setStudents(pageData.content || [])
      setTotalElements(pageData.totalElements || 0)
      setTotalPages(pageData.totalPages || 0)
      if (
        (pageData.content || []).length === 0 &&
        (pageData.totalElements || 0) > 0 &&
        nextPage > 1
      ) {
        const safePage = Math.max(pageData.totalPages || 1, 1)
        if (nextPage > safePage) {
          setCurrentPage(safePage)
        }
      }
    } catch (error) {
      console.error('获取学生列表失败:', error)
    } finally {
      setLoading(false)
    }
  }

  const teacherScopedClasses = useMemo(
    () =>
      isTeacher
        ? classes.filter((cls) => String(cls.teacherId) === String(user.teacherId || user.id))
        : classes,
    [classes, isTeacher, user.id, user.teacherId],
  )

  const classMetaMap = useMemo(() => {
    const map = new Map()
    classes.forEach((cls) => {
      const major = majors.find((item) => item.id === cls.majorId) || null
      const college = major ? colleges.find((item) => item.id === major.collegeId) || null : null
      map.set(cls.name, {
        grade: cls.grade || '',
        majorName: major?.name || '',
        collegeName: college?.name || '',
        classStatus: cls.status || '',
        teacherId: cls.teacherId,
      })
    })
    return map
  }, [classes, majors, colleges])

  const gradeOptions = useMemo(
    () =>
      grades
        .map((grade) => String(grade.name || '').trim())
        .filter(Boolean)
        .sort((a, b) => Number(b) - Number(a)),
    [grades],
  )

  const searchMajorOptions = useMemo(() => {
    const availableMajorIds = new Set(
      teacherScopedClasses.map((cls) => cls.majorId).filter(Boolean),
    )
    return majors
      .filter((major) => major.status !== 'inactive')
      .filter((major) => !availableMajorIds.size || availableMajorIds.has(major.id))
      .filter((major) => {
        if (!searchForm.college) return true
        const college = colleges.find((item) => item.id === major.collegeId)
        return college?.name === searchForm.college
      })
      .sort((a, b) => String(a.name || '').localeCompare(String(b.name || ''), 'zh-CN'))
  }, [colleges, majors, searchForm.college, teacherScopedClasses])

  const filteredSearchClasses = useMemo(
    () =>
      teacherScopedClasses.filter((cls) => {
        if (searchForm.grade && String(cls.grade || '') !== searchForm.grade) return false
        if (searchForm.college) {
          const major = majors.find((item) => item.id === cls.majorId)
          const college = major ? colleges.find((item) => item.id === major.collegeId) : null
          if (!college || college.name !== searchForm.college) return false
        }
        if (searchForm.major) {
          const major = majors.find((item) => item.id === cls.majorId)
          if (!major || major.name !== searchForm.major) return false
        }
        return cls.status !== 'inactive'
      }),
    [
      teacherScopedClasses,
      searchForm.grade,
      searchForm.college,
      searchForm.major,
      majors,
      colleges,
    ],
  )

  const filteredModalClasses = useMemo(
    () =>
      teacherScopedClasses.filter((cls) => {
        if (modalGrade && String(cls.grade || '') !== modalGrade) return false
        return cls.status !== 'inactive'
      }),
    [teacherScopedClasses, modalGrade],
  )

  const selectedClass =
    teacherScopedClasses.find((cls) => cls.name === modalForm.className) ||
    classes.find((cls) => cls.name === modalForm.className)
  const selectedMajor = selectedClass
    ? majors.find((major) => major.id === selectedClass.majorId)
    : null
  const selectedCollege = selectedMajor
    ? colleges.find((college) => college.id === selectedMajor.collegeId)
    : null

  const decoratedStudents = useMemo(
    () =>
      students.map((student) => {
        const meta = classMetaMap.get(student.className) || {}
        return {
          ...student,
          grade: meta.grade || '-',
          majorName: meta.majorName || '-',
          collegeName: meta.collegeName || '-',
        }
      }),
    [students, classMetaMap],
  )

  const paginationNumbers = useMemo(() => {
    if (totalPages <= 5) {
      return Array.from({ length: totalPages }, (_, index) => index + 1)
    }
    const pages = new Set([1, totalPages, currentPage - 1, currentPage, currentPage + 1])
    return Array.from(pages)
      .filter((page) => page >= 1 && page <= totalPages)
      .sort((a, b) => a - b)
  }, [currentPage, totalPages])

  const handleSearch = () => {
    setAppliedSearchForm({ ...searchForm })
    setCurrentPage(1)
  }

  const handleReset = () => {
    const resetForm = {
      keyword: '',
      college: '',
      major: '',
      grade: '',
      className: '',
      committeeFilter: 'ALL',
    }
    setSearchForm(resetForm)
    setAppliedSearchForm(resetForm)
    setCurrentPage(1)
  }

  const handleSelectStudent = (id) => {
    setSelectedStudents((prev) =>
      prev.includes(id) ? prev.filter((sid) => sid !== id) : [...prev, id],
    )
  }

  const handleSelectAll = () => {
    if (selectedStudents.length === decoratedStudents.length) {
      setSelectedStudents([])
    } else {
      setSelectedStudents(decoratedStudents.map((student) => student.id))
    }
  }

  const openDrawer = (student = null) => {
    const nextClassName = student?.className || ''
    const matchedClass =
      teacherScopedClasses.find((cls) => cls.name === nextClassName) ||
      classes.find((cls) => cls.name === nextClassName)
    setEditingStudent(student)
    setModalGrade(matchedClass?.grade || '')
    setModalForm({
      studentNo: student?.studentNo || '',
      name: student?.name || '',
      className: nextClassName,
      classCommittee: Boolean(student?.classCommittee),
    })
    setIsDrawerOpen(true)
  }

  const closeDrawer = () => {
    setIsDrawerOpen(false)
    setEditingStudent(null)
    setModalGrade('')
    setModalForm({
      studentNo: '',
      name: '',
      className: '',
      classCommittee: false,
    })
  }

  const handleSave = async (e) => {
    e.preventDefault()
    const studentData = {
      studentNo: modalForm.studentNo.trim(),
      name: modalForm.name.trim(),
      className: modalForm.className,
      classCommittee: modalForm.classCommittee,
    }

    try {
      if (editingStudent) {
        await updateStudent(editingStudent.id, studentData)
      } else {
        await createStudent(studentData)
      }
      setReloadToken((prev) => prev + 1)
      closeDrawer()
    } catch (error) {
      console.error('保存学生失败:', error)
      const status = error?.response?.status
      const reason = error?.response?.data?.message
        || (status >= 500 ? '服务器处理学生数据时发生异常' : '学生信息保存失败')
      await noticeAction({
        title: editingStudent ? '学生信息修改失败' : '学生创建失败',
        message: reason,
        detail: status >= 500
          ? `请求状态：HTTP ${status}。服务器未返回可识别的业务原因，请检查后端运行日志。`
          : '请根据以上原因修改学生信息后重新保存。',
        tone: 'danger',
        confirmText: '我知道了',
      })
    }
  }

  const handleDelete = async (id) => {
    if (!await confirmAction({
      title: '删除学生？',
      message: '删除后该学生账号及关联信息可能无法恢复。',
      confirmText: '删除学生',
      tone: 'danger',
    })) {
      return
    }
    try {
      await deleteStudent(id)
      setReloadToken((prev) => prev + 1)
      setSelectedStudents((prev) => prev.filter((sid) => sid !== id))
    } catch (error) {
      console.error('删除学生失败:', error)
    }
  }

  const handleResetPassword = async (student) => {
    if (!await confirmAction({
      title: '重置学生密码？',
      message: `${student.name || student.studentNo} 的登录密码将被重置为该学生账号。`,
      confirmText: '确认重置',
      tone: 'warning',
    })) {
      return
    }
    try {
      const response = await resetStudentPassword(student.id)
      const account =
        response?.account || response?.data?.account || student.studentNo || '学生账号'
      window.alert(`密码已重置为账号：${account}`)
    } catch (error) {
      console.error('重置学生密码失败:', error)
      window.alert(error?.response?.data?.message || '重置学生密码失败')
    }
  }

  const handleBatchDelete = async () => {
    if (selectedStudents.length === 0) {
      alert('请先选择要删除的学生')
      return
    }
    if (!await confirmAction({
      title: `删除 ${selectedStudents.length} 名学生？`,
      message: '选中的学生账号及关联信息将被批量删除。',
      confirmText: '批量删除',
      tone: 'danger',
    })) {
      return
    }
    try {
      await batchDeleteStudents(selectedStudents)
      setReloadToken((prev) => prev + 1)
      setSelectedStudents([])
    } catch (error) {
      console.error('批量删除学生失败:', error)
    }
  }

  return (
    <div className="student-management-page">
      <section className="student-filter-card">
        <div className="student-filter-grid">
          <label className="student-field">
            <span>学号/姓名</span>
            <input
              type="text"
              placeholder="请输入学号或姓名"
              value={searchForm.keyword}
              onChange={(e) => setSearchForm({ ...searchForm, keyword: e.target.value })}
            />
          </label>
          <label className="student-field">
            <span>学院</span>
            <select
              value={searchForm.college}
              onChange={(e) =>
                setSearchForm({ ...searchForm, college: e.target.value, major: '', className: '' })
              }
            >
              <option value="">请选择学院</option>
              {colleges.map((college) => (
                <option key={college.id} value={college.name}>
                  {college.name}
                </option>
              ))}
            </select>
          </label>
          <label className="student-field">
            <span>专业</span>
            <select
              value={searchForm.major}
              onChange={(e) =>
                setSearchForm({ ...searchForm, major: e.target.value, className: '' })
              }
            >
              <option value="">请选择专业</option>
              {searchMajorOptions.map((major) => (
                <option key={major.id} value={major.name}>
                  {major.name}
                </option>
              ))}
            </select>
          </label>
          <label className="student-field">
            <span>年级</span>
            <select
              value={searchForm.grade}
              onChange={(e) =>
                setSearchForm({ ...searchForm, grade: e.target.value, className: '' })
              }
            >
              <option value="">请选择年级</option>
              {gradeOptions.map((grade) => (
                <option key={grade} value={grade}>
                  {grade}级
                </option>
              ))}
            </select>
          </label>
          <label className="student-field">
            <span>班级</span>
            <select
              value={searchForm.className}
              onChange={(e) => setSearchForm({ ...searchForm, className: e.target.value })}
            >
              <option value="">请选择班级</option>
              {filteredSearchClasses.map((cls) => (
                <option key={cls.id} value={cls.name}>
                  {cls.name}
                </option>
              ))}
            </select>
          </label>
          <label className="student-field">
            <span>班委筛选</span>
            <select
              value={searchForm.committeeFilter}
              onChange={(e) => setSearchForm({ ...searchForm, committeeFilter: e.target.value })}
            >
              {committeeFilterOptions.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </label>
          <div className="student-filter-actions">
            <button type="button" className="primary-action" onClick={handleSearch}>
              <span>搜索</span>
            </button>
            <button type="button" className="secondary-action" onClick={handleReset}>
              <span>重置</span>
            </button>
          </div>
        </div>
      </section>

      <section className="student-table-card">
        <div className="student-table-toolbar">
          <div className="student-table-actions">
            <button type="button" className="toolbar-primary" onClick={() => openDrawer()}>
              <span>新增学生</span>
            </button>
            <button
              type="button"
              className="secondary-action"
              onClick={() => navigate('/admin/scores')}
            >
              <span>按成绩单导入学生</span>
            </button>
            {!isTeacher && (
              <button type="button" className="toolbar-danger" onClick={handleBatchDelete}>
                <span>批量删除</span>
              </button>
            )}
            <span className="student-selection-summary">
              已选 <strong>{selectedStudents.length}</strong> 项
            </span>
            {isTeacher && <span className="student-scope-tip">老师仅可删除自己负责的学生</span>}
          </div>
        </div>

        <div className="student-table-wrap">
          <table className="student-table">
            <thead>
              <tr>
                <th>
                  <input
                    type="checkbox"
                    checked={
                      selectedStudents.length === decoratedStudents.length &&
                      decoratedStudents.length > 0
                    }
                    onChange={handleSelectAll}
                  />
                </th>
                <th>学号</th>
                <th>姓名</th>
                <th>学院</th>
                <th>专业</th>
                <th>年级</th>
                <th>班级</th>
                <th>身份</th>
                <th>任课教师</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr>
                  <td colSpan="10" className="table-status-cell">
                    加载中...
                  </td>
                </tr>
              ) : decoratedStudents.length === 0 ? (
                <tr>
                  <td colSpan="10" className="table-status-cell">
                    暂无学生数据
                  </td>
                </tr>
              ) : (
                decoratedStudents.map((student) => (
                  <tr key={student.id}>
                    <td>
                      <input
                        type="checkbox"
                        checked={selectedStudents.includes(student.id)}
                        onChange={() => handleSelectStudent(student.id)}
                      />
                    </td>
                    <td>{student.studentNo}</td>
                    <td>{student.name}</td>
                    <td>{student.collegeName}</td>
                    <td>{student.majorName}</td>
                    <td>{student.grade}</td>
                    <td>{student.className}</td>
                    <td>
                      <span
                        className={`identity-badge ${student.classCommittee ? 'committee' : 'student'}`}
                      >
                        {student.classCommittee ? '班委' : '普通学生'}
                      </span>
                    </td>
                    <td>{student.teacherName || '-'}</td>
                    <td>
                      <div className="student-row-actions">
                        <button type="button" onClick={() => openDrawer(student)}>
                          <span>编辑</span>
                        </button>
                        <button
                          type="button"
                          className="warning"
                          onClick={() => handleResetPassword(student)}
                        >
                          <span>重置密码</span>
                        </button>
                        <button
                          type="button"
                          className="danger"
                          onClick={() => handleDelete(student.id)}
                        >
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

        <div className="student-pagination">
          <span className="student-pagination-total">共 {totalElements} 条记录</span>
          <label className="student-pagination-size">
            <span>每页</span>
            <select
              value={pageSize}
              onChange={(e) => {
                setPageSize(Number(e.target.value))
                setCurrentPage(1)
              }}
            >
              {[10, 20, 50].map((size) => (
                <option key={size} value={size}>
                  {size}
                </option>
              ))}
            </select>
            <span>条</span>
          </label>
          <div className="student-pagination-pages">
            <button
              type="button"
              disabled={currentPage === 1}
              onClick={() => setCurrentPage((prev) => prev - 1)}
            >
              ‹
            </button>
            {paginationNumbers.map((page, index) => {
              const previous = paginationNumbers[index - 1]
              return (
                <span key={page} className="student-page-fragment">
                  {previous && page - previous > 1 && (
                    <span className="student-page-ellipsis">…</span>
                  )}
                  <button
                    type="button"
                    className={page === currentPage ? 'active' : ''}
                    onClick={() => setCurrentPage(page)}
                  >
                    {page}
                  </button>
                </span>
              )
            })}
            <button
              type="button"
              disabled={currentPage === totalPages || totalPages === 0}
              onClick={() => setCurrentPage((prev) => prev + 1)}
            >
              ›
            </button>
          </div>
          <span className="student-pagination-jump">
            前往 <strong>{currentPage}</strong> / {totalPages || 1} 页
          </span>
        </div>
      </section>

      <div
        className={`student-drawer-backdrop ${isDrawerOpen ? 'open' : ''}`}
        onClick={closeDrawer}
      />
      <aside className={`student-drawer ${isDrawerOpen ? 'open' : ''}`}>
        <div className="student-drawer-header">
          <div>
            <h3>{editingStudent ? '编辑学生' : '新增学生'}</h3>
            <p>数据保存后将同步到后端接口</p>
          </div>
          <button type="button" className="student-drawer-close" onClick={closeDrawer}>
            ×
          </button>
        </div>

        <form className="student-drawer-form" onSubmit={handleSave}>
          <label className="drawer-field">
            <span>
              学号 <em>*</em>
            </span>
            <input
              value={modalForm.studentNo}
              onChange={(e) => setModalForm({ ...modalForm, studentNo: e.target.value })}
              placeholder="请输入学号"
              required
            />
          </label>
          <label className="drawer-field">
            <span>
              姓名 <em>*</em>
            </span>
            <input
              value={modalForm.name}
              onChange={(e) => setModalForm({ ...modalForm, name: e.target.value })}
              placeholder="请输入姓名"
              required
            />
          </label>
          <label className="drawer-field">
            <span>
              年级 <em>*</em>
            </span>
            <select
              value={modalGrade}
              onChange={(e) => {
                const nextGrade = e.target.value
                setModalGrade(nextGrade)
                setModalForm((prev) => ({ ...prev, className: '' }))
              }}
              required
            >
              <option value="">请选择年级</option>
              {gradeOptions.map((grade) => (
                <option key={grade} value={grade}>
                  {grade}级
                </option>
              ))}
            </select>
          </label>

          <label className="drawer-field">
            <span>
              班级 <em>*</em>
            </span>
            <select
              value={modalForm.className}
              onChange={(e) => setModalForm({ ...modalForm, className: e.target.value })}
              required
            >
              <option value="">请选择班级</option>
              {filteredModalClasses.map((cls) => (
                <option key={cls.id} value={cls.name}>
                  {cls.name}
                </option>
              ))}
            </select>
          </label>

          <div className="drawer-tip">选择班级后，将自动带出年级、学院、专业信息</div>

          <label className="drawer-field">
            <span>学院</span>
            <input value={selectedCollege?.name || ''} placeholder="-" readOnly />
          </label>

          <label className="drawer-field">
            <span>专业</span>
            <input value={selectedMajor?.name || ''} placeholder="-" readOnly />
          </label>

          <fieldset className="drawer-radio-group">
            <legend>
              学生身份 <em>*</em>
            </legend>
            <label>
              <input
                type="radio"
                checked={!modalForm.classCommittee}
                onChange={() => setModalForm({ ...modalForm, classCommittee: false })}
              />
              <span>普通学生</span>
            </label>
            <label>
              <input
                type="radio"
                checked={modalForm.classCommittee}
                onChange={() => setModalForm({ ...modalForm, classCommittee: true })}
              />
              <span>班委</span>
            </label>
          </fieldset>

          <div className="student-drawer-footer">
            <button type="button" className="drawer-cancel" onClick={closeDrawer}>
              取消
            </button>
            <button type="submit" className="drawer-submit">
              保存
            </button>
          </div>
        </form>
      </aside>
    </div>
  )
}

export default StudentManagement
