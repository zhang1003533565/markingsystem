import request from '../../utils/request';

/**
 * User 学生查询接口（只读）
 */

// 分页查询学生列表
export const getStudentList = (params) => {
  return request({
    url: '/user/students',
    method: 'get',
    params
  });
};

// 根据ID查询学生
export const getStudentById = (id) => {
  return request({
    url: `/user/students/${id}`,
    method: 'get'
  });
};

// 根据学号查询学生
export const getStudentByNo = (studentNo) => {
  return request({
    url: `/user/students/no/${studentNo}`,
    method: 'get'
  });
};

// 获取老师的学生列表
export const getStudentsByTeacher = (teacherId) => {
  return request({
    url: `/user/students/teacher/${teacherId}`,
    method: 'get'
  });
};

// 获取班级的学生列表
export const getStudentsByClass = (className) => {
  return request({
    url: `/user/students/class/${className}`,
    method: 'get'
  });
};
