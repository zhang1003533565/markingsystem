import { useMemo, useState } from 'react'
import { Segmented } from 'antd'
import {
  formatCompositeSignedScore,
  normalizeCompositeScoreDirection,
} from '../../utils/compositeScoreDetails'

const toNumber = (value) => {
  const numericValue = Number(value)
  return Number.isFinite(numericValue) ? numericValue : 0
}

function CompositeExtraBreakdown({ rows }) {
  const [filter, setFilter] = useState('all')

  const countedRows = useMemo(() => rows.filter((item) => item.counted !== false), [rows])
  const excludedRows = useMemo(() => rows.filter((item) => item.counted === false), [rows])
  const visibleRows =
    filter === 'counted' ? countedRows : filter === 'excluded' ? excludedRows : rows
  const effectiveTotal = countedRows.reduce(
    (total, item) => total + toNumber(item.effectiveSignedScore ?? item.signedScore),
    0,
  )

  return (
    <div className="composite-detail-section">
      <div className="composite-detail-summary composite-detail-summary--three">
        <div>
          <span>申请记录</span>
          <strong>{rows.length}</strong>
        </div>
        <div>
          <span>有效计入</span>
          <strong>{countedRows.length}</strong>
        </div>
        <div>
          <span>计入合计</span>
          <strong>{formatCompositeSignedScore(effectiveTotal)}</strong>
        </div>
      </div>

      <div className="composite-extra-toolbar">
        <Segmented
          value={filter}
          onChange={setFilter}
          options={[
            { label: `全部 ${rows.length}`, value: 'all' },
            { label: `已计入 ${countedRows.length}`, value: 'counted' },
            { label: `未计入 ${excludedRows.length}`, value: 'excluded' },
          ]}
        />
      </div>

      <div className="composite-extra-list">
        {visibleRows.map((item, index) => {
          const counted = item.counted !== false
          const direction = normalizeCompositeScoreDirection(item.scoreDirection)
          const originalScore = item.originalSignedScore ?? item.expectedScore
          const effectiveScore = item.effectiveSignedScore ?? item.signedScore ?? 0

          return (
            <article
              className={`composite-extra-item ${counted ? 'is-counted' : 'is-excluded'}`}
              key={item.id || index}
            >
              <div className="composite-extra-item__main">
                <div className="composite-extra-item__title">
                  <span className={`composite-count-tag ${counted ? 'counted' : 'excluded'}`}>
                    {counted ? '已计入' : '未计入'}
                  </span>
                  <div>
                    <strong>{item.itemName || '未命名项目'}</strong>
                    <p>
                      {[item.groupName, item.levelName, item.authorLabel]
                        .filter(Boolean)
                        .join(' · ') || '暂无规则说明'}
                    </p>
                  </div>
                </div>

                <div className="composite-extra-item__score">
                  <span>{direction === 'deduct' ? '减分项' : '加分项'}</span>
                  <div>
                    <small>原始 {formatCompositeSignedScore(originalScore)}</small>
                    <strong>{formatCompositeSignedScore(effectiveScore)}</strong>
                  </div>
                </div>
              </div>

              <div className="composite-extra-item__meta">
                {item.templateName ? <span>模板：{item.templateName}</span> : null}
                {item.awardTime ? <span>获得时间：{item.awardTime}</span> : null}
              </div>

              {!counted ? (
                <div className="composite-extra-item__reason">
                  <strong>{item.exclusiveRule || '未满足计入规则'}</strong>
                  <span>{item.excludeReason || '该项目未计入综测总分'}</span>
                  {item.countedApplicationLabel ? (
                    <small>已计入：{item.countedApplicationLabel}</small>
                  ) : null}
                </div>
              ) : null}
            </article>
          )
        })}
      </div>
    </div>
  )
}

export default CompositeExtraBreakdown
