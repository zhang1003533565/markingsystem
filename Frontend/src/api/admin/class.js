import request from '../../utils/request';

/**
 * Admin 班级管理接口
 */

// 获取所有班级
export const getClassList = () => {
  return request({
    url: '/admin/classes',
    method: 'get'
  });
};

export const getClassOptions = () => {
  return request({
    url: '/admin/classes/options',
    method: 'get'
  });
};

// 根据ID查询班级
export const getClassById = (id) => {
  return request({
    url: `/admin/classes/${id}`,
    method: 'get'
  });
};

// 根据学院ID查询班级
export const getClassesByCollege = (collegeId) => {
  return request({
    url: `/admin/classes/college/${collegeId}`,
    method: 'get'
  });
};

// 根据专业ID查询班级
export const getClassesByMajor = (majorId) => {
  return request({
    url: `/admin/classes/major/${majorId}`,
    method: 'get'
  });
};

// 搜索班级
export const searchClasses = (params) => {
  return request({
    url: '/admin/classes/search',
    method: 'get',
    params
  });
};

// 新增班级
export const createClass = (data) => {
  return request({
    url: '/admin/classes',
    method: 'post',
    data
  });
};

// 更新班级信息
export const updateClass = (id, data) => {
  return request({
    url: `/admin/classes/${id}`,
    method: 'put',
    data
  });
};

// 删除班级
export const deleteClass = (id) => {
  return request({
    url: `/admin/classes/${id}`,
    method: 'delete'
  });
};

// 切换班级状态
export const toggleClassStatus = (id) => {
  return request({
    url: `/admin/classes/${id}/status`,
    method: 'put'
  });
};
