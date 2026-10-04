import request from '../../utils/request';

/**
 * 教师端 AI 对话日志接口（仅本人带教班级）。
 */

export const getTeacherKnowledgeChatLogs = (params) => {
  return request({
    url: '/teacher/knowledge-chat-logs',
    method: 'get',
    params
  });
};

export const deleteTeacherKnowledgeChatLog = (id) => {
  return request({
    url: `/teacher/knowledge-chat-logs/${id}`,
    method: 'delete'
  });
};
