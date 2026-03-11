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
  MessageOutlined,
  AudioOutlined
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
    navigate('/login');
  };

  const menuItems = [
    { key: '/dashboard', icon: <DashboardOutlined />, label: '工作台' },
    { key: '/calls', icon: <PhoneOutlined />, label: '电话列表' },
    { key: '/communication', icon: <AudioOutlined />, label: '通信记录' },
    { key: '/stats', icon: <BarChartOutlined />, label: '我的业绩' },
    { key: '/settings', icon: <SettingOutlined />, label: '拨号设置' },
  ];

  const userMenuItems = [
    { key: 'profile', label: '个人设置' },
    { key: 'logout', label: '退出登录', icon: <LogoutOutlined />, danger: true }
  ];

  const handleUserMenuClick = ({ key }: { key: string }) => {
    if (key === 'logout') {
      handleLogout();
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
            icon={collapsed ? <span>展开</span> : <span>收起</span>}
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
