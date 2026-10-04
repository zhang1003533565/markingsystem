import { Drawer, Tag, Space, Divider, Image, Button, Spin } from 'antd';
import { BellOutlined, NotificationOutlined, WarningOutlined, LinkOutlined } from './AcademicIcons';
import AppIcon from './AppIcon';
import AnnouncementArticle from './AnnouncementArticle';
import { withAuthToken } from '../utils/authStorage';

// 分类图标
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

// 分类标签颜色
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

function AnnouncementDetailDrawer({ open, announcement, loading = false, onClose, width = 720 }) {
  return (
    <Drawer
      title={
        <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
          <Tag
            color={getCategoryColor(announcement?.category)}
            icon={getCategoryIcon(announcement?.category)}
          >
            {getCategoryName(announcement?.category)}
          </Tag>
          <span style={{ fontWeight: 600 }}>公告详情</span>
        </div>
      }
      placement="right"
      width={width}
      onClose={onClose}
      open={open}
      className="announcement-detail-drawer"
    >
      {loading ? (
        <div style={{ display: 'flex', justifyContent: 'center', padding: 48 }}>
          <Spin />
        </div>
      ) : announcement && (
        <div className="detail-content">
          {/* 标题 */}
          <div className="detail-header">
            <h2 className="detail-title">{announcement.title}</h2>
            <div className="detail-meta">
              <span className="meta-item">
                <strong>发布人:</strong> {announcement.publisherName}
              </span>
              <span className="meta-item">
                <strong>发布时间:</strong> {new Date(announcement.publishTime || announcement.createdAt).toLocaleString('zh-CN')}
              </span>
            </div>
          </div>

          <Divider />

          {/* 公告内容 */}
          <div className="detail-section">
            <h3 className="section-title"><AppIcon name="review" size={20} /> 公告内容</h3>
            <AnnouncementArticle
              announcement={{
                ...announcement,
                publishTime: new Date(announcement.publishTime || announcement.createdAt).toLocaleString('zh-CN')
              }}
              className="section-content"
            />
          </div>

          {announcement.image ? (
            <div className="detail-section">
              <h3 className="section-title"><AppIcon name="import" size={20} /> 相关图片</h3>
              <div className="image-gallery">
                <Image
                  width="100%"
                  src={withAuthToken(announcement.image)}
                  alt="公告图片"
                  style={{ borderRadius: '8px' }}
                  fallback="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mN8/5+hHgAHggJ/PchI7wAAAABJRU5ErkJggg=="
                />
              </div>
            </div>
          ) : null}

          {announcement.video ? (
            <div className="detail-section">
              <h3 className="section-title"><AppIcon name="announcement" size={20} /> 相关视频</h3>
              <div className="image-gallery">
                <video
                  controls
                  className="detail-video"
                  src={withAuthToken(announcement.video)}
                  style={{ width: '100%', borderRadius: '8px' }}
                />
              </div>
            </div>
          ) : null}

          {/* 链接扩展 */}
          <div className="detail-section">
            <h3 className="section-title"><AppIcon name="review" size={20} /> 相关链接</h3>
            {announcement.link ? (
              <div className="link-section">
                <Button
                  type="link"
                  icon={<LinkOutlined />}
                  href={announcement.link}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="detail-link"
                >
                  点击查看详情
                </Button>
              </div>
            ) : (
              <div className="link-placeholder">
                <p className="placeholder-text">暂无相关链接</p>
                <p className="placeholder-hint">此公告未附加外部链接</p>
              </div>
            )}
          </div>

          <Divider />

          {/* 统计信息 - 所有角色可见（含学生/班委） */}
          <div className="detail-stats">
            <Space size="large">
              <div className="stat-item">
                <div className="stat-label">阅读人数</div>
                <div className="stat-value">{announcement.readCount || 0}</div>
              </div>
              <div className="stat-item">
                <div className="stat-label">未读人数</div>
                <div className="stat-value">{announcement.unreadCount || 0}</div>
              </div>
              <div className="stat-item">
                <div className="stat-label">总人数</div>
                <div className="stat-value">{announcement.recipientCount || 0}</div>
              </div>
            </Space>
          </div>
        </div>
      )}
    </Drawer>
  );
}

export default AnnouncementDetailDrawer;
