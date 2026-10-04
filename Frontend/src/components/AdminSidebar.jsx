import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { buildAdminMenuItems } from './adminNavigation.jsx';
import { HeartOutlined } from './AcademicIcons';
import sidebarBackground from '../assets/index/back.png';
import { menuRouteMap, resolveMenuIdFromPath } from '../router';
import dropdownArrow from '../assets/index/xiala.png';
import schoolLogo from '../assets/index/school.png';
import './AdminSidebar.css';

const developerCreditsMenuId = 900;

function AdminSidebar({ role, pathname, collapsed = false, onToggleCollapse }) {
  const navigate = useNavigate();
  const menuItems = useMemo(() => buildAdminMenuItems(role), [role]);
  const activeMenuId = useMemo(() => resolveMenuIdFromPath(pathname), [pathname]);
  const isDeveloperCreditsActive = activeMenuId === developerCreditsMenuId;
  const activeParentMenuId = useMemo(() => {
    const matchedParent = menuItems.find((item) => item.children?.some((child) => child.id === activeMenuId));
    return matchedParent?.id || null;
  }, [activeMenuId, menuItems]);
  const [expandedMenuState, setExpandedMenuState] = useState({ pathname, menuId: null });
  const manualExpandedMenuId = expandedMenuState.pathname === pathname ? expandedMenuState.menuId : null;
  const expandedMenuId = manualExpandedMenuId ?? activeParentMenuId;

  const topLevelItems = menuItems.filter((item) => !item.parentId);

  const handleItemClick = (item) => {
    if (item.children?.length) {
      setExpandedMenuState({
        pathname,
        menuId: expandedMenuId === item.id ? null : item.id,
      });
      return;
    }

    const route = menuRouteMap[item.id];
    if (route) {
      navigate(route);
    }
  };

  const handleDeveloperCreditsClick = () => {
    const route = menuRouteMap[developerCreditsMenuId];
    if (route) {
      navigate(route);
    }
  };

  return (
    <aside
      className={`admin-sidebar ${collapsed ? 'collapsed' : ''}`}
      style={{ '--admin-sidebar-bg': `url(${sidebarBackground})` }}
    >
      <div className="admin-sidebar-brand">
        {!collapsed && (
          <div className="admin-sidebar-brand-copy">
            <img src={schoolLogo} alt="学生管理系统" className="admin-sidebar-brand-logo" />
          </div>
        )}
      </div>

      <nav className="admin-sidebar-nav">
        {topLevelItems.map((item) => {
          const hasChildren = Boolean(item.children?.length);
          const isExpanded = expandedMenuId === item.id;

          return (
            <div key={item.id} className="admin-sidebar-group">
              <button
                type="button"
                className={`admin-sidebar-item ${isExpanded ? 'expanded' : ''}`}
                onClick={() => handleItemClick(item)}
                title={collapsed ? item.title : undefined}
              >
                <span className="admin-sidebar-icon">{item.icon}</span>
                {!collapsed && <span className="admin-sidebar-label">{item.title}</span>}
                {!collapsed && hasChildren && (
                  <span className={`admin-sidebar-arrow ${isExpanded ? 'expanded' : ''}`}>
                    <img src={dropdownArrow} alt="" className="admin-sidebar-arrow-icon" />
                  </span>
                )}
              </button>

              {!collapsed && hasChildren && isExpanded && (
                <div className="admin-sidebar-submenu">
                  {item.children.map((child) => (
                    <button
                      key={child.id}
                      type="button"
                      className={`admin-sidebar-subitem ${child.id === activeMenuId ? 'active' : ''}`}
                      onClick={() => handleItemClick(child)}
                    >
                      <span>{child.title}</span>
                    </button>
                  ))}
                </div>
              )}
            </div>
          );
        })}
      </nav>

      <div className="admin-sidebar-footer">
        <button
          type="button"
          className={`admin-sidebar-item admin-sidebar-footer-link ${
            isDeveloperCreditsActive ? 'active' : ''
          }`}
          onClick={handleDeveloperCreditsClick}
          title={collapsed ? '开发者致谢' : undefined}
        >
          <span className="admin-sidebar-icon">
            <HeartOutlined style={{ fontSize: 18 }} />
          </span>
          {!collapsed && <span className="admin-sidebar-label">开发者致谢</span>}
        </button>
      </div>

      <button type="button" className="admin-sidebar-collapse" onClick={onToggleCollapse}>
        <span>{collapsed ? '展开菜单' : '收起菜单'}</span>
      </button>
    </aside>
  );
}

export default AdminSidebar;
