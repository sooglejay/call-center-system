import { useEffect, useState, useMemo } from 'react';
import { Table, Button, Modal, Form, Input, Select, DatePicker, message, Tag, Tabs, Badge, Checkbox, Space, Divider, Typography, Empty, Alert, Progress, Card, Descriptions, Tooltip, Statistic, Row, Col } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, UserOutlined, TeamOutlined, ScheduleOutlined, InfoCircleOutlined, EyeOutlined, PhoneOutlined, CheckCircleOutlined, CloseCircleOutlined, ClockCircleOutlined, MessageOutlined, MinusCircleOutlined } from '@ant-design/icons';
import { taskApi, userApi, customerApi } from '../../services/api';
import type { Task, User, Customer } from '../../services/api';
import dayjs from 'dayjs';

const { Text } = Typography;
const { TabPane } = Tabs;

// 获取姓氏首字母
const getFirstLetter = (name: string): string => {
  if (!name) return '#';
  const firstChar = name.charAt(0);
  if (/[\u4e00-\u9fa5]/.test(firstChar)) {
    const pinyinMap: Record<string, string> = {
      '阿': 'A', '艾': 'A', '安': 'A', '白': 'B', '班': 'B', '包': 'B', '鲍': 'B', '毕': 'B', '边': 'B', '卞': 'B',
      '蔡': 'C', '曹': 'C', '岑': 'C', '常': 'C', '陈': 'C', '程': 'C', '池': 'C', '褚': 'C', '楚': 'C', '崔': 'C',
      '戴': 'D', '邓': 'D', '丁': 'D', '董': 'D', '杜': 'D', '段': 'D',
      '樊': 'F', '范': 'F', '方': 'F', '费': 'F', '冯': 'F', '符': 'F', '傅': 'F', '富': 'F',
      '高': 'G', '葛': 'G', '耿': 'G', '龚': 'G', '顾': 'G', '管': 'G', '郭': 'G',
      '韩': 'H', '郝': 'H', '何': 'H', '贺': 'H', '侯': 'H', '胡': 'H', '花': 'H', '华': 'H', '黄': 'H', '霍': 'H',
      '姬': 'J', '纪': 'J', '季': 'J', '贾': 'J', '简': 'J', '江': 'J', '姜': 'J', '蒋': 'J', '金': 'J', '靳': 'J', '景': 'J', '静': 'J',
      '康': 'K', '柯': 'K', '孔': 'K',
      '赖': 'L', '兰': 'L', '雷': 'L', '黎': 'L', '李': 'L', '梁': 'L', '林': 'L', '刘': 'L', '柳': 'L', '龙': 'L', '卢': 'L', '鲁': 'L', '陆': 'L', '路': 'L', '罗': 'L', '吕': 'L',
      '马': 'M', '毛': 'M', '茅': 'M', '梅': 'M', '孟': 'M', '米': 'M', '苗': 'M', '闵': 'M', '莫': 'M', '穆': 'M',
      '倪': 'N', '宁': 'N', '牛': 'N', '欧': 'O', '区': 'O',
      '潘': 'P', '庞': 'P', '裴': 'P', '彭': 'P', '皮': 'P', '朴': 'P',
      '齐': 'Q', '钱': 'Q', '乔': 'Q', '秦': 'Q', '邱': 'Q', '裘': 'Q', '曲': 'Q',
      '冉': 'R', '任': 'R', '荣': 'R', '阮': 'R',
      '沙': 'S', '邵': 'S', '沈': 'S', '盛': 'S', '施': 'S', '石': 'S', '史': 'S', '舒': 'S', '宋': 'S', '苏': 'S', '孙': 'S', '索': 'S',
      '汤': 'T', '唐': 'T', '陶': 'T', '田': 'T', '童': 'T',
      '万': 'W', '汪': 'W', '王': 'W', '韦': 'W', '卫': 'W', '魏': 'W', '温': 'W', '文': 'W', '翁': 'W', '巫': 'W', '吴': 'W', '伍': 'W', '武': 'W',
      '席': 'X', '夏': 'X', '项': 'X', '萧': 'X', '谢': 'X', '辛': 'X', '邢': 'X', '熊': 'X', '徐': 'X', '许': 'X', '薛': 'X',
      '严': 'Y', '颜': 'Y', '杨': 'Y', '叶': 'Y', '易': 'Y', '殷': 'Y', '尹': 'Y', '应': 'Y', '尤': 'Y', '于': 'Y', '余': 'Y', '俞': 'Y', '虞': 'Y', '袁': 'Y', '岳': 'Y', '云': 'Y',
      '藏': 'Z', '曾': 'Z', '翟': 'Z', '詹': 'Z', '张': 'Z', '章': 'Z', '赵': 'Z', '郑': 'Z', '钟': 'Z', '周': 'Z', '朱': 'Z', '诸': 'Z', '祝': 'Z', '庄': 'Z'
    };
    return pinyinMap[firstChar] || '#';
  }
  return firstChar.toUpperCase();
};

// 字母表
const ALPHABET = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ#'.split('');

// 任务详情类型
interface TaskDetail extends Task {
  customer_count: number;
  completed_count: number;
  called_count: number;
  progress: number;
  customers?: Array<{
    task_customer_id: number;
    id: number;
    name: string;
    phone: string;
    tag?: string;
    email?: string;
    company?: string;
    customer_status: string;
    call_status: string;
    call_result?: string;
    called_at?: string;
    call_id?: number;
    call_duration?: number;
    is_connected?: boolean;
    call_time?: string;
  }>;
}

export default function TaskManagement() {
  const [tasks, setTasks] = useState<TaskDetail[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [detailModalVisible, setDetailModalVisible] = useState(false);
  const [editingTask, setEditingTask] = useState<TaskDetail | null>(null);
  const [selectedTask, setSelectedTask] = useState<TaskDetail | null>(null);
  const [agents, setAgents] = useState<User[]>([]);
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [nameLetterStats, setNameLetterStats] = useState<Record<string, number>>({});
  const [selectedLetters, setSelectedLetters] = useState<string[]>([]);
  const [selectedCustomerIds, setSelectedCustomerIds] = useState<number[]>([]);
  const [customerSelectMode, setCustomerSelectMode] = useState<'id' | 'letter'>('id');
  const [unassignedOnly, setUnassignedOnly] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [form] = Form.useForm();
  
  // 任务详情中客户列表的通话状态筛选
  const [customerCallStatusFilter, setCustomerCallStatusFilter] = useState<string>('all');

  // 任务详情中客户列表的分页状态
  const [detailCustomerPage, setDetailCustomerPage] = useState(1);
  const [detailCustomerPageSize, setDetailCustomerPageSize] = useState(10);

  // 任务列表分页状态
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [total, setTotal] = useState(0);

  // 客户选择分页状态
  const [customerPage, setCustomerPage] = useState(1);
  const [customerPageSize, setCustomerPageSize] = useState(20);
  const [customerTotal, setCustomerTotal] = useState(0);
  const [customerSearch, setCustomerSearch] = useState('');

  useEffect(() => {
    fetchTasks();
    fetchAgents();
    fetchNameLetterStats();
  }, [currentPage, pageSize]);

  useEffect(() => {
    if (customerSelectMode === 'letter' && selectedLetters.length > 0) {
      fetchCustomersByLetter();
    } else if (customerSelectMode === 'id') {
      fetchAllCustomers();
    }
  }, [selectedLetters, customerSelectMode, unassignedOnly, customerPage, customerPageSize, customerSearch]);

  const fetchTasks = async () => {
    setLoading(true);
    try {
      const response = await taskApi.getTasks({
        page: currentPage,
        pageSize: pageSize
      });
      const tasksData = response.data?.data || response.data || [];
      setTasks(Array.isArray(tasksData) ? tasksData : []);
      setTotal(response.data?.total || 0);
    } catch (error: any) {
      message.error(error.response?.data?.error || '获取任务列表失败，请刷新重试');
    } finally {
      setLoading(false);
    }
  };

  const fetchAgents = async () => {
    try {
      const response = await userApi.getAgents();
      const agentsData = response.data || [];
      setAgents(Array.isArray(agentsData) ? agentsData : []);
    } catch (error) {
      console.error('获取客服列表失败');
    }
  };

  const fetchNameLetterStats = async () => {
    try {
      const response = await customerApi.getNameLetterStats(unassignedOnly);
      setNameLetterStats(response.data || {});
    } catch (error) {
      console.error('获取姓氏统计失败');
    }
  };

  const fetchCustomersByLetter = async () => {
    try {
      const letters = selectedLetters.join(',');
      const response = await customerApi.getCustomersByNameLetter(letters, unassignedOnly);
      const customersData = response.data?.data || response.data || [];
      const customersArray = Array.isArray(customersData) ? customersData : [];
      setCustomers(customersArray);
      setSelectedCustomerIds(customersArray.map((c: Customer) => c.id));
    } catch (error) {
      console.error('获取客户列表失败');
    }
  };

  const fetchAllCustomers = async () => {
    try {
      const response = await customerApi.getCustomers({ 
        assigned_to: unassignedOnly ? 0 : undefined,
        page: customerPage,
        pageSize: customerPageSize,
        search: customerSearch || undefined
      });
      const customersData = response.data?.data || response.data || [];
      setCustomers(Array.isArray(customersData) ? customersData : []);
      setCustomerTotal(response.data?.total || 0);
    } catch (error) {
      console.error('获取客户列表失败');
    }
  };

  const fetchTaskDetail = async (taskId: number) => {
    setDetailLoading(true);
    try {
      const response = await taskApi.getTaskById(taskId);
      setSelectedTask(response.data);
    } catch (error: any) {
      message.error(error.response?.data?.error || '获取任务详情失败');
    } finally {
      setDetailLoading(false);
    }
  };

  const handleAdd = () => {
    setEditingTask(null);
    setSelectedLetters([]);
    setSelectedCustomerIds([]);
    setCustomerSelectMode('id');
    setCustomerPage(1);
    setCustomerSearch('');
    form.resetFields();
    setModalVisible(true);
    fetchAllCustomers();
  };

  const handleViewDetail = (task: TaskDetail) => {
    setSelectedTask(task);
    setDetailModalVisible(true);
    fetchTaskDetail(task.id);
  };

  const handleEdit = (task: TaskDetail) => {
    setEditingTask(task);
    form.setFieldsValue({
      title: task.title,
      description: task.description,
      assigned_to: task.assigned_to,
      priority: task.priority,
      status: task.status,
      due_date: task.due_date ? dayjs(task.due_date) : undefined
    });
    setModalVisible(true);
  };

  const handleDelete = async (id: number) => {
    try {
      await taskApi.deleteTask(id);
      message.success('删除任务成功');
      fetchTasks();
    } catch (error: any) {
      message.error(error.response?.data?.error || '删除任务失败，请重试');
    }
  };

  const handleSubmit = async (values: any) => {
    try {
      if (editingTask) {
        // 编辑模式：只更新任务基本信息
        await taskApi.updateTask(editingTask.id, {
          title: values.title,
          description: values.description,
          assigned_to: values.assigned_to,
          priority: values.priority,
          status: values.status,
          due_date: values.due_date?.format('YYYY-MM-DD')
        });
        message.success('任务更新成功');
      } else {
        // 创建模式：创建任务并关联客户
        const customerIds = customerSelectMode === 'letter' && selectedLetters.length > 0
          ? customers.filter(c => selectedLetters.includes(getFirstLetter(c.name || ''))).map(c => c.id)
          : selectedCustomerIds;

        if (customerIds.length === 0) {
          message.error('请至少选择一个客户');
          return;
        }

        await taskApi.createTask({
          title: values.title,
          description: values.description || '',
          assigned_to: values.assigned_to,
          customer_ids: customerIds,
          priority: values.priority || 'normal',
          due_date: values.due_date?.format('YYYY-MM-DD')
        });
        message.success(`成功创建任务，包含 ${customerIds.length} 个客户`);
      }
      
      setModalVisible(false);
      fetchTasks();
    } catch (error: any) {
      message.error(error.response?.data?.error || '操作失败');
    }
  };

  const handleLetterToggle = (letter: string) => {
    setSelectedLetters(prev => 
      prev.includes(letter) 
        ? prev.filter(l => l !== letter)
        : [...prev, letter]
    );
  };

  // 根据通话状态筛选客户
  const filteredCustomers = useMemo(() => {
    if (!selectedTask?.customers) return [];
    if (customerCallStatusFilter === 'all') return selectedTask.customers;

    return selectedTask.customers.filter(customer => {
      const status = customer.call_status || 'pending';
      const result = customer.call_result;

      if (customerCallStatusFilter === 'called') {
        // "其他已拨打" 包括 called 和 completed 状态
        return status === 'called' || status === 'completed';
      }

      if (customerCallStatusFilter === 'pending') {
        // 待拨打：call_status 为 pending
        return status === 'pending';
      }

      if (customerCallStatusFilter === 'completed') {
        // 已完成：call_status 为 completed
        return status === 'completed';
      }

      if (customerCallStatusFilter === 'failed') {
        // 拨打失败：call_status 为 failed
        return status === 'failed';
      }

      // 其他筛选器根据 call_result 匹配
      // 需要同时满足：已拨打（status != pending）且 call_result 匹配
      if (status === 'pending') {
        return false;
      }

      // 根据 call_result 匹配各种状态
      const resultMap: Record<string, string[]> = {
        'connected': ['已接听', 'connected'],
        'voicemail': ['语音信箱', 'voicemail'],
        'unanswered': ['响铃未接', 'unanswered'],
        'rejected': ['对方拒接', 'rejected'],
        'busy': ['对方忙线', 'busy'],
        'power_off': ['关机/停机', 'power_off'],
        'no_answer': ['无人接听', 'no_answer'],
        'ivr': ['IVR语音', 'ivr'],
        'other': ['其他', 'other']
      };

      const validResults = resultMap[customerCallStatusFilter];
      if (validResults && result) {
        return validResults.includes(result);
      }

      return false;
    });
  }, [selectedTask?.customers, customerCallStatusFilter]);

  // 当筛选条件或任务变化时，重置分页到第一页
  useEffect(() => {
    setDetailCustomerPage(1);
  }, [selectedTask?.id, customerCallStatusFilter]);

  // 任务状态标签
  const renderStatusTag = (status: string) => {
    const statusConfig: Record<string, { color: string; text: string }> = {
      pending: { color: 'warning', text: '待处理' },
      in_progress: { color: 'processing', text: '进行中' },
      completed: { color: 'success', text: '已完成' },
      cancelled: { color: 'default', text: '已取消' }
    };
    const config = statusConfig[status] || statusConfig.pending;
    return <Tag color={config.color}>{config.text}</Tag>;
  };

  // 拨打状态标签 - 与 Android 端对齐
  const renderCallStatusTag = (status: string, callResult?: string) => {
    // 优先根据 call_result 显示更详细的状态
    if (callResult) {
      const resultConfig: Record<string, { color: string; text: string; icon: React.ReactNode }> = {
        '已接听': { color: 'success', text: '已接听', icon: <CheckCircleOutlined /> },
        'connected': { color: 'success', text: '已接听', icon: <CheckCircleOutlined /> },
        '语音信箱': { color: 'blue', text: '语音信箱', icon: <MessageOutlined /> },
        'voicemail': { color: 'blue', text: '语音信箱', icon: <MessageOutlined /> },
        '响铃未接': { color: 'orange', text: '响铃未接', icon: <CloseCircleOutlined /> },
        'unanswered': { color: 'orange', text: '响铃未接', icon: <CloseCircleOutlined /> },
        '对方拒接': { color: 'red', text: '对方拒接', icon: <CloseCircleOutlined /> },
        'rejected': { color: 'red', text: '对方拒接', icon: <CloseCircleOutlined /> },
        '对方忙线': { color: 'orange', text: '对方忙线', icon: <ClockCircleOutlined /> },
        'busy': { color: 'orange', text: '对方忙线', icon: <ClockCircleOutlined /> },
        '关机/停机': { color: 'default', text: '关机/停机', icon: <CloseCircleOutlined /> },
        'power_off': { color: 'default', text: '关机/停机', icon: <CloseCircleOutlined /> },
        '无人接听': { color: 'default', text: '无人接听', icon: <ClockCircleOutlined /> },
        'no_answer': { color: 'default', text: '无人接听', icon: <ClockCircleOutlined /> },
        'IVR语音': { color: 'cyan', text: 'IVR语音', icon: <MessageOutlined /> },
        'ivr': { color: 'cyan', text: 'IVR语音', icon: <MessageOutlined /> },
        '其他': { color: 'default', text: '其他', icon: <MinusCircleOutlined /> },
        'other': { color: 'default', text: '其他', icon: <MinusCircleOutlined /> }
      };
      const config = resultConfig[callResult];
      if (config) {
        return <Tag color={config.color} icon={config.icon}>{config.text}</Tag>;
      }
    }
    
    // 根据 call_status 显示基础状态
    const statusConfig: Record<string, { color: string; text: string; icon: React.ReactNode }> = {
      pending: { color: 'default', text: '待拨打', icon: <ClockCircleOutlined /> },
      called: { color: 'processing', text: '已拨打', icon: <PhoneOutlined /> },
      connected: { color: 'success', text: '已接听', icon: <CheckCircleOutlined /> },
      voicemail: { color: 'blue', text: '语音信箱', icon: <MessageOutlined /> },
      unanswered: { color: 'orange', text: '响铃未接', icon: <CloseCircleOutlined /> },
      failed: { color: 'error', text: '拨打失败', icon: <CloseCircleOutlined /> },
      completed: { color: 'success', text: '已完成', icon: <CheckCircleOutlined /> }
    };
    const config = statusConfig[status] || statusConfig.pending;
    return <Tag color={config.color} icon={config.icon}>{config.text}</Tag>;
  };

  // 任务列表列定义
  const columns = [
    { 
      title: '任务名称', 
      dataIndex: 'title', 
      key: 'title',
      render: (title: string) => <Text strong>{title}</Text>
    },
    { 
      title: '分配客服', 
      key: 'agent',
      render: (_: any, record: TaskDetail) => (
        <Space>
          <UserOutlined />
          {record.assigned_agent?.real_name || '未分配'}
        </Space>
      )
    },
    { 
      title: '客户数量', 
      key: 'customers',
      render: (_: any, record: TaskDetail) => (
        <Space>
          <TeamOutlined />
          <Text>{record.customer_count || 0} 人</Text>
        </Space>
      )
    },
    { 
      title: '完成进度', 
      key: 'progress',
      render: (_: any, record: TaskDetail) => {
        const progress = record.progress || 0;
        return (
          <div style={{ width: 120 }}>
            <Progress 
              percent={progress} 
              size="small" 
              status={progress === 100 ? 'success' : 'active'}
              format={() => `${record.completed_count || 0}/${record.customer_count || 0}`}
            />
          </div>
        );
      }
    },
    { 
      title: '优先级', 
      dataIndex: 'priority', 
      key: 'priority', 
      render: (p: string) => {
        const priorityConfig: Record<string, { color: string; text: string }> = {
          urgent: { color: 'red', text: '紧急' },
          high: { color: 'orange', text: '高' },
          normal: { color: 'blue', text: '普通' },
          low: { color: 'default', text: '低' }
        };
        const config = priorityConfig[p] || priorityConfig.normal;
        return <Tag color={config.color}>{config.text}</Tag>;
      }
    },
    { 
      title: '截止日期', 
      dataIndex: 'due_date', 
      key: 'due_date',
      render: (date: string) => date || '-'
    },
    { 
      title: '状态', 
      dataIndex: 'status', 
      key: 'status', 
      render: (status: string) => renderStatusTag(status)
    },
    {
      title: '操作',
      key: 'action',
      render: (_: any, record: TaskDetail) => (
        <Space>
          <Tooltip title="查看详情">
            <Button 
              icon={<EyeOutlined />} 
              size="small" 
              onClick={() => handleViewDetail(record)}
            >
              详情
            </Button>
          </Tooltip>
          <Tooltip title="编辑">
            <Button 
              icon={<EditOutlined />} 
              size="small" 
              onClick={() => handleEdit(record)}
            />
          </Tooltip>
          <Tooltip title="删除">
            <Button 
              danger 
              icon={<DeleteOutlined />} 
              size="small" 
              onClick={() => {
                Modal.confirm({
                  title: '确认删除',
                  content: `确定要删除任务「${record.title}」吗？`,
                  onOk: () => handleDelete(record.id)
                });
              }}
            />
          </Tooltip>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
        <h2><ScheduleOutlined style={{ marginRight: 8 }} />任务管理</h2>
        <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>
          创建任务
        </Button>
      </div>

      {tasks.length === 0 && !loading ? (
        <Alert
          message="暂无任务数据"
          description={
            <div>
              <p>系统中还没有任务，请先创建任务：</p>
              <Button 
                type="primary" 
                icon={<PlusOutlined />}
                onClick={handleAdd}
                style={{ marginTop: 8 }}
              >
                创建任务
              </Button>
            </div>
          }
          type="info"
          showIcon
          icon={<InfoCircleOutlined />}
        />
      ) : (
        <Table 
          columns={columns} 
          dataSource={tasks} 
          rowKey="id" 
          loading={loading}
          pagination={{
            current: currentPage,
            pageSize: pageSize,
            total: total,
            showSizeChanger: true,
            showQuickJumper: true,
            showTotal: (total) => `共 ${total} 条`,
            onChange: (page, size) => {
              setCurrentPage(page);
              if (size) setPageSize(size);
            }
          }}
          locale={{
            emptyText: (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description="暂无任务数据"
              />
            )
          }}
        />
      )}

      {/* 创建/编辑任务弹窗 */}
      <Modal
        title={editingTask ? '编辑任务' : '创建任务'}
        open={modalVisible}
        onOk={() => form.submit()}
        onCancel={() => setModalVisible(false)}
        width={800}
        destroyOnClose
      >
        <Form form={form} layout="vertical" onFinish={handleSubmit}>
          <Form.Item name="title" label="任务名称" rules={[{ required: true, message: '请输入任务名称' }]}>
            <Input placeholder="例如：本周回访任务" />
          </Form.Item>
          
          <Form.Item name="assigned_to" label="分配客服" rules={[{ required: true, message: '请选择客服' }]}>
            <Select placeholder="选择客服">
              {agents.map(agent => (
                <Select.Option key={agent.id} value={agent.id}>
                  <Space>
                    <UserOutlined />
                    {agent.real_name}
                  </Space>
                </Select.Option>
              ))}
            </Select>
          </Form.Item>

          <Form.Item name="priority" label="优先级" initialValue="normal">
            <Select>
              <Select.Option value="urgent">紧急</Select.Option>
              <Select.Option value="high">高</Select.Option>
              <Select.Option value="normal">普通</Select.Option>
              <Select.Option value="low">低</Select.Option>
            </Select>
          </Form.Item>

          <Form.Item name="due_date" label="截止日期">
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>

          <Form.Item name="description" label="任务描述">
            <Input.TextArea rows={2} placeholder="可选：添加任务详细说明" />
          </Form.Item>

          {/* 编辑模式显示状态选择 */}
          {editingTask && (
            <Form.Item name="status" label="任务状态">
              <Select>
                <Select.Option value="pending">待处理</Select.Option>
                <Select.Option value="in_progress">进行中</Select.Option>
                <Select.Option value="completed">已完成</Select.Option>
                <Select.Option value="cancelled">已取消</Select.Option>
              </Select>
            </Form.Item>
          )}

          {/* 创建模式显示客户选择 */}
          {!editingTask && (
            <>
              <Divider orientation="left">选择客户</Divider>

              <div style={{ marginBottom: 16 }}>
                <Space>
                  <Checkbox 
                    checked={unassignedOnly}
                    onChange={(e) => {
                      setUnassignedOnly(e.target.checked);
                      fetchNameLetterStats();
                    }}
                  >
                    只显示未分配客户
                  </Checkbox>
                </Space>
              </div>

              <Tabs activeKey={customerSelectMode} onChange={(k) => setCustomerSelectMode(k as 'id' | 'letter')}>
                <TabPane tab="按客户选择" key="id">
                  <div style={{ marginBottom: 16 }}>
                    <Input.Search
                      placeholder="搜索客户姓名或电话"
                      value={customerSearch}
                      onChange={(e) => setCustomerSearch(e.target.value)}
                      onSearch={() => {
                        setCustomerPage(1);
                        fetchAllCustomers();
                      }}
                      style={{ width: 250 }}
                      allowClear
                    />
                  </div>
                  <Table
                    rowSelection={{
                      selectedRowKeys: selectedCustomerIds,
                      onChange: (keys) => setSelectedCustomerIds(keys as number[]),
                      preserveSelectedRowKeys: true
                    }}
                    columns={[
                      {
                        title: '姓名',
                        dataIndex: 'name',
                        key: 'name',
                        render: (name: string) => (
                          <Space>
                            <Badge count={getFirstLetter(name || '')} style={{ backgroundColor: '#1890ff', fontSize: 10 }} />
                            <span>{name || '未命名'}</span>
                          </Space>
                        )
                      },
                      { title: '电话', dataIndex: 'phone', key: 'phone' },
                      {
                        title: '分配状态',
                        dataIndex: 'assigned_to_name',
                        key: 'assigned_to_name',
                        render: (name: string) => (
                          <Tag color={name === '未分配' ? 'default' : 'blue'}>
                            {name || '未分配'}
                          </Tag>
                        )
                      }
                    ]}
                    dataSource={customers}
                    rowKey="id"
                    pagination={{
                      current: customerPage,
                      pageSize: customerPageSize,
                      total: customerTotal,
                      showSizeChanger: true,
                      showQuickJumper: true,
                      showTotal: (total) => `共 ${total} 条`,
                      onChange: (page, size) => {
                        setCustomerPage(page);
                        if (size) setCustomerPageSize(size);
                      }
                    }}
                    size="small"
                    scroll={{ y: 300 }}
                  />
                  <div style={{ marginTop: 8, textAlign: 'center' }}>
                    <Text type="secondary">已选择 {selectedCustomerIds.length} 个客户</Text>
                  </div>
                </TabPane>

                <TabPane tab="按姓氏首字母选择" key="letter">
                  <div style={{ marginBottom: 16 }}>
                    <Text type="secondary">点击字母选择该姓氏的所有客户（支持多选）</Text>
                  </div>
                  <Space wrap style={{ marginBottom: 16 }}>
                    {ALPHABET.map(letter => {
                      const count = nameLetterStats[letter] || 0;
                      const isSelected = selectedLetters.includes(letter);
                      return (
                        <Button
                          key={letter}
                          type={isSelected ? 'primary' : 'default'}
                          size="small"
                          disabled={count === 0}
                          onClick={() => handleLetterToggle(letter)}
                          style={isSelected ? { backgroundColor: '#1890ff', color: '#fff' } : {}}
                        >
                          {letter}
                          {count > 0 && <span style={{ marginLeft: 4, fontSize: 10 }}>({count})</span>}
                        </Button>
                      );
                    })}
                  </Space>
                  
                  {selectedLetters.length > 0 && (
                    <div style={{ 
                      padding: 12, 
                      background: '#f6ffed', 
                      border: '1px solid #b7eb8f', 
                      borderRadius: 4,
                      marginBottom: 16
                    }}>
                      <Space direction="vertical" style={{ width: '100%' }}>
                        <div>
                          <Text strong>已选择姓氏: </Text>
                          {selectedLetters.map(letter => (
                            <Tag 
                              key={letter} 
                              color="blue" 
                              closable 
                              onClose={() => handleLetterToggle(letter)}
                              style={{ margin: '0 4px 4px 0' }}
                            >
                              {letter}
                            </Tag>
                          ))}
                        </div>
                        <div>
                          <Text type="success">
                            将自动包含 {customers.filter(c => selectedLetters.includes(getFirstLetter(c.name || ''))).length} 个客户
                          </Text>
                        </div>
                      </Space>
                    </div>
                  )}
                </TabPane>
              </Tabs>
            </>
          )}
        </Form>
      </Modal>

      {/* 任务详情弹窗 */}
      <Modal
        title={
          <Space>
            <ScheduleOutlined />
            {selectedTask?.title || '任务详情'}
          </Space>
        }
        open={detailModalVisible}
        onCancel={() => {
          setDetailModalVisible(false);
          setSelectedTask(null);
        }}
        footer={null}
        width={900}
      >
        {detailLoading ? (
          <div style={{ textAlign: 'center', padding: 40 }}>加载中...</div>
        ) : selectedTask ? (
          <div>
            {/* 任务基本信息 */}
            <Card size="small" style={{ marginBottom: 16 }}>
              <Descriptions column={4} size="small">
                <Descriptions.Item label="分配客服">
                  <Space>
                    <UserOutlined />
                    {selectedTask.assigned_agent?.real_name || '未分配'}
                  </Space>
                </Descriptions.Item>
                <Descriptions.Item label="优先级">
                  <Tag color={selectedTask.priority === 'urgent' ? 'red' : selectedTask.priority === 'high' ? 'orange' : 'blue'}>
                    {selectedTask.priority === 'urgent' ? '紧急' : selectedTask.priority === 'high' ? '高' : '普通'}
                  </Tag>
                </Descriptions.Item>
                <Descriptions.Item label="截止日期">{selectedTask.due_date || '-'}</Descriptions.Item>
                <Descriptions.Item label="状态">{renderStatusTag(selectedTask.status)}</Descriptions.Item>
              </Descriptions>
              {selectedTask.description && (
                <div style={{ marginTop: 12 }}>
                  <Text type="secondary">任务描述：{selectedTask.description}</Text>
                </div>
              )}
            </Card>

            {/* 统计概览 */}
            <Row gutter={16} style={{ marginBottom: 16 }}>
              <Col span={6}>
                <Card size="small">
                  <Statistic 
                    title="客户总数" 
                    value={selectedTask.customer_count || 0}
                    prefix={<TeamOutlined />}
                  />
                </Card>
              </Col>
              <Col span={6}>
                <Card size="small">
                  <Statistic 
                    title="已拨打" 
                    value={selectedTask.called_count || 0}
                    prefix={<PhoneOutlined />}
                    valueStyle={{ color: '#1890ff' }}
                  />
                </Card>
              </Col>
              <Col span={6}>
                <Card size="small">
                  <Statistic 
                    title="已完成" 
                    value={selectedTask.completed_count || 0}
                    prefix={<CheckCircleOutlined />}
                    valueStyle={{ color: '#52c41a' }}
                  />
                </Card>
              </Col>
              <Col span={6}>
                <Card size="small">
                  <Statistic 
                    title="完成率" 
                    value={selectedTask.progress || 0}
                    suffix="%"
                    valueStyle={{ color: selectedTask.progress === 100 ? '#52c41a' : '#1890ff' }}
                  />
                </Card>
              </Col>
            </Row>

            {/* 客户列表 */}
            <Card 
              title={
                <Space>
                  <span>客户列表及拨打情况</span>
                  {selectedTask.customers && (
                    <Tag color="blue">共 {filteredCustomers.length} 人</Tag>
                  )}
                </Space>
              }
              size="small"
              extra={
                <Select
                  value={customerCallStatusFilter}
                  onChange={setCustomerCallStatusFilter}
                  style={{ width: 120 }}
                  size="small"
                  options={[
                    { value: 'all', label: '全部客户' },
                    { value: 'pending', label: '待拨打' },
                    { value: 'connected', label: '已接听' },
                    { value: 'voicemail', label: '语音信箱' },
                    { value: 'unanswered', label: '响铃未接' },
                    { value: 'rejected', label: '对方拒接' },
                    { value: 'busy', label: '对方忙线' },
                    { value: 'power_off', label: '关机/停机' },
                    { value: 'no_answer', label: '无人接听' },
                    { value: 'ivr', label: 'IVR语音' },
                    { value: 'failed', label: '拨打失败' },
                    { value: 'completed', label: '已完成' },
                    { value: 'called', label: '其他已拨打' },              
                    { value: 'other', label: '其他' }
                  ]}
                />
              }
            >
              <Table
                dataSource={filteredCustomers}
                rowKey="task_customer_id"
                size="small"
                pagination={{
                  current: detailCustomerPage,
                  pageSize: detailCustomerPageSize,
                  showSizeChanger: true,
                  showQuickJumper: true,
                  showTotal: (total) => `共 ${total} 条`,
                  onChange: (page, size) => {
                    setDetailCustomerPage(page);
                    if (size) setDetailCustomerPageSize(size);
                  }
                }}
                columns={[
                  { 
                    title: '客户姓名', 
                    dataIndex: 'name', 
                    key: 'name',
                    render: (name: string) => <Text strong>{name}</Text>
                  },
                  { 
                    title: '电话', 
                    dataIndex: 'phone', 
                    key: 'phone'
                  },
                  {
                    title: '标签',
                    dataIndex: 'tag',
                    key: 'tag',
                    render: (tag: string) => (
                      <Tag color={tag === '未打标客户' ? 'default' : 'geekblue'}>
                        {tag || '未打标客户'}
                      </Tag>
                    )
                  },
                  { 
                    title: '公司', 
                    dataIndex: 'company', 
                    key: 'company',
                    render: (company: string) => company || '-'
                  },
                  { 
                    title: '拨打状态', 
                    dataIndex: 'call_status', 
                    key: 'call_status',
                    render: (status: string, record: any) => renderCallStatusTag(status, record.call_result)
                  },
                  { 
                    title: '通话时长', 
                    dataIndex: 'call_duration', 
                    key: 'call_duration',
                    render: (duration: number) => duration ? `${duration}秒` : '-'
                  },
                  { 
                    title: '拨打时间', 
                    dataIndex: 'called_at', 
                    key: 'called_at',
                    render: (time: string) => time ? dayjs(time).format('MM-DD HH:mm') : '-'
                  },
                  { 
                    title: '备注', 
                    dataIndex: 'call_result', 
                    key: 'call_result',
                    render: (result: string) => result || '-'
                  }
                ]}
                locale={{
                  emptyText: (
                    <Empty
                      image={Empty.PRESENTED_IMAGE_SIMPLE}
                      description="暂无客户"
                    />
                  )
                }}
              />
            </Card>
          </div>
        ) : null}
      </Modal>
    </div>
  );
}
