import request from '../../utils/request';

/**
 * 岗位成长链路 - 学习任务卡（以岗定教）
 * 有缓存返回缓存，无缓存触发 DeepSeek AI 生成（消耗任务卡额度，每日独立额度）
 * @param {number} recommendationId
 * @param {boolean} generate 默认 true（无缓存时触发 AI 生成）；
 *                           传 false 则仅读缓存、不生成不耗额度（首页/切换岗位用）；
 *                           无缓存时后端返回 data=null（HTTP 200），不是错误
 */
export const getJobTaskCard = (recommendationId, generate = true) => request({
  url: `/student/job-task-cards/${recommendationId}`,
  method: 'get',
  params: { generate },
  timeout: 120000
});

/**
 * 查询今日学习任务卡生成额度
 */
export const getJobTaskCardQuota = () => request({
  url: '/student/job-task-cards/quota',
  method: 'get'
});

/**
 * 刷新某岗位的学习任务卡（需先完成当前任务卡全部步骤；任务卡板块每日 5 次、
 * 「刷新」操作每日最多 3 次）。成功后返回新的任务卡数据与最新额度。
 */
export const refreshJobTaskCard = (recommendationId) => request({
  url: `/student/job-task-cards/${recommendationId}/refresh`,
  method: 'post',
  timeout: 120000
});

/**
 * 获取某收藏岗位的学习资源（AI 异步生成，可能返回 generating，需轮询）
 * @param {number} recommendationId
 * @param {boolean} generate 默认 true；false 时仅读缓存/状态、不生成不耗额度
 */
export const getJobResourcesWithTimeout = (recommendationId, generate = true) => request({
  url: `/student/job-resources/${recommendationId}`,
  method: 'get',
  params: { generate },
  timeout: generate ? 30000 : 15000,
  silentError: !generate
});

/**
 * 刷新某收藏岗位的学习资源（异步启动）
 */
export const refreshJobResourcesWithTimeout = (recommendationId) => request({
  url: `/student/job-resources/${recommendationId}/refresh`,
  method: 'post',
  timeout: 30000
});

export {
  requestJobResources,
  requestRefreshJobResources,
  waitForJobResources,
} from './developmentResource';
