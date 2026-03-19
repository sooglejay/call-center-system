import { useEffect, useState } from 'react';
import { Card, DatePicker, Table, Statistic, Row, Col, Empty, Alert, Spin } from 'antd';
import { BarChartOutlined, InfoCircleOutlined } from '@ant-design/icons';
import { statsApi } from '../../services/api';
import dayjs from 'dayjs';

const { RangePicker } = DatePicker;

export default function Stats() {
  const [stats, setStats] = useState<any>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [dateRange, setDateRange] = useState<[dayjs.Dayjs, dayjs.Dayjs]>([
    dayjs().subtract(30, 'days'),
    dayjs()
  ]);

  useEffect(() => {
    fetchStats();
  }, [dateRange]);

  const fetchStats = async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await statsApi.getStats({
        start_date: dateRange[0].format('YYYY-MM-DD'),
        end_date: dateRange[1].format('YYYY-MM-DD')
      });
      setStats(response.data);
    } catch (error: any) {
      console.error('获取统计数据失败:', error);
      setError(error.response?.data?.error || '获取统计数据失败，请稍后重试');
    } finally {
      setLoading(false);
    }
  };

  const columns = [
    { title: '排名', key: 'rank', render: (_: any, __: any, index: number) => index + 1 },
    { title: '客服姓名', dataIndex: 'agent_name', key: 'agent_name' },
    { title: '总通话数', dataIndex: 'total_calls', key: 'total_calls' },
    { title: '接通数', dataIndex: 'connected_calls', key: 'connected_calls' },
    { title: '接通率', dataIndex: 'connection_rate', key: 'connection_rate', render: (rate: number) => `${rate}%` },
    { title: '总通话时长(秒)', dataIndex: 'total_duration', key: 'total_duration' },
  ];

  return (
    <div>
      <h2><BarChartOutlined style={{ marginRight: 8 }} />监控统计</h2>
      
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
      
      <div style={{ marginBottom: 24 }}>
        <RangePicker
          value={dateRange}
          onChange={(dates) => dates && setDateRange(dates as [dayjs.Dayjs, dayjs.Dayjs])}
        />
      </div>

      <Spin spinning={loading} tip="正在加载统计数据...">
        <Row gutter={16} style={{ marginBottom: 24 }}>
          <Col span={6}>
            <Card>
              <Statistic title="总通话数" value={stats?.overview?.total_calls || 0} />
            </Card>
          </Col>
          <Col span={6}>
            <Card>
              <Statistic title="接通数" value={stats?.overview?.connected_calls || 0} />
            </Card>
          </Col>
          <Col span={6}>
            <Card>
              <Statistic title="未接通数" value={stats?.overview?.failed_calls || 0} />
            </Card>
          </Col>
          <Col span={6}>
            <Card>
              <Statistic title="平均通话时长(秒)" value={Math.round(stats?.overview?.avg_duration || 0)} />
            </Card>
          </Col>
        </Row>

        <Card title="客服业绩排名">
          {stats?.agent_ranking?.length > 0 ? (
            <Table
              columns={columns}
              dataSource={stats.agent_ranking}
              rowKey="agent_id"
              pagination={false}
            />
          ) : (
            <Empty
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              description={
                <div>
                  <p>暂无统计数据</p>
                  <p style={{ fontSize: 12, color: '#8c8c8c' }}>
                    请确保客服已完成外呼任务，或调整查询时间范围
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
