import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import TopNav from '../components/TopNav';
import TopBar from '../components/TopBar';
import AppIcon from '../components/AppIcon';
import { clearStoredUser, getStoredUser } from '../utils/authStorage';
import './Home.css';

function Home() {
  const navigate = useNavigate();
  const user = getStoredUser();
  const [activeMenu, setActiveMenu] = useState(0);

  const handleLogout = () => {
    clearStoredUser();
    navigate('/login', { replace: true });
  };

  const menuItems = [
    { id: 1, icon: <AppIcon name="home" />, title: '首页' },
    { id: 2, icon: <AppIcon name="scores" />, title: '评分管理', desc: '查看和管理评分数据' },
    { id: 3, icon: <AppIcon name="users" />, title: '用户管理', desc: '管理系统用户信息' },
    { id: 4, icon: <AppIcon name="college" />, title: '项目管理', desc: '管理比赛项目信息' },
    { id: 5, icon: <AppIcon name="analysis" />, title: '数据统计', desc: '查看评分统计报表' },
    { id: 6, icon: <AppIcon name="college" />, title: '系统设置', desc: '配置系统参数' },
    { id: 7, icon: <AppIcon name="review" />, title: '日志记录', desc: '查看系统操作日志' },
  ];

  const handleMenuClick = (index) => {
    setActiveMenu(index);
  };

  return (
    <div className="home-container">
      {/* 顶部导航栏 */}
      <TopNav 
        menuItems={menuItems} 
        activeItem={activeMenu} 
        onMenuClick={handleMenuClick}
      />

      {/* 主内容区 */}
      <div className="main-wrapper">
        {/* 顶部栏 */}
        <TopBar 
          user={user} 
          onLogout={handleLogout}
          breadcrumb="首页"
        />

        {/* 主内容 */}
        <main className="main-content">
          <div className="welcome-section">
            <h2>欢迎使用评分管理系统</h2>
            <p>请选择左侧功能模块开始使用</p>
          </div>

          {/* 通知栏目 */}
          <div className="notifications-section">
            <div className="notifications-header">
              <h3><AppIcon name="announcement" size={22} /> 最新通知</h3>
              <a href="#" className="view-all">查看全部</a>
            </div>
            <div className="notifications-list">
              <div className="notification-item">
                <span className="notification-dot"></span>
                <div className="notification-content">
                  <p className="notification-text">2026年计算机设计大赛作品提交截止日期为5月15日</p>
                  <span className="notification-time">2026-04-08</span>
                </div>
              </div>
              <div className="notification-item">
                <span className="notification-dot"></span>
                <div className="notification-content">
                  <p className="notification-text">系统将于4月12日凌晨2点进行维护升级，预计耗时2小时</p>
                  <span className="notification-time">2026-04-07</span>
                </div>
              </div>
              <div className="notification-item">
                <span className="notification-dot"></span>
                <div className="notification-content">
                  <p className="notification-text">新增评审专家评分功能，请及时完善个人信息</p>
                  <span className="notification-time">2026-04-05</span>
                </div>
              </div>
            </div>
          </div>

          <div className="stats-section">
            <h3>系统概览</h3>
            <div className="stats-grid">
              <div className="stat-card">
                <div className="stat-number">128</div>
                <div className="stat-label">参赛项目</div>
              </div>
              <div className="stat-card">
                <div className="stat-number">45</div>
                <div className="stat-label">评审专家</div>
              </div>
              <div className="stat-card">
                <div className="stat-number">2560</div>
                <div className="stat-label">评分记录</div>
              </div>
              <div className="stat-card">
                <div className="stat-number">98%</div>
                <div className="stat-label">完成率</div>
              </div>
            </div>
          </div>

          <div className="quick-actions">
            <h3>快捷操作</h3>
            <div className="action-grid">
              {menuItems.slice(0, 4).map((item, index) => (
                <div key={index} className="action-card">
                  <span className="action-icon">{item.icon}</span>
                  <span className="action-title">{item.title}</span>
                </div>
              ))}
            </div>
          </div>
        </main>

        <footer className="home-footer">
          <p>© 2026 成绩管理系统</p>
        </footer>
      </div>
    </div>
  );
}

export default Home;
