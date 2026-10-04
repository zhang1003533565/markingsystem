import { useEffect, useMemo, useState } from 'react'
import {
  Button,
  Descriptions,
  Drawer,
  Dropdown,
  Empty,
  Image,
  Input,
  Pagination,
  Select,
  Skeleton,
  Tag,
  Timeline,
  Typography,
  message,
} from 'antd'
import {
  DownOutlined,
  DownloadOutlined,
  EditOutlined,
  EyeOutlined,
  SearchOutlined,
} from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import StudentLayout from '../../components/StudentLayout'
import {
  exportMyExtraScoreCertificates,
  exportMyExtraScoreExcel,
  getExtraScoreApplicationDetail,
  getExtraScoreApplicationHistory,
} from '../../api/student/extraScore'
import { getStudentAcademicYears } from '../../api/student/portal'
import { withAuthToken } from '../../utils/authStorage'
import './ExtraScoreHistory.css'

const { Text, Title } = Typography

const scoreTypeOptions = [
  { value: 'all', label: '全部分类' },
  { value: 'MORAL', label: '德育' },
  { value: 'INTELLECTUAL', label: '智育' },
  { value: 'PHYSICAL', label: '体育' },
]

const statusGroupOptions = [
  { value: 'all', label: '全部状态' },
  { value: 'action_required', label: '需要处理' },
  { value: 'reviewing', label: '审核中' },
  { value: 'completed', label: '已完成' },
]

const scoreTypeLabelMap = {
  MORAL: '德育',
  INTELLECTUAL: '智育',
  PHYSICAL: '体育',
}

const statusColorMap = {
  draft: 'default',
  returned: 'red',
  rejected: 'red',
  pending: 'gold',
  approved: 'green',
}

const toStoredList = (value) => {
  if (Array.isArray(value)) {
    return value.filter(Boolean)
  }
  const text = String(value || '')
  return text ? text.split('||').filter(Boolean) : []
}

const getFileUrl = (value) => {
  const text = String(value || '')
  return text.startsWith('/uploads/') ? `/api${text}` : text
}

const isImageUrl = (value) => /\.(png|jpe?g|webp|gif|bmp|svg)(\?.*)?$/i.test(String(value || ''))

const formatDateTime = (value) => (value ? String(value).replace('T', ' ').slice(0, 16) : '-')

const formatScore = (value, direction) => {
  const score = Number(value || 0).toFixed(1)
  return `${String(direction || 'add').toLowerCase() === 'deduct' ? '-' : '+'}${score} 分`
}

const downloadBlob = (blob, filename) => {
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  link.remove()
  window.URL.revokeObjectURL(url)
}

function ExtraScoreHistory() {
  const navigate = useNavigate()
  const [loading, setLoading] = useState(true)
  const [academicYears, setAcademicYears] = useState([])
  const [selectedAcademicYear, setSelectedAcademicYear] = useState('')
  const [selectedScoreType, setSelectedScoreType] = useState('all')
  const [selectedStatusGroup, setSelectedStatusGroup] = useState('all')
  const [keywordInput, setKeywordInput] = useState('')
  const [keyword, setKeyword] = useState('')
  const [records, setRecords] = useState([])
  const [counts, setCounts] = useState({ all: 0, actionRequired: 0, reviewing: 0, completed: 0 })
  const [pagination, setPagination] = useState({ current: 1, pageSize: 20, total: 0 })
  const [exporting, setExporting] = useState('')
  const [detailOpen, setDetailOpen] = useState(false)
  const [detailLoading, setDetailLoading] = useState(false)
  const [detailRecord, setDetailRecord] = useState(null)
  const { current, pageSize } = pagination

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setKeyword(keywordInput.trim())
      setPagination((previous) => (previous.current === 1 ? previous : { ...previous, current: 1 }))
    }, 300)
    return () => window.clearTimeout(timer)
  }, [keywordInput])

  useEffect(() => {
    const loadAcademicYears = async () => {
      setLoading(true)
      try {
        const response = await getStudentAcademicYears()
        const years = Array.isArray(response?.data) ? response.data : []
        setAcademicYears(years)
        const currentYear =
          years.find((item) => item.current && item.status !== 'inactive') ||
          years.find((item) => item.status !== 'inactive')
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
      setRecords([])
      setLoading(false)
      return
    }

    const loadHistory = async () => {
      setLoading(true)
      try {
        const response = await getExtraScoreApplicationHistory({
          academicYear: selectedAcademicYear,
          scoreType: selectedScoreType,
          statusGroup: selectedStatusGroup,
          keyword: keyword || undefined,
          page: current - 1,
          size: pageSize,
        })
        const payload = response?.data || {}
        setRecords(Array.isArray(payload.records) ? payload.records : [])
        setCounts(payload.counts || { all: 0, actionRequired: 0, reviewing: 0, completed: 0 })
        setPagination((previous) => ({
          ...previous,
          total: Number(payload.total || 0),
        }))
      } catch (error) {
        message.error(error?.response?.data?.message || '获取申报历史失败')
        setRecords([])
      } finally {
        setLoading(false)
      }
    }

    loadHistory()
  }, [current, keyword, pageSize, selectedAcademicYear, selectedScoreType, selectedStatusGroup])

  const academicYearOptions = useMemo(
    () =>
      academicYears
        .filter((item) => item?.status !== 'inactive')
        .map((item) => ({
          value: item.name,
          label: item.current ? `${item.name}（当前）` : item.name,
        })),
    [academicYears],
  )

  const actionableRecords = useMemo(
    () => records.filter((record) => record.actionRequired),
    [records],
  )
  const otherRecords = useMemo(() => records.filter((record) => !record.actionRequired), [records])

  const buildExportParams = () => ({
    academicYear: selectedAcademicYear || undefined,
    scoreType: selectedScoreType === 'all' ? undefined : selectedScoreType,
  })

  const handleExport = async (key) => {
    setExporting(key)
    try {
      if (key === 'excel') {
        const response = await exportMyExtraScoreExcel(buildExportParams())
        downloadBlob(response, '附加分申报明细.xlsx')
      } else {
        const response = await exportMyExtraScoreCertificates(buildExportParams())
        downloadBlob(response, '附加分证明材料.zip')
      }
    } catch (error) {
      message.error(error?.response?.data?.message || '导出失败')
    } finally {
      setExporting('')
    }
  }

  const handleOpenDetail = async (record) => {
    setDetailRecord(record)
    setDetailOpen(true)
    setDetailLoading(true)
    try {
      const response = await getExtraScoreApplicationDetail(record.id)
      setDetailRecord(response?.data || record)
    } catch (error) {
      message.error(error?.response?.data?.message || '获取详情失败')
    } finally {
      setDetailLoading(false)
    }
  }

  const handlePrimaryAction = (record) => {
    if (record.actionRequired) {
      navigate(`/student/extra/edit/${record.id}?type=${record.scoreType}`, {
        state: { returnTo: '/student/extra/history' },
      })
      return
    }
    handleOpenDetail(record)
  }

  const renderRecord = (record) => {
    const title = record.certName || record.itemName || '附加分申请'
    const meta = [
      scoreTypeLabelMap[record.scoreType] || record.scoreType,
      record.itemName && record.itemName !== title ? record.itemName : record.groupName,
      record.levelName,
      record.submissionPeriodLabel,
      formatDateTime(record.updatedAt || record.uploadTime),
    ].filter((item) => item && item !== '-')
    const showReturnReason = ['returned', 'rejected'].includes(record.status) && record.returnReason

    return (
      <article
        key={record.id}
        className={`extra-history-record${record.actionRequired ? ' is-actionable' : ''}`}
      >
        <div className="extra-history-record-status">
          <Tag color={statusColorMap[record.status] || 'default'}>
            {record.displayStatus || record.statusLabel || '未知状态'}
          </Tag>
        </div>
        <div className="extra-history-record-main">
          <strong>{title}</strong>
          <div className="extra-history-record-meta">
            {meta.map((item, index) => (
              <span key={`${record.id}-meta-${index}`}>{item}</span>
            ))}
          </div>
          {showReturnReason ? (
            <div className="extra-history-return-reason">{record.returnReason}</div>
          ) : null}
        </div>
        <div className="extra-history-record-score">
          <span>申报分值</span>
          <strong>{formatScore(record.expectedScore, record.scoreDirection)}</strong>
        </div>
        <div className="extra-history-record-actions">
          {record.actionRequired ? (
            <Button
              type="primary"
              size="small"
              icon={<EditOutlined />}
              onClick={() => handlePrimaryAction(record)}
            >
              {record.primaryActionLabel || '修改重提'}
            </Button>
          ) : null}
          <Button
            type="text"
            size="small"
            icon={<EyeOutlined />}
            onClick={() => handleOpenDetail(record)}
          >
            查看
          </Button>
        </div>
      </article>
    )
  }

  const detailMedia = useMemo(() => {
    if (!detailRecord) {
      return []
    }
    const media = []
    const certificateUrls = toStoredList(
      detailRecord.certFileUrls?.length ? detailRecord.certFileUrls : detailRecord.certFileUrl,
    )
    const certificateNames = toStoredList(
      detailRecord.certFileNames?.length ? detailRecord.certFileNames : detailRecord.certFileName,
    )
    certificateUrls.forEach((url, index) => {
      media.push({
        url,
        name: certificateNames[index] || detailRecord.certName || `证书${index + 1}`,
        source: '证书',
      })
    })
    if (Array.isArray(detailRecord.proofMedia) && detailRecord.proofMedia.length) {
      detailRecord.proofMedia.forEach((item, index) => {
        if (item?.url) {
          media.push({
            url: item.url,
            name: item.name || `凭证${index + 1}`,
            source: '凭证',
            type: item.type,
          })
        }
      })
    } else {
      toStoredList(detailRecord.proofImages).forEach((url, index) => {
        media.push({ url, name: `凭证${index + 1}`, source: '凭证' })
      })
    }
    return media.filter(
      (item, index, list) => list.findIndex((candidate) => candidate.url === item.url) === index,
    )
  }, [detailRecord])

  const detailDescriptionItems = detailRecord
    ? [
        {
          key: 'path',
          label: '申报项目',
          children:
            [detailRecord.groupName, detailRecord.itemName].filter(Boolean).join(' / ') || '-',
        },
        { key: 'level', label: '级别', children: detailRecord.levelName || '-' },
        { key: 'author', label: '作者/团队', children: detailRecord.authorLabel || '-' },
        {
          key: 'period',
          label: '所属周期',
          children: detailRecord.submissionPeriodLabel || '-',
        },
        { key: 'awardTime', label: '获得时间', children: detailRecord.awardTime || '-' },
        {
          key: 'updatedAt',
          label: '最近更新',
          children: formatDateTime(detailRecord.updatedAt || detailRecord.uploadTime),
        },
        ...(detailRecord.detailDesc || detailRecord.proofText
          ? [
              {
                key: 'description',
                label: '申请说明',
                children: detailRecord.detailDesc || detailRecord.proofText,
              },
            ]
          : []),
      ]
    : []

  const timelineItems = Array.isArray(detailRecord?.events)
    ? detailRecord.events.map((event) => ({
        color: event.eventType?.includes('RETURNED')
          ? 'red'
          : event.eventType?.includes('APPROVED')
            ? 'green'
            : 'blue',
        children: (
          <div className="extra-history-timeline-item">
            <strong>{event.eventLabel || event.eventType}</strong>
            <span>{formatDateTime(event.occurredAt)}</span>
            {event.operatorName ? <span>{event.operatorName}</span> : null}
            {event.comment ? <p>{event.comment}</p> : null}
          </div>
        ),
      }))
    : []

  const exportMenu = {
    items: [
      { key: 'excel', label: '导出申报明细 Excel' },
      { key: 'certificates', label: '导出证明材料 ZIP' },
    ],
    onClick: ({ key }) => handleExport(key),
  }

  return (
    <StudentLayout activePath="/student/extra/history" breadcrumb="附加分 / 申报历史">
      <div className="section extra-history-page">
        <div className="extra-history-page-head">
          <Title level={3}>申报历史</Title>
          <Dropdown menu={exportMenu} trigger={['click']}>
            <Button icon={<DownloadOutlined />} loading={Boolean(exporting)}>
              导出 <DownOutlined />
            </Button>
          </Dropdown>
        </div>

        <div className="extra-history-toolbar">
          <Select
            aria-label="选择学年"
            value={selectedAcademicYear || undefined}
            options={academicYearOptions}
            placeholder="选择学年"
            onChange={(value) => {
              setSelectedAcademicYear(value || '')
              setPagination((previous) => ({ ...previous, current: 1 }))
            }}
          />
          <Select
            aria-label="选择附加分分类"
            value={selectedScoreType}
            options={scoreTypeOptions}
            onChange={(value) => {
              setSelectedScoreType(value)
              setPagination((previous) => ({ ...previous, current: 1 }))
            }}
          />
          <Select
            aria-label="选择申报状态"
            value={selectedStatusGroup}
            options={statusGroupOptions}
            onChange={(value) => {
              setSelectedStatusGroup(value)
              setPagination((previous) => ({ ...previous, current: 1 }))
            }}
          />
          <Input
            aria-label="搜索申报项目或证书"
            value={keywordInput}
            prefix={<SearchOutlined />}
            allowClear
            placeholder="搜索项目或证书"
            onChange={(event) => setKeywordInput(event.target.value)}
          />
        </div>

        <section className="extra-history-list-panel">
          {loading ? (
            <div className="extra-history-loading">
              <Skeleton active paragraph={{ rows: 6 }} />
            </div>
          ) : records.length ? (
            <>
              {actionableRecords.length ? (
                <div className="extra-history-section">
                  <div className="extra-history-section-title">
                    <h2>需要你处理</h2>
                    <span>{counts.actionRequired || actionableRecords.length}</span>
                  </div>
                  <div className="extra-history-record-list">
                    {actionableRecords.map(renderRecord)}
                  </div>
                </div>
              ) : null}

              {otherRecords.length ? (
                <div className="extra-history-section">
                  <div className="extra-history-section-title">
                    <h2>{actionableRecords.length ? '其他记录' : '历史记录'}</h2>
                  </div>
                  <div className="extra-history-record-list">{otherRecords.map(renderRecord)}</div>
                </div>
              ) : null}

              {pagination.total > pagination.pageSize ? (
                <div className="extra-history-pagination">
                  <Text type="secondary">共 {pagination.total} 条</Text>
                  <Pagination
                    current={pagination.current}
                    pageSize={pagination.pageSize}
                    total={pagination.total}
                    showSizeChanger={false}
                    onChange={(nextPage) =>
                      setPagination((previous) => ({ ...previous, current: nextPage }))
                    }
                  />
                </div>
              ) : null}
            </>
          ) : (
            <div className="extra-history-empty">
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无符合条件的申报记录" />
            </div>
          )}
        </section>
      </div>

      <Drawer
        title="申报详情"
        width={560}
        open={detailOpen}
        destroyOnHidden
        onClose={() => {
          setDetailOpen(false)
          setDetailRecord(null)
        }}
      >
        {detailLoading ? (
          <Skeleton active paragraph={{ rows: 8 }} />
        ) : detailRecord ? (
          <div className="extra-history-detail">
            <div className="extra-history-detail-summary">
              <div>
                <Tag color={statusColorMap[detailRecord.status] || 'default'}>
                  {detailRecord.displayStatus || detailRecord.statusLabel || '未知状态'}
                </Tag>
                <strong>{detailRecord.certName || detailRecord.itemName || '附加分申请'}</strong>
              </div>
              <span>{formatScore(detailRecord.expectedScore, detailRecord.scoreDirection)}</span>
            </div>

            {detailRecord.returnReason ? (
              <div className="extra-history-detail-return">{detailRecord.returnReason}</div>
            ) : null}

            <section>
              <h3>申报信息</h3>
              <Descriptions column={1} size="small" items={detailDescriptionItems} />
            </section>

            <section>
              <h3>审核记录</h3>
              {timelineItems.length ? (
                <Timeline items={timelineItems} />
              ) : (
                <Text type="secondary">暂无审核时间线</Text>
              )}
            </section>

            <section>
              <h3>证明材料</h3>
              {detailMedia.length ? (
                <div className="extra-history-media-list">
                  <Image.PreviewGroup>
                    {detailMedia.map((item, index) => {
                      const url = getFileUrl(item.url)
                      if (item.type !== 'image' && !isImageUrl(url)) {
                        return (
                          <a key={`${url}-${index}`} href={url} target="_blank" rel="noreferrer">
                            {item.name}
                          </a>
                        )
                      }
                      return (
                        <Image
                          key={`${url}-${index}`}
                          width={72}
                          height={72}
                          src={withAuthToken(url)}
                          alt={item.name}
                          preview={{ mask: '查看' }}
                        />
                      )
                    })}
                  </Image.PreviewGroup>
                </div>
              ) : (
                <Text type="secondary">暂无证明材料</Text>
              )}
            </section>
          </div>
        ) : null}
      </Drawer>
    </StudentLayout>
  )
}

export default ExtraScoreHistory
