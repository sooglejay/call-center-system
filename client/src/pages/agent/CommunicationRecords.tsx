import { useEffect, useState } from 'react';
import { Card, Table, Tag, Statistic, Row, Col, Empty, message } from 'antd';
import { 
  MessageOutlined, 
  PhoneOutlined, 
  AudioOutlined,
  CloseCircleOutlined
} from '@ant-design/icons';
import { communicationApi } from '../../services/api';

interface VoicemailRecord {
  id: number;
  call_id: number;
  customer_name: string;
  customer_phone: string;
  voicemail_url: string;
  duration: number;
  created_at: string;
}

interface SmsRecord {
  id: number;
  call_id: number;
  customer_name: string;
  customer_phone: string;
  sms_content: string;
  sms_status: string;
  created_at: string;
}

interface UnansweredRecord {
  id: number;
  call_id: number;
  customer_name: string;
  customer_phone: string;
  created_at: string;
}

export default function CommunicationRecords() {
  const [voicemailList, setVoicemailList] = useState<VoicemailRecord[]>([]);
  const [smsList, setSmsList] = useState<SmsRecord[]>([]);
  const [unansweredList, setUnansweredList] = useState<UnansweredRecord[]>([]);
  const [stats, setStats] = useState({
    voicemailCount: 0,
    smsCount: 0,
    unansweredCount: 0
  });

  useEffect(() => {
    fetchCommunicationData();
  }, []);

  const fetchCommunicationData = async () => {
    try {
      const response = await communicationApi.getRecords();
      setVoicemailList(response.data.voicemail || []);
      setSmsList(response.data.sms || []);
      setUnansweredList(response.data.unanswered || []);
      setStats(response.data.stats || {
        voicemailCount: 0,
        smsCount: 0,
        unansweredCount: 0
      });
    } catch (error) {
      message.error('获取通信记录失败');
    }
  };

  const voicemailColumns = [
    {
      title: '客户姓名',
      dataIndex: 'customer_name',
      key: 'customer_name',
    },
    {
      title: '电话号码',
      dataIndex: 'customer_phone',
      key: 'customer_phone',
    },
    {
      title: '时长(秒)',
      dataIndex: 'duration',
      key: 'duration',
      render: (duration: number) => `${duration}秒`,
    },
    {
      title: '录音文件',
      dataIndex: 'voicemail_url',
      key: 'voicemail_url',
      render: (url: string) => (
        <a href={url} target="_blank" rel="noopener noreferrer">
          <AudioOutlined /> 播放录音
        </a>
      ),
    },
    {
      title: '时间',
      dataIndex: 'created_at',
      key: 'created_at',
      render: (time: string) => new Date(time).toLocaleString(),
    },
  ];

  const smsColumns = [
    {
      title: '客户姓名',
      dataIndex: 'customer_name',
      key: 'customer_name',
    },
    {
      title: '电话号码',
      dataIndex: 'customer_phone',
      key: 'customer_phone',
    },
    {
      title: '短信内容',
      dataIndex: 'sms_content',
      key: 'sms_content',
      ellipsis: true,
    },
    {
      title: '状态',
      dataIndex: 'sms_status',
      key: 'sms_status',
      render: (status: string) => {
        const color = status === 'sent' ? 'green' : status === 'failed' ? 'red' : 'orange';
        const text = status === 'sent' ? '已发送' : status === 'failed' ? '发送失败' : '处理中';
        return <Tag color={color}>{text}</Tag>;
      },
    },
    {
      title: '时间',
      dataIndex: 'created_at',
      key: 'created_at',
      render: (time: string) => new Date(time).toLocaleString(),
    },
  ];

  const unansweredColumns = [
    {
      title: '客户姓名',
      dataIndex: 'customer_name',
      key: 'customer_name',
    },
    {
      title: '电话号码',
      dataIndex: 'customer_phone',
      key: 'customer_phone',
    },
    {
      title: '状态',
      key: 'status',
      render: () => <Tag icon={<CloseCircleOutlined />} color="red">未接通</Tag>,
    },
    {
      title: '时间',
      dataIndex: 'created_at',
      key: 'created_at',
      render: (time: string) => new Date(time).toLocaleString(),
    },
  ];

  return (
    <div>
      <h2>通信记录</h2>

      <Row gutter={16} style={{ marginBottom: 24 }}>
        <Col span={8}>
          <Card>
            <Statistic
              title="语音信箱"
              value={stats.voicemailCount}
              prefix={<AudioOutlined />}
              valueStyle={{ color: '#1890ff' }}
            />
          </Card>
        </Col>
        <Col span={8}>
          <Card>
            <Statistic
              title="已发送短信"
              value={stats.smsCount}
              prefix={<MessageOutlined />}
              valueStyle={{ color: '#52c41a' }}
            />
          </Card>
        </Col>
        <Col span={8}>
          <Card>
            <Statistic
              title="未接通记录"
              value={stats.unansweredCount}
              prefix={<PhoneOutlined />}
              valueStyle={{ color: '#ff4d4f' }}
            />
          </Card>
        </Col>
      </Row>

      <Card 
        title={<span><AudioOutlined /> 语音信箱记录</span>}
        style={{ marginBottom: 24 }}
      >
        <Table 
          columns={voicemailColumns}
          dataSource={voicemailList}
          rowKey="id"
          pagination={{ pageSize: 5 }}
          locale={{ emptyText: <Empty description="暂无语音信箱记录" /> }}
        />
      </Card>

      <Card 
        title={<span><MessageOutlined /> 短信记录</span>}
        style={{ marginBottom: 24 }}
      >
        <Table 
          columns={smsColumns}
          dataSource={smsList}
          rowKey="id"
          pagination={{ pageSize: 5 }}
          locale={{ emptyText: <Empty description="暂无短信记录" /> }}
        />
      </Card>

      <Card title={<span><PhoneOutlined /> 未接通记录</span>}>
        <Table 
          columns={unansweredColumns}
          dataSource={unansweredList}
          rowKey="id"
          pagination={{ pageSize: 5 }}
          locale={{ emptyText: <Empty description="暂无未接通记录" /> }}
        />
      </Card>
    </div>
  );
}
