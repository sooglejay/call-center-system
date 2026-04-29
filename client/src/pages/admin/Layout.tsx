import { useState } from 'react';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import { Layout, Menu, Button, Dropdown, Avatar, message } from 'antd';
import {
  DashboardOutlined,
  TeamOutlined,
  UserOutlined,
  FileTextOutlined,
  BarChartOutlined,
  SettingOutlined,
  LogoutOutlined,
  DownOutlined,
  PhoneOutlined,
  DatabaseOutlined,
  QuestionCircleOutlined,
  MobileOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined
} from '@ant-design/icons';
import { useAuthStore } from '../../stores';

const { Header, Sider, Content } = Layout;

export default function AdminLayout() {
  const [collapsed, setCollapsed] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();
  const { user, clearAuth } = useAuthStore();

  const handleLogout = () => {
    clearAuth();
    message.success('已退出登录');
    // 使用 replace 避免后退按钮回到已登录页面
    navigate('/login', { replace: true });
    // 强制刷新确保状态完全清除
    window.location.reload();
  };

  const menuItems = [
    { key: '/admin/dashboard', icon: <DashboardOutlined />, label: '仪表板' },
    { key: '/admin/users', icon: <TeamOutlined />, label: '人员管理' },
    { key: '/admin/customers', icon: <UserOutlined />, label: '客户管理' },
    { key: '/admin/tasks', icon: <FileTextOutlined />, label: '任务分配' },
    { key: '/admin/stats', icon: <BarChartOutlined />, label: '监控统计' },
    { key: '/admin/data-permission', icon: <DatabaseOutlined />, label: '数据权限' },
    { key: '/admin/version', icon: <MobileOutlined />, label: 'App版本' },
    { key: '/admin/logs', icon: <FileTextOutlined />, label: '日志管理' },
    { key: '/admin/config', icon: <SettingOutlined />, label: '系统配置' },
    { key: '/admin/twilio-test', icon: <PhoneOutlined />, label: 'Twilio测试' },
  ];

  const userMenuItems = [
    { key: 'profile', label: '个人设置' },
    { key: 'help', label: '使用说明', icon: <QuestionCircleOutlined /> },
    { key: 'logout', label: '退出登录', icon: <LogoutOutlined />, danger: true }
  ];

  const handleUserMenuClick = ({ key }: { key: string }) => {
    if (key === 'logout') {
      handleLogout();
    } else if (key === 'help') {
      window.open('/help', '_blank');
    }
  };

  return (
    <Layout>
      <Sider trigger={null} collapsible collapsed={collapsed} theme="dark">
        <div style={{ height: 64, display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#fff', fontSize: 18, fontWeight: 'bold' }}>
          {collapsed ? '客服' : '客服系统'}
        </div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[location.pathname]}
          items={menuItems}
          onClick={({ key }) => navigate(key)}
        />
      </Sider>
      <Layout>
        <Header style={{ background: '#fff', padding: '0 24px', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <Button
            type="text"
            icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
            onClick={() => setCollapsed(!collapsed)}
          />
          <Dropdown menu={{ items: userMenuItems, onClick: handleUserMenuClick }} placement="bottomRight">
            <div style={{ cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 8 }}>
              <Avatar icon={<UserOutlined />} />
              <span>{user?.real_name || user?.username}</span>
              <DownOutlined />
            </div>
          </Dropdown>
        </Header>
        <Content style={{ margin: 24, padding: 24, background: '#fff', borderRadius: 4, minHeight: 280 }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}
