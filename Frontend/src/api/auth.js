import request from '../utils/request';
//前后端认证交互

// 登录
export const login = (data) => {
  return request({
    url: '/auth/login',
    method: 'post',
    data
  });
};

// 获取当前用户信息
export const getUserInfo = () => {
  return request({
    url: '/auth/userInfo',
    method: 'get'
  });
};

// 退出登录
export const logout = () => {
  return request({
    url: '/auth/logout',
    method: 'post'
  });
};
