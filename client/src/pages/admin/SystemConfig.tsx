import { useEffect, useState } from 'react';
import { Card, Form, Input, Button, message, Alert } from 'antd';
import { configApi } from '../../services/api';

export default function SystemConfig() {
  const [configs, setConfigs] = useState<any>({});
  const [form] = Form.useForm();

  useEffect(() => {
    fetchConfigs();
  }, []);

  const fetchConfigs = async () => {
    try {
      const response = await configApi.getConfigs();
      const configMap: any = {};
      response.data.forEach((item: any) => {
        configMap[item.config_key] = item.config_value;
      });
      setConfigs(configMap);
      form.setFieldsValue(configMap);
    } catch (error) {
      message.error('获取配置失败');
    }
  };

  const handleUpdateConfig = async (key: string, value: string) => {
    try {
      await configApi.updateConfig(key, value);
      message.success('配置更新成功');
    } catch (error) {
      message.error('配置更新失败');
    }
  };

  return (
    <div>
      <h2>系统配置</h2>
      
      <Alert
        message="Twilio 配置说明"
        description="请在下方配置Twilio账号信息以启用电话拨打功能。需要配置Account SID、Auth Token、发信号码和Webhook回调URL。"
        type="info"
        showIcon
        style={{ marginBottom: 24 }}
      />

      <Card title="Twilio 配置">
        <Form form={form} layout="vertical">
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
              placeholder="输入Webhook回调URL"
              addonAfter={<Button type="link" onClick={() => handleUpdateConfig('twilio_callback_url', form.getFieldValue('twilio_callback_url'))}>保存</Button>}
            />
          </Form.Item>
        </Form>
      </Card>
    </div>
  );
}
