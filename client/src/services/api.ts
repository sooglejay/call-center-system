import axios, { AxiosError } from 'axios';

const api = axios.create({
  baseURL: '/api',
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
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

// 认证API
export const authApi = {
  login: (username: string, password: string) => api.post('/auth/login', { username, password }),
  getMe: () => api.get('/auth/me'),
  updateProfile: (data: Partial<User>) => api.put('/auth/profile', data),
  changePassword: (old_password: string, new_password: string) => 
    api.put('/auth/password', { old_password, new_password })
};

// 用户API
export const userApi = {
  getUsers: (params?: any) => api.get('/users', { params }),
  createUser: (data: any) => api.post('/users', data),
  updateUser: (id: number, data: any) => api.put(`/users/${id}`, data),
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
  updateCustomer: (id: number, data: any) => api.put(`/customers/${id}`, data),
  deleteCustomer: (id: number) => api.delete(`/customers/${id}`),
  importCustomers: (customers: any[], assigned_to?: number) => api.post('/customers/import', { customers, assigned_to }),
  batchAssign: (customer_ids: number[], assigned_to: number) => api.post('/customers/batch-assign', { customer_ids, assigned_to })
};

// 通话API
export const callApi = {
  getCalls: (params?: any) => api.get('/calls', { params }),
  createCall: (data: any) => api.post('/calls', data),
  updateCall: (id: number, data: any) => api.put(`/calls/${id}`, data),
  updateCallNotes: (id: number, call_notes: string, call_result?: string) => 
    api.put(`/calls/${id}/notes`, { call_notes, call_result }),
  getNextCall: () => api.get('/calls/next/dial')
};

// 任务API
export const taskApi = {
  getTasks: (params?: any) => api.get('/tasks', { params }),
  createTask: (data: any) => api.post('/tasks', data),
  updateTask: (id: number, data: any) => api.put(`/tasks/${id}`, data),
  deleteTask: (id: number) => api.delete(`/tasks/${id}`),
  getMyTasks: () => api.get('/tasks/my/list')
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
  endCall: (callSid: string) => api.post(`/twilio/call/${callSid}/end`)
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

import type { User, Customer, CallRecord, Task, AgentConfig, SystemConfig, Stats, DashboardStats, AgentRanking } from '../types';
export type { User, Customer, CallRecord, Task, AgentConfig, SystemConfig, Stats, DashboardStats, AgentRanking };

export default api;
