import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { login } from '../api/auth';
import './Login.css';
// 替换为你项目内的图片路径（按实际存放位置修改）
import schoolLogo from '../../assets/school-logo.png';

function Login({ onLoginSuccess }) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!username || !password) {
      setError('请输入用户名和密码');
      return;
    }
    setLoading(true);
    setError('');
    try {
      const res = await login({ username, password });
      if (res.code === 200) {
        localStorage.setItem('user', JSON.stringify(res.data));
        // 如果有传入onLoginSuccess回调，则调用它
        if (onLoginSuccess) {
          onLoginSuccess();
        } else {
          navigate('/');
        }
      } else {
        setError(res.message || '登录失败');
      }
    } catch (err) {
      setError(err.message || '登录失败，请检查网络');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="login-container">
      {/* 官网同款校园背景 */}
      <div className="login-bg" style={{ backgroundImage: `url(/lulu.jpg)` }}></div>
      
      {/* 顶部导航栏（1:1复刻官网样式） */}
      <header className="login-header">
        < img src={schoolLogo} alt="河北建筑工程学院校徽" className="school-logo" />
        <div className="school-title">
          <h1>河北建筑工程学院</h1>
          <p>Hebei University of Architecture</p >
        </div>
      </header>

      {/* 登录卡片（完全保留原结构、原提示、原文案） */}
      <main className="login-form-section">
        <div className="login-box">
          <h2>用户登录</h2>
          <p className="login-desc">请使用您的账号密码登录系统</p >
          
          {error && <div className="error-message">{error}</div>}
          
          <form onSubmit={handleSubmit}>
            <div className="form-group">
              <label htmlFor="username">用户名</label>
              <input
                type="text"
                id="username"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                placeholder="请输入用户名"
                disabled={loading}
                required
              />
            </div>
            <div className="form-group">
              <label htmlFor="password">密码</label>
              <input
                type="password"
                id="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="请输入密码"
                disabled={loading}
                required
              />
            </div>
            <button type="submit" className="login-btn" disabled={loading}>
              {loading ? '登录中...' : '登录'}
            </button>
          </form>
          
          <div className="login-tips">
            <p>测试账号：admin / 123456</p >
          </div>
        </div>
      </main>

      {/* 底部版权信息（和官网风格统一） */}
      <footer className="login-footer">
        <p>© 2026 河北建筑工程学院 版权所有</p >
        <p>官网：https://www.hebiace.edu.cn/</p >
      </footer>
    </div>
  );
}

export default Login;