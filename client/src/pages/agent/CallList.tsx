import { useEffect, useState, useCallback } from 'react';
import { Table, Button, Tag, Input, Select, Form, Modal, message, Badge, Space } from 'antd';
import { PhoneOutlined, PlayCircleOutlined, PauseCircleOutlined, AudioOutlined, EditOutlined } from '@ant-design/icons';
import { customerApi, callApi, twilioApi } from '../../services/api';
import type { Customer, CallRecord } from '../../services/api';
import { useAutoDialStore, useAgentConfigStore } from '../../stores';

const { Option } = Select;

export default function CallList() {
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [loading, setLoading] = useState(false);
  const [filters, setFilters] = useState({ status: 'pending', search: '' });
  const [callingCustomer, setCallingCustomer] = useState<Customer | null>(null);
  const [callModalVisible, setCallModalVisible] = useState(false);
  const [notesModalVisible, setNotesModalVisible] = useState(false);
  const [editingRecord, setEditingRecord] = useState<CallRecord | null>(null);
  const [noteForm] = Form.useForm();
  
  const { isAutoDialing, setAutoDialing, currentCustomer, setCurrentCustomer, dialStatus, setDialStatus } = useAutoDialStore();
  const { config } = useAgentConfigStore();

  useEffect(() => {
    fetchCustomers();
  }, [filters]);

  useEffect(() => {
    if (isAutoDialing && !callingCustomer) {
      startNextCall();
    }
  }, [isAutoDialing]);

  const fetchCustomers = async () => {
    setLoading(true);
    try {
      const response = await customerApi.getAgentCustomers({
        status: filters.status,
        search: filters.search
      });
      setCustomers(response.data.data);
    } finally {
      setLoading(false);
    }
  };

  const startNextCall = async () => {
    try {
      const response = await callApi.getNextCall();
      if (response.data.message) {
        message.info('没有待拨打的客户');
        setAutoDialing(false);
        return;
      }
      
      const customer = response.data;
      setCurrentCustomer(customer);
      await handleCall(customer);
    } catch (error) {
      message.error('获取下一个客户失败');
      setAutoDialing(false);
    }
  };

  const handleCall = async (customer: Customer) => {
    setCallingCustomer(customer);
    setCallModalVisible(true);
    setDialStatus('dialing');

    try {
      // 创建通话记录
      const callResponse = await callApi.createCall({
        customer_id: customer.id,
        phone: customer.phone,
        task_id: customer.task_id
      });

      // 模拟拨打电话（实际项目中调用Twilio）
      // await twilioApi.makeCall({...});

      message.success(`正在拨打: ${customer.phone}`);
      
      // 模拟通话状态更新
      setTimeout(() => {
        setDialStatus('connected');
      }, 2000);

    } catch (error) {
      message.error('拨打电话失败');
      setDialStatus('idle');
    }
  };

  const handleEndCall = async () => {
    setDialStatus('idle');
    setCallModalVisible(false);
    setCallingCustomer(null);
    
    // 延迟后拨打下一个
    if (isAutoDialing) {
      const delay = (config?.dial_delay || 3) * 1000;
      setTimeout(() => {
        startNextCall();
      }, delay);
    }
  };

  const handleStopAutoDial = () => {
    setAutoDialing(false);
    setDialStatus('idle');
    setCallModalVisible(false);
    setCallingCustomer(null);
    message.info('已停止自动拨号');
  };

  const handleSaveNotes = async (values: any) => {
    if (!editingRecord) return;
    
    try {
      await callApi.updateCallNotes(editingRecord.id, values.call_notes, values.call_result);
      message.success('保存成功');
      setNotesModalVisible(false);
      fetchCustomers();
    } catch (error) {
      message.error('保存失败');
    }
  };

  const columns = [
    { 
      title: '电话号码', 
      dataIndex: 'phone', 
      key: 'phone',
      render: (phone: string, record: Customer) => (
        <span>
          {phone}
          {record.is_duplicate && <Tag color="red" style={{ marginLeft: 8 }}>重复</Tag>}
        </span>
      )
    },
    { title: '客户姓名', dataIndex: 'name', key: 'name' },
    { title: '备注', dataIndex: 'remark', key: 'remark' },
    { 
      title: '状态', 
      dataIndex: 'call_status', 
      key: 'status',
      render: (status: string, record: Customer) => {
        if (!status) return <Tag>待拨打</Tag>;
        const colors: any = {
          completed: 'green',
          failed: 'red',
          no_answer: 'orange',
          busy: 'orange'
        };
        return <Tag color={colors[status] || 'default'}>{status}</Tag>;
      }
    },
    { 
      title: '通话时长', 
      dataIndex: 'call_duration', 
      key: 'call_duration',
      render: (duration: number) => duration ? `${duration}秒` : '-'
    },
    {
      title: '录音',
      key: 'recording',
      render: (_: any, record: Customer) => (
        record.recording_url ? (
          <audio controls src={record.recording_url} style={{ width: 200 }} />
        ) : '-'
      )
    },
    {
      title: '操作',
      key: 'action',
      render: (_: any, record: Customer) => (
        <Space>
          <Button 
            type="primary" 
            icon={<PhoneOutlined />} 
            onClick={() => handleCall(record)}
            disabled={callModalVisible}
          >
            拨打
          </Button>
          <Button 
            icon={<EditOutlined />}
            onClick={() => {
              setEditingRecord(record as any);
              noteForm.setFieldsValue(record);
              setNotesModalVisible(true);
            }}
          >
            备注
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16, alignItems: 'center' }}>
        <h2>电话列表</h2>
        <Space>
          <Input.Search
            placeholder="搜索电话号码或姓名"
            value={filters.search}
            onChange={(e) => setFilters({ ...filters, search: e.target.value })}
            onSearch={() => fetchCustomers()}
            style={{ width: 250 }}
          />
          <Select 
            value={filters.status} 
            onChange={(value) => setFilters({ ...filters, status: value })}
            style={{ width: 120 }}
          >
            <Option value="pending">待拨打</Option>
            <Option value="completed">已拨打</Option>
          </Select>
          {!isAutoDialing ? (
            <Button 
              type="primary" 
              icon={<PlayCircleOutlined />} 
              onClick={() => setAutoDialing(true)}
            >
              开始自动拨号
            </Button>
          ) : (
            <Button 
              danger 
              icon={<PauseCircleOutlined />} 
              onClick={handleStopAutoDial}
            >
              停止自动拨号
            </Button>
          )}
        </Space>
      </div>

      {isAutoDialing && (
        <div className="calling-status dialing" style={{ marginBottom: 16 }}>
          <Badge status="processing" />
          <span>自动拨号进行中... {dialStatus === 'dialing' ? '正在拨打' : '通话中'}</span>
        </div>
      )}

      <Table columns={columns} dataSource={customers} rowKey="id" loading={loading} />

      {/* 通话中弹窗 */}
      <Modal
        title="通话中"
        open={callModalVisible}
        onCancel={handleEndCall}
        footer={[
          <Button key="end" type="primary" danger onClick={handleEndCall}>
            结束通话
          </Button>
        ]}
        width={500}
      >
        <div style={{ textAlign: 'center', padding: 24 }}>
          <PhoneOutlined style={{ fontSize: 48, color: '#1890ff' }} />
          <h3 style={{ marginTop: 16 }}>{callingCustomer?.phone}</h3>
          <p>{callingCustomer?.name}</p>
          <p style={{ color: '#999' }}>{callingCustomer?.remark}</p>
          <div className="calling-status" style={{ marginTop: 24 }}>
            <Badge status={dialStatus === 'connected' ? 'success' : 'processing'} />
            <span>{dialStatus === 'connected' ? '通话中' : '正在拨打...'}</span>
          </div>
        </div>
      </Modal>

      {/* 备注弹窗 */}
      <Modal
        title="编辑通话备注"
        open={notesModalVisible}
        onOk={() => noteForm.submit()}
        onCancel={() => setNotesModalVisible(false)}
      >
        <Form form={noteForm} onFinish={handleSaveNotes}>
          <Form.Item name="call_notes" label="通话备注">
            <Input.TextArea rows={4} />
          </Form.Item>
          <Form.Item name="call_result" label="通话结果">
            <Select placeholder="选择通话结果">
              <Option value="interested">有意向</Option>
              <Option value="not_interested">无意向</Option>
              <Option value="callback">需回访</Option>
              <Option value="wrong_number">号码错误</Option>
            </Select>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
