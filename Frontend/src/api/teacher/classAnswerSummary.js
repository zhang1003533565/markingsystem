import request from '../../utils/request';

/**
 * 教师端 - 班级答疑总结接口。
 */

// 教师可总结的班级列表（仅返回本人带教的班级）
export const getTeacherSummaryClasses = () => {
  return request({
    url: '/teacher/class-answer-summary/classes',
    method: 'get'
  });
};

// 历史总结列表（可选 className 筛选）
export const listClassAnswerSummaryHistory = (params = {}) => {
  return request({
    url: '/teacher/class-answer-summary/history',
    method: 'get',
    params
  });
};

// 查看单条历史总结
export const getClassAnswerSummaryHistory = (id) => {
  return request({
    url: `/teacher/class-answer-summary/history/${id}`,
    method: 'get'
  });
};

// 为指定班级生成答疑总结（className + startTime + endTime）
// AI 总结可能超过默认 10s，这里放宽到 90s
export const generateClassAnswerSummary = (payload) => {
  return request({
    url: '/teacher/class-answer-summary/generate',
    method: 'post',
    data: payload,
    timeout: 90000
  });
};
