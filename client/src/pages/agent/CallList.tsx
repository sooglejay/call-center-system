import { useEffect, useState } from 'react';
import { Table, Button, Tag, Input, Select, Form, Modal, message, Badge, Space } from 'antd';
import { PhoneOutlined, PlayCircleOutlined, PauseCircleOutlined, EditOutlined, ExclamationCircleOutlined } from '@ant-design/icons';
import { customerApi, callApi } from '../../services/api';
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
  
  // 分页状态
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [total, setTotal] = useState(0);
  
  const { isAutoDialing, setAutoDialing, setCurrentCustomer, dialStatus, setDialStatus } = useAutoDialStore();
  const { config } = useAgentConfigStore();

  useEffect(() => {
    fetchCustomers();
  }, [currentPage, pageSize]);

  // 当筛选条件变化时，重置到第一页
  useEffect(() => {
    setCurrentPage(1);
  }, [filters]);

  const fetchCustomers = async () => {
    setLoading(true);
    try {
      const response = await customerApi.getAgentCustomers({
        status: filters.status,
        search: filters.search,
        page: currentPage,
        pageSize: pageSize
      });
      const customersData = response.data?.data || response.data || [];
      setCustomers(Array.isArray(customersData) ? customersData : []);
      setTotal(response.data?.pagination?.total || response.data?.total || 0);
    } catch (error: any) {
      console.error('获取客户列表失败:', error);
      message.error(error.response?.data?.error || '获取客户列表失败');
    } finally {
      setLoading(false);
    }
  };

  const startNextCall = async () => {
    try {
      const response = await callApi.getNextCall();
      if (response.data.message) {
        Modal.info({
          title: '提示',
          content: '没有待拨打的客户，请联系管理员分配任务',
          okText: '我知道了',
          onOk: () => {
            setAutoDialing(false);
          }
        });
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
      await callApi.createCall({
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

    } catch (error: any) {
      const errorMsg = error.response?.data?.error || '拨打电话失败';
      message.error(errorMsg);
      setDialStatus('idle');
      setCallModalVisible(false);
      setCallingCustomer(null);
    }
  };

  const handleEndCall = async () => {
    setDialStatus('idle');
    setCallModalVisible(false);
    setCallingCustomer(null);
    
    // 延迟后拨打下一个
    if (isAutoDialing) {
      const delay = (config?.dial_delay || 3) * 1000;
      message.info(`${(delay / 1000).toFixed(0)}秒后拨打下一个...`);
      setTimeout(() => {
        startNextCall();
      }, delay);
    }
  };

  const handleStartAutoDial = () => {
    if (customers.length === 0) {
      Modal.warning({
        title: '暂无拨打任务',
        content: '没有待拨打的客户，请联系管理员分配任务后再进行拨打',
        okText: '我知道了'
      });
      return;
    }
    
    Modal.confirm({
      title: '开始自动拨号',
      icon: <ExclamationCircleOutlined />,
      content: `即将开始自动拨号，共有 ${total} 个待拨打客户。拨号过程中您可以随时停止。`,
      okText: '开始',
      cancelText: '取消',
      onOk: () => {
        setAutoDialing(true);
        message.success('自动拨号已开始');
      }
    });
  };

  const handleStopAutoDial = () => {
    Modal.confirm({
      title: '停止自动拨号',
      content: '确定要停止自动拨号吗？',
      okText: '确定',
      cancelText: '取消',
      onOk: () => {
        setAutoDialing(false);
        setDialStatus('idle');
        setCallModalVisible(false);
        setCallingCustomer(null);
        message.info('已停止自动拨号');
      }
    });
  };

  const handleSaveNotes = async (values: any) => {
    if (!editingRecord) return;
    
    try {
      await callApi.updateCallNotes(editingRecord.id, values.call_notes);
      message.success('保存成功');
      setNotesModalVisible(false);
      fetchCustomers();
    } catch (error) {
      message.error('保存失败，请重试');
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
      render: (status: string) => {
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
              onClick={handleStartAutoDial}
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

      <Table 
        columns={columns} 
        dataSource={customers} 
        rowKey="id" 
        loading={loading}
        pagination={{
          current: currentPage,
          pageSize: pageSize,
          total: total,
          showSizeChanger: true,
          showQuickJumper: true,
          showTotal: (total) => `共 ${total} 条`,
          onChange: (page, size) => {
            setCurrentPage(page);
            if (size) setPageSize(size);
          }
        }}
      />

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
        </Form>
      </Modal>
    </div>
  );
}
