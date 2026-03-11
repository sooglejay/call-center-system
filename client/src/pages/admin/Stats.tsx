import { useEffect, useState } from 'react';
import { Card, DatePicker, Table, Statistic, Row, Col } from 'antd';
import { statsApi } from '../../services/api';
import type { AgentRanking } from '../../services/api';
import dayjs from 'dayjs';

const { RangePicker } = DatePicker;

export default function Stats() {
  const [stats, setStats] = useState<any>(null);
  const [dateRange, setDateRange] = useState<[dayjs.Dayjs, dayjs.Dayjs]>([
    dayjs().subtract(30, 'days'),
    dayjs()
  ]);

  useEffect(() => {
    fetchStats();
  }, [dateRange]);

  const fetchStats = async () => {
    try {
      const response = await statsApi.getStats({
        start_date: dateRange[0].format('YYYY-MM-DD'),
        end_date: dateRange[1].format('YYYY-MM-DD')
      });
      setStats(response.data);
    } catch (error) {
      console.error('获取统计数据失败');
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
      <h2>监控统计</h2>
      
      <div style={{ marginBottom: 24 }}>
        <RangePicker
          value={dateRange}
          onChange={(dates) => dates && setDateRange(dates as [dayjs.Dayjs, dayjs.Dayjs])}
        />
      </div>

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
        <Table
          columns={columns}
          dataSource={stats?.agent_ranking || []}
          rowKey="agent_id"
          pagination={false}
        />
      </Card>
    </div>
  );
}
