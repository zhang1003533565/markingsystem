import { useCallback, useEffect, useMemo, useState } from 'react';
import { Button, Card, Empty, Space, Steps, Tag, Typography, message } from 'antd';
import StudentLayout from '../../components/StudentLayout';
import {
  getExtraScoreApplicationProcess,
  getCurrentExtraScoreApplicationProcess,
  submitCurrentExtraScoreApplications,
  withdrawCurrentExtraScoreApplications
} from '../../api/student/extraScore';
import './ExtraScoreProcess.css';

const { Text, Title } = Typography;

const statusTextMap = {
  draft: '草稿',
  pending: '进行中',
  approved: '已通过',
  returned: '已驳回',
  rejected: '已驳回'
};

const stageTextMap = {
  draft: '草稿',
  committee_pending: '待班委初审',
  teacher_pending: '待老师审核',
  approved: '审核通过',
  returned: '已驳回',
  rejected: '已驳回'
};

const formatDateTime = (value) => (value ? String(value).replace('T', ' ').slice(0, 16) : '-');
const resolveStatusLabel = (record) => record?.statusLabel || statusTextMap[record?.status] || record?.status || '-';
const resolveStageLabel = (record) => {
  if (record?.withdrawalPending) {
    return '等待班委同意撤回';
  }
  if (record?.status === 'approved' && !record?.scoreSnapshotUpdated) {
    return '等待更新综测';
  }
  return record?.reviewStageLabel || stageTextMap[record?.reviewStage] || record?.reviewStage || '-';
};
const resolveStatusColor = (status) => {
  switch (status) {
    case 'approved':
      return 'success';
    case 'pending':
      return 'processing';
    case 'draft':
      return 'default';
    case 'returned':
    case 'rejected':
      return 'error';
    default:
      return 'default';
  }
};

const getCount = (record, key) => Number(record?.[key] || 0);

const isTeacherImportRecord = (record) => {
  const periodKey = String(record?.periodKey || '').toLowerCase();
  const periodLabel = String(record?.submissionPeriodLabel || '').trim();
  return periodKey.startsWith('teacher-import') || periodLabel === '教师导入';
};

const buildCurrentProcessPeriodKey = (record) => (
  record?.submissionBatchCode
    || [
      record?.submissionPeriodLabel || '',
      record?.submissionStartTime || '',
      record?.submissionEndTime || ''
    ].join('|')
);

const isSamePeriod = (left, right) => {
  if (!left || !right) {
    return false;
  }
  const rightKey = right.periodKey || buildCurrentProcessPeriodKey(right);
  if (left.periodKey && rightKey && left.periodKey === rightKey) {
    return true;
  }
  return String(left.submissionPeriodLabel || '') === String(right.submissionPeriodLabel || '')
    && String(left.submissionStartTime || '') === String(right.submissionStartTime || '')
    && String(left.submissionEndTime || '') === String(right.submissionEndTime || '');
};

const normalizeCurrentProcess = (record) => record ? ({
  ...record,
  periodKey: buildCurrentProcessPeriodKey(record),
  totalCount: record.totalCount ?? record.total ?? 0,
  draftCount: record.draftCount ?? (record.status === 'draft' ? record.total || 0 : 0),
  pendingCount: record.pendingCount ?? (record.status === 'pending' ? record.total || 0 : 0),
  approvedCount: record.approvedCount ?? (record.status === 'approved' ? record.total || 0 : 0),
  committeePendingCount: record.committeePendingCount ?? (record.reviewStage === 'committee_pending' ? record.total || 0 : 0),
  teacherPendingCount: record.teacherPendingCount ?? (record.reviewStage === 'teacher_pending' ? record.total || 0 : 0),
  withdrawalPendingCount: record.withdrawalPendingCount ?? 0,
  canSubmit: Boolean(record.canSubmit),
  canWithdraw: Boolean(record.canWithdraw)
}) : null;

function buildTimeline(record) {
  const isDraft = record?.status === 'draft' || record?.reviewStage === 'draft';
  const isWithdrawalPending = Boolean(record?.withdrawalPending);
  const totalCount = Number(record?.totalCount || 0);
  const submittedCount = totalCount - Number(record?.draftCount || 0);
  const scoreSnapshotUpdated = Boolean(record?.scoreSnapshotUpdated || record?.periodSettled);
  return [
    {
      title: '提交申请',
      status: isDraft ? 'process' : 'finish',
      description: isDraft
        ? `本周期还有 ${record?.draftCount || 0} 条待提交`
        : `本周期已提交 ${submittedCount} 条`
    },
    {
      title: '班委初审',
      status: isWithdrawalPending
        ? 'process'
        : Number(record?.committeePendingCount || 0) > 0
        ? 'process'
        : record?.reviewStage === 'teacher_pending' || record?.status === 'approved'
          ? 'finish'
          : record?.status === 'returned' || record?.status === 'rejected'
            ? 'error'
            : 'wait',
      description: isWithdrawalPending
        ? '学生已提交撤回申请，等待班委确认'
        : Number(record?.committeePendingCount || 0) > 0
        ? `等待班委处理 ${record.committeePendingCount} 条`
        : (isDraft ? '统一提交后进入班委初审' : '已进入下一阶段')
    },
    {
      title: '老师终审',
      status: isWithdrawalPending
        ? 'wait'
        : Number(record?.teacherPendingCount || 0) > 0
        ? 'process'
        : record?.status === 'approved'
          ? 'finish'
          : record?.status === 'returned' || record?.status === 'rejected'
            ? 'error'
            : 'wait',
      description: isWithdrawalPending
        ? '撤回申请处理后再继续'
        : Number(record?.teacherPendingCount || 0) > 0
        ? `等待老师处理 ${record.teacherPendingCount} 条`
        : (record?.status === 'approved' ? '老师终审完成' : '待班委通过后进入')
    },
    {
      title: '最终结果',
      status: scoreSnapshotUpdated
        ? 'finish'
        : record?.status === 'returned' || record?.status === 'rejected'
          ? 'error'
          : record?.status === 'approved'
            ? 'process'
            : 'wait',
      description: scoreSnapshotUpdated
        ? '已写入综测成绩'
        : record?.status === 'approved'
          ? '审核已通过，等待老师更新综测成绩'
        : `待完成 ${Number(record?.totalCount || 0) - Number(record?.approvedCount || 0)} 条`
    }
  ];
}

function ExtraScoreProcess() {
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [records, setRecords] = useState([]);
  const [currentProcess, setCurrentProcess] = useState(null);

  const fetchData = useCallback(async () => {
    setLoading(true);
    try {
      const [processRes, currentRes] = await Promise.all([
        getExtraScoreApplicationProcess(),
        getCurrentExtraScoreApplicationProcess().catch(() => ({ data: null }))
      ]);
      setRecords(Array.isArray(processRes?.data) ? processRes.data : []);
      setCurrentProcess(currentRes?.data?.currentProcess || null);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  const teacherImportRecords = useMemo(
    () => records.filter(isTeacherImportRecord),
    [records]
  );
  const periodRecords = useMemo(
    () => records.filter((item) => !isTeacherImportRecord(item)),
    [records]
  );
  const currentPeriodRecord = useMemo(() => {
    const normalized = normalizeCurrentProcess(currentProcess);
    if (!normalized) {
      return null;
    }
    return periodRecords.find((item) => isSamePeriod(item, normalized)) || normalized;
  }, [currentProcess, periodRecords]);
  const timelineSteps = useMemo(
    () => currentPeriodRecord ? buildTimeline(currentPeriodRecord) : [],
    [currentPeriodRecord]
  );
  const historyPeriodRecords = useMemo(
    () => periodRecords.filter((item) => !isSamePeriod(item, currentPeriodRecord)),
    [currentPeriodRecord, periodRecords]
  );
  const draftRecords = useMemo(
    () => currentPeriodRecord?.canSubmit ? [currentPeriodRecord] : [],
    [currentPeriodRecord]
  );
  const withdrawableRecords = useMemo(
    () => currentPeriodRecord?.canWithdraw ? [currentPeriodRecord] : [],
    [currentPeriodRecord]
  );
  const withdrawalPendingRecords = useMemo(
    () => currentPeriodRecord?.withdrawalPending ? [currentPeriodRecord] : [],
    [currentPeriodRecord]
  );
  const draftCount = getCount(currentPeriodRecord, 'draftCount');
  const reviewingCount = getCount(currentPeriodRecord, 'pendingCount');
  const periodRangeText = currentPeriodRecord?.submissionStartTime || currentPeriodRecord?.submissionEndTime
    ? `${formatDateTime(currentPeriodRecord.submissionStartTime)} 至 ${formatDateTime(currentPeriodRecord.submissionEndTime)}`
    : '暂无周期时间';
  const flowActionLabel = draftRecords.length
    ? '确认提交审核'
    : withdrawalPendingRecords.length
      ? '等待班委同意撤回'
      : '申请撤回';
  const flowActionDisabled = !draftRecords.length
    && (!withdrawableRecords.length || Boolean(withdrawalPendingRecords.length));

  const handleFlowAction = async () => {
    if (draftRecords.length) {
      setSubmitting(true);
      try {
        const response = await submitCurrentExtraScoreApplications();
        const count = response?.data?.submittedCount || 0;
        message.success(`已提交 ${count} 条申请进入班委初审`);
        await fetchData();
      } catch (error) {
        message.error(error?.response?.data?.message || '提交审核失败');
      } finally {
        setSubmitting(false);
      }
      return;
    }

    if (withdrawableRecords.length) {
      setSubmitting(true);
      try {
        const response = await withdrawCurrentExtraScoreApplications();
        const data = response?.data || {};
        const count = data.withdrawnCount || data.requestedCount || 0;
        message.success(data.message || `已处理 ${count} 条撤回申请`);
        await fetchData();
      } catch (error) {
        message.error(error?.response?.data?.message || '撤回失败');
      } finally {
        setSubmitting(false);
      }
      return;
    }

    if (withdrawalPendingRecords.length) {
      message.info('撤回申请已提交，正在等待班委同意');
      return;
    }

    message.info('当前没有草稿或可撤回的已提交申请');
  };

  const renderCountLine = (record) => (
    <>
      共 {getCount(record, 'totalCount')} 条，待提交 {getCount(record, 'draftCount')} 条，班委初审 {getCount(record, 'committeePendingCount')} 条，
      老师终审 {getCount(record, 'teacherPendingCount')} 条，已通过 {getCount(record, 'approvedCount')} 条。
      {record?.withdrawalPending ? ` 撤回待班委处理 ${getCount(record, 'withdrawalPendingCount')} 条。` : ''}
    </>
  );

  const renderPeriodSummary = (record) => (
    <div key={record.periodKey} className="extra-process-history-row">
      <div>
        <Text strong>{record.submissionPeriodLabel || '提交周期'}</Text>
        <div className="extra-process-muted">
          {formatDateTime(record.submissionStartTime)} 至 {formatDateTime(record.submissionEndTime)}
        </div>
      </div>
      <div className="extra-process-history-count">{renderCountLine(record)}</div>
      <Space>
        <Tag color={resolveStatusColor(record.status)}>{resolveStatusLabel(record)}</Tag>
        <Tag>已结束</Tag>
      </Space>
    </div>
  );

  const renderTeacherImportSummary = (record) => (
    <div key={record.periodKey} className="extra-process-history-row extra-process-import-row">
      <div>
        <Text strong>教师导入加分</Text>
        <div className="extra-process-muted">不计入提交周期流程</div>
      </div>
      <div className="extra-process-history-count">{renderCountLine(record)}</div>
      <Tag color="green">{resolveStatusLabel(record)}</Tag>
    </div>
  );

  return (
    <StudentLayout activePath="/student/extra/process" breadcrumb="附加分 / 审核流程">
      <div className="student-page-wrapper">
        <Card title="附加分审核流程" variant="borderless" loading={loading} className="extra-process-shell">
          <div className="extra-process-hero">
            <div>
              <Text className="extra-process-eyebrow">当前周期</Text>
              <Title level={3} className="extra-process-title">
                {currentPeriodRecord?.submissionPeriodLabel || '暂无进行中的周期'}
              </Title>
              <Text type="secondary">
                {currentPeriodRecord
                  ? `${periodRangeText}，当前阶段：${resolveStageLabel(currentPeriodRecord)}。`
                  : '当前没有开放或审核中的附加分周期，已结束的周期在下方查看。'}
              </Text>
            </div>
            <Tag color={currentPeriodRecord ? 'processing' : 'default'} className="extra-process-main-tag">
              {currentPeriodRecord ? '进行中' : '暂无周期'}
            </Tag>
          </div>

          {currentPeriodRecord ? (
            <Card size="small" className="extra-process-current-card">
              <div className="extra-process-current-head">
                <div>
                  <Text strong>本周期流程</Text>
                  <div className="extra-process-muted">
                    待提交 {draftCount} 条，审核中 {reviewingCount} 条。
                    {withdrawalPendingRecords.length ? ` 等待班委同意撤回 ${getCount(currentPeriodRecord, 'withdrawalPendingCount')} 条。` : ''}
                  </div>
                </div>
                <Space>
                  <Tag color={resolveStatusColor(currentPeriodRecord.status)}>{resolveStatusLabel(currentPeriodRecord)}</Tag>
                  {currentPeriodRecord.withdrawalPending ? <Tag color="orange">等待撤回确认</Tag> : null}
                </Space>
              </div>

              <div className="extra-process-action">
                <Text type="secondary">
                  {draftCount
                    ? '确认后，本周期草稿和补充材料会一起进入班委初审。'
                    : withdrawalPendingRecords.length
                      ? '撤回申请已提交，等待班委同意后会退回草稿。'
                      : '班委尚未审核任何记录时可直接撤回；已有班委审核结果后，撤回需要班委同意。'}
                </Text>
                <Button type="primary" loading={submitting} disabled={flowActionDisabled} onClick={handleFlowAction}>
                  {flowActionLabel}
                </Button>
              </div>

              <Steps
                className="extra-process-steps"
                current={-1}
                size="small"
                items={timelineSteps.map((step) => ({
                  title: step.title,
                  status: step.status,
                  content: step.description,
                  styles: step.status === 'finish' ? {
                    icon: {
                      borderColor: '#2563eb',
                      background: '#2563eb',
                      color: '#fff'
                    },
                    rail: {
                      borderColor: '#2563eb'
                    }
                  } : undefined
                }))}
              />
            </Card>
          ) : (
            <div className="extra-process-empty">
              <Empty description="暂无进行中的审核周期" />
            </div>
          )}

          <div className="extra-process-section">
            <div className="extra-process-section-head">
              <Title level={4}>已结束周期</Title>
              <Text type="secondary">只展示周期结果摘要，避免和当前流程混在一起。</Text>
            </div>
            {historyPeriodRecords.length ? (
              <div className="extra-process-history-list">
                {historyPeriodRecords.map(renderPeriodSummary)}
              </div>
            ) : (
              <div className="extra-process-empty compact">
                <Empty description="暂无已结束周期记录" />
              </div>
            )}
          </div>

          <div className="extra-process-section">
            <div className="extra-process-section-head">
              <Title level={4}>教师导入记录</Title>
              <Text type="secondary">教师导入直接生效，不进入学生提交周期流程。</Text>
            </div>
            {teacherImportRecords.length ? (
              <div className="extra-process-history-list">
                {teacherImportRecords.map(renderTeacherImportSummary)}
              </div>
            ) : (
              <div className="extra-process-empty compact">
                <Empty description="暂无教师导入记录" />
              </div>
            )}
          </div>
        </Card>
      </div>
    </StudentLayout>
  );
}

export default ExtraScoreProcess;
