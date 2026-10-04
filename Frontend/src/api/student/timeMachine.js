import request from '../../utils/request';

/**
 * AI 时光机 - 执行一次未来路径推演（实时计算，不落库）。
 * @param {Object} data - { scoreDelta, competitionCount, practiceCount, certCount }
 */
export const simulateTimeMachine = (data) => request({
  url: '/student/time-machine/simulate',
  method: 'post',
  data,
  // 后端需实时调用 DeepSeek（同步 20s 超时），覆盖全局默认 10s 超时
  timeout: 60000,
  // 时光机页面自带错误展示区，关闭拦截器全局 toast，避免双重提示
  silentError: true
});