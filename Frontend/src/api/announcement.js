import request from '../utils/request';

export const getMyAnnouncements = (params) => {
  return request({
    url: '/announcements',
    method: 'get',
    params
  });
};

export const getPublishedAnnouncements = (params) => {
  return request({
    url: '/announcements/published',
    method: 'get',
    params
  });
};

export const getAnnouncementDetail = (id) => {
  return request({
    url: `/announcements/${id}`,
    method: 'get'
  });
};

export const createAnnouncement = (data) => {
  return request({
    url: '/announcements',
    method: 'post',
    data,
    timeout: 120000
  });
};

export const remindAnnouncementUnread = (id, senderType) => {
  return request({
    url: `/announcements/${id}/remind?senderType=${senderType}`,
    method: 'post',
    timeout: 120000
  });
};

export const getAnnouncementRecipients = (id) => {
  return request({
    url: `/announcements/${id}/recipients`,
    method: 'get'
  });
};

export const markAnnouncementRead = (id) => {
  return request({
    url: `/announcements/${id}/read`,
    method: 'post'
  });
};

export const getEmailTaskProgress = (taskId) => {
  return request({
    url: `/announcements/email-tasks/${taskId}`,
    method: 'get'
  });
};
