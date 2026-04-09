export interface User {
  id: number;
  username: string;
  password: string;
  real_name: string;
  role: 'admin' | 'agent';
  phone?: string;
  email?: string;
  avatar_url?: string;
  status: 'active' | 'inactive';
  created_at: Date;
  updated_at: Date;
}

export interface Customer {
  id: number;
  phone: string;
  name?: string;
  tag?: string;
  remark?: string;
  source?: string;
  imported_by?: number;
  imported_at?: Date;
  created_at: Date;
  updated_at: Date;
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
  started_at?: Date;
  connected_at?: Date;
  ended_at?: Date;
  created_at: Date;
  updated_at: Date;
}

export interface Task {
  id: number;
  name: string;
  agent_id: number;
  customer_ids: number[];
  task_type: 'daily' | 'weekly';
  status: 'active' | 'completed' | 'cancelled';
  start_date: string;
  end_date: string;
  created_by: number;
  created_at: Date;
  updated_at: Date;
}

export interface AgentConfig {
  id: number;
  agent_id: number;
  dial_strategy: 'newest' | 'oldest';
  dial_delay: number;
  remove_duplicates: boolean;
  updated_at: Date;
}

export interface SystemConfig {
  id: number;
  config_key: string;
  config_value?: string;
  description?: string;
  updated_by?: number;
  updated_at: Date;
}

export interface CallStats {
  agent_id: number;
  agent_name: string;
  total_calls: number;
  connected_calls: number;
  total_duration: number;
  completion_rate: number;
}
