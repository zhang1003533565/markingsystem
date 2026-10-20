import { Drawer, Empty, Skeleton } from 'antd'
import {
  buildCompositeDetailEmptyText,
  buildCompositeDetailTitle,
} from '../../utils/compositeScoreDetails'
import CompositeBaseBreakdown from './CompositeBaseBreakdown'
import CompositeExtraBreakdown from './CompositeExtraBreakdown'
import CompositeScoreSummary from './CompositeScoreSummary'
import './CompositeScoreDetail.css'

function CompositeScoreDetailDrawer({
  open,
  onClose,
  studentName,
  className,
  academicYear,
  type,
  category,
  rows = [],
  loading = false,
  finalScore,
  weight,
  summary,
}) {
  const title = buildCompositeDetailTitle(type, category)
  const emptyText = buildCompositeDetailEmptyText(type, category)

  return (
    <Drawer
      open={open}
      onClose={onClose}
      width={780}
      destroyOnHidden
      rootClassName="composite-score-detail-drawer"
      title={
        <div className="composite-detail-drawer-title">
          <strong>
            {studentName || '学生'}的{title}
          </strong>
          <span>
            {academicYear || '全部学年'} · {className || '未分配班级'}
          </span>
        </div>
      }
    >
      <div className="composite-score-detail-body">
        <CompositeScoreSummary summary={summary} />
        {loading ? (
          <div className="composite-detail-loading">
            <Skeleton active title paragraph={{ rows: 5 }} />
          </div>
        ) : rows.length === 0 ? (
          <div className="composite-detail-empty">
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={emptyText} />
          </div>
        ) : type === 'base' ? (
          <CompositeBaseBreakdown
            category={category}
            rows={rows}
            finalScore={finalScore}
            weight={weight}
          />
        ) : (
          <CompositeExtraBreakdown rows={rows} />
        )}
      </div>
    </Drawer>
  )
}

export default CompositeScoreDetailDrawer
