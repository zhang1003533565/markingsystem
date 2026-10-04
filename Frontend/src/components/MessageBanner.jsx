import { useState, useEffect } from 'react';
import { Carousel, Badge, Tag, Button } from 'antd';
import { BellOutlined, NotificationOutlined, WarningOutlined, EyeOutlined } from './AcademicIcons';
import { getAnnouncementDetail, getMyAnnouncements, getPublishedAnnouncements } from '../api/announcement';
import AnnouncementDetailDrawer from './AnnouncementDetailDrawer';
import { getStoredUser } from '../utils/authStorage';
import './MessageBanner.css';

function MessageBanner() {
  const user = getStoredUser();
  const isInboxUser = ['STUDENT', 'CLASS_COMMITTEE'].includes(user.role);
  const [announcements, setAnnouncements] = useState([]);
  const [loading, setLoading] = useState(true);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailVisible, setDetailVisible] = useState(false);
  const [currentAnnouncement, setCurrentAnnouncement] = useState(null);

  // 获取公告列表
  useEffect(() => {
    fetchAnnouncements();
  }, []);

  const fetchAnnouncements = async () => {
    try {
      const response = isInboxUser ? await getMyAnnouncements() : await getPublishedAnnouncements();
      // API返回格式: {code: 200, data: {records: [...], total: 5}}
      const records = response?.data?.records || [];
      // 只展示最近的公告（最多10条）
      const recentAnnouncements = records.slice(0, 10);
      setAnnouncements(recentAnnouncements);
    } catch (error) {
      console.error('获取公告失败:', error);
    } finally {
      setLoading(false);
    }
  };

  // 查看详情
  const handleViewDetail = async (announcement) => {
    setCurrentAnnouncement(announcement);
    setDetailVisible(true);
    setDetailLoading(true);
    try {
      const response = await getAnnouncementDetail(announcement.id);
      setCurrentAnnouncement(response?.data || announcement);
      setAnnouncements((prev) => prev.map((item) => (
        item.id === announcement.id ? { ...item, read: true, readAt: response?.data?.readAt || item.readAt } : item
      )));
    } catch (error) {
      console.error('获取公告详情失败:', error);
    } finally {
      setDetailLoading(false);
    }
  };

  // 关闭详情
  const handleCloseDetail = () => {
    setDetailVisible(false);
    setDetailLoading(false);
    setCurrentAnnouncement(null);
  };

  const stripHtml = (html) => {
    if (!html) return '';
    const text = html.replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ').trim();
    return text.length > 60 ? text.slice(0, 60) + '...' : text;
  };

  // 根据分类获取图标
  const getCategoryIcon = (category) => {
    switch (category) {
      case 'MANAGEMENT_NOTICE':
        return <WarningOutlined />;
      case 'COLLEGE_NOTICE':
        return <NotificationOutlined />;
      case 'COMMITTEE_NOTICE':
        return <BellOutlined />;
      default:
        return <BellOutlined />;
    }
  };

  // 根据分类获取标签颜色
  const getCategoryColor = (category) => {
    switch (category) {
      case 'MANAGEMENT_NOTICE':
        return 'red';
      case 'COLLEGE_NOTICE':
        return 'blue';
      case 'COMMITTEE_NOTICE':
        return 'orange';
      default:
        return 'default';
    }
  };

  // 分类显示名称
  const getCategoryName = (category) => {
    switch (category) {
      case 'MANAGEMENT_NOTICE':
        return '管理通知';
      case 'COLLEGE_NOTICE':
        return '学院通知';
      case 'COMMITTEE_NOTICE':
        return '班委通知';
      default:
        return category || '通知';
    }
  };

  if (loading || announcements.length === 0) {
    return null;
  }

  return (
    <div className="message-banner-wrapper">
      <div className="message-banner-header">
        <BellOutlined className="banner-icon" />
        <span className="banner-title">最新公告</span>
        <Badge count={announcements.length} className="banner-badge" />
      </div>

      <div className="message-carousel-container">
        <Carousel
          autoplay
          autoplaySpeed={3000}
          dots={true}
          pauseOnHover
        >
          {announcements.map((announcement, index) => (
            <div key={announcement.id || index} className="carousel-item">
              <div className="carousel-item-content">
                <div className="carousel-item-header">
                  <Tag color={getCategoryColor(announcement.category)} icon={getCategoryIcon(announcement.category)}>
                    {getCategoryName(announcement.category)}
                  </Tag>
                  <span className="carousel-item-time">
                    {new Date(announcement.publishTime || announcement.createdAt).toLocaleString('zh-CN')}
                  </span>
                </div>
                <div className="carousel-item-body">
                  <h4 className="carousel-item-title">{announcement.title}</h4>
                  <p className="carousel-item-text">{announcement.summary || stripHtml(announcement.content)}</p>
                </div>
                <div className="carousel-item-footer">
                  <span className="publisher">发布人: {announcement.publisherName}</span>
                  <Button
                    type="primary"
                    size="small"
                    onClick={(e) => {
                      e.stopPropagation();
                      handleViewDetail(announcement);
                    }}
                  >
                    查看详情
                  </Button>
                </div>
              </div>
            </div>
          ))}
        </Carousel>
      </div>

      {/* 详情面板 */}
      <AnnouncementDetailDrawer
        open={detailVisible}
        announcement={currentAnnouncement}
        loading={detailLoading}
        onClose={handleCloseDetail}
      />
    </div>
  );
}

export default MessageBanner;
