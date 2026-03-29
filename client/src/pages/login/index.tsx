import { useState, useEffect } from 'react';
import { Card, Form, Input, Button, message, Typography, Divider, Alert, Select, Space, Tag } from 'antd';
import { PhoneOutlined, LockOutlined, UserOutlined, MailOutlined, ExclamationCircleOutlined, GlobalOutlined, PlusOutlined, CloseOutlined } from '@ant-design/icons';
import { authApi } from '../../services/api';

const { Title, Text, Link } = Typography;
const { Option } = Select;

interface PublicConfig {
  allowRegister: boolean;
  registerDefaultRole: string;
}

interface ServerAddress {
  url: string;
  name: string;
  lastUsed: number;
}

// 服务器地址存储键
const SERVER_ADDRESSES_KEY = 'server_addresses';
const CURRENT_SERVER_KEY = 'current_server';

export default function LoginPage() {
  const [loading, setLoading] = useState(false);
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [config, setConfig] = useState<PublicConfig | null>(null);
  const [loginError, setLoginError] = useState<string | null>(null);
  const [form] = Form.useForm();
  
  // 服务器地址相关状态
  const [serverAddresses, setServerAddresses] = useState<ServerAddress[]>([]);
  const [currentServer, setCurrentServer] = useState<string>('');
  const [showAddServer, setShowAddServer] = useState(false);
  const [newServerUrl, setNewServerUrl] = useState('');
  const [newServerName, setNewServerName] = useState('');

  useEffect(() => {
    // 获取公开配置
    authApi.getPublicConfig()
      .then(res => setConfig(res.data))
      .catch(() => {
        // 默认允许注册
        setConfig({ allowRegister: true, registerDefaultRole: 'agent' });
      });
    
    // 加载保存的服务器地址
    loadServerAddresses();
  }, []);

  // 加载服务器地址列表
  const loadServerAddresses = () => {
    try {
      const saved = localStorage.getItem(SERVER_ADDRESSES_KEY);
      const addresses: ServerAddress[] = saved ? JSON.parse(saved) : [];
      
      // 添加默认地址（如果没有）
      const hasDefault = addresses.some(s => s.url === '/api' || s.url === '');
      if (!hasDefault) {
        addresses.unshift({
          url: '',
          name: '默认服务器',
          lastUsed: Date.now()
        });
      }
      
      setServerAddresses(addresses);
      
      // 获取当前选中的服务器
      const current = localStorage.getItem(CURRENT_SERVER_KEY);
      if (current !== null) {
        setCurrentServer(current);
      } else if (addresses.length > 0) {
        setCurrentServer(addresses[0].url);
      }
    } catch (e) {
      console.error('加载服务器地址失败:', e);
    }
  };

  // 保存服务器地址列表
  const saveServerAddresses = (addresses: ServerAddress[]) => {
    try {
      localStorage.setItem(SERVER_ADDRESSES_KEY, JSON.stringify(addresses));
      setServerAddresses(addresses);
    } catch (e) {
      console.error('保存服务器地址失败:', e);
    }
  };

  // 切换服务器
  const handleServerChange = (value: string) => {
    setCurrentServer(value);
    localStorage.setItem(CURRENT_SERVER_KEY, value);
    
    // 更新最后使用时间
    const updated = serverAddresses.map(s => 
      s.url === value ? { ...s, lastUsed: Date.now() } : s
    );
    saveServerAddresses(updated);
    
    // 刷新页面以应用新的服务器地址
    window.location.reload();
  };

  // 添加新服务器
  const handleAddServer = () => {
    if (!newServerUrl.trim()) {
      message.error('请输入服务器地址');
      return;
    }
    
    // 规范化 URL
    let url = newServerUrl.trim();
    if (!url.startsWith('http://') && !url.startsWith('https://')) {
      url = 'http://' + url;
    }
    
    // 检查是否已存在
    if (serverAddresses.some(s => s.url === url)) {
      message.error('该服务器地址已存在');
      return;
    }
    
    const newServer: ServerAddress = {
      url,
      name: newServerName.trim() || url,
      lastUsed: Date.now()
    };
    
    const updated = [...serverAddresses, newServer];
    saveServerAddresses(updated);
    
    // 选中新添加的服务器
    setCurrentServer(url);
    localStorage.setItem(CURRENT_SERVER_KEY, url);
    
    // 重置表单
    setNewServerUrl('');
    setNewServerName('');
    setShowAddServer(false);
    
    message.success('服务器地址已添加');
    
    // 刷新页面以应用新的服务器地址
    window.location.reload();
  };

  // 删除服务器地址
  const handleDeleteServer = (url: string, e: React.MouseEvent) => {
    e.stopPropagation();
    
    const updated = serverAddresses.filter(s => s.url !== url);
    saveServerAddresses(updated);
    
    // 如果删除的是当前选中的，切换到第一个
    if (currentServer === url && updated.length > 0) {
      setCurrentServer(updated[0].url);
      localStorage.setItem(CURRENT_SERVER_KEY, updated[0].url);
      window.location.reload();
    }
  };

  const handleLogin = async (values: { username: string; password: string }) => {
    setLoading(true);
    setLoginError(null);
    
    try {
      const response = await authApi.login(values.username, values.password);
      const { token, user } = response.data;
      
      // 保存到 localStorage
      localStorage.setItem('token', token);
      localStorage.setItem('user', JSON.stringify(user));
      
      message.success('登录成功，正在跳转...');
      
      // 强制刷新跳转到首页
      setTimeout(() => {
        window.location.href = '/';
      }, 500);
      
    } catch (error: any) {
      console.error('登录错误:', error);
      const errorMsg = error.response?.data?.error || '登录失败，请稍后重试';
      setLoginError(errorMsg);
      message.error(errorMsg);
    } finally {
      setLoading(false);
    }
  };

  const handleRegister = async (values: any) => {
    setLoading(true);
    setLoginError(null);
    
    try {
      const response = await authApi.register({
        username: values.username,
        password: values.password,
        real_name: values.real_name,
        phone: values.phone,
        email: values.email
      });
      
      const { token, user } = response.data;
      
      // 保存到 localStorage
      localStorage.setItem('token', token);
      localStorage.setItem('user', JSON.stringify(user));
      
      message.success('注册成功，正在跳转...');
      
      // 跳转到首页
      setTimeout(() => {
        window.location.href = '/';
      }, 500);
      
    } catch (error: any) {
      console.error('注册错误:', error);
      const errorMsg = error.response?.data?.error || '注册失败，请稍后重试';
      setLoginError(errorMsg);
      message.error(errorMsg);
    } finally {
      setLoading(false);
    }
  };

  const switchMode = () => {
    setMode(mode === 'login' ? 'register' : 'login');
    setLoginError(null);
    form.resetFields();
  };

  return (
    <div style={{ 
      minHeight: '100vh', 
      display: 'flex', 
      alignItems: 'center', 
      justifyContent: 'center',
      background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)'
    }}>
      <Card style={{ width: 420, borderRadius: 8, boxShadow: '0 4px 20px rgba(0,0,0,0.1)' }}>
        <div style={{ textAlign: 'center', marginBottom: 32 }}>
          <Title level={3} style={{ margin: 0, color: '#1890ff' }}>
            <PhoneOutlined /> 自动拨号客服系统
          </Title>
          <p style={{ color: '#666', marginTop: 8 }}>
            {mode === 'login' ? '请登录以继续使用' : '创建新账号'}
          </p>
        </div>
        
        {/* 错误提示 */}
        {loginError && (
          <Alert
            message={loginError}
            type="error"
            showIcon
            icon={<ExclamationCircleOutlined />}
            style={{ marginBottom: 16 }}
            closable
            onClose={() => setLoginError(null)}
          />
        )}
        
        <Form
          form={form}
          name={mode}
          onFinish={mode === 'login' ? handleLogin : handleRegister}
          autoComplete="off"
          size="large"
        >
          {/* 服务器地址选择 */}
          <Form.Item>
            <div style={{ marginBottom: 8, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <Text type="secondary"><GlobalOutlined /> 服务器地址</Text>
              <Button 
                type="link" 
                size="small" 
                onClick={() => setShowAddServer(!showAddServer)}
                icon={<PlusOutlined />}
              >
                {showAddServer ? '取消' : '添加'}
              </Button>
            </div>
            
            {showAddServer ? (
              <Space direction="vertical" style={{ width: '100%' }}>
                <Input
                  placeholder="服务器名称（选填）"
                  value={newServerName}
                  onChange={(e) => setNewServerName(e.target.value)}
                />
                <Space style={{ width: '100%' }}>
                  <Input
                    placeholder="服务器地址，如：192.168.1.100:8081"
                    value={newServerUrl}
                    onChange={(e) => setNewServerUrl(e.target.value)}
                    style={{ width: 280 }}
                  />
                  <Button type="primary" onClick={handleAddServer}>
                    添加
                  </Button>
                </Space>
                <Text type="secondary" style={{ fontSize: 12 }}>
                  提示：输入 IP:端口 或完整 URL，如 http://192.168.1.100:8081
                </Text>
              </Space>
            ) : (
              <Select
                value={currentServer}
                onChange={handleServerChange}
                style={{ width: '100%' }}
                placeholder="选择服务器"
              >
                {serverAddresses.map((server) => (
                  <Option key={server.url} value={server.url}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <span>
                        {server.name}
                        {server.url && (
                          <Tag color="default" style={{ marginLeft: 8, fontSize: 12 }}>
                            {server.url}
                          </Tag>
                        )}
                      </span>
                      {server.url !== '' && (
                        <CloseOutlined
                          style={{ color: '#999', fontSize: 12 }}
                          onClick={(e) => handleDeleteServer(server.url, e)}
                        />
                      )}
                    </div>
                  </Option>
                ))}
              </Select>
            )}
          </Form.Item>

          <Form.Item
            name="username"
            rules={[
              { required: true, message: '请输入用户名' },
              { min: 3, max: 20, message: '用户名长度需在3-20个字符之间' }
            ]}
          >
            <Input 
              prefix={<UserOutlined />} 
              placeholder="用户名" 
            />
          </Form.Item>

          <Form.Item
            name="password"
            rules={[
              { required: true, message: '请输入密码' },
              { min: 6, message: '密码长度至少6个字符' }
            ]}
          >
            <Input.Password 
              prefix={<LockOutlined />} 
              placeholder="密码" 
            />
          </Form.Item>

          {mode === 'register' && (
            <>
              <Form.Item
                name="confirmPassword"
                dependencies={['password']}
                rules={[
                  { required: true, message: '请确认密码' },
                  ({ getFieldValue }) => ({
                    validator(_, value) {
                      if (!value || getFieldValue('password') === value) {
                        return Promise.resolve();
                      }
                      return Promise.reject(new Error('两次输入的密码不一致'));
                    },
                  }),
                ]}
              >
                <Input.Password 
                  prefix={<LockOutlined />} 
                  placeholder="确认密码" 
                />
              </Form.Item>

              <Form.Item
                name="real_name"
              >
                <Input 
                  prefix={<UserOutlined />} 
                  placeholder="姓名（选填）" 
                />
              </Form.Item>

              <Form.Item
                name="phone"
              >
                <Input 
                  prefix={<PhoneOutlined />} 
                  placeholder="手机号（选填）" 
                />
              </Form.Item>

              <Form.Item
                name="email"
              >
                <Input 
                  prefix={<MailOutlined />} 
                  placeholder="邮箱（选填）" 
                />
              </Form.Item>
            </>
          )}

          <Form.Item>
            <Button 
              type="primary" 
              htmlType="submit" 
              loading={loading}
              block
            >
              {mode === 'login' ? '登录' : '注册'}
            </Button>
          </Form.Item>
        </Form>

        {config?.allowRegister && (
          <>
            <Divider style={{ margin: '16px 0' }} />
            <div style={{ textAlign: 'center' }}>
              <Text type="secondary">
                {mode === 'login' ? '还没有账号？' : '已有账号？'}
              </Text>
              <Link onClick={switchMode} style={{ marginLeft: 4 }}>
                {mode === 'login' ? '立即注册' : '返回登录'}
              </Link>
            </div>
          </>
        )}
      </Card>
    </div>
  );
}
