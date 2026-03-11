import { useEffect, useState } from 'react';
import { Card, Statistic, Row, Col, Progress, Table, DatePicker } from 'antd';
import { PhoneOutlined, CheckCircleOutlined, ClockCircleOutlined, TrophyOutlined } from '@ant-design/icons';
import { statsApi } from '../../services/api';
import type { AgentRanking } from '../../services/api';
import dayjs from 'dayjs';

const { RangePicker } = DatePicker;

export default function MyStats() {
  const [stats, setStats] = useState<any>(null);
  const [ranking, setRanking] = useState<AgentRanking[]>([]);
  const [dateRange, setDateRange] = useState<[dayjs.Dayjs, dayjs.Dayjs]>([
    dayjs().subtract(30, 'days'),
    dayjs()
  ]);

  useEffect(() => {
    fetchStats();
  }, [dateRange]);

  const fetchStats = async () => {
    try {
      const response = await statsApi.getMyStats({
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
    { title: '接通数', dataIndex: 'connected_calls', key: 'connected_calls' },
    { title: '接通率', dataIndex: 'connection_rate', key: 'connection_rate', render: (rate: number) => `${rate}%` },
  ];

  return (
    <div>
      <h2>我的业绩</h2>
      
      <div style={{ marginBottom: 24 }}>
        <RangePicker
          value={dateRange}
          onChange={(dates) => dates && setDateRange(dates as [dayjs.Dayjs, dayjs.Dayjs])}
        />
      </div>

      <Row gutter={16} style={{ marginBottom: 24 }}>
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
              prefix={<ClockCircleOutlined />}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="排名"
              value={stats?.ranking || '-'}
              prefix={<TrophyOutlined />}
            />
          </Card>
        </Col>
      </Row>

      <Row gutter={16}>
        <Col span={12}>
          <Card title="目标完成度">
            <div style={{ textAlign: 'center', padding: 24 }}>
              <Progress
                type="circle"
                percent={Math.round(((stats?.connected_calls || 0) / 100) * 100)}
                format={(percent) => (
                  <div>
                    <div style={{ fontSize: 24, fontWeight: 'bold' }}>{stats?.connected_calls || 0}</div>
                    <div style={{ fontSize: 12, color: '#999' }}>/ 100 目标</div>
                  </div>
                )}
              />
            </div>
          </Card>
        </Col>
        <Col span={12}>
          <Card title="详细数据">
            <Row gutter={16}>
              <Col span={12}>
                <Statistic title="总通话时长(秒)" value={stats?.total_duration || 0} />
              </Col>
              <Col span={12}>
                <Statistic title="平均通话时长(秒)" value={Math.round(stats?.avg_duration || 0)} />
              </Col>
            </Row>
            <Row gutter={16} style={{ marginTop: 16 }}>
              <Col span={12}>
                <Statistic title="未接通数" value={stats?.failed_calls || 0} />
              </Col>
              <Col span={12}>
                <Statistic title="接通率" value={`${stats?.connection_rate || 0}%`} />
              </Col>
            </Row>
          </Card>
        </Col>
      </Row>
    </div>
  );
}
