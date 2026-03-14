import { Pool } from 'pg';
import dotenv from 'dotenv';

dotenv.config();

// 检查是否使用 PostgreSQL
const usePostgres = !!(process.env.DATABASE_URL || (process.env.DB_HOST && process.env.DB_HOST !== 'localhost'));

// 生成日期辅助函数
const daysAgo = (days: number) => {
  const d = new Date();
  d.setDate(d.getDate() - days);
  return d.toISOString();
};

// ============ 测试数据生成 ============
const generateCustomers = () => [
  { id: 1, name: '张三', phone: '13800138001', email: 'zhangsan@example.com', company: 'ABC科技有限公司', address: '北京市朝阳区', notes: '有意向购买', status: 'pending', priority: 1, assigned_to: 2, imported_by: 1, created_at: daysAgo(10), updated_at: daysAgo(5) },
  { id: 2, name: '李四', phone: '13800138002', email: 'lisi@example.com', company: 'XYZ贸易有限公司', address: '上海市浦东新区', notes: '需要报价', status: 'contacted', priority: 2, assigned_to: 2, imported_by: 1, created_at: daysAgo(8), updated_at: daysAgo(3) },
  { id: 3, name: '王五', phone: '13800138003', email: 'wangwu@example.com', company: '创新科技', address: '深圳市南山区', notes: '已经成交', status: 'converted', priority: 1, assigned_to: 2, imported_by: 1, created_at: daysAgo(15), updated_at: daysAgo(2) },
  { id: 4, name: '赵六', phone: '13800138004', email: 'zhaoliu@example.com', company: '华盛集团', address: '广州市天河区', notes: '暂时不需要', status: 'not_interested', priority: 3, assigned_to: 2, imported_by: 1, created_at: daysAgo(12), updated_at: daysAgo(4) },
  { id: 5, name: '钱七', phone: '13800138005', email: 'qianqi@example.com', company: '鼎盛实业', address: '杭州市西湖区', notes: '高价值客户', status: 'interested', priority: 1, assigned_to: 2, imported_by: 1, created_at: daysAgo(7), updated_at: daysAgo(1) },
  { id: 6, name: '孙八', phone: '13800138006', email: 'sunba@example.com', company: '远航物流', address: '成都市高新区', notes: '需跟进', status: 'pending', priority: 2, assigned_to: 2, imported_by: 1, created_at: daysAgo(9), updated_at: daysAgo(6) },
  { id: 7, name: '周九', phone: '13800138007', email: 'zhoujiu@example.com', company: '星辰传媒', address: '武汉市江汉区', notes: '等待回复', status: 'contacted', priority: 3, assigned_to: 2, imported_by: 1, created_at: daysAgo(11), updated_at: daysAgo(4) },
  { id: 8, name: '吴十', phone: '13800138008', email: 'wushi@example.com', company: '天宇建设', address: '南京市鼓楼区', notes: '已签约', status: 'converted', priority: 1, assigned_to: 2, imported_by: 1, created_at: daysAgo(20), updated_at: daysAgo(3) },
  { id: 9, name: '郑一', phone: '13800138009', email: 'zhengyi@example.com', company: '云端网络', address: '西安市雁塔区', notes: '产品咨询', status: 'pending', priority: 2, assigned_to: 2, imported_by: 1, created_at: daysAgo(6), updated_at: daysAgo(2) },
  { id: 10, name: '陈二', phone: '13800138010', email: 'chener@example.com', company: '明达咨询', address: '重庆市渝中区', notes: '价格敏感', status: 'interested', priority: 2, assigned_to: 2, imported_by: 1, created_at: daysAgo(5), updated_at: daysAgo(1) },
  { id: 11, name: '刘明', phone: '13900139001', email: 'liuming@example.com', company: '东方电子', address: '苏州市工业园区', notes: '大批量采购', status: 'pending', priority: 1, assigned_to: null, imported_by: 1, created_at: daysAgo(4), updated_at: daysAgo(1) },
  { id: 12, name: '黄强', phone: '13900139002', email: 'huangqiang@example.com', company: '国信证券', address: '天津市和平区', notes: '需演示', status: 'contacted', priority: 1, assigned_to: null, imported_by: 1, created_at: daysAgo(3), updated_at: daysAgo(1) },
  { id: 13, name: '林娜', phone: '13900139003', email: 'linna@example.com', company: '美丽化妆品', address: '青岛市市南区', notes: '正在比较', status: 'interested', priority: 2, assigned_to: null, imported_by: 1, created_at: daysAgo(6), updated_at: daysAgo(2) },
  { id: 14, name: '何伟', phone: '13900139004', email: 'hewei@example.com', company: '鑫源制造', address: '宁波市鄞州区', notes: '预算有限', status: 'pending', priority: 3, assigned_to: null, imported_by: 1, created_at: daysAgo(8), updated_at: daysAgo(3) },
  { id: 15, name: '高敏', phone: '13900139005', email: 'gaomin@example.com', company: '智慧教育', address: '厦门市思明区', notes: '决策中', status: 'contacted', priority: 2, assigned_to: null, imported_by: 1, created_at: daysAgo(7), updated_at: daysAgo(2) },
];

const generateCalls = () => [
  { id: 1, customer_id: 1, agent_id: 2, customer_phone: '13800138001', customer_name: '张三', twilio_call_sid: 'CA1234567890', status: 'completed', call_result: 'answered', call_notes: '客户有意向，需要发送详细报价', recording_url: '', recording_duration: 120, started_at: daysAgo(5), ended_at: daysAgo(5), created_at: daysAgo(5), updated_at: daysAgo(5), is_connected: true },
  { id: 2, customer_id: 2, agent_id: 2, customer_phone: '13800138002', customer_name: '李四', twilio_call_sid: 'CA1234567891', status: 'completed', call_result: 'no-answer', call_notes: '无人接听，已发送短信', recording_url: '', recording_duration: 0, started_at: daysAgo(4), ended_at: daysAgo(4), created_at: daysAgo(4), updated_at: daysAgo(4), is_connected: false },
  { id: 3, customer_id: 3, agent_id: 2, customer_phone: '13800138003', customer_name: '王五', twilio_call_sid: 'CA1234567892', status: 'completed', call_result: 'answered', call_notes: '成功签约，客户很满意', recording_url: 'https://api.twilio.com/recordings/RE123', recording_duration: 300, started_at: daysAgo(3), ended_at: daysAgo(3), created_at: daysAgo(3), updated_at: daysAgo(3), is_connected: true },
  { id: 4, customer_id: 4, agent_id: 2, customer_phone: '13800138004', customer_name: '赵六', twilio_call_sid: 'CA1234567893', status: 'completed', call_result: 'busy', call_notes: '客户正在忙，约下午再打', recording_url: '', recording_duration: 0, started_at: daysAgo(2), ended_at: daysAgo(2), created_at: daysAgo(2), updated_at: daysAgo(2), is_connected: false },
  { id: 5, customer_id: 5, agent_id: 2, customer_phone: '13800138005', customer_name: '钱七', twilio_call_sid: 'CA1234567894', status: 'completed', call_result: 'answered', call_notes: '详细沟通了产品功能，客户很感兴趣', recording_url: 'https://api.twilio.com/recordings/RE124', recording_duration: 480, started_at: daysAgo(1), ended_at: daysAgo(1), created_at: daysAgo(1), updated_at: daysAgo(1), is_connected: true },
  { id: 6, customer_id: 6, agent_id: 2, customer_phone: '13800138006', customer_name: '孙八', twilio_call_sid: 'CA1234567895', status: 'completed', call_result: 'voicemail', call_notes: '已留言，等待回复', recording_url: '', recording_duration: 0, started_at: daysAgo(6), ended_at: daysAgo(6), created_at: daysAgo(6), updated_at: daysAgo(6), is_connected: false },
  { id: 7, customer_id: 7, agent_id: 2, customer_phone: '13800138007', customer_name: '周九', twilio_call_sid: 'CA1234567896', status: 'completed', call_result: 'answered', call_notes: '客户需要更多时间考虑', recording_url: '', recording_duration: 180, started_at: daysAgo(7), ended_at: daysAgo(7), created_at: daysAgo(7), updated_at: daysAgo(7), is_connected: true },
  { id: 8, customer_id: 1, agent_id: 2, customer_phone: '13800138001', customer_name: '张三', twilio_call_sid: 'CA1234567897', status: 'completed', call_result: 'answered', call_notes: '跟进报价，客户基本确定', recording_url: '', recording_duration: 240, started_at: daysAgo(2), ended_at: daysAgo(2), created_at: daysAgo(2), updated_at: daysAgo(2), is_connected: true },
];

const generateTasks = () => [
  { id: 1, title: '跟进张三报价', description: '发送正式报价单，跟进签约进度', assigned_to: 2, customer_id: 1, priority: 'high', status: 'in_progress', due_date: daysAgo(-3).split('T')[0], completed_at: null, created_by: 1, created_at: daysAgo(5), updated_at: daysAgo(2) },
  { id: 2, title: '联系李四发送资料', description: '准备产品手册和案例，邮件发送', assigned_to: 2, customer_id: 2, priority: 'normal', status: 'pending', due_date: daysAgo(-2).split('T')[0], completed_at: null, created_by: 1, created_at: daysAgo(4), updated_at: daysAgo(4) },
  { id: 3, title: '王五合同签署', description: '准备合同文档，约时间签署', assigned_to: 2, customer_id: 3, priority: 'urgent', status: 'completed', due_date: daysAgo(-1).split('T')[0], completed_at: daysAgo(1), created_by: 1, created_at: daysAgo(3), updated_at: daysAgo(1) },
  { id: 4, title: '钱七产品演示', description: '安排线上产品演示会议', assigned_to: 2, customer_id: 5, priority: 'high', status: 'in_progress', due_date: daysAgo(-5).split('T')[0], completed_at: null, created_by: 1, created_at: daysAgo(7), updated_at: daysAgo(2) },
  { id: 5, title: '整理客户反馈', description: '收集本周客户反馈，汇总报告', assigned_to: 2, customer_id: null, priority: 'normal', status: 'pending', due_date: daysAgo(-7).split('T')[0], completed_at: null, created_by: 1, created_at: daysAgo(2), updated_at: daysAgo(2) },
];

const generateVoicemailRecords = () => [
  { id: 1, call_id: 6, customer_id: 6, agent_id: 2, customer_phone: '13800138006', customer_name: '孙八', voicemail_url: 'https://api.twilio.com/recordings/RE201', duration: 45, created_at: daysAgo(6) },
  { id: 2, call_id: null, customer_id: 9, agent_id: 2, customer_phone: '13800138009', customer_name: '郑一', voicemail_url: 'https://api.twilio.com/recordings/RE202', duration: 30, created_at: daysAgo(3) },
];

const generateSmsRecords = () => [
  { id: 1, call_id: 2, customer_id: 2, agent_id: 2, customer_phone: '13800138002', customer_name: '李四', sms_content: '您好，我是客服小王，刚刚给您打了电话。如有需要请联系我：13800138001', twilio_message_sid: 'SM123456', sms_status: 'sent', created_at: daysAgo(4) },
  { id: 2, call_id: 4, customer_id: 4, agent_id: 2, customer_phone: '13800138004', customer_name: '赵六', sms_content: '您好，我是客服小王，刚刚给您打了电话。如有需要请联系我：13800138001', twilio_message_sid: 'SM123457', sms_status: 'sent', created_at: daysAgo(2) },
];

const generateUnansweredRecords = () => [
  { id: 1, call_id: 2, customer_id: 2, agent_id: 2, customer_phone: '13800138002', customer_name: '李四', reason: 'no-answer', created_at: daysAgo(4) },
  { id: 2, call_id: 4, customer_id: 4, agent_id: 2, customer_phone: '13800138004', customer_name: '赵六', reason: 'busy', created_at: daysAgo(2) },
  { id: 3, call_id: null, customer_id: 14, agent_id: 2, customer_phone: '13900139004', customer_name: '何伟', reason: 'no-answer', created_at: daysAgo(1) },
];

// ============ 内存数据存储 ============
const memoryData: any = {
  users: [
    {
      id: 1,
      username: 'admin',
      password: '$2a$10$EwMFYcfu99eV3ZFN6EzUg.PTqD3aOzu.4x7vHBC/Lxf99sswJtR4W',
      role: 'admin',
      real_name: '系统管理员',
      phone: '13800138000',
      email: 'admin@example.com',
      department: '管理部',
      position: '管理员',
      avatar_url: '',
      status: 'active',
      created_at: daysAgo(30),
      updated_at: daysAgo(1)
    },
    {
      id: 2,
      username: 'agent',
      password: '$2a$10$EwMFYcfu99eV3ZFN6EzUg.PTqD3aOzu.4x7vHBC/Lxf99sswJtR4W',
      role: 'agent',
      real_name: '张客服',
      phone: '13800138001',
      email: 'agent@example.com',
      department: '客服部',
      position: '客服专员',
      avatar_url: '',
      status: 'active',
      created_at: daysAgo(30),
      updated_at: daysAgo(1)
    }
  ],
  customers: generateCustomers(),
  calls: generateCalls(),
  tasks: generateTasks(),
  voicemail_records: generateVoicemailRecords(),
  sms_records: generateSmsRecords(),
  unanswered_records: generateUnansweredRecords(),
  system_configs: {
    twilio_account_sid: { id: 1, config_key: 'twilio_account_sid', config_value: 'AC1234567890abcdef', description: 'Twilio Account SID' },
    twilio_auth_token: { id: 2, config_key: 'twilio_auth_token', config_value: 'auth_token_secret', description: 'Twilio Auth Token' },
    twilio_phone_number: { id: 3, config_key: 'twilio_phone_number', config_value: '+1234567890', description: 'Twilio发信号码' },
    twilio_callback_url: { id: 4, config_key: 'twilio_callback_url', config_value: 'https://example.com/api/twilio', description: 'Twilio Webhook回调URL' },
    sms_enabled: { id: 5, config_key: 'sms_enabled', config_value: 'true', description: '是否启用短信功能' },
    sms_template_unanswered: { id: 6, config_key: 'sms_template_unanswered', config_value: '您好，我是{agentName}，刚刚给您打了电话。如有需要请联系我：{agentPhone}', description: '未接通短信模板' },
    voicemail_enabled: { id: 7, config_key: 'voicemail_enabled', config_value: 'true', description: '是否启用语音信箱' },
    voicemail_greeting: { id: 8, config_key: 'voicemail_greeting', config_value: '您好，我现在无法接听您的电话，请在听到提示音后留言。', description: '语音信箱问候语' }
  },
  agent_configs: {
    2: { id: 1, agent_id: 2, auto_dial_enabled: true, dial_interval: 30, dial_start_time: '09:00:00', dial_end_time: '18:00:00', sip_endpoint: '', sip_username: '', sip_password: '', created_at: daysAgo(30), updated_at: daysAgo(1) }
  },
};

function getNextId(table: string): number {
  const items = memoryData[table];
  if (!Array.isArray(items)) return 1;
  return items.length > 0 ? Math.max(...items.map((i: any) => i.id || 0)) + 1 : 1;
}

// ============ 内存数据库类 ============
class MemoryDB {
  async query(text: string, params?: any[]): Promise<{ rows: any[]; rowCount: number }> {
    const sql = text.toLowerCase().trim();
    const originalSql = text;
    
    if (sql.startsWith('select')) {
      return this.select(sql, originalSql, params);
    }
    if (sql.startsWith('insert')) {
      return this.insert(sql, originalSql, params);
    }
    if (sql.startsWith('update')) {
      return this.update(sql, originalSql, params);
    }
    if (sql.startsWith('delete')) {
      return this.delete(sql, originalSql, params);
    }
    
    return { rows: [], rowCount: 0 };
  }
  
  private select(sql: string, originalSql: string, params?: any[]): { rows: any[]; rowCount: number } {
    // COUNT 查询
    if (sql.includes('count(*)')) {
      return this.handleCount(sql, originalSql, params);
    }
    
    // 接通率查询
    if (sql.includes('case') && sql.includes('is_connected') && sql.includes('rate')) {
      const totalCalls = memoryData.calls.length;
      const connectedCalls = memoryData.calls.filter((c: any) => c.is_connected).length;
      const rate = totalCalls > 0 ? (connectedCalls * 100.0 / totalCalls).toFixed(2) : '0.00';
      return { rows: [{ rate }], rowCount: 1 };
    }
    
    // DISTINCT agent_id 查询
    if (sql.includes('distinct') && sql.includes('agent_id')) {
      const agentIds = [...new Set(memoryData.calls.map((c: any) => c.agent_id))];
      return { rows: [{ count: agentIds.length.toString() }], rowCount: 1 };
    }
    
    // GROUP BY date 趋势查询
    if (sql.includes('group by') && sql.includes('date(')) {
      const trend = [];
      for (let i = 6; i >= 0; i--) {
        const date = new Date();
        date.setDate(date.getDate() - i);
        trend.push({
          date: date.toISOString().split('T')[0],
          total_calls: Math.floor(Math.random() * 10).toString(),
          connected_calls: Math.floor(Math.random() * 8).toString()
        });
      }
      return { rows: trend, rowCount: trend.length };
    }
    
    // 通用 SELECT 查询
    const fromMatch = originalSql.match(/FROM\s+(\w+)/i);
    if (fromMatch) {
      const table = fromMatch[1].toLowerCase();
      const tableMap: Record<string, string> = {
        'call_records': 'calls',
        'users': 'users',
        'customers': 'customers',
        'calls': 'calls',
        'tasks': 'tasks',
        'voicemail_records': 'voicemail_records',
        'sms_records': 'sms_records',
        'unanswered_records': 'unanswered_records'
      };
      
      const actualTable = tableMap[table] || table;
      if (actualTable && memoryData[actualTable]) {
        let data = [...memoryData[actualTable]];
        
        // WHERE 过滤
        if (sql.includes('where') && params) {
          // 处理 id = $1
          const idMatch = originalSql.match(/id\s*=\s*\$(\d+)/i);
          if (idMatch) {
            const idx = parseInt(idMatch[1]) - 1;
            const id = parseInt(params[idx]);
            data = data.filter((item: any) => item.id === id);
          }
          
          // 处理 assigned_to = $1
          if (sql.includes('assigned_to')) {
            const match = originalSql.match(/assigned_to\s*=\s*\$(\d+)/i);
            if (match) {
              const idx = parseInt(match[1]) - 1;
              const val = parseInt(params[idx]);
              data = data.filter((item: any) => item.assigned_to === val);
            }
          }
          
          // 处理 username = $1 AND status = $2
          if (sql.includes('username') && sql.includes('status')) {
            data = data.filter((u: any) => 
              u.username === params[0] && u.status === params[1]
            );
          }
          
          // 处理 config_key = $1
          if (sql.includes('config_key')) {
            const config = memoryData.system_configs[params?.[0] as string];
            return { rows: config ? [config] : [], rowCount: config ? 1 : 0 };
          }
        }
        
        // 处理 ORDER BY
        if (sql.includes('order by')) {
          const isDesc = sql.includes('desc');
          data = [...data].sort((a: any, b: any) => {
            const timeA = new Date(a.created_at || 0).getTime();
            const timeB = new Date(b.created_at || 0).getTime();
            return isDesc ? timeB - timeA : timeA - timeB;
          });
        }
        
        // 处理 LIMIT
        const limitMatch = originalSql.match(/LIMIT\s+\$(\d+)/i);
        if (limitMatch) {
          const limitIdx = parseInt(limitMatch[1]) - 1;
          const limit = parseInt(params?.[limitIdx] as string);
          const offsetMatch = originalSql.match(/OFFSET\s+\$(\d+)/i);
          const offset = offsetMatch ? parseInt(params?.[parseInt(offsetMatch[1]) - 1] as string) : 0;
          data = data.slice(offset, offset + limit);
        }
        
        // users 表：检查是否需要排除密码
        if (actualTable === 'users') {
          const selectAll = /select\s+\*/i.test(originalSql);
          if (!selectAll) {
            data = data.map((u: any) => {
              const { password, ...rest } = u;
              return rest;
            });
          }
        }
        
        // 添加 JOIN 字段
        if (actualTable === 'customers') {
          data = data.map((c: any) => ({
            ...c,
            imported_by_name: memoryData.users.find((u: any) => u.id === c.imported_by)?.real_name || '',
            is_duplicate: false
          }));
        }
        
        if (actualTable === 'tasks') {
          data = data.map((t: any) => {
            const customer = memoryData.customers.find((c: any) => c.id === t.customer_id);
            const agent = memoryData.users.find((u: any) => u.id === t.assigned_to);
            return {
              ...t,
              customer_name: customer?.name || '',
              assigned_to_name: agent?.real_name || ''
            };
          });
        }
        
        if (actualTable === 'calls') {
          data = data.map((c: any) => {
            const agent = memoryData.users.find((u: any) => u.id === c.agent_id);
            const customer = memoryData.customers.find((cust: any) => cust.id === c.customer_id);
            return {
              ...c,
              agent_name: agent?.real_name || '',
              customer_name: customer?.name || ''
            };
          });
        }
        
        return { rows: data, rowCount: data.length };
      }
    }
    
    // agent_configs 查询
    if (sql.includes('from agent_configs')) {
      const configs = Object.values(memoryData.agent_configs);
      return { rows: configs, rowCount: configs.length };
    }
    
    console.log('内存数据库：未处理的查询', originalSql.substring(0, 100));
    return { rows: [], rowCount: 0 };
  }
  
  private handleCount(sql: string, originalSql: string, params?: any[]): { rows: any[]; rowCount: number } {
    const fromMatch = originalSql.match(/FROM\s+(\w+)/i);
    if (!fromMatch) return { rows: [{ count: '0' }], rowCount: 1 };
    
    const table = fromMatch[1].toLowerCase();
    const tableMap: Record<string, string> = {
      'call_records': 'calls',
      'users': 'users',
      'customers': 'customers',
      'calls': 'calls',
      'tasks': 'tasks',
      'voicemail_records': 'voicemail_records',
      'sms_records': 'sms_records',
      'unanswered_records': 'unanswered_records'
    };
    
    const actualTable = tableMap[table] || table;
    if (memoryData[actualTable]) {
      let data = memoryData[actualTable];
      
      // 处理 WHERE 条件
      if (sql.includes('where') && params) {
        // 简单处理：根据params数量返回不同结果
        if (params.length > 0) {
          // 对于子查询中的count，返回实际数量
          const count = Array.isArray(data) ? data.length : Object.keys(data).length;
          return { rows: [{ count: count.toString() }], rowCount: 1 };
        }
      }
      
      const count = Array.isArray(data) ? data.length : Object.keys(data).length;
      return { rows: [{ count: count.toString() }], rowCount: 1 };
    }
    
    return { rows: [{ count: '0' }], rowCount: 1 };
  }
  
  private insert(sql: string, originalSql: string, params?: any[]): { rows: any[]; rowCount: number } {
    const tableMatch = originalSql.match(/into\s+(\w+)/i);
    const tableName = tableMatch ? tableMatch[1] : '';
    const tableMap: Record<string, string> = {
      'call_records': 'calls',
      'users': 'users',
      'customers': 'customers',
      'calls': 'calls',
      'tasks': 'tasks',
      'voicemail_records': 'voicemail_records',
      'sms_records': 'sms_records',
      'unanswered_records': 'unanswered_records'
    };
    
    const actualTable = tableMap[tableName] || tableName;
    if (!actualTable || !memoryData[actualTable]) {
      return { rows: [], rowCount: 0 };
    }
    
    const newItem: any = { id: getNextId(actualTable) };
    
    if (actualTable === 'calls') {
      newItem.customer_id = params?.[0];
      newItem.agent_id = params?.[1];
      newItem.customer_phone = params?.[2];
      newItem.status = params?.[3] || 'pending';
      newItem.created_at = new Date().toISOString();
      newItem.updated_at = new Date().toISOString();
    } else if (actualTable === 'customers') {
      newItem.name = params?.[0];
      newItem.phone = params?.[1];
      newItem.email = params?.[2];
      newItem.company = params?.[3];
      newItem.status = params?.[4] || 'pending';
      newItem.created_at = new Date().toISOString();
      newItem.updated_at = new Date().toISOString();
    } else if (actualTable === 'users') {
      newItem.username = params?.[0];
      newItem.password = params?.[1];
      newItem.role = params?.[2] || 'agent';
      newItem.real_name = params?.[3];
      newItem.status = 'active';
      newItem.created_at = new Date().toISOString();
      newItem.updated_at = new Date().toISOString();
    } else if (actualTable === 'tasks') {
      newItem.title = params?.[0];
      newItem.description = params?.[1];
      newItem.assigned_to = params?.[2];
      newItem.customer_id = params?.[3];
      newItem.priority = params?.[4] || 'normal';
      newItem.status = 'pending';
      newItem.created_by = params?.[5];
      newItem.created_at = new Date().toISOString();
      newItem.updated_at = new Date().toISOString();
    }
    
    memoryData[actualTable].push(newItem);
    return { rows: [newItem], rowCount: 1 };
  }
  
  private update(sql: string, originalSql: string, params?: any[]): { rows: any[]; rowCount: number } {
    const tableMatch = originalSql.match(/update\s+(\w+)/i);
    const tableName = tableMatch ? tableMatch[1] : '';
    const tableMap: Record<string, string> = {
      'call_records': 'calls',
      'users': 'users',
      'customers': 'customers',
      'calls': 'calls',
      'tasks': 'tasks'
    };
    
    const actualTable = tableMap[tableName] || tableName;
    if (!actualTable || !memoryData[actualTable]) {
      return { rows: [], rowCount: 0 };
    }
    
    const idMatch = originalSql.match(/where\s+id\s*=\s*\$(\d+)/i);
    if (idMatch) {
      const idIndex = parseInt(idMatch[1]) - 1;
      const id = params?.[idIndex];
      
      const items = memoryData[actualTable];
      const item = items.find((i: any) => i.id === id);
      
      if (item) {
        item.updated_at = new Date().toISOString();
        return { rows: [item], rowCount: 1 };
      }
    }
    
    return { rows: [], rowCount: 0 };
  }
  
  private delete(sql: string, originalSql: string, params?: any[]): { rows: any[]; rowCount: number } {
    const tableMatch = originalSql.match(/from\s+(\w+)/i);
    const tableName = tableMatch ? tableMatch[1] : '';
    const tableMap: Record<string, string> = {
      'call_records': 'calls',
      'users': 'users',
      'customers': 'customers',
      'calls': 'calls',
      'tasks': 'tasks'
    };
    
    const actualTable = tableMap[tableName] || tableName;
    if (!actualTable || !memoryData[actualTable]) {
      return { rows: [], rowCount: 0 };
    }
    
    const idMatch = originalSql.match(/where\s+id\s*=\s*\$(\d+)/i);
    if (idMatch) {
      const idIndex = parseInt(idMatch[1]) - 1;
      const id = params?.[idIndex];
      
      const items = memoryData[actualTable];
      const index = items.findIndex((i: any) => i.id === id);
      
      if (index > -1) {
        items.splice(index, 1);
        return { rows: [], rowCount: 1 };
      }
    }
    
    return { rows: [], rowCount: 0 };
  }
}

// 创建内存数据库实例
const memoryDB = new MemoryDB();

// 如果有 PostgreSQL 配置，使用 PostgreSQL，否则使用内存数据库
let pool: any;

if (usePostgres) {
  pool = new Pool({
    host: process.env.DB_HOST || 'localhost',
    port: parseInt(process.env.DB_PORT || '5432'),
    database: process.env.DB_NAME || 'postgres',
    user: process.env.DB_USER || 'postgres',
    password: process.env.DB_PASSWORD || '',
  });
  console.log('✅ 使用 PostgreSQL 数据库');
} else {
  pool = memoryDB;
  console.log('✅ 使用内存数据库（演示模式）');
  console.log('   📊 测试数据：');
  console.log('   - 管理员: admin / admin123');
  console.log('   - 客服: agent / agent123');
  console.log(`   - 客户: ${memoryData.customers.length} 条`);
  console.log(`   - 通话记录: ${memoryData.calls.length} 条`);
  console.log(`   - 任务: ${memoryData.tasks.length} 条`);
  console.log(`   - 语音信箱: ${memoryData.voicemail_records.length} 条`);
  console.log(`   - 短信记录: ${memoryData.sms_records.length} 条`);
}

export default pool;
export const query = (text: string, params?: any[]) => pool.query(text, params);
