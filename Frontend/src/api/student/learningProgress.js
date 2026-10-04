import request from '../../utils/request';

/**
 * 岗位学习进度 / 打卡接口（发展助手「学习记录」）
 */

/** 汇总所有收藏岗位的学习完成度 + 每日打卡信息 */
export const getLearningProgressOverview = () => request({
  url: '/student/learning-progress/overview',
  method: 'get'
});

/** 查询某岗位的完成记录（资源/任务步骤/路径步骤/已解锁资源） */
export const getJobProgress = (recommendationId) => request({
  url: `/student/learning-progress/job/${recommendationId}`,
  method: 'get'
});

/** 解锁学习资源（打开阅读器时调用，标记完成前必须先解锁） */
export const unlockLearningResource = (payload) => request({
  url: '/student/learning-progress/unlock',
  method: 'post',
  data: payload
});

/** 标记某项学习内容已完成 */
export const markProgressCompleted = (payload) => request({
  url: '/student/learning-progress/complete',
  method: 'post',
  data: payload
});

/** 取消某项学习内容的完成标记 */
export const unmarkProgressCompleted = (payload) => request({
  url: '/student/learning-progress/uncomplete',
  method: 'post',
  data: payload
});

/** 每日打卡概览：今日是否打卡、连续天数、最近 7 天内已打卡日期 */
export const getDailyCheckinOverview = () => request({
  url: '/student/learning-progress/daily-checkin',
  method: 'get'
});
