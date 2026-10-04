import './PageHeader.css';

/**
 * 通用页面标题栏组件
 * @param {Object} props
 * @param {string} props.title - 页面标题
 * @param {string} props.subtitle - 副标题/描述（可选）
 * @param {React.ReactNode} props.icon - 标题图标（可选）
 * @param {React.ReactNode[]} props.actions - 右侧操作按钮数组
 * @param {React.ReactNode} props.extra - 标题下方的额外内容区域（如搜索框等）
 * @param {string} props.gradient - 自定义渐变色（可选，默认紫色渐变）
 * @param {string} props.className - 额外的类名（可选）
 */
function PageHeader({ 
  title, 
  subtitle, 
  icon, 
  actions = [], 
  extra,
  gradient,
  className = '' 
}) {
  const style = gradient ? { background: gradient } : {};

  return (
    <div className={`page-header-container ${className}`} style={style}>
      <div className="page-header-main">
        <div className="page-header-left">
          <h2 className="page-header-title">
            {icon && <span className="page-header-icon">{icon}</span>}
            {title}
          </h2>
          {subtitle && <p className="page-header-subtitle">{subtitle}</p>}
        </div>
        {actions && actions.length > 0 && (
          <div className="page-header-actions">
            {actions}
          </div>
        )}
      </div>
      {extra && <div className="page-header-extra">{extra}</div>}
    </div>
  );
}

export default PageHeader;
