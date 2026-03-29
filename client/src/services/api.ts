import axios, { AxiosError } from 'axios';

// 服务器地址存储键
const CURRENT_SERVER_KEY = 'current_server';

// 获取 API 基础路径
// 子路径部署时，API 路径应该是相对路径，让浏览器自动拼接
const getBaseURL = () => {
  // 首先检查是否有手动设置的服务器地址
  const customServer = localStorage.getItem(CURRENT_SERVER_KEY);
  if (customServer) {
    // 如果设置了自定义服务器地址，使用它
    return `${customServer}/api`;
  }
  
  // 否则使用默认逻辑
  const basePath = import.meta.env.VITE_BASE_PATH || '';
  // 如果是根路径（/ 或空），直接返回 /api
  // 如果是子路径（如 /callcenter），拼接为 /callcenter/api
  if (!basePath || basePath === '/') {
    return '/api';
  }
  // 确保子路径不以 / 结尾，然后拼接 /api
  const normalizedPath = basePath.endsWith('/') ? basePath.slice(0, -1) : basePath;
  return `${normalizedPath}/api`;
};

const api = axios.create({
  baseURL: getBaseURL(),
  headers: {
    'Content-Type': 'application/json'
  }
});

// 请求拦截器
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// 响应拦截器
api.interceptors.response.use(
  (response) => response,
  (error: AxiosError) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token');
      localStorage.removeItem('user');
      // 支持子路径部署
      const basePath = import.meta.env.VITE_BASE_PATH || '';
      window.location.href = `${basePath}/login`;
    }
    return Promise.reject(error);
  }
);

// 认证API
export const authApi = {
  login: (username: string, password: string) => api.post('/auth/login', { username, password }),
  register: (data: { username: string; password: string; real_name?: string; phone?: string; email?: string }) => 
    api.post('/auth/register', data),
  getMe: () => api.get('/auth/me'),
  updateProfile: (data: Partial<User>) => api.put('/auth/profile', data),
  changePassword: (old_password: string, new_password: string) => 
    api.put('/auth/password', { old_password, new_password }),
  getPublicConfig: () => api.get('/auth/public-config')
};

// 用户API
export const userApi = {
  getUsers: (params?: any) => api.get('/users', { params }),
  createUser: (data: any) => api.post('/users', data),
  updateUser: (id: number, data: any) => api.put(`/users/${id}`, data),
  updateDataAccess: (id: number, data_access_type: string) => api.put(`/users/${id}/data-access`, { data_access_type }),
  deleteUser: (id: number) => api.delete(`/users/${id}`),
  resetPassword: (id: number, new_password: string) => api.post(`/users/${id}/reset-password`, { new_password }),
  getAgents: () => api.get('/users/agents/list')
};

// 客户API
export const customerApi = {
  getCustomers: (params?: any) => api.get('/customers', { params }),
  getCustomersByNameLetter: (letters?: string, unassigned_only?: boolean) => api.get('/customers/by-name-letter', { params: { letters, unassigned_only } }),
  getNameLetterStats: (unassigned_only?: boolean) => api.get('/customers/name-letter-stats', { params: { unassigned_only } }),
  getAgentCustomers: (params?: any) => api.get('/customers/agent/list', { params }),
  getCustomer: (id: number) => api.get(`/customers/${id}`),
  createCustomer: (data: any) => api.post('/customers', data),
  updateCustomer: (id: number, data: any) => api.put(`/customers/${id}`, data),
  deleteCustomer: (id: number) => api.delete(`/customers/${id}`),
  importCustomers: (customers: any[], assigned_to?: number, data_source?: string) => 
    api.post('/customers/import', { customers, assigned_to, data_source }),
  batchAssign: (customer_ids: number[], assigned_to: number) => api.post('/customers/batch-assign', { customer_ids, assigned_to })
};

// 通话API
export const callApi = {
  getCalls: (params?: any) => api.get('/calls', { params }),
  createCall: (data: any) => api.post('/calls', data),
  updateCall: (id: number, data: any) => api.put(`/calls/${id}`, data),
  updateCallNotes: (id: number, call_notes: string) => 
    api.put(`/calls/${id}/notes`, { call_notes }),
  getNextCall: () => api.get('/calls/next/dial')
};

// 任务API
export const taskApi = {
  getTasks: (params?: any) => api.get('/tasks', { params }),
  getTaskById: (id: number) => api.get(`/tasks/${id}`),
  createTask: (data: any) => api.post('/tasks', data),
  updateTask: (id: number, data: any) => api.put(`/tasks/${id}`, data),
  deleteTask: (id: number) => api.delete(`/tasks/${id}`),
  getMyTasks: () => api.get('/tasks/my/list'),
  addCustomers: (taskId: number, customer_ids: number[]) => 
    api.post(`/tasks/${taskId}/customers`, { customer_ids }),
  removeCustomer: (taskId: number, customerId: number) => 
    api.delete(`/tasks/${taskId}/customers/${customerId}`),
  updateCustomerStatus: (taskId: number, customerId: number, data: any) => 
    api.put(`/tasks/${taskId}/customers/${customerId}/status`, data)
};

// 统计API
export const statsApi = {
  getStats: (params?: any) => api.get('/stats', { params }),
  getMyStats: (params?: any) => api.get('/stats/my', { params }),
  getDashboardStats: () => api.get('/stats/dashboard')
};

// Twilio API
export const twilioApi = {
  makeCall: (data: any) => api.post('/twilio/call', data),
  getCallStatus: (callSid: string) => api.get(`/twilio/call/${callSid}/status`),
  endCall: (callSid: string) => api.post(`/twilio/call/${callSid}/end`),
  // 测试功能
  getConfig: () => api.get('/twilio/config'),
  testConnection: () => api.get('/twilio/test/connection'),
  testSms: (to: string, message?: string) => api.post('/twilio/test/sms', { to, message }),
  testCall: (to: string, message?: string) => api.post('/twilio/test/call', { to, message }),
  getPhoneNumbers: () => api.get('/twilio/phone-numbers'),
  getSmsRecords: (limit?: number) => api.get('/twilio/sms/records', { params: { limit } }),
  checkSmsStatus: (messageSid: string) => api.get(`/twilio/sms/${messageSid}/status`)
};

// 配置API
export const configApi = {
  getConfigs: () => api.get('/config/system'),
  updateConfig: (config_key: string, config_value: string) => api.put('/config/system', { config_key, config_value }),
  getAgentConfig: () => api.get('/config/agent'),
  updateAgentConfig: (data: Partial<AgentConfig>) => api.put('/config/agent', data),
  uploadFile: (formData: FormData) => api.post('/config/upload/file', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  }),
  uploadImage: (formData: FormData) => api.post('/config/upload/image', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
};

// 通信记录API
export const communicationApi = {
  getRecords: (params?: any) => api.get('/communication/records', { params }),
  getStats: () => api.get('/communication/stats')
};

// 数据导入API
export const dataImportApi = {
  getStats: () => api.get('/data-import/stats'),
  // 预览 CSV 文件
  previewCsv: (formData: FormData) => api.post('/data-import/preview', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  }),
  // 使用列映射导入
  importWithMapping: (formData: FormData) => api.post('/data-import/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  }),
  // 快速上传（自动匹配）
  uploadCsv: (formData: FormData) => api.post('/data-import/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  }),
  initMockData: () => api.post('/data-import/init-mock'),
  clearMockData: () => api.delete('/data-import/mock'),
  clearRealData: () => api.delete('/data-import/real')
};

import type { User, Customer, CallRecord, Task, AgentConfig, SystemConfig, Stats, DashboardStats, AgentRanking } from '../types';
export type { User, Customer, CallRecord, Task, AgentConfig, SystemConfig, Stats, DashboardStats, AgentRanking };

export default api;
