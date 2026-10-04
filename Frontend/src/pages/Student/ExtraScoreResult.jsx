import { useEffect, useMemo, useState } from 'react'
import {
  Button,
  Card,
  Descriptions,
  Drawer,
  Empty,
  Segmented,
  Select,
  Skeleton,
  Tabs,
  Tag,
  Typography,
  message,
} from 'antd'
import { EyeOutlined, InfoCircleOutlined } from '@ant-design/icons'
import StudentLayout from '../../components/StudentLayout'
import {
  getStudentAcademicYears,
  getStudentCompositeExtraScoreDetails,
} from '../../api/student/portal'
import {
  COMPOSITE_CATEGORY_LABELS,
  formatCompositeSignedScore,
  normalizeCompositeScoreDirection,
} from '../../utils/compositeScoreDetails'
import {
  buildExtraScoreResultSummary,
  resolveExtraScoreReplacement,
  splitExtraScoreResultRows,
} from '../../utils/extraScoreResultSummary'
import './ExtraScoreResult.css'

const { Text, Title } = Typography

const scoreTypeOptions = [
  { value: 'all', label: '全部' },
  { value: 'MORAL', label: '德育' },
  { value: 'INTELLECTUAL', label: '智育' },
  { value: 'PHYSICAL', label: '体育' },
]

const scoreTypeColorMap = {
  MORAL: 'green',
  INTELLECTUAL: 'blue',
  PHYSICAL: 'orange',
}

const formatScore = (value) => `${formatCompositeSignedScore(value)} 分`
const formatDate = (value) => value || '-'
const formatRecordTitle = (record) => record?.itemName || record?.certName || '附加分记录'
const formatDirection = (value) =>
  normalizeCompositeScoreDirection(value) === 'deduct' ? '减分' : '加分'

function ExtraScoreResult() {
  const [loading, setLoading] = useState(true)
  const [academicYears, setAcademicYears] = useState([])
  const [selectedAcademicYear, setSelectedAcademicYear] = useState('')
  const [selectedScoreType, setSelectedScoreType] = useState('all')
  const [activeResultTab, setActiveResultTab] = useState('counted')
  const [detailRecord, setDetailRecord] = useState(null)
  const [rows, setRows] = useState([])

  useEffect(() => {
    const loadAcademicYears = async () => {
      setLoading(true)
      try {
        const response = await getStudentAcademicYears()
        const years = Array.isArray(response?.data) ? response.data : []
        setAcademicYears(years)
        const currentYear =
          years.find((item) => item.current && item.status !== 'inactive') || years[0]
        setSelectedAcademicYear(currentYear?.name || '')
      } catch (error) {
        message.error(error?.response?.data?.message || '获取学年失败')
        setLoading(false)
      }
    }

    loadAcademicYears()
  }, [])

  useEffect(() => {
    if (!selectedAcademicYear) {
      setRows([])
      setLoading(false)
      return
    }

    const loadRows = async () => {
      setLoading(true)
      try {
        const response = await getStudentCompositeExtraScoreDetails({
          academicYear: selectedAcademicYear,
          category: selectedScoreType === 'all' ? undefined : selectedScoreType,
        })
        setRows(Array.isArray(response?.data) ? response.data : [])
      } catch (error) {
        message.error(error?.response?.data?.message || '获取计入结果失败')
        setRows([])
      } finally {
        setLoading(false)
      }
    }

    loadRows()
  }, [selectedAcademicYear, selectedScoreType])

  const academicYearOptions = useMemo(() => {
    return academicYears
      .filter((item) => item?.status !== 'inactive')
      .map((item) => ({
        value: item.name,
        label: item.current ? `${item.name}（当前）` : item.name,
      }))
  }, [academicYears])

  const summary = useMemo(() => buildExtraScoreResultSummary(rows), [rows])
  const { countedRows, excludedRows } = useMemo(() => splitExtraScoreResultRows(rows), [rows])
  const detailReplacement = useMemo(
    () => resolveExtraScoreReplacement(rows, detailRecord),
    [detailRecord, rows],
  )

  const getReplacementText = (record) => {
    const replacement = resolveExtraScoreReplacement(rows, record)
    if (replacement) {
      return `${formatRecordTitle(replacement)} · ${replacement.levelName || '未设置级别'} · ${formatScore(replacement.effectiveSignedScore ?? replacement.signedScore)}`
    }
    return record?.countedApplicationLabel || ''
  }

  const renderRecordList = (records, excluded = false) => {
    if (loading) {
      return (
        <div className="extra-result-loading">
          <Skeleton active paragraph={{ rows: 4 }} title={false} />
        </div>
      )
    }

    if (!records.length) {
      return (
        <div className="extra-result-empty">
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description={excluded ? '暂无未计入记录' : '暂无有效计入记录'}
          />
        </div>
      )
    }

    return (
      <div className="extra-result-record-list">
        {records.map((record) => {
          const replacementText = excluded ? getReplacementText(record) : ''
          const metaItems = [
            record.groupName,
            record.levelName,
            formatDate(record.awardTime),
          ].filter((item) => item && item !== '-')
          return (
            <div
              key={record.id}
              className={`extra-result-record ${excluded ? 'is-excluded' : 'is-counted'}`}
            >
              <div className="extra-result-record-main">
                <div className="extra-result-record-title">
                  <Tag color={scoreTypeColorMap[record.scoreType] || 'default'}>
                    {COMPOSITE_CATEGORY_LABELS[record.scoreType] || record.scoreType || '其他'}
                  </Tag>
                  <strong>{formatRecordTitle(record)}</strong>
                </div>
                <div className="extra-result-record-meta">
                  {metaItems.map((item, index) => (
                    <span key={`${record.id}-meta-${index}`}>{item}</span>
                  ))}
                </div>
              </div>

              {excluded ? (
                <div className="extra-result-record-reason">
                  <strong>{record.exclusiveRule || '未计入'}</strong>
                  <span>
                    {replacementText
                      ? `已由 ${replacementText} 计入`
                      : record.excludeReason || '该记录不符合当前模板的计入规则'}
                  </span>
                </div>
              ) : null}

              <div className="extra-result-record-score">
                <span>{excluded ? '原始分' : '计入分'}</span>
                <strong className={excluded ? 'muted' : ''}>
                  {formatScore(
                    excluded
                      ? (record.originalSignedScore ?? record.expectedScore)
                      : (record.effectiveSignedScore ?? record.signedScore),
                  )}
                </strong>
                {excluded ? <em>未计入</em> : null}
              </div>

              <Button
                type="text"
                size="small"
                icon={<EyeOutlined />}
                onClick={() => setDetailRecord(record)}
              >
                详情
              </Button>
            </div>
          )
        })}
      </div>
    )
  }

  const detailItems = detailRecord
    ? [
        {
          key: 'result',
          label: '计入结果',
          children:
            detailRecord.counted === false ? (
              <Tag color="red">未计入</Tag>
            ) : (
              <Tag color="green">已计入</Tag>
            ),
        },
        {
          key: 'scoreType',
          label: '分类',
          children:
            COMPOSITE_CATEGORY_LABELS[detailRecord.scoreType] || detailRecord.scoreType || '-',
        },
        { key: 'itemName', label: '项目', children: formatRecordTitle(detailRecord) },
        { key: 'groupName', label: '分组', children: detailRecord.groupName || '-' },
        { key: 'levelName', label: '级别', children: detailRecord.levelName || '-' },
        {
          key: 'originalScore',
          label: '原始分',
          children: formatScore(detailRecord.originalSignedScore ?? detailRecord.expectedScore),
        },
        {
          key: 'effectiveScore',
          label: '计入分',
          children: formatScore(detailRecord.effectiveSignedScore ?? detailRecord.signedScore),
        },
        { key: 'awardTime', label: '获得时间', children: formatDate(detailRecord.awardTime) },
        { key: 'templateName', label: '使用模板', children: detailRecord.templateName || '-' },
        { key: 'authorLabel', label: '作者/团队', children: detailRecord.authorLabel || '-' },
        {
          key: 'direction',
          label: '计分方向',
          children: formatDirection(detailRecord.scoreDirection),
        },
        { key: 'certName', label: '证书名称', children: detailRecord.certName || '-' },
        ...(detailRecord.detailDesc
          ? [
              {
                key: 'detailDesc',
                label: '具体说明',
                children: detailRecord.detailDesc,
              },
            ]
          : []),
        ...(detailRecord.counted === false
          ? [
              {
                key: 'exclusiveRule',
                label: '未计入规则',
                children: detailRecord.exclusiveRule || '-',
              },
              {
                key: 'excludeReason',
                label: '具体原因',
                children: detailRecord.excludeReason || '该记录未计入综测总分',
              },
              {
                key: 'replacement',
                label: '替代记录',
                children: detailReplacement
                  ? `${formatRecordTitle(detailReplacement)} · ${detailReplacement.levelName || '未设置级别'} · ${formatScore(detailReplacement.effectiveSignedScore ?? detailReplacement.signedScore)}`
                  : detailRecord.countedApplicationLabel || '-',
              },
            ]
          : []),
      ]
    : []

  const resultTabs = [
    {
      key: 'counted',
      label: `已计入 ${countedRows.length}`,
      children: renderRecordList(countedRows),
    },
    {
      key: 'excluded',
      label: `未计入 ${excludedRows.length}`,
      children: renderRecordList(excludedRows, true),
    },
  ]

  return (
    <StudentLayout activePath="/student/extra/result" breadcrumb="附加分 / 计入结果">
      <div className="section extra-score-result-page">
        <div className="extra-result-page-head">
          <div>
            <Title level={3}>附加分计入结果</Title>
            <Text type="secondary">查看最终计入成绩，以及未计入记录的具体原因。</Text>
          </div>
          <div className="extra-result-filters">
            <div className="extra-result-year-filter">
              <span>学年</span>
              <Select
                aria-label="选择学年"
                placeholder="请选择学年"
                loading={loading && !academicYearOptions.length}
                value={selectedAcademicYear || undefined}
                options={academicYearOptions}
                onChange={(value) => setSelectedAcademicYear(value || '')}
              />
            </div>
            <Segmented
              aria-label="附加分分类"
              value={selectedScoreType}
              options={scoreTypeOptions}
              onChange={(value) => setSelectedScoreType(value)}
            />
          </div>
        </div>

        <Card variant="borderless" className="extra-result-overview-card">
          {loading ? (
            <Skeleton active paragraph={{ rows: 1 }} />
          ) : (
            <div className="extra-result-overview">
              <div className="extra-result-final-score">
                <span>最终计入</span>
                <strong>{formatScore(summary.countedScore)}</strong>
              </div>
              <div className="extra-result-overview-context">
                <div className="extra-result-overview-counts">
                  <span>
                    审核通过 <strong>{summary.totalCount}</strong> 条
                  </span>
                  <span>
                    有效计入 <strong>{summary.countedCount}</strong> 条
                  </span>
                  <span>
                    未计入 <strong>{summary.excludedCount}</strong> 条
                  </span>
                </div>
                <div className="extra-result-overview-note">
                  <InfoCircleOutlined />
                  <span>审核通过后仍需按照模板互斥、最高分和次数上限规则计算最终结果。</span>
                </div>
              </div>
            </div>
          )}
        </Card>

        <Card variant="borderless" className="extra-result-content-card">
          <Tabs activeKey={activeResultTab} items={resultTabs} onChange={setActiveResultTab} />
        </Card>
      </div>

      <Drawer
        title="附加分详情"
        width={520}
        open={Boolean(detailRecord)}
        destroyOnHidden
        onClose={() => setDetailRecord(null)}
      >
        <Descriptions
          className="extra-result-detail-descriptions"
          bordered
          size="small"
          column={1}
          items={detailItems}
        />
      </Drawer>
    </StudentLayout>
  )
}

export default ExtraScoreResult
