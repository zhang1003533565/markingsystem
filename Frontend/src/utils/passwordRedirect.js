import { normalizeStoredRole } from './authStorage.js';

export const getPasswordChangeRedirectPath = (user = {}) => {
  const role = normalizeStoredRole(user.role);

  if (role === 'ADMIN' || role === 'TEACHER') {
    return '/admin/home';
  }

  return '/student/home';
};
