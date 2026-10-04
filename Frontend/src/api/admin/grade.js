import request from '../../utils/request';

export const getGradeList = () => {
  return request({
    url: '/admin/grades',
    method: 'get'
  });
};

export const getActiveGradeList = () => {
  return request({
    url: '/admin/grades/active',
    method: 'get'
  });
};

export const createGrade = (data) => {
  return request({
    url: '/admin/grades',
    method: 'post',
    data
  });
};

export const updateGrade = (id, data) => {
  return request({
    url: `/admin/grades/${id}`,
    method: 'put',
    data
  });
};

export const deleteGrade = (id) => {
  return request({
    url: `/admin/grades/${id}`,
    method: 'delete'
  });
};

export const toggleGradeStatus = (id) => {
  return request({
    url: `/admin/grades/${id}/status`,
    method: 'put'
  });
};
