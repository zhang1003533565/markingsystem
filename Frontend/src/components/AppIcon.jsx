import academicHome from '../assets/icons/academic-svg/home.svg';
import academicUsers from '../assets/icons/academic-svg/users.svg';
import academicCollege from '../assets/icons/academic-svg/college.svg';
import academicClass from '../assets/icons/academic-svg/class.svg';
import academicScores from '../assets/icons/academic-svg/scores.svg';
import academicAnalysis from '../assets/icons/academic-svg/analysis.svg';
import academicAnnouncement from '../assets/icons/academic-svg/announcement.svg';
import academicCalendar from '../assets/icons/academic-svg/calendar.svg';
import academicExtraScore from '../assets/icons/academic-svg/extra-score.svg';
import academicMoral from '../assets/icons/academic-svg/moral.svg';
import academicIntellectual from '../assets/icons/academic-svg/intellectual.svg';
import academicPhysical from '../assets/icons/academic-svg/physical.svg';
import academicImport from '../assets/icons/academic-svg/import.svg';
import academicReview from '../assets/icons/academic-svg/review.svg';
import academicProfile from '../assets/icons/academic-svg/profile.svg';
import academicLogout from '../assets/icons/academic-svg/logout.svg';
import academicEmail from '../assets/icons/academic-svg/email.svg';

const iconAssets = {
  home: academicHome,
  users: academicUsers,
  college: academicCollege,
  class: academicClass,
  scores: academicScores,
  analysis: academicAnalysis,
  announcement: academicAnnouncement,
  calendar: academicCalendar,
  extra: academicExtraScore,
  moral: academicMoral,
  intellectual: academicIntellectual,
  physical: academicPhysical,
  import: academicImport,
  review: academicReview,
  profile: academicProfile,
  logout: academicLogout,
  email: academicEmail
};

const semanticMap = {
  home: 'home',
  dashboard: 'home',
  homeOutlined: 'home',

  users: 'users',
  user: 'profile',
  student: 'users',
  studentList: 'users',
  teacher: 'users',
  committee: 'users',
  teamOutlined: 'users',
  userOutlined: 'profile',

  college: 'college',
  bankOutlined: 'college',
  school: 'college',

  class: 'class',
  form: 'review',
  formOutlined: 'review',

  score: 'scores',
  scores: 'scores',
  scoreboard: 'scores',
  barChartOutlined: 'scores',
  appstoreOutlined: 'scores',
  tableOutlined: 'scores',
  percentageOutlined: 'scores',
  bookOutlined: 'scores',
  bookFilled: 'scores',

  analysis: 'analysis',
  trend: 'analysis',
  lineChartOutlined: 'analysis',
  pieChartOutlined: 'analysis',
  orderedListOutlined: 'analysis',
  fundOutlined: 'analysis',
  riseOutlined: 'analysis',
  radarChartOutlined: 'analysis',

  announcement: 'announcement',
  notice: 'announcement',
  bellOutlined: 'announcement',
  notificationOutlined: 'announcement',
  warningOutlined: 'announcement',
  megaphone: 'announcement',
  flagOutlined: 'announcement',

  calendar: 'calendar',
  academicYear: 'calendar',
  calendarOutlined: 'calendar',

  extra: 'extra',
  plus: 'extra',
  plusCircleOutlined: 'extra',

  moral: 'moral',
  heartOutlined: 'moral',
  heartFilled: 'moral',
  safetyCertificateOutlined: 'moral',

  intellectual: 'intellectual',
  bulbOutlined: 'intellectual',
  starOutlined: 'intellectual',

  physical: 'physical',
  thunderboltOutlined: 'physical',
  trophyOutlined: 'physical',

  import: 'import',
  upload: 'import',
  download: 'import',
  file: 'import',
  fileText: 'import',
  fileOutlined: 'import',
  fileTextOutlined: 'import',
  uploadOutlined: 'import',
  downloadOutlined: 'import',

  review: 'review',
  search: 'review',
  view: 'review',
  approve: 'review',
  reject: 'review',
  history: 'review',
  info: 'review',
  reloadOutlined: 'review',
  searchOutlined: 'review',
  eyeOutlined: 'review',
  checkOutlined: 'review',
  checkCircleOutlined: 'review',
  closeCircleOutlined: 'review',
  clockCircleOutlined: 'review',
  rollbackOutlined: 'review',
  historyOutlined: 'review',
  infoCircleOutlined: 'review',
  linkOutlined: 'review',

  profile: 'profile',
  edit: 'profile',
  editOutlined: 'profile',
  mobileOutlined: 'profile',
  mailOutlined: 'profile',
  idcardOutlined: 'profile',
  lockOutlined: 'profile',

  logout: 'logout',
  logoutOutlined: 'logout',
  delete: 'logout',
  deleteOutlined: 'logout',
  close: 'logout',
  arrowUpOutlined: 'analysis',
  arrowDownOutlined: 'analysis',

  project: 'college',
  settings: 'college',
  log: 'review'
};

function normalizeName(name) {
  if (!name) {
    return 'review';
  }
  const raw = String(name).trim();
  const normalized = raw.charAt(0).toLowerCase() + raw.slice(1);
  return semanticMap[normalized] || semanticMap[raw] || normalized;
}

export function AppIcon({ name, size = 20, className = '', style, title }) {
  const key = normalizeName(name);
  const src = iconAssets[key] || academicReview;

  return (
    <span
      className={className}
      title={title}
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        width: size,
        height: size,
        flex: '0 0 auto',
        lineHeight: 0,
        ...style
      }}
    >
      <img
        src={src}
        alt=""
        aria-hidden="true"
        draggable="false"
        style={{
          width: '100%',
          height: '100%',
          objectFit: 'contain',
          display: 'block'
        }}
      />
    </span>
  );
}

export function getCollegeIconName(name) {
  const value = String(name || '');
  if (/体育/i.test(value)) return 'physical';
  if (/医|药|护理|生物/i.test(value)) return 'moral';
  if (/经济|金融|管理|会计/i.test(value)) return 'analysis';
  if (/外语|文学|教育|艺术|音乐/i.test(value)) return 'intellectual';
  return 'college';
}

export function getMajorIconName(name) {
  const value = String(name || '');
  if (/软件|计算机|网络|数据|数学|人工智能|信息/i.test(value)) return 'scores';
  if (/机械|电气|材料|土木|建筑|工程|交通|航空|航天|矿业|石油/i.test(value)) return 'class';
  if (/经济|金融|管理|会计/i.test(value)) return 'analysis';
  if (/医|药|护理|生物/i.test(value)) return 'moral';
  if (/体育/i.test(value)) return 'physical';
  return 'intellectual';
}

export function getClassIconName(name) {
  const value = String(name || '');
  if (/软|计|网|数/i.test(value)) return 'scores';
  if (/经|管|会/i.test(value)) return 'analysis';
  if (/医|药|护|生/i.test(value)) return 'moral';
  if (/体/i.test(value)) return 'physical';
  return 'class';
}

export default AppIcon;
