import { useEffect, useRef, useState, useCallback, useMemo } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { Modal, Pagination } from 'antd';
import AnnouncementRichEditor from './AnnouncementRichEditor';
import AnnouncementArticle from './AnnouncementArticle';
import AnnouncementDetailDrawer from './AnnouncementDetailDrawer';
import {
  createAnnouncement,
  getAnnouncementDetail,
  getEmailTaskProgress,
  getAnnouncementRecipients,
  getMyAnnouncements,
  getPublishedAnnouncements,
  remindAnnouncementUnread
} from '../api/announcement';
import { getClassOptions } from '../api/admin/class';
import { getCurrentAdminUser } from '../api/admin/user';
import { getStoredUser } from '../utils/authStorage';
import '../pages/Admin/StudentManagement.css';
import '../pages/AnnouncementCenter.css';

function AnnouncementCenterPanel({ showDevelopmentBadge = false }) {
  const location = useLocation();
  const navigate = useNavigate();
  const user = getStoredUser();
  const isInboxUser = ['STUDENT', 'CLASS_COMMITTEE'].includes(user.role);
  const canPublish = ['ADMIN', 'TEACHER'].includes(user.role);
  const emailTaskStorageKey = `announcementEmailTasks:${user.id || 'guest'}`;

  const [activePublisherTab, setActivePublisherTab] = useState('publish');
  const [loading, setLoading] = useState(false);
  const [records, setRecords] = useState([]);
  const [listPagination, setListPagination] = useState({
    current: 1,
    pageSize: 10,
    total: 0
  });
  const [selectedAnnouncement, setSelectedAnnouncement] = useState(null);
  const [recipientData, setRecipientData] = useState([]);
  const [receiptTab, setReceiptTab] = useState('unread');
  const [form, setForm] = useState({
    title: '',
    content: ''
  });
  const [publishMode, setPublishMode] = useState('SITE');
  const [classOptions, setClassOptions] = useState([]);
  const [targetType, setTargetType] = useState('ALL');
  const [selectedGrades, setSelectedGrades] = useState([]);
  const [selectedClassNames, setSelectedClassNames] = useState([]);
  const [emailTasks, setEmailTasks] = useState(() => {
    try {
      const cachedTasks = JSON.parse(localStorage.getItem(emailTaskStorageKey) || '[]');
      return Array.isArray(cachedTasks)
        ? cachedTasks.filter((task) => task.kind === 'reminder' || task.label?.startsWith('催读邮件'))
        : [];
    } catch (error) {
      console.error('恢复邮件任务记录失败:', error);
      return [];
    }
  });
  const cardRefs = useRef({});
  const [highlightedAnnouncementId, setHighlightedAnnouncementId] = useState(null);
  const [detailVisible, setDetailVisible] = useState(false);
  const [detailAnnouncement, setDetailAnnouncement] = useState(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [statisticsAnnouncement, setStatisticsAnnouncement] = useState(null);
  const [statisticsLoading, setStatisticsLoading] = useState(false);
  const [userProfile, setUserProfile] = useState(null);

  const formatTime = (value) => {
    if (!value) return '-';
    return String(value).replace('T', ' ').slice(0, 16);
  };

  useEffect(() => {
    loadData();
    if (canPublish) {
      loadUserProfile();
      loadClassOptions();
    }
  }, []);

  const loadClassOptions = async () => {
    try {
      const res = await getClassOptions();
      const rows = Array.isArray(res?.data) ? res.data : (Array.isArray(res) ? res : []);
      setClassOptions(rows.filter((item) => item?.name));
    } catch (error) {
      console.error('获取班级选项失败:', error);
    }
  };

  const loadUserProfile = async () => {
    try {
      const res = await getCurrentAdminUser();
      const profile = res?.data || res || null;
      setUserProfile(profile);
    } catch (error) {
      console.error('获取用户信息失败:', error);
    }
  };

  useEffect(() => {
    localStorage.setItem(emailTaskStorageKey, JSON.stringify(emailTasks.slice(0, 8)));
  }, [emailTaskStorageKey, emailTasks]);

  useEffect(() => {
    const hasActiveTasks = emailTasks.some((task) => isEmailTaskActive(task.status));
    if (!hasActiveTasks) {
      return;
    }

    const timer = window.setInterval(async () => {
      const activeTasks = emailTasks.filter((task) => isEmailTaskActive(task.status));
      const updates = await Promise.all(
        activeTasks.map(async (task) => {
          try {
            const res = await getEmailTaskProgress(task.taskId);
            return res?.data || null;
          } catch (error) {
            console.error('获取邮件任务进度失败:', error);
            return null;
          }
        })
      );
      const updateMap = updates
        .filter(Boolean)
        .reduce((map, task) => ({ ...map, [task.taskId]: task }), {});

      setEmailTasks((prev) => prev.map((task) => (
        updateMap[task.taskId] ? { ...task, ...updateMap[task.taskId] } : task
      )));
    }, 1500);

    return () => window.clearInterval(timer);
  }, [emailTasks]);

  useEffect(() => {
    const focusAnnouncementId = location.state?.focusAnnouncementId;
    if (!focusAnnouncementId || records.length === 0) {
      return;
    }

    setActivePublisherTab('published');

    const targetCard = cardRefs.current[focusAnnouncementId];
    if (targetCard) {
      setHighlightedAnnouncementId(focusAnnouncementId);
      targetCard.scrollIntoView({ behavior: 'smooth', block: 'center' });
      const timer = window.setTimeout(() => {
        setHighlightedAnnouncementId(null);
      }, 2200);
      return () => window.clearTimeout(timer);
    }
  }, [activePublisherTab, location.state, records]);

  useEffect(() => {
    const presetTitle = location.state?.presetTitle;
    const presetContent = location.state?.presetContent;
    const focusClassName = location.state?.focusClassName;
    if (!presetTitle && !presetContent && !focusClassName) {
      return;
    }
    setActivePublisherTab('publish');
    setForm((prev) => ({
      ...prev,
      title: presetTitle || prev.title,
      content: presetContent || prev.content
    }));
    if (focusClassName) {
      setTargetType('CLASS');
      setSelectedClassNames([focusClassName]);
    }
  }, [location.state]);

  const loadData = async (pagination = listPagination) => {
    setLoading(true);
    try {
      const current = pagination.current || 1;
      const pageSize = pagination.pageSize || 10;
      const res = isInboxUser
        ? await getMyAnnouncements({ page: current - 1, size: pageSize })
        : await getPublishedAnnouncements({ page: current - 1, size: pageSize });
      const payload = res?.data || {};
      const nextRecords = Array.isArray(payload.records) ? payload.records : (Array.isArray(payload.content) ? payload.content : []);
      setRecords(nextRecords);
      if (!isInboxUser) {
        const refreshedDetail = statisticsAnnouncement
          ? nextRecords.find((item) => item.id === statisticsAnnouncement.id)
          : null;
        const nextDetail = refreshedDetail
          ? { ...statisticsAnnouncement, ...refreshedDetail }
          : (nextRecords[0] || null);
        setStatisticsAnnouncement(nextDetail);
        if (nextDetail) {
          loadRecipients(nextDetail.id);
        }
      }
      setListPagination({
        current,
        pageSize,
        total: Number(payload.totalElements ?? payload.total ?? 0)
      });
    } catch (error) {
      console.error('获取公告失败:', error);
    } finally {
      setLoading(false);
    }
  };

  const loadRecipients = async (announcementId) => {
    try {
      const res = await getAnnouncementRecipients(announcementId);
      setSelectedAnnouncement(res.data?.announcement || null);
      setRecipientData(res.data?.records || []);
    } catch (error) {
      console.error('获取公告回执失败:', error);
    }
  };

  const isEmailTaskActive = (status) => ['QUEUED', 'RUNNING'].includes(status);

  const trackEmailTask = (task, label, metadata = {}) => {
    if (!task?.taskId) {
      return;
    }
    const nextTask = {
      ...task,
      ...metadata,
      label,
      createdAt: metadata.createdAt || task.createdAt || new Date().toISOString(),
      processedCount: task.processedCount || 0,
      pendingCount: task.pendingCount ?? task.total ?? 0
    };
    setEmailTasks((prev) => {
      if (prev.some((item) => item.taskId === task.taskId)) {
        return prev.map((item) => (item.taskId === task.taskId ? { ...item, ...nextTask } : item));
      }
      return [nextTask, ...prev].slice(0, 8);
    });
  };

  const buildEmailTaskLabel = (prefix, title) => {
    const normalizedTitle = title?.trim();
    return normalizedTitle ? `${prefix}：《${normalizedTitle}》` : prefix;
  };

  const getEmailTaskStatusText = (status) => {
    const statusText = {
      QUEUED: '排队中',
      RUNNING: '发送中',
      SUCCESS: '已完成',
      PARTIAL_SUCCESS: '部分成功',
      FAILED: '发送失败',
      SMTP_CHECK_FAILED: 'SMTP 校验失败',
      NO_RECIPIENT_EMAIL: '无可发送邮箱'
    };
    return statusText[status] || status || '-';
  };

  const getEmailResultMessage = (result, options = {}) => {
    const { sitePublished = true, siteAction = '通知' } = options;
    if (!result) return '公告已发布，但没有返回邮件发送结果。';
    const missingSenderMessage = result.message || '发送邮箱未配置。';
    if (!sitePublished) {
      const unpublishedStatusText = {
        DISABLED: `邮箱功能未启用，站内${siteAction}未发布。`,
        NO_SENDER: `${missingSenderMessage}站内${siteAction}未发布。`,
        NO_SENDER_PASSWORD: `${missingSenderMessage}站内${siteAction}未发布。`,
        NO_RECIPIENT_EMAIL: `本次范围内没有学生绑定邮箱，站内${siteAction}未发布。`,
        SMTP_CHECK_FAILED: `${missingSenderMessage}站内${siteAction}未发布。`,
        SUCCESS: `邮件通知已全部发送成功，站内${siteAction}已同步发布。`,
        PARTIAL_SUCCESS: `邮件通知部分发送成功，站内${siteAction}已同步发布。`,
        FAILED: `邮件通知全部发送失败，站内${siteAction}未发布。`,
        ERROR: `邮件发送时出现异常，站内${siteAction}未发布。`
      };
      return unpublishedStatusText[result.status] || result.message || `邮件发送失败，站内${siteAction}未发布。`;
    }

    if (siteAction === '催读') {
      const reminderStatusText = {
        DISABLED: '邮箱功能未启用，站内催读未发布。',
        NO_SENDER: `${missingSenderMessage}站内催读未发布。`,
        NO_SENDER_PASSWORD: `${missingSenderMessage}站内催读未发布。`,
        NO_RECIPIENT_EMAIL: '本次范围内没有学生绑定邮箱，站内催读未发布。',
        SMTP_CHECK_FAILED: `${missingSenderMessage}站内催读未发布。`,
        SUCCESS: '邮件通知已全部发送成功，站内催读已同步发布。',
        PARTIAL_SUCCESS: '邮件通知部分发送成功，站内催读已同步发布。',
        FAILED: '邮件通知全部发送失败，站内催读未发布。',
        ERROR: '邮件发送时出现异常，站内催读未发布。'
      };
      return reminderStatusText[result.status] || result.message || '邮件发送失败，站内催读未发布。';
    }

    const statusText = {
      DISABLED: '邮箱功能未启用，公告已发布为站内通知。',
      NO_SENDER: `${missingSenderMessage}公告已发布为站内通知。`,
      NO_SENDER_PASSWORD: `${missingSenderMessage}公告已发布为站内通知。`,
      NO_RECIPIENT_EMAIL: '公告已发布，但本次范围内没有学生绑定邮箱。',
      SMTP_CHECK_FAILED: `${missingSenderMessage}公告已发布为站内通知。`,
      SUCCESS: '公告已发布，邮件通知已全部发送成功。',
      PARTIAL_SUCCESS: '公告已发布，邮件通知部分发送成功。',
      FAILED: '公告已发布，但邮件通知全部发送失败。',
      ERROR: '公告已发布，但邮件发送时出现异常。'
    };
    return statusText[result.status] || result.message || '公告已发布，邮件发送已完成。';
  };

  const isBlockingEmailStatus = (result) => [
    'DISABLED',
    'NO_SENDER',
    'NO_SENDER_PASSWORD',
    'NO_RECIPIENT_EMAIL',
    'SMTP_CHECK_FAILED',
    'FAILED',
    'ERROR'
  ].includes(result?.status);

  const renderHighlightedEmailMessage = (message) => {
    const highlightTerms = [
      '未配置发送邮箱',
      '请先配置系统邮箱，或为当前账号填写邮箱。',
      '教师邮箱未配置',
      '默认邮箱未配置',
      '发件邮箱未配置',
      '邮箱功能未启用',
      '没有学生绑定邮箱',
      '邮件通知全部发送失败',
      '邮件发送时出现异常',
      'SMTP 校验失败',
      'SMTP 登录失败',
      '无法连接 SMTP 服务器',
      '授权码/密码不正确',
      '未开启 SMTP 服务',
      '网络环境',
      'SMTP 地址',
      '站内通知未发布',
      '站内催读未发布',
      '临时限制',
      '发送频率过高',
      '重复发送',
      '被邮箱服务商拒绝'
    ];
    const parts = [];
    let remaining = message;

    while (remaining) {
      const nextMatch = highlightTerms
        .map((term) => ({ term, index: remaining.indexOf(term) }))
        .filter((item) => item.index >= 0)
        .sort((a, b) => a.index - b.index || b.term.length - a.term.length)[0];

      if (!nextMatch) {
        parts.push({ text: remaining, highlighted: false });
        break;
      }

      if (nextMatch.index > 0) {
        parts.push({ text: remaining.slice(0, nextMatch.index), highlighted: false });
      }

      parts.push({ text: nextMatch.term, highlighted: true });
      remaining = remaining.slice(nextMatch.index + nextMatch.term.length);
    }

    return parts.map((part, index) => (
      part.highlighted
        ? <span key={`${part.text}-${index}`} style={{ color: '#ff4d4f', fontWeight: 600 }}>{part.text}</span>
        : <span key={`${part.text}-${index}`}>{part.text}</span>
    ));
  };

  const showEmailResult = (result, title = '邮件发送结果', options = {}) => {
    const { sitePublished = true, siteAction = '通知', type = 'info' } = options;
    const {
      recipientCount = 0,
      total = 0,
      taskId,
      status,
      successCount = 0,
      failureCount = 0,
      failedEmails = [],
      failures = []
    } = result || {};
    const isQueuedTask = Boolean(taskId) && ['QUEUED', 'RUNNING'].includes(status);
    const isPreflightFailure = [
      'DISABLED',
      'NO_SENDER',
      'NO_SENDER_PASSWORD',
      'NO_RECIPIENT_EMAIL',
      'SMTP_CHECK_FAILED'
    ].includes(status);
    const statusMessage = getEmailResultMessage(result, { sitePublished, siteAction });
    const shouldAppendBackendMessage = result?.status !== 'NO_SENDER'
      && result?.message
      && result.message !== statusMessage
      && !statusMessage.includes(result.message);
    const detailMessage = shouldAppendBackendMessage
      ? `${statusMessage} ${result.message}`
      : statusMessage;
    const uniqueFailureReasons = failures
      .map((item) => item.reason || '未知原因')
      .filter((reason, index, list) => list.indexOf(reason) === index);

    const content = (
      <div>
        <p>{renderHighlightedEmailMessage(detailMessage)}</p>
        {isQueuedTask ? (
          <p style={{ marginTop: 8 }}>
            覆盖学生 <strong>{recipientCount}</strong> 人，可发送邮箱 <strong>{total}</strong> 封。发送任务已进入后台队列。
          </p>
        ) : isPreflightFailure ? (
          <p style={{ marginTop: 8 }}>
            覆盖学生 <strong>{recipientCount}</strong> 人，可发送邮箱 <strong>{total}</strong> 封。
          </p>
        ) : (
          <p style={{ marginTop: 8 }}>
            覆盖学生 <strong>{recipientCount}</strong> 人，可发送邮箱 <strong>{total}</strong> 封，
            成功 <strong style={{ color: '#52c41a' }}>{successCount}</strong> 封，
            失败 <strong style={{ color: '#ff4d4f' }}>{failureCount}</strong> 封。
          </p>
        )}
        {uniqueFailureReasons.length > 0 ? (
          <>
            <p style={{ marginTop: 8, color: '#ff4d4f' }}>失败原因：</p>
            <ul style={{ margin: '4px 0 0 0', paddingLeft: 20, maxHeight: 220, overflowY: 'auto', fontSize: 13, color: '#ff4d4f' }}>
              {uniqueFailureReasons.map((reason, i) => (
                <li key={`${reason}-${i}`}>{reason}</li>
              ))}
            </ul>
          </>
        ) : failedEmails && failedEmails.length > 0 ? (
          <>
            <p style={{ marginTop: 8, color: '#ff4d4f' }}>失败邮箱：</p>
            <ul style={{ margin: '4px 0 0 0', paddingLeft: 20, maxHeight: 200, overflowY: 'auto', fontSize: 13, color: '#ff4d4f' }}>
              {failedEmails.map((e, i) => <li key={i}>{e}</li>)}
            </ul>
          </>
        ) : null}
      </div>
    );

    Modal[type]({
      title,
      content,
      okText: '确定'
    });
  };

  const hasEmailResult = (result) => Boolean(result?.status);

  const showSiteOnlySuccess = () => {
    Modal.success({
      title: '公告发布成功',
      content: '站内通知已发布',
      okText: '确定'
    });
  };

  const checkEmailConfigured = useCallback(() => {
    return Boolean(userProfile?.emailConfigured);
  }, [userProfile]);

  const requireEmailConfigured = () => {
    if (checkEmailConfigured()) {
      return false;
    }
    Modal.warning({
      title: '个人邮箱未绑定',
      content: '请先前往「个人信息」页面绑定邮箱并配置 SMTP 授权码，绑定后即可使用自己的邮箱发送通知。',
      okText: '去绑定',
      cancelText: '取消',
      onOk: () => navigate('/admin/profile'),
      onCancel: () => {}
    });
    return true;
  };

  const handlePublishModeChange = (mode) => {
    if (mode === 'TEACHER' && !checkEmailConfigured()) {
      requireEmailConfigured();
      return;
    }
    setPublishMode(mode);
  };

  const handleTargetTypeChange = (type) => {
    setTargetType(type);
    if (type !== 'GRADE') {
      setSelectedGrades([]);
    }
    if (type !== 'CLASS') {
      setSelectedClassNames([]);
    }
  };

  const toggleTargetGrade = (grade) => {
    setSelectedGrades((prev) => (
      prev.includes(grade) ? prev.filter((item) => item !== grade) : [...prev, grade]
    ));
  };

  const toggleTargetClassName = (className) => {
    setSelectedClassNames((prev) => (
      prev.includes(className) ? prev.filter((item) => item !== className) : [...prev, className]
    ));
  };

  const validateTargetScope = () => {
    if (targetType === 'GRADE' && selectedGrades.length === 0) {
      Modal.warning({ title: '请选择接收年级', content: '按年级发布时，需要至少选择一个年级。', okText: '确定' });
      return false;
    }
    if (targetType === 'CLASS' && selectedClassNames.length === 0) {
      Modal.warning({ title: '请选择接收班级', content: '按班级发布时，需要至少选择一个班级。', okText: '确定' });
      return false;
    }
    return true;
  };

  const handlePublish = async () => {
    if (!form.title.trim() || !form.content.trim()) {
      return;
    }
    if (!validateTargetScope()) {
      return;
    }
    await doPublish();
  };

  const doPublish = async () => {
    if (!form.title.trim() || !form.content.trim()) {
      return;
    }
    if (!validateTargetScope()) {
      return;
    }

    try {
      const res = await createAnnouncement({
        title: form.title,
        content: form.content,
        senderType: publishMode,
        targetType,
        targetGrades: targetType === 'GRADE' ? selectedGrades : [],
        targetClassNames: targetType === 'CLASS' ? selectedClassNames : []
      });
      const emailResult = res?.data?.emailResult;
      if (hasEmailResult(emailResult)) {
        if (isBlockingEmailStatus(emailResult)) {
          showEmailResult(emailResult, emailResult.message || '发布失败', {
            sitePublished: false,
            siteAction: '通知',
            type: 'error'
          });
          return;
        }
        showEmailResult(emailResult, '公告发布成功');
      } else {
        showSiteOnlySuccess();
      }
      setForm({ title: '', content: '' });
      setTargetType('ALL');
      setSelectedGrades([]);
      setSelectedClassNames([]);
      const firstPage = { ...listPagination, current: 1 };
      setListPagination(firstPage);
      loadData(firstPage);
    } catch (error) {
      const emailResult = error?.response?.data?.data?.emailResult;
      if (hasEmailResult(emailResult)) {
        showEmailResult(emailResult, error?.response?.data?.message || '发布失败', {
          sitePublished: false,
          siteAction: '通知',
          type: 'error'
        });
        return;
      }
      Modal.error({
        title: '发布失败',
        content: error?.response?.data?.message || error?.message || '发布公告失败',
        okText: '确定'
      });
    }
  };

  const handleViewDetail = async (announcement) => {
    setDetailAnnouncement(announcement);
    setDetailVisible(true);
    setDetailLoading(true);
    try {
      const res = await getAnnouncementDetail(announcement.id);
      const detail = res?.data || announcement;
      setDetailAnnouncement(detail);
      if (isInboxUser) {
        setRecords((prev) => prev.map((item) => (
          item.id === announcement.id
            ? {
                ...item,
                read: true,
                readAt: detail.readAt || item.readAt,
                readCount: detail.readCount ?? item.readCount,
                unreadCount: detail.unreadCount ?? item.unreadCount
              }
            : item
        )));
      }
    } catch (error) {
      console.error('获取公告详情失败:', error);
    } finally {
      setDetailLoading(false);
    }
  };

  const handleCloseDetail = () => {
    setDetailVisible(false);
    setDetailAnnouncement(null);
    setDetailLoading(false);
  };

  const doRemind = async (announcementId, senderType) => {
    try {
      const res = await remindAnnouncementUnread(announcementId, senderType);
      const emailResult = res?.data?.emailResult;
      if (hasEmailResult(emailResult)) {
        const announcementTitle = records.find((item) => item.id === announcementId)?.title;
        trackEmailTask(emailResult, buildEmailTaskLabel('催读邮件', announcementTitle), {
          kind: 'reminder',
          announcementId,
          announcementTitle,
          senderType
        });
        showEmailResult(emailResult, '催读完成', { siteAction: '催读' });
      } else {
        Modal.success({ title: '催读完成', content: '站内催读已发布', okText: '确定' });
      }
      loadData();
      loadRecipients(announcementId);
    } catch (error) {
      const emailResult = error?.response?.data?.data?.emailResult;
      if (hasEmailResult(emailResult)) {
        showEmailResult(emailResult, error?.response?.data?.message || '催读失败', {
          sitePublished: false,
          siteAction: '催读',
          type: 'error'
        });
        return;
      }
      Modal.error({
        title: '催读失败',
        content: error?.response?.data?.message || error?.message || '催读失败',
        okText: '确定'
      });
    }
  };

  const handleViewStatistics = async (announcement) => {
    setReceiptTab('unread');
    setStatisticsAnnouncement(announcement);
    setStatisticsLoading(true);
    loadRecipients(announcement.id);
    try {
      const res = await getAnnouncementDetail(announcement.id);
      setStatisticsAnnouncement(res?.data || announcement);
    } catch (error) {
      console.error('获取公告统计失败:', error);
    } finally {
      setStatisticsLoading(false);
    }
  };

  const handleRemind = async (announcementId, senderType) => {
    if (senderType === 'TEACHER' && requireEmailConfigured()) {
      return;
    }
    await doRemind(announcementId, senderType);
  };

  const gradeOptions = useMemo(() => {
    const grades = classOptions
      .map((item) => item.grade)
      .filter((grade, index, list) => grade && list.indexOf(grade) === index);
    return grades.sort((a, b) => String(b).localeCompare(String(a), 'zh-CN'));
  }, [classOptions]);

  const classGroupsByGrade = useMemo(() => {
    return classOptions.reduce((groups, item) => {
      const grade = item.grade || '未分年级';
      if (!groups[grade]) {
        groups[grade] = [];
      }
      groups[grade].push(item);
      return groups;
    }, {});
  }, [classOptions]);

  const targetReady = targetType === 'ALL'
    || (targetType === 'GRADE' && selectedGrades.length > 0)
    || (targetType === 'CLASS' && selectedClassNames.length > 0);

  const targetSummary = targetType === 'ALL'
    ? `将推送给当前可见范围内的全部学生，共 ${classOptions.length} 个班级`
    : targetType === 'GRADE'
      ? `已选择 ${selectedGrades.length} 个年级`
      : `已选择 ${selectedClassNames.length} 个班级`;

  const readRecords = recipientData.filter((item) => item.read);
  const unreadRecords = recipientData.filter((item) => !item.read);
  const reminderEmailTasks = statisticsAnnouncement
    ? emailTasks.filter((task) => (
        task.kind === 'reminder'
        && String(task.announcementId) === String(statisticsAnnouncement.id)
      ) || (
        !task.kind
        && task.label?.startsWith('催读邮件')
        && task.label.includes(`《${statisticsAnnouncement.title}》`)
      ))
    : [];
  const unreadTotal = records.reduce((sum, item) => sum + Number(item.unreadCount || 0), 0);
  const readTotal = records.reduce((sum, item) => sum + Number(item.readCount || 0), 0);
  const publishTotal = listPagination.total;
  const publishModeOptions = [
    {
      value: 'SITE',
      title: '站内通知',
      description: '只发布站内通知，不发送邮件'
    },
    {
      value: 'TEACHER',
      title: '自己的邮箱',
      description: '使用老师自己绑定的邮箱发送邮件，成功或部分成功后同步发布站内通知'
    },
    {
      value: 'SYSTEM',
      title: '系统邮箱',
      description: '使用系统默认邮箱发送邮件，成功或部分成功后同步发布站内通知'
    }
  ];

  return (
    <div className="announcement-center" style={{ paddingTop: 0, paddingLeft: 0, paddingRight: 0 }}>

      {!isInboxUser ? (
        <div className="announcement-page-tabs" role="tablist" aria-label="通知公告">
          <button
            type="button"
            role="tab"
            aria-selected={activePublisherTab === 'publish'}
            className={activePublisherTab === 'publish' ? 'active' : ''}
            onClick={() => setActivePublisherTab('publish')}
          >
            创建公告
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={activePublisherTab === 'published'}
            className={activePublisherTab === 'published' ? 'active' : ''}
            onClick={() => setActivePublisherTab('published')}
          >
            已发布公告
          </button>
          {activePublisherTab === 'publish' ? (
            <button
              type="button"
              className="announcement-tab-publish"
              onClick={handlePublish}
              disabled={!form.title.trim() || !form.content.trim() || !targetReady}
            >
              发布公告
            </button>
          ) : null}
        </div>
      ) : null}

      {!isInboxUser && activePublisherTab === 'published' && (
        <div className="announcement-stats">
          <div className="announcement-stat-card">
            <span>已发布公告</span>
            <strong>{publishTotal}</strong>
          </div>
          <div className="announcement-stat-card">
            <span>累计已读</span>
            <strong>{readTotal}</strong>
          </div>
          <div className="announcement-stat-card warning">
            <span>当前未读</span>
            <strong>{unreadTotal}</strong>
          </div>
        </div>
      )}

      {canPublish && activePublisherTab === 'publish' && (
        <form className="announcement-editor announcement-editor-layout" onSubmit={(e) => e.preventDefault()}>
          <div className="announcement-compose-panel">
            <div className="announcement-editor-head">
              <h3>公告内容编辑</h3>
            </div>
            <div className="announcement-mail-tip">
              选择邮箱通知时，邮件全部或部分发送成功都会同步发布站内通知；邮件全部失败则整体发布失败。
            </div>
            <div className="announcement-field-group announcement-mode-field">
              <div className="announcement-field-label">发送方式 <span>*</span></div>
              <div className="announcement-publish-modes" role="radiogroup" aria-label="发布方式">
                {publishModeOptions.map((option) => (
                  <button
                    key={option.value}
                    type="button"
                    className={`announcement-publish-mode${publishMode === option.value ? ' active' : ''}`}
                    onClick={() => handlePublishModeChange(option.value)}
                    role="radio"
                    aria-checked={publishMode === option.value}
                    title={option.description}
                  >
                    <i aria-hidden="true" />
                    <strong>{option.title}</strong>
                    <span>{option.description}</span>
                  </button>
                ))}
              </div>
            </div>
            <div className="announcement-field-group announcement-target-field">
              <div className="announcement-field-label">接收范围 <span>*</span></div>
              <div className="announcement-target-tabs" role="radiogroup" aria-label="接收范围">
                {[
                  { value: 'ALL', label: '全部可见班级' },
                  { value: 'GRADE', label: '按年级' },
                  { value: 'CLASS', label: '按班级' }
                ].map((option) => (
                  <button
                    key={option.value}
                    type="button"
                    className={targetType === option.value ? 'active' : ''}
                    onClick={() => handleTargetTypeChange(option.value)}
                    role="radio"
                    aria-checked={targetType === option.value}
                  >
                    {option.label}
                  </button>
                ))}
              </div>
              {targetType === 'GRADE' ? (
                <div className="announcement-target-options">
                  {gradeOptions.length === 0 ? (
                    <span className="announcement-target-empty">暂无可选年级</span>
                  ) : gradeOptions.map((grade) => (
                    <button
                      key={grade}
                      type="button"
                      className={`announcement-target-chip${selectedGrades.includes(grade) ? ' active' : ''}`}
                      onClick={() => toggleTargetGrade(grade)}
                    >
                      {grade}
                    </button>
                  ))}
                </div>
              ) : null}
              {targetType === 'CLASS' ? (
                <div className="announcement-target-class-list">
                  {Object.keys(classGroupsByGrade).length === 0 ? (
                    <span className="announcement-target-empty">暂无可选班级</span>
                  ) : Object.entries(classGroupsByGrade).map(([grade, classes]) => (
                    <div key={grade} className="announcement-target-grade-group">
                      <span>{grade}</span>
                      <div>
                        {classes.map((item) => (
                          <button
                            key={item.id || item.name}
                            type="button"
                            className={`announcement-target-chip${selectedClassNames.includes(item.name) ? ' active' : ''}`}
                            onClick={() => toggleTargetClassName(item.name)}
                          >
                            {item.name}
                          </button>
                        ))}
                      </div>
                    </div>
                  ))}
                </div>
              ) : null}
              <div className={`announcement-target-summary${targetReady ? '' : ' warning'}`}>
                {targetReady ? targetSummary : '请先选择具体接收范围'}
              </div>
            </div>
            <div className="announcement-field-group announcement-title-field">
              <div className="announcement-field-label">公告标题 <span>*</span></div>
              <div className="announcement-title-input">
                <input
                  value={form.title}
                  onChange={(e) => setForm((prev) => ({ ...prev, title: e.target.value }))}
                  placeholder="请输入公告标题，最多 120 字"
                  maxLength={120}
                />
                <span>{form.title.length}/120</span>
              </div>
            </div>
            <div className="announcement-field-group announcement-content-field">
              <div className="announcement-field-label">正文内容 <span>*</span></div>
              <AnnouncementRichEditor
                value={form.content}
                onChange={(content) => setForm((prev) => ({ ...prev, content }))}
              />
            </div>
          </div>
          <div className="announcement-preview-panel">
            <div className="announcement-preview-head">
              <h3>内容预览 <span aria-hidden="true">i</span></h3>
            </div>
            <AnnouncementArticle
              announcement={{
                title: form.title,
                content: form.content,
                publisherName: user.nickname || user.username || '当前用户'
              }}
              showMeta={false}
              className="announcement-preview-article"
            />
          </div>
        </form>
      )}

      {isInboxUser || activePublisherTab === 'published' ? (
      <div className={isInboxUser ? '' : 'published-main-layout'}>
      <div className="announcement-list-panel inbox-style">
        <div className="inbox-header">
          <h3>
            {isInboxUser ? '收到的通知' : '已发布通知'}
            {showDevelopmentBadge ? <span className="announcement-dev-badge">开发中</span> : null}
          </h3>
          <div className="inbox-header-right">
            <span className="inbox-total">共 {publishTotal} 条</span>
            <button type="button" className="btn btn-default" onClick={() => loadData()}>刷新</button>
          </div>
        </div>

        {loading ? (
          <div className="announcement-empty">加载中...</div>
        ) : records.length === 0 ? (
          <div className="announcement-empty">暂无公告</div>
        ) : (
          <>
            <div className="inbox-list">
              {records.map((item) => {
                const isUnread = isInboxUser ? item.read === false : (item.unreadCount > 0);
                return (
                  <div
                  key={item.id}
                  ref={(element) => {
                    if (element) {
                      cardRefs.current[item.id] = element;
                    }
                  }}
                  className={`inbox-row ${isUnread ? 'unread' : 'read'} ${highlightedAnnouncementId === item.id ? 'focused' : ''} ${!isInboxUser && statisticsAnnouncement?.id === item.id ? 'selected' : ''}`}
                >
                  <div className="inbox-row-main" onClick={isInboxUser ? () => handleViewDetail(item) : undefined}>
                    {/* 信封图标 */}
                    <div className="inbox-envelope">
                      {isUnread ? (
                        <svg viewBox="0 0 24 24" width="22" height="22" className="envelope-icon unread">
                          <rect x="2" y="5" width="20" height="14" rx="2" fill="#faad14" stroke="#d48806" strokeWidth="1"/>
                          <path d="M2 7l10 6 10-6" fill="none" stroke="#d48806" strokeWidth="1.2" strokeLinecap="round"/>
                        </svg>
                      ) : (
                        <svg viewBox="0 0 24 24" width="22" height="22" className="envelope-icon read">
                          <path d="M2 9l10-5 10 5v10a2 2 0 01-2 2H4a2 2 0 01-2-2V9z" fill="#f5f5f5" stroke="#bfbfbf" strokeWidth="1"/>
                          <path d="M2 9l10 6 10-6" fill="none" stroke="#bfbfbf" strokeWidth="1.2" strokeLinecap="round"/>
                          <rect x="6" y="3" width="12" height="9" rx="1" fill="#fff" stroke="#d9d9d9" strokeWidth="0.8"/>
                        </svg>
                      )}
                    </div>
                    {/* 发送者 */}
                    <div className="inbox-sender">
                      <span className={isUnread ? 'bold' : ''}>{item.publisherName || '系统'}</span>
                    </div>
                    {/* 标题 + 内容预览 */}
                    <div className="inbox-content">
                      <span className={`inbox-title ${isUnread ? 'bold' : ''}`}>{item.title}</span>
                      <span className="inbox-preview">{item.summary || item.content?.replace(/<[^>]+>/g, '').slice(0, 80)}</span>
                    </div>
                    {/* 公告操作（管理员/教师） */}
                    {!isInboxUser && (
                      <div className="inbox-row-controls">
                        <button type="button" className="btn btn-default btn-sm" onClick={() => handleViewDetail(item)}>查看</button>
                        <button type="button" className="btn btn-primary btn-sm" onClick={() => handleViewStatistics(item)}>统计</button>
                      </div>
                    )}
                    {/* 时间 */}
                    <div className="inbox-time">
                      <span>{formatTime(item.publishTime)}</span>
                    </div>
                  </div>
                  </div>
                );
              })}
            </div>
            {listPagination.total > 0 ? (
              <div style={{ display: 'flex', justifyContent: 'flex-end', paddingTop: 16 }}>
                <Pagination
                  current={listPagination.current}
                  pageSize={10}
                  total={listPagination.total}
                  showTotal={(total) => `共 ${total} 条`}
                  onChange={(current) => {
                    const nextPagination = { current, pageSize: 10, total: listPagination.total };
                    setListPagination(nextPagination);
                    loadData(nextPagination);
                  }}
                />
              </div>
            ) : null}
          </>
        )}
      </div>
      {!isInboxUser ? (
        <aside className="published-detail-panel">
          <div className="published-detail-heading">
            <div>
              <h3 title={statisticsAnnouncement?.title || ''}>
                {statisticsAnnouncement?.title || '请选择公告'}
              </h3>
              {statisticsAnnouncement ? (
                <p>
                  发布人：{statisticsAnnouncement.publisherName || '-'}
                  <span>发布时间：{formatTime(statisticsAnnouncement.publishTime || statisticsAnnouncement.createdAt)}</span>
                </p>
              ) : null}
            </div>
            {statisticsAnnouncement ? <span className="published-status-tag">已完成</span> : null}
          </div>

          {statisticsLoading ? (
            <div className="announcement-empty">加载中...</div>
          ) : statisticsAnnouncement ? (
            <>
              <div className="published-detail-metrics">
                <div><span>接收人数</span><strong>{statisticsAnnouncement.recipientCount ?? ((statisticsAnnouncement.readCount || 0) + (statisticsAnnouncement.unreadCount || 0))}</strong></div>
                <div><span>已读人数</span><strong>{statisticsAnnouncement.readCount || 0}</strong></div>
                <div><span>未读人数</span><strong>{statisticsAnnouncement.unreadCount || 0}</strong></div>
              </div>

              <div className="published-detail-actions">
                <button type="button" className="btn btn-default btn-sm" onClick={() => handleRemind(statisticsAnnouncement.id, 'SITE')}>站内催读</button>
                <button type="button" className="btn btn-default btn-sm" onClick={() => handleRemind(statisticsAnnouncement.id, 'TEACHER')}>自己的邮箱</button>
                <button type="button" className="btn btn-primary btn-sm" onClick={() => handleRemind(statisticsAnnouncement.id, 'SYSTEM')}>系统邮箱</button>
              </div>

              {selectedAnnouncement?.id === statisticsAnnouncement.id ? (
                <div className="published-receipt-panel">
                  <div className="published-receipt-tabs">
                    <button
                      type="button"
                      className={receiptTab === 'unread' ? 'active' : ''}
                      onClick={() => setReceiptTab('unread')}
                    >
                      未读学生（{unreadRecords.length}）
                    </button>
                    <button
                      type="button"
                      className={receiptTab === 'read' ? 'active' : ''}
                      onClick={() => setReceiptTab('read')}
                    >
                      已读学生（{readRecords.length}）
                    </button>
                  </div>
                  <div className="published-receipt-list">
                    {(receiptTab === 'unread' ? unreadRecords : readRecords).length === 0 ? (
                      <div className="announcement-empty small">
                        {receiptTab === 'unread' ? '全部学生均已阅读' : '暂无已读学生'}
                      </div>
                    ) : (receiptTab === 'unread' ? unreadRecords : readRecords).map((record) => (
                      <div key={record.id} className="published-receipt-row">
                        <span>{record.studentName}</span>
                        <span>{record.className || '-'}</span>
                        <span>{record.readAt ? formatTime(record.readAt) : '-'}</span>
                        <em className={record.read ? 'read' : ''}>{record.read ? '已读' : '未读'}</em>
                      </div>
                    ))}
                  </div>
                </div>
              ) : null}

              <div className="published-reminder-history">
                <h4>催读记录</h4>
                {reminderEmailTasks.length > 0 ? (
                  reminderEmailTasks.map((task) => (
                    <div key={task.taskId} className="published-reminder-row">
                      <span>{formatTime(task.createdAt)}</span>
                      <span>{task.senderType === 'TEACHER' ? '自己的邮箱' : '系统邮箱'}</span>
                      <span>{getEmailTaskStatusText(task.status)}</span>
                      <strong>成功 {task.successCount || 0} 封</strong>
                    </div>
                  ))
                ) : (
                  <div className="announcement-empty small">暂无邮箱催读记录</div>
                )}
              </div>
            </>
          ) : (
            <div className="announcement-empty">请选择左侧公告查看统计</div>
          )}
        </aside>
      ) : null}
      </div>
      ) : null}

      {/* 公告详情抽屉 */}
      <AnnouncementDetailDrawer
        open={detailVisible}
        announcement={detailAnnouncement}
        loading={detailLoading}
        onClose={handleCloseDetail}
      />
    </div>
  );
}

export default AnnouncementCenterPanel;
