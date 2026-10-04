import request from '../../utils/request';

/**
 * AI 对话日志接口（管理员看全部；教师仅看带教班级）。
 */

// 对话日志列表（keyword / className 过滤，分页）
export const getKnowledgeChatLogs = (params) => {
  return request({
    url: '/admin/knowledge-chat-logs',
    method: 'get',
    params
  });
};

export const deleteKnowledgeChatLog = (id) => {
  return request({
    url: `/admin/knowledge-chat-logs/${id}`,
    method: 'delete'
  });
};
