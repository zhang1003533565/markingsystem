import request from '../utils/request';

export const getMessages = (params) => {
  return request({
    url: '/messages',
    method: 'get',
    params,
  });
};

export const getMessageUnreadCount = () => {
  return request({
    url: '/messages/unread-count',
    method: 'get',
  });
};

export const markMessageRead = (id) => {
  return request({
    url: `/messages/${id}/read`,
    method: 'post',
  });
};

export const markAllMessagesRead = () => {
  return request({
    url: '/messages/read-all',
    method: 'post',
  });
};
