import request from '../../utils/request';

export const getSystemEmailList = () => {
  return request({
    url: '/admin/system-emails',
    method: 'get'
  });
};

export const addSystemEmail = (email, password) => {
  return request({
    url: '/admin/system-emails',
    method: 'post',
    data: { email, password }
  });
};

export const removeSystemEmail = (id) => {
  return request({
    url: `/admin/system-emails/${id}`,
    method: 'delete'
  });
};

export const setDefaultSystemEmail = (id) => {
  return request({
    url: `/admin/system-emails/${id}/default`,
    method: 'put'
  });
};

export const getEmailBindingPolicy = () => {
  return request({
    url: '/admin/system-emails/email-binding-policy',
    method: 'get'
  });
};

export const updateEmailBindingPolicy = (forceEmailBindingRequired) => {
  return request({
    url: '/admin/system-emails/email-binding-policy',
    method: 'put',
    data: { forceEmailBindingRequired }
  });
};
