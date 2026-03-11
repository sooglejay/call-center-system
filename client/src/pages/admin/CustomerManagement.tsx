import { useEffect, useState, useRef } from 'react';
import { Table, Button, Modal, Upload, message, Tabs, Select, Form, Input } from 'antd';
import { UploadOutlined, ImportOutlined, CameraOutlined } from '@ant-design/icons';
import { customerApi, configApi, userApi } from '../../services/api';
import type { Customer, User } from '../../services/api';
import * as XLSX from 'xlsx';

const { TabPane } = Tabs;

export default function CustomerManagement() {
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [loading, setLoading] = useState(false);
  const [importModalVisible, setImportModalVisible] = useState(false);
  const [importedData, setImportedData] = useState<any[]>([]);
  const [agents, setAgents] = useState<User[]>([]);
  const [selectedAgent, setSelectedAgent] = useState<number | undefined>();
  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    fetchCustomers();
    fetchAgents();
  }, []);

  const fetchCustomers = async () => {
    setLoading(true);
    try {
      const response = await customerApi.getCustomers();
      setCustomers(response.data.data);
    } finally {
      setLoading(false);
    }
  };

  const fetchAgents = async () => {
    try {
      const response = await userApi.getAgents();
      setAgents(response.data);
    } catch (error) {
      console.error('获取客服列表失败');
    }
  };

  const handleFileUpload = async (file: File) => {
    const formData = new FormData();
    formData.append('file', file);
    
    try {
      const response = await configApi.uploadFile(formData);
      setImportedData(response.data.data);
      setImportModalVisible(true);
      message.success(`成功解析 ${response.data.data.length} 条记录`);
    } catch (error) {
      message.error('文件解析失败');
    }
    return false;
  };

  const handleImport = async () => {
    try {
      await customerApi.importCustomers(importedData, selectedAgent);
      message.success('导入成功');
      setImportModalVisible(false);
      setImportedData([]);
      fetchCustomers();
    } catch (error) {
      message.error('导入失败');
    }
  };

  const columns = [
    { title: '电话号码', dataIndex: 'phone', key: 'phone' },
    { title: '客户姓名', dataIndex: 'name', key: 'name' },
    { title: '备注', dataIndex: 'remark', key: 'remark' },
    { title: '导入人', dataIndex: 'imported_by_name', key: 'imported_by_name' },
    { title: '导入时间', dataIndex: 'imported_at', key: 'imported_at' },
  ];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
        <h2>客户管理</h2>
        <div style={{ display: 'flex', gap: 8 }}>
          <Upload beforeUpload={handleFileUpload} showUploadList={false} accept=".xlsx,.xls,.csv,.txt">
            <Button icon={<UploadOutlined />}>导入Excel/CSV</Button>
          </Upload>
          <Button icon={<CameraOutlined />} onClick={() => message.info('OCR功能需要集成OCR服务')}>
            拍照识别
          </Button>
        </div>
      </div>

      <Tabs defaultActiveKey="list">
        <TabPane tab="客户列表" key="list">
          <Table columns={columns} dataSource={customers} rowKey="id" loading={loading} />
        </TabPane>
      </Tabs>

      <Modal
        title="导入客户数据"
        open={importModalVisible}
        onOk={handleImport}
        onCancel={() => {
          setImportModalVisible(false);
          setImportedData([]);
        }}
        width={800}
      >
        <Form layout="vertical">
          <Form.Item label="分配给客服">
            <Select
              placeholder="选择客服（可选）"
              allowClear
              onChange={(value) => setSelectedAgent(value)}
            >
              {agents.map(agent => (
                <Select.Option key={agent.id} value={agent.id}>{agent.real_name}</Select.Option>
              ))}
            </Select>
          </Form.Item>
        </Form>
        <Table
          dataSource={importedData}
          columns={[
            { title: '电话号码', dataIndex: 'phone', key: 'phone' },
            { title: '客户姓名', dataIndex: 'name', key: 'name' },
            { title: '备注', dataIndex: 'remark', key: 'remark' },
          ]}
          pagination={{ pageSize: 5 }}
          rowKey={(record, index) => index?.toString() || ''}
        />
      </Modal>
    </div>
  );
}
