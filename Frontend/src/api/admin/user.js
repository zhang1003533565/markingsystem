import request from '../../utils/request';

export const getUserList = (params) => {
  return request({
    url: '/admin/users',
    method: 'get',
    params
  });
};

export const createUser = (data) => {
  return request({
    url: '/admin/users',
    method: 'post',
    data
  });
};

export const getCurrentAdminUser = () => {
  return request({
    url: '/admin/users/me',
    method: 'get'
  });
};

export const updateCurrentUserEmailConfig = (data) => {
  return request({
    url: '/admin/users/me/email-config',
    method: 'put',
    data
  });
};

export const uploadAdminAvatar = (file) => {
  const formData = new FormData();
  formData.append('file', file);
  return request({
    url: '/admin/users/me/avatar',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' }
  });
};

export const updateCurrentUserPhone = (data) => {
  return request({
    url: '/admin/users/me/phone',
    method: 'put',
    data
  });
};

export const resetUserPassword = (id) => {
  return request({
    url: `/admin/users/${id}/reset-password`,
    method: 'post'
  });
};

export const getTeacherList = () => {
  return request({
    url: '/admin/users/teachers',
    method: 'get'
  });
};

export const getCommitteeList = () => {
  return request({
    url: '/admin/users/committees',
    method: 'get'
  });
};
