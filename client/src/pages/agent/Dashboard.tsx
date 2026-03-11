import { useEffect, useState } from 'react';
import { Card, Statistic, Row, Col, List, Tag, Button } from 'antd';
import { PhoneOutlined, CheckCircleOutlined, ClockCircleOutlined, RiseOutlined } from '@ant-design/icons';
import { statsApi, taskApi } from '../../services/api';
import type { Task } from '../../services/api';
import { useAutoDialStore } from '../../stores';

export default function AgentDashboard() {
  const [stats, setStats] = useState<any>(null);
  const [tasks, setTasks] = useState<Task[]>([]);
  const { setAutoDialing } = useAutoDialStore();

  useEffect(() => {
    fetchStats();
    fetchTasks();
  }, []);

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
      setTasks(response.data);
    } catch (error) {
      console.error('获取任务列表失败');
    }
  };

  const handleStartAutoDial = () => {
    setAutoDialing(true);
    window.location.href = '/calls';
  };

  return (
    <div>
      <h2>工作台</h2>
      
      <Row gutter={16} style={{ marginBottom: 24 }}>
        <Col span={6}>
          <Card>
            <Statistic
              title="今日通话数"
              value={stats?.total_calls || 0}
              prefix={<PhoneOutlined />}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="接通数"
              value={stats?.connected_calls || 0}
              prefix={<CheckCircleOutlined />}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
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
          <Card>
            <Statistic
              title="排名"
              value={stats?.ranking || '-'}
              prefix={<ClockCircleOutlined />}
            />
          </Card>
        </Col>
      </Row>

      <Row gutter={16}>
        <Col span={12}>
          <Card title="我的任务" extra={<Button type="primary" onClick={handleStartAutoDial}>开始自动拨号</Button>}>
            <List
              dataSource={tasks}
              renderItem={(task) => (
                <List.Item>
                  <List.Item.Meta
                    title={task.name}
                    description={`${task.completed_count || 0} / ${task.customer_count || 0} 完成`}
                  />
                  <Tag color={task.task_type === 'daily' ? 'blue' : 'green'}>
                    {task.task_type === 'daily' ? '日任务' : '周任务'}
                  </Tag>
                </List.Item>
              )}
            />
          </Card>
        </Col>
        <Col span={12}>
          <Card title="快速操作">
            <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              <Button type="primary" size="large" icon={<PhoneOutlined />} onClick={handleStartAutoDial}>
                开始自动拨号
              </Button>
              <Button size="large" onClick={() => window.location.href = '/calls'}>
                查看电话列表
              </Button>
              <Button size="large" onClick={() => window.location.href = '/stats'}>
                查看业绩统计
              </Button>
            </div>
          </Card>
        </Col>
      </Row>
    </div>
  );
}
