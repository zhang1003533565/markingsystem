const USER_STORAGE_KEY = 'user';
const AUTH_STORAGE_VERSION = 2;

const ROLE_ALIASES = {
  管理员: 'ADMIN',
  系统管理员: 'ADMIN',
  学生: 'STUDENT',
  教师: 'TEACHER',
  老师: 'TEACHER',
  班委: 'CLASS_COMMITTEE',
  班级委员: 'CLASS_COMMITTEE'
};

const USER_STORAGE_FIELDS = [
  'id',
  'username',
  'nickname',
  'role',
  'teacherId',
  'className',
  'studentNo',
  'avatarUrl',
  'email',
  'phone',
  'passwordResetRequired',
  'forceEmailBindingRequired',
  'authStorageVersion',
  'token'
];

export const normalizeStoredRole = (role) => {
  const value = String(role || '').trim();
  return ROLE_ALIASES[value] || value.toUpperCase();
};

const normalizeBoolean = (value) => {
  if (typeof value === 'boolean') {
    return value;
  }

  if (typeof value === 'string') {
    return value.trim().toLowerCase() === 'true';
  }

  return Boolean(value);
};

const normalizeStoredUser = (user) => {
  if (!user || typeof user !== 'object') {
    return {};
  }

  const normalized = {};

  USER_STORAGE_FIELDS.forEach((field) => {
    const value = user[field];
    if (value === undefined || value === null) {
      return;
    }

    if (field === 'role') {
      normalized.role = normalizeStoredRole(value);
      return;
    }

    if (field === 'passwordResetRequired' || field === 'forceEmailBindingRequired') {
      normalized[field] = normalizeBoolean(value);
      return;
    }

    if (['string', 'number', 'boolean'].includes(typeof value)) {
      normalized[field] = value;
    }
  });

  if (!normalized.role && Array.isArray(user.roles)) {
    normalized.role = normalizeStoredRole(user.roles[0]?.name || user.roles[0]);
  }

  return normalized;
};

const isStorageVersionValid = (user) => {
  return Number(user?.authStorageVersion || 0) === AUTH_STORAGE_VERSION;
};

const parseUser = (rawValue) => {
  if (!rawValue) {
    return {};
  }

  try {
    return JSON.parse(rawValue) || {};
  } catch {
    return {};
  }
};

export const getStoredUser = () => {
  if (typeof window === 'undefined') {
    return {};
  }

  const parsedUser = parseUser(window.sessionStorage.getItem(USER_STORAGE_KEY));
  if (!parsedUser || !Object.keys(parsedUser).length) {
    return {};
  }
  if (!isStorageVersionValid(parsedUser)) {
    clearStoredUser();
    return {};
  }
  return normalizeStoredUser(parsedUser);
};

export const hasStoredUser = () => {
  if (typeof window === 'undefined') {
    return false;
  }

  const parsedUser = parseUser(window.sessionStorage.getItem(USER_STORAGE_KEY));
  if (!parsedUser || !Object.keys(parsedUser).length) {
    return false;
  }
  if (!isStorageVersionValid(parsedUser)) {
    clearStoredUser();
    return false;
  }
  return true;
};

export const setStoredUser = (user) => {
  if (typeof window === 'undefined') {
    return {};
  }

  const normalizedUser = normalizeStoredUser(user);
  normalizedUser.authStorageVersion = AUTH_STORAGE_VERSION;
  window.sessionStorage.setItem(USER_STORAGE_KEY, JSON.stringify(normalizedUser));
  window.localStorage.removeItem(USER_STORAGE_KEY);
  return normalizedUser;
};

export const clearStoredUser = () => {
  if (typeof window === 'undefined') {
    return;
  }

  window.sessionStorage.removeItem(USER_STORAGE_KEY);
  window.localStorage.removeItem(USER_STORAGE_KEY);
};

export const updateStoredUser = (updater) => {
  const currentUser = getStoredUser();
  const nextUser = typeof updater === 'function'
    ? updater(currentUser)
    : { ...currentUser, ...(updater || {}) };

  setStoredUser(nextUser);
  return nextUser;
};

export const withAuthToken = (value) => {
  if (typeof value !== 'string' || !value.includes('/api/uploads/')) {
    return value;
  }
  const token = getStoredUser().token;
  if (!token || typeof window === 'undefined') {
    return value;
  }
  try {
    const url = new URL(value, window.location.origin);
    url.searchParams.set('access_token', token);
    return url.toString();
  } catch {
    return value;
  }
};
