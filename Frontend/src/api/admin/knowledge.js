import request from '../../utils/request';

/**
 * Admin AI 知识库管理接口（MaxKB 式三级结构：知识库 → 文档 → 分段）
 */

// ---------------- 知识库 ----------------

// 知识库列表（卡片式，含文档数 / 分段数统计）与总览统计
export const getKnowledgeBases = () => {
  return request({
    url: '/admin/knowledge/kbs',
    method: 'get'
  });
};

export const getKnowledgeBase = (id) => {
  return request({
    url: `/admin/knowledge/kbs/${id}`,
    method: 'get'
  });
};

export const createKnowledgeBase = (data) => {
  return request({
    url: '/admin/knowledge/kbs',
    method: 'post',
    data
  });
};

export const updateKnowledgeBase = (id, data) => {
  return request({
    url: `/admin/knowledge/kbs/${id}`,
    method: 'put',
    data
  });
};

export const deleteKnowledgeBase = (id) => {
  return request({
    url: `/admin/knowledge/kbs/${id}`,
    method: 'delete'
  });
};

export const toggleKnowledgeBase = (id) => {
  return request({
    url: `/admin/knowledge/kbs/${id}/toggle`,
    method: 'put'
  });
};

// ---------------- 文档 ----------------

export const getKnowledgeDocuments = (kbId) => {
  return request({
    url: `/admin/knowledge/kbs/${kbId}/documents`,
    method: 'get'
  });
};

// 文档详情（文档信息 + 分段列表）
export const getKnowledgeDocument = (docId) => {
  return request({
    url: `/admin/knowledge/documents/${docId}`,
    method: 'get'
  });
};

// 上传 PDF / Word(.docx) / TXT / Markdown 文档，解析切块后批量导入
// options.silentError 为 true 时不弹全局错误提示（批量上传时由结果列表逐份展示）
export const uploadKnowledgeDocument = (kbId, file, options = {}) => {
  const formData = new FormData();
  formData.append('file', file);
  if (options.category) formData.append('category', options.category);
  if (options.source) formData.append('source', options.source);
  return request({
    url: `/admin/knowledge/kbs/${kbId}/documents/upload`,
    method: 'post',
    data: formData,
    headers: {
      'Content-Type': 'multipart/form-data'
    },
    timeout: 120000,
    silentError: options.silentError === true,
    signal: options.signal
  });
};

export const deleteKnowledgeDocument = (docId) => {
  return request({
    url: `/admin/knowledge/documents/${docId}`,
    method: 'delete'
  });
};

// 重新向量化某文档内的全部分段
export const reembedKnowledgeDocument = (docId) => {
  return request({
    url: `/admin/knowledge/documents/${docId}/re-embed`,
    method: 'post'
  });
};

// ---------------- 分段 ----------------

// 知识库内分段列表（keyword 过滤标题 / 正文 / 关键词）
export const getKnowledgeSegments = (kbId, keyword) => {
  return request({
    url: `/admin/knowledge/kbs/${kbId}/segments`,
    method: 'get',
    params: { keyword }
  });
};

export const createKnowledgeSegment = (kbId, data) => {
  return request({
    url: `/admin/knowledge/kbs/${kbId}/segments`,
    method: 'post',
    data
  });
};

export const updateKnowledgeSegment = (id, data) => {
  return request({
    url: `/admin/knowledge/segments/${id}`,
    method: 'put',
    data
  });
};

export const toggleKnowledgeSegment = (id) => {
  return request({
    url: `/admin/knowledge/segments/${id}/toggle`,
    method: 'put'
  });
};

export const deleteKnowledgeSegment = (id) => {
  return request({
    url: `/admin/knowledge/segments/${id}`,
    method: 'delete'
  });
};

export const reembedKnowledgeSegment = (id) => {
  return request({
    url: `/admin/knowledge/segments/${id}/re-embed`,
    method: 'post'
  });
};

// 补齐缺失向量（kbId 可选，缺省全部）
export const embedMissingKnowledgeSegments = (kbId) => {
  return request({
    url: '/admin/knowledge/segments/embed-missing',
    method: 'post',
    params: { kbId }
  });
};

// 知识库内全部分段重新向量化
export const reembedKnowledgeBase = (kbId) => {
  return request({
    url: `/admin/knowledge/kbs/${kbId}/reembed`,
    method: 'post'
  });
};

// 全部分段重新向量化（数据维护用）
export const reembedKnowledgeAll = () => {
  return request({
    url: '/admin/knowledge/reembed',
    method: 'post'
  });
};

// 检索预览（管理员侧验证检索效果；kbId 可选，缺省检索全部启用库）
export const searchKnowledge = (query, topK, kbId) => {
  return request({
    url: '/admin/knowledge/search',
    method: 'get',
    params: { query, topK, kbId }
  });
};

// ---------------- 配置（全局） ----------------

// 读取 embedding 配置
export const getEmbeddingConfig = () => {
  return request({
    url: '/admin/knowledge/embedding-config',
    method: 'get'
  });
};

// 保存 embedding 配置（apiKey 为空表示不修改已保存的 Key）
export const saveEmbeddingConfig = (data) => {
  return request({
    url: '/admin/knowledge/embedding-config',
    method: 'put',
    data
  });
};

// 测试 embedding 连接（body 可选：baseUrl / model / apiKey 仅用于本次测试，不落库）
export const testEmbeddingConfig = (data) => {
  return request({
    url: '/admin/knowledge/embedding-config/test',
    method: 'post',
    data
  });
};

// 读取检索与分段参数（Top-K / 相似度阈值 / 文档切块长度）
export const getRetrievalConfig = () => {
  return request({
    url: '/admin/knowledge/retrieval-config',
    method: 'get'
  });
};

// 保存检索与分段参数
export const saveRetrievalConfig = (data) => {
  return request({
    url: '/admin/knowledge/retrieval-config',
    method: 'put',
    data
  });
};
