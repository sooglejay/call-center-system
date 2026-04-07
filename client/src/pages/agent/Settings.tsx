import { useEffect, useState } from 'react';
import { Card, Form, Select, Switch, Button, message, Radio, Descriptions, QRCode, Tag } from 'antd';
import { DownloadOutlined, AndroidOutlined } from '@ant-design/icons';
import { configApi, versionApi } from '../../services/api';
import { useAgentConfigStore } from '../../stores';

const { Option } = Select;

export default function Settings() {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [latestVersion, setLatestVersion] = useState<any>(null);
  const { setConfig } = useAgentConfigStore();

  useEffect(() => {
    fetchConfig();
    fetchLatestVersion();
  }, []);

  const fetchLatestVersion = async () => {
    try {
      const response = await versionApi.getLatestVersion();
      setLatestVersion(response.data);
    } catch (error) {
      // 忽略错误，不显示版本信息
    }
  };

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

      {/* App下载区域 */}
      <Card
        title={<span><AndroidOutlined /> Android App 下载</span>}
        style={{ maxWidth: 600, marginTop: 24 }}
      >
        {latestVersion ? (
          <>
            <Descriptions column={1} size="small">
              <Descriptions.Item label="最新版本">
                <Tag color="blue">v{latestVersion.version_name}</Tag>
                <span style={{ marginLeft: 8, color: '#666', fontSize: 12 }}>
                  (Build {latestVersion.version_code})
                </span>
              </Descriptions.Item>
              <Descriptions.Item label="更新日期">
                {new Date(latestVersion.created_at).toLocaleDateString()}
              </Descriptions.Item>
            </Descriptions>

            <div style={{ marginTop: 16, display: 'flex', gap: 16, alignItems: 'flex-start' }}>
              <Button
                type="primary"
                icon={<DownloadOutlined />}
                href={`${import.meta.env.VITE_API_BASE_URL || ''}${latestVersion.download_url}`}
                target="_blank"
              >
                下载APK
              </Button>

              <div style={{ textAlign: 'center' }}>
                <QRCode
                  value={`${window.location.origin}${import.meta.env.VITE_API_BASE_URL || ''}${latestVersion.download_url}`}
                  size={100}
                />
                <div style={{ marginTop: 4, fontSize: 12, color: '#666' }}>扫码下载</div>
              </div>
            </div>

            {latestVersion.update_log && (
              <div style={{ marginTop: 16 }}>
                <div style={{ fontWeight: 'bold', marginBottom: 8 }}>更新日志：</div>
                <pre style={{
                  margin: 0,
                  padding: 12,
                  background: '#f5f5f5',
                  borderRadius: 4,
                  fontSize: 12,
                  maxHeight: 200,
                  overflow: 'auto',
                  whiteSpace: 'pre-wrap',
                  wordWrap: 'break-word'
                }}>
                  {latestVersion.update_log}
                </pre>
              </div>
            )}
          </>
        ) : (
          <div style={{ color: '#999', textAlign: 'center', padding: '20px 0' }}>
            暂无版本信息
          </div>
        )}
      </Card>
    </div>
  );
}
