import request from '../../utils/request';

/**
 * Admin 成绩导入接口
 */

// 上传成绩文件
export const uploadScoreFile = (formData) => {
  return request({
    url: '/scores/import',
    method: 'post',
    data: formData,
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  });
};

// 获取导入历史记录
export const getImportHistory = (params) => {
  return request({
    url: '/scores/import/history',
    method: 'get',
    params
  });
};

// 获取导入详情
export const getImportDetail = (id) => {
  return request({
    url: `/scores/import/${id}`,
    method: 'get'
  });
};

// 删除导入记录
export const deleteImportRecord = (id) => {
  return request({
    url: `/scores/import/${id}`,
    method: 'delete'
  });
};

// 批量删除导入记录
export const batchDeleteImportRecords = (ids) => {
  return request({
    url: '/scores/import/batch',
    method: 'delete',
    data: { ids }
  });
};

// 下载导入模板
export const downloadTemplate = () => {
  return request({
    url: '/scores/template',
    method: 'get',
    responseType: 'blob'
  });
};

export const previewRainSubjectScores = (formData) => {
  return request({
    url: '/subjects/rain/preview',
    method: 'post',
    data: formData,
    timeout: 60000,
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  });
};

export const importRainSubjectScores = (formData) => {
  return request({
    url: '/subjects/rain/import',
    method: 'post',
    data: formData,
    timeout: 120000,
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  });
};

export const getSubjectScoreImportBatches = () => {
  return request({
    url: '/subjects/rain/import/batches',
    method: 'get'
  });
};

// 导入前试算校验（dry-run）：不写库，返回与导入一致的变更推演结果
export const validateRainSubjectScoreImport = (formData) => {
  return request({
    url: '/subjects/rain/import/validate',
    method: 'post',
    data: formData,
    timeout: 120000,
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  });
};

export const getSubjectScoreImportStudentRecords = (params) => {
  return request({
    url: '/subjects/rain/import/student-records',
    method: 'get',
    params
  });
};

export const getSubjectScoreImportStudentRecordsByBatch = (batchId, params) => {
  return request({
    url: `/subjects/rain/import/student-records/${encodeURIComponent(batchId)}`,
    method: 'get',
    params
  });
};

export const deleteSubjectScoreImportStudentRecord = (batchId, studentId) => {
  return request({
    url: `/subjects/rain/import/student-records/${encodeURIComponent(batchId)}/${encodeURIComponent(studentId)}`,
    method: 'delete'
  });
};

export const undoSubjectScoreImportBatch = (batchId) => {
  return request({
    url: `/subjects/rain/import/undo/${batchId}`,
    method: 'post'
  });
};
