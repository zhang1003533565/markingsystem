import StudentLayout from '../../components/StudentLayout';
import ExtraScoreReviewPanel from '../../components/ExtraScoreReviewPanel';

function ClassStatistics() {
  return (
    <StudentLayout activePath="/student/class/stats" breadcrumb="班委工作台 / 班级综测提交审核">
      <ExtraScoreReviewPanel
        title="班级综测提交审核"
        description="查看本班学生当前周期的综测附加分提交；班委通过后申请会自动进入老师审核。"
        showForceTeacherStageAction={false}
      />
    </StudentLayout>
  );
}

export default ClassStatistics;
