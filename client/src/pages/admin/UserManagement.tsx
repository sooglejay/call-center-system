import { useEffect, useState } from 'react';
import { Table, Button, Modal, Form, Input, Select, Popconfirm, message, Tag, Tooltip, Space } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, KeyOutlined, FileTextOutlined } from '@ant-design/icons';
import { userApi, logsApi } from '../../services/api';
import type { User } from '../../services/api';

export default function UserManagement() {
  const [users, setUsers] = useState<User[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [editingUser, setEditingUser] = useState<User | null>(null);
  const [resetPasswordVisible, setResetPasswordVisible] = useState(false);
  const [resetUserId, setResetUserId] = useState<number | null>(null);
  const [form] = Form.useForm();
  const [passwordForm] = Form.useForm();
  
  // 多选状态
  const [selectedUserIds, setSelectedUserIds] = useState<number[]>([]);

  useEffect(() => {
    fetchUsers();
  }, []);

  const fetchUsers = async () => {
    setLoading(true);
    try {
      const response = await userApi.getUsers();
      // 支持分页格式 { data: [...], total: ... } 和直接数组格式
      const userData = response.data?.data || response.data || [];
      setUsers(Array.isArray(userData) ? userData : []);
    } catch (error: any) {
      message.error(error.response?.data?.error || '获取用户列表失败');
    } finally {
      setLoading(false);
    }
  };

  const handleAdd = () => {
    setEditingUser(null);
    form.resetFields();
    setModalVisible(true);
  };

  const handleEdit = (record: User) => {
    setEditingUser(record);
    form.setFieldsValue(record);
    setModalVisible(true);
  };

  const handleDelete = async (id: number) => {
    // 防止删除自己
    const currentUser = localStorage.getItem('user');
    if (currentUser) {
      const user = JSON.parse(currentUser);
      if (user.id === id) {
        message.warning('不能删除当前登录的账号');
        return;
      }
    }
    
    try {
      await userApi.deleteUser(id);
      message.success('删除成功');
      fetchUsers();
    } catch (error: any) {
      message.error(error.response?.data?.error || '删除失败，请重试');
    }
  };

  const handleSubmit = async (values: any) => {
    try {
      if (editingUser) {
        await userApi.updateUser(editingUser.id, values);
        message.success('用户信息更新成功');
      } else {
        await userApi.createUser(values);
        message.success('用户创建成功');
      }
      setModalVisible(false);
      fetchUsers();
    } catch (error: any) {
      const errorMsg = error.response?.data?.error || '操作失败，请重试';
      message.error(errorMsg);
    }
  };

  const handleResetPassword = async (values: any) => {
    if (!resetUserId) return;
    try {
      await userApi.resetPassword(resetUserId, values.new_password);
      message.success('密码重置成功，请通知用户新密码');
      setResetPasswordVisible(false);
      passwordForm.resetFields();
    } catch (error: any) {
      message.error(error.response?.data?.error || '密码重置失败，请重试');
    }
  };

  const openResetPassword = (userId: number) => {
    setResetUserId(userId);
    setResetPasswordVisible(true);
  };

  const handleDataAccessChange = async (userId: number, dataAccessType: string) => {
    try {
      await userApi.updateDataAccess(userId, dataAccessType);
      message.success('数据权限更新成功');
      fetchUsers();
    } catch (error) {
      message.error('数据权限更新失败');
    }
  };

  // 获取当前登录用户ID
  const getCurrentUserId = (): number | null => {
    const currentUser = localStorage.getItem('user');
    if (currentUser) {
      const user = JSON.parse(currentUser);
      return user.id;
    }
    return null;
  };

  // 批量删除用户
  const handleBatchDelete = async () => {
    if (selectedUserIds.length === 0) {
      message.warning('请先选择要删除的人员');
      return;
    }

    // 防止删除自己
    const currentUserId = getCurrentUserId();
    if (currentUserId && selectedUserIds.includes(currentUserId)) {
      message.warning('不能删除当前登录的账号，请取消选择后再试');
      return;
    }

    Modal.confirm({
      title: '确认批量删除',
      content: `确定要删除选中的 ${selectedUserIds.length} 个人员吗？此操作不可恢复。`,
      okText: '确定删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        try {
          let successCount = 0;
          let failCount = 0;

          for (const userId of selectedUserIds) {
            // 跳过当前用户
            if (userId === currentUserId) {
              failCount++;
              continue;
            }
            try {
              await userApi.deleteUser(userId);
              successCount++;
            } catch {
              failCount++;
            }
          }

          if (failCount === 0) {
            message.success(`成功删除 ${successCount} 个人员`);
          } else {
            message.warning(`成功删除 ${successCount} 个人员，失败 ${failCount} 个`);
          }

          setSelectedUserIds([]);
          fetchUsers();
        } catch (error: any) {
          message.error(error.response?.data?.error || '批量删除失败');
        }
      }
    });
  };

  // 下载用户设备日志
  const handleDownloadLog = async (user: User) => {
    try {
      message.loading({ content: `正在下载 ${user.real_name || user.username} 的设备日志...`, key: 'downloadLog' });

      const blob = await logsApi.downloadLog(user.id);

      // 创建下载链接
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `user_${user.id}_${user.username}_logs.txt`;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      window.URL.revokeObjectURL(url);

      message.success({ content: '日志下载成功', key: 'downloadLog' });
    } catch (error: any) {
      if (error.response?.status === 404) {
        message.warning({ content: '该用户暂无设备日志', key: 'downloadLog' });
      } else {
        message.error({ content: error.response?.data?.error || '下载日志失败', key: 'downloadLog' });
      }
    }
  };

  const columns = [
    { title: '用户名', dataIndex: 'username', key: 'username' },
    { title: '姓名', dataIndex: 'real_name', key: 'real_name' },
    { title: '角色', dataIndex: 'role', key: 'role', render: (role: string) => (
      <Tag color={role === 'admin' ? 'red' : 'blue'}>
        {role === 'admin' ? '管理员' : '客服'}
      </Tag>
    )},
    { title: '电话', dataIndex: 'phone', key: 'phone' },
    { title: '邮箱', dataIndex: 'email', key: 'email' },
    { title: '状态', dataIndex: 'status', key: 'status', render: (status: string) => (
      <Tag color={status === 'active' ? 'green' : 'default'}>
        {status === 'active' ? '启用' : '禁用'}
      </Tag>
    )},
    { 
      title: '数据权限', 
      dataIndex: 'data_access_type', 
      key: 'data_access_type', 
      render: (dataAccessType: string, record: User) => {
        if (record.role === 'admin') {
          return <Tag color="purple">全部数据</Tag>;
        }
        return (
          <Select
            value={dataAccessType || 'mock'}
            style={{ width: 120 }}
            onChange={(value) => handleDataAccessChange(record.id, value)}
            size="small"
          >
            <Select.Option value="mock">
              <Tooltip title="仅能访问测试数据">
                <span>测试数据</span>
              </Tooltip>
            </Select.Option>
            <Select.Option value="real">
              <Tooltip title="仅能访问真实客户数据">
                <span>真实数据</span>
              </Tooltip>
            </Select.Option>
          </Select>
        );
      }
    },
    {
      title: '操作',
      key: 'action',
      render: (_: any, record: User) => (
        <div style={{ display: 'flex', gap: 8 }}>
          <Button icon={<EditOutlined />} onClick={() => handleEdit(record)}>编辑</Button>
          <Button icon={<KeyOutlined />} onClick={() => openResetPassword(record.id)}>重置密码</Button>
          <Tooltip title="下载设备日志">
            <Button icon={<FileTextOutlined />} onClick={() => handleDownloadLog(record)}>
              日志
            </Button>
          </Tooltip>
          <Popconfirm title="确定删除吗？" onConfirm={() => handleDelete(record.id)}>
            <Button danger icon={<DeleteOutlined />}>删除</Button>
          </Popconfirm>
        </div>
      ),
    },
  ];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
        <h2>人员管理</h2>
        <Space>
          {selectedUserIds.length > 0 && (
            <Button 
              danger 
              icon={<DeleteOutlined />} 
              onClick={handleBatchDelete}
            >
              删除选中 ({selectedUserIds.length})
            </Button>
          )}
          <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>
            添加人员
          </Button>
        </Space>
      </div>

      <Table 
        columns={columns} 
        dataSource={users} 
        rowKey="id" 
        loading={loading}
        rowSelection={{
          selectedRowKeys: selectedUserIds,
          onChange: (keys) => setSelectedUserIds(keys as number[]),
          selections: [
            Table.SELECTION_ALL,
            Table.SELECTION_INVERT,
            Table.SELECTION_NONE,
          ]
        }}
      />

      <Modal
        title={editingUser ? '编辑人员' : '添加人员'}
        open={modalVisible}
        onOk={() => form.submit()}
        onCancel={() => setModalVisible(false)}
        width={600}
      >
        <Form form={form} layout="vertical" onFinish={handleSubmit}>
          <Form.Item name="username" label="用户名" rules={[{ required: true }]}>
            <Input disabled={!!editingUser} />
          </Form.Item>
          {!editingUser && (
            <Form.Item name="password" label="密码" rules={[{ required: true, min: 6 }]}>
              <Input.Password />
            </Form.Item>
          )}
          <Form.Item name="real_name" label="姓名" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="role" label="角色" rules={[{ required: true }]}>
            <Select>
              <Select.Option value="admin">管理员</Select.Option>
              <Select.Option value="agent">客服</Select.Option>
            </Select>
          </Form.Item>
          <Form.Item name="phone" label="电话">
            <Input />
          </Form.Item>
          <Form.Item name="email" label="邮箱">
            <Input />
          </Form.Item>
          {editingUser && (
            <Form.Item name="status" label="状态">
              <Select>
                <Select.Option value="active">启用</Select.Option>
                <Select.Option value="inactive">禁用</Select.Option>
              </Select>
            </Form.Item>
          )}
        </Form>
      </Modal>

      <Modal
        title="重置密码"
        open={resetPasswordVisible}
        onOk={() => passwordForm.submit()}
        onCancel={() => {
          setResetPasswordVisible(false);
          passwordForm.resetFields();
        }}
      >
        <Form form={passwordForm} onFinish={handleResetPassword}>
          <Form.Item name="new_password" label="新密码" rules={[{ required: true, min: 6 }]}>
            <Input.Password />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
