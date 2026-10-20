import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Badge, Button, List, Modal, Segmented, Tag } from 'antd';
import { BellOutlined, CheckOutlined, NotificationOutlined } from './AcademicIcons';
import { getMyAnnouncements, markAnnouncementRead } from '../api/announcement';
import { getMessages, markAllMessagesRead, markMessageRead } from '../api/message';
import { getStoredUser } from '../utils/authStorage';
import './MessageCenter.css';

const TYPE_META = {
  NOTICE: { label: '公告', color: 'blue' },
  REVIEW_RESULT: { label: '审核结果', color: 'green' },
  WARNING: { label: '预警', color: 'red' },
  TODO: { label: '待办', color: 'orange' },
};

const FILTER_OPTIONS = [
  { label: '全部', value: 'ALL' },
  { label: '公告', value: 'NOTICE' },
  { label: '审核结果', value: 'REVIEW_RESULT' },
  { label: '预警', value: 'WARNING' },
  { label: '待办', value: 'TODO' },
];

const formatTime = (value) => (value ? new Date(value).toLocaleString('zh-CN') : '');

function MessageCenter() {
  const navigate = useNavigate();
  const user = getStoredUser();
  const role = user?.role;
  const isInboxUser = ['STUDENT', 'CLASS_COMMITTEE'].includes(role);

  const [open, setOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [messages, setMessages] = useState([]);
  const [announcements, setAnnouncements] = useState([]);
  const [filter, setFilter] = useState('ALL');
  const pollTimerRef = useRef(null);

  const fetchAll = async () => {
    try {
      if (isInboxUser) {
        const [annRes, msgRes] = await Promise.all([getMyAnnouncements(), getMessages()]);
        setAnnouncements(annRes?.data?.records || []);
        setMessages(msgRes?.data?.records || []);
      } else {
        const msgRes = await getMessages();
        setMessages(msgRes?.data?.records || []);
      }
    } catch (_error) {
      // 静默处理，避免轮询期间刷错误
    }
  };

  useEffect(() => {
    fetchAll();
    pollTimerRef.current = setInterval(fetchAll, 30000);
    return () => {
      if (pollTimerRef.current) {
        clearInterval(pollTimerRef.current);
        pollTimerRef.current = null;
      }
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const unified = useMemo(() => {
    const announcementRows = announcements.map((item) => ({
      key: `ann-${item.id}`,
      source: 'announcement',
      type: 'NOTICE',
      title: item.title,
      content: item.summary || item.content || '',
      read: item.read,
      link: isInboxUser ? '/student/announcements' : '/admin/announcements',
      createdAt: item.publishTime,
      rawId: item.id,
    }));
    const messageRows = messages.map((item) => ({
      key: `msg-${item.id}`,
      source: 'message',
      type: item.type,
      title: item.title,
      content: item.content || '',
      read: item.read,
      link: item.link,
      createdAt: item.createdAt,
      rawId: item.id,
    }));
    return [...messageRows, ...announcementRows].sort(
      (a, b) => new Date(b.createdAt || 0) - new Date(a.createdAt || 0),
    );
  }, [announcements, messages, isInboxUser]);

  const unreadCount = unified.filter((item) => !item.read).length;
  const visibleRows = filter === 'ALL' ? unified : unified.filter((item) => item.type === filter);

  const handleOpen = () => {
    setOpen(true);
    fetchAll();
  };

  const handleMarkRead = async (item) => {
    try {
      if (item.source === 'message') {
        await markMessageRead(item.rawId);
      } else {
        await markAnnouncementRead(item.rawId);
      }
      fetchAll();
    } catch (_error) {
      // 静默处理
    }
  };

  const handleMarkAll = async () => {
    setLoading(true);
    try {
      await markAllMessagesRead();
      const unreadAnnouncements = announcements.filter((item) => !item.read);
      for (const item of unreadAnnouncements) {
        await markAnnouncementRead(item.id);
      }
      fetchAll();
    } catch (_error) {
      // 静默处理
    } finally {
      setLoading(false);
    }
  };

  const handleClick = (item) => {
    handleMarkRead(item);
    setOpen(false);
    if (item.link) {
      navigate(item.link);
    }
  };

  const renderMeta = (item) => {
    const meta = TYPE_META[item.type] || { label: item.type, color: 'default' };
    return (
      <Tag color={meta.color} className="message-center-tag">
        {meta.label}
      </Tag>
    );
  };

  return (
    <>
      <div className="message-center-bell">
        <Badge count={unreadCount} overflowCount={99}>
          <button
            type="button"
            className="message-center-bell-btn"
            onClick={handleOpen}
            title="站内消息"
          >
            <BellOutlined style={{ fontSize: 20 }} />
          </button>
        </Badge>
      </div>

      <Modal
        open={open}
        onCancel={() => setOpen(false)}
        footer={null}
        width={720}
        className="message-center-modal"
        title={
          <div className="message-center-modal-title">
            <NotificationOutlined />
            <span>消息中心</span>
            {unreadCount > 0 ? <Badge count={unreadCount} /> : null}
          </div>
        }
      >
        <div className="message-center-toolbar">
          <Segmented
            value={filter}
            onChange={setFilter}
            options={FILTER_OPTIONS}
            size="small"
          />
          {unreadCount > 0 ? (
            <Button
              type="primary"
              size="small"
              icon={<CheckOutlined />}
              onClick={handleMarkAll}
              loading={loading}
            >
              全部已读
            </Button>
          ) : null}
        </div>

        <List
          className="message-center-list"
          dataSource={visibleRows}
          locale={{
            emptyText: (
              <div className="message-center-empty">
                <BellOutlined style={{ fontSize: 48, color: '#d9d9d9', marginBottom: 12 }} />
                <p>暂无消息</p>
              </div>
            ),
          }}
          renderItem={(item) => (
            <List.Item
              className={`message-center-item ${item.read ? '' : 'unread'}`}
              onClick={() => handleClick(item)}
            >
              <div className="message-center-item-body">
                <div className="message-center-item-title">
                  {!item.read ? <span className="message-center-dot" /> : null}
                  {renderMeta(item)}
                  <strong>{item.title}</strong>
                </div>
                <div className="message-center-item-content">{item.content}</div>
                <div className="message-center-item-time">{formatTime(item.createdAt)}</div>
              </div>
            </List.Item>
          )}
        />
      </Modal>
    </>
  );
}

export default MessageCenter;
