import { useEffect, useState } from 'react';
import { Card, Descriptions, QRCode, Button, Spin, Typography, Divider, Tooltip, Table, Tag, Space } from 'antd';
import { DownloadOutlined, AndroidOutlined, QrcodeOutlined, CheckCircleOutlined } from '@ant-design/icons';
import { versionApi } from '../../services/api';

const { Title, Paragraph, Text } = Typography;

interface VersionInfo {
  id: number;
  version_code: number;
  version_name: string;
  platform: string;
  apk_url: string;
  download_url?: string;
  file_size?: number;
  update_log: string;
  force_update: number;
  min_version_code: number;
  is_active: number;
  created_by_name: string;
  created_at: string;
}

/**
 * App下载页面 - 客服版本
 */
export default function AppDownload() {
  const [latestVersion, setLatestVersion] = useState<VersionInfo | null>(null);
  const [versions, setVersions] = useState<VersionInfo[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchVersions();
  }, []);

  const fetchVersions = async () => {
    try {
      setLoading(true);
      const response = await versionApi.getVersions();
      setVersions(response.data || []);
      // 设置最新版本
      const active = (response.data || []).find((v: VersionInfo) => v.is_active === 1);
      setLatestVersion(active || null);
    } catch (error) {
      console.error('获取版本信息失败', error);
    } finally {
      setLoading(false);
    }
  };

  const columns = [
    {
      title: '版本号',
      dataIndex: 'version_code',
      key: 'version_code',
      width: 100,
      sorter: (b: VersionInfo, a: VersionInfo) => b.version_code - a.version_code,
      defaultSortOrder: 'descend' as const
    },
    {
      title: '版本名称',
      dataIndex: 'version_name',
      key: 'version_name',
      width: 120
    },
    {
      title: '状态',
      dataIndex: 'is_active',
      key: 'is_active',
      width: 100,
      render: (isActive: number) => (
        isActive === 1 ? (
          <Tag color="success" icon={<CheckCircleOutlined />}>当前版本</Tag>
        ) : (
          <Tag color="default">历史版本</Tag>
        )
      )
    },
    {
      title: '强制更新',
      dataIndex: 'force_update',
      key: 'force_update',
      width: 100,
      render: (force: number) => (
        force === 1 ? <Tag color="red">是</Tag> : <Tag color="default">否</Tag>
      )
    },
    {
      title: '发布人',
      dataIndex: 'created_by_name',
      key: 'created_by_name',
      width: 120
    },
    {
      title: '发布时间',
      dataIndex: 'created_at',
      key: 'created_at',
      width: 180,
      sorter: (b: VersionInfo, a: VersionInfo) => new Date(b.created_at).getTime() - new Date(a.created_at).getTime(),
      render: (date: string) => new Date(date).toLocaleString()
    },
    {
      title: '操作',
      key: 'action',
      width: 220,
      render: (_: any, record: VersionInfo) => (
        <Space size="small">
          <Button
            type="link"
            icon={<DownloadOutlined />}
            href={record.apk_url}
            target="_blank"
          >
            下载
          </Button>
          <Tooltip
            title={
              <div style={{ padding: 8 }}>
                <QRCode
                  value={record.apk_url}
                  size={200}
                />
                <div style={{ textAlign: 'center', marginTop: 8, color: '#666' }}>
                  扫码下载 v{record.version_name}
                </div>
              </div>
            }
            placement="top"
            color="#fff"
          >
            <div style={{ cursor: 'pointer', display: 'inline-flex' }}>
              <QRCode
                value={record.apk_url}
                size={48}
              />
            </div>
          </Tooltip>
        </Space>
      )
    }
  ];

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
        App下载
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
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <Button
                type="primary"
                icon={<DownloadOutlined />}
                href={latestVersion.apk_url || latestVersion.download_url}
                target="_blank"
              >
                下载APK
              </Button>
              <Tooltip
                title={
                  <div style={{ padding: 8 }}>
                    <QRCode
                      value={latestVersion.apk_url || latestVersion.download_url || ''}
                      size={200}
                    />
                    <div style={{ textAlign: 'center', marginTop: 8, color: '#666' }}>
                      扫码下载 v{latestVersion.version_name}
                    </div>
                  </div>
                }
                placement="top"
                color="#fff"
              >
                <div style={{ cursor: 'pointer' }}>
                  <QRCode
                    value={latestVersion.apk_url || latestVersion.download_url || ''}
                    size={48}
                  />
                </div>
              </Tooltip>
            </div>
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
                value={latestVersion.apk_url || latestVersion.download_url || ''}
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

      {/* 版本历史 */}
      <Card
        title="版本历史"
        style={{ marginTop: 24 }}
      >
        <Table
          dataSource={versions}
          columns={columns}
          rowKey="id"
          loading={loading}
          pagination={{ pageSize: 10 }}
        />
      </Card>

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
