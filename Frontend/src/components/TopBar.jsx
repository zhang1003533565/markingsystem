import { useNavigate } from 'react-router-dom';
import NewMessageNotification from './NewMessageNotification';
import './TopBar.css';

function TopBar({ breadcrumb, user, onLogout }) {
  const navigate = useNavigate();

  const handleAvatarClick = () => {
    // 跳转到个人信息页面，这里假设通过 Dashboard 的菜单索引 2 来激活
    // 实际项目中可能需要更复杂的路由逻辑
    navigate('/dashboard?tab=profile');
  };

  const displayName = user?.nickname || user?.username || '用户';
  const avatarText = displayName.trim().charAt(0).toUpperCase() || 'U';

  return (
    <header className="top-bar">
      <div className="breadcrumb">
        <span>{breadcrumb || '首页'}</span>
      </div>
      <div className="user-info">
        <div className="user-avatar-small" onClick={handleAvatarClick} title="查看个人信息">
          {avatarText}
        </div>
        <span className="welcome">欢迎，{user?.username || '用户'}</span>
        <div className="notification-area">
          <NewMessageNotification />
        </div>
        <button className="logout-btn" onClick={onLogout}>退出登录</button>
      </div>
    </header>
  );
}

export default TopBar;
