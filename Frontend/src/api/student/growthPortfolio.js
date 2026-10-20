import request from '../../utils/request';

/**
 * 学生电子成长档案 - 概览（基本信息、统计、学年级综测快照、学期均分、二课序列）
 */
export const getGrowthSummary = () => request({
  url: '/student/growth-portfolio/summary',
  method: 'get'
});

/**
 * 学生电子成长档案 - 成长时间轴（课程/竞赛/荣誉/志愿/实践/技能证书/第二课堂，按时间倒序）
 */
export const getGrowthTimeline = () => request({
  url: '/student/growth-portfolio/timeline',
  method: 'get'
});

/**
 * 学生电子成长档案 - 求职简历素材（教育经历/荣誉与竞赛/实践经历/技能证书）
 */
export const getGrowthResumeMaterials = () => request({
  url: '/student/growth-portfolio/resume-materials',
  method: 'get'
});
