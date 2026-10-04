import assert from 'node:assert/strict';
import { getPasswordChangeRedirectPath } from './passwordRedirect.js';

assert.equal(getPasswordChangeRedirectPath({ role: 'ADMIN' }), '/admin/home');
assert.equal(getPasswordChangeRedirectPath({ role: 'TEACHER' }), '/admin/home');
assert.equal(getPasswordChangeRedirectPath({ role: 'STUDENT' }), '/student/home');
assert.equal(getPasswordChangeRedirectPath({ role: 'CLASS_COMMITTEE' }), '/student/home');
