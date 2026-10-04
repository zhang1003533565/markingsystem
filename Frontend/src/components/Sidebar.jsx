import { useState } from 'react';
import './Sidebar.css';

function Sidebar({ menuItems, activeItem, onMenuClick }) {
  // 展开的一级菜单状态
  const [expandedMenus, setExpandedMenus] = useState(() => {
    // 默认展开包含活跃项的父菜单
    const initialExpanded = {};
    menuItems.forEach((item, index) => {
      if (item.children) {
        const hasActiveChild = item.children.some(
          child => menuItems.findIndex(m => m.id === child.id) === activeItem
        );
        if (hasActiveChild) {
          initialExpanded[item.id] = true;
        }
      }
    });
    return initialExpanded;
  });

  // 根据id排序菜单项
  const sortedMenuItems = [...menuItems].sort((a, b) => (a.id || 0) - (b.id || 0));

  // 切换子菜单展开状态
  const toggleSubmenu = (itemId, e) => {
    e.stopPropagation();
    setExpandedMenus(prev => ({
      ...prev,
      [itemId]: !prev[itemId]
    }));
  };

  // 处理菜单点击
  const handleMenuClick = (item, index, e) => {
    if (item.children && item.children.length > 0) {
      // 有子菜单时，切换展开状态
      toggleSubmenu(item.id, e);
    } else {
      // 没有子菜单时，触发点击回调
      onMenuClick && onMenuClick(index, item);
    }
  };

  // 处理子菜单点击
  const handleSubmenuClick = (parentItem, childItem, e) => {
    e.stopPropagation();
    const childIndex = menuItems.findIndex(m => m.id === childItem.id);
    if (childIndex !== -1) {
      onMenuClick && onMenuClick(childIndex, childItem);
    }
  };

  return (
    <aside className="sidebar">
      <div className="sidebar-logo">
        <h1>成绩管理系统</h1>
      </div>
      <nav className="sidebar-nav">
        {sortedMenuItems.map((item, sortedIndex) => {
          // 跳过子菜单项，它们会在父菜单下渲染
          if (item.parentId) return null;

          // 获取该项在原始数组中的真实索引
          const originalIndex = menuItems.findIndex(m => m.id === item.id);
          
          const isExpanded = expandedMenus[item.id];
          const hasChildren = item.children && item.children.length > 0;
          const isActive = activeItem === originalIndex;
          const hasActiveChild = hasChildren && item.children.some(
            child => menuItems.findIndex(m => m.id === child.id) === activeItem
          );

          return (
            <div key={item.id || sortedIndex} className="menu-item-wrapper">
              <div
                className={`nav-item ${isActive ? 'active' : ''} ${hasActiveChild ? 'has-active-child' : ''}`}
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
                  {item.children.map((child) => {
                    const childIndex = menuItems.findIndex(m => m.id === child.id);
                    const isChildActive = activeItem === childIndex;
                    
                    return (
                      <div
                        key={child.id}
                        className={`submenu-item ${isChildActive ? 'active' : ''}`}
                        onClick={(e) => handleSubmenuClick(item, child, e)}
                      >
                        <span className="submenu-icon">{child.icon || '•'}</span>
                        <span>{child.title}</span>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          );
        })}
      </nav>
    </aside>
  );
}

export default Sidebar;