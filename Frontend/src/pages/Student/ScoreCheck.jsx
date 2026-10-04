import StudentLayout from '../../components/StudentLayout';
import ExtraScoreReviewPanel from '../../components/ExtraScoreReviewPanel';

function ScoreCheck() {
  return (
    <StudentLayout activePath="/student/class/check" breadcrumb="班委工作台 / 宗测审核">
      <div className="student-page-wrapper">
        <ExtraScoreReviewPanel />
      </div>
    </StudentLayout>
  );
}

export default ScoreCheck;
