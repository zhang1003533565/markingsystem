import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ArrowDownOutlined, HeartOutlined, LogoutOutlined } from './AcademicIcons';
import { withAuthToken } from '../utils/authStorage';
import { isAssistantPath, navigateWithViewTransition } from '../utils/viewTransition';
import schoolLogo from '../assets/index/school.png';
import './StudentPortalHeader.css';

function StudentPortalHeader({ activePath = '', user = {}, userRole, onLogout }) {
  const navigate = useNavigate();
  const headerRef = useRef(null);
  const [openMenuKey, setOpenMenuKey] = useState('');
  const isCommittee = (userRole || user?.role) === 'CLASS_COMMITTEE';
  const isStudentUser = ['STUDENT', 'CLASS_COMMITTEE'].includes(userRole || user?.role);
  const needsEmailBinding = isStudentUser && !user?.email;
  const displayName = user?.nickname || user?.username || '同学';
  const avatarText = displayName.trim().charAt(0).toUpperCase() || 'S';

  const navItems = useMemo(() => {
    const items = [
      { key: 'home', label: '首页', path: '/student/home', icon: 'home' },
      { key: 'scores', label: '成绩', path: '/student/scores', icon: 'scores' },
      { key: 'announcements', label: '公告', path: '/student/announcements', icon: 'announcement' },
      {
        key: 'extra',
        label: '附加分',
        path: '/student/extra',
        icon: 'extra',
        matchPrefix: '/student/extra',
        children: [
          { key: 'extra-apply', label: '附加分申请', path: '/student/extra' },
          { key: 'extra-process', label: '审核流程', path: '/student/extra/process' },
          { key: 'extra-result', label: '计入结果', path: '/student/extra/result' },
          { key: 'extra-history', label: '历史明细', path: '/student/extra/history' }
        ]
      },
      {
        key: 'assistant',
        label: '发展助手',
        path: '/student/assistant',
        icon: 'assistant'
      },
      { key: 'ai-chat', label: 'AI 对话', path: '/student/ai-chat', icon: 'assistant' },
      { key: 'profile', label: '个人中心', path: '/student/profile', icon: 'profile', matchPrefix: '/student/profile' }
    ];

    if (isCommittee) {
      items.push({ key: 'class', label: '班级事务', path: '/student/class/stats', icon: 'class', matchPrefix: '/student/class' });
    }

    return items;
  }, [isCommittee]);

  const isItemActive = (item) => {
    if (!activePath) {
      return false;
    }
    if (item.path === activePath) {
      return true;
    }
    if (item.matchPrefix && activePath.startsWith(item.matchPrefix)) {
      return true;
    }
    if (item.children?.some((child) => child.path === activePath)) {
      return true;
    }
    return false;
  };

  const closeMenuAndNavigate = (path) => {
    setOpenMenuKey('');
    if (isAssistantPath(path)) {
      navigateWithViewTransition(navigate, path);
      return;
    }
    navigate(path);
  };

  useEffect(() => {
    if (!openMenuKey) {
      return undefined;
    }

    const handlePointerDown = (event) => {
      if (!headerRef.current?.contains(event.target)) {
        setOpenMenuKey('');
      }
    };

    const handleKeyDown = (event) => {
      if (event.key === 'Escape') {
        setOpenMenuKey('');
      }
    };

    document.addEventListener('pointerdown', handlePointerDown);
    document.addEventListener('keydown', handleKeyDown);

    return () => {
      document.removeEventListener('pointerdown', handlePointerDown);
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [openMenuKey]);

  return (
    <header ref={headerRef} className="student-portal-header">
      <div
        className="student-portal-brand"
        onClick={() => closeMenuAndNavigate('/student/home')}
        role="button"
        tabIndex={0}
        onKeyDown={(event) => {
          if (event.key === 'Enter' || event.key === ' ') {
            closeMenuAndNavigate('/student/home');
          }
        }}
      >
        <img className="student-portal-brand-logo" src={schoolLogo} alt="河北建筑工程学院" />
      </div>

      <nav className="student-portal-nav">
        {navItems.map((item) => (
          <div
            key={item.key}
            className={`student-portal-nav-group ${item.children?.length ? 'has-children' : ''}`}
          >
            <button
              type="button"
              className={`student-portal-nav-item ${isItemActive(item) ? 'active' : ''}`}
              aria-expanded={item.children?.length ? openMenuKey === item.key : undefined}
              onClick={() => {
                if (item.children?.length) {
                  setOpenMenuKey((prev) => (prev === item.key ? '' : item.key));
                  return;
                }
                closeMenuAndNavigate(item.path);
              }}
            >
              <span>{item.label}</span>
              {item.children?.length ? (
                item.key === 'extra' ? (
                  <span
                    className={`student-portal-nav-caret student-portal-nav-caret--triangle ${openMenuKey === item.key ? 'open' : ''}`}
                    aria-hidden="true"
                  />
                ) : (
                  <ArrowDownOutlined className={`student-portal-nav-caret ${openMenuKey === item.key ? 'open' : ''}`} />
                )
              ) : null}
            </button>

            {item.children?.length && openMenuKey === item.key ? (
              <div className="student-portal-submenu">
                {item.children.map((child) => (
                  <button
                    key={child.key}
                    type="button"
                    className={`student-portal-submenu-item ${activePath === child.path ? 'active' : ''}`}
                    onClick={() => closeMenuAndNavigate(child.path)}
                  >
                    {child.label}
                  </button>
                ))}
              </div>
            ) : null}
          </div>
        ))}
      </nav>

      <div className="student-portal-actions">
        {needsEmailBinding ? (
          <button
            type="button"
            className="student-portal-email-warning"
            onClick={() => closeMenuAndNavigate('/student/profile')}
          >
            绑定邮箱
          </button>
        ) : null}

        <button
          type="button"
          className="student-portal-credits"
          onClick={() => closeMenuAndNavigate('/student/developer-credits')}
        >
          <HeartOutlined style={{ fontSize: 15 }} />
          <span>开发者致谢</span>
        </button>

        <button
          type="button"
          className="student-portal-user"
          onClick={() => closeMenuAndNavigate('/student/profile')}
        >
          <span className="student-portal-avatar">
            {user?.avatarUrl ? <img src={withAuthToken(user.avatarUrl)} alt="" /> : avatarText}
          </span>
          <span className="student-portal-user-meta">
            <strong>{displayName}</strong>
            <span>{user?.studentNo || '未绑定学号'}</span>
          </span>
        </button>

        <button
          type="button"
          className="student-portal-logout"
          onClick={() => {
            setOpenMenuKey('');
            onLogout?.();
          }}
        >
          <LogoutOutlined style={{ fontSize: 15 }} />
          <span>退出</span>
        </button>
      </div>
    </header>
  );
}

export default StudentPortalHeader;
