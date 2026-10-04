import request from '../../utils/request';

let studentAcademicYearsCache = null;
let studentAcademicYearsRequest = null;

export const getStudentPortalInfo = () => request({
  url: '/student/info',
  method: 'get'
});

export const getStudentPortalScores = (params) => request({
  url: '/student/scores',
  method: 'get',
  params
});

export const getStudentVisibleSubjectScores = () => request({
  url: '/subject-scores/rain/visible',
  method: 'get'
});

export const getStudentSecondClassScores = (params) => request({
  url: '/student/second-class-scores',
  method: 'get',
  params
});

export const getStudentPortalTrend = (studentId) => request({
  url: `/student/scores/trend/${studentId}`,
  method: 'get'
});

export const getStudentPortalClassStats = (className) => request({
  url: `/student/class/stats/${encodeURIComponent(className)}`,
  method: 'get'
});

export const getStudentPortalClassLeaderboard = (params) => request({
  url: '/student/leaderboards/class',
  method: 'get',
  params
});

export const getStudentPortalGradeLeaderboard = (params) => request({
  url: '/student/leaderboards/grade',
  method: 'get',
  params
});

export const getStudentHomeSummary = (params) => request({
  url: '/student/home/summary',
  method: 'get',
  params
});

export const getStudentCompositeScoreSnapshot = (params) => request({
  url: '/student/composite-score/snapshot',
  method: 'get',
  params
});

export const getStudentCompositeExtraScoreDetails = (params) => request({
  url: '/student/composite-score/extra-details',
  method: 'get',
  params
});

export const getStudentCompositeBaseScoreDetails = (params) => request({
  url: '/student/composite-score/base-details',
  method: 'get',
  params
});

export const getStudentWeeklyJobRecommendations = () => request({
  url: '/student/job-recommendations/latest',
  method: 'get',
  timeout: 30000,
  silentError: true
});

export const refreshStudentWeeklyJobRecommendations = () => request({
  url: '/student/job-recommendations/refresh',
  method: 'post',
  timeout: 30000
});

export const getStudentProfileAnalysis = (params) => request({
  url: '/student/analysis/profile',
  method: 'get',
  params,
  timeout: 30000
});

export const getStudentProfileAnalysisHistory = (params) => request({
  url: '/student/analysis/profile/history',
  method: 'get',
  params
});

export const getStudentPortraitMatch = (params) => request({
  url: '/student/analysis/portrait-match',
  method: 'get',
  params,
  timeout: 30000
});

export const changeStudentPassword = (data) => request({
  url: '/student/password/change',
  method: 'post',
  data
});

export const uploadStudentAvatar = (formData) => request({
  url: '/student/profile/avatar',
  method: 'post',
  data: formData,
  headers: {
    'Content-Type': 'multipart/form-data'
  }
});

export const updateStudentProfile = (id, data) => request({
  url: `/student/profile/${id}`,
  method: 'put',
  data
});

export const sendStudentEmailVerificationCode = (data) => request({
  url: '/student/profile/email/verification-code',
  method: 'post',
  data
});

export const bindStudentEmail = (data) => request({
  url: '/student/profile/email/bind',
  method: 'post',
  data
});

export const getStudentAcademicYears = (config = {}) => {
  if (studentAcademicYearsCache && !config.forceRefresh) {
    return Promise.resolve(studentAcademicYearsCache);
  }
  if (studentAcademicYearsRequest && !config.forceRefresh) {
    return studentAcademicYearsRequest;
  }

  const requestConfig = { ...config };
  delete requestConfig.forceRefresh;
  studentAcademicYearsRequest = request({
    url: '/student/academic-years',
    method: 'get',
    ...requestConfig
  })
    .then((response) => {
      studentAcademicYearsCache = response;
      return response;
    })
    .catch((error) => {
      studentAcademicYearsCache = null;
      throw error;
    })
    .finally(() => {
      studentAcademicYearsRequest = null;
    });

  return studentAcademicYearsRequest;
};
