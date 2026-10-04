import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { login } from '../api/auth';
import { clearStoredUser, setStoredUser } from '../utils/authStorage';
import { isEmailBindingRequiredForUser } from '../utils/emailBindingPolicy';
import { getPasswordChangeRedirectPath } from '../utils/passwordRedirect';
import './Login.css';

const REMEMBERED_LOGIN_KEY = 'marking-system-remembered-login';
const REMEMBERED_LOGIN_LIST_KEY = 'marking-system-remembered-login-list';
const LEGACY_REMEMBERED_USERNAME_KEY = 'marking-system-remembered-username';

const readRememberedLogins = () => {
  try {
    const savedList = JSON.parse(localStorage.getItem(REMEMBERED_LOGIN_LIST_KEY) || '[]');
    if (Array.isArray(savedList)) {
      return savedList.filter((item) => item?.username && item?.password);
    }
  } catch {
    localStorage.removeItem(REMEMBERED_LOGIN_LIST_KEY);
  }

  return [];
};

const saveRememberedLogins = (logins) => {
  if (logins.length > 0) {
    localStorage.setItem(REMEMBERED_LOGIN_LIST_KEY, JSON.stringify(logins));
  } else {
    localStorage.removeItem(REMEMBERED_LOGIN_LIST_KEY);
  }
};

const resolveLoginUser = (response) => {
  const isWrappedSuccess = Number(response?.code) === 200;
  const user = isWrappedSuccess ? response?.data : response?.data || response;

  if (user && (user.id || user.username || user.role)) {
    return user;
  }

  return null;
};

function Login({ onLoginSuccess }) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [rememberLogin, setRememberLogin] = useState(false);
  const [rememberedLogins, setRememberedLogins] = useState([]);
  const [showRememberedLogins, setShowRememberedLogins] = useState(false);
  const navigate = useNavigate();

  useEffect(() => {
    clearStoredUser();

    const savedLogins = readRememberedLogins();
    const rememberedLogin = localStorage.getItem(REMEMBERED_LOGIN_KEY);
    if (rememberedLogin) {
      try {
        const parsedLogin = JSON.parse(rememberedLogin);
        if (parsedLogin?.username && parsedLogin?.password) {
          savedLogins.unshift(parsedLogin);
        }
      } catch {
        localStorage.removeItem(REMEMBERED_LOGIN_KEY);
      }
    }

    const uniqueLogins = savedLogins.filter(
      (loginItem, index, logins) =>
        logins.findIndex((item) => item.username === loginItem.username) === index,
    );
    setRememberedLogins(uniqueLogins);
    saveRememberedLogins(uniqueLogins);
    localStorage.removeItem(REMEMBERED_LOGIN_KEY);

    if (uniqueLogins.length > 0) {
      setUsername(uniqueLogins[0].username);
      setPassword(uniqueLogins[0].password);
      setRememberLogin(true);
      return;
    }

    const rememberedUsername = localStorage.getItem(LEGACY_REMEMBERED_USERNAME_KEY);
    if (rememberedUsername) {
      setUsername(rememberedUsername);
      setRememberLogin(true);
    }
  }, []);

  const handleUsernameChange = (e) => {
    const nextUsername = e.target.value;
    const selectedLogin = rememberedLogins.find((item) => item.username === nextUsername);

    setUsername(nextUsername);
    if (selectedLogin) {
      setPassword(selectedLogin.password);
      setRememberLogin(true);
    }
  };

  const handleRememberedLoginSelect = (selectedUsername) => {
    const selectedLogin = rememberedLogins.find((item) => item.username === selectedUsername);
    if (!selectedLogin) {
      return;
    }

    setUsername(selectedLogin.username);
    setPassword(selectedLogin.password);
    setRememberLogin(true);
    setShowRememberedLogins(false);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();

    if (!username || !password) {
      setError('请输入用户名和密码');
      return;
    }

    setLoading(true);
    setError('');
    clearStoredUser();

    try {
      const res = await login({ username, password });
      const loginUser = resolveLoginUser(res);
      if (loginUser) {
        const nextRememberedLogins = rememberedLogins.filter((item) => item.username !== username);
        if (rememberLogin) {
          nextRememberedLogins.unshift({ username, password });
        }
        setRememberedLogins(nextRememberedLogins);
        saveRememberedLogins(nextRememberedLogins);
        localStorage.removeItem(LEGACY_REMEMBERED_USERNAME_KEY);

        const storedUser = setStoredUser(loginUser);
        if (onLoginSuccess) {
          onLoginSuccess(storedUser);
        } else {
          if (storedUser.passwordResetRequired) {
            navigate('/force-change-password', { replace: true });
            return;
          }
          if (isEmailBindingRequiredForUser(storedUser)) {
            navigate('/force-bind-email', { replace: true });
            return;
          }
          navigate(getPasswordChangeRedirectPath(storedUser), { replace: true });
        }
      } else {
        setError(res.message || '登录失败');
      }
    } catch (err) {
      const serverMessage = err.response?.data?.message;
      setError(serverMessage || err.message || '登录失败，请检查网络');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="login-container">
      <div className="login-form-section">
        <div className="login-box">
          <h2>用户登录</h2>
          <p className="login-desc">请使用您的账号密码登录系统</p>

          {error && <div className="error-message">{error}</div>}

          <form onSubmit={handleSubmit}>
            <div className="form-group">
              <label htmlFor="username">用户名</label>
              <div className="username-input-wrapper">
                <input
                  type="text"
                  id="username"
                  value={username}
                  onChange={handleUsernameChange}
                  placeholder="请输入用户名"
                  autoComplete="username"
                />
                {rememberedLogins.length > 0 ? (
                  <button
                    type="button"
                    className="remembered-login-toggle"
                    onClick={() => setShowRememberedLogins((visible) => !visible)}
                    aria-label="选择历史账号"
                    aria-expanded={showRememberedLogins}
                  >
                    <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">
                      <path d="M6 9l6 6 6-6" />
                    </svg>
                  </button>
                ) : null}
                {showRememberedLogins && rememberedLogins.length > 0 ? (
                  <div className="remembered-login-menu">
                    {rememberedLogins.map((item) => (
                      <button
                        type="button"
                        key={item.username}
                        className="remembered-login-option"
                        onClick={() => handleRememberedLoginSelect(item.username)}
                      >
                        {item.username}
                      </button>
                    ))}
                  </div>
                ) : null}
              </div>
            </div>

            <div className="form-group">
              <label htmlFor="password">密码</label>
              <div className="password-input-wrapper">
                <input
                  type={showPassword ? 'text' : 'password'}
                  id="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="请输入密码"
                  autoComplete="current-password"
                />
                <button
                  type="button"
                  className="password-toggle"
                  onClick={() => setShowPassword((visible) => !visible)}
                  aria-label={showPassword ? '隐藏密码' : '显示密码'}
                  aria-pressed={showPassword}
                >
                  <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">
                    <path d="M2.3 11.1C3.5 9.3 7 5 12 5s8.5 4.3 9.7 6.1a1.6 1.6 0 0 1 0 1.8C20.5 14.7 17 19 12 19s-8.5-4.3-9.7-6.1a1.6 1.6 0 0 1 0-1.8Z" />
                    <circle cx="12" cy="12" r="3" />
                  </svg>
                </button>
              </div>
            </div>

            <label className="remember-login">
              <input
                type="checkbox"
                checked={rememberLogin}
                onChange={(e) => setRememberLogin(e.target.checked)}
              />
              <span>记住账号和密码</span>
            </label>

            <button type="submit" className="login-btn" disabled={loading}>
              {loading ? '登录中...' : '登录'}
            </button>
          </form>
        </div>
      </div>
    </div>
  );
}

export default Login;
