import { useEffect, useState } from 'react';
import { Row, Col, Card, Statistic, Table } from 'antd';
import { UserOutlined, PhoneOutlined, CheckCircleOutlined, TeamOutlined } from '@ant-design/icons';
import { statsApi } from '../../services/api';
import type { DashboardStats } from '../../services/api';

export default function Dashboard() {
  const [stats, setStats] = useState<DashboardStats | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    fetchStats();
  }, []);

  const fetchStats = async () => {
    setLoading(true);
    try {
      const response = await statsApi.getDashboardStats();
      setStats(response.data);
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
      <h2>仪表板</h2>
      
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

      <Card title="最近7天通话趋势" loading={loading}>
        <Table
          dataSource={stats?.trend || []}
          columns={trendColumns}
          rowKey="date"
          pagination={false}
        />
      </Card>
    </div>
  );
}
