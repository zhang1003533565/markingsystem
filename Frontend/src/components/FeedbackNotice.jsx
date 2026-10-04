import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  InfoCircleOutlined,
  WarningOutlined,
} from './AcademicIcons';
import './FeedbackNotice.css';

const noticeMeta = {
  success: { icon: CheckCircleOutlined, fallbackTitle: '操作已完成' },
  error: { icon: CloseCircleOutlined, fallbackTitle: '操作未完成' },
  warning: { icon: WarningOutlined, fallbackTitle: '暂时无法操作' },
  info: { icon: InfoCircleOutlined, fallbackTitle: '提示信息' },
};

function FeedbackNotice({
  type = 'info',
  title,
  message,
  reason,
  suggestion,
  actions,
  onDismiss,
  className = '',
}) {
  const normalizedType = noticeMeta[type] ? type : 'info';
  const { icon: NoticeIcon, fallbackTitle } = noticeMeta[normalizedType];

  return (
    <section
      className={`feedback-notice feedback-notice-${normalizedType} ${className}`.trim()}
      role={normalizedType === 'error' || normalizedType === 'warning' ? 'alert' : 'status'}
    >
      <div className="feedback-notice-icon" aria-hidden="true">
        <NoticeIcon />
      </div>
      <div className="feedback-notice-content">
        <div className="feedback-notice-title">{title || fallbackTitle}</div>
        {message ? <div className="feedback-notice-message">{message}</div> : null}
        {reason || suggestion ? (
          <div className="feedback-notice-details">
            {reason ? (
              <div className="feedback-notice-explanation">
                <span>原因</span>
                <p>{reason}</p>
              </div>
            ) : null}
            {suggestion ? (
              <div className="feedback-notice-explanation suggestion">
                <span>建议</span>
                <p>{suggestion}</p>
              </div>
            ) : null}
          </div>
        ) : null}
        {actions ? <div className="feedback-notice-actions">{actions}</div> : null}
      </div>
      {onDismiss ? (
        <button
          type="button"
          className="feedback-notice-dismiss"
          onClick={onDismiss}
          aria-label="关闭提示"
        >
          ×
        </button>
      ) : null}
    </section>
  );
}

export default FeedbackNotice;
