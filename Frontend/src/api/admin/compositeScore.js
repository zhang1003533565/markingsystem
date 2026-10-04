import request from '../../utils/request'

export const getCompositeScoreList = (params) => {
  return request({
    url: '/admin/composite-scores',
    method: 'get',
    params,
  })
}

export const refreshCompositeScoreList = (params) => {
  return request({
    url: '/admin/composite-scores/refresh',
    method: 'post',
    params,
    timeout: 30000,
  })
}

export const getCompositeScoreRefreshTask = (taskId) => {
  return request({
    url: `/admin/composite-scores/refresh-task/${taskId}`,
    method: 'get',
  })
}

export const getCompositeScoreMeta = (params) => {
  return request({
    url: '/admin/composite-scores/meta',
    method: 'get',
    params,
  })
}

export const getCompositeScoreHistory = (params) => {
  return request({
    url: '/admin/composite-scores/history',
    method: 'get',
    params,
  })
}

export const getCompositeExtraScoreDetails = (studentNo, params) => {
  return request({
    url: `/admin/composite-scores/${encodeURIComponent(studentNo)}/extra-details`,
    method: 'get',
    params,
  })
}

export const getCompositeBaseScoreDetails = (studentNo, params) => {
  return request({
    url: `/admin/composite-scores/${encodeURIComponent(studentNo)}/base-details`,
    method: 'get',
    params,
  })
}

export const getCompositeScoreWeights = () => {
  return request({
    url: '/admin/composite-scores/weights',
    method: 'get',
  })
}

export const updateCompositeScoreWeights = (data) => {
  return request({
    url: '/admin/composite-scores/weights',
    method: 'put',
    data,
  })
}

export const getCompositePhysicalFullScoreRules = (params) => {
  return request({
    url: '/admin/composite-physical-full-score-rules',
    method: 'get',
    params,
  })
}

export const updateCompositePhysicalFullScoreRules = (data) => {
  return request({
    url: '/admin/composite-physical-full-score-rules',
    method: 'put',
    data,
  })
}
