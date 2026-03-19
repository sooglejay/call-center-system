import { useState, useEffect } from 'react';
import { Card, Form, Input, Button, message, Typography, Divider, Alert } from 'antd';
import { PhoneOutlined, LockOutlined, UserOutlined, MailOutlined, ExclamationCircleOutlined } from '@ant-design/icons';
import { authApi } from '../../services/api';

const { Title, Text, Link } = Typography;

interface PublicConfig {
  allowRegister: boolean;
  registerDefaultRole: string;
}

export default function LoginPage() {
  const [loading, setLoading] = useState(false);
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [config, setConfig] = useState<PublicConfig | null>(null);
  const [loginError, setLoginError] = useState<string | null>(null);
  const [form] = Form.useForm();

  useEffect(() => {
    // 获取公开配置
    authApi.getPublicConfig()
      .then(res => setConfig(res.data))
      .catch(() => {
        // 默认允许注册
        setConfig({ allowRegister: true, registerDefaultRole: 'agent' });
      });
  }, []);

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
        
        {mode === 'login' && (
          <div style={{ textAlign: 'center', color: '#999', fontSize: 12, marginTop: 16 }}>
            <p style={{ margin: 0 }}>默认管理员账号：admin / admin123</p>
            <p style={{ margin: 0 }}>默认客服账号：agent / agent123</p>
          </div>
        )}
        
        {mode === 'register' && config?.registerDefaultRole && (
          <div style={{ textAlign: 'center', color: '#999', fontSize: 12, marginTop: 16 }}>
            <p style={{ margin: 0 }}>注册账号默认角色：{config.registerDefaultRole === 'agent' ? '客服' : config.registerDefaultRole}</p>
          </div>
        )}
      </Card>
    </div>
  );
}
