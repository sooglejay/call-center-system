import { useState } from 'react';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import { Layout, Menu, Button, Dropdown, Avatar, Badge, message } from 'antd';
import {
  DashboardOutlined,
  PhoneOutlined,
  BarChartOutlined,
  SettingOutlined,
  LogoutOutlined,
  DownOutlined,
  CustomerServiceOutlined,
  AudioOutlined,
  QuestionCircleOutlined,
  ScheduleOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  AndroidOutlined
} from '@ant-design/icons';
import { useAuthStore } from '../../stores';

const { Header, Sider, Content } = Layout;

export default function AgentLayout() {
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
    { key: '/agent/dashboard', icon: <DashboardOutlined />, label: '工作台' },
    { key: '/agent/tasks', icon: <ScheduleOutlined />, label: '我的任务' },
    { key: '/agent/calls', icon: <PhoneOutlined />, label: '电话列表' },
    { key: '/agent/communication', icon: <AudioOutlined />, label: '通信记录' },
    { key: '/agent/stats', icon: <BarChartOutlined />, label: '我的业绩' },
    { key: '/agent/download', icon: <AndroidOutlined />, label: 'App下载' },
    { key: '/agent/settings', icon: <SettingOutlined />, label: '拨号设置' },
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
          {collapsed ? <CustomerServiceOutlined /> : '客服工作台'}
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
              <Badge dot color="green">
                <Avatar icon={<CustomerServiceOutlined />} />
              </Badge>
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
