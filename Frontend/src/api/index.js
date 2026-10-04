/**
 * API 统一入口
 * 
 * 使用方式：
 * import { adminApi, userApi, authApi } from '@/api';
 * 
 * 或按需引入：
 * import { getStudentList, createStudent } from '@/api/admin';
 */

// Admin API（管理权限）
export * as adminApi from './admin';

// User API（普通用户权限）
export * as userApi from './user';

// 认证相关
export * from './auth';

// 用户管理（保留原有的）
export * from './user.js';
