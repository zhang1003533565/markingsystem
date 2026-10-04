import { normalizeStoredRole } from './authStorage.js';

export const normalizeForceEmailBindingRequired = (value) => {
  if (value === undefined || value === null || value === '') {
    return true;
  }

  if (typeof value === 'boolean') {
    return value;
  }

  if (typeof value === 'string') {
    return value.trim().toLowerCase() !== 'false';
  }

  return Boolean(value);
};

export const isEmailBindingRequiredForUser = (user = {}) => {
  if (!normalizeForceEmailBindingRequired(user.forceEmailBindingRequired)) {
    return false;
  }

  const role = normalizeStoredRole(user.role);
  return (role === 'STUDENT' || role === 'CLASS_COMMITTEE') && !String(user.email || '').trim();
};
