import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import './TopNav.css';

function TopNav({ menuItems, activeItem, onMenuClick, user, onLogout, avatarMenuItems = [] }) {
  const navigate = useNavigate();
  const navRef = useRef(null);
  // 当前展开的一级菜单，保证同一时间只展开一个
  const [expandedMenuId, setExpandedMenuId] = useState(null);
  const [isAvatarMenuOpen, setIsAvatarMenuOpen] = useState(false);

  // 波纹效果状态
  const [rippleItems, setRippleItems] = useState({});
  const rippleTimersRef = useRef({});

  // 根据id排序菜单项
  const sortedMenuItems = [...menuItems].sort((a, b) => (a.id || 0) - (b.id || 0));

  useEffect(() => {
    const handleDocumentClick = (event) => {
      if (navRef.current && !navRef.current.contains(event.target)) {
        setExpandedMenuId(null);
        setIsAvatarMenuOpen(false);
      }
    };

    document.addEventListener('mousedown', handleDocumentClick);
    return () => {
      document.removeEventListener('mousedown', handleDocumentClick);
    };
  }, []);

  // 切换子菜单展开状态
  const toggleSubmenu = (itemId, e) => {
    e.stopPropagation();
    setExpandedMenuId(prev => (prev === itemId ? null : itemId));
  };

  // 处理菜单点击 - 添加波纹效果
  const handleMenuClick = (item, index, e) => {
    // 触发波纹效果
    const itemId = item.id || index;
    setRippleItems(prev => ({ ...prev, [itemId]: true }));

    // 清除之前的定时器
    if (rippleTimersRef.current[itemId]) {
      clearTimeout(rippleTimersRef.current[itemId]);
    }

    // 设置新的定时器移除波纹效果
    rippleTimersRef.current[itemId] = setTimeout(() => {
      setRippleItems(prev => ({ ...prev, [itemId]: false }));
    }, 400);

    const hasChildren = menuItems.some(child => child.parentId === item.id);
    if (hasChildren) {
      // 有子菜单时，切换展开状态
      toggleSubmenu(item.id, e);
    } else {
      // 没有子菜单时，触发点击回调
      setIsAvatarMenuOpen(false);
      onMenuClick && onMenuClick(index, item);
    }
  };

  // 处理子菜单点击
  const handleSubmenuClick = (parentItem, childItem, e) => {
    e.stopPropagation();

    // 触发子菜单波纹效果
    const childId = childItem.id;
    setRippleItems(prev => ({ ...prev, [childId]: true }));

    if (rippleTimersRef.current[childId]) {
      clearTimeout(rippleTimersRef.current[childId]);
    }

    rippleTimersRef.current[childId] = setTimeout(() => {
      setRippleItems(prev => ({ ...prev, [childId]: false }));
    }, 400);

    const childIndex = menuItems.findIndex(m => m.id === childItem.id);
    if (childIndex !== -1) {
      setExpandedMenuId(null);
      setIsAvatarMenuOpen(false);
      onMenuClick && onMenuClick(childIndex, childItem);
    }
  };

  const handleAvatarClick = () => {
    if (!avatarMenuItems.length) {
      return;
    }
    setExpandedMenuId(null);
    setIsAvatarMenuOpen((prev) => !prev);
  };

  const handleAvatarMenuItemClick = (item) => {
    setIsAvatarMenuOpen(false);
    if (item?.path) {
      navigate(item.path);
      return;
    }
    if (item?.action === 'logout') {
      onLogout && onLogout();
    }
  };

  const displayName = user?.nickname || user?.username || '用户';
  const avatarText = displayName.trim().charAt(0).toUpperCase() || 'U';

  return (
    <nav className="top-nav" ref={navRef}>
      <div className="top-nav-logo">
        <h1>成绩管理系统</h1>
      </div>
      <div className="top-nav-menu">
        {sortedMenuItems.map((item, sortedIndex) => {
          // 跳过子菜单项，它们会在父菜单下渲染
          if (item.parentId) return null;

          // 获取该项在原始数组中的真实索引
          const originalIndex = menuItems.findIndex(m => m.id === item.id);

          const isExpanded = expandedMenuId === item.id;
          const hasChildren = menuItems.some(child => child.parentId === item.id);
          const isActive = activeItem === originalIndex;
          const hasActiveChild = hasChildren && menuItems.some(
            child => child.parentId === item.id && menuItems.findIndex(m => m.id === child.id) === activeItem
          );
          const isTopLevelActive = isActive || hasActiveChild;
          const hasRipple = rippleItems[item.id];

          return (
            <div key={item.id || sortedIndex} className="menu-item-wrapper">
              <div
                className={`nav-item ${isTopLevelActive ? 'active' : ''} ${hasActiveChild ? 'has-active-child' : ''} ${hasRipple ? 'ripple' : ''}`}
                onClick={(e) => handleMenuClick(item, originalIndex, e)}
              >
                <span className="nav-icon">{item.icon}</span>
                <span className="nav-title">{item.title}</span>
                {hasChildren && (
                  <span className={`nav-arrow ${isExpanded ? 'expanded' : ''}`}>
                    ▼
                  </span>
                )}
              </div>

              {/* 子菜单 */}
              {hasChildren && isExpanded && (
                <div className="submenu">
                  {menuItems.filter(child => child.parentId === item.id).map((child) => {
                    const childIndex = menuItems.findIndex(m => m.id === child.id);
                    const isChildActive = activeItem === childIndex;
                    const childHasRipple = rippleItems[child.id];

                    return (
                      <div
                        key={child.id}
                        className={`submenu-item ${isChildActive ? 'active' : ''} ${childHasRipple ? 'ripple' : ''}`}
                        onClick={(e) => handleSubmenuClick(item, child, e)}
                      >
                        <span className="submenu-icon">{child.icon || '•'}</span>
                        <span className="nav-title">{child.title}</span>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          );
        })}
      </div>
      <div className="top-nav-user">
        <div className="top-nav-user-menu">
          <div className="top-nav-avatar" onClick={handleAvatarClick} title={avatarMenuItems.length ? '打开用户菜单' : '用户头像'}>{avatarText}</div>
          {avatarMenuItems.length && isAvatarMenuOpen && (
            <div className="top-nav-user-dropdown">
              {avatarMenuItems.map((item) => (
                <button
                  key={item.key || item.path || item.title}
                  type="button"
                  className="top-nav-user-dropdown-item"
                  onClick={() => handleAvatarMenuItemClick(item)}
                >
                  <span className="top-nav-user-dropdown-icon">{item.icon || '•'}</span>
                  <span>{item.title}</span>
                </button>
              ))}
            </div>
          )}
        </div>
        <span className="top-nav-name">{displayName}</span>
        {!avatarMenuItems.length && (
          <button className="top-nav-logout" onClick={onLogout}>退出登录</button>
        )}
      </div>
    </nav>
  );
}

export default TopNav;
