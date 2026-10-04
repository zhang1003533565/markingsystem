import request from '../../utils/request';

/**
 * Admin 专业管理接口
 */

// 获取所有专业
export const getMajorList = (params) => {
  return request({
    url: '/admin/majors',
    method: 'get',
    params
  });
};

export const getMajorOptions = () => {
  return request({
    url: '/admin/majors/options',
    method: 'get'
  });
};

// 根据ID查询专业
export const getMajorById = (id) => {
  return request({
    url: `/admin/majors/${id}`,
    method: 'get'
  });
};

// 根据学院ID查询专业
export const getMajorsByCollege = (collegeId) => {
  return request({
    url: `/admin/majors/college/${collegeId}`,
    method: 'get'
  });
};

// 搜索专业
export const searchMajors = (params) => {
  return request({
    url: '/admin/majors/search',
    method: 'get',
    params
  });
};

// 新增专业
export const createMajor = (data) => {
  return request({
    url: '/admin/majors',
    method: 'post',
    data
  });
};

// 更新专业信息
export const updateMajor = (id, data) => {
  return request({
    url: `/admin/majors/${id}`,
    method: 'put',
    data
  });
};

// 删除专业
export const getMajorDeleteImpact = (id) => {
  return request({
    url: `/admin/majors/${id}/delete-impact`,
    method: 'get'
  });
};

export const deleteMajor = (id, params) => {
  return request({
    url: `/admin/majors/${id}`,
    method: 'delete',
    params
  });
};

// 切换专业状态
export const toggleMajorStatus = (id) => {
  return request({
    url: `/admin/majors/${id}/status`,
    method: 'put'
  });
};
