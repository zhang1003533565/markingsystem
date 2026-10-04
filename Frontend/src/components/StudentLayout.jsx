import { useNavigate } from 'react-router-dom';
import StudentPortalHeader from './StudentPortalHeader';
import AIFloatingChat from './AIFloatingChat';
import { clearStoredUser, getStoredUser } from '../utils/authStorage';
import '../styles/flat-tokens.css';
import '../pages/Student/Dashboard.css';

function StudentLayout({ activePath, children, userRole, notificationCount = 0 }) {
  const navigate = useNavigate();
  const user = getStoredUser();

  const handleLogout = () => {
    clearStoredUser();
    navigate('/login', { replace: true });
  };

  return (
    <div className="student-home-page">
      <StudentPortalHeader
        activePath={activePath}
        user={user}
        userRole={userRole || user.role}
        notificationCount={notificationCount}
        onLogout={handleLogout}
      />
      <main className="student-home-main">{children}</main>
      <AIFloatingChat role={userRole || user?.role || 'STUDENT'} />
    </div>
  );
}

export default StudentLayout;
