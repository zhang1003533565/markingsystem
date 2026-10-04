import request from '../../utils/request';

export const getFavoriteIds = () => request({
  url: '/student/job-favorites/ids',
  method: 'get'
});

export const getFavorites = () => request({
  url: '/student/job-favorites',
  method: 'get'
});

export const addFavorite = (recommendationId) => request({
  url: `/student/job-favorites/${recommendationId}`,
  method: 'post'
});

export const removeFavorite = (recommendationId) => request({
  url: `/student/job-favorites/${recommendationId}`,
  method: 'delete'
});
