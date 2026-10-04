import request from '../../utils/request';

export const getAcademicYearList = () => {
  return request({
    url: '/admin/academic-years',
    method: 'get'
  });
};

export const getActiveAcademicYearList = () => {
  return request({
    url: '/admin/academic-years/active',
    method: 'get'
  });
};

export const getCurrentAcademicYear = (config = {}) => {
  return request({
    url: '/admin/academic-years/current',
    method: 'get',
    ...config
  });
};

export const createAcademicYear = (data) => {
  return request({
    url: '/admin/academic-years',
    method: 'post',
    data
  });
};

export const updateAcademicYear = (id, data) => {
  return request({
    url: `/admin/academic-years/${id}`,
    method: 'put',
    data
  });
};

export const deleteAcademicYear = (id) => {
  return request({
    url: `/admin/academic-years/${id}`,
    method: 'delete'
  });
};

export const toggleAcademicYearStatus = (id) => {
  return request({
    url: `/admin/academic-years/${id}/status`,
    method: 'put'
  });
};

export const setCurrentAcademicYear = (id) => {
  return request({
    url: `/admin/academic-years/${id}/current`,
    method: 'put'
  });
};
