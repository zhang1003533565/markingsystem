import { Navigate, useParams } from 'react-router-dom';

function LegacyExtraScoreRedirect({ type, mode }) {
  const { applicationId } = useParams();
  const basePath = mode === 'apply'
    ? '/student/extra/apply'
    : mode === 'edit'
      ? `/student/extra/edit/${applicationId}`
      : '/student/extra';

  return <Navigate to={`${basePath}?type=${type}`} replace />;
}

export default LegacyExtraScoreRedirect;
