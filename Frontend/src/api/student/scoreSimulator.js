import request from '../../utils/request';

// 综测成绩模拟器（学生端，仅本人）
export const simulateScore = (data) => request({
  url: '/student/score-simulator/simulate',
  method: 'post',
  data
});

export const reverseScore = (data) => request({
  url: '/student/score-simulator/reverse',
  method: 'post',
  data
});
