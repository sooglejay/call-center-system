import { useState, useEffect } from 'react';
import {
  Card,
  Button,
  Table,
  Tag,
  Modal,
  message,
  Typography,
  Space,
  Tooltip,
  Empty,
  Descriptions
} from 'antd';
import {
  DownloadOutlined,
  FileTextOutlined,
  ReloadOutlined,
  EyeOutlined,
  DeleteOutlined
} from '@ant-design/icons';
import { logsApi } from '../../services/api';

const { Title, Text } = Typography;

interface LogInfo {
  user_id: number;
  file_name: string;
  file_size: number;
  upload_time: string;
  file_path: string;
  description: string;
}

export default function LogsManagement() {
  const [logs, setLogs] = useState<LogInfo[]>([]);
  const [loading, setLoading] = useState(false);
  const [detailVisible, setDetailVisible] = useState(false);
  const [selectedLog, setSelectedLog] = useState<LogInfo | null>(null);

  // 获取日志列表
  const fetchLogs = async () => {
    setLoading(true);
    try {
      const response = await logsApi.getLogsList();
      const data = response.data;
      setLogs(data.logs || []);
    } catch (error) {
      message.error('获取日志列表失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchLogs();
  }, []);

  // 下载日志
  const handleDownload = async (fileName: string) => {
    try {
      message.loading({ content: '正在下载...', key: 'download' });
      const blob = await logsApi.downloadLog(fileName);
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = fileName;
      document.body.appendChild(a);
      a.click();
      window.URL.revokeObjectURL(url);
      document.body.removeChild(a);
      message.success({ content: '下载成功', key: 'download' });
    } catch (error) {
      message.error({ content: '下载失败', key: 'download' });
    }
  };

  // 清空所有日志
  const handleDeleteAll = async () => {
    Modal.confirm({
      title: '确认清空所有日志',
      content: '此操作将删除所有用户的日志文件，且不可恢复。确定要继续吗？',
      okText: '确定清空',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        try {
          message.loading({ content: '正在清空...', key: 'deleteAll' });
          const response = await logsApi.deleteAllLogs();
          const data = response.data;
          message.success({
            content: `成功删除 ${data.deleted_count} 个文件`,
            key: 'deleteAll'
          });
          fetchLogs();
        } catch (error) {
          message.error({ content: '清空失败', key: 'deleteAll' });
        }
      }
    });
  };

  // 查看详情
  const handleViewDetail = (log: LogInfo) => {
    setSelectedLog(log);
    setDetailVisible(true);
  };

  // 格式化文件大小
  const formatFileSize = (bytes: number) => {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i];
  };

  // 格式化时间
  const formatTime = (time: string) => {
    return new Date(time).toLocaleString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit'
    });
  };

  const columns = [
    {
      title: '用户ID',
      dataIndex: 'user_id',
      key: 'user_id',
      width: 100,
      render: (userId: number) => (
        <Tag color="blue">{userId}</Tag>
      )
    },
    {
      title: '文件名',
      dataIndex: 'file_name',
      key: 'file_name',
      render: (fileName: string) => (
        <Space>
          <FileTextOutlined />
          <Text copyable>{fileName}</Text>
        </Space>
      )
    },
    {
      title: '描述',
      dataIndex: 'description',
      key: 'description',
      ellipsis: true,
      render: (description: string) => (
        <Tooltip title={description || '无描述'}>
          <Text type={description ? undefined : 'secondary'}>
            {description || '无描述'}
          </Text>
        </Tooltip>
      )
    },
    {
      title: '文件大小',
      dataIndex: 'file_size',
      key: 'file_size',
      width: 120,
      render: (size: number) => formatFileSize(size)
    },
    {
      title: '上传时间',
      dataIndex: 'upload_time',
      key: 'upload_time',
      width: 180,
      render: (time: string) => formatTime(time)
    },
    {
      title: '操作',
      key: 'action',
      width: 150,
      render: (_: any, record: LogInfo) => (
        <Space>
          <Button
            type="link"
            size="small"
            icon={<EyeOutlined />}
            onClick={() => handleViewDetail(record)}
          >
            详情
          </Button>
          <Button
            type="link"
            size="small"
            icon={<DownloadOutlined />}
            onClick={() => handleDownload(record.file_name)}
          >
            下载
          </Button>
        </Space>
      )
    }
  ];

  return (
    <div>
      <Card>
        <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Title level={4} style={{ margin: 0 }}>
            <FileTextOutlined style={{ marginRight: 8 }} />
            日志管理
          </Title>
          <Space>
            <Button
              danger
              icon={<DeleteOutlined />}
              onClick={handleDeleteAll}
              disabled={logs.length === 0}
            >
              清空所有日志
            </Button>
            <Button
              icon={<ReloadOutlined />}
              onClick={fetchLogs}
              loading={loading}
            >
              刷新
            </Button>
          </Space>
        </div>

        <Table
          columns={columns}
          dataSource={logs}
          rowKey="user_id"
          loading={loading}
          locale={{
            emptyText: (
              <Empty
                description="暂无日志"
                image={Empty.PRESENTED_IMAGE_SIMPLE}
              />
            )
          }}
          pagination={{
            pageSize: 20,
            showSizeChanger: true,
            showTotal: (total) => `共 ${total} 条记录`
          }}
        />
      </Card>

      {/* 日志详情弹窗 */}
      <Modal
        title="日志详情"
        open={detailVisible}
        onCancel={() => setDetailVisible(false)}
        footer={[
          <Button key="close" onClick={() => setDetailVisible(false)}>
            关闭
          </Button>,
          <Button
            key="download"
            type="primary"
            icon={<DownloadOutlined />}
            onClick={() => {
              if (selectedLog) {
                handleDownload(selectedLog.file_name);
              }
            }}
          >
            下载日志
          </Button>
        ]}
        width={600}
      >
        {selectedLog && (
          <Descriptions column={1} bordered>
            <Descriptions.Item label="用户ID">
              <Tag color="blue">{selectedLog.user_id}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label="文件名">
              <Text copyable>{selectedLog.file_name}</Text>
            </Descriptions.Item>
            <Descriptions.Item label="描述">
              <Text type={selectedLog.description ? undefined : 'secondary'}>
                {selectedLog.description || '无描述'}
              </Text>
            </Descriptions.Item>
            <Descriptions.Item label="文件大小">
              {formatFileSize(selectedLog.file_size)}
            </Descriptions.Item>
            <Descriptions.Item label="上传时间">
              {formatTime(selectedLog.upload_time)}
            </Descriptions.Item>
            <Descriptions.Item label="文件路径">
              <Text copyable type="secondary">
                {selectedLog.file_path}
              </Text>
            </Descriptions.Item>
          </Descriptions>
        )}
      </Modal>
    </div>
  );
}
