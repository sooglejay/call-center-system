import { useEffect, useState, useMemo } from 'react';
import { Table, Button, Modal, Upload, message, Tabs, Select, Form, Input, Badge, Space, Tag, Radio, Divider, Typography, Alert, Card, Row, Col } from 'antd';
import { UploadOutlined, CameraOutlined, UserAddOutlined, TeamOutlined, InfoCircleOutlined, DownloadOutlined } from '@ant-design/icons';
import { customerApi, configApi, userApi } from '../../services/api';
import type { Customer, User } from '../../services/api';
import * as XLSX from 'xlsx';

const { TabPane } = Tabs;
const { Search } = Input;
const { Text } = Typography;

// 获取姓氏首字母
const getFirstLetter = (name: string): string => {
  if (!name) return '#';
  const firstChar = name.charAt(0);
  if (/[\u4e00-\u9fa5]/.test(firstChar)) {
    const pinyinMap: Record<string, string> = {
      '阿': 'A', '艾': 'A', '安': 'A', '白': 'B', '班': 'B', '包': 'B', '鲍': 'B', '毕': 'B', '边': 'B', '卞': 'B',
      '蔡': 'C', '曹': 'C', '岑': 'C', '常': 'C', '陈': 'C', '程': 'C', '池': 'C', '褚': 'C', '楚': 'C', '崔': 'C',
      '戴': 'D', '邓': 'D', '丁': 'D', '董': 'D', '杜': 'D', '段': 'D',
      '樊': 'F', '范': 'F', '方': 'F', '费': 'F', '冯': 'F', '符': 'F', '傅': 'F', '富': 'F',
      '高': 'G', '葛': 'G', '耿': 'G', '龚': 'G', '顾': 'G', '管': 'G', '郭': 'G',
      '韩': 'H', '郝': 'H', '何': 'H', '贺': 'H', '侯': 'H', '胡': 'H', '花': 'H', '华': 'H', '黄': 'H', '霍': 'H',
      '姬': 'J', '纪': 'J', '季': 'J', '贾': 'J', '简': 'J', '江': 'J', '姜': 'J', '蒋': 'J', '金': 'J', '靳': 'J', '景': 'J', '静': 'J',
      '康': 'K', '柯': 'K', '孔': 'K',
      '赖': 'L', '兰': 'L', '雷': 'L', '黎': 'L', '李': 'L', '梁': 'L', '林': 'L', '刘': 'L', '柳': 'L', '龙': 'L', '卢': 'L', '鲁': 'L', '陆': 'L', '路': 'L', '罗': 'L', '吕': 'L',
      '马': 'M', '毛': 'M', '茅': 'M', '梅': 'M', '孟': 'M', '米': 'M', '苗': 'M', '闵': 'M', '莫': 'M', '穆': 'M',
      '倪': 'N', '宁': 'N', '牛': 'N', '欧': 'O', '区': 'O',
      '潘': 'P', '庞': 'P', '裴': 'P', '彭': 'P', '皮': 'P', '朴': 'P',
      '齐': 'Q', '钱': 'Q', '乔': 'Q', '秦': 'Q', '邱': 'Q', '裘': 'Q', '曲': 'Q',
      '冉': 'R', '任': 'R', '荣': 'R', '阮': 'R',
      '沙': 'S', '邵': 'S', '沈': 'S', '盛': 'S', '施': 'S', '石': 'S', '史': 'S', '舒': 'S', '宋': 'S', '苏': 'S', '孙': 'S', '索': 'S',
      '汤': 'T', '唐': 'T', '陶': 'T', '田': 'T', '童': 'T',
      '万': 'W', '汪': 'W', '王': 'W', '韦': 'W', '卫': 'W', '魏': 'W', '温': 'W', '文': 'W', '翁': 'W', '巫': 'W', '吴': 'W', '伍': 'W', '武': 'W',
      '席': 'X', '夏': 'X', '项': 'X', '萧': 'X', '谢': 'X', '辛': 'X', '邢': 'X', '熊': 'X', '徐': 'X', '许': 'X', '薛': 'X',
      '严': 'Y', '颜': 'Y', '杨': 'Y', '叶': 'Y', '易': 'Y', '殷': 'Y', '尹': 'Y', '应': 'Y', '尤': 'Y', '于': 'Y', '余': 'Y', '俞': 'Y', '虞': 'Y', '袁': 'Y', '岳': 'Y', '云': 'Y',
      '藏': 'Z', '曾': 'Z', '翟': 'Z', '詹': 'Z', '张': 'Z', '章': 'Z', '赵': 'Z', '郑': 'Z', '钟': 'Z', '周': 'Z', '朱': 'Z', '诸': 'Z', '祝': 'Z', '庄': 'Z'
    };
    return pinyinMap[firstChar] || '#';
  }
  return firstChar.toUpperCase();
};

export default function CustomerManagement() {
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [loading, setLoading] = useState(false);
  const [importModalVisible, setImportModalVisible] = useState(false);
  const [importGuideVisible, setImportGuideVisible] = useState(false);
  const [assignModalVisible, setAssignModalVisible] = useState(false);
  const [importedData, setImportedData] = useState<any[]>([]);
  const [agents, setAgents] = useState<User[]>([]);
  const [selectedAgent, setSelectedAgent] = useState<number | undefined>();
  const [assignAgentId, setAssignAgentId] = useState<number | undefined>();
  const [selectedRowKeys, setSelectedRowKeys] = useState<number[]>([]);
  const [searchText, setSearchText] = useState('');
  const [filterStatus, setFilterStatus] = useState<string>('');
  const [filterAssigned, setFilterAssigned] = useState<string>('');
  const [sortBy, setSortBy] = useState<string>('created_at');
  const [activeLetters, setActiveLetters] = useState<string[]>([]);
  const [nameGroups, setNameGroups] = useState<Record<string, number>>({});
  const [detailModalVisible, setDetailModalVisible] = useState(false);
  const [editModalVisible, setEditModalVisible] = useState(false);
  const [currentCustomer, setCurrentCustomer] = useState<Customer | null>(null);
  const [editForm] = Form.useForm();
  const [importDataSource, setImportDataSource] = useState<'mock' | 'real'>('real');

  useEffect(() => {
    fetchCustomers();
    fetchAgents();
  }, []);

  const fetchCustomers = async () => {
    setLoading(true);
    try {
      const response = await customerApi.getCustomers({ 
        sort_by: sortBy
      });
      setCustomers(response.data.data);
      if (response.data.name_groups) {
        setNameGroups(response.data.name_groups);
      }
    } catch (error) {
      console.error('获取客户列表失败:', error);
      message.error('获取客户列表失败');
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

  // 按姓氏分组
  const groupedCustomers = useMemo(() => {
    let filtered = customers;
    
    // 搜索过滤
    if (searchText) {
      const lowerSearch = searchText.toLowerCase();
      filtered = filtered.filter(c => 
        (c.name && c.name.toLowerCase().includes(lowerSearch)) ||
        (c.phone && c.phone.includes(searchText))
      );
    }
    
    // 状态过滤
    if (filterStatus) {
      filtered = filtered.filter(c => c.status === filterStatus);
    }
    
    // 客服过滤
    if (filterAssigned) {
      if (filterAssigned === '0') {
        filtered = filtered.filter(c => !c.assigned_to);
      } else {
        filtered = filtered.filter(c => c.assigned_to === parseInt(filterAssigned));
      }
    }
    
    // 首字母过滤（支持多选）
    if (activeLetters.length > 0) {
      filtered = filtered.filter(c => activeLetters.includes(getFirstLetter(c.name || '')));
    }
    
    // 按姓氏排序
    if (sortBy === 'name') {
      filtered = [...filtered].sort((a, b) => {
        const letterA = getFirstLetter(a.name || '');
        const letterB = getFirstLetter(b.name || '');
        if (letterA !== letterB) return letterA.localeCompare(letterB);
        return (a.name || '').localeCompare(b.name || '');
      });
    }
    
    // 分组
    const groups: Record<string, Customer[]> = {};
    filtered.forEach(customer => {
      const letter = getFirstLetter(customer.name || '');
      if (!groups[letter]) groups[letter] = [];
      groups[letter].push(customer);
    });
    
    return groups;
  }, [customers, searchText, filterStatus, filterAssigned, activeLetters, sortBy]);

  // 处理分组表格的选择变化（支持跨组多选）
  const handleGroupSelectionChange = (groupCustomers: Customer[], keys: (string | number)[], selected: boolean) => {
    const groupIds = groupCustomers.map(c => c.id);
    if (selected) {
      // 选中：添加当前组的选中项，保留其他组的选中项
      const otherSelectedKeys = selectedRowKeys.filter(k => !groupIds.includes(k as number));
      setSelectedRowKeys([...otherSelectedKeys, ...(keys as number[])]);
    } else {
      // 取消选中：移除当前组的选中项，保留其他组的选中项
      const otherSelectedKeys = selectedRowKeys.filter(k => !groupIds.includes(k as number));
      setSelectedRowKeys(otherSelectedKeys);
    }
  };

  // 下载导入模板
  const downloadTemplate = () => {
    const template = [
      ['姓名', '电话', '邮箱', '公司', '地址', '备注'],
      ['张三', '13800138001', 'zhangsan@example.com', '张三科技', '北京市朝阳区', 'VIP客户'],
      ['李四', '13900139001', 'lisi@example.com', '李四集团', '上海市浦东新区', ''],
      ['王五', '13700137001', '', '', '', '潜在客户'],
    ];
    
    const ws = XLSX.utils.aoa_to_sheet(template);
    const wb = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(wb, ws, '客户导入模板');
    
    // 设置列宽
    ws['!cols'] = [
      { wch: 12 }, // 姓名
      { wch: 15 }, // 电话
      { wch: 25 }, // 邮箱
      { wch: 20 }, // 公司
      { wch: 30 }, // 地址
      { wch: 20 }, // 备注
    ];
    
    XLSX.writeFile(wb, '客户导入模板.xlsx');
    message.success('模板下载成功');
  };

  // 处理文件上传（从引导弹窗触发）
  const handleFileUploadFromGuide = async (file: File) => {
    const formData = new FormData();
    formData.append('file', file);
    
    try {
      const response = await configApi.uploadFile(formData);
      setImportedData(response.data.data);
      setImportGuideVisible(false);
      setImportModalVisible(true);
      message.success(`成功解析 ${response.data.data.length} 条记录`);
    } catch (error) {
      message.error('文件解析失败，请检查文件格式是否符合要求');
    }
    return false;
  };

  const handleImport = async () => {
    try {
      await customerApi.importCustomers(importedData, selectedAgent, importDataSource);
      message.success(`成功导入 ${importedData.length} 条${importDataSource === 'real' ? '真实' : '测试'}数据`);
      setImportModalVisible(false);
      setImportedData([]);
      setImportDataSource('real');
      fetchCustomers();
    } catch (error) {
      message.error('导入失败');
    }
  };

  const handleBatchAssign = async () => {
    console.log('[批量分配] 开始分配流程:', {
      selectedCustomers: selectedRowKeys,
      targetAgentId: assignAgentId
    });
    
    if (selectedRowKeys.length === 0) {
      message.warning('请先选择客户');
      return;
    }
    if (!assignAgentId) {
      message.warning('请选择要分配的客服');
      return;
    }
    
    try {
      console.log(`[批量分配] 调用API: customer_ids=${selectedRowKeys}, assigned_to=${assignAgentId}`);
      const response = await customerApi.batchAssign(selectedRowKeys, assignAgentId);
      console.log('[批量分配] API响应:', response.data);
      
      message.success(response.data.message);
      setAssignModalVisible(false);
      setSelectedRowKeys([]);
      setAssignAgentId(undefined);
      fetchCustomers();
    } catch (error: any) {
      console.error('[批量分配] 失败:', error);
      
      // 提取详细错误信息
      const errorDetail = error.response?.data?.detail || '';
      const errorMessage = error.response?.data?.error || '分配失败';
      const failedCount = error.response?.data?.failed_count;
      
      let displayMessage = errorMessage;
      if (errorDetail) {
        displayMessage += `: ${errorDetail}`;
      }
      if (failedCount !== undefined && failedCount > 0) {
        displayMessage += ` (失败${failedCount}个)`;
      }
      
      message.error(displayMessage);
      
      // 如果是部分失败，给出提示
      if (error.response?.data?.assigned_count > 0) {
        message.warning(`部分成功: ${error.response.data.assigned_count}个客户已分配`);
      }
    }
  };

  const statusColors: Record<string, string> = {
    pending: 'default',
    contacted: 'processing',
    converted: 'success',
    not_interested: 'error',
    interested: 'warning'
  };

  const statusLabels: Record<string, string> = {
    pending: '待跟进',
    contacted: '已联系',
    converted: '已转化',
    not_interested: '无意向',
    interested: '有意向'
  };

  const columns = [
    { 
      title: '客户姓名', 
      dataIndex: 'name', 
      key: 'name',
      render: (name: string, record: Customer) => (
        <Space direction="vertical" size={0}>
          <Text strong>{name || '未命名'}</Text>
          <Tag color={statusColors[record.status || 'pending']}>
            {statusLabels[record.status || 'pending']}
          </Tag>
        </Space>
      )
    },
    { 
      title: '电话号码', 
      dataIndex: 'phone', 
      key: 'phone' 
    },
    { 
      title: '关联客服', 
      dataIndex: 'assigned_to_name', 
      key: 'assigned_to_name',
      render: (name: string) => (
        name && name !== '未分配' ? (
          <Tag color="blue" icon={<TeamOutlined />}>
            {name}
          </Tag>
        ) : (
          <Tag color="default">未分配</Tag>
        )
      )
    },
    { 
      title: '导入人', 
      dataIndex: 'imported_by_name', 
      key: 'imported_by_name' 
    },
    { 
      title: '导入时间', 
      dataIndex: 'created_at', 
      key: 'created_at',
      render: (date: string) => new Date(date).toLocaleDateString()
    },
    {
      title: '操作',
      key: 'action',
      render: (_: any, record: Customer) => (
        <Space>
          <Button 
            type="link" 
            size="small"
            onClick={() => {
              setCurrentCustomer(record);
              setDetailModalVisible(true);
            }}
          >
            详情
          </Button>
          <Button 
            type="link" 
            size="small"
            onClick={() => {
              setCurrentCustomer(record);
              editForm.setFieldsValue({
                name: record.name,
                phone: record.phone,
                email: record.email,
                company: record.company,
                address: record.address,
                status: record.status,
                remark: record.remark,
              });
              setEditModalVisible(true);
            }}
          >
            编辑
          </Button>
        </Space>
      )
    }
  ];

  // 字母索引导航
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ#'.split('');

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16, alignItems: 'center' }}>
        <Space>
          <h2 style={{ margin: 0 }}>客户管理</h2>
          {selectedRowKeys.length > 0 && (
            <Tag color="blue" style={{ fontSize: 14, padding: '4px 8px' }}>
              已选 {selectedRowKeys.length} 个客户
            </Tag>
          )}
        </Space>
        <Space>
          {selectedRowKeys.length > 0 && (
            <Button 
              type="primary" 
              icon={<UserAddOutlined />}
              onClick={() => setAssignModalVisible(true)}
            >
              批量分配 ({selectedRowKeys.length})
            </Button>
          )}
          <Button 
            icon={<UploadOutlined />} 
            onClick={() => setImportGuideVisible(true)}
          >
            导入Excel/CSV
          </Button>
          <Button icon={<CameraOutlined />} onClick={() => message.info('OCR功能需要集成OCR服务')}>
            拍照识别
          </Button>
        </Space>
      </div>

      {/* 筛选和排序工具栏 */}
      <div style={{ marginBottom: 16, padding: 16, background: '#f5f5f5', borderRadius: 8 }}>
        <Space wrap align="center" style={{ width: '100%' }}>
          <Search 
            placeholder="搜索姓名或电话" 
            allowClear 
            onSearch={setSearchText}
            style={{ width: 200 }}
          />
          <Select 
            placeholder="状态筛选" 
            allowClear 
            style={{ width: 120 }}
            onChange={setFilterStatus}
            options={[
              { value: 'pending', label: '待跟进' },
              { value: 'contacted', label: '已联系' },
              { value: 'converted', label: '已转化' },
              { value: 'not_interested', label: '无意向' },
              { value: 'interested', label: '有意向' }
            ]}
          />
          <Select 
            placeholder="客服筛选" 
            allowClear 
            style={{ width: 150 }}
            onChange={setFilterAssigned}
            options={[
              { value: '0', label: '未分配' },
              ...agents.map(a => ({ value: a.id.toString(), label: a.real_name }))
            ]}
          />
          <Radio.Group value={sortBy} onChange={e => setSortBy(e.target.value)}>
            <Radio.Button value="created_at">按时间</Radio.Button>
            <Radio.Button value="name">按姓名</Radio.Button>
          </Radio.Group>
          <Button type="primary" onClick={fetchCustomers}>刷新</Button>
        </Space>

        {/* 姓氏首字母索引（支持多选） */}
        <Divider style={{ margin: '12px 0' }} />
        <Space wrap>
          <Text type="secondary">姓氏索引（可多选）:</Text>
          <Button 
            type={activeLetters.length === 0 ? 'primary' : 'default'} 
            size="small"
            onClick={() => setActiveLetters([])}
          >
            全部
          </Button>
          {alphabet.map(letter => {
            const count = nameGroups[letter] || 0;
            const isSelected = activeLetters.includes(letter);
            return (
              <Button
                key={letter}
                type={isSelected ? 'primary' : 'default'}
                size="small"
                disabled={count === 0}
                onClick={() => {
                  if (isSelected) {
                    setActiveLetters(activeLetters.filter(l => l !== letter));
                  } else {
                    setActiveLetters([...activeLetters, letter]);
                  }
                }}
              >
                {letter} {count > 0 && `(${count})`}
              </Button>
            );
          })}
        </Space>
      </div>

      <Tabs defaultActiveKey="list">
        <TabPane tab="客户列表" key="list">
          {/* 按姓氏分组显示 */}
          {sortBy === 'name' ? (
            Object.entries(groupedCustomers)
              .sort(([a], [b]) => a.localeCompare(b))
              .map(([letter, groupCustomers]) => (
                <div key={letter} style={{ marginBottom: 24 }}>
                  <div style={{ 
                    display: 'flex', 
                    alignItems: 'center', 
                    marginBottom: 8,
                    padding: '8px 16px',
                    background: '#e6f7ff',
                    borderRadius: 4
                  }}>
                    <Text strong style={{ fontSize: 18, marginRight: 8 }}>{letter}</Text>
                    <Badge count={groupCustomers.length} style={{ backgroundColor: '#1890ff' }} />
                  </div>
                  <Table 
                    columns={columns} 
                    dataSource={groupCustomers} 
                    rowKey="id" 
                    loading={loading}
                    pagination={false}
                    rowSelection={{
                      selectedRowKeys: selectedRowKeys.filter(key => 
                        groupCustomers.some(c => c.id === key)
                      ),
                      onChange: (keys) => handleGroupSelectionChange(
                        groupCustomers, 
                        keys as number[], 
                        keys.length > 0
                      ),
                      onSelect: (record, selected) => {
                        if (selected) {
                          setSelectedRowKeys([...selectedRowKeys, record.id]);
                        } else {
                          setSelectedRowKeys(selectedRowKeys.filter(k => k !== record.id));
                        }
                      },
                      onSelectAll: (selected, _selectedRows, changeRows) => {
                        const changeIds = changeRows.map(r => r.id);
                        if (selected) {
                          setSelectedRowKeys([...selectedRowKeys, ...changeIds]);
                        } else {
                          setSelectedRowKeys(selectedRowKeys.filter(k => !changeIds.includes(k)));
                        }
                      }
                    }}
                  />
                </div>
              ))
          ) : (
            <Table 
              columns={columns} 
              dataSource={Object.values(groupedCustomers).flat()} 
              rowKey="id" 
              loading={loading}
              rowSelection={{
                selectedRowKeys,
                onChange: (keys) => setSelectedRowKeys(keys as number[])
              }}
              pagination={{ pageSize: 20 }}
            />
          )}
        </TabPane>
      </Tabs>

      {/* 导入引导弹窗 */}
      <Modal
        title={
          <Space>
            <InfoCircleOutlined style={{ color: '#1890ff' }} />
            <span>导入客户数据</span>
          </Space>
        }
        open={importGuideVisible}
        onCancel={() => setImportGuideVisible(false)}
        footer={[
          <Button key="template" icon={<DownloadOutlined />} onClick={downloadTemplate}>
            下载模板
          </Button>,
          <Upload
            key="upload"
            beforeUpload={handleFileUploadFromGuide}
            showUploadList={false}
            accept=".xlsx,.xls,.csv"
          >
            <Button type="primary" icon={<UploadOutlined />}>
              选择文件导入
            </Button>
          </Upload>,
        ]}
        width={700}
      >
        <Alert
          message="导入前请确认文件格式"
          description="系统支持 Excel(.xlsx/.xls) 和 CSV 格式文件，请确保文件内容符合以下字段要求"
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
        />
        
        <Card title="必填字段" size="small" style={{ marginBottom: 16 }}>
          <Row gutter={[16, 8]}>
            <Col span={12}>
              <Text strong style={{ color: '#ff4d4f' }}>姓名 (name)</Text>
              <div style={{ color: '#666', fontSize: 12 }}>客户姓名，支持中英文</div>
            </Col>
            <Col span={12}>
              <Text strong style={{ color: '#ff4d4f' }}>电话 (phone)</Text>
              <div style={{ color: '#666', fontSize: 12 }}>手机号码，如：13800138000</div>
            </Col>
          </Row>
        </Card>

        <Card title="可选字段" size="small" style={{ marginBottom: 16 }}>
          <Row gutter={[16, 12]}>
            <Col span={12}>
              <Text strong>邮箱 (email)</Text>
              <div style={{ color: '#666', fontSize: 12 }}>如：customer@example.com</div>
            </Col>
            <Col span={12}>
              <Text strong>公司 (company)</Text>
              <div style={{ color: '#666', fontSize: 12 }}>客户所在公司名称</div>
            </Col>
            <Col span={12}>
              <Text strong>地址 (address)</Text>
              <div style={{ color: '#666', fontSize: 12 }}>客户联系地址</div>
            </Col>
            <Col span={12}>
              <Text strong>备注 (remark)</Text>
              <div style={{ color: '#666', fontSize: 12 }}>其他补充信息</div>
            </Col>
          </Row>
        </Card>

        <Alert
          message="导入提示"
          description={
            <ul style={{ margin: 0, paddingLeft: 16 }}>
              <li>建议先下载模板文件，按模板格式填写数据</li>
              <li>Excel 文件建议只保留一个工作表</li>
              <li>第一行应为表头（姓名、电话等），数据从第二行开始</li>
              <li>单次导入建议不超过 1000 条记录</li>
              <li>电话号码会自动去重，重复号码将跳过</li>
            </ul>
          }
          type="warning"
          showIcon
        />
      </Modal>

      {/* 导入模态框 */}
      <Modal
        title="导入客户数据"
        open={importModalVisible}
        onOk={handleImport}
        onCancel={() => {
          setImportModalVisible(false);
          setImportedData([]);
          setImportDataSource('real');
        }}
        width={800}
      >
        <Form layout="vertical">
          <Form.Item label="数据类型">
            <Radio.Group 
              value={importDataSource} 
              onChange={e => setImportDataSource(e.target.value)}
              buttonStyle="solid"
            >
              <Radio.Button value="real">真实客户数据</Radio.Button>
              <Radio.Button value="mock">测试数据</Radio.Button>
            </Radio.Group>
            <div style={{ marginTop: 8, color: '#666', fontSize: 12 }}>
              {importDataSource === 'real' 
                ? '真实数据：仅对拥有真实数据权限的客服可见' 
                : '测试数据：用于系统测试和培训，对测试数据权限的客服可见'}
            </div>
          </Form.Item>
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
          rowKey={(_, index) => index?.toString() || ''}
        />
      </Modal>

      {/* 批量分配模态框 */}
      <Modal
        title="批量分配客服"
        open={assignModalVisible}
        onOk={handleBatchAssign}
        onCancel={() => {
          setAssignModalVisible(false);
          setAssignAgentId(undefined);
        }}
      >
        <Form layout="vertical">
          <Form.Item label={`已选择 ${selectedRowKeys.length} 个客户`}>
            <Select
              placeholder="选择要分配的客服"
              style={{ width: '100%' }}
              value={assignAgentId}
              onChange={(value) => setAssignAgentId(value)}
            >
              {agents.map(agent => (
                <Select.Option key={agent.id} value={agent.id}>
                  {agent.real_name}
                </Select.Option>
              ))}
            </Select>
          </Form.Item>
        </Form>
      </Modal>

      {/* 客户详情弹窗 */}
      <Modal
        title="客户详情"
        open={detailModalVisible}
        onCancel={() => {
          setDetailModalVisible(false);
          setCurrentCustomer(null);
        }}
        footer={[
          <Button key="close" onClick={() => {
            setDetailModalVisible(false);
            setCurrentCustomer(null);
          }}>
            关闭
          </Button>
        ]}
        width={600}
      >
        {currentCustomer && (
          <div>
            <Row gutter={[16, 16]}>
              <Col span={12}>
                <Text type="secondary">客户姓名</Text>
                <div style={{ fontSize: 16, fontWeight: 'bold' }}>{currentCustomer.name || '未命名'}</div>
              </Col>
              <Col span={12}>
                <Text type="secondary">联系电话</Text>
                <div style={{ fontSize: 16 }}>{currentCustomer.phone || '-'}</div>
              </Col>
              <Col span={12}>
                <Text type="secondary">邮箱</Text>
                <div>{currentCustomer.email || '-'}</div>
              </Col>
              <Col span={12}>
                <Text type="secondary">公司名称</Text>
                <div>{currentCustomer.company || '-'}</div>
              </Col>
              <Col span={24}>
                <Text type="secondary">联系地址</Text>
                <div>{currentCustomer.address || '-'}</div>
              </Col>
              <Col span={12}>
                <Text type="secondary">客户状态</Text>
                <div>
                  <Tag color={statusColors[currentCustomer.status || 'pending']}>
                    {statusLabels[currentCustomer.status || 'pending']}
                  </Tag>
                </div>
              </Col>
              <Col span={12}>
                <Text type="secondary">分配客服</Text>
                <div>{currentCustomer.assigned_to_name || <Tag color="default">未分配</Tag>}</div>
              </Col>
              <Col span={24}>
                <Text type="secondary">备注</Text>
                <div style={{ background: '#f5f5f5', padding: 12, borderRadius: 4, minHeight: 60 }}>
                  {currentCustomer.remark || '无备注'}
                </div>
              </Col>
              <Col span={12}>
                <Text type="secondary">导入时间</Text>
                <div>{currentCustomer.created_at ? new Date(currentCustomer.created_at).toLocaleString() : '-'}</div>
              </Col>
              <Col span={12}>
                <Text type="secondary">最后更新</Text>
                <div>{currentCustomer.updated_at ? new Date(currentCustomer.updated_at).toLocaleString() : '-'}</div>
              </Col>
            </Row>
          </div>
        )}
      </Modal>

      {/* 客户编辑弹窗 */}
      <Modal
        title="编辑客户"
        open={editModalVisible}
        onOk={async () => {
          try {
            const values = await editForm.validateFields();
            await customerApi.updateCustomer(currentCustomer!.id, values);
            message.success('更新成功');
            setEditModalVisible(false);
            setCurrentCustomer(null);
            fetchCustomers();
          } catch (error) {
            message.error('更新失败');
          }
        }}
        onCancel={() => {
          setEditModalVisible(false);
          setCurrentCustomer(null);
          editForm.resetFields();
        }}
        width={600}
      >
        <Form form={editForm} layout="vertical">
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="name"
                label="客户姓名"
                rules={[{ required: true, message: '请输入客户姓名' }]}
              >
                <Input placeholder="客户姓名" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="phone"
                label="联系电话"
                rules={[{ required: true, message: '请输入联系电话' }]}
              >
                <Input placeholder="联系电话" />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="email"
                label="邮箱"
              >
                <Input placeholder="邮箱" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="company"
                label="公司名称"
              >
                <Input placeholder="公司名称" />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item
            name="address"
            label="联系地址"
          >
            <Input.TextArea rows={2} placeholder="联系地址" />
          </Form.Item>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="status"
                label="客户状态"
              >
                <Select placeholder="选择状态">
                  <Select.Option value="pending">待跟进</Select.Option>
                  <Select.Option value="contacted">已联系</Select.Option>
                  <Select.Option value="interested">有意向</Select.Option>
                  <Select.Option value="converted">已转化</Select.Option>
                  <Select.Option value="not_interested">无意向</Select.Option>
                </Select>
              </Form.Item>
            </Col>
          </Row>
          <Form.Item
            name="remark"
            label="备注"
          >
            <Input.TextArea rows={3} placeholder="备注信息" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
