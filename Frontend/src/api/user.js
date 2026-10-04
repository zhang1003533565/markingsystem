import request from '../utils/request';
// 前后端用户交互

// 获取用户列表
export const getUserList = (params) => {
  return request({
    url: '/users',
    method: 'get',
    params
  });
};

// 获取用户详情
export const getUserById = (id) => {
  return request({
    url: `/users/${id}`,
    method: 'get'
  });
};

// 创建用户
export const createUser = (data) => {
  return request({
    url: '/users',
    method: 'post',
    data
  });
};

// 更新用户
export const updateUser = (id, data) => {
  return request({
    url: `/users/${id}`,
    method: 'put',
    data
  });
};

// 删除用户
export const deleteUser = (id) => {
  return request({
    url: `/users/${id}`,
    method: 'delete'
  });
};
