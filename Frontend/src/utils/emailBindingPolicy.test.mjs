import assert from 'node:assert/strict';
import { isEmailBindingRequiredForUser } from './emailBindingPolicy.js';

assert.equal(
  isEmailBindingRequiredForUser({ role: 'STUDENT', email: '' }),
  true
);

assert.equal(
  isEmailBindingRequiredForUser({ role: 'CLASS_COMMITTEE', email: '   ' }),
  true
);

assert.equal(
  isEmailBindingRequiredForUser({ role: 'STUDENT', email: 'student@example.com' }),
  false
);

assert.equal(
  isEmailBindingRequiredForUser({ role: 'STUDENT', email: '', forceEmailBindingRequired: false }),
  false
);

assert.equal(
  isEmailBindingRequiredForUser({ role: 'CLASS_COMMITTEE', email: '', forceEmailBindingRequired: 'false' }),
  false
);

assert.equal(
  isEmailBindingRequiredForUser({ role: 'TEACHER', email: '' }),
  false
);
