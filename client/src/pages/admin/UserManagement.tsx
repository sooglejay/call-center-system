import { useEffect, useState } from 'react';
import { Table, Button, Modal, Form, Input, Select, Popconfirm, message, Tag, Tooltip } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, KeyOutlined } from '@ant-design/icons';
import { userApi } from '../../services/api';
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

  useEffect(() => {
    fetchUsers();
  }, []);

  const fetchUsers = async () => {
    setLoading(true);
    try {
      const response = await userApi.getUsers();
      setUsers(response.data);
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
    try {
      await userApi.deleteUser(id);
      message.success('删除成功');
      fetchUsers();
    } catch (error) {
      message.error('删除失败');
    }
  };

  const handleSubmit = async (values: any) => {
    try {
      if (editingUser) {
        await userApi.updateUser(editingUser.id, values);
        message.success('更新成功');
      } else {
        await userApi.createUser(values);
        message.success('创建成功');
      }
      setModalVisible(false);
      fetchUsers();
    } catch (error: any) {
      message.error(error.response?.data?.error || '操作失败');
    }
  };

  const handleResetPassword = async (values: any) => {
    if (!resetUserId) return;
    try {
      await userApi.resetPassword(resetUserId, values.new_password);
      message.success('密码重置成功');
      setResetPasswordVisible(false);
      passwordForm.resetFields();
    } catch (error) {
      message.error('密码重置失败');
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
        <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>
          添加人员
        </Button>
      </div>

      <Table columns={columns} dataSource={users} rowKey="id" loading={loading} />

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
