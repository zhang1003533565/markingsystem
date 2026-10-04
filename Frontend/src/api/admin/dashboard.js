import request from '../../utils/request';

export const getAdminDashboardOverview = () => request({
  url: '/admin/dashboard/overview',
  method: 'get'
});

export const getAdminDashboardPortraitOverview = () => request({
  url: '/admin/dashboard/portrait-overview',
  method: 'get'
});
