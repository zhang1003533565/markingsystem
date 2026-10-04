import request from '../../utils/request';

export const getImportedSecondClassScores = (params) => {
  return request({
    url: '/admin/second-class-scores',
    method: 'get',
    params
  });
};

export const previewSecondClassScoreFile = (formData) => {
  return request({
    url: '/admin/second-class-scores/preview',
    method: 'post',
    data: formData,
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  });
};

export const importSecondClassScores = (formData, onUploadProgress) => {
  return request({
    url: '/admin/second-class-scores/import',
    method: 'post',
    data: formData,
    headers: {
      'Content-Type': 'multipart/form-data'
    },
    onUploadProgress
  });
};

export const updateImportedSecondClassScore = (id, data) => {
  return request({
    url: `/admin/second-class-scores/${id}`,
    method: 'put',
    data
  });
};
