export interface User {
  id: number;
  username: string;
  real_name: string;
  role: 'admin' | 'agent';
  phone?: string;
  email?: string;
  avatar_url?: string;
  status: 'active' | 'inactive';
  created_at: string;
}

export interface Customer {
  id: number;
  phone: string;
  name?: string;
  email?: string;
  company?: string;
  address?: string;
  remark?: string;
  source?: string;
  status?: string;
  priority?: number;
  imported_by?: number;
  imported_by_name?: string;
  imported_at?: string;
  created_at: string;
  updated_at?: string;
  is_duplicate?: boolean;
  call_status?: string;
  is_connected?: boolean;
  call_duration?: number;
  recording_url?: string;
  call_notes?: string;
  call_time?: string;
  task_id?: number;
  assigned_to?: number | null;
  assigned_to_name?: string;
}

export interface CallRecord {
  id: number;
  customer_id: number;
  agent_id: number;
  task_id?: number;
  twilio_call_sid?: string;
  phone: string;
  status: 'pending' | 'calling' | 'connected' | 'completed' | 'failed' | 'no_answer' | 'busy';
  is_connected: boolean;
  call_duration: number;
  recording_url?: string;
  recording_duration: number;
  call_notes?: string;
  call_result?: string;
  started_at?: string;
  connected_at?: string;
  ended_at?: string;
  created_at: string;
  customer_name?: string;
  agent_name?: string;
}

export interface Task {
  id: number;
  name: string;
  title?: string;
  description?: string;
  agent_id: number;
  agent_name?: string;
  customer_ids: number[];
  customer_count?: number;
  completed_count?: number;
  task_type: 'daily' | 'weekly';
  status: 'active' | 'completed' | 'cancelled' | 'pending' | 'in_progress';
  priority?: 'low' | 'normal' | 'high' | 'urgent';
  due_date?: string;
  start_date: string;
  end_date: string;
  created_by?: number;
  created_by_name?: string;
  created_at: string;
  updated_at: string;
}

export interface AgentConfig {
  id: number;
  agent_id: number;
  dial_strategy: 'newest' | 'oldest';
  dial_delay: number;
  remove_duplicates: boolean;
  updated_at: string;
}

export interface SystemConfig {
  id: number;
  config_key: string;
  config_value?: string;
  description?: string;
  updated_at: string;
}

export interface Stats {
  total_calls: number;
  connected_calls: number;
  failed_calls: number;
  total_duration: number;
  avg_duration: number;
  connection_rate: number;
  ranking?: number;
}

export interface DashboardStats {
  total_customers: number;
  total_calls: number;
  connection_rate: number;
  active_agents: number;
  trend: Array<{
    date: string;
    total_calls: number;
    connected_calls: number;
  }>;
}

export interface AgentRanking {
  agent_id: number;
  agent_name: string;
  total_calls: number;
  connected_calls: number;
  total_duration: number;
  connection_rate: number;
}
