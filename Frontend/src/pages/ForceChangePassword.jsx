import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { changeCurrentUserPassword } from '../api/account';
import { clearStoredUser, getStoredUser, setStoredUser } from '../utils/authStorage';
import { getPasswordChangeRedirectPath } from '../utils/passwordRedirect';
import resetPasswordBackground from '../assets/index/re-password.png';
import schoolImage from '../assets/index/school.png';
import './ForceChangePassword.css';

function ForceChangePassword() {
  const navigate = useNavigate();
  const [form, setForm] = useState({
    oldPassword: '',
    newPassword: '',
    confirmPassword: ''
  });
  const [message, setMessage] = useState('');
  const [loading, setLoading] = useState(false);

  const currentUser = getStoredUser();

  const goHome = () => {
    const user = getStoredUser();
    window.location.href = getPasswordChangeRedirectPath(user);
  };

  const returnToLogin = () => {
    clearStoredUser();
    navigate('/login', { replace: true });
  };

  const validatePasswordStrength = (value) => {
    if (value.length < 8 || value.length > 16) {
      return '密码长度必须为 8-16 位';
    }
    if (!/[a-z]/.test(value)) {
      return '密码必须包含小写字母';
    }
    if (!/[A-Z]/.test(value)) {
      return '密码必须包含大写字母';
    }
    if (!/\d/.test(value)) {
      return '密码必须包含数字';
    }
    if (!/[^A-Za-z0-9]/.test(value)) {
      return '密码必须包含特殊符号';
    }
    for (let index = 0; index < value.length - 2; index += 1) {
      const segment = value.slice(index, index + 3);
      if (!/^\d{3}$/.test(segment)) {
        continue;
      }
      const digits = segment.split('').map(Number);
      if (
        (digits[1] === digits[0] + 1 && digits[2] === digits[1] + 1)
        || (digits[1] === digits[0] - 1 && digits[2] === digits[1] - 1)
      ) {
        return '密码中的数字不能连续递增或连续递减';
      }
    }
    return '';
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    setMessage('');

    if (!form.oldPassword || !form.newPassword || !form.confirmPassword) {
      setMessage('请完整填写原密码、新密码和确认密码');
      return;
    }

    const passwordError = validatePasswordStrength(form.newPassword);
    if (passwordError) {
      setMessage(passwordError);
      return;
    }

    if (form.newPassword !== form.confirmPassword) {
      setMessage('两次输入的新密码不一致');
      return;
    }

    if (form.oldPassword === form.newPassword) {
      setMessage('新密码不能和原密码相同');
      return;
    }

    setLoading(true);
    try {
      await changeCurrentUserPassword({
        oldPassword: form.oldPassword,
        newPassword: form.newPassword
      });
      setStoredUser({
        ...currentUser,
        passwordResetRequired: false
      });
      goHome();
    } catch (error) {
      setMessage(error?.response?.data?.message || error?.message || '密码修改失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div
      className="force-password-page"
      style={{ '--force-password-bg': `url(${resetPasswordBackground})` }}
    >
      <div className="force-password-shell">
        <img className="force-password-top-image" src={schoolImage} alt="校园插图" />
        <form className="force-password-card" onSubmit={handleSubmit}>
          <div className="force-password-header">
            <div>
              <h1>首次登录请修改密码</h1>
              <p>当前账号仍在使用初始密码或管理员重置密码，修改后才能继续使用系统。</p>
            </div>
          </div>

          {message ? <div className="force-password-message">{message}</div> : null}

          <label>
            原密码<span>*</span>
            <input
              type="password"
              value={form.oldPassword}
              onChange={(event) => setForm((prev) => ({ ...prev, oldPassword: event.target.value }))}
              placeholder="请输入当前密码"
              autoComplete="current-password"
            />
          </label>

          <label>
            新密码<span>*</span>
            <input
              type="password"
              value={form.newPassword}
              onChange={(event) => setForm((prev) => ({ ...prev, newPassword: event.target.value }))}
              placeholder="8-16位，含大小写、数字、特殊符号"
              autoComplete="new-password"
            />
          </label>

          <div className="force-password-rule">
            密码长度需为 8-16 位，必须包含大小写字母、数字和特殊符号，且不能出现连续递增或递减的数字片段。
          </div>

          <label>
            确认新密码<span>*</span>
            <input
              type="password"
              value={form.confirmPassword}
              onChange={(event) => setForm((prev) => ({ ...prev, confirmPassword: event.target.value }))}
              placeholder="再次输入新密码"
              autoComplete="new-password"
            />
          </label>

          <button type="submit" disabled={loading}>
            {loading ? '保存中...' : '保存并进入系统'}
          </button>
          <button
            type="button"
            className="force-password-secondary-button"
            disabled={loading}
            onClick={returnToLogin}
          >
            暂不修改，返回登录
          </button>
        </form>
      </div>
    </div>
  );
}

export default ForceChangePassword;
