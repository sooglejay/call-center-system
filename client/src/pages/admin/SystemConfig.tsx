import { useEffect } from 'react';
import { Card, Form, Input, Button, message, Alert, Switch, Typography, Select } from 'antd';
import { configApi } from '../../services/api';

const { TextArea } = Input;
const { Text } = Typography;

export default function SystemConfig() {
  const [form] = Form.useForm();

  useEffect(() => {
    fetchConfigs();
  }, []);

  const fetchConfigs = async () => {
    try {
      const response = await configApi.getConfigs();
      const configMap: any = {};
      response.data.forEach((item: any) => {
        // 转换字符串 'true'/'false' 为布尔值
        if (item.config_value === 'true' || item.config_value === 'false') {
          configMap[item.config_key] = item.config_value === 'true';
        } else {
          configMap[item.config_key] = item.config_value;
        }
      });
      form.setFieldsValue(configMap);
    } catch (error) {
      message.error('获取配置失败');
    }
  };

  const handleUpdateConfig = async (key: string, value: string) => {
    try {
      await configApi.updateConfig(key, value);
      message.success('配置更新成功');
      fetchConfigs();
    } catch (error) {
      message.error('配置更新失败');
    }
  };

  return (
    <div>
      <h2>系统配置</h2>
      
      <Alert
        message="配置说明"
        description="请在下方配置系统参数，包括 Twilio 电话服务、短信功能和用户注册设置。"
        type="info"
        showIcon
        style={{ marginBottom: 24 }}
      />

      <Form form={form} layout="vertical">
        <Card title="Twilio 基础配置" style={{ marginBottom: 24 }}>
          <Form.Item label="Account SID" name="twilio_account_sid">
            <Input.Password 
              placeholder="输入Twilio Account SID"
              addonAfter={<Button type="link" onClick={() => handleUpdateConfig('twilio_account_sid', form.getFieldValue('twilio_account_sid'))}>保存</Button>}
            />
          </Form.Item>
          <Form.Item label="Auth Token" name="twilio_auth_token">
            <Input.Password 
              placeholder="输入Twilio Auth Token"
              addonAfter={<Button type="link" onClick={() => handleUpdateConfig('twilio_auth_token', form.getFieldValue('twilio_auth_token'))}>保存</Button>}
            />
          </Form.Item>
          <Form.Item label="发信号码" name="twilio_phone_number">
            <Input 
              placeholder="输入Twilio电话号码，例如：+1234567890"
              addonAfter={<Button type="link" onClick={() => handleUpdateConfig('twilio_phone_number', form.getFieldValue('twilio_phone_number'))}>保存</Button>}
            />
          </Form.Item>
          <Form.Item label="Webhook回调URL" name="twilio_callback_url">
            <Input 
              placeholder="输入Webhook回调URL，例如：https://your-domain.com/api/twilio"
              addonAfter={<Button type="link" onClick={() => handleUpdateConfig('twilio_callback_url', form.getFieldValue('twilio_callback_url'))}>保存</Button>}
            />
          </Form.Item>
        </Card>

        <Card title="短信功能配置" style={{ marginBottom: 24 }}>
          <Form.Item label="启用短信功能" name="sms_enabled" valuePropName="checked">
            <Switch 
              checkedChildren="开启" 
              unCheckedChildren="关闭"
              onChange={(checked) => handleUpdateConfig('sms_enabled', checked.toString())}
            />
          </Form.Item>
          <Text type="secondary" style={{ display: 'block', marginBottom: 16 }}>
            开启后，当电话未接通时会自动发送短信给客户
          </Text>
          
          <Form.Item 
            label="未接通短信模板" 
            name="sms_template_unanswered"
            extra="可用变量：{agentName} - 客服姓名, {agentPhone} - 客服电话"
          >
            <TextArea 
              rows={3} 
              placeholder="请输入短信模板"
            />
          </Form.Item>
          <Button type="primary" onClick={() => handleUpdateConfig('sms_template_unanswered', form.getFieldValue('sms_template_unanswered'))}>保存短信模板</Button>
        </Card>

        <Card title="语音信箱配置" style={{ marginBottom: 24 }}>
          <Form.Item label="启用语音信箱" name="voicemail_enabled" valuePropName="checked">
            <Switch 
              checkedChildren="开启" 
              unCheckedChildren="关闭"
              onChange={(checked) => handleUpdateConfig('voicemail_enabled', checked.toString())}
            />
          </Form.Item>
          <Text type="secondary" style={{ display: 'block', marginBottom: 16 }}>
            开启后，当客户未接通时会自动转接到语音信箱
          </Text>
          
          <Form.Item label="语音信箱问候语" name="voicemail_greeting">
            <TextArea 
              rows={3} 
              placeholder="请输入语音信箱问候语"
            />
          </Form.Item>
          <Button type="primary" onClick={() => handleUpdateConfig('voicemail_greeting', form.getFieldValue('voicemail_greeting'))}>保存问候语</Button>
        </Card>

        <Card title="用户注册配置">
          <Alert
            message="注册开关"
            description="开启后，用户可以在登录页面自行注册账号。关闭后，只能由管理员在人员管理中添加用户。"
            type="info"
            showIcon
            style={{ marginBottom: 16 }}
          />
          <Form.Item label="允许用户注册" name="allow_register" valuePropName="checked">
            <Switch 
              checkedChildren="开启" 
              unCheckedChildren="关闭"
              onChange={(checked) => handleUpdateConfig('allow_register', checked.toString())}
            />
          </Form.Item>
          
          <Form.Item 
            label="注册用户默认角色" 
            name="register_default_role"
            extra="新注册用户将自动获得此角色，一般设置为 agent（客服）"
          >
            <Select 
              style={{ maxWidth: 200 }}
              onChange={(value) => handleUpdateConfig('register_default_role', value)}
              options={[
                { value: 'agent', label: '客服' },
                { value: 'admin', label: '管理员' }
              ]}
            />
          </Form.Item>
        </Card>
      </Form>
    </div>
  );
}
