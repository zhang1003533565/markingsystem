import { useState, useEffect, useRef } from 'react';
import { Modal, Button, Badge, List } from 'antd';
import { BellOutlined, CheckOutlined, NotificationOutlined } from './AcademicIcons';
import AppIcon from './AppIcon';
import { getMyAnnouncements, markAnnouncementRead } from '../api/announcement';
import './NewMessageNotification.css';

function NewMessageNotification() {
  const [announcements, setAnnouncements] = useState([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const [modalVisible, setModalVisible] = useState(false);
  const [loading, setLoading] = useState(false);
  const pollTimerRef = useRef(null);

  // 初始加载和轮询：页签隐藏时暂停轮询，恢复可见时立即刷新并重启
  useEffect(() => {
    fetchUnreadAnnouncements();

    const startPolling = () => {
      if (pollTimerRef.current) return;
      pollTimerRef.current = setInterval(fetchUnreadAnnouncements, 30000);
    };
    const stopPolling = () => {
      if (pollTimerRef.current) {
        clearInterval(pollTimerRef.current);
        pollTimerRef.current = null;
      }
    };
    const handleVisibility = () => {
      if (document.hidden) {
        stopPolling();
      } else {
        fetchUnreadAnnouncements();
        startPolling();
      }
    };

    startPolling();
    if (typeof document !== 'undefined') {
      document.addEventListener('visibilitychange', handleVisibility);
    }

    return () => {
      stopPolling();
      if (typeof document !== 'undefined') {
        document.removeEventListener('visibilitychange', handleVisibility);
      }
    };
  }, []);

  // 获取未读公告
  const fetchUnreadAnnouncements = async () => {
    try {
      const response = await getMyAnnouncements();
      // API返回格式: {code: 200, data: {records: [...], total: 5}}
      const data = response?.data?.records || [];
      setAnnouncements(data);

      // 计算未读数量
      const unread = data.filter(item => !item.read).length;
      setUnreadCount(unread);
    } catch (_error) {
      // 获取公告失败时静默处理，避免轮询期间刷错误
    }
  };

  // 打开消息弹窗
  const handleOpenModal = () => {
    setModalVisible(true);
    fetchUnreadAnnouncements();
  };

  // 关闭消息弹窗
  const handleCloseModal = () => {
    setModalVisible(false);
  };

  // 标记为已读
  const handleMarkAsRead = async (id) => {
    try {
      await markAnnouncementRead(id);
      // 更新本地状态
      setAnnouncements(prev => 
        prev.map(item => 
          item.id === id ? { ...item, read: true } : item
        )
      );
      setUnreadCount(prev => Math.max(0, prev - 1));
    } catch (error) {
      console.error('标记已读失败:', error);
    }
  };

  // 全部标记为已读
  const handleMarkAllAsRead = async () => {
    setLoading(true);
    try {
      const unreadItems = announcements.filter(item => !item.read);
      for (const item of unreadItems) {
        await markAnnouncementRead(item.id);
      }
      // 更新本地状态
      setAnnouncements(prev => 
        prev.map(item => ({ ...item, read: true }))
      );
      setUnreadCount(0);
    } catch (error) {
      console.error('全部标记已读失败:', error);
    } finally {
      setLoading(false);
    }
  };

  // 获取分类图标
  const getCategoryIcon = (category) => {
    switch (category) {
      case 'MANAGEMENT_NOTICE':
        return <AppIcon name="announcement" size={28} />;
      case 'COLLEGE_NOTICE':
        return <AppIcon name="college" size={28} />;
      case 'COMMITTEE_NOTICE':
        return <AppIcon name="users" size={28} />;
      default:
        return <AppIcon name="announcement" size={28} />;
    }
  };

  return (
    <>
      {/* 消息铃铛按钮 */}
      <div className="notification-bell-wrapper">
        <Badge count={unreadCount} overflowCount={99} className="notification-badge">
          <Button
            type="text"
            icon={<BellOutlined style={{ fontSize: '20px' }} />}
            onClick={handleOpenModal}
            className="notification-bell-btn"
          />
        </Badge>
      </div>

      {/* 消息弹窗 */}
      <Modal
        title={
          <div className="modal-header">
            <NotificationOutlined className="modal-icon" />
            <span>消息通知</span>
            {unreadCount > 0 && (
              <Badge count={unreadCount} className="modal-badge" />
            )}
          </div>
        }
        open={modalVisible}
        onCancel={handleCloseModal}
        footer={null}
        width={700}
        className="notification-modal"
      >
        <div className="modal-actions">
          {unreadCount > 0 && (
            <Button
              type="primary"
              size="small"
              icon={<CheckOutlined />}
              onClick={handleMarkAllAsRead}
              loading={loading}
            >
              全部已读
            </Button>
          )}
          <Button
            size="small"
            onClick={fetchUnreadAnnouncements}
          >
            刷新
          </Button>
        </div>

        <List
          className="announcement-list"
          loading={loading}
          dataSource={announcements}
          renderItem={(item) => (
            <List.Item
              className={`announcement-item ${!item.read ? 'unread' : ''}`}
              actions={[
                !item.read && (
                  <Button
                    type="link"
                    size="small"
                    onClick={() => handleMarkAsRead(item.id)}
                  >
                    标记已读
                  </Button>
                )
              ]}
            >
              <List.Item.Meta
                avatar={
                  <div className={`announcement-avatar ${!item.read ? 'unread-avatar' : ''}`}>
                    {getCategoryIcon(item.category)}
                  </div>
                }
                title={
                  <div className="announcement-title-row">
                    <span className={`announcement-title ${!item.read ? 'unread-title' : ''}`}>
                      {item.title}
                    </span>
                    {!item.read && (
                      <Badge status="processing" className="unread-dot" />
                    )}
                  </div>
                }
                description={
                  <div className="announcement-desc">
                    <p className="announcement-content">{item.content}</p>
                    <div className="announcement-meta">
                      <span className="publisher">发布人: {item.publisherName}</span>
                      <span className="time">
                        {new Date(item.publishTime || item.publishTime).toLocaleString('zh-CN')}
                      </span>
                    </div>
                  </div>
                }
              />
            </List.Item>
          )}
          locale={{
            emptyText: (
              <div className="empty-announcements">
                <BellOutlined style={{ fontSize: '48px', color: '#d9d9d9', marginBottom: '16px' }} />
                <p>暂无消息通知</p>
              </div>
            )
          }}
        />
      </Modal>
    </>
  );
}

export default NewMessageNotification;
