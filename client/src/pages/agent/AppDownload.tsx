import { useEffect, useState } from 'react';
import { Card, Descriptions, QRCode, Button, Spin, Typography, Divider } from 'antd';
import { DownloadOutlined, AndroidOutlined, QrcodeOutlined } from '@ant-design/icons';
import { versionApi } from '../../services/api';

const { Title, Paragraph, Text } = Typography;

/**
 * App下载页面 - 客服版本
 */
export default function AppDownload() {
  const [latestVersion, setLatestVersion] = useState<any>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchLatestVersion();
  }, []);

  const fetchLatestVersion = async () => {
    try {
      setLoading(true);
      const response = await versionApi.getLatestVersion();
      setLatestVersion(response.data);
    } catch (error) {
      console.error('获取版本信息失败', error);
    } finally {
      setLoading(false);
    }
  };

  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: '100px 0' }}>
        <Spin size="large" />
        <div style={{ marginTop: 16, color: '#999' }}>加载中...</div>
      </div>
    );
  }

  return (
    <div>
      <Title level={2}>
        <AndroidOutlined style={{ marginRight: 12 }} />
        Android App 下载
      </Title>
      <Paragraph type="secondary">
        下载最新版本的Android App，使用当前账号登录即可开始工作。
      </Paragraph>

      <Divider />

      {/* 当前版本信息 */}
      {latestVersion ? (
        <Card
          title="最新版本"
          extra={
            <Button
              type="primary"
              icon={<DownloadOutlined />}
              href={latestVersion.apk_url || latestVersion.download_url}
              target="_blank"
            >
              下载APK
            </Button>
          }
        >
          <Descriptions bordered column={3}>
            <Descriptions.Item label="版本号">{latestVersion.version_code}</Descriptions.Item>
            <Descriptions.Item label="版本名称">{latestVersion.version_name}</Descriptions.Item>
            <Descriptions.Item label="文件大小">
              {latestVersion.file_size ? `${(latestVersion.file_size / 1024 / 1024).toFixed(2)} MB` : '-'}
            </Descriptions.Item>
            <Descriptions.Item label="发布人">{latestVersion.created_by_name || '管理员'}</Descriptions.Item>
            <Descriptions.Item label="发布时间">
              {new Date(latestVersion.created_at).toLocaleString()}
            </Descriptions.Item>
            <Descriptions.Item label="下载地址">
              <Text copyable style={{ fontSize: 12 }}>
                {latestVersion.apk_url || latestVersion.download_url}
              </Text>
            </Descriptions.Item>
            <Descriptions.Item label="更新日志" span={3}>
              <pre style={{ margin: 0, whiteSpace: 'pre-wrap' }}>{latestVersion.update_log || '暂无更新日志'}</pre>
            </Descriptions.Item>
          </Descriptions>

          <Divider />

          {/* 二维码下载区域 */}
          <div style={{ textAlign: 'center', padding: '24px 0' }}>
            <Title level={5} style={{ marginBottom: 16 }}>
              <QrcodeOutlined style={{ marginRight: 8 }} />
              扫码下载最新版本
            </Title>
            <div style={{
              display: 'inline-block',
              padding: 16,
              background: '#fff',
              borderRadius: 8,
              border: '1px solid #f0f0f0'
            }}>
              <QRCode
                value={latestVersion.apk_url || latestVersion.download_url}
                size={200}
              />
            </div>
            <Paragraph type="secondary" style={{ marginTop: 16 }}>
              使用手机浏览器扫描二维码即可下载安装
            </Paragraph>
          </div>
        </Card>
      ) : (
        <Card>
          <div style={{ textAlign: 'center', padding: '60px 0', color: '#999' }}>
            <AndroidOutlined style={{ fontSize: 64, marginBottom: 16 }} />
            <div style={{ fontSize: 16 }}>暂无版本信息</div>
            <div style={{ marginTop: 8, fontSize: 14 }}>请联系管理员上传App版本</div>
          </div>
        </Card>
      )}

      {/* 安装说明 */}
      <Card title="安装说明" style={{ marginTop: 24 }}>
        <ol style={{ lineHeight: 2, paddingLeft: 20 }}>
          <li>点击"下载APK"按钮或扫描二维码下载安装包</li>
          <li>下载完成后，点击通知栏的下载完成提示进行安装</li>
          <li>如果提示"未知来源应用"，请前往设置允许安装此来源的应用</li>
          <li>安装完成后，使用当前账号登录即可开始使用</li>
          <li>建议开启自动更新，及时获取最新功能和修复</li>
        </ol>
      </Card>
    </div>
  );
}
