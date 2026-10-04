import reviewIcon from '../../assets/icons/academic-svg/review.svg';
import ExtraScoreReviewPanel from '../../components/ExtraScoreReviewPanel';
import './ExtraScoreReview.css';
import './ExtraScoreReviewWorkbench.css';

function ExtraScoreReview() {
  return (
    <div
      className="extra-score-review-page"
      style={{ '--extra-score-review-icon': `url(${reviewIcon})` }}
    >
      <ExtraScoreReviewPanel showPageHeader={false} workbenchMode />
    </div>
  );
}

export default ExtraScoreReview;
