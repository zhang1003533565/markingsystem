import request from '../utils/request';

export const changeCurrentUserPassword = (data) => request({
  url: '/user/password/change',
  method: 'post',
  data
});

export const sendCurrentUserEmailVerificationCode = (data) => request({
  url: '/user/email/verification-code',
  method: 'post',
  data
});

export const bindCurrentUserEmail = (data) => request({
  url: '/user/email/bind',
  method: 'post',
  data
});
