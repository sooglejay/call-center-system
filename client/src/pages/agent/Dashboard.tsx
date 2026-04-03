import { useEffect, useState } from 'react';
import { Card, Statistic, Row, Col, List, Tag, Button, message, Modal, Empty, Space, Avatar } from 'antd';
import { PhoneOutlined, CheckCircleOutlined, ClockCircleOutlined, RiseOutlined, ExclamationCircleOutlined, ScheduleOutlined, ArrowRightOutlined, UserOutlined } from '@ant-design/icons';
import { statsApi, taskApi, customerApi } from '../../services/api';
import type { Task } from '../../services/api';
import { useAutoDialStore, useAuthStore } from '../../stores';
import { useNavigate } from 'react-router-dom';

export default function AgentDashboard() {
  const [stats, setStats] = useState<any>(null);
  const [tasks, setTasks] = useState<Task[]>([]);
  const [customerCount, setCustomerCount] = useState(0);
  const [loading, setLoading] = useState(true);
  const { setAutoDialing } = useAutoDialStore();
  const { user } = useAuthStore();
  const navigate = useNavigate();

  useEffect(() => {
    fetchData();
  }, []);

  const fetchData = async () => {
    setLoading(true);
    try {
      await Promise.all([
        fetchStats(),
        fetchTasks(),
        fetchCustomerCount()
      ]);
    } finally {
      setLoading(false);
    }
  };

  const fetchStats = async () => {
    try {
      const response = await statsApi.getMyStats();
      setStats(response.data);
    } catch (error) {
      console.error('获取统计数据失败');
    }
  };

  const fetchTasks = async () => {
    try {
      const response = await taskApi.getMyTasks();
      setTasks(response.data || []);
    } catch (error) {
      console.error('获取任务列表失败');
    }
  };

  const fetchCustomerCount = async () => {
    try {
      const response = await customerApi.getAgentCustomers({ status: 'pending', pageSize: 1000 });
      setCustomerCount(response.data?.pagination?.total || 0);
    } catch (error) {
      console.error('获取客户数量失败');
    }
  };

  const handleStartAutoDial = () => {
    // 检查是否有待拨打的客户
    if (customerCount === 0) {
      Modal.warning({
        title: '暂无拨打任务',
        content: '请管理员为您分配客户任务后再进行拨打',
        okText: '我知道了'
      });
      return;
    }
    
    // 检查是否有进行中的任务
    const hasActiveTask = tasks.some(t => t.status === 'in_progress');
    if (!hasActiveTask && tasks.length === 0) {
      Modal.confirm({
        title: '确认开始拨号',
        icon: <ExclamationCircleOutlined />,
        content: `您有 ${customerCount} 个待拨打的客户，是否开始自动拨号？`,
        okText: '开始拨号',
        cancelText: '取消',
        onOk: () => {
          setAutoDialing(true);
          window.location.href = '/agent/calls';
        }
      });
    } else {
      setAutoDialing(true);
      window.location.href = '/agent/calls';
    }
  };

  return (
    <div>
      {/* 欢迎区域 */}
      <Card style={{ marginBottom: 24, background: 'linear-gradient(135deg, #1890ff 0%, #36cfc9 100%)' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
          <Avatar size={64} icon={<UserOutlined />} style={{ backgroundColor: 'rgba(255,255,255,0.3)' }} />
          <div>
            <h2 style={{ margin: 0, color: '#fff' }}>欢迎回来，{user?.real_name || user?.username || '客服'}</h2>
            <p style={{ margin: '4px 0 0 0', color: 'rgba(255,255,255,0.85)' }}>
              账号: {user?.username} | 角色: {user?.role === 'admin' ? '管理员' : '客服'}
            </p>
          </div>
        </div>
      </Card>

      <h3 style={{ marginBottom: 16 }}>今日概览</h3>
      
      <Row gutter={16} style={{ marginBottom: 24 }}>
        <Col span={6}>
          <Card loading={loading}>
            <Statistic
              title="今日通话数"
              value={stats?.total_calls || 0}
              prefix={<PhoneOutlined />}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card loading={loading}>
            <Statistic
              title="接通数"
              value={stats?.connected_calls || 0}
              prefix={<CheckCircleOutlined />}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card loading={loading}>
            <Statistic
              title="接通率"
              value={stats?.connection_rate || 0}
              suffix="%"
              precision={2}
              prefix={<RiseOutlined />}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card loading={loading}>
            <Statistic
              title="待拨打客户"
              value={customerCount}
              prefix={<ClockCircleOutlined />}
              valueStyle={{ color: customerCount > 0 ? '#1890ff' : '#999' }}
            />
          </Card>
        </Col>
      </Row>

      <Row gutter={16}>
        <Col span={12}>
          <Card 
            title="我的任务" 
            extra={
              <Button 
                type="link" 
                onClick={() => navigate('/agent/tasks')}
              >
                查看全部 <ArrowRightOutlined />
              </Button>
            }
          >
            {tasks.length === 0 ? (
              <Empty 
                description="暂无任务" 
                image={Empty.PRESENTED_IMAGE_SIMPLE}
              >
                <Button type="primary" onClick={() => message.info('请联系管理员分配任务')}>
                  申请任务
                </Button>
              </Empty>
            ) : (
              <List
                dataSource={tasks.slice(0, 5)}
                renderItem={(task) => (
                  <List.Item
                    actions={[
                      <Button 
                        type="primary" 
                        size="small"
                        onClick={() => navigate(`/agent/tasks/${task.id}`)}
                      >
                        执行
                      </Button>
                    ]}
                  >
                    <List.Item.Meta
                      title={
                        <Space>
                          {task.name || task.title}
                          <Tag color={task.status === 'completed' ? 'green' : task.status === 'in_progress' ? 'blue' : 'default'}>
                            {task.status === 'completed' ? '已完成' : task.status === 'in_progress' ? '进行中' : '待开始'}
                          </Tag>
                        </Space>
                      }
                      description={`${task.completed_count || 0} / ${task.customer_count || 0} 完成`}
                    />
                  </List.Item>
                )}
              />
            )}
          </Card>
        </Col>
        <Col span={12}>
          <Card title="快速操作">
            <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              <Button 
                type="primary" 
                size="large" 
                icon={<ScheduleOutlined />} 
                onClick={() => navigate('/agent/tasks')}
              >
                任务管理
              </Button>
              <Button 
                type="primary" 
                size="large" 
                icon={<PhoneOutlined />} 
                onClick={handleStartAutoDial}
                disabled={customerCount === 0}
              >
                {customerCount === 0 ? '暂无拨打任务' : '开始自动拨号'}
              </Button>
              <Button size="large" onClick={() => window.location.href = '/agent/calls'}>
                查看电话列表
              </Button>
              <Button size="large" onClick={() => window.location.href = '/agent/stats'}>
                查看业绩统计
              </Button>
            </div>
            {customerCount === 0 && (
              <div style={{ marginTop: 16, padding: 12, background: '#fff7e6', borderRadius: 4, border: '1px solid #ffd591' }}>
                <ExclamationCircleOutlined style={{ color: '#fa8c16', marginRight: 8 }} />
                <span style={{ color: '#d46b08' }}>您暂无待拨打的客户，请联系管理员分配任务</span>
              </div>
            )}
          </Card>
        </Col>
      </Row>
    </div>
  );
}
