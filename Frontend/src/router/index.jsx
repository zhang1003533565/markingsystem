import { lazy } from 'react'
import { Navigate } from 'react-router-dom'
const AdminDashboard = lazy(() => import('../pages/Admin/Dashboard'))
const StudentManagement = lazy(() => import('../pages/Admin/StudentManagement'))
const CollegeManagement = lazy(() => import('../pages/Admin/CollegeManagement'))
const MajorManagement = lazy(() => import('../pages/Admin/MajorManagement'))
const ClassManagement = lazy(() => import('../pages/Admin/ClassManagement'))
const GradeManagement = lazy(() => import('../pages/Admin/GradeManagement'))
const AcademicYearManagement = lazy(() => import('../pages/Admin/AcademicYearManagement'))
const ScoreManagement = lazy(() => import('../pages/Admin/ScoreManagement'))
const ScoreImportHistory = lazy(() => import('../pages/Admin/ScoreImportHistory'))
const SecondClassScoreImport = lazy(() => import('../pages/Admin/SecondClassScoreImport'))
const ScoreQuery = lazy(() => import('../pages/Admin/ScoreQuery'))
const CompositeScoreManagement = lazy(() => import('../pages/Admin/CompositeScoreManagement'))
const CompositeScoreConfig = lazy(() => import('../pages/Admin/CompositeScoreConfig'))
const Analysis = lazy(() => import('../pages/Admin/Analysis'))
const UserManagement = lazy(() => import('../pages/Admin/UserManagement'))
const MoralExtraScoreTemplateManagement = lazy(
  () => import('../pages/Admin/MoralExtraScoreTemplateManagement'),
)
const IntellectualExtraScoreTemplateManagement = lazy(
  () => import('../pages/Admin/IntellectualExtraScoreTemplateManagement'),
)
const PhysicalExtraScoreTemplateManagement = lazy(
  () => import('../pages/Admin/PhysicalExtraScoreTemplateManagement'),
)
const MoralExtraScoreTemplateEditor = lazy(
  () => import('../pages/Admin/MoralExtraScoreTemplateEditor'),
)
const IntellectualExtraScoreTemplateEditor = lazy(
  () => import('../pages/Admin/IntellectualExtraScoreTemplateEditor'),
)
const PhysicalExtraScoreTemplateEditor = lazy(
  () => import('../pages/Admin/PhysicalExtraScoreTemplateEditor'),
)
const StudentDashboard = lazy(() => import('../pages/Student/Dashboard'))
const StudentScores = lazy(() => import('../pages/Student/Scores'))
const StudentProfile = lazy(() => import('../pages/Student/Profile'))
const ExtraScore = lazy(() => import('../pages/Student/ExtraScore'))
const ScoreCheck = lazy(() => import('../pages/Student/ScoreCheck'))
const ExtraScoreProcess = lazy(() => import('../pages/Student/ExtraScoreProcess'))
const ExtraScoreResult = lazy(() => import('../pages/Student/ExtraScoreResult'))
const ExtraScoreHistory = lazy(() => import('../pages/Student/ExtraScoreHistory'))
const AIChat = lazy(() => import('../pages/Student/AIChat'))
const AssistantHome = lazy(() => import('../pages/Student/AssistantHome'))
const AdminAnnouncementCenter = lazy(() => import('../pages/Admin/AnnouncementCenter'))
const StudentAnnouncementCenter = lazy(() => import('../pages/Student/AnnouncementCenter'))
const ExtraScoreReview = lazy(() => import('../pages/Admin/ExtraScoreReview'))
const ExtraScoreSubmissionWindowManagement = lazy(
  () => import('../pages/Admin/ExtraScoreSubmissionWindowManagement'),
)
const EmailConfig = lazy(() => import('../pages/Admin/EmailConfig'))
const TeacherProfile = lazy(() => import('../pages/Admin/TeacherProfile'))
const DeveloperCredits = lazy(() => import('../pages/Admin/DeveloperCredits'))
const KnowledgeBase = lazy(() => import('../pages/Admin/KnowledgeBase'))
const KnowledgeLogs = lazy(() => import('../pages/Admin/KnowledgeLogs'))
const ClassAnswerSummary = lazy(() => import('../pages/Admin/ClassAnswerSummary'))
const AdminHome = lazy(() => import('../pages/Admin/Home'))
const Home = lazy(() => import('../pages/Home'))
import Login from '../pages/Login'
import MessageBanner from '../components/MessageBanner'
import StudentLayout from '../components/StudentLayout'
import ClassStatistics from '../pages/Student/ClassStatistics'
import LegacyExtraScoreRedirect from '../components/LegacyExtraScoreRedirect'
const MaterialUpload = lazy(() => import('../pages/Student/MaterialUpload'))
const MaterialTrace = lazy(() => import('../pages/Student/MaterialTrace'))

// 路由配置
export const routes = [
  {
    path: '/login',
    element: <Login />,
    public: true
  },
  {
    path: '/home',
    element: <Home />,
    protected: true
  },
  {
    path: '/admin',
    element: <AdminDashboard />,
    protected: true,
    allowedRoles: ['ADMIN', 'TEACHER'],
    children: [
      {
        index: true,
        element: <Navigate to="/admin/home" replace />
      },
      {
        path: 'home',
        element: <AdminHome />
      },
      {
        path: 'students',
        element: <StudentManagement />
      },
      {
        path: 'colleges',
        element: <CollegeManagement />,
        allowedRoles: ['ADMIN']
      },
      {
        path: 'majors',
        element: <MajorManagement />,
        allowedRoles: ['ADMIN']
      },
      {
        path: 'classes',
        element: <ClassManagement />
      },
      {
        path: 'grades',
        element: <GradeManagement />,
        allowedRoles: ['ADMIN']
      },
      {
        path: 'extra-score-templates/moral',
        element: <MoralExtraScoreTemplateManagement />
      },
      {
        path: 'extra-score-templates/moral/new',
        element: <MoralExtraScoreTemplateEditor />
      },
      {
        path: 'extra-score-templates/moral/:templateId',
        element: <MoralExtraScoreTemplateEditor />
      },
      {
        path: 'extra-score-templates/moral/:templateId/groups/:groupIndex',
        element: <MoralExtraScoreTemplateEditor />
      },
      {
        path: 'extra-score-templates/moral/:templateId/group/:groupIndex',
        element: <MoralExtraScoreTemplateEditor />
      },
      {
        path: 'extra-score-templates/moral/:templateId/groups/:groupIndex/items/:itemIndex',
        element: <MoralExtraScoreTemplateEditor />
      },
      {
        path: 'extra-score-templates/moral/:templateId/group/:groupIndex/item/:itemIndex',
        element: <MoralExtraScoreTemplateEditor />
      },
      {
        path: 'extra-score-templates/intellectual',
        element: <IntellectualExtraScoreTemplateManagement />
      },
      {
        path: 'extra-score-templates/intellectual/new',
        element: <IntellectualExtraScoreTemplateEditor />
      },
      {
        path: 'extra-score-templates/intellectual/:templateId',
        element: <IntellectualExtraScoreTemplateEditor />
      },
      {
        path: 'extra-score-templates/intellectual/:templateId/groups/:groupIndex',
        element: <IntellectualExtraScoreTemplateEditor />
      },
      {
        path: 'extra-score-templates/intellectual/:templateId/group/:groupIndex',
        element: <IntellectualExtraScoreTemplateEditor />
      },
      {
        path: 'extra-score-templates/intellectual/:templateId/groups/:groupIndex/items/:itemIndex',
        element: <IntellectualExtraScoreTemplateEditor />
      },
      {
        path: 'extra-score-templates/intellectual/:templateId/group/:groupIndex/item/:itemIndex',
        element: <IntellectualExtraScoreTemplateEditor />
      },
      {
        path: 'extra-score-templates/physical',
        element: <PhysicalExtraScoreTemplateManagement />
      },
      {
        path: 'extra-score-templates/physical/new',
        element: <PhysicalExtraScoreTemplateEditor />
      },
      {
        path: 'extra-score-templates/physical/:templateId',
        element: <PhysicalExtraScoreTemplateEditor />
      },
      {
        path: 'extra-score-templates/physical/:templateId/groups/:groupIndex',
        element: <PhysicalExtraScoreTemplateEditor />
      },
      {
        path: 'extra-score-templates/physical/:templateId/group/:groupIndex',
        element: <PhysicalExtraScoreTemplateEditor />
      },
      {
        path: 'extra-score-templates/physical/:templateId/groups/:groupIndex/items/:itemIndex',
        element: <PhysicalExtraScoreTemplateEditor />
      },
      {
        path: 'extra-score-templates/physical/:templateId/group/:groupIndex/item/:itemIndex',
        element: <PhysicalExtraScoreTemplateEditor />
      },
      {
        path: 'academic-years',
        element: <AcademicYearManagement />,
        allowedRoles: ['ADMIN']
      },
      {
        path: 'scores',
        element: <ScoreManagement />
      },
      {
        path: 'scores/history',
        element: <ScoreImportHistory />
      },
      {
        path: 'second-class-scores',
        element: <SecondClassScoreImport />
      },
      {
        path: 'scores/query',
        element: <ScoreQuery />
      },
      {
        path: 'composite-score-config',
        element: <CompositeScoreConfig />
      },
      {
        path: 'composite-scores',
        element: <CompositeScoreManagement />
      },
      {
        path: 'analysis',
        element: <Analysis />
      },
      {
        path: 'users',
        element: <UserManagement />,
        allowedRoles: ['ADMIN']
      },
      {
        path: 'announcements',
        element: <AdminAnnouncementCenter />
      },
      {
        path: 'extra-score-review',
        element: <ExtraScoreReview />
      },
      {
        path: 'extra-score-submission-windows',
        element: <ExtraScoreSubmissionWindowManagement />
      },
      {
        path: 'email-config',
        element: <EmailConfig />
      },
      {
        path: 'profile',
        element: <TeacherProfile />,
        allowedRoles: ['ADMIN', 'TEACHER']
      },
      {
        path: 'knowledge',
        element: <KnowledgeBase />,
        allowedRoles: ['ADMIN']
      },
      {
        path: 'knowledge-logs',
        element: <KnowledgeLogs />,
        allowedRoles: ['ADMIN', 'TEACHER']
      },
      {
        path: 'class-answer-summary',
        element: <ClassAnswerSummary />,
        allowedRoles: ['TEACHER', 'ADMIN']
      },
      {
        path: 'developer-credits',
        element: <DeveloperCredits />
      },
      {
        path: 'material-trace/:chainCode',
        element: <MaterialTrace />
      }
    ]
  },
  {
    path: '/student',
    element: <Navigate to="/student/home" replace />,
    protected: true,
    allowedRoles: ['STUDENT', 'CLASS_COMMITTEE']
  },
  {
    path: '/student/home',
    element: <StudentDashboard />,
    protected: true,
    allowedRoles: ['STUDENT', 'CLASS_COMMITTEE']
  },
  {
    path: '/student/scores',
    element: <StudentScores />,
    protected: true,
    allowedRoles: ['STUDENT', 'CLASS_COMMITTEE']
  },
  {
    path: '/student/profile',
    element: <StudentProfile />,
    protected: true,
    allowedRoles: ['STUDENT', 'CLASS_COMMITTEE']
  },
  {
    path: '/student/developer-credits',
    element: (
      <StudentLayout activePath="/student/developer-credits">
        <DeveloperCredits />
      </StudentLayout>
    ),
    protected: true,
    allowedRoles: ['STUDENT', 'CLASS_COMMITTEE']
  },
  {
    path: '/student/extra',
    element: <ExtraScore />,
    protected: true,
    allowedRoles: ['STUDENT', 'CLASS_COMMITTEE']
  },
  {
    path: '/student/extra/apply',
    element: <ExtraScore />,
    protected: true,
    allowedRoles: ['STUDENT', 'CLASS_COMMITTEE']
  },
  {
    path: '/student/extra/edit/:applicationId',
    element: <ExtraScore />,
    protected: true,
    allowedRoles: ['STUDENT', 'CLASS_COMMITTEE']
  },
  {
    path: '/student/extra/moral',
    element: <LegacyExtraScoreRedirect type="MORAL" />,
    protected: true,
    allowedRoles: ['STUDENT', 'CLASS_COMMITTEE']
  },
  {
    path: '/student/extra/moral/apply',
    element: <LegacyExtraScoreRedirect type="MORAL" mode="apply" />,
    protected: true,
    allowedRoles: ['STUDENT', 'CLASS_COMMITTEE']
  },
  {
    path: '/student/extra/moral/edit/:applicationId',
    element: <LegacyExtraScoreRedirect type="MORAL" mode="edit" />,
    protected: true,
    allowedRoles: ['STUDENT', 'CLASS_COMMITTEE']
  },
  {
    path: '/student/extra/intellectual',
    element: <LegacyExtraScoreRedirect type="INTELLECTUAL" />,
    protected: true,
    allowedRoles: ['STUDENT', 'CLASS_COMMITTEE']
  },
  {
    path: '/student/extra/intellectual/apply',
    element: <LegacyExtraScoreRedirect type="INTELLECTUAL" mode="apply" />,
    protected: true,
    allowedRoles: ['STUDENT', 'CLASS_COMMITTEE']
  },
  {
    path: '/student/extra/intellectual/edit/:applicationId',
    element: <LegacyExtraScoreRedirect type="INTELLECTUAL" mode="edit" />,
    protected: true,
    allowedRoles: ['STUDENT', 'CLASS_COMMITTEE']
  },
  {
    path: '/student/extra/physical',
    element: <LegacyExtraScoreRedirect type="PHYSICAL" />,
    protected: true,
    allowedRoles: ['STUDENT', 'CLASS_COMMITTEE']
  },
  {
    path: '/student/extra/physical/apply',
    element: <LegacyExtraScoreRedirect type="PHYSICAL" mode="apply" />,
    protected: true,
    allowedRoles: ['STUDENT', 'CLASS_COMMITTEE']
  },
  {
    path: '/student/extra/physical/edit/:applicationId',
    element: <LegacyExtraScoreRedirect type="PHYSICAL" mode="edit" />,
    protected: true,
    allowedRoles: ['STUDENT', 'CLASS_COMMITTEE']
  },
  {
    path: '/student/class/stats',
    element: <ClassStatistics />,
    protected: true,
    allowedRoles: ['CLASS_COMMITTEE']
  },
  {
    path: '/student/class/check',
    element: <ScoreCheck />,
    protected: true,
    allowedRoles: ['CLASS_COMMITTEE']
  },
  {
    path: '/student/material-upload',
    element: <MaterialUpload />,
    protected: true,
    allowedRoles: ['STUDENT', 'CLASS_COMMITTEE']
  },
  {
    path: '/student/material-trace/:chainCode',
    element: <MaterialTrace />,
    protected: true,
    allowedRoles: ['STUDENT', 'CLASS_COMMITTEE']
  },
  {
    path: '/student/extra/process',
    element: <ExtraScoreProcess />,
    protected: true,
    allowedRoles: ['STUDENT', 'CLASS_COMMITTEE']
  },
  {
    path: '/student/extra/result',
    element: <ExtraScoreResult />,
    protected: true,
    allowedRoles: ['STUDENT', 'CLASS_COMMITTEE']
  },
  {
    path: '/student/extra/history',
    element: <ExtraScoreHistory />,
    protected: true,
    allowedRoles: ['STUDENT', 'CLASS_COMMITTEE']
  },
  {
    path: '/student/announcements',
    element: <StudentAnnouncementCenter />,
    protected: true,
    allowedRoles: ['STUDENT', 'CLASS_COMMITTEE']
  },
  {
    path: '/student/assistant',
    element: <AssistantHome />,
    protected: true,
    allowedRoles: ['STUDENT', 'CLASS_COMMITTEE']
  },
  {
    path: '/student/ai-chat',
    element: <AIChat />,
    protected: true,
    allowedRoles: ['STUDENT', 'CLASS_COMMITTEE']
  },
  {
    path: '/',
    element: <Navigate to="/login" replace />
  },
  {
    path: '*',
    element: (
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
    )
  }
];

// 菜单路由映射（用于 Dashboard 组件）
export const menuRouteMap = {
  1: '/admin/home',
  21: '/admin/students',
  22: '/admin/colleges',
  23: '/admin/majors',
  24: '/admin/classes',
  33: '/admin/grades',
  29: '/admin/extra-score-templates/moral',
  30: '/admin/extra-score-templates/intellectual',
  31: '/admin/extra-score-templates/physical',
  32: '/admin/extra-score-review',
  37: '/admin/extra-score-submission-windows',
  25: '/admin/academic-years',
  26: '/admin/scores',
  38: '/admin/scores/history',
  36: '/admin/second-class-scores',
  27: '/admin/scores/query',
  35: '/admin/composite-score-config',
  34: '/admin/composite-scores',
  4: '/admin/analysis',
  28: '/admin/announcements',
  53: '/admin/profile',
  54: '/admin/knowledge',
  56: '/admin/knowledge-logs',
  58: '/admin/class-answer-summary',
  900: '/admin/developer-credits',
  51: '/admin/users',
  52: '/admin/email-config',
};

// 路径到菜单ID映射（用于 Dashboard 组件）
export const pathToMenuId = {
  '/admin/home': 1,
  '/admin/students': 21,
  '/admin/colleges': 22,
  '/admin/majors': 23,
  '/admin/classes': 24,
  '/admin/grades': 33,
  '/admin/extra-score-templates/moral': 29,
  '/admin/extra-score-templates/intellectual': 30,
  '/admin/extra-score-templates/physical': 31,
  '/admin/extra-score-review': 32,
  '/admin/extra-score-submission-windows': 37,
  '/admin/academic-years': 25,
  '/admin/scores': 26,
  '/admin/scores/history': 38,
  '/admin/second-class-scores': 36,
  '/admin/scores/query': 27,
  '/admin/composite-score-config': 35,
  '/admin/composite-scores': 34,
  '/admin/analysis': 4,
  '/admin/announcements': 28,
  '/admin/profile': 53,
  '/admin/knowledge': 54,
  '/admin/knowledge-logs': 56,
  '/admin/class-answer-summary': 58,
  '/admin/developer-credits': 900,
  '/admin/users': 51,
  '/admin/email-config': 52,
};

export const resolveMenuIdFromPath = (path) => {
  if (pathToMenuId[path]) {
    return pathToMenuId[path];
  }
  if (path.startsWith('/admin/extra-score-templates/moral')) {
    return 29;
  }
  if (path.startsWith('/admin/extra-score-templates/intellectual')) {
    return 30;
  }
  if (path.startsWith('/admin/extra-score-templates/physical')) {
    return 31;
  }
  if (path.startsWith('/admin/extra-score-review')) {
    return 32;
  }
  if (path.startsWith('/admin/extra-score-submission-windows')) {
    return 37;
  }
  if (path.startsWith('/admin/developer-credits')) {
    return 900;
  }
  if (path.startsWith('/admin/knowledge-logs')) {
    return 56;
  }
  if (path.startsWith('/admin/class-answer-summary')) {
    return 58;
  }
  if (path.startsWith('/admin/knowledge')) {
    return 54;
  }
  if (path.startsWith('/admin/composite-score-config')) {
    return 35;
  }
  if (path.startsWith('/admin/scores/history')) {
    return 38;
  }
  if (path.startsWith('/admin/second-class-scores')) {
    return 36;
  }
  return undefined;
};

export default routes;
