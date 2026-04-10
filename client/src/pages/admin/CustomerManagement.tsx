import { useEffect, useState, useMemo } from 'react';
import { Table, Button, Modal, Upload, message, Tabs, Select, Form, Input, Badge, Space, Tag, Radio, Divider, Typography, Alert, Card, Row, Col, Spin } from 'antd';
import { UploadOutlined, CameraOutlined, UserAddOutlined, TeamOutlined, InfoCircleOutlined, DownloadOutlined, PlusOutlined, DeleteOutlined, ExportOutlined, FileAddOutlined } from '@ant-design/icons';
import { customerApi, dataImportApi, userApi } from '../../services/api';
import { taskApi } from '../../services/api';
import type { Customer, User } from '../../services/api';
import * as XLSX from 'xlsx';

const { TabPane } = Tabs;
const { Search } = Input;
const { Text } = Typography;
const DEFAULT_CUSTOMER_TAG = '未打标客户';

// 系统字段定义
interface SystemField {
  key: string;
  label: string;
  required: boolean;
}

// 复合字段的子字段
interface SubField {
  key: string;
  label: string;
  type: string;
  samples: string[];
}

// 复合字段信息
interface CompositeField {
  separator: string;
  partCount: number;
  subFields: SubField[];
}

// CSV 预览数据
interface CsvPreviewData {
  columns: string[];
  preview: Record<string, string>[];
  total_rows: number;
  system_fields: SystemField[];
  suggestions: Record<string, string>;
  has_required_fields: boolean;
  composite_fields?: Record<string, CompositeField>; // 复合字段信息
}

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
  const [filterCallStatus, setFilterCallStatus] = useState<string>('');
  const [filterAssigned, setFilterAssigned] = useState<string>('');
  const [filterTag, setFilterTag] = useState<string>('');
  const [sortBy, setSortBy] = useState<string>('created_at');
  const [activeLetters, setActiveLetters] = useState<string[]>([]);
  const [nameGroups, setNameGroups] = useState<Record<string, number>>({});
  const [detailModalVisible, setDetailModalVisible] = useState(false);
  const [editModalVisible, setEditModalVisible] = useState(false);
  const [addModalVisible, setAddModalVisible] = useState(false);
  const [createTaskModalVisible, setCreateTaskModalVisible] = useState(false);
  const [createTaskForm] = Form.useForm();
  const [currentCustomer, setCurrentCustomer] = useState<Customer | null>(null);
  const [editForm] = Form.useForm();
  const [addForm] = Form.useForm();
  const [importDataSource, setImportDataSource] = useState<'mock' | 'real'>('real');
  const [availableTags, setAvailableTags] = useState<string[]>([DEFAULT_CUSTOMER_TAG]);
  const [selectedImportTag, setSelectedImportTag] = useState<string>('');

  // 分页状态
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [total, setTotal] = useState(0);

  // CSV 预览相关状态
  const [previewModalVisible, setPreviewModalVisible] = useState(false);
  const [previewData, setPreviewData] = useState<CsvPreviewData | null>(null);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [importLoading, setImportLoading] = useState(false);
  const [columnMapping, setColumnMapping] = useState<Record<string, string>>({});
  const [pendingFile, setPendingFile] = useState<File | null>(null);

  useEffect(() => {
    fetchCustomers();
    fetchAgents();
  }, [currentPage, pageSize, sortBy, searchText, filterStatus, filterCallStatus, filterAssigned, filterTag, activeLetters]);

  useEffect(() => {
    fetchTags();
  }, []);

  const fetchCustomers = async () => {
    setLoading(true);
    try {
      const response = await customerApi.getCustomers({ 
        sort_by: sortBy,
        page: currentPage,
        pageSize: pageSize,
        search: searchText || undefined,
        status: filterStatus || undefined,
        call_status: filterCallStatus || undefined,
        assigned_to: filterAssigned ? parseInt(filterAssigned) : undefined,
        tag: filterTag || undefined,
        name_letter: activeLetters.length > 0 ? activeLetters.join(',') : undefined
      });
      const customersData = response.data?.data || response.data || [];
      setCustomers(Array.isArray(customersData) ? customersData : []);
      setTotal(response.data?.total || 0);
      if (response.data?.name_groups) {
        setNameGroups(response.data.name_groups);
      }
    } catch (error) {
      console.error('获取客户列表失败:', error);
      message.error('获取客户列表失败');
    } finally {
      setLoading(false);
    }
  };

  // 当搜索或过滤条件改变时，重置到第一页
  useEffect(() => {
    setCurrentPage(1);
  }, [searchText, filterStatus, filterAssigned, filterCallStatus, filterTag]);

  const fetchAgents = async () => {
    try {
      const response = await userApi.getAgents();
      // getAgents 返回直接数组
      const agentsData = response.data || [];
      setAgents(Array.isArray(agentsData) ? agentsData : []);
    } catch (error) {
      console.error('获取客服列表失败');
    }
  };

  const fetchTags = async () => {
    try {
      const response = await customerApi.getTags();
      const tags = Array.isArray(response.data) ? response.data : [];
      const mergedTags = Array.from(new Set([DEFAULT_CUSTOMER_TAG, ...tags.filter(Boolean)]));
      setAvailableTags(mergedTags);
    } catch (error) {
      console.error('获取标签列表失败:', error);
    }
  };

  const normalizeTagValue = (values?: string[]) => {
    if (!values || values.length === 0) return '';
    const value = values[values.length - 1]?.trim();
    return value || '';
  };

  const normalizeEditableTagValue = (values?: string[]) => {
    const value = normalizeTagValue(values);
    return value || DEFAULT_CUSTOMER_TAG;
  };

  // 按姓氏分组（后端已做过滤和分页，前端只做分组）
  const groupedCustomers = useMemo(() => {
    // 按姓氏排序
    let sorted = [...customers];
    if (sortBy === 'name') {
      sorted = sorted.sort((a, b) => {
        const letterA = getFirstLetter(a.name || '');
        const letterB = getFirstLetter(b.name || '');
        if (letterA !== letterB) return letterA.localeCompare(letterB);
        return (a.name || '').localeCompare(b.name || '');
      });
    }
    
    // 分组
    const groups: Record<string, Customer[]> = {};
    sorted.forEach(customer => {
      const letter = getFirstLetter(customer.name || '');
      if (!groups[letter]) groups[letter] = [];
      groups[letter].push(customer);
    });
    
    return groups;
  }, [customers, sortBy]);

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
      ['姓名', '电话', '标签', '邮箱', '公司', '地址', '备注'],
      ['张三', '13800138001', '抖音渠道', 'zhangsan@example.com', '张三科技', '北京市朝阳区', 'VIP客户'],
      ['李四', '13900139001', '百度投放', 'lisi@example.com', '李四集团', '上海市浦东新区', ''],
      ['王五', '13700137001', '', '', '', '', '潜在客户'],
    ];
    
    const ws = XLSX.utils.aoa_to_sheet(template);
    const wb = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(wb, ws, '客户导入模板');
    
    // 设置列宽
    ws['!cols'] = [
      { wch: 12 }, // 姓名
      { wch: 15 }, // 电话
      { wch: 16 }, // 标签
      { wch: 25 }, // 邮箱
      { wch: 20 }, // 公司
      { wch: 30 }, // 地址
      { wch: 20 }, // 备注
    ];
    
    XLSX.writeFile(wb, '客户导入模板.xlsx');
    message.success('模板下载成功');
  };

  // 处理文件上传 - 使用动态列匹配预览
  const handleFileUploadFromGuide = async (file: File) => {
    setPreviewLoading(true);
    setPendingFile(file);
    
    const formData = new FormData();
    formData.append('file', file);
    
    try {
      const response = await dataImportApi.previewCsv(formData);
      const data = response.data as CsvPreviewData;
      setPreviewData(data);
      
      // 初始化列映射（使用建议值）
      const initialMapping: Record<string, string> = {};
      data.system_fields.forEach(field => {
        if (data.suggestions[field.key]) {
          initialMapping[field.key] = data.suggestions[field.key];
        }
      });
      setColumnMapping(initialMapping);
      
      setImportGuideVisible(false);
      setPreviewModalVisible(true);
    } catch (error: any) {
      message.error(error.response?.data?.error || '文件解析失败，请检查文件格式');
    } finally {
      setPreviewLoading(false);
    }
    return false;
  };

  // 执行导入 - 使用列映射
  const handleConfirmImport = async () => {
    if (!pendingFile) {
      message.error('请重新选择文件');
      return;
    }
    
    // 验证必填字段
    if (!columnMapping.name || !columnMapping.phone) {
      message.error('姓名和电话是必填字段，请确保已映射');
      return;
    }
    
    setImportLoading(true);
    
    const formData = new FormData();
    formData.append('file', pendingFile);
    formData.append('column_mapping', JSON.stringify(columnMapping));
    formData.append('data_source', importDataSource);
    
    // 添加复合字段配置
    if (previewData?.composite_fields) {
      formData.append('composite_fields', JSON.stringify(previewData.composite_fields));
    }
    
    if (selectedAgent) {
      formData.append('assigned_to', selectedAgent.toString());
    }
    if (selectedImportTag.trim()) {
      formData.append('tag', selectedImportTag.trim());
    }
    
    try {
      const response = await dataImportApi.importWithMapping(formData);
      const result = response.data;
      
      setPreviewModalVisible(false);
      setPendingFile(null);
      setColumnMapping({});
      setSelectedAgent(undefined);
      setSelectedImportTag('');
      
      // 显示导入结果
      Modal.success({
        title: '导入完成',
        content: (
          <div>
            <p>总记录数: {result.summary.total}</p>
            <p>成功导入: {result.summary.imported}</p>
            <p>重复跳过: {result.summary.duplicates}</p>
            <p>导入失败: {result.summary.errors}</p>
          </div>
        ),
      });
      
      fetchCustomers();
      fetchTags();
    } catch (error: any) {
      message.error(error.response?.data?.error || '导入失败，请重试');
    } finally {
      setImportLoading(false);
    }
  };

  // 旧的导入函数（保留兼容）
  const handleImport = async () => {
    try {
      await customerApi.importCustomers(importedData, selectedAgent, importDataSource, selectedImportTag.trim() || undefined);
      message.success(`成功导入 ${importedData.length} 条${importDataSource === 'real' ? '真实' : '测试'}数据`);
      setImportModalVisible(false);
      setImportedData([]);
      setImportDataSource('real');
      setSelectedImportTag('');
      fetchCustomers();
      fetchTags();
    } catch (error: any) {
      message.error(error.response?.data?.error || '导入失败，请重试');
    }
  };

  // 导出客户数据
  const handleExportCustomers = async () => {
    try {
      // 只导出选中的客户
      if (selectedRowKeys.length === 0) {
        message.warning('请先选择要导出的客户');
        return;
      }
      
      const customersData = customers.filter(c => selectedRowKeys.includes(c.id));
      
      if (customersData.length === 0) {
        message.warning('选中的客户数据不存在');
        return;
      }
      
      // 准备导出数据
      const exportData = customersData.map((customer: Customer) => ({
        '客户姓名': customer.name || '',
        '电话号码': customer.phone || '',
        '客户标签': customer.tag || DEFAULT_CUSTOMER_TAG,
        '邮箱': customer.email || '',
        '公司': customer.company || '',
        '地址': customer.address || '',
        '客户状态': statusLabels[customer.status || 'pending'] || '待跟进',
        '通话状态': callStatusLabels[customer.call_status || 'pending'] || '待拨打',
        '通话结果': customer.call_result || '',
        '分配客服': customer.assigned_to_name || '未分配',
        '导入人': customer.imported_by_name || '',
        '导入时间': customer.created_at ? new Date(customer.created_at).toLocaleString() : ''
      }));
      
      // 创建工作簿
      const ws = XLSX.utils.json_to_sheet(exportData);
      const wb = XLSX.utils.book_new();
      XLSX.utils.book_append_sheet(wb, ws, '客户数据');
      
      // 设置列宽
      ws['!cols'] = [
        { wch: 12 }, // 客户姓名
        { wch: 15 }, // 电话号码
        { wch: 16 }, // 客户标签
        { wch: 25 }, // 邮箱
        { wch: 20 }, // 公司
        { wch: 30 }, // 地址
        { wch: 10 }, // 客户状态
        { wch: 10 }, // 通话状态
        { wch: 15 }, // 通话结果
        { wch: 12 }, // 分配客服
        { wch: 12 }, // 导入人
        { wch: 20 }, // 导入时间
      ];
      
      // 生成文件名
      const timestamp = new Date().toISOString().slice(0, 19).replace(/:/g, '-');
      const filename = `客户数据_${timestamp}.xlsx`;
      
      XLSX.writeFile(wb, filename);
      message.success(`成功导出 ${exportData.length} 条客户数据`);
    } catch (error) {
      console.error('导出失败:', error);
      message.error('导出失败，请重试');
    }
  };

  // 删除客户
  const handleDeleteCustomer = (record: Customer) => {
    Modal.confirm({
      title: '确认删除',
      content: `确定要删除客户「${record.name || '未命名'}」吗？此操作不可恢复。`,
      okText: '删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          await customerApi.deleteCustomer(record.id);
          message.success('删除成功');
          fetchCustomers();
        } catch (error: any) {
          message.error(error.response?.data?.error || '删除失败');
        }
      }
    });
  };

  // 批量删除客户
  const handleBatchDelete = () => {
    if (selectedRowKeys.length === 0) {
      message.warning('请先选择客户');
      return;
    }
    
    Modal.confirm({
      title: '确认批量删除',
      content: `确定要删除选中的 ${selectedRowKeys.length} 个客户吗？此操作不可恢复。`,
      okText: '删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          // 循环调用单个删除接口
          const deletePromises = selectedRowKeys.map(id => customerApi.deleteCustomer(id));
          await Promise.all(deletePromises);
          message.success(`成功删除 ${selectedRowKeys.length} 个客户`);
          setSelectedRowKeys([]);
          fetchCustomers();
        } catch (error: any) {
          message.error(error.response?.data?.error || '批量删除失败');
        }
      }
    });
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

  // 批量创建任务
  const handleCreateTask = async () => {
    if (selectedRowKeys.length === 0) {
      message.warning('请先选择客户');
      return;
    }
    
    try {
      const values = await createTaskForm.validateFields();
      
      // 创建任务
      const taskData = {
        title: values.title,
        description: values.description || '',
        priority: values.priority || 'normal',
        assigned_to: values.assigned_to,
        due_date: values.due_date,
        customer_ids: selectedRowKeys
      };
      
      await taskApi.createTask(taskData);
      message.success(`任务创建成功，已添加 ${selectedRowKeys.length} 个客户到任务`);
      setCreateTaskModalVisible(false);
      createTaskForm.resetFields();
      setSelectedRowKeys([]);
      fetchCustomers();
    } catch (error: any) {
      if (error.errorFields) {
        // 表单验证错误
        return;
      }
      message.error(error.response?.data?.error || '创建任务失败');
    }
  };

  const statusColors: Record<string, string> = {
    pending: 'default',
    contacted: 'processing',
    converted: 'success',
    not_interested: 'error',
    interested: 'warning'
  };

  const callStatusColors: Record<string, string> = {
    pending: 'default',
    ringing: 'processing',
    connected: 'success',
    voicemail: 'warning',
    unanswered: 'error',
    failed: 'error',
    completed: 'success'
  };

  const statusLabels: Record<string, string> = {
    pending: '待跟进',
    contacted: '已联系',
    converted: '已转化',
    not_interested: '无意向',
    interested: '有意向'
  };

  const callStatusLabels: Record<string, string> = {
    pending: '待拨打',
    ringing: '响铃中',
    connected: '已接听',
    voicemail: '语音信箱',
    unanswered: '响铃未接',
    rejected: '对方拒接',
    busy: '对方忙线',
    power_off: '关机/停机',
    no_answer: '无人接听',
    ivr: 'IVR语音',
    other: '其他',
    failed: '拨打失败',
    completed: '已完成'
  };

  const columns = [
    {
      title: '序号',
      key: 'index',
      width: 60,
      align: 'center' as const,
      render: (_: any, __: any, index: number) => (currentPage - 1) * pageSize + index + 1
    },
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
      title: '客户标签',
      dataIndex: 'tag',
      key: 'tag',
      render: (tag: string) => <Tag color={tag === DEFAULT_CUSTOMER_TAG ? 'default' : 'geekblue'}>{tag || DEFAULT_CUSTOMER_TAG}</Tag>
    },
    { 
      title: '通话状态', 
      dataIndex: 'call_status', 
      key: 'call_status',
      render: (call_status: string, record: Customer) => (
        <Space direction="vertical" size={0}>
          <Tag color={callStatusColors[call_status || 'pending']}>
            {callStatusLabels[call_status || 'pending']}
          </Tag>
          {record.call_result && (
            <Text type="secondary" style={{ fontSize: 12 }}>
              {record.call_result}
            </Text>
          )}
        </Space>
      )
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
                tag: [record.tag || DEFAULT_CUSTOMER_TAG],
                remark: record.remark,
              });
              setEditModalVisible(true);
            }}
          >
            编辑
          </Button>
          <Button 
            type="link" 
            size="small"
            danger
            onClick={() => handleDeleteCustomer(record)}
          >
            删除
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
          <Button 
            type="primary" 
            icon={<UserAddOutlined />}
            onClick={() => {
              if (selectedRowKeys.length === 0) {
                message.warning('请先选择客户');
                return;
              }
              setAssignModalVisible(true);
            }}
          >
            批量分配 {selectedRowKeys.length > 0 && `(${selectedRowKeys.length})`}
          </Button>
          <Button 
            type="primary"
            icon={<FileAddOutlined />}
            onClick={() => {
              if (selectedRowKeys.length === 0) {
                message.warning('请先选择客户');
                return;
              }
              setCreateTaskModalVisible(true);
            }}
          >
            创建任务 {selectedRowKeys.length > 0 && `(${selectedRowKeys.length})`}
          </Button>
          <Button 
            danger
            icon={<DeleteOutlined />}
            onClick={() => {
              if (selectedRowKeys.length === 0) {
                message.warning('请先选择客户');
                return;
              }
              handleBatchDelete();
            }}
          >
            批量删除 {selectedRowKeys.length > 0 && `(${selectedRowKeys.length})`}
          </Button>
          <Button 
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => setAddModalVisible(true)}
          >
            手动添加客户
          </Button>
          <Button 
            icon={<UploadOutlined />} 
            onClick={() => setImportGuideVisible(true)}
          >
            导入Excel/CSV
          </Button>
          <Button icon={<CameraOutlined />} onClick={() => message.info('OCR功能需要集成OCR服务')}>
            拍照识别
          </Button>
          <Button 
            icon={<ExportOutlined />} 
            onClick={handleExportCustomers}
          >
            导出数据
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
            placeholder="通话状态" 
            allowClear 
            style={{ width: 120 }}
            onChange={setFilterCallStatus}
            options={[
              { value: 'pending', label: '待拨打' },
              { value: 'connected', label: '已接听' },
              { value: 'voicemail', label: '语音信箱' },
              { value: 'unanswered', label: '响铃未接' },
              { value: 'rejected', label: '对方拒接' },
              { value: 'busy', label: '对方忙线' },
              { value: 'power_off', label: '关机/停机' },
              { value: 'no_answer', label: '无人接听' },
              { value: 'ivr', label: 'IVR语音' },
              { value: 'failed', label: '拨打失败' },
              { value: 'completed', label: '已完成' },
              { value: 'called', label: '其他已拨打' },              
              { value: 'other', label: '其他' }
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
          <Select
            placeholder="标签筛选"
            allowClear
            style={{ width: 160 }}
            value={filterTag || undefined}
            onChange={(value) => setFilterTag(value || '')}
            options={availableTags.map(tag => ({ value: tag, label: tag }))}
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
            Object.entries(groupedCustomers).length === 0 ? (
              <Alert
                message="暂无客户数据"
                description={
                  <div>
                    <p>系统中还没有客户数据，请先导入客户：</p>
                    <Button 
                      type="primary" 
                      icon={<UploadOutlined />}
                      onClick={() => setImportGuideVisible(true)}
                      style={{ marginTop: 8 }}
                    >
                      导入客户数据
                    </Button>
                  </div>
                }
                type="info"
                showIcon
                icon={<InfoCircleOutlined />}
                style={{ marginTop: 24 }}
              />
            ) : (
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
            )
          ) : (
            <Table 
              columns={columns} 
              dataSource={Object.values(groupedCustomers).flat()} 
              rowKey="id" 
              loading={loading}
              rowSelection={{
                selectedRowKeys,
                onChange: (keys) => setSelectedRowKeys(keys as number[]),
                preserveSelectedRowKeys: true
              }}
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
              locale={{
                emptyText: (
                  <div style={{ padding: '24px 0' }}>
                    <InfoCircleOutlined style={{ fontSize: 48, color: '#bfbfbf', marginBottom: 16 }} />
                    <p style={{ fontSize: 16, color: '#8c8c8c', marginBottom: 16 }}>暂无客户数据</p>
                    <Button 
                      type="primary" 
                      icon={<UploadOutlined />}
                      onClick={() => setImportGuideVisible(true)}
                    >
                      导入客户数据
                    </Button>
                  </div>
                )
              }}
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
              <Text strong>标签 (tag)</Text>
              <div style={{ color: '#666', fontSize: 12 }}>客户来源渠道标签，如：抖音渠道/百度投放</div>
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
              <li>若文件中包含“标签”列，且本页标签输入框留空，则会按文件中的标签值导入</li>
              <li>若本页标签输入框填写了非空内容，则本次导入客户统一使用该标签</li>
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
          setSelectedImportTag('');
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
          <Form.Item label="客户标签">
            <Select
              mode="tags"
              maxCount={1}
              placeholder="选择或输入标签"
              value={selectedImportTag ? [selectedImportTag] : []}
              onChange={(values) => setSelectedImportTag(normalizeTagValue(values))}
              options={availableTags.map(tag => ({ value: tag, label: tag }))}
            />
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

      {/* CSV 预览和列映射 Modal */}
      <Modal
        title="数据预览与列映射"
        open={previewModalVisible}
        onCancel={() => {
          setPreviewModalVisible(false);
          setPendingFile(null);
          setColumnMapping({});
          setSelectedImportTag('');
        }}
        footer={[
          <Button key="cancel" onClick={() => setPreviewModalVisible(false)}>
            取消
          </Button>,
          <Button 
            key="import" 
            type="primary" 
            loading={importLoading}
            onClick={handleConfirmImport}
            disabled={!columnMapping.name || !columnMapping.phone}
          >
            开始导入
          </Button>,
        ]}
        width={900}
      >
        <Spin spinning={previewLoading}>
          {previewData && (
            <>
              <Alert
                message={`检测到 ${previewData.total_rows} 条数据，共 ${previewData.columns.length} 列`}
                type="info"
                showIcon
                style={{ marginBottom: 16 }}
              />
              
              <Card title="列映射配置" size="small" style={{ marginBottom: 16 }}>
                {/* 复合字段提示 */}
                {previewData.composite_fields && Object.keys(previewData.composite_fields).length > 0 && (
                  <Alert
                    message="检测到复合字段"
                    description={
                      <div>
                        {Object.entries(previewData.composite_fields).map(([colName, composite]) => (
                          <div key={colName} style={{ marginBottom: 8 }}>
                            <Text strong>{colName}</Text>
                            <Text type="secondary"> 包含 {composite.partCount} 个子字段（分隔符: "{composite.separator}"）</Text>
                            <div style={{ marginTop: 4, paddingLeft: 16 }}>
                              {composite.subFields.map((sf, idx) => (
                                <Tag key={idx} color="blue">{sf.label}: {sf.samples.slice(0, 2).join(', ')}</Tag>
                              ))}
                            </div>
                          </div>
                        ))}
                      </div>
                    }
                    type="info"
                    showIcon
                    style={{ marginBottom: 12 }}
                  />
                )}
                
                <Row gutter={[16, 12]}>
                  {previewData.system_fields.map(field => {
                    const matched = previewData.columns.find(col => 
                      col.toLowerCase() === field.key.toLowerCase() ||
                      col.includes(field.label) ||
                      col.toLowerCase().includes(field.key.toLowerCase())
                    );
                    
                    // 构建可选项：普通列 + 复合字段的子字段
                    const options: { value: string; label: string; isComposite?: boolean }[] = [
                      ...previewData.columns.map(col => ({ value: col, label: col })),
                    ];
                    
                    // 添加复合字段的子字段
                    if (previewData.composite_fields) {
                      Object.entries(previewData.composite_fields).forEach(([colName, composite]) => {
                        composite.subFields.forEach(sf => {
                          options.push({
                            value: sf.key,
                            label: `${colName} → ${sf.label}`,
                            isComposite: true
                          });
                        });
                      });
                    }
                    
                    return (
                      <Col span={8} key={field.key}>
                        <div style={{ marginBottom: 4 }}>
                          <Text strong>{field.label}</Text>
                          {field.required && <Tag color="red" style={{ marginLeft: 4 }}>必填</Tag>}
                        </div>
                        <Select
                          style={{ width: '100%' }}
                          placeholder={`选择对应的列`}
                          value={columnMapping[field.key] || undefined}
                          onChange={(value) => {
                            setColumnMapping(prev => ({
                              ...prev,
                              [field.key]: value
                            }));
                          }}
                          allowClear
                          showSearch
                          optionFilterProp="label"
                        >
                          {options.map(opt => (
                            <Select.Option 
                              key={opt.value} 
                              value={opt.value}
                              label={opt.label}
                            >
                              {opt.isComposite ? (
                                <span>
                                  <Tag color="purple" style={{ marginRight: 4 }}>拆分</Tag>
                                  {opt.label}
                                </span>
                              ) : opt.label}
                            </Select.Option>
                          ))}
                        </Select>
                        {matched && !columnMapping[field.key] && (
                          <Text type="secondary" style={{ fontSize: 12 }}>
                            建议: {matched}
                          </Text>
                        )}
                      </Col>
                    );
                  })}
                </Row>
              </Card>

              <Card title="数据预览（前5行）" size="small">
                <Table
                  dataSource={previewData.preview}
                  columns={previewData.columns.map(col => ({
                    title: (
                      <span>
                        {col}
                        {columnMapping.name === col && <Tag color="blue" style={{ marginLeft: 4 }}>姓名</Tag>}
                        {columnMapping.phone === col && <Tag color="green" style={{ marginLeft: 4 }}>电话</Tag>}
                      </span>
                    ),
                    dataIndex: col,
                    key: col,
                    ellipsis: true,
                    width: 150,
                  }))}
                  pagination={false}
                  scroll={{ x: true }}
                  size="small"
                  rowKey={(_, index) => index?.toString() || ''}
                />
              </Card>

              <div style={{ marginTop: 16 }}>
                <Form layout="inline">
                  <Form.Item label="数据来源">
                    <Radio.Group 
                      value={importDataSource} 
                      onChange={e => setImportDataSource(e.target.value)}
                      buttonStyle="solid"
                    >
                      <Radio.Button value="real">真实数据</Radio.Button>
                      <Radio.Button value="mock">测试数据</Radio.Button>
                    </Radio.Group>
                  </Form.Item>
                  <Form.Item label="分配客服">
                    <Select
                      placeholder="可选"
                      allowClear
                      style={{ width: 200 }}
                      value={selectedAgent}
                      onChange={(value) => setSelectedAgent(value)}
                    >
                      {agents.map(agent => (
                        <Select.Option key={agent.id} value={agent.id}>
                          {agent.real_name}
                        </Select.Option>
                      ))}
                    </Select>
                  </Form.Item>
                  <Form.Item label="客户标签">
                    <Select
                      mode="tags"
                      maxCount={1}
                      style={{ width: 220 }}
                      placeholder="选择或输入标签"
                      value={selectedImportTag ? [selectedImportTag] : []}
                      onChange={(values) => setSelectedImportTag(normalizeTagValue(values))}
                      options={availableTags.map(tag => ({ value: tag, label: tag }))}
                    />
                  </Form.Item>
                </Form>
              </div>
            </>
          )}
        </Spin>
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

      {/* 批量创建任务模态框 */}
      <Modal
        title="批量创建任务"
        open={createTaskModalVisible}
        onOk={handleCreateTask}
        onCancel={() => {
          setCreateTaskModalVisible(false);
          createTaskForm.resetFields();
        }}
        width={600}
      >
        <Form form={createTaskForm} layout="vertical">
          <Alert
            message={`已选择 ${selectedRowKeys.length} 个客户，将添加到新创建的任务中`}
            type="info"
            showIcon
            style={{ marginBottom: 16 }}
          />
          <Form.Item
            name="title"
            label="任务标题"
            rules={[{ required: true, message: '请输入任务标题' }]}
          >
            <Input placeholder="请输入任务标题" />
          </Form.Item>
          <Form.Item
            name="description"
            label="任务描述"
          >
            <Input.TextArea rows={3} placeholder="请输入任务描述（可选）" />
          </Form.Item>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="priority"
                label="优先级"
                initialValue="normal"
              >
                <Select placeholder="选择优先级">
                  <Select.Option value="urgent">紧急</Select.Option>
                  <Select.Option value="high">高</Select.Option>
                  <Select.Option value="normal">普通</Select.Option>
                  <Select.Option value="low">低</Select.Option>
                </Select>
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="assigned_to"
                label="指派给"
                rules={[{ required: true, message: '请选择指派的客服' }]}
              >
                <Select placeholder="选择客服" allowClear>
                  {agents.map(agent => (
                    <Select.Option key={agent.id} value={agent.id}>
                      {agent.real_name}
                    </Select.Option>
                  ))}
                </Select>
              </Form.Item>
            </Col>
          </Row>
          <Form.Item
            name="due_date"
            label="截止日期"
          >
            <Input type="date" placeholder="选择截止日期（可选）" />
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
                <Text type="secondary">客户标签</Text>
                <div><Tag color={currentCustomer.tag === DEFAULT_CUSTOMER_TAG ? 'default' : 'geekblue'}>{currentCustomer.tag || DEFAULT_CUSTOMER_TAG}</Tag></div>
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
            await customerApi.updateCustomer(currentCustomer!.id, {
              ...values,
              tag: normalizeEditableTagValue(Array.isArray(values.tag) ? values.tag : [values.tag])
            });
            message.success('更新成功');
            setEditModalVisible(false);
            setCurrentCustomer(null);
            editForm.resetFields();
            fetchCustomers();
            fetchTags();
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
            <Col span={12}>
              <Form.Item
                name="tag"
                label="客户标签"
                initialValue={[DEFAULT_CUSTOMER_TAG]}
              >
                <Select
                  mode="tags"
                  maxCount={1}
                  placeholder="选择或输入标签"
                  options={availableTags.map(tag => ({ value: tag, label: tag }))}
                />
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

      {/* 手动添加客户弹窗 */}
      <Modal
        title="手动添加客户"
        open={addModalVisible}
        onOk={async () => {
          try {
            const values = await addForm.validateFields();
            await customerApi.createCustomer({
              ...values,
              tag: normalizeEditableTagValue(Array.isArray(values.tag) ? values.tag : [values.tag])
            });
            message.success('客户添加成功');
            setAddModalVisible(false);
            addForm.resetFields();
            fetchCustomers();
            fetchTags();
          } catch (error: any) {
            if (error.response?.data?.error) {
              message.error(error.response.data.error);
            } else if (error.errorFields) {
              // 表单验证错误，不需要额外提示
            } else {
              message.error('添加失败');
            }
          }
        }}
        onCancel={() => {
          setAddModalVisible(false);
          addForm.resetFields();
        }}
        width={600}
        okText="添加"
        cancelText="取消"
      >
        <Form form={addForm} layout="vertical">
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="name"
                label="客户姓名"
                rules={[{ required: true, message: '请输入客户姓名' }]}
              >
                <Input placeholder="请输入客户姓名" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="phone"
                label="联系电话"
                rules={[
                  { required: true, message: '请输入联系电话' },
                  { pattern: /^1[3-9]\d{9}$/, message: '请输入有效的手机号码' }
                ]}
              >
                <Input placeholder="请输入联系电话" />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="email"
                label="邮箱"
                rules={[{ type: 'email', message: '请输入有效的邮箱地址' }]}
              >
                <Input placeholder="请输入邮箱" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="company"
                label="公司名称"
              >
                <Input placeholder="请输入公司名称" />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item
            name="address"
            label="联系地址"
          >
            <Input.TextArea rows={2} placeholder="请输入联系地址" />
          </Form.Item>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="status"
                label="客户状态"
                initialValue="pending"
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
            <Col span={12}>
              <Form.Item
                name="tag"
                label="客户标签"
                initialValue={[DEFAULT_CUSTOMER_TAG]}
              >
                <Select
                  mode="tags"
                  maxCount={1}
                  placeholder="选择或输入标签"
                  options={availableTags.map(tag => ({ value: tag, label: tag }))}
                />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="assigned_to"
                label="分配客服"
              >
                <Select placeholder="选择客服（可选）" allowClear>
                  {agents.map(agent => (
                    <Select.Option key={agent.id} value={agent.id}>
                      {agent.real_name}
                    </Select.Option>
                  ))}
                </Select>
              </Form.Item>
            </Col>
          </Row>
          <Form.Item
            name="notes"
            label="备注"
          >
            <Input.TextArea rows={3} placeholder="请输入备注信息" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
