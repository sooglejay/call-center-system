import { useEffect, useState } from 'react';
import { Row, Col, Card, Statistic, Table, Empty, Alert, Spin, Avatar } from 'antd';
import { UserOutlined, PhoneOutlined, CheckCircleOutlined, TeamOutlined, DashboardOutlined, CrownOutlined } from '@ant-design/icons';
import { statsApi } from '../../services/api';
import type { DashboardStats } from '../../services/api';
import { useAuthStore } from '../../stores';

export default function Dashboard() {
  const [stats, setStats] = useState<DashboardStats | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const { user } = useAuthStore();

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
      {/* 欢迎区域 */}
      <Card style={{ marginBottom: 24, background: 'linear-gradient(135deg, #722ed1 0%, #eb2f96 100%)' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
          <Avatar size={64} icon={<CrownOutlined />} style={{ backgroundColor: 'rgba(255,255,255,0.3)' }} />
          <div>
            <h2 style={{ margin: 0, color: '#fff' }}>欢迎回来，{user?.real_name || user?.username || '管理员'}</h2>
            <p style={{ margin: '4px 0 0 0', color: 'rgba(255,255,255,0.85)' }}>
              账号: {user?.username} | 角色: 管理员
            </p>
          </div>
        </div>
      </Card>

      <h3 style={{ marginBottom: 16 }}><DashboardOutlined style={{ marginRight: 8 }} />运营概览</h3>
      
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
