import request from '../../utils/request';

export const getExtraScoreSubmissionWindowList = (params) => request({
  url: '/admin/extra-score-submission-windows',
  method: 'get',
  params
});

export const saveExtraScoreSubmissionWindow = (data) => request({
  url: '/admin/extra-score-submission-windows',
  method: 'post',
  data
});

export const closeExtraScoreSubmissionWindow = (id) => request({
  url: `/admin/extra-score-submission-windows/${id}/close`,
  method: 'post'
});

export const completeExtraScoreSubmissionWindow = (id) => request({
  url: `/admin/extra-score-submission-windows/${id}/complete`,
  method: 'post'
});
