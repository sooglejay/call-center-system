import { useEffect, useState } from 'react';
import { Card, Form, Select, Switch, Button, message, Radio } from 'antd';
import { configApi } from '../../services/api';
import { useAgentConfigStore } from '../../stores';

const { Option } = Select;

export default function Settings() {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const { setConfig } = useAgentConfigStore();

  useEffect(() => {
    fetchConfig();
  }, []);

  const fetchConfig = async () => {
    try {
      const response = await configApi.getAgentConfig();
      form.setFieldsValue(response.data);
      setConfig(response.data);
    } catch (error) {
      message.error('获取配置失败');
    }
  };

  const handleSave = async (values: any) => {
    setLoading(true);
    try {
      const response = await configApi.updateAgentConfig(values);
      setConfig(response.data);
      message.success('配置保存成功');
    } catch (error) {
      message.error('保存失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      <h2>拨号设置</h2>
      
      <Card title="自动拨号配置" style={{ maxWidth: 600 }}>
        <Form form={form} layout="vertical" onFinish={handleSave}>
          <Form.Item name="dial_strategy" label="选号策略">
            <Radio.Group>
              <Radio.Button value="newest">最新优先</Radio.Button>
              <Radio.Button value="oldest">最老优先</Radio.Button>
            </Radio.Group>
          </Form.Item>
          
          <Form.Item name="dial_delay" label="拨号延迟">
            <Select>
              <Option value={2}>2秒</Option>
              <Option value={3}>3秒</Option>
              <Option value={5}>5秒</Option>
              <Option value={10}>10秒</Option>
            </Select>
          </Form.Item>
          
          <Form.Item name="remove_duplicates" label="自动移除重复号码" valuePropName="checked">
            <Switch />
          </Form.Item>
          
          <Form.Item>
            <Button type="primary" htmlType="submit" loading={loading}>
              保存设置
            </Button>
          </Form.Item>
        </Form>
      </Card>

      <Card title="设置说明" style={{ maxWidth: 600, marginTop: 24 }}>
        <ul>
          <li><strong>选号策略</strong>：自动拨号时选择客户的顺序</li>
          <li><strong>拨号延迟</strong>：通话结束后等待多久开始拨打下一个</li>
          <li><strong>自动移除重复号码</strong>：如果号码已被其他客服拨打过，是否从列表中移除</li>
        </ul>
      </Card>
    </div>
  );
}
