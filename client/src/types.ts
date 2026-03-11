// 用户类型
export interface User {
  id: number;
  username: string;
  role: 'admin' | 'agent';
  real_name?: string;
  phone?: string;
  email?: string;
  department?: string;
  position?: string;
  avatar_url?: string;
  status: 'active' | 'inactive';
  last_login_at?: string;
  created_at: string;
}

// 客户类型
export interface Customer {
  id: number;
  name: string;
  phone: string;
  email?: string;
  company?: string;
  address?: string;
  notes?: string;
  status: 'pending' | 'contacted' | 'interested' | 'not_interested' | 'converted';
  priority: number;
  assigned_to?: number;
  assigned_agent_name?: string;
  created_at: string;
  updated_at: string;
}

// 通话记录类型
export interface CallRecord {
  id: number;
  customer_id: number;
  agent_id: number;
  customer_phone: string;
  customer_name?: string;
  twilio_call_sid?: string;
  status: 'pending' | 'calling' | 'in-progress' | 'completed';
  call_result?: 'answered' | 'no-answer' | 'busy' | 'failed' | 'voicemail' | 'canceled';
  call_notes?: string;
  recording_url?: string;
  recording_duration?: number;
  started_at?: string;
  ended_at?: string;
  created_at: string;
  updated_at: string;
}

// 任务类型
export interface Task {
  id: number;
  title: string;
  description?: string;
  assigned_to?: number;
  assigned_agent_name?: string;
  customer_id?: number;
  customer_name?: string;
  priority: 'low' | 'normal' | 'high' | 'urgent';
  status: 'pending' | 'in_progress' | 'completed' | 'cancelled';
  due_date?: string;
  completed_at?: string;
  created_by: number;
  created_at: string;
  updated_at: string;
}

// 客服配置类型
export interface AgentConfig {
  id: number;
  agent_id: number;
  auto_dial_enabled: boolean;
  dial_interval: number;
  dial_start_time: string;
  dial_end_time: string;
  sip_endpoint?: string;
  sip_username?: string;
  sip_password?: string;
  created_at: string;
  updated_at: string;
}

// 系统配置类型
export interface SystemConfig {
  id: number;
  config_key: string;
  config_value: string;
  description?: string;
  created_at: string;
  updated_at: string;
}

// 统计类型
export interface Stats {
  totalCalls: number;
  completedCalls: number;
  missedCalls: number;
  conversionRate: number;
  avgCallDuration: number;
  totalCustomers: number;
  newCustomers: number;
}

// 仪表盘统计类型
export interface DashboardStats {
  today: {
    calls: number;
    completed: number;
    duration: number;
  };
  week: {
    calls: number;
    completed: number;
    duration: number;
  };
  month: {
    calls: number;
    completed: number;
    duration: number;
  };
  agents: AgentRanking[];
}

// 客服排名类型
export interface AgentRanking {
  agent_id: number;
  agent_name: string;
  total_calls: number;
  completed_calls: number;
  total_duration: number;
  conversion_rate: number;
}
