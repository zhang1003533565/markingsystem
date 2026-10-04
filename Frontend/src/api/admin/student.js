import request from '../../utils/request';

/**
 * Admin 学生管理接口
 */

// 分页查询学生列表
export const getStudentList = (params) => {
  return request({
    url: '/admin/students',
    method: 'get',
    params
  });
};

// 根据ID查询学生
export const getStudentById = (id) => {
  return request({
    url: `/admin/students/${id}`,
    method: 'get'
  });
};

// 根据学号查询学生
export const getStudentByNo = (studentNo) => {
  return request({
    url: `/admin/students/no/${studentNo}`,
    method: 'get'
  });
};

// 新增学生
export const createStudent = (data) => {
  return request({
    url: '/admin/students',
    method: 'post',
    data
  });
};

// 更新学生信息
export const updateStudent = (id, data) => {
  return request({
    url: `/admin/students/${id}`,
    method: 'put',
    data
  });
};

// 删除学生
export const deleteStudent = (id) => {
  return request({
    url: `/admin/students/${id}`,
    method: 'delete'
  });
};

export const resetStudentPassword = (id) => {
  return request({
    url: `/admin/students/${id}/reset-password`,
    method: 'post'
  });
};

// 批量删除学生
export const batchDeleteStudents = (ids) => {
  return request({
    url: '/admin/students/batch',
    method: 'delete',
    data: ids
  });
};

export const getAdminStudentAnalysis = (studentNo) => {
  return request({
    url: '/admin/analysis/student-profile',
    method: 'get',
    params: { studentNo },
    timeout: 30000
  });
};

export const refreshAdminStudentAnalysis = (studentNo) => {
  return request({
    url: '/admin/analysis/student-profile/refresh',
    method: 'post',
    params: { studentNo },
    timeout: 30000
  });
};

export const getAdminStudentAnalysisHistory = (studentNo) => {
  return request({
    url: '/admin/analysis/student-profile/history',
    method: 'get',
    params: { studentNo }
  });
};

export const getAdminAnalysisOverview = (params) => {
  return request({
    url: '/admin/analysis/overview',
    method: 'get',
    params
  });
};

export const refreshAdminAnalysisOverview = (params) => {
  return request({
    url: '/admin/analysis/overview/refresh',
    method: 'post',
    data: params,
    timeout: 30000
  });
};

export const getAdminAnalysisTask = (taskId) => {
  return request({
    url: `/admin/analysis/overview/refresh-task/${taskId}`,
    method: 'get'
  });
};

export const cancelAdminAnalysisTask = (taskId) => {
  return request({
    url: `/admin/analysis/overview/refresh-task/${taskId}/cancel`,
    method: 'post'
  });
};
