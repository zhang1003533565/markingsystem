import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import {
  Button,
  Card,
  Checkbox,
  DatePicker,
  Input,
  Modal,
  Progress,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from 'antd'
import dayjs from 'dayjs'
import { getActiveAcademicYearList } from '../../api/admin/academicYear'
import { getClassOptions } from '../../api/admin/class'
import {
  closeExtraScoreSubmissionWindow,
  completeExtraScoreSubmissionWindow,
  getExtraScoreSubmissionWindowList,
  saveExtraScoreSubmissionWindow,
} from '../../api/admin/extraScoreSubmissionWindow'
import { getStoredUser } from '../../utils/authStorage'
import { confirmAction } from '../../components/confirmDialogService'
import './ExtraScoreSubmissionWindowManagement.css'

const { Text, Title } = Typography

function formatDateTime(value) {
  if (!value) {
    return '-'
  }
  return String(value).replace('T', ' ').slice(0, 16)
}

const stageColorMap = {
  scheduled: 'default',
  collecting: 'green',
  committee_review: 'gold',
  teacher_review: 'blue',
  ready_to_complete: 'purple',
  completed: 'success',
  closed: 'default',
}

function ExtraScoreSubmissionWindowManagement() {
  const [searchParams] = useSearchParams()
  const searchParamKey = searchParams.toString()
  const presetClassName = useMemo(() => {
    const params = new URLSearchParams(searchParamKey)
    return String(params.get('presetClassName') || params.get('className') || '').trim()
  }, [searchParamKey])
  const user = getStoredUser()
  const isTeacher = user?.role === 'TEACHER'
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [academicYears, setAcademicYears] = useState([])
  const [selectedAcademicYear, setSelectedAcademicYear] = useState('')
  const [records, setRecords] = useState([])
  const [startTime, setStartTime] = useState('')
  const [endTime, setEndTime] = useState('')
  const [availableClassOptions, setAvailableClassOptions] = useState([])
  const [availableClasses, setAvailableClasses] = useState([])
  const [selectedClasses, setSelectedClasses] = useState([])
  const [recordClassFilter, setRecordClassFilter] = useState(presetClassName || 'all')
  const [closingId, setClosingId] = useState(null)
  const [completingId, setCompletingId] = useState(null)

  const loadRecords = async (academicYear) => {
    if (!academicYear) {
      setRecords([])
      return
    }
    const response = await getExtraScoreSubmissionWindowList({ academicYear }).catch(() => ({
      data: [],
    }))
    setRecords(Array.isArray(response?.data) ? response.data : [])
  }

  useEffect(() => {
    const init = async () => {
      setLoading(true)
      try {
        const yearRes = await getActiveAcademicYearList().catch(() => [])
        const classRes = await getClassOptions().catch(() => [])
        const yearList = Array.isArray(yearRes)
          ? yearRes
          : Array.isArray(yearRes?.data)
            ? yearRes.data
            : []
        const classList = Array.isArray(classRes)
          ? classRes
          : Array.isArray(classRes?.data)
            ? classRes.data
            : []
        const currentYear = yearList.find((item) => item.current)?.name || yearList[0]?.name || ''
        setAcademicYears(yearList)
        const classOptions = classList
          .map((item) => item?.name)
          .filter(Boolean)
          .sort((a, b) => String(a).localeCompare(String(b), 'zh-CN'))
        setAvailableClasses(classOptions)
        setAvailableClassOptions(
          classList
            .map((item) => ({
              name: item?.name,
              grade: item?.grade || '未分级',
            }))
            .filter((item) => item.name)
            .sort((left, right) => {
              const gradeCompare = String(right.grade || '').localeCompare(
                String(left.grade || ''),
                'zh-CN',
                { numeric: true },
              )
              return gradeCompare || String(left.name).localeCompare(String(right.name), 'zh-CN')
            }),
        )
        setSelectedAcademicYear(currentYear)
        await loadRecords(currentYear)
      } finally {
        setLoading(false)
      }
    }
    init()
  }, [])

  useEffect(() => {
    if (!presetClassName || !availableClasses.includes(presetClassName)) {
      return
    }
    setSelectedClasses([presetClassName])
    setRecordClassFilter(presetClassName)
  }, [availableClasses, presetClassName])

  const classGroupsByGrade = useMemo(() => {
    const grouped = new Map()
    availableClassOptions.forEach((item) => {
      const grade = item.grade || '未分级'
      const classes = grouped.get(grade) || []
      classes.push(item)
      grouped.set(grade, classes)
    })
    return Array.from(grouped.entries()).map(([grade, classes]) => ({ grade, classes }))
  }, [availableClassOptions])

  const recordClassFilterOptions = useMemo(
    () => [
      { label: '全部班级周期', value: 'all' },
      ...classGroupsByGrade.map((group) => ({
        label: group.grade === '未分级' ? group.grade : `${group.grade}级`,
        options: group.classes.map((item) => ({ label: item.name, value: item.name })),
      })),
    ],
    [classGroupsByGrade],
  )

  const filteredRecords = useMemo(() => {
    if (recordClassFilter === 'all') {
      return records
    }
    return records.filter((record) => (record.classNames || []).includes(recordClassFilter))
  }, [recordClassFilter, records])

  const toggleSelectedClass = (className, checked) => {
    setSelectedClasses((prev) => {
      if (checked) {
        return prev.includes(className) ? prev : [...prev, className]
      }
      return prev.filter((item) => item !== className)
    })
  }

  const allClassesChecked =
    availableClasses.length > 0 && selectedClasses.length === availableClasses.length
  const periodLabelByKey = useMemo(() => {
    const grouped = new Map()
    records
      .slice()
      .sort((left, right) =>
        String(left.startTime || left.createdAt || '').localeCompare(
          String(right.startTime || right.createdAt || ''),
        ),
      )
      .forEach((record) => {
        const year = record.academicYear || selectedAcademicYear || 'unknown'
        const list = grouped.get(year) || []
        list.push(record)
        grouped.set(year, list)
      })

    const labels = new Map()
    grouped.forEach((list) => {
      list.forEach((record, index) => {
        const key = record.batchCode || record.id
        labels.set(key, index === 0 ? '年度主周期' : `补充周期 ${index}`)
      })
    })
    return labels
  }, [records, selectedAcademicYear])

  const columns = [
    {
      title: '周期类型',
      key: 'periodType',
      render: (_, record) => (
        <Tag
          color={
            (
              record.periodLabel ||
              periodLabelByKey.get(record.batchCode || record.id) ||
              ''
            ).startsWith('补充')
              ? 'orange'
              : 'blue'
          }
        >
          {record.periodLabel || periodLabelByKey.get(record.batchCode || record.id) || '提交周期'}
        </Tag>
      ),
    },
    {
      title: '学年',
      dataIndex: 'academicYear',
      key: 'academicYear',
    },
    {
      title: '提交时间',
      key: 'timeRange',
      render: (_, record) =>
        `${formatDateTime(record.startTime)} 至 ${formatDateTime(record.endTime)}`,
    },
    {
      title: '批次班级范围',
      key: 'classNames',
      render: (_, record) => (
        <Space wrap>
          {(record.classNames || []).map((item) => (
            <Tag key={item}>{item}</Tag>
          ))}
        </Space>
      ),
    },
    {
      title: '班级数',
      dataIndex: 'classCount',
      key: 'classCount',
      render: (value) => <Tag color="blue">{value || 0} 个班</Tag>,
    },
    {
      title: '审核阶段',
      key: 'currentStage',
      render: (_, record) => (
        <Tag color={stageColorMap[record.periodStatus || record.currentStage] || 'default'}>
          {record.periodStatusLabel || record.currentStageLabel || '提交周期'}
        </Tag>
      ),
    },
    {
      title: '审核进度',
      key: 'progress',
      width: 220,
      render: (_, record) => (
        <Space orientation="vertical" size={4} style={{ width: '100%' }}>
          <Progress percent={Number(record.completionRate || 0)} size="small" />
          <Text type="secondary" style={{ fontSize: 12 }}>
            已完成 {record.completedCount || 0} / 总申请 {record.totalApplications || 0}
            <br />
            周期人数：{record.studentCount || 0} 人
          </Text>
        </Space>
      ),
    },
    {
      title: '待处理',
      key: 'pending',
      render: (_, record) => (
        <Space direction="vertical" size={2}>
          <Text style={{ fontSize: 13 }}>班委待审：{record.committeePendingCount || 0}</Text>
          <Text style={{ fontSize: 13 }}>老师待审：{record.teacherPendingCount || 0}</Text>
          <Text type="secondary" style={{ fontSize: 12 }}>
            未完成：{record.pendingCount || 0}
          </Text>
        </Space>
      ),
    },
    {
      title: '运行状态',
      dataIndex: 'periodStatus',
      key: 'periodStatus',
      render: (_, record) => (
        <Tag color={stageColorMap[record.periodStatus] || 'default'}>
          {record.periodStatusLabel || (record.enabled ? '运行中' : '已关闭')}
        </Tag>
      ),
    },
    {
      title: '操作',
      key: 'actions',
      fixed: 'right',
      render: (_, record) => {
        if (record.canComplete) {
          return (
            <Button
              type="primary"
              size="small"
              loading={completingId === record.id}
              onClick={() => handleComplete(record)}
            >
              完成审核
            </Button>
          )
        }
        if (record.canClose) {
          return (
            <Button
              danger
              size="small"
              loading={closingId === record.id}
              onClick={() => handleClose(record)}
            >
              提前结束
            </Button>
          )
        }
        return (
          <Button size="small" onClick={() => handleViewPeriod(record)}>
            查看详情
          </Button>
        )
      },
    },
  ]

  function explainCannotComplete(record) {
    if (!record.canComplete) {
      return record.primaryActionReason || '当前周期暂不能完成审核。'
    }
    return ''
  }

  function explainCannotClose(record) {
    if (!record.canClose) {
      return record.primaryActionReason || '当前周期暂不能提前结束。'
    }
    return ''
  }

  function handleViewPeriod(record) {
    Modal.info({
      title: `${record.periodLabel || '提交批次'} · ${record.periodStatusLabel || '周期详情'}`,
      content: (
        <Space orientation="vertical" size={8}>
          <Text>学年：{record.academicYear || '-'}</Text>
          <Text>
            提交时间：{formatDateTime(record.startTime)} 至 {formatDateTime(record.endTime)}
          </Text>
          <Text>本批次班级：{(record.classNames || []).join('、') || '-'}</Text>
          <Text>周期人数：{record.studentCount || 0} 人</Text>
          <Text>
            审核进度：已完成 {record.completedCount || 0} / 总申请 {record.totalApplications || 0}
          </Text>
          <Text>
            待处理：班委 {record.committeePendingCount || 0} 条，老师{' '}
            {record.teacherPendingCount || 0} 条
          </Text>
          {record.primaryActionReason ? (
            <Text type="secondary">{record.primaryActionReason}</Text>
          ) : null}
        </Space>
      ),
      okText: '知道了',
    })
  }

  function renderPendingSettlementHint(record) {
    if (!record.pendingCount) {
      return <Text type="secondary">当前批次没有未完成审核学生，完成后会关闭本批次所有班级周期。</Text>
    }
    const details = [
      record.committeePendingCount ? `班委待审 ${record.committeePendingCount} 条` : '',
      record.teacherPendingCount ? `老师待审 ${record.teacherPendingCount} 条` : '',
    ]
      .filter(Boolean)
      .join('，')
    return (
      <Text type="secondary">
        当前批次还有 {record.pendingCount} 条未完成审核申请{details ? `（${details}）` : ''}
        ；系统会按学生整包处理，凡个人审核未完成，该学生本周期所有申请都会退回草稿并移出本周期。
      </Text>
    )
  }

  async function handleComplete(record) {
    const reason = explainCannotComplete(record)
    if (reason) {
      message.info(reason)
      return
    }
    const confirmed = await confirmAction({
      title: '完成本批次审核？',
      message: (
        <Space orientation="vertical" size={8}>
          <Text>完成后会关闭本批次所有班级周期，并把已审核通过的附加分写入综测快照。</Text>
          <Text type="secondary">本批次班级：{(record.classNames || []).join('、') || '-'}</Text>
          {renderPendingSettlementHint(record)}
        </Space>
      ),
      confirmText: '完成本批次并更新综测',
      tone: 'primary',
    })
    if (!confirmed) return

    setCompletingId(record.id)
    try {
      const response = await completeExtraScoreSubmissionWindow(record.id)
      message.success(response?.data?.message || '审核已完成，综测快照已更新')
      await loadRecords(selectedAcademicYear)
    } catch (error) {
      message.error(error?.response?.data?.message || '完成审核失败')
    } finally {
      setCompletingId(null)
    }
  }

  async function handleClose(record) {
    const reason = explainCannotClose(record)
    if (reason) {
      message.info(reason)
      return
    }
    const confirmed = await confirmAction({
      title: '提前结束提交批次？',
      message: (
        <Space orientation="vertical" size={8}>
          <Text>结束后，本批次所有班级的学生不能继续在该周期提交附加分申请。</Text>
          <Text type="secondary">本批次班级：{(record.classNames || []).join('、') || '-'}</Text>
          {renderPendingSettlementHint(record)}
          <Text type="secondary">退回草稿的申请会在下一个开放周期自动归入。</Text>
        </Space>
      ),
      confirmText: '提前结束本批次',
      tone: 'danger',
    })
    if (!confirmed) return

    setClosingId(record.id)
    try {
      const response = await closeExtraScoreSubmissionWindow(record.id)
      message.success(response?.data?.message || '提交周期已结束')
      await loadRecords(selectedAcademicYear)
    } catch (error) {
      message.error(error?.response?.data?.message || '结束提交周期失败')
    } finally {
      setClosingId(null)
    }
  }

  const handleCreate = async () => {
    if (!selectedAcademicYear) {
      message.error('请选择学年')
      return
    }
    if (!startTime || !endTime) {
      message.error('请填写开始和截止时间')
      return
    }
    if (!selectedClasses.length) {
      message.error('请至少选择一个班级')
      return
    }
    setSaving(true)
    try {
      await saveExtraScoreSubmissionWindow({
        academicYear: selectedAcademicYear,
        classNames: selectedClasses,
        enabled: true,
        startTime: `${startTime}:00`,
        endTime: `${endTime}:00`,
      })
      await loadRecords(selectedAcademicYear)
      setSelectedClasses([])
      setStartTime('')
      setEndTime('')
      message.success('提交周期已创建')
    } catch (error) {
      message.error(error?.response?.data?.message || '创建提交周期失败')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="extra-score-submission-window-page" style={{ display: 'grid', gap: 24 }}>
      <Card variant="borderless" style={{ borderRadius: 24 }}>
        <Space orientation="vertical" size={10} style={{ width: '100%' }}>
          <Title level={2} style={{ margin: 0 }}>
            附加分提交周期
          </Title>
          <Text type="secondary">
            {isTeacher
              ? '每个学年可创建年度主周期，也可按需追加补充周期。先选择学年和提交起止时间，再勾选要应用的班级。'
              : '管理员仅查看各学年提交周期、审核阶段和处理进度，不参与周期运行操作。'}
          </Text>
        </Space>
      </Card>

      <Card variant="borderless" style={{ borderRadius: 24 }}>
        <Space orientation="vertical" size={20} style={{ width: '100%' }}>
          <Space wrap size={16}>
            <div>
              <Text strong>学年</Text>
              <Select
                value={selectedAcademicYear || undefined}
                style={{ width: 220, display: 'block', marginTop: 8 }}
                placeholder="请选择学年"
                options={academicYears.map((item) => ({ value: item.name, label: item.name }))}
                onChange={async (value) => {
                  setSelectedAcademicYear(value)
                  setLoading(true)
                  try {
                    await loadRecords(value)
                    setSelectedClasses([])
                  } finally {
                    setLoading(false)
                  }
                }}
              />
            </div>
            {isTeacher ? (
              <>
                <div>
                  <Text strong>开始时间</Text>
                  <DatePicker
                    showTime={{ format: 'HH:mm' }}
                    format="YYYY-MM-DD HH:mm"
                    value={startTime ? dayjs(startTime) : null}
                    onChange={(value) =>
                      setStartTime(value ? value.format('YYYY-MM-DDTHH:mm') : '')
                    }
                    placeholder="选择开始时间"
                    style={{ width: 240, display: 'block', marginTop: 8 }}
                  />
                </div>
                <div>
                  <Text strong>截止时间</Text>
                  <DatePicker
                    showTime={{ format: 'HH:mm' }}
                    format="YYYY-MM-DD HH:mm"
                    value={endTime ? dayjs(endTime) : null}
                    onChange={(value) => setEndTime(value ? value.format('YYYY-MM-DDTHH:mm') : '')}
                    placeholder="选择截止时间"
                    style={{ width: 240, display: 'block', marginTop: 8 }}
                  />
                </div>
              </>
            ) : null}
          </Space>

          {isTeacher ? (
            <div>
              <Space style={{ width: '100%', justifyContent: 'space-between', marginBottom: 12 }}>
                <Text strong>选择班级</Text>
                <Space>
                  <Button
                    size="small"
                    onClick={() => {
                      if (!availableClasses.length) {
                        message.info('当前没有可选择的班级。')
                        return
                      }
                      setSelectedClasses(availableClasses)
                    }}
                  >
                    全选
                  </Button>
                  <Button
                    size="small"
                    onClick={() => {
                      if (!selectedClasses.length) {
                        message.info('当前还没有选择班级。')
                        return
                      }
                      setSelectedClasses([])
                    }}
                  >
                    清空
                  </Button>
                </Space>
              </Space>
              <Checkbox
                checked={allClassesChecked}
                indeterminate={
                  selectedClasses.length > 0 && selectedClasses.length < availableClasses.length
                }
                onChange={(event) =>
                  setSelectedClasses(event.target.checked ? availableClasses : [])
                }
              >
                全部班级
              </Checkbox>
              <div style={{ display: 'grid', gap: 12, marginTop: 12 }}>
                {classGroupsByGrade.map((group) => (
                  <div
                    key={group.grade}
                    style={{
                      border: '1px solid #eef2f7',
                      borderRadius: 12,
                      padding: '10px 12px',
                    }}
                  >
                    <Text type="secondary" style={{ display: 'block', marginBottom: 8 }}>
                      {group.grade === '未分级' ? group.grade : `${group.grade}级`}
                    </Text>
                    <Space wrap size={[16, 8]}>
                      {group.classes.map((item) => (
                        <Checkbox
                          key={item.name}
                          checked={selectedClasses.includes(item.name)}
                          onChange={(event) => toggleSelectedClass(item.name, event.target.checked)}
                        >
                          {item.name}
                        </Checkbox>
                      ))}
                    </Space>
                  </div>
                ))}
              </div>
            </div>
          ) : null}

          {isTeacher ? (
            <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
              <Button type="primary" loading={saving} onClick={handleCreate}>
                创建提交周期
              </Button>
            </div>
          ) : null}
        </Space>
      </Card>

      <Card
        title="年度主周期 / 补充周期记录"
        variant="borderless"
        style={{ borderRadius: 24 }}
        extra={
          <Space wrap>
            <Text type="secondary">查看班级周期</Text>
            <Select
              value={recordClassFilter}
              style={{ width: 220 }}
              options={recordClassFilterOptions}
              onChange={(value) => setRecordClassFilter(value)}
            />
          </Space>
        }
      >
        <Table
          rowKey={(record) => record.batchCode || record.id}
          loading={loading}
          columns={columns}
          dataSource={filteredRecords}
          pagination={false}
          scroll={{ x: 1100 }}
        />
      </Card>
    </div>
  )
}

export default ExtraScoreSubmissionWindowManagement
