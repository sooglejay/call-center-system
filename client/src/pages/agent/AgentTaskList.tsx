import { useEffect, useState } from 'react';
import { Card, Table, Button, Tag, Progress, Space, Empty, message, Modal } from 'antd';
import { ScheduleOutlined, EyeOutlined, PlayCircleOutlined } from '@ant-design/icons';
import { taskApi } from '../../services/api';
import type { Task } from '../../services/api';
import { useNavigate } from 'react-router-dom';

interface TaskWithStats extends Task {
  customer_count: number;
  completed_count: number;
  called_count: number;
  progress: number;
}

export default function AgentTaskList() {
  const [tasks, setTasks] = useState<TaskWithStats[]>([]);
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  useEffect(() => {
    fetchTasks();
  }, []);

  const fetchTasks = async () => {
    setLoading(true);
    try {
      const response = await taskApi.getMyTasks();
      const tasksData = response.data || [];
      setTasks(Array.isArray(tasksData) ? tasksData : []);
    } catch (error: any) {
      message.error(error.response?.data?.error || '获取任务列表失败');
    } finally {
      setLoading(false);
    }
  };

  const handleViewTask = (taskId: number) => {
    navigate(`/agent/tasks/${taskId}`);
  };

  const handleStartTask = (task: TaskWithStats) => {
    const pendingCount = task.customer_count - (task.called_count || 0);
    if (pendingCount === 0) {
      message.info('该任务所有客户已拨打完成');
      return;
    }
    
    Modal.confirm({
      title: '开始执行任务',
      content: `任务「${task.title || task.name}」还有 ${pendingCount} 个客户待拨打，是否开始执行？`,
      okText: '开始执行',
      cancelText: '取消',
      onOk: () => {
        navigate(`/agent/tasks/${task.id}`);
      }
    });
  };

  // 任务状态标签
  const renderStatusTag = (status: string) => {
    const statusConfig: Record<string, { color: string; text: string }> = {
      pending: { color: 'warning', text: '待处理' },
      in_progress: { color: 'processing', text: '进行中' },
      active: { color: 'processing', text: '进行中' },
      completed: { color: 'success', text: '已完成' },
      cancelled: { color: 'default', text: '已取消' }
    };
    const config = statusConfig[status] || statusConfig.pending;
    return <Tag color={config.color}>{config.text}</Tag>;
  };

  // 优先级标签
  const renderPriorityTag = (priority?: string) => {
    const priorityConfig: Record<string, { color: string; text: string }> = {
      urgent: { color: 'red', text: '紧急' },
      high: { color: 'orange', text: '高' },
      normal: { color: 'blue', text: '普通' },
      low: { color: 'default', text: '低' }
    };
    const config = priorityConfig[priority || 'normal'] || priorityConfig.normal;
    return <Tag color={config.color}>{config.text}</Tag>;
  };

  const columns = [
    {
      title: '任务名称',
      dataIndex: 'title',
      key: 'title',
      render: (title: string, record: TaskWithStats) => (
        <Space direction="vertical" size={0}>
          <span style={{ fontWeight: 'bold' }}>{title || record.name}</span>
          {record.description && (
            <span style={{ fontSize: 12, color: '#999' }}>{record.description}</span>
          )}
        </Space>
      )
    },
    {
      title: '优先级',
      dataIndex: 'priority',
      key: 'priority',
      width: 100,
      render: (priority: string) => renderPriorityTag(priority)
    },
    {
      title: '客户数量',
      key: 'customerStats',
      width: 150,
      render: (_: any, record: TaskWithStats) => (
        <Space direction="vertical" size={0}>
          <span>总计: {record.customer_count || 0} 人</span>
          <span style={{ fontSize: 12, color: '#1890ff' }}>
            已拨打: {record.called_count || 0} 人
          </span>
          <span style={{ fontSize: 12, color: '#52c41a' }}>
            已完成: {record.completed_count || 0} 人
          </span>
        </Space>
      )
    },
    {
      title: '完成进度',
      key: 'progress',
      width: 180,
      render: (_: any, record: TaskWithStats) => {
        const progress = record.progress || 0;
        const pendingCount = (record.customer_count || 0) - (record.called_count || 0);
        return (
          <Space direction="vertical" size={0} style={{ width: '100%' }}>
            <Progress 
              percent={Math.round(progress)} 
              size="small" 
              status={progress === 100 ? 'success' : 'active'}
            />
            {pendingCount > 0 && (
              <span style={{ fontSize: 12, color: '#fa8c16' }}>
                待拨打: {pendingCount} 人
              </span>
            )}
          </Space>
        );
      }
    },
    {
      title: '截止日期',
      dataIndex: 'due_date',
      key: 'due_date',
      width: 120,
      render: (date: string) => date || '-'
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (status: string) => renderStatusTag(status)
    },
    {
      title: '操作',
      key: 'action',
      width: 200,
      render: (_: any, record: TaskWithStats) => {
        const pendingCount = (record.customer_count || 0) - (record.called_count || 0);
        const isCompleted = record.status === 'completed' || pendingCount === 0;
        
        return (
          <Space>
            <Button
              type="primary"
              icon={<PlayCircleOutlined />}
              size="small"
              disabled={isCompleted}
              onClick={() => handleStartTask(record)}
            >
              {isCompleted ? '已完成' : '执行任务'}
            </Button>
            <Button
              icon={<EyeOutlined />}
              size="small"
              onClick={() => handleViewTask(record.id)}
            >
              详情
            </Button>
          </Space>
        );
      }
    }
  ];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
        <h2><ScheduleOutlined style={{ marginRight: 8 }} />我的任务</h2>
      </div>

      <Card>
        {tasks.length === 0 && !loading ? (
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description={
              <Space direction="vertical" align="center">
                <span>暂无任务</span>
                <span style={{ fontSize: 12, color: '#999' }}>
                  请联系管理员为您分配任务
                </span>
              </Space>
            }
          />
        ) : (
          <Table
            columns={columns}
            dataSource={tasks}
            rowKey="id"
            loading={loading}
            pagination={{ pageSize: 10 }}
          />
        )}
      </Card>
    </div>
  );
}
