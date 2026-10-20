import request from '../utils/request';

export const getExtraScoreReviewList = (params) => request({
  url: '/extra-score-review',
  method: 'get',
  params
});

export const getExtraScoreReviewStudentList = (params) => request({
  url: '/extra-score-review/students',
  method: 'get',
  params
});

export const getExtraScoreReviewClassList = (params) => request({
  url: '/extra-score-review/classes',
  method: 'get',
  params
});

export const getExtraScoreReviewWorkbench = (params) => request({
  url: '/extra-score-review/workbench',
  method: 'get',
  params
});

export const getExtraScoreReviewClassProgress = () => request({
  url: '/extra-score-review/class-progress',
  method: 'get',
  silentError: true
});

export const getExtraScoreReviewClassStudents = (className, params) => request({
  url: `/extra-score-review/classes/${encodeURIComponent(className)}/students`,
  method: 'get',
  params
});

export const getExtraScoreReviewStudentApplications = (studentNo, params) => request({
  url: `/extra-score-review/students/${encodeURIComponent(studentNo)}/applications`,
  method: 'get',
  params
});

export const getExtraScoreReviewApplicationDetail = (id) => request({
  url: `/extra-score-review/applications/${id}`,
  method: 'get'
});

export const aiAssistExtraScoreApplication = (id) => request({
  url: `/extra-score-review/${id}/ai-assist`,
  method: 'post'
});

export const committeeReviewExtraScoreApplication = (id, data) => request({
  url: `/extra-score-review/${id}/committee`,
  method: 'post',
  data
});

export const reviewExtraScoreWithdrawal = (id, data) => request({
  url: `/extra-score-review/${id}/withdrawal`,
  method: 'post',
  data
});

export const teacherReviewExtraScoreApplication = (id, data) => request({
  url: `/extra-score-review/${id}/teacher`,
  method: 'post',
  data
});

export const batchApproveExtraScoreApplications = (applicationIds) => request({
  url: '/extra-score-review/batch/approve',
  method: 'post',
  data: { applicationIds }
});

export const batchReturnExtraScoreApplications = (data) => request({
  url: '/extra-score-review/batch/return',
  method: 'post',
  data
});

export const downloadExtraScoreTeacherImportTemplate = (params) => request({
  url: '/extra-score-review/teacher-import/template',
  method: 'get',
  params,
  responseType: 'blob'
});

export const importExtraScoreTeacherExcel = (formData) => request({
  url: '/extra-score-review/teacher-import',
  method: 'post',
  data: formData,
  headers: {
    'Content-Type': 'multipart/form-data'
  }
});

export const deleteExtraScoreTeacherImportRecord = (id) => request({
  url: `/extra-score-review/teacher-import/${id}`,
  method: 'delete'
});

export const forceCurrentClassToTeacherStage = () => request({
  url: '/extra-score-review/committee/force-teacher-stage',
  method: 'post'
});

export const exportExtraScoreProofImages = (params) => request({
  url: '/extra-score-review/proof-images/export',
  method: 'get',
  params,
  responseType: 'blob'
});
