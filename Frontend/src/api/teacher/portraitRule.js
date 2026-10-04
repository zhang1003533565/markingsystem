import request from '../../utils/request';

export const getTeacherPortraitRules = () => {
  return request({
    url: '/teacher/portrait-rules',
    method: 'get',
  });
};

export const saveTeacherPortraitRules = (data) => {
  return request({
    url: '/teacher/portrait-rules',
    method: 'put',
    data,
  });
};

export const getDefaultPortraitRules = () => {
  return request({
    url: '/teacher/portrait-rules/defaults',
    method: 'get',
  });
};

export const togglePortraitRuleEnabled = (enabled) => {
  return request({
    url: '/teacher/portrait-rules/enable',
    method: 'put',
    data: { enabled },
  });
};
