import request from '../../utils/request';

export const getExtraScoreApplicationListContext = (scoreType, params = {}) => request({
  url: '/extra-score-applications/list-context',
  method: 'get',
  params: { scoreType, ...params }
});

export const getExtraScoreApplicationList = (scoreType, params = {}) => request({
  url: '/extra-score-applications',
  method: 'get',
  params: { scoreType, ...params }
});

export const getExtraScoreApplicationProcess = () => request({
  url: '/extra-score-applications/process',
  method: 'get'
});

export const getCurrentExtraScoreApplicationProcess = () => request({
  url: '/extra-score-applications/process/current',
  method: 'get'
});

export const getExtraScoreApplicationDetail = (id) => request({
  url: `/extra-score-applications/${id}`,
  method: 'get'
});

export const getExtraScoreApplicationEditContext = (id, scoreType) => request({
  url: `/extra-score-applications/edit-context/${id}`,
  method: 'get',
  params: { scoreType }
});

export const searchExtraScoreTemplateItems = (scoreType, keyword, limit = 30) => request({
  url: '/extra-score-applications/template-items/search',
  method: 'get',
  params: { scoreType, keyword, limit }
});

export const getExtraScoreApplicationHistory = (params = {}) => request({
  url: '/extra-score-applications/history',
  method: 'get',
  params
});

export const submitCurrentExtraScoreApplications = () => request({
  url: '/extra-score-applications/submit-current-window',
  method: 'post'
});

export const submitSelectedExtraScoreApplications = (applicationIds) => request({
  url: '/extra-score-applications/submit-selected',
  method: 'post',
  data: { applicationIds }
});

export const withdrawCurrentExtraScoreApplications = () => request({
  url: '/extra-score-applications/withdraw-current-window',
  method: 'post'
});

export const exportMyExtraScoreExcel = (params = {}) => request({
  url: '/extra-score-applications/export/excel',
  method: 'get',
  params,
  responseType: 'blob'
});

export const exportMyExtraScoreProofImages = (params = {}) => request({
  url: '/extra-score-applications/export/proof-images',
  method: 'get',
  params,
  responseType: 'blob'
});

export const exportMyExtraScoreCertificates = (params = {}) => request({
  url: '/extra-score-applications/export/certificates',
  method: 'get',
  params,
  responseType: 'blob'
});

export const createExtraScoreApplication = (data) => request({
  url: '/extra-score-applications',
  method: 'post',
  data
});

export const uploadExtraScoreProofImages = (formData) => request({
  url: '/extra-score-applications/proof-images',
  method: 'post',
  data: formData,
  headers: {
    'Content-Type': 'multipart/form-data'
  }
});

export const updateExtraScoreApplication = (id, data) => request({
  url: `/extra-score-applications/${id}`,
  method: 'put',
  data
});

export const deleteExtraScoreApplication = (id) => request({
  url: `/extra-score-applications/${id}`,
  method: 'delete'
});

export const getExtraScoreSubmissionWindowHistory = () => request({
  url: '/extra-score-applications/submission-window/history',
  method: 'get'
});
