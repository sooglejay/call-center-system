import { useEffect, useState } from 'react';
import { Table, Button, Modal, Form, Input, Select, DatePicker, message, Tag } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons';
import { taskApi, userApi } from '../../services/api';
import type { Task, User } from '../../services/api';
import dayjs from 'dayjs';

const { RangePicker } = DatePicker;

export default function TaskManagement() {
  const [tasks, setTasks] = useState<Task[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [editingTask, setEditingTask] = useState<Task | null>(null);
  const [agents, setAgents] = useState<User[]>([]);
  const [form] = Form.useForm();

  useEffect(() => {
    fetchTasks();
    fetchAgents();
  }, []);

  const fetchTasks = async () => {
    setLoading(true);
    try {
      const response = await taskApi.getTasks();
      setTasks(response.data.data);
    } finally {
      setLoading(false);
    }
  };

  const fetchAgents = async () => {
    try {
      const response = await userApi.getAgents();
      setAgents(response.data);
    } catch (error) {
      console.error('获取客服列表失败');
    }
  };

  const handleAdd = () => {
    setEditingTask(null);
    form.resetFields();
    setModalVisible(true);
  };

  const handleDelete = async (id: number) => {
    try {
      await taskApi.deleteTask(id);
      message.success('删除成功');
      fetchTasks();
    } catch (error) {
      message.error('删除失败');
    }
  };

  const handleSubmit = async (values: any) => {
    try {
      const data = {
        name: values.name,
        agent_id: values.agent_id,
        customer_ids: values.customer_ids?.split(',').map((id: string) => parseInt(id.trim())) || [],
        task_type: values.task_type,
        start_date: values.date_range[0].format('YYYY-MM-DD'),
        end_date: values.date_range[1].format('YYYY-MM-DD'),
      };

      if (editingTask) {
        await taskApi.updateTask(editingTask.id, data);
        message.success('更新成功');
      } else {
        await taskApi.createTask(data);
        message.success('创建成功');
      }
      setModalVisible(false);
      fetchTasks();
    } catch (error: any) {
      message.error(error.response?.data?.error || '操作失败');
    }
  };

  const columns = [
    { title: '任务名称', dataIndex: 'name', key: 'name' },
    { title: '分配客服', dataIndex: 'agent_name', key: 'agent_name' },
    { title: '客户数', dataIndex: 'customer_count', key: 'customer_count' },
    { title: '已完成', dataIndex: 'completed_count', key: 'completed_count' },
    { title: '任务类型', dataIndex: 'task_type', key: 'task_type', render: (type: string) => (
      <Tag color={type === 'daily' ? 'blue' : 'green'}>
        {type === 'daily' ? '日任务' : '周任务'}
      </Tag>
    )},
    { title: '开始日期', dataIndex: 'start_date', key: 'start_date' },
    { title: '结束日期', dataIndex: 'end_date', key: 'end_date' },
    { title: '状态', dataIndex: 'status', key: 'status', render: (status: string) => (
      <Tag color={status === 'active' ? 'green' : status === 'completed' ? 'blue' : 'default'}>
        {status === 'active' ? '进行中' : status === 'completed' ? '已完成' : '已取消'}
      </Tag>
    )},
    {
      title: '操作',
      key: 'action',
      render: (_: any, record: Task) => (
        <div style={{ display: 'flex', gap: 8 }}>
          <Button icon={<EditOutlined />} onClick={() => {
            setEditingTask(record);
            form.setFieldsValue({
              ...record,
              date_range: [dayjs(record.start_date), dayjs(record.end_date)]
            });
            setModalVisible(true);
          }}>编辑</Button>
          <Button danger icon={<DeleteOutlined />} onClick={() => handleDelete(record.id)}>删除</Button>
        </div>
      ),
    },
  ];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
        <h2>任务分配</h2>
        <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>
          创建任务
        </Button>
      </div>

      <Table columns={columns} dataSource={tasks} rowKey="id" loading={loading} />

      <Modal
        title={editingTask ? '编辑任务' : '创建任务'}
        open={modalVisible}
        onOk={() => form.submit()}
        onCancel={() => setModalVisible(false)}
        width={600}
      >
        <Form form={form} layout="vertical" onFinish={handleSubmit}>
          <Form.Item name="name" label="任务名称" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="agent_id" label="分配客服" rules={[{ required: true }]}>
            <Select placeholder="选择客服">
              {agents.map(agent => (
                <Select.Option key={agent.id} value={agent.id}>{agent.real_name}</Select.Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item name="customer_ids" label="客户ID列表" rules={[{ required: true }]}>
            <Input.TextArea placeholder="输入客户ID，用逗号分隔，例如：1,2,3,4,5" rows={3} />
          </Form.Item>
          <Form.Item name="task_type" label="任务类型" rules={[{ required: true }]}>
            <Select>
              <Select.Option value="daily">日任务</Select.Option>
              <Select.Option value="weekly">周任务</Select.Option>
            </Select>
          </Form.Item>
          <Form.Item name="date_range" label="任务时间范围" rules={[{ required: true }]}>
            <RangePicker style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
