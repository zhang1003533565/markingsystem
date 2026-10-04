import request from '../../utils/request';

const KICKOFF_TIMEOUT_MS = 30000;
const POLL_TIMEOUT_MS = 15000;
const POLL_INTERVAL_MS = 2500;
const POLL_MAX_WAIT_MS = 5 * 60 * 1000;

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

/**
 * 模块级轮询去重表：同一岗位的生成轮询全局共享同一份 Promise。
 * 发展助手内「成长链路」与「学习资源」两个子视图会同时保持挂载，
 * 若各自触发同一岗位的生成，避免重复打轮询接口（双份 2.5s 打点）。
 */
const jobResourcePollPromises = new Map();

/**
 * 获取某收藏岗位的学习资源（单次请求）。
 * 后端已改为异步生成：可能立即返回 status=generating，需配合 waitForJobResources 轮询。
 * @param {number} recommendationId
 * @param {boolean} generate 默认 true（无缓存时触发 AI 生成）；
 *                           传 false 则仅读缓存/状态、不生成不耗额度
 */
export const getJobResources = (recommendationId, generate = true) => request({
  url: `/student/job-resources/${recommendationId}`,
  method: 'get',
  params: { generate },
  timeout: generate ? KICKOFF_TIMEOUT_MS : POLL_TIMEOUT_MS,
  silentError: !generate
});

/**
 * 刷新某收藏岗位的学习资源（异步启动，返回 generating 后需轮询）
 */
export const refreshJobResources = (recommendationId) => request({
  url: `/student/job-resources/${recommendationId}/refresh`,
  method: 'post',
  timeout: KICKOFF_TIMEOUT_MS
});

/**
 * 重新生成某岗位学习资源（异步启动，仅扣一次 REGENERATE）
 */
export const regenerateJobResources = (recommendationId) => request({
  url: `/student/job-resources/${recommendationId}/regenerate`,
  method: 'post',
  timeout: KICKOFF_TIMEOUT_MS
});

/**
 * @deprecated 请改用 regenerateJobResources
 */
export const clearResourceCache = (recommendationId) => regenerateJobResources(recommendationId);

/**
 * 获取今日生成额度使用情况
 */
export const getResourceQuota = () => request({
  url: '/student/job-resources/quota',
  method: 'get'
});

/**
 * 轮询直到学习资源 ready / failed / 超时。
 * 用于异步 AI 生成，避免单次 HTTP 卡死超时。
 * 同一岗位的并发轮询会共享同一份 Promise（见 jobResourcePollPromises），
 * 避免「成长链路」与「学习资源」两个常驻子视图各自打一份轮询。
 */
export async function waitForJobResources(recommendationId, options = {}) {
  if (recommendationId != null) {
    const inflight = jobResourcePollPromises.get(recommendationId);
    if (inflight) return inflight;
    const promise = doPoll(recommendationId, options).finally(() => {
      // 仅当仍是当前这份 Promise 时清理，避免误删新触发的轮询
      if (jobResourcePollPromises.get(recommendationId) === promise) {
        jobResourcePollPromises.delete(recommendationId);
      }
    });
    jobResourcePollPromises.set(recommendationId, promise);
    return promise;
  }
  return doPoll(recommendationId, options);
}

async function doPoll(recommendationId, options = {}) {
  const {
    maxWaitMs = POLL_MAX_WAIT_MS,
    intervalMs = POLL_INTERVAL_MS,
    signal,
  } = options;
  const deadline = Date.now() + maxWaitMs;

  while (Date.now() < deadline) {
    if (signal?.aborted) {
      throw new Error('已取消等待');
    }
    await sleep(intervalMs);
    if (signal?.aborted) {
      throw new Error('已取消等待');
    }

    let res;
    try {
      res = await getJobResources(recommendationId, false);
    } catch {
      // 轮询偶发网络错误不立刻失败，继续直到总超时
      continue;
    }

    const status = res?.status;
    const data = Array.isArray(res?.data) ? res.data : [];

    if (status === 'ready' || (status !== 'generating' && status !== 'failed' && data.length > 0)) {
      return res;
    }
    if (status === 'failed') {
      throw new Error(res?.message || '学习资源生成失败，请稍后再试');
    }
    // generating / empty：继续等
  }

  throw new Error('学习资源生成超时，请稍后在页面重试');
}

/**
 * 触发获取/生成并在需要时轮询到完成。
 */
export async function requestJobResources(recommendationId, generate = true) {
  const res = await getJobResources(recommendationId, generate);
  if (res?.status === 'failed') {
    throw new Error(res?.message || '学习资源生成失败，请稍后再试');
  }
  if (res?.status === 'generating') {
    return waitForJobResources(recommendationId);
  }
  return res;
}

/**
 * 触发刷新并轮询到完成。
 */
export async function requestRefreshJobResources(recommendationId) {
  const res = await refreshJobResources(recommendationId);
  if (res?.status === 'failed') {
    throw new Error(res?.message || '学习资源刷新失败，请稍后再试');
  }
  if (res?.status === 'generating') {
    return waitForJobResources(recommendationId);
  }
  return res;
}

/**
 * 触发重新生成并轮询到完成。
 */
export async function requestRegenerateJobResources(recommendationId) {
  const res = await regenerateJobResources(recommendationId);
  if (res?.status === 'failed') {
    throw new Error(res?.message || '学习资源重新生成失败，请稍后再试');
  }
  if (res?.status === 'generating') {
    return waitForJobResources(recommendationId);
  }
  return res;
}
