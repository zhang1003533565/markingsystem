import request from '../../utils/request';

export const getCompositeSubjectConfigs = (params) => {
  return request({
    url: '/admin/composite-subject-configs',
    method: 'get',
    params
  });
};

export const getCompositeSubjectConfigStatus = (params) => {
  return request({
    url: '/admin/composite-subject-configs/status',
    method: 'get',
    params
  });
};

export const updateCompositeSubjectConfigs = (data) => {
  return request({
    url: '/admin/composite-subject-configs',
    method: 'put',
    data
  });
};
