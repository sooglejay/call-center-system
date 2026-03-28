import { useState, useEffect } from 'react';
import {
  Card,
  Button,
  Table,
  Tag,
  Modal,
  Form,
  Input,
  InputNumber,
  Switch,
  Upload,
  message,
  Descriptions,
  Typography,
  Divider,
  Alert
} from 'antd';
import {
  UploadOutlined,
  PlusOutlined,
  DeleteOutlined,
  MobileOutlined,
  CheckCircleOutlined,
  ExclamationCircleOutlined
} from '@ant-design/icons';
import type { UploadFile } from 'antd/es/upload/interface';

const { Title, Paragraph, Text } = Typography;
const { TextArea } = Input;
const { confirm } = Modal;

interface VersionInfo {
  id: number;
  version_code: number;
  version_name: string;
  platform: string;
  apk_url: string;
  update_log: string;
  force_update: number;
  min_version_code: number;
  is_active: number;
  created_by_name: string;
  created_at: string;
}

export default function VersionManagement() {
  const [versions, setVersions] = useState<VersionInfo[]>([]);
  const [currentVersion, setCurrentVersion] = useState<VersionInfo | null>(null);
  const [loading, setLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [uploadedFile, setUploadedFile] = useState<UploadFile | null>(null);
  const [form] = Form.useForm();

  // 获取版本列表
  const fetchVersions = async () => {
    setLoading(true);
    try {
      const response = await fetch('/api/version/list', {
        headers: {
          'Authorization': `Bearer ${localStorage.getItem('token')}`
        }
      });
      if (response.ok) {
        const data = await response.json();
        setVersions(data);
        // 设置当前活跃版本
        const active = data.find((v: VersionInfo) => v.is_active === 1);
        setCurrentVersion(active || null);
      }
    } catch (error) {
      message.error('获取版本列表失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchVersions();
  }, []);

  // 上传APK
  const handleUpload = async (file: File) => {
    const versionCode = form.getFieldValue('version_code');
    if (!versionCode) {
      message.error('请先输入版本号');
      return false;
    }

    const formData = new FormData();
    formData.append('apk', file);
    formData.append('version_code', versionCode);

    try {
      const response = await fetch('/api/version/upload', {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${localStorage.getItem('token')}`
        },
        body: formData
      });

      if (response.ok) {
        await response.json();
        message.success('APK上传成功');
        return true;
      } else {
        const error = await response.json();
        message.error(error.error || '上传失败');
        return false;
      }
    } catch (error) {
      message.error('上传失败');
      return false;
    }
  };

  // 创建版本
  const handleCreate = async (values: any) => {
    if (!uploadedFile) {
      message.error('请先上传APK文件');
      return;
    }

    try {
      const response = await fetch('/api/version/create', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${localStorage.getItem('token')}`
        },
        body: JSON.stringify(values)
      });

      if (response.ok) {
        message.success('版本发布成功');
        setModalVisible(false);
        form.resetFields();
        setUploadedFile(null);
        fetchVersions();
      } else {
        const error = await response.json();
        message.error(error.error || '发布失败');
      }
    } catch (error) {
      message.error('发布失败');
    }
  };

  // 删除版本
  const handleDelete = (record: VersionInfo) => {
    confirm({
      title: '确认删除',
      icon: <ExclamationCircleOutlined />,
      content: `确定要删除版本 ${record.version_name} 吗？`,
      onOk: async () => {
        try {
          const response = await fetch(`/api/version/${record.id}`, {
            method: 'DELETE',
            headers: {
              'Authorization': `Bearer ${localStorage.getItem('token')}`
            }
          });

          if (response.ok) {
            message.success('删除成功');
            fetchVersions();
          } else {
            const error = await response.json();
            message.error(error.error || '删除失败');
          }
        } catch (error) {
          message.error('删除失败');
        }
      }
    });
  };

  const columns = [
    {
      title: '版本号',
      dataIndex: 'version_code',
      key: 'version_code',
      width: 100
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
      render: (date: string) => new Date(date).toLocaleString()
    },
    {
      title: '操作',
      key: 'action',
      width: 100,
      render: (_: any, record: VersionInfo) => (
        record.is_active !== 1 && (
          <Button
            type="text"
            danger
            icon={<DeleteOutlined />}
            onClick={() => handleDelete(record)}
          >
            删除
          </Button>
        )
      )
    }
  ];

  return (
    <div>
      <Title level={2}>
        <MobileOutlined style={{ marginRight: 12 }} />
        App版本管理
      </Title>
      <Paragraph type="secondary">
        管理Android App的版本发布和更新。发布新版本后，客户端会自动检测并提示用户更新。
      </Paragraph>

      <Divider />

      {/* 当前版本信息 */}
      {currentVersion && (
        <Card title="当前版本" style={{ marginBottom: 24 }}>
          <Descriptions bordered column={3}>
            <Descriptions.Item label="版本号">{currentVersion.version_code}</Descriptions.Item>
            <Descriptions.Item label="版本名称">{currentVersion.version_name}</Descriptions.Item>
            <Descriptions.Item label="强制更新">
              {currentVersion.force_update === 1 ? <Tag color="red">是</Tag> : <Tag color="default">否</Tag>}
            </Descriptions.Item>
            <Descriptions.Item label="最低版本号">{currentVersion.min_version_code}</Descriptions.Item>
            <Descriptions.Item label="发布人">{currentVersion.created_by_name}</Descriptions.Item>
            <Descriptions.Item label="发布时间">
              {new Date(currentVersion.created_at).toLocaleString()}
            </Descriptions.Item>
            <Descriptions.Item label="更新日志" span={3}>
              <pre style={{ margin: 0, whiteSpace: 'pre-wrap' }}>{currentVersion.update_log}</pre>
            </Descriptions.Item>
          </Descriptions>
        </Card>
      )}

      {/* 版本列表 */}
      <Card
        title="版本历史"
        extra={
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => setModalVisible(true)}
          >
            发布新版本
          </Button>
        }
      >
        <Table
          dataSource={versions}
          columns={columns}
          rowKey="id"
          loading={loading}
          pagination={{ pageSize: 10 }}
        />
      </Card>

      {/* 发布新版本弹窗 */}
      <Modal
        title="发布新版本"
        open={modalVisible}
        onOk={() => form.submit()}
        onCancel={() => {
          setModalVisible(false);
          form.resetFields();
          setUploadedFile(null);
        }}
        width={600}
      >
        <Alert
          message="发布流程"
          description="1. 填写版本信息 → 2. 上传APK文件 → 3. 点击发布"
          type="info"
          showIcon
          style={{ marginBottom: 24 }}
        />

        <Form
          form={form}
          layout="vertical"
          onFinish={handleCreate}
        >
          <Form.Item
            name="version_code"
            label="版本号（数字）"
            rules={[{ required: true, message: '请输入版本号' }]}
            help="Android的versionCode，必须是递增的数字"
          >
            <InputNumber style={{ width: '100%' }} min={1} placeholder="例如：100" />
          </Form.Item>

          <Form.Item
            name="version_name"
            label="版本名称"
            rules={[{ required: true, message: '请输入版本名称' }]}
            help="用户可见的版本号，例如：1.0.0"
          >
            <Input placeholder="例如：1.0.0" />
          </Form.Item>

          <Form.Item
            name="update_log"
            label="更新日志"
            rules={[{ required: true, message: '请输入更新日志' }]}
          >
            <TextArea
              rows={4}
              placeholder="填写本次更新的内容，每行一条"
            />
          </Form.Item>

          <Form.Item
            name="force_update"
            label="强制更新"
            valuePropName="checked"
          >
            <Switch />
          </Form.Item>

          <Form.Item
            name="min_version_code"
            label="最低要求版本号"
            help="低于此版本的客户端将被强制更新"
          >
            <InputNumber style={{ width: '100%' }} min={1} placeholder="可选，默认等于版本号" />
          </Form.Item>

          <Form.Item
            label="APK文件"
            required
            help="请先输入版本号，然后上传APK文件"
          >
            <Upload
              beforeUpload={handleUpload}
              onChange={({ file }) => setUploadedFile(file.status === 'done' ? file : null)}
              accept=".apk"
              maxCount={1}
            >
              <Button icon={<UploadOutlined />}>上传APK</Button>
            </Upload>
            {uploadedFile && (
              <Text type="success" style={{ display: 'block', marginTop: 8 }}>
                <CheckCircleOutlined /> 已上传: {uploadedFile.name}
              </Text>
            )}
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
