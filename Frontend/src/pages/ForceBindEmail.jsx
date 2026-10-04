import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  bindCurrentUserEmail,
  sendCurrentUserEmailVerificationCode
} from '../api/account';
import { getStoredUser, setStoredUser } from '../utils/authStorage';
import { getPasswordChangeRedirectPath } from '../utils/passwordRedirect';
import './ForceBindEmail.css';

function ForceBindEmail() {
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [code, setCode] = useState('');
  const [message, setMessage] = useState('');
  const [sendingCode, setSendingCode] = useState(false);
  const [binding, setBinding] = useState(false);

  const currentUser = getStoredUser();

  const goHome = (nextUser) => {
    navigate(getPasswordChangeRedirectPath(nextUser), { replace: true });
  };

  const normalizeEmail = () => email.trim().toLowerCase();

  const validateEmail = () => {
    const value = normalizeEmail();
    if (!value) {
      return '请输入邮箱';
    }
    if (!/^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/.test(value)) {
      return '邮箱格式不正确';
    }
    return '';
  };

  const handleSendCode = async () => {
    const error = validateEmail();
    if (error) {
      setMessage(error);
      return;
    }

    setMessage('');
    setSendingCode(true);
    try {
      await sendCurrentUserEmailVerificationCode({ email: normalizeEmail() });
      setMessage('验证码已发送，请查看邮箱');
    } catch (error) {
      setMessage(error?.response?.data?.message || error?.message || '验证码发送失败');
    } finally {
      setSendingCode(false);
    }
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    const emailError = validateEmail();
    if (emailError) {
      setMessage(emailError);
      return;
    }
    if (!/^\d{6}$/.test(code.trim())) {
      setMessage('请输入6位邮箱验证码');
      return;
    }

    setMessage('');
    setBinding(true);
    try {
      const response = await bindCurrentUserEmail({
        email: normalizeEmail(),
        code: code.trim()
      });
      const boundEmail = response?.data?.email || normalizeEmail();
      const nextUser = {
        ...currentUser,
        email: boundEmail
      };
      setStoredUser(nextUser);
      goHome(nextUser);
    } catch (error) {
      setMessage(error?.response?.data?.message || error?.message || '邮箱绑定失败');
    } finally {
      setBinding(false);
    }
  };

  return (
    <div className="force-email-page">
      <form className="force-email-card" onSubmit={handleSubmit}>
        <div className="force-email-header">
          <span className="force-email-mark">邮</span>
          <div>
            <h1>请先绑定邮箱</h1>
            <p>邮箱用于接收验证码、通知和账号安全提醒，绑定完成后才能继续使用系统。</p>
          </div>
        </div>

        {message ? <div className="force-email-message">{message}</div> : null}

        <label>
          邮箱地址 <span>*</span>
          <input
            type="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            placeholder="请输入可接收邮件的邮箱"
            autoComplete="email"
          />
        </label>

        <label>
          邮箱验证码 <span>*</span>
          <div className="force-email-code-row">
            <input
              type="text"
              value={code}
              onChange={(event) => setCode(event.target.value.replace(/\D/g, '').slice(0, 6))}
              placeholder="6位验证码"
              inputMode="numeric"
            />
            <button type="button" onClick={handleSendCode}>
              {sendingCode ? '发送中...' : '获取验证码'}
            </button>
          </div>
        </label>

        <button className="force-email-submit" type="submit">
          {binding ? '绑定中...' : '绑定并进入系统'}
        </button>
      </form>
    </div>
  );
}

export default ForceBindEmail;
