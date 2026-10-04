import request from '../../utils/request';

/**
 * Admin 学院管理接口
 */

// 获取所有学院
export const getCollegeList = () => {
  return request({
    url: '/admin/colleges',
    method: 'get'
  });
};

// 根据ID查询学院
export const getCollegeById = (id) => {
  return request({
    url: `/admin/colleges/${id}`,
    method: 'get'
  });
};

// 搜索学院
export const searchColleges = (keyword) => {
  return request({
    url: '/admin/colleges/search',
    method: 'get',
    params: { keyword }
  });
};

// 新增学院
export const createCollege = (data) => {
  return request({
    url: '/admin/colleges',
    method: 'post',
    data
  });
};

// 更新学院信息
export const updateCollege = (id, data) => {
  return request({
    url: `/admin/colleges/${id}`,
    method: 'put',
    data
  });
};

// 删除学院
export const getCollegeDeleteImpact = (id) => {
  return request({
    url: `/admin/colleges/${id}/delete-impact`,
    method: 'get'
  });
};

export const deleteCollege = (id, params) => {
  return request({
    url: `/admin/colleges/${id}`,
    method: 'delete',
    params
  });
};

// 切换学院状态
export const toggleCollegeStatus = (id) => {
  return request({
    url: `/admin/colleges/${id}/status`,
    method: 'put'
  });
};
