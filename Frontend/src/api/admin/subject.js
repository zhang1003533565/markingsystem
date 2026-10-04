import request from '../../utils/request';

/**
 * Admin 科目管理接口
 */

// 获取所有科目
export const getSubjectList = (params) => {
  return request({
    url: '/subjects',
    method: 'get',
    params
  });
};

// 根据ID查询科目
export const getSubjectById = (id) => {
  return request({
    url: `/subjects/${id}`,
    method: 'get'
  });
};

// 根据科目代码查询科目
export const getSubjectByCode = (subjectCode) => {
  return request({
    url: `/subjects/code/${subjectCode}`,
    method: 'get'
  });
};

// 根据科目名称查询科目
export const getSubjectByName = (subjectName) => {
  return request({
    url: `/subjects/name/${subjectName}`,
    method: 'get'
  });
};

// 根据科目类型查询科目
export const getSubjectsByType = (subjectType) => {
  return request({
    url: `/subjects/type/${subjectType}`,
    method: 'get'
  });
};

// 根据状态查询科目
export const getSubjectsByStatus = (status) => {
  return request({
    url: `/subjects/status/${status}`,
    method: 'get'
  });
};

// 新增科目
export const createSubject = (data) => {
  return request({
    url: '/subjects',
    method: 'post',
    data
  });
};

// 更新科目信息
export const updateSubject = (id, data) => {
  return request({
    url: `/subjects/${id}`,
    method: 'put',
    data
  });
};

// 删除科目
export const deleteSubject = (id) => {
  return request({
    url: `/subjects/${id}`,
    method: 'delete'
  });
};

// 批量删除科目
export const batchDeleteSubjects = (ids) => {
  return request({
    url: '/subjects/batch-delete',
    method: 'post',
    data: ids
  });
};

// 更新科目状态
export const updateSubjectStatus = (id, status) => {
  return request({
    url: `/subjects/${id}/status`,
    method: 'put',
    data: { status }
  });
};