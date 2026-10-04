import { useState } from 'react';
import { useNavigate, useLocation, Outlet } from 'react-router-dom';
import AdminSidebar from '../../components/AdminSidebar';
import AdminTopbar from '../../components/AdminTopbar';
import AIFloatingChat from '../../components/AIFloatingChat';
import { clearStoredUser, getStoredUser } from '../../utils/authStorage';
import dashboardBackground from '../../assets/index/pages-back2.png';
import './Dashboard.css';

const Dashboard = ({ userRole }) => {
  const navigate = useNavigate();
  const location = useLocation();
  const user = getStoredUser();
  const currentRole = userRole || user.role || '';
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);

  const handleLogout = () => {
    clearStoredUser();
    navigate('/login', { replace: true });
  };

  return (
    <div className={`admin-dashboard ${sidebarCollapsed ? 'sidebar-collapsed' : ''}`}>
      <AdminSidebar
        role={currentRole}
        pathname={location.pathname}
        collapsed={sidebarCollapsed}
        onToggleCollapse={() => setSidebarCollapsed((prev) => !prev)}
      />
      <div className="admin-dashboard-main">
        <AdminTopbar pathname={location.pathname} user={user} onLogout={handleLogout} />
        <main
          className="main-content"
          style={{ '--admin-main-bg': `url(${dashboardBackground})` }}
        >
          <Outlet />
        </main>
      </div>
      <AIFloatingChat role={currentRole} />
    </div>
  );
};

export default Dashboard;
