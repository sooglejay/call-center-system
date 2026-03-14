import { useEffect, useState } from 'react';
import { Table, Button, Modal, Form, Input, Select, DatePicker, message, Tag, Tabs, Badge, Checkbox, Transfer, Space, Divider, Typography } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, UserOutlined, TeamOutlined } from '@ant-design/icons';
import { taskApi, userApi, customerApi } from '../../services/api';
import type { Task, User, Customer } from '../../services/api';
import dayjs from 'dayjs';

const { RangePicker } = DatePicker;
const { Text } = Typography;
const { TabPane } = Tabs;

// 获取姓氏首字母
const getFirstLetter = (name: string): string => {
  if (!name) return '#';
  const firstChar = name.charAt(0);
  if (/[\u4e00-\u9fa5]/.test(firstChar)) {
    const pinyinMap: Record<string, string> = {
      '阿': 'A', '艾': 'A', '安': 'A', '白': 'B', '班': 'B', '包': 'B', '鲍': 'B', '毕': 'B', '边': 'B', '卞': 'B',
      '蔡': 'C', '曹': 'C', '岑': 'C', '曾': 'C', '常': 'C', '陈': 'C', '程': 'C', '池': 'C', '褚': 'C', '楚': 'C', '崔': 'C',
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

export default function TaskManagement() {
  const [tasks, setTasks] = useState<Task[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [editingTask, setEditingTask] = useState<Task | null>(null);
  const [agents, setAgents] = useState<User[]>([]);
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [nameLetterStats, setNameLetterStats] = useState<Record<string, number>>({});
  const [selectedLetters, setSelectedLetters] = useState<string[]>([]);
  const [selectedCustomerIds, setSelectedCustomerIds] = useState<number[]>([]);
  const [customerSelectMode, setCustomerSelectMode] = useState<'id' | 'letter'>('id');
  const [unassignedOnly, setUnassignedOnly] = useState(false);
  const [form] = Form.useForm();

  useEffect(() => {
    fetchTasks();
    fetchAgents();
    fetchNameLetterStats();
  }, []);

  useEffect(() => {
    if (customerSelectMode === 'letter' && selectedLetters.length > 0) {
      fetchCustomersByLetter();
    } else if (customerSelectMode === 'id') {
      fetchAllCustomers();
    }
  }, [selectedLetters, customerSelectMode, unassignedOnly]);

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

  const fetchNameLetterStats = async () => {
    try {
      const response = await customerApi.getNameLetterStats(unassignedOnly);
      setNameLetterStats(response.data);
    } catch (error) {
      console.error('获取姓氏统计失败');
    }
  };

  const fetchCustomersByLetter = async () => {
    try {
      const letters = selectedLetters.join(',');
      const response = await customerApi.getCustomersByNameLetter(letters, unassignedOnly);
      setCustomers(response.data.data);
      // 自动选中这些客户
      setSelectedCustomerIds(response.data.data.map((c: Customer) => c.id));
    } catch (error) {
      console.error('获取客户列表失败');
    }
  };

  const fetchAllCustomers = async () => {
    try {
      const response = await customerApi.getCustomers({ 
        assigned_to: unassignedOnly ? 0 : undefined 
      });
      setCustomers(response.data.data);
    } catch (error) {
      console.error('获取客户列表失败');
    }
  };

  const handleAdd = () => {
    setEditingTask(null);
    setSelectedLetters([]);
    setSelectedCustomerIds([]);
    setCustomerSelectMode('id');
    form.resetFields();
    setModalVisible(true);
    fetchAllCustomers();
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
      const customerIds = customerSelectMode === 'letter' && selectedLetters.length > 0
        ? customers.filter(c => selectedLetters.includes(getFirstLetter(c.name || ''))).map(c => c.id)
        : selectedCustomerIds;

      if (customerIds.length === 0) {
        message.error('请至少选择一个客户');
        return;
      }

      // 创建多个任务（每个客户一个任务）
      const promises = customerIds.map((customerId: number) => {
        const data = {
          title: values.title,
          description: values.description || '',
          assigned_to: values.assigned_to,
          customer_id: customerId,
          priority: values.priority || 'normal',
          due_date: values.due_date?.format('YYYY-MM-DD'),
        };
        return taskApi.createTask(data);
      });

      await Promise.all(promises);
      message.success(`成功创建 ${customerIds.length} 个任务`);
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

  const columns = [
    { title: '任务名称', dataIndex: 'title', key: 'title' },
    { title: '分配客服', dataIndex: 'agent_name', key: 'agent_name' },
    { title: '客户', dataIndex: 'customer_id', key: 'customer_id', render: (id: number) => `客户 #${id}` },
    { title: '优先级', dataIndex: 'priority', key: 'priority', render: (p: string) => (
      <Tag color={p === 'urgent' ? 'red' : p === 'high' ? 'orange' : p === 'normal' ? 'blue' : 'default'}>
        {p === 'urgent' ? '紧急' : p === 'high' ? '高' : p === 'normal' ? '普通' : '低'}
      </Tag>
    )},
    { title: '截止日期', dataIndex: 'due_date', key: 'due_date' },
    { title: '状态', dataIndex: 'status', key: 'status', render: (status: string) => (
      <Tag color={status === 'completed' ? 'success' : status === 'in_progress' ? 'processing' : status === 'pending' ? 'warning' : 'default'}>
        {status === 'completed' ? '已完成' : status === 'in_progress' ? '进行中' : status === 'pending' ? '待处理' : '已取消'}
      </Tag>
    )},
    {
      title: '操作',
      key: 'action',
      render: (_: any, record: Task) => (
        <Space>
          <Button icon={<EditOutlined />} size="small" onClick={() => {
            setEditingTask(record);
            form.setFieldsValue({
              ...record,
              due_date: record.due_date ? dayjs(record.due_date) : undefined
            });
            setModalVisible(true);
          }}>编辑</Button>
          <Button danger icon={<DeleteOutlined />} size="small" onClick={() => handleDelete(record.id)}>删除</Button>
        </Space>
      ),
    },
  ];

  // Transfer 数据源
  const transferData = customers.map(c => ({
    key: c.id.toString(),
    title: `${c.name || '未命名'} (${c.phone})`,
    description: c.assigned_to_name || '未分配',
    letter: getFirstLetter(c.name || '')
  }));

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
        width={800}
        destroyOnClose
      >
        <Form form={form} layout="vertical" onFinish={handleSubmit}>
          <Form.Item name="title" label="任务名称" rules={[{ required: true }]}>
            <Input placeholder="例如：跟进意向客户" />
          </Form.Item>
          
          <Form.Item name="assigned_to" label="分配客服" rules={[{ required: true }]}>
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

          <Divider orientation="left">选择客户</Divider>

          {!editingTask && (
            <>
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
                <TabPane tab="按客户ID选择" key="id">
                  <Transfer
                    dataSource={transferData}
                    titles={['可选客户', '已选客户']}
                    targetKeys={selectedCustomerIds.map(String)}
                    onChange={(keys) => setSelectedCustomerIds(keys.map(Number))}
                    render={(item) => (
                      <Space>
                        <Badge count={item.letter} style={{ backgroundColor: '#1890ff', fontSize: 10 }} />
                        <span>{item.title}</span>
                        <Tag size="small" color={item.description === '未分配' ? 'default' : 'blue'}>
                          {item.description}
                        </Tag>
                      </Space>
                    )}
                    listStyle={{ width: 300, height: 300 }}
                    showSearch
                    filterOption={(inputValue, item) =>
                      item.title?.toLowerCase().includes(inputValue.toLowerCase()) ||
                      item.description?.toLowerCase().includes(inputValue.toLowerCase())
                    }
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

                  {customers.length > 0 && selectedLetters.length > 0 && (
                    <div style={{ maxHeight: 200, overflow: 'auto', border: '1px solid #d9d9d9', borderRadius: 4, padding: 8 }}>
                      <Text type="secondary" style={{ display: 'block', marginBottom: 8 }}>
                        预览匹配的客户：
                      </Text>
                      <Space wrap>
                        {customers
                          .filter(c => selectedLetters.includes(getFirstLetter(c.name || '')))
                          .map(c => (
                            <Tag key={c.id} size="small" icon={<TeamOutlined />}>
                              {c.name || '未命名'} ({c.phone})
                            </Tag>
                          ))}
                      </Space>
                    </div>
                  )}
                </TabPane>
              </Tabs>
            </>
          )}

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
        </Form>
      </Modal>
    </div>
  );
}
