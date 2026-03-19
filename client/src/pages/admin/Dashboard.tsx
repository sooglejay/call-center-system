import { useEffect, useState } from 'react';
import { Row, Col, Card, Statistic, Table, Empty, Alert, Spin } from 'antd';
import { UserOutlined, PhoneOutlined, CheckCircleOutlined, TeamOutlined, DashboardOutlined } from '@ant-design/icons';
import { statsApi } from '../../services/api';
import type { DashboardStats } from '../../services/api';

export default function Dashboard() {
  const [stats, setStats] = useState<DashboardStats | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchStats();
  }, []);

  const fetchStats = async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await statsApi.getDashboardStats();
      setStats(response.data);
    } catch (error: any) {
      console.error('获取仪表板数据失败:', error);
      setError(error.response?.data?.error || '获取仪表板数据失败，请刷新重试');
    } finally {
      setLoading(false);
    }
  };

  const trendColumns = [
    { title: '日期', dataIndex: 'date', key: 'date' },
    { title: '总通话数', dataIndex: 'total_calls', key: 'total_calls' },
    { title: '接通数', dataIndex: 'connected_calls', key: 'connected_calls' },
  ];

  return (
    <div>
      <h2><DashboardOutlined style={{ marginRight: 8 }} />仪表板</h2>
      
      {error && (
        <Alert
          message="加载失败"
          description={error}
          type="error"
          showIcon
          closable
          onClose={() => setError(null)}
          style={{ marginBottom: 16 }}
        />
      )}
      
      <Spin spinning={loading} tip="正在加载数据...">
        <Row gutter={16} style={{ marginBottom: 24 }}>
          <Col span={6}>
            <Card>
              <Statistic
                title="总客户数"
                value={stats?.total_customers || 0}
                prefix={<UserOutlined />}
              />
            </Card>
          </Col>
          <Col span={6}>
            <Card>
              <Statistic
                title="总通话数"
                value={stats?.total_calls || 0}
                prefix={<PhoneOutlined />}
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
                prefix={<CheckCircleOutlined />}
              />
            </Card>
          </Col>
          <Col span={6}>
            <Card>
              <Statistic
                title="活跃客服"
                value={stats?.active_agents || 0}
                prefix={<TeamOutlined />}
              />
            </Card>
          </Col>
        </Row>

        <Card title="最近7天通话趋势">
          {stats?.trend && stats.trend.length > 0 ? (
            <Table
              dataSource={stats.trend}
              columns={trendColumns}
              rowKey="date"
              pagination={false}
            />
          ) : (
            <Empty
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              description={
                <div>
                  <p>暂无通话数据</p>
                  <p style={{ fontSize: 12, color: '#8c8c8c' }}>
                    请确保客服已完成外呼任务
                  </p>
                </div>
              }
            />
          )}
        </Card>
      </Spin>
    </div>
  );
}
