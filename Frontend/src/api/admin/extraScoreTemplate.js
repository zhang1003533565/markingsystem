import request from '../../utils/request';

export const getExtraScoreTemplateList = () => request({
  url: '/extra-score-templates',
  method: 'get'
});

export const getExtraScoreTemplateDetail = (id) => request({
  url: `/extra-score-templates/${id}`,
  method: 'get'
});

export const createExtraScoreTemplate = (data) => request({
  url: '/extra-score-templates',
  method: 'post',
  data
});

export const updateExtraScoreTemplate = (id, data) => request({
  url: `/extra-score-templates/${id}`,
  method: 'put',
  data
});

export const deleteExtraScoreTemplate = (id) => request({
  url: `/extra-score-templates/${id}`,
  method: 'delete'
});

export const toggleExtraScoreTemplateStatus = (id) => request({
  url: `/extra-score-templates/${id}/toggle`,
  method: 'post'
});

export const assignExtraScoreTemplate = (id) => request({
  url: `/extra-score-templates/${id}/assign`,
  method: 'post'
});

export const getAssignedExtraScoreTemplate = (scoreType) => request({
  url: '/extra-score-templates/assigned',
  method: 'get',
  params: { scoreType }
});

export const exportAssignedExtraScoreTemplate = (scoreType) => request({
  url: '/extra-score-templates/assigned/export',
  method: 'get',
  params: { scoreType },
  responseType: 'blob'
});

export const importExtraScoreTemplateWorkbook = (formData) => request({
  url: '/extra-score-templates/import',
  method: 'post',
  data: formData,
  headers: {
    'Content-Type': 'multipart/form-data'
  }
});
