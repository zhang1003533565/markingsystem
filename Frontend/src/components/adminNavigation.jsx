import addScoreIcon from '../assets/index/add-score.png';
import announcementIcon from '../assets/index/gonggao.png';
import homeIcon from '../assets/index/home.png';
import personIcon from '../assets/index/person.png';
import scoreIcon from '../assets/index/scor.png';
import statsIcon from '../assets/index/shuju.png';
import studentsIcon from '../assets/index/students.png';

const addScoreSidebarIcon = <img src={addScoreIcon} alt="" style={{ width: 18, height: 18, objectFit: 'contain' }} />;
const announcementSidebarIcon = <img src={announcementIcon} alt="" style={{ width: 18, height: 18, objectFit: 'contain' }} />;
const sidebarIcon = <img src={homeIcon} alt="" style={{ width: 18, height: 18, objectFit: 'contain' }} />;
const personSidebarIcon = <img src={personIcon} alt="" style={{ width: 18, height: 18, objectFit: 'contain' }} />;
const scoreSidebarIcon = <img src={scoreIcon} alt="" style={{ width: 18, height: 18, objectFit: 'contain' }} />;
const statsSidebarIcon = <img src={statsIcon} alt="" style={{ width: 18, height: 18, objectFit: 'contain' }} />;
const studentsSidebarIcon = <img src={studentsIcon} alt="" style={{ width: 18, height: 18, objectFit: 'contain' }} />;

export function buildAdminMenuItems(role) {
  const isAdmin = role === 'ADMIN';
  const isTeacher = role === 'TEACHER';

  const baseMenuItems = [
    {
      id: 100,
      icon: sidebarIcon,
      title: '首页',
      children: [
        { id: 1, icon: sidebarIcon, title: '首页', parentId: 100 }
      ]
    },
    {
      id: 2,
      icon: studentsSidebarIcon,
      title: '学生管理',
      children: [
        { id: 21, icon: sidebarIcon, title: '学生列表', parentId: 2 },
        { id: 24, icon: sidebarIcon, title: '班级管理', parentId: 2 }
      ]
    },
    {
      id: 3,
      icon: scoreSidebarIcon,
      title: '成绩管理',
      children: [
        { id: 26, icon: sidebarIcon, title: '成绩导入', parentId: 3 },
        { id: 38, icon: sidebarIcon, title: '导入历史', parentId: 3 },
        { id: 27, icon: sidebarIcon, title: '成绩查询', parentId: 3 },
        { id: 36, icon: sidebarIcon, title: '二课成绩导入', parentId: 3 },
        { id: 35, icon: sidebarIcon, title: '综测配置', parentId: 3 },
        { id: 34, icon: sidebarIcon, title: '综合成绩', parentId: 3 }
      ]
    },
    {
      id: 290,
      icon: addScoreSidebarIcon,
      title: '附加分管理',
      children: [
        { id: 29, icon: sidebarIcon, title: '德育附加分模板', parentId: 290 },
        { id: 30, icon: sidebarIcon, title: '智育附加分模板', parentId: 290 },
        { id: 31, icon: sidebarIcon, title: '体育附加分模板', parentId: 290 },
        { id: 32, icon: sidebarIcon, title: '附加分审核', parentId: 290 },
        { id: 37, icon: sidebarIcon, title: '附加分提交周期', parentId: 290 }
      ]
    },
    {
      id: 280,
      icon: announcementSidebarIcon,
      title: '通知公告',
      children: [
        { id: 28, icon: sidebarIcon, title: '通知公告', parentId: 280 }
      ]
    },
    {
      id: 40,
      icon: statsSidebarIcon,
      title: '数据统计',
      children: [
        { id: 4, icon: sidebarIcon, title: '数据统计', parentId: 40 }
      ]
    }
  ];

  const adminMenuItems = isAdmin
    ? [
        {
          id: 5,
          icon: sidebarIcon,
          title: '系统设置',
          children: [
            { id: 22, icon: sidebarIcon, title: '学院管理', parentId: 5 },
            { id: 23, icon: sidebarIcon, title: '专业管理', parentId: 5 },
            { id: 33, icon: sidebarIcon, title: '年级管理', parentId: 5 },
            { id: 25, icon: sidebarIcon, title: '学年管理', parentId: 5 },
            { id: 51, icon: sidebarIcon, title: '用户管理', parentId: 5 },
            { id: 52, icon: sidebarIcon, title: '系统邮箱', parentId: 5 },
            { id: 54, icon: sidebarIcon, title: 'AI 知识库', parentId: 5 },
            { id: 56, icon: sidebarIcon, title: 'AI 对话日志', parentId: 5 }
          ]
        }
      ]
    : [];

  const teacherMenuItems = isTeacher
    ? [
        {
          id: 531,
          icon: sidebarIcon,
          title: '班级答疑',
          children: [
            { id: 58, icon: sidebarIcon, title: '班级答疑总结', parentId: 531 },
            { id: 56, icon: sidebarIcon, title: 'AI 对话日志', parentId: 531 }
          ]
        },
        {
          id: 530,
          icon: personSidebarIcon,
          title: '个人信息',
          children: [
            { id: 53, icon: sidebarIcon, title: '个人信息', parentId: 530 }
          ]
        }
      ]
    : [];

  return [...baseMenuItems, ...adminMenuItems, ...teacherMenuItems].reduce((acc, item) => {
    acc.push(item);
    if (item.children) {
      acc.push(...item.children);
    }
    return acc;
  }, []);
}

function appendTemplateBreadcrumbs(items, pathname, type, label) {
  const basePath = `/admin/extra-score-templates/${type}`;
  const isListPage = pathname === basePath || pathname === `${basePath}/`;
  items.push(
    { label: '附加分管理' },
    { label, path: isListPage ? undefined : basePath }
  );
  if (isListPage) {
    return items;
  }

  const currentLabel = pathname.includes('/items/') || pathname.includes('/item/')
    ? '小类配置'
    : pathname.includes('/groups/') || pathname.includes('/group/')
      ? '大类配置'
      : pathname.endsWith('/new')
        ? '新建模板'
        : '编辑模板';
  items.push({ label: currentLabel });
  return items;
}

export function getAdminBreadcrumbs(pathname, role) {
  const rootLabel = role === 'ADMIN' ? '管理端' : '教师端';
  const items = [{ label: rootLabel, path: '/admin/home' }];

  if (pathname.startsWith('/admin/home')) {
    items.push({ label: '首页' });
    return items;
  }

  if (pathname.startsWith('/admin/students')) {
    items.push({ label: '学生管理' }, { label: '学生列表' });
    return items;
  }

  if (pathname.startsWith('/admin/classes')) {
    items.push({ label: '学生管理' }, { label: '班级管理' });
    return items;
  }

  if (pathname.startsWith('/admin/scores')) {
    const label = pathname.startsWith('/admin/scores/query')
      ? '成绩查询'
      : pathname.startsWith('/admin/scores/history')
        ? '导入历史'
        : '成绩导入';
    items.push({ label: '成绩管理' }, { label });
    return items;
  }

  if (pathname.startsWith('/admin/second-class-scores')) {
    items.push({ label: '成绩管理' }, { label: '二课成绩导入' });
    return items;
  }

  if (pathname.startsWith('/admin/composite-score-config')) {
    items.push({ label: '成绩管理' }, { label: '综测配置' });
    return items;
  }

  if (pathname.startsWith('/admin/composite-scores')) {
    items.push({ label: '成绩管理' }, { label: '综合成绩' });
    return items;
  }

  if (pathname.startsWith('/admin/extra-score-templates/moral')) {
    return appendTemplateBreadcrumbs(items, pathname, 'moral', '德育附加分模板');
  }

  if (pathname.startsWith('/admin/extra-score-templates/intellectual')) {
    return appendTemplateBreadcrumbs(items, pathname, 'intellectual', '智育附加分模板');
  }

  if (pathname.startsWith('/admin/extra-score-templates/physical')) {
    return appendTemplateBreadcrumbs(items, pathname, 'physical', '体育附加分模板');
  }

  if (pathname.startsWith('/admin/extra-score-review')) {
    items.push({ label: '附加分管理' }, { label: '附加分审核' });
    return items;
  }

  if (pathname.startsWith('/admin/extra-score-submission-windows')) {
    items.push({ label: '附加分管理' }, { label: '附加分提交周期' });
    return items;
  }

  if (pathname.startsWith('/admin/announcements')) {
    items.push({ label: '通知公告' });
    return items;
  }

  if (pathname.startsWith('/admin/analysis')) {
    items.push({ label: '数据统计' });
    return items;
  }

  if (pathname.startsWith('/admin/profile')) {
    items.push({ label: '个人信息' });
    return items;
  }

  if (pathname.startsWith('/admin/class-answer-summary')) {
    items.push({ label: '班级答疑' }, { label: '班级答疑总结' });
    return items;
  }

  if (pathname.startsWith('/admin/knowledge-logs')) {
    if (role === 'TEACHER') {
      items.push({ label: '班级答疑' }, { label: 'AI 对话日志' });
    } else {
      items.push({ label: '系统设置' }, { label: 'AI 对话日志' });
    }
    return items;
  }

  if (pathname.startsWith('/admin/knowledge')) {
    items.push({ label: '系统设置' }, { label: 'AI 知识库' });
    return items;
  }

  if (pathname.startsWith('/admin/developer-credits')) {
    items.push({ label: '系统信息' }, { label: '开发者致谢' });
    return items;
  }

  if (pathname.startsWith('/admin/colleges')) {
    items.push({ label: '系统设置' }, { label: '学院管理' });
    return items;
  }

  if (pathname.startsWith('/admin/majors')) {
    items.push({ label: '系统设置' }, { label: '专业管理' });
    return items;
  }

  if (pathname.startsWith('/admin/grades')) {
    items.push({ label: '系统设置' }, { label: '年级管理' });
    return items;
  }

  if (pathname.startsWith('/admin/academic-years')) {
    items.push({ label: '系统设置' }, { label: '学年管理' });
    return items;
  }

  if (pathname.startsWith('/admin/users')) {
    items.push({ label: '系统设置' }, { label: '用户管理' });
    return items;
  }

  if (pathname.startsWith('/admin/email-config')) {
    items.push({ label: '系统设置' }, { label: '系统邮箱' });
    return items;
  }

  items.push({ label: '后台管理' });
  return items;
}
