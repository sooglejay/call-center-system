import { useEffect, useState, useCallback } from 'react';
import { Card, Button, Table, Tag, Space, message, Modal, Input, Radio, Progress, Row, Col, Statistic, Divider, Empty } from 'antd';
import { 
  PhoneOutlined, 
  CloseCircleOutlined, 
  ArrowLeftOutlined,
  CustomerServiceOutlined,
  ClockCircleOutlined,
  CheckSquareOutlined
} from '@ant-design/icons';
import { taskApi, twilioApi } from '../../services/api';
import type { Task } from '../../services/api';
import { useNavigate, useParams } from 'react-router-dom';

interface TaskCustomer {
  task_customer_id: number;
  id: number;
  name: string;
  phone: string;
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
}

interface TaskDetail extends Task {
  customer_count: number;
  completed_count: number;
  called_count: number;
  progress: number;
  customers?: TaskCustomer[];
}

// 通话结果选项
const CALL_RESULTS = [
  { value: 'interested', label: '有意向', color: 'green' },
  { value: 'not_interested', label: '无意向', color: 'orange' },
  { value: 'callback', label: '需回拨', color: 'blue' },
  { value: 'wrong_number', label: '号码错误', color: 'red' },
  { value: 'no_answer', label: '无人接听', color: 'default' },
  { value: 'busy', label: '占线', color: 'default' },
  { value: 'voicemail', label: '语音信箱', color: 'purple' },
  { value: 'other', label: '其他', color: 'default' }
];

export default function AgentTaskExecution() {
  const { taskId } = useParams<{ taskId: string }>();
  const navigate = useNavigate();
  const [task, setTask] = useState<TaskDetail | null>(null);
  const [customers, setCustomers] = useState<TaskCustomer[]>([]);
  const [loading, setLoading] = useState(false);
  const [currentCustomer, setCurrentCustomer] = useState<TaskCustomer | null>(null);
  const [callResultModalVisible, setCallResultModalVisible] = useState(false);
  const [selectedResult, setSelectedResult] = useState<string>('');
  const [callNotes, setCallNotes] = useState('');
  const [isCalling, setIsCalling] = useState(false);

  const fetchTaskDetail = useCallback(async () => {
    if (!taskId) return;
    setLoading(true);
    try {
      const response = await taskApi.getTaskById(Number(taskId));
      const taskData = response.data;
      setTask(taskData);
      setCustomers(taskData.customers || []);
      
      // 自动选择第一个待拨打的客户
      const pendingCustomers = (taskData.customers || []).filter(
        (c: TaskCustomer) => c.call_status === 'pending'
      );
      if (pendingCustomers.length > 0 && !currentCustomer) {
        setCurrentCustomer(pendingCustomers[0]);
      }
    } catch (error: any) {
      message.error(error.response?.data?.error || '获取任务详情失败');
    } finally {
      setLoading(false);
    }
  }, [taskId, currentCustomer]);

  useEffect(() => {
    fetchTaskDetail();
  }, [fetchTaskDetail]);

  // 拨打电话
  const handleCallCustomer = async (customer: TaskCustomer) => {
    setCurrentCustomer(customer);
    setIsCalling(true);
    
    try {
      // 先更新状态为已拨打
      await taskApi.updateCustomerStatus(Number(taskId), customer.id, {
        status: 'called'
      });
      
      // 调用 Twilio 拨打电话
      await twilioApi.makeCall({
        to: customer.phone,
        customer_id: customer.id,
        task_id: Number(taskId)
      });
      
      message.success(`正在拨打 ${customer.name} - ${customer.phone}`);
      
      // 显示通话结果录入弹窗
      setTimeout(() => {
        setCallResultModalVisible(true);
      }, 2000);
      
    } catch (error: any) {
      message.error(error.response?.data?.error || '拨打电话失败');
    } finally {
      setIsCalling(false);
    }
  };

  // 跳过当前客户
  const handleSkipCustomer = () => {
    const currentIndex = customers.findIndex(c => c.id === currentCustomer?.id);
    const nextCustomer = customers.slice(currentIndex + 1).find(c => c.call_status === 'pending');
    
    if (nextCustomer) {
      setCurrentCustomer(nextCustomer);
      message.info(`已跳过，下一个客户: ${nextCustomer.name}`);
    } else {
      // 查找从头开始的待拨打客户
      const firstPending = customers.find(c => c.call_status === 'pending');
      if (firstPending && firstPending.id !== currentCustomer?.id) {
        setCurrentCustomer(firstPending);
        message.info(`已跳过，下一个客户: ${firstPending.name}`);
      } else {
        message.info('没有更多待拨打的客户了');
      }
    }
  };

  // 提交通话结果
  const handleSubmitCallResult = async () => {
    if (!currentCustomer || !taskId) return;
    
    try {
      const isConnected = selectedResult === 'interested' || selectedResult === 'not_interested' || selectedResult === 'callback';
      const finalStatus = isConnected ? 'connected' : 'failed';
      
      await taskApi.updateCustomerStatus(Number(taskId), currentCustomer.id, {
        status: finalStatus,
        call_result: callNotes || selectedResult
      });
      
      message.success('通话结果已保存');
      setCallResultModalVisible(false);
      setSelectedResult('');
      setCallNotes('');
      
      // 刷新数据并自动切换到下一个客户
      await fetchTaskDetail();
      
      // 自动选择下一个待拨打客户
      const currentIndex = customers.findIndex(c => c.id === currentCustomer.id);
      const nextCustomer = customers.slice(currentIndex + 1).find(c => c.call_status === 'pending');
      
      if (nextCustomer) {
        setCurrentCustomer(nextCustomer);
      } else {
        const firstPending = customers.find(c => c.call_status === 'pending' && c.id !== currentCustomer.id);
        if (firstPending) {
          setCurrentCustomer(firstPending);
        } else {
          setCurrentCustomer(null);
          Modal.success({
            title: '任务完成',
            content: '恭喜！您已完成所有客户的拨打任务。',
            onOk: () => navigate('/agent/tasks')
          });
        }
      }
    } catch (error: any) {
      message.error(error.response?.data?.error || '保存通话结果失败');
    }
  };

  // 拨打状态标签
  const renderCallStatusTag = (status: string) => {
    const statusConfig: Record<string, { color: string; text: string }> = {
      pending: { color: 'default', text: '待拨打' },
      called: { color: 'processing', text: '已拨打' },
      connected: { color: 'success', text: '已接通' },
      completed: { color: 'success', text: '已完成' },
      failed: { color: 'error', text: '未接通' }
    };
    const config = statusConfig[status] || statusConfig.pending;
    return <Tag color={config.color}>{config.text}</Tag>;
  };

  // 获取通话结果标签
  const getCallResultLabel = (result?: string) => {
    const found = CALL_RESULTS.find(r => r.value === result);
    return found ? found.label : result || '-';
  };

  const pendingCount = customers.filter(c => c.call_status === 'pending').length;
  const calledCount = customers.filter(c => c.call_status !== 'pending').length;
  const progress = task?.progress || 0;

  return (
    <div>
      {/* 顶部导航和统计 */}
      <div style={{ marginBottom: 24 }}>
        <Button 
          icon={<ArrowLeftOutlined />} 
          onClick={() => navigate('/agent/tasks')}
          style={{ marginBottom: 16 }}
        >
          返回任务列表
        </Button>
        
        <h2>{task?.title || task?.name || '任务执行'}</h2>
        
        <Row gutter={16} style={{ marginTop: 16 }}>
          <Col span={6}>
            <Card size="small">
              <Statistic 
                title="客户总数" 
                value={task?.customer_count || 0}
                prefix={<CustomerServiceOutlined />}
              />
            </Card>
          </Col>
          <Col span={6}>
            <Card size="small">
              <Statistic 
                title="待拨打" 
                value={pendingCount}
                prefix={<ClockCircleOutlined />}
                valueStyle={{ color: '#fa8c16' }}
              />
            </Card>
          </Col>
          <Col span={6}>
            <Card size="small">
              <Statistic 
                title="已拨打" 
                value={calledCount}
                prefix={<PhoneOutlined />}
                valueStyle={{ color: '#1890ff' }}
              />
            </Card>
          </Col>
          <Col span={6}>
            <Card size="small">
              <Statistic 
                title="完成进度" 
                value={Math.round(progress)}
                suffix="%"
                prefix={<CheckSquareOutlined />}
                valueStyle={{ color: progress === 100 ? '#52c41a' : '#1890ff' }}
              />
            </Card>
          </Col>
        </Row>
        
        <Progress 
          percent={Math.round(progress)} 
          status={progress === 100 ? 'success' : 'active'}
          style={{ marginTop: 16 }}
        />
      </div>

      <Row gutter={24}>
        {/* 左侧：当前拨打客户 */}
        <Col span={10}>
          <Card 
            title="当前拨打客户" 
            loading={loading}
            extra={currentCustomer && renderCallStatusTag(currentCustomer.call_status)}
          >
            {currentCustomer ? (
              <Space direction="vertical" style={{ width: '100%' }} size="large">
                <div>
                  <div style={{ fontSize: 24, fontWeight: 'bold', marginBottom: 8 }}>
                    {currentCustomer.name}
                  </div>
                  <div style={{ fontSize: 18, color: '#1890ff' }}>
                    <PhoneOutlined style={{ marginRight: 8 }} />
                    {currentCustomer.phone}
                  </div>
                </div>
                
                {currentCustomer.company && (
                  <div>
                    <span style={{ color: '#999' }}>公司: </span>
                    {currentCustomer.company}
                  </div>
                )}
                
                {currentCustomer.email && (
                  <div>
                    <span style={{ color: '#999' }}>邮箱: </span>
                    {currentCustomer.email}
                  </div>
                )}
                
                <Divider />
                
                <Space style={{ width: '100%', justifyContent: 'center' }}>
                  <Button
                    type="primary"
                    size="large"
                    icon={<PhoneOutlined />}
                    loading={isCalling}
                    disabled={currentCustomer.call_status !== 'pending'}
                    onClick={() => handleCallCustomer(currentCustomer)}
                  >
                    {currentCustomer.call_status === 'pending' ? '拨打电话' : '已拨打'}
                  </Button>
                  
                  {currentCustomer.call_status === 'pending' && (
                    <Button
                      size="large"
                      icon={<CloseCircleOutlined />}
                      onClick={handleSkipCustomer}
                    >
                      跳过
                    </Button>
                  )}
                </Space>
              </Space>
            ) : (
              <Empty description="没有待拨打的客户" />
            )}
          </Card>
          
          {/* 快速统计 */}
          <Card size="small" style={{ marginTop: 16 }}>
            <Space direction="vertical" style={{ width: '100%' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span>剩余客户:</span>
                <span style={{ fontWeight: 'bold', color: '#fa8c16' }}>{pendingCount} 人</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span>已完成:</span>
                <span style={{ fontWeight: 'bold', color: '#52c41a' }}>{task?.completed_count || 0} 人</span>
              </div>
            </Space>
          </Card>
        </Col>

        {/* 右侧：客户列表 */}
        <Col span={14}>
          <Card title="客户列表" loading={loading}>
            <Table
              dataSource={customers}
              rowKey="task_customer_id"
              size="small"
              pagination={{ pageSize: 8 }}
              rowClassName={(record) => 
                record.id === currentCustomer?.id ? 'ant-table-row-selected' : ''
              }
              onRow={(record) => ({
                onClick: () => setCurrentCustomer(record),
                style: { cursor: 'pointer' }
              })}
              columns={[
                {
                  title: '姓名',
                  dataIndex: 'name',
                  key: 'name',
                  width: 100,
                  render: (name: string) => <span style={{ fontWeight: 'bold' }}>{name}</span>
                },
                {
                  title: '电话',
                  dataIndex: 'phone',
                  key: 'phone',
                  width: 130
                },
                {
                  title: '公司',
                  dataIndex: 'company',
                  key: 'company',
                  width: 120,
                  render: (company: string) => company || '-'
                },
                {
                  title: '状态',
                  dataIndex: 'call_status',
                  key: 'call_status',
                  width: 100,
                  render: (status: string) => renderCallStatusTag(status)
                },
                {
                  title: '通话结果',
                  dataIndex: 'call_result',
                  key: 'call_result',
                  render: (result: string) => (
                    <Tag color={result ? 'blue' : 'default'}>
                      {getCallResultLabel(result)}
                    </Tag>
                  )
                },
                {
                  title: '操作',
                  key: 'action',
                  width: 100,
                  render: (_: any, record: TaskCustomer) => (
                    <Button
                      type="primary"
                      size="small"
                      icon={<PhoneOutlined />}
                      disabled={record.call_status !== 'pending'}
                      onClick={(e) => {
                        e.stopPropagation();
                        handleCallCustomer(record);
                      }}
                    >
                      拨打
                    </Button>
                  )
                }
              ]}
            />
          </Card>
        </Col>
      </Row>

      {/* 通话结果录入弹窗 */}
      <Modal
        title="录入通话结果"
        open={callResultModalVisible}
        onOk={handleSubmitCallResult}
        onCancel={() => setCallResultModalVisible(false)}
        okText="保存并继续"
        cancelText="取消"
        width={500}
      >
        <Space direction="vertical" style={{ width: '100%' }} size="large">
          <div>
            <div style={{ marginBottom: 8, fontWeight: 'bold' }}>
              客户: {currentCustomer?.name} ({currentCustomer?.phone})
            </div>
          </div>
          
          <div>
            <div style={{ marginBottom: 8 }}>通话结果:</div>
            <Radio.Group 
              value={selectedResult} 
              onChange={(e) => setSelectedResult(e.target.value)}
            >
              <Space direction="vertical">
                {CALL_RESULTS.map(result => (
                  <Radio key={result.value} value={result.value}>
                    <Tag color={result.color}>{result.label}</Tag>
                  </Radio>
                ))}
              </Space>
            </Radio.Group>
          </div>
          
          <div>
            <div style={{ marginBottom: 8 }}>备注 (可选):</div>
            <Input.TextArea
              value={callNotes}
              onChange={(e) => setCallNotes(e.target.value)}
              placeholder="记录通话详情..."
              rows={3}
            />
          </div>
        </Space>
      </Modal>
    </div>
  );
}
