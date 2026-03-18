import React, { useState, useEffect } from 'react';
import {
  Card,
  Table,
  Button,
  Space,
  Tag,
  message,
  Upload,
  Descriptions,
  Divider,
  Alert,
  Popconfirm,
  Select,
  Typography
} from 'antd';
import {
  UploadOutlined,
  DatabaseOutlined,
  TeamOutlined,
  DeleteOutlined,
  PlayCircleOutlined,
  ReloadOutlined
} from '@ant-design/icons';
import type { UploadProps } from 'antd';
import { userApi, dataImportApi } from '../../services/api';
import type { User } from '../../services/api';

const { Text, Title } = Typography;

interface DataStats {
  customers: {
    mock: number;
    real: number;
  };
  users: {
    mock: number;
    real: number;
  };
  user_list: Array<{
    id: number;
    username: string;
    real_name: string;
    role: string;
    data_access_type: string;
  }>;
}

const DataPermission: React.FC = () => {
  const [users, setUsers] = useState<User[]>([]);
  const [stats, setStats] = useState<DataStats | null>(null);
  const [loading, setLoading] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [importResult, setImportResult] = useState<any>(null);

  // 加载数据
  const loadData = async () => {
    setLoading(true);
    try {
      const [usersRes, statsRes] = await Promise.all([
        userApi.getUsers(),
        dataImportApi.getStats()
      ]);
      setUsers(usersRes.data);
      setStats(statsRes.data);
    } catch (error) {
      message.error('加载数据失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  // 更新用户数据权限
  const handleUpdateDataAccess = async (userId: number, dataAccessType: string) => {
    try {
      await userApi.updateDataAccess(userId, dataAccessType);
      message.success('权限更新成功');
      loadData();
    } catch (error) {
      message.error('权限更新失败');
    }
  };

  // CSV 上传配置
  const uploadProps: UploadProps = {
    accept: '.csv',
    showUploadList: false,
    beforeUpload: async (file) => {
      setUploading(true);
      setImportResult(null);
      
      const formData = new FormData();
      formData.append('file', file);
      
      try {
        const res = await dataImportApi.uploadCsv(formData);
        setImportResult(res.data);
        message.success('CSV 导入完成');
        loadData();
      } catch (error: any) {
        message.error(error.response?.data?.error || '导入失败');
      } finally {
        setUploading(false);
      }
      
      return false;
    }
  };

  // 初始化 Mock 数据
  const handleInitMockData = async () => {
    setLoading(true);
    try {
      const res = await dataImportApi.initMockData();
      message.success(`成功初始化 ${res.data.imported} 条 Mock 数据`);
      loadData();
    } catch (error: any) {
      message.error(error.response?.data?.error || '初始化失败');
    } finally {
      setLoading(false);
    }
  };

  // 清空 Mock 数据
  const handleClearMockData = async () => {
    setLoading(true);
    try {
      const res = await dataImportApi.clearMockData();
      message.success(`已清空 ${res.data.deleted_count} 条 Mock 数据`);
      loadData();
    } catch (error: any) {
      message.error(error.response?.data?.error || '清空失败');
    } finally {
      setLoading(false);
    }
  };

  // 清空真实数据
  const handleClearRealData = async () => {
    setLoading(true);
    try {
      const res = await dataImportApi.clearRealData();
      message.success(`已清空 ${res.data.deleted_count} 条真实数据`);
      loadData();
    } catch (error: any) {
      message.error(error.response?.data?.error || '清空失败');
    } finally {
      setLoading(false);
    }
  };

  // 用户表格列定义
  const userColumns = [
    {
      title: '用户名',
      dataIndex: 'username',
      key: 'username'
    },
    {
      title: '姓名',
      dataIndex: 'real_name',
      key: 'real_name'
    },
    {
      title: '角色',
      dataIndex: 'role',
      key: 'role',
      render: (role: string) => (
        <Tag color={role === 'admin' ? 'red' : 'blue'}>
          {role === 'admin' ? '管理员' : '客服'}
        </Tag>
      )
    },
    {
      title: '数据权限',
      dataIndex: 'data_access_type',
      key: 'data_access_type',
      render: (value: string, record: User) => (
        <Select
          value={value || 'mock'}
          style={{ width: 120 }}
          onChange={(val) => handleUpdateDataAccess(record.id, val)}
          options={[
            { value: 'mock', label: '测试数据' },
            { value: 'real', label: '真实数据' }
          ]}
        />
      )
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (status: string) => (
        <Tag color={status === 'active' ? 'green' : 'red'}>
          {status === 'active' ? '正常' : '禁用'}
        </Tag>
      )
    }
  ];

  return (
    <div style={{ padding: 24 }}>
      <Title level={3}>数据权限管理</Title>
      
      {/* 数据统计概览 */}
      <Card title={<><DatabaseOutlined /> 数据统计</>} style={{ marginBottom: 24 }} loading={loading}>
        {stats && (
          <Descriptions column={4}>
            <Descriptions.Item label="测试客户数">
              <Text strong>{stats.customers.mock}</Text> 条
            </Descriptions.Item>
            <Descriptions.Item label="真实客户数">
              <Text strong>{stats.customers.real}</Text> 条
            </Descriptions.Item>
            <Descriptions.Item label="测试权限用户">
              <Text strong>{stats.users.mock}</Text> 人
            </Descriptions.Item>
            <Descriptions.Item label="真实权限用户">
              <Text strong>{stats.users.real}</Text> 人
            </Descriptions.Item>
          </Descriptions>
        )}
      </Card>

      {/* 数据操作 */}
      <Card title="数据操作" style={{ marginBottom: 24 }}>
        <Space direction="vertical" style={{ width: '100%' }}>
          <Alert
            message="数据说明"
            description={
              <div>
                <p>• <b>测试数据（Mock）</b>：用于开发和测试，客服账号默认使用测试数据</p>
                <p>• <b>真实数据（Real）</b>：正式客户数据，管理员可上传 CSV 导入真实数据</p>
                <p>• 客服只能看到其权限范围内的数据，管理员可以看到所有数据</p>
              </div>
            }
            type="info"
            showIcon
            style={{ marginBottom: 16 }}
          />

          <Divider orientation="left">测试数据管理</Divider>
          
          <Space>
            <Button
              type="default"
              icon={<PlayCircleOutlined />}
              onClick={handleInitMockData}
              loading={loading}
            >
              初始化测试数据
            </Button>
            <Popconfirm
              title="确定要清空所有测试数据吗？"
              description="此操作不可恢复"
              onConfirm={handleClearMockData}
              okText="确定"
              cancelText="取消"
            >
              <Button danger icon={<DeleteOutlined />} loading={loading}>
                清空测试数据
              </Button>
            </Popconfirm>
            <Button icon={<ReloadOutlined />} onClick={loadData}>
              刷新统计
            </Button>
          </Space>

          <Divider orientation="left">真实数据管理</Divider>

          <Space>
            <Upload {...uploadProps}>
              <Button icon={<UploadOutlined />} loading={uploading}>
                上传 CSV 导入真实数据
              </Button>
            </Upload>
            <Popconfirm
              title="确定要清空所有真实数据吗？"
              description="此操作不可恢复"
              onConfirm={handleClearRealData}
              okText="确定"
              cancelText="取消"
            >
              <Button danger icon={<DeleteOutlined />} loading={loading}>
                清空真实数据
              </Button>
            </Popconfirm>
          </Space>

          {/* 导入结果 */}
          {importResult && (
            <Card size="small" title="导入结果" style={{ marginTop: 16 }}>
              <Descriptions column={4}>
                <Descriptions.Item label="总记录数">{importResult.summary.total}</Descriptions.Item>
                <Descriptions.Item label="成功导入">{importResult.summary.imported}</Descriptions.Item>
                <Descriptions.Item label="重复跳过">{importResult.summary.duplicates}</Descriptions.Item>
                <Descriptions.Item label="导入失败">{importResult.summary.errors}</Descriptions.Item>
              </Descriptions>
              {importResult.error_details?.length > 0 && (
                <div style={{ marginTop: 8 }}>
                  <Text type="danger">错误详情（前10条）：</Text>
                  <ul style={{ margin: '8px 0', color: '#ff4d4f' }}>
                    {importResult.error_details.map((err: string, idx: number) => (
                      <li key={idx}>{err}</li>
                    ))}
                  </ul>
                </div>
              )}
            </Card>
          )}
        </Space>
      </Card>

      {/* 用户权限管理 */}
      <Card title={<><TeamOutlined /> 用户数据权限</>}>
        <Table
          dataSource={users}
          columns={userColumns}
          rowKey="id"
          loading={loading}
          pagination={{ pageSize: 10 }}
        />
      </Card>

      {/* CSV 格式说明 */}
      <Card title="CSV 格式说明" style={{ marginTop: 24 }}>
        <Alert
          message="CSV 文件必须包含以下字段"
          description={
            <div>
              <p><b>必要字段：</b>name（姓名）, phone（电话）</p>
              <p><b>可选字段：</b>email, company, address, notes, status, priority</p>
              <p><b>示例：</b></p>
              <pre style={{ background: '#f5f5f5', padding: 12, borderRadius: 4 }}>
{`name,phone,email,company,notes
张三,13800138000,zhangsan@example.com,测试公司,重要客户
李四,13900139000,lisi@example.com,示例公司,潜在客户`}
              </pre>
            </div>
          }
          type="info"
        />
      </Card>
    </div>
  );
};

export default DataPermission;
