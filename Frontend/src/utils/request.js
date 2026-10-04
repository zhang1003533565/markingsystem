  import axios from 'axios';
  import { message } from 'antd';
  import { clearStoredUser, getStoredUser } from './authStorage';

  // 创建 axios 实例
  const request = axios.create({
    baseURL: '/api',
    timeout: 10000,
    headers: {
      'Content-Type': 'application/json'
    }
  });

  // 请求拦截器
  request.interceptors.request.use(
    (config) => {
      // 从 localStorage 获取 token
      const user = getStoredUser();
      if (user.token) {
        config.headers.Authorization = `Bearer ${user.token}`;
      }

      // 添加用户信息到头信息(后端需要)
      if (user.id) {
        config.headers['X-User-Id'] = user.id;
      }
      if (user.role) {
        config.headers['X-User-Role'] = user.role;
      }
      if (user.teacherId) {
        config.headers['X-Teacher-Id'] = user.teacherId;
      } else if (user.role === 'TEACHER' && user.id) {
        config.headers['X-Teacher-Id'] = user.id;
      }
      if (user.nickname || user.username) {
        config.headers['X-User-Name'] = encodeURIComponent(user.nickname || user.username);
      }
      if (user.className) {
        config.headers['X-Class-Name'] = encodeURIComponent(user.className);
      }
      if (user.studentNo) {
        config.headers['X-Student-No'] = user.studentNo;
      }

      return config;
    },
    (error) => {
      return Promise.reject(error);
    }
  );

  // 响应拦截器
  request.interceptors.response.use(
    (response) => {
      const { data } = response;

      // 如果响应中有 code 字段，按统一格式处理
      if (data.code !== undefined) {
        if (data.code !== 200) {
          const silent = Boolean(response.config?.silentError);
          if (!silent) message.error(data.message || '请求失败');
          return Promise.reject(new Error(data.message));
        }
        return data;
      }

      // 如果没有 code 字段，直接返回数据（兼容 Spring Data 分页格式）
      return data;
    },
    (error) => {
      const { response } = error;

      const silent = Boolean(error?.config?.silentError);

      if (response) {
        switch (response.status) {
          case 401:
            message.error('登录已过期，请重新登录');
            clearStoredUser();
            window.location.href = '/login';
            break;
          case 403:
            if (!silent) message.error('没有权限访问');
            break;
          case 404:
            if (!silent) message.error('请求的资源不存在');
            break;
          case 500:
            if (!silent) message.error(response.data?.message || '服务器错误');
            break;
          default:
            if (!silent) message.error(response.data?.message || '网络错误');
        }
      } else if (error.code === 'ECONNABORTED') {
        if (!silent) message.error('请求超时，请稍后查看结果或重试');
      } else {
        const rawMessage = String(error?.message || '').toLowerCase();
        const requestUrl = error?.config?.url || '';
        const isApiRequest = requestUrl.startsWith('/admin/')
          || requestUrl.startsWith('/student/')
          || requestUrl.startsWith('/auth/')
          || requestUrl.startsWith('/user/')
          || requestUrl.startsWith('/announcement/')
          || requestUrl.startsWith('/roles/')
          || requestUrl.startsWith('/extra-score-review')
          || requestUrl.startsWith('/subject')
          || requestUrl.startsWith('/health');

        if (rawMessage.includes('network error')
          || rawMessage.includes('failed to fetch')
          || rawMessage.includes('err_network')
          || rawMessage.includes('err_connection_refused')
          || rawMessage.includes('err_connection_reset')
          || rawMessage.includes('err_empty_response')) {
          if (!silent) message.error(isApiRequest ? '后端服务未响应，请检查 8080 服务或代理连接' : '网络连接失败');
        } else if (rawMessage.includes('timeout')) {
          if (!silent) message.error(isApiRequest ? '请求处理超时，请检查后端接口性能或是否卡住' : '请求超时');
        } else {
          if (!silent) message.error('请求失败，请稍后重试');
        }
      }

      return Promise.reject(error);
    }
  );

  export default request;
