import AnnouncementCenterPanel from '../../components/AnnouncementCenterPanel';
import StudentLayout from '../../components/StudentLayout';

function StudentAnnouncementCenter() {
  return (
    <StudentLayout activePath="/student/announcements" breadcrumb="学生个人中心 / 通知公告">
      <AnnouncementCenterPanel showDevelopmentBadge />
    </StudentLayout>
  );
}

export default StudentAnnouncementCenter;
