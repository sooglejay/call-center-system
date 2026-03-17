import { useEffect, useState } from 'react';
import {
  Card, Button, Input, Form, message, Alert, Spin, Tag, Descriptions,
  Table, Tabs, Typography, Space, Divider, Result
} from 'antd';
import {
  PhoneOutlined, MailOutlined, ReloadOutlined, SettingOutlined
} from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { twilioApi } from '../../services/api';

const { Title, Text, Paragraph } = Typography;
const { TabPane } = Tabs;

interface TwilioConfig {
  configured: boolean;
  hasAccountSid: boolean;
  hasAuthToken: boolean;
  hasPhoneNumber: boolean;
  hasCallbackUrl: boolean;
  phoneNumber: string | null;
}

interface TestResult {
  success: boolean;
  data?: any;
  error?: string;
  code?: string;
}

interface SmsRecord {
  id: number;
  customer_phone: string;
  sms_content: string;
  twilio_message_sid: string;
  sms_status: string;
  created_at: string;
}

export default function TwilioTest() {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);
  const [config, setConfig] = useState<TwilioConfig | null>(null);
  const [connectionResult, setConnectionResult] = useState<TestResult | null>(null);
  const [smsForm] = Form.useForm();
  const [callForm] = Form.useForm();
  const [smsRecords, setSmsRecords] = useState<SmsRecord[]>([]);
  const [testingSms, setTestingSms] = useState(false);
  const [testingCall, setTestingCall] = useState(false);

  useEffect(() => {
    loadConfig();
    loadSmsRecords();
  }, []);

  const loadConfig = async () => {
    try {
      const response = await twilioApi.getConfig();
      // 后端返回 { success: true, data: {...} }，实际配置在 response.data.data 中
      const configData = response.data.data || response.data;
      setConfig(configData);
    } catch (error) {
      message.error('获取配置失败');
    }
  };

  const loadSmsRecords = async () => {
    try {
      const response = await twilioApi.getSmsRecords(20);
      // 后端返回 { success: true, data: [...] }
      const records = response.data.data || response.data || [];
      setSmsRecords(records);
    } catch (error) {
      console.error('获取短信记录失败', error);
    }
  };

  const testConnection = async () => {
    setLoading(true);
    setConnectionResult(null);
    try {
      const response = await twilioApi.testConnection();
      const data = response.data.data || response.data;
      setConnectionResult({ success: true, data });
    } catch (error: any) {
      setConnectionResult({
        success: false,
        error: error.response?.data?.error || error.message,
        code: error.response?.data?.code
      });
    } finally {
      setLoading(false);
    }
  };

  const handleTestSms = async (values: { to: string; message?: string }) => {
    setTestingSms(true);
    try {
      const response = await twilioApi.testSms(values.to, values.message);
      const data = response.data.data || response.data;
      message.success(`短信发送成功！SID: ${data.sid}`);
      smsForm.resetFields();
      loadSmsRecords();
    } catch (error: any) {
      message.error(error.response?.data?.error || '短信发送失败');
    } finally {
      setTestingSms(false);
    }
  };

  const handleTestCall = async (values: { to: string; message?: string }) => {
    setTestingCall(true);
    try {
      const response = await twilioApi.testCall(values.to, values.message);
      const data = response.data.data || response.data;
      message.success(`电话拨打成功！SID: ${data.sid}`);
      callForm.resetFields();
    } catch (error: any) {
      message.error(error.response?.data?.error || '电话拨打失败');
    } finally {
      setTestingCall(false);
    }
  };

  const getStatusTag = (status: string) => {
    const statusMap: Record<string, { color: string; text: string }> = {
      'queued': { color: 'default', text: '排队中' },
      'sent': { color: 'processing', text: '已发送' },
      'delivered': { color: 'success', text: '已送达' },
      'undelivered': { color: 'error', text: '未送达' },
      'failed': { color: 'error', text: '失败' },
    };
    const item = statusMap[status] || { color: 'default', text: status };
    return <Tag color={item.color}>{item.text}</Tag>;
  };

  // 检查是否已配置
  if (!config) {
    return (
      <div style={{ textAlign: 'center', padding: 100 }}>
        <Spin size="large" />
      </div>
    );
  }

  if (!config.configured) {
    return (
      <div>
        <Title level={2}>Twilio 功能测试</Title>
        <Result
          status="warning"
          title="Twilio 未配置"
          subTitle="请先在系统配置中完成 Twilio 账号配置"
          extra={[
            <Button type="primary" key="config" onClick={() => navigate('/admin/config')}>
              前往配置
            </Button>
          ]}
        />
      </div>
    );
  }

  const smsColumns = [
    { title: '接收号码', dataIndex: 'customer_phone', key: 'phone', width: 150 },
    { title: '短信内容', dataIndex: 'sms_content', key: 'content', ellipsis: true },
    { title: '状态', dataIndex: 'sms_status', key: 'status', width: 100, render: getStatusTag },
    { 
      title: '发送时间', 
      dataIndex: 'created_at', 
      key: 'created_at', 
      width: 180,
      render: (text: string) => new Date(text).toLocaleString()
    },
  ];

  return (
    <div>
      <Title level={2}>Twilio 功能测试</Title>
      <Paragraph type="secondary">
        测试 Twilio 服务的各项功能是否正常工作。请确保已在系统配置中正确配置 Twilio 账号信息。
      </Paragraph>

      <Tabs defaultActiveKey="connection">
        {/* 连接测试 */}
        <TabPane tab="连接测试" key="connection">
          <Card>
            <Descriptions column={2} bordered size="small">
              <Descriptions.Item label="账号 SID">
                {config.hasAccountSid ? (
                  <Tag color="success">已配置</Tag>
                ) : (
                  <Tag color="error">未配置</Tag>
                )}
              </Descriptions.Item>
              <Descriptions.Item label="Auth Token">
                {config.hasAuthToken ? (
                  <Tag color="success">已配置</Tag>
                ) : (
                  <Tag color="error">未配置</Tag>
                )}
              </Descriptions.Item>
              <Descriptions.Item label="发信号码">
                {config.hasPhoneNumber ? (
                  <Tag color="success">{config.phoneNumber}</Tag>
                ) : (
                  <Tag color="error">未配置</Tag>
                )}
              </Descriptions.Item>
              <Descriptions.Item label="回调 URL">
                {config.hasCallbackUrl ? (
                  <Tag color="success">已配置</Tag>
                ) : (
                  <Tag color="warning">未配置</Tag>
                )}
              </Descriptions.Item>
            </Descriptions>

            <Divider />

            <Space>
              <Button 
                type="primary" 
                icon={<ReloadOutlined />} 
                onClick={testConnection}
                loading={loading}
              >
                测试连接
              </Button>
              <Button icon={<SettingOutlined />} onClick={() => navigate('/admin/config')}>
                修改配置
              </Button>
            </Space>

            {connectionResult && (
              <Alert
                style={{ marginTop: 16 }}
                type={connectionResult.success ? 'success' : 'error'}
                message={connectionResult.success ? '连接成功' : '连接失败'}
                description={
                  connectionResult.success ? (
                    <Descriptions column={2} size="small">
                      <Descriptions.Item label="账号名称">{connectionResult.data?.accountName}</Descriptions.Item>
                      <Descriptions.Item label="状态">{connectionResult.data?.status}</Descriptions.Item>
                      <Descriptions.Item label="类型">{connectionResult.data?.type}</Descriptions.Item>
                      <Descriptions.Item label="Account SID">{connectionResult.data?.accountSid}</Descriptions.Item>
                    </Descriptions>
                  ) : (
                    <div>
                      <Text type="danger">{connectionResult.error}</Text>
                      {connectionResult.code && <Text type="secondary"> (错误码: {connectionResult.code})</Text>}
                    </div>
                  )
                }
                showIcon
              />
            )}
          </Card>
        </TabPane>

        {/* 短信测试 */}
        <TabPane tab="短信测试" key="sms">
          <Card title="发送测试短信">
            <Alert
              message="注意"
              description="测试短信将发送到真实号码，请注意费用。建议先发送到自己的手机进行测试。"
              type="warning"
              showIcon
              style={{ marginBottom: 16 }}
            />
            <Form form={smsForm} onFinish={handleTestSms} layout="vertical">
              <Form.Item
                label="接收号码"
                name="to"
                rules={[
                  { required: true, message: '请输入接收号码' },
                  { pattern: /^\+?[1-9]\d{1,14}$/, message: '请输入有效的电话号码（国际格式，如：+8613800138000）' }
                ]}
              >
                <Input placeholder="+8613800138000" />
              </Form.Item>
              <Form.Item label="短信内容（可选）" name="message">
                <Input.TextArea 
                  rows={3} 
                  placeholder="留空将发送默认测试短信"
                  maxLength={1600}
                  showCount
                />
              </Form.Item>
              <Form.Item>
                <Button type="primary" htmlType="submit" loading={testingSms} icon={<MailOutlined />}>
                  发送测试短信
                </Button>
              </Form.Item>
            </Form>
          </Card>

          <Card title="最近发送记录" style={{ marginTop: 16 }}>
            <Table
              dataSource={smsRecords}
              columns={smsColumns}
              rowKey="id"
              size="small"
              pagination={false}
            />
          </Card>
        </TabPane>

        {/* 电话测试 */}
        <TabPane tab="电话测试" key="call">
          <Card title="拨打测试电话">
            <Alert
              message="注意"
              description="测试电话将拨打到真实号码，请注意费用。建议先拨打到自己的手机进行测试。"
              type="warning"
              showIcon
              style={{ marginBottom: 16 }}
            />
            <Form form={callForm} onFinish={handleTestCall} layout="vertical">
              <Form.Item
                label="拨打号码"
                name="to"
                rules={[
                  { required: true, message: '请输入拨打号码' },
                  { pattern: /^\+?[1-9]\d{1,14}$/, message: '请输入有效的电话号码（国际格式，如：+8613800138000）' }
                ]}
              >
                <Input placeholder="+8613800138000" />
              </Form.Item>
              <Form.Item label="语音内容（可选）" name="message">
                <Input.TextArea 
                  rows={3} 
                  placeholder="留空将播放默认语音"
                  maxLength={500}
                  showCount
                />
              </Form.Item>
              <Form.Item>
                <Button type="primary" htmlType="submit" loading={testingCall} icon={<PhoneOutlined />}>
                  拨打测试电话
                </Button>
              </Form.Item>
            </Form>
          </Card>

          <Card title="功能说明" style={{ marginTop: 16 }}>
            <Descriptions column={1} bordered size="small">
              <Descriptions.Item label="测试电话">
                拨打后会播放指定的语音内容，然后自动挂断。用于测试 Twilio 电话功能是否正常。
              </Descriptions.Item>
              <Descriptions.Item label="回调 Webhook">
                如果配置了回调 URL，电话状态变化（如接通、挂断、未接等）会发送通知到该地址。
              </Descriptions.Item>
              <Descriptions.Item label="生产环境">
                在实际使用中，系统会根据客户通话状态自动处理未接通、语音信箱等场景。
              </Descriptions.Item>
            </Descriptions>
          </Card>
        </TabPane>

        {/* 帮助文档 */}
        <TabPane tab="配置帮助" key="help">
          <Card title="Twilio 配置指南">
            <Descriptions column={1} bordered>
              <Descriptions.Item label="1. 注册账号">
                <a href="https://www.twilio.com/try-twilio" target="_blank" rel="noopener noreferrer">
                  https://www.twilio.com/try-twilio
                </a>
                <Paragraph type="secondary" style={{ marginTop: 8 }}>
                  注册 Twilio 账号，新用户可获得免费试用额度。
                </Paragraph>
              </Descriptions.Item>
              <Descriptions.Item label="2. 获取凭证">
                <Paragraph>
                  登录 Twilio Console 后，在 Dashboard 页面可以找到：
                </Paragraph>
                <ul>
                  <li><Text strong>Account SID</Text> - 账号唯一标识</li>
                  <li><Text strong>Auth Token</Text> - 认证令牌（点击显示）</li>
                </ul>
              </Descriptions.Item>
              <Descriptions.Item label="3. 获取电话号码">
                <Paragraph>
                  在 Phone Numbers → Manage → Buy a number 购买或获取一个电话号码。
                </Paragraph>
                <Paragraph type="secondary">
                  注意：试用账号只能拨打验证过的号码，需要升级为付费账号才能拨打任意号码。
                </Paragraph>
              </Descriptions.Item>
              <Descriptions.Item label="4. 配置 Webhook（可选）">
                <Paragraph>
                  如果需要接收通话状态回调，需要配置一个公网可访问的 URL。
                </Paragraph>
                <Paragraph type="secondary">
                  格式：https://your-domain.com/api/twilio
                </Paragraph>
              </Descriptions.Item>
              <Descriptions.Item label="5. 号码格式">
                <Paragraph>
                  所有电话号码需要使用国际格式（E.164），例如：
                </Paragraph>
                <ul>
                  <li>中国手机：+8613800138000</li>
                  <li>美国号码：+12125551234</li>
                </ul>
              </Descriptions.Item>
            </Descriptions>
          </Card>
        </TabPane>
      </Tabs>
    </div>
  );
}
