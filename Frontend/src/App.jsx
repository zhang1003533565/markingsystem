import { Suspense } from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate, Outlet } from 'react-router-dom';
import { routes } from './router';
import Login from './pages/Login';
import ForceChangePassword from './pages/ForceChangePassword';
import ForceBindEmail from './pages/ForceBindEmail';
import AcademicYearGuard from './components/AcademicYearGuard';
import ConfirmDialogHost from './components/ConfirmDialog';
import { getStoredUser, hasStoredUser, normalizeStoredRole } from './utils/authStorage';
import { isEmailBindingRequiredForUser } from './utils/emailBindingPolicy';

// 检查用户是否已登录
const isAuthenticated = () => {
  return hasStoredUser();
};

// 获取用户角色（用于初始化）
const getUserRole = () => {
  const user = getStoredUser();
  return normalizeStoredRole(user.role);
};

const isPasswordResetRequired = () => {
  const user = getStoredUser();
  return Boolean(user.passwordResetRequired);
};

const isEmailBindingRequired = () => {
  return isEmailBindingRequiredForUser(getStoredUser());
};

// 受保护的路由组件
const ProtectedRoute = ({ allowedRoles }) => {
  if (!isAuthenticated()) {
    return <Navigate to="/login" replace />;
  }

  if (isPasswordResetRequired()) {
    return <Navigate to="/force-change-password" replace />;
  }

  if (isEmailBindingRequired()) {
    return <Navigate to="/force-bind-email" replace />;
  }

  // 每次渲染时重新获取角色，确保是最新的
  const currentRole = getUserRole();

  if (allowedRoles && !allowedRoles.includes(currentRole)) {
    // 无权限，重定向到对应的首页
    if (currentRole === 'ADMIN' || currentRole === 'TEACHER') {
      return <Navigate to="/admin" replace />;
    }
    return <Navigate to="/student" replace />;
  }

  return <Outlet />;
};

const ProtectedPage = ({ allowedRoles, children }) => {
  if (!isAuthenticated()) {
    return <Navigate to="/login" replace />;
  }

  if (isPasswordResetRequired()) {
    return <Navigate to="/force-change-password" replace />;
  }

  if (isEmailBindingRequired()) {
    return <Navigate to="/force-bind-email" replace />;
  }

  const currentRole = getUserRole();
  if (allowedRoles && !allowedRoles.includes(currentRole)) {
    if (currentRole === 'ADMIN' || currentRole === 'TEACHER') {
      return <Navigate to="/admin" replace />;
    }
    return <Navigate to="/student" replace />;
  }

  return children;
};

const getPostLoginPath = (user = getStoredUser()) => {
  if (user.passwordResetRequired) {
    return '/force-change-password';
  }

  const role = normalizeStoredRole(user.role);
  if (isEmailBindingRequiredForUser(user)) {
    return '/force-bind-email';
  }

  if (role === 'ADMIN' || role === 'TEACHER') {
    return '/admin/home';
  }

  return '/student/home';
};

const RootRedirect = () => <Navigate to="/login" replace />;

// 登录页面包装组件
const LoginWrapper = () => {
  const handleLoginSuccess = (user) => {
    const path = getPostLoginPath(user);
    window.location.href = path;
  };

  return <Login onLoginSuccess={handleLoginSuccess} />;
};

const adminRoutesWithoutAcademicYearGuard = new Set(['academic-years', 'developer-credits']);

function App() {
  const getAdminChildRoute = (path) => routes.find(r => r.path === '/admin')?.children?.find(c => c.path === path);
  const renderAdminChild = (path) => {
    const child = getAdminChildRoute(path);
    if (!child?.element) {
      return null;
    }
    const page = adminRoutesWithoutAcademicYearGuard.has(path)
      ? child.element
      : <AcademicYearGuard>{child.element}</AcademicYearGuard>;

    return (
      <ProtectedPage allowedRoles={child.allowedRoles || ['ADMIN', 'TEACHER']}>
        {page}
      </ProtectedPage>
    );
  };

  const renderStudentRoute = (path) => {
    const element = routes.find(r => r.path === path)?.element;
    if (
      path.startsWith('/student/extra') ||
      path === '/student/announcements' ||
      path === '/student/profile' ||
      path === '/student/developer-credits' ||
      path === '/student/assistant' ||
      path === '/student/ai-chat'
    ) {
      return element;
    }
    return (
      <AcademicYearGuard>
        {element}
      </AcademicYearGuard>
    );
  };

  // 404页面组件
  const NotFound = () => {
    return (
      <div style={{ textAlign: 'center', padding: '50px' }}>
        <h1>404 - 页面不存在</h1>
        <p>您访问的页面不存在，请检查URL是否正确。</p>
        <button
          onClick={() => window.location.href = '/login'}
          style={{
            marginTop: '20px',
            padding: '10px 20px',
            backgroundColor: '#1890ff',
            color: 'white',
            border: 'none',
            borderRadius: '4px',
            cursor: 'pointer'
          }}
        >
          返回首页
        </button>
      </div>
    );
  };

  return (
    <Router>
      <ConfirmDialogHost />
      <Suspense
        fallback={
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', minHeight: '100vh', background: '#fff' }}>
            <span style={{ color: '#1677ff', fontSize: 14 }}>页面加载中…</span>
          </div>
        }
      >
        <Routes>
        {/* 公开路由 */}
        <Route path="/login" element={<LoginWrapper />} />
        <Route path="/force-change-password" element={
          isAuthenticated() ? <ForceChangePassword /> : <Navigate to="/login" replace />
        } />
        <Route path="/force-bind-email" element={
          isAuthenticated()
            ? (isPasswordResetRequired() ? <Navigate to="/force-change-password" replace /> : <ForceBindEmail />)
            : <Navigate to="/login" replace />
        } />

        {/* 受保护的路由组 */}
        <Route element={<ProtectedRoute />}>
          <Route path="/home" element={routes.find(r => r.path === '/home')?.element} />
        </Route>

        <Route element={<ProtectedRoute allowedRoles={['ADMIN', 'TEACHER']} />}>
          <Route path="/admin" element={routes.find(r => r.path === '/admin')?.element}>
            <Route index element={<Navigate to="/admin/home" replace />} />
            <Route path="home" element={renderAdminChild('home')} />
            <Route path="students" element={renderAdminChild('students')} />
            <Route path="colleges" element={renderAdminChild('colleges')} />
            <Route path="majors" element={renderAdminChild('majors')} />
            <Route path="classes" element={renderAdminChild('classes')} />
            <Route path="grades" element={renderAdminChild('grades')} />
            <Route path="extra-score-templates/moral" element={renderAdminChild('extra-score-templates/moral')} />
            <Route path="extra-score-templates/moral/new" element={renderAdminChild('extra-score-templates/moral/new')} />
            <Route path="extra-score-templates/moral/:templateId" element={renderAdminChild('extra-score-templates/moral/:templateId')} />
            <Route path="extra-score-templates/moral/:templateId/groups/:groupIndex" element={renderAdminChild('extra-score-templates/moral/:templateId/groups/:groupIndex')} />
            <Route path="extra-score-templates/moral/:templateId/group/:groupIndex" element={renderAdminChild('extra-score-templates/moral/:templateId/group/:groupIndex')} />
            <Route path="extra-score-templates/moral/:templateId/groups/:groupIndex/items/:itemIndex" element={renderAdminChild('extra-score-templates/moral/:templateId/groups/:groupIndex/items/:itemIndex')} />
            <Route path="extra-score-templates/moral/:templateId/group/:groupIndex/item/:itemIndex" element={renderAdminChild('extra-score-templates/moral/:templateId/group/:groupIndex/item/:itemIndex')} />
            <Route path="extra-score-templates/intellectual" element={renderAdminChild('extra-score-templates/intellectual')} />
            <Route path="extra-score-templates/intellectual/new" element={renderAdminChild('extra-score-templates/intellectual/new')} />
            <Route path="extra-score-templates/intellectual/:templateId" element={renderAdminChild('extra-score-templates/intellectual/:templateId')} />
            <Route path="extra-score-templates/intellectual/:templateId/groups/:groupIndex" element={renderAdminChild('extra-score-templates/intellectual/:templateId/groups/:groupIndex')} />
            <Route path="extra-score-templates/intellectual/:templateId/group/:groupIndex" element={renderAdminChild('extra-score-templates/intellectual/:templateId/group/:groupIndex')} />
            <Route path="extra-score-templates/intellectual/:templateId/groups/:groupIndex/items/:itemIndex" element={renderAdminChild('extra-score-templates/intellectual/:templateId/groups/:groupIndex/items/:itemIndex')} />
            <Route path="extra-score-templates/intellectual/:templateId/group/:groupIndex/item/:itemIndex" element={renderAdminChild('extra-score-templates/intellectual/:templateId/group/:groupIndex/item/:itemIndex')} />
            <Route path="extra-score-templates/physical" element={renderAdminChild('extra-score-templates/physical')} />
            <Route path="extra-score-templates/physical/new" element={renderAdminChild('extra-score-templates/physical/new')} />
            <Route path="extra-score-templates/physical/:templateId" element={renderAdminChild('extra-score-templates/physical/:templateId')} />
            <Route path="extra-score-templates/physical/:templateId/groups/:groupIndex" element={renderAdminChild('extra-score-templates/physical/:templateId/groups/:groupIndex')} />
            <Route path="extra-score-templates/physical/:templateId/group/:groupIndex" element={renderAdminChild('extra-score-templates/physical/:templateId/group/:groupIndex')} />
            <Route path="extra-score-templates/physical/:templateId/groups/:groupIndex/items/:itemIndex" element={renderAdminChild('extra-score-templates/physical/:templateId/groups/:groupIndex/items/:itemIndex')} />
            <Route path="extra-score-templates/physical/:templateId/group/:groupIndex/item/:itemIndex" element={renderAdminChild('extra-score-templates/physical/:templateId/group/:groupIndex/item/:itemIndex')} />
            <Route path="academic-years" element={renderAdminChild('academic-years')} />
            <Route path="scores" element={renderAdminChild('scores')} />
            <Route path="scores/history" element={renderAdminChild('scores/history')} />
            <Route path="second-class-scores" element={renderAdminChild('second-class-scores')} />
            <Route path="scores/query" element={renderAdminChild('scores/query')} />
            <Route path="composite-score-config" element={renderAdminChild('composite-score-config')} />
            <Route path="composite-scores" element={renderAdminChild('composite-scores')} />
            <Route path="analysis" element={renderAdminChild('analysis')} />
            <Route path="extra-score-review" element={renderAdminChild('extra-score-review')} />
            <Route path="extra-score-submission-windows" element={renderAdminChild('extra-score-submission-windows')} />
            <Route path="users" element={renderAdminChild('users')} />
            <Route path="announcements" element={renderAdminChild('announcements')} />
            <Route path="email-config" element={renderAdminChild('email-config')} />
            <Route path="profile" element={renderAdminChild('profile')} />
            <Route path="knowledge" element={renderAdminChild('knowledge')} />
            <Route path="knowledge-logs" element={renderAdminChild('knowledge-logs')} />
            <Route path="class-answer-summary" element={renderAdminChild('class-answer-summary')} />
            <Route path="developer-credits" element={renderAdminChild('developer-credits')} />
          </Route>
        </Route>

        <Route element={<ProtectedRoute allowedRoles={['STUDENT', 'CLASS_COMMITTEE']} />}>
          <Route path="/student" element={routes.find(r => r.path === '/student')?.element} />
          <Route path="/student/home" element={renderStudentRoute('/student/home')} />
          <Route path="/student/scores" element={renderStudentRoute('/student/scores')} />
          <Route path="/student/profile" element={renderStudentRoute('/student/profile')} />
          <Route path="/student/developer-credits" element={renderStudentRoute('/student/developer-credits')} />
          <Route path="/student/assistant" element={renderStudentRoute('/student/assistant')} />
          <Route path="/student/extra" element={renderStudentRoute('/student/extra')} />
          <Route path="/student/extra/apply" element={renderStudentRoute('/student/extra/apply')} />
          <Route path="/student/extra/edit/:applicationId" element={renderStudentRoute('/student/extra/edit/:applicationId')} />
          <Route path="/student/extra/moral" element={renderStudentRoute('/student/extra/moral')} />
          <Route path="/student/extra/moral/apply" element={renderStudentRoute('/student/extra/moral/apply')} />
          <Route path="/student/extra/moral/edit/:applicationId" element={renderStudentRoute('/student/extra/moral/edit/:applicationId')} />
          <Route path="/student/extra/intellectual" element={renderStudentRoute('/student/extra/intellectual')} />
          <Route path="/student/extra/intellectual/apply" element={renderStudentRoute('/student/extra/intellectual/apply')} />
          <Route path="/student/extra/intellectual/edit/:applicationId" element={renderStudentRoute('/student/extra/intellectual/edit/:applicationId')} />
          <Route path="/student/extra/physical" element={renderStudentRoute('/student/extra/physical')} />
          <Route path="/student/extra/physical/apply" element={renderStudentRoute('/student/extra/physical/apply')} />
          <Route path="/student/extra/physical/edit/:applicationId" element={renderStudentRoute('/student/extra/physical/edit/:applicationId')} />
          <Route path="/student/extra/process" element={renderStudentRoute('/student/extra/process')} />
          <Route path="/student/extra/result" element={renderStudentRoute('/student/extra/result')} />
          <Route path="/student/extra/history" element={renderStudentRoute('/student/extra/history')} />
          <Route path="/student/class/stats" element={renderStudentRoute('/student/class/stats')} />
          <Route path="/student/class/check" element={renderStudentRoute('/student/class/check')} />
          <Route path="/student/announcements" element={renderStudentRoute('/student/announcements')} />
          <Route path="/student/ai-chat" element={renderStudentRoute('/student/ai-chat')} />
        </Route>

        {/* 根路径固定进入登录页，避免落到旧首页或随机角色页 */}
        <Route path="/" element={<RootRedirect />} />

        {/* 404 */}
        <Route path="*" element={routes.find(r => r.path === '*')?.element} />
        </Routes>
      </Suspense>
    </Router>
  );
}

export default App;
