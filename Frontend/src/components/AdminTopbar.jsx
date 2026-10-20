import { useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { getAdminBreadcrumbs } from './adminNavigation.jsx';
import { withAuthToken } from '../utils/authStorage';
import MessageCenter from './MessageCenter';
import topbarBackground from '../assets/index/topback.png';
import './AdminTopbar.css';

function AdminTopbar({ pathname, user = {}, onLogout }) {
  const navigate = useNavigate();
  const breadcrumbs = useMemo(() => getAdminBreadcrumbs(pathname, user.role), [pathname, user.role]);
  const displayName = user.nickname || user.username || '用户';
  const roleLabel = user.role === 'ADMIN' ? '管理员' : '教师';
  const avatarText = displayName.trim().charAt(0).toUpperCase() || 'U';

  return (
    <header
      className="admin-topbar"
      style={{ '--admin-topbar-bg': `url(${topbarBackground})` }}
    >
      <div className="admin-topbar-breadcrumbs">
        {breadcrumbs.map((item, index) => (
          <span key={`${item.label}-${index}`} className="admin-topbar-crumb">
            {index > 0 && <span className="admin-topbar-sep">/</span>}
            {item.path ? (
              <button type="button" onClick={() => navigate(item.path)}>
                {item.label}
              </button>
            ) : (
              <strong>{item.label}</strong>
            )}
          </span>
        ))}
      </div>

      <div className="admin-topbar-actions">
        <MessageCenter />
        <button
          type="button"
          className="admin-topbar-user"
          onClick={() => navigate('/admin/profile')}
          title="查看个人信息"
        >
          <span className="admin-topbar-avatar">
            {user.avatarUrl ? <img src={withAuthToken(user.avatarUrl)} alt="" /> : avatarText}
          </span>
          <span className="admin-topbar-user-meta">
            <strong>{displayName}</strong>
            <span>{roleLabel}{user.username ? ` · ${user.username}` : ''}</span>
          </span>
        </button>
        <button type="button" className="admin-topbar-logout" onClick={onLogout} title="退出登录">
          <span>退出</span>
        </button>
      </div>
    </header>
  );
}

export default AdminTopbar;
