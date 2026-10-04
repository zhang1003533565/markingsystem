import request from '../../utils/request';

export const getSecondClassScoreRules = () => {
  return request({
    url: '/admin/second-class-score-rules',
    method: 'get'
  });
};

export const updateSecondClassScoreRules = (data) => {
  return request({
    url: '/admin/second-class-score-rules',
    method: 'put',
    data
  });
};
