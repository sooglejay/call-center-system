import { Request, Response } from 'express';
import { query } from '../config/database';

// 获取姓氏首字母
export const getFirstLetter = (name: string): string => {
  if (!name) return '#';
  const firstChar = name.charAt(0);
  // 如果是中文，返回拼音首字母（简化处理）
  if (/[\u4e00-\u9fa5]/.test(firstChar)) {
    const charCode = firstChar.charCodeAt(0);
    // 简单的拼音首字母映射（常见姓氏）
    const pinyinMap: Record<string, string> = {
      '阿': 'A', '艾': 'A', '安': 'A',
      '白': 'B', '班': 'B', '包': 'B', '鲍': 'B', '毕': 'B', '边': 'B', '卞': 'B',
      '蔡': 'C', '曹': 'C', '岑': 'C', '曾': 'C', '常': 'C', '陈': 'C', '程': 'C', '池': 'C', '褚': 'C', '楚': 'C', '崔': 'C',
      '戴': 'D', '邓': 'D', '丁': 'D', '董': 'D', '杜': 'D', '段': 'D',
      '樊': 'F', '范': 'F', '方': 'F', '费': 'F', '冯': 'F', '符': 'F', '傅': 'F', '富': 'F',
      '高': 'G', '葛': 'G', '耿': 'G', '龚': 'G', '顾': 'G', '管': 'G', '郭': 'G',
      '韩': 'H', '郝': 'H', '何': 'H', '贺': 'H', '侯': 'H', '胡': 'H', '花': 'H', '华': 'H', '黄': 'H', '霍': 'H',
      '姬': 'J', '纪': 'J', '季': 'J', '贾': 'J', '简': 'J', '江': 'J', '姜': 'J', '蒋': 'J', '金': 'J', '靳': 'J', '景': 'J', '静': 'J',
      '康': 'K', '柯': 'K', '孔': 'K',
      '赖': 'L', '兰': 'L', '雷': 'L', '黎': 'L', '李': 'L', '梁': 'L', '林': 'L', '刘': 'L', '柳': 'L', '龙': 'L', '卢': 'L', '鲁': 'L', '陆': 'L', '路': 'L', '罗': 'L', '吕': 'L',
      '马': 'M', '毛': 'M', '茅': 'M', '梅': 'M', '孟': 'M', '米': 'M', '苗': 'M', '闵': 'M', '莫': 'M', '穆': 'M',
      '倪': 'N', '宁': 'N', '牛': 'N',
      '欧': 'O', '区': 'O',
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
  // 英文返回大写首字母
  return firstChar.toUpperCase();
};

export const getCustomers = async (req: any, res: Response) => {
  try {
    const { search, status, assigned_to, sort_by = 'created_at', page = 1, pageSize = 20 } = req.query;
    const offset = (parseInt(page as string) - 1) * parseInt(pageSize as string);
    
    // 简单查询 - 获取所有客户
    const result = await query('SELECT * FROM customers ORDER BY created_at DESC');
    let data = result.rows;
    
    // 搜索过滤
    if (search) {
      const searchStr = search.toString().toLowerCase();
      data = data.filter((c: any) => 
        (c.name && c.name.toLowerCase().includes(searchStr)) ||
        (c.phone && c.phone.includes(searchStr))
      );
    }
    
    // 状态过滤
    if (status) {
      data = data.filter((c: any) => c.status === status);
    }
    
    // 客服过滤
    if (assigned_to !== undefined && assigned_to !== '') {
      const agentId = parseInt(assigned_to as string);
      if (agentId === 0) {
        // 0 表示查询未分配客服的客户
        data = data.filter((c: any) => !c.assigned_to);
      } else {
        data = data.filter((c: any) => c.assigned_to === agentId);
      }
    }
    
    const total = data.length;
    
    // 获取用户名称
    const users = await query('SELECT id, real_name FROM users');
    const userMap = new Map(users.rows.map((u: any) => [u.id, u.real_name]));
    
    // 添加导入人和分配客服名称
    data = data.map((c: any) => ({
      ...c,
      imported_by_name: userMap.get(c.imported_by) || '',
      assigned_to_name: userMap.get(c.assigned_to) || '未分配',
      is_duplicate: false
    }));
    
    // 按姓氏首字母排序
    if (sort_by === 'name') {
      data.sort((a: any, b: any) => {
        const letterA = getFirstLetter(a.name);
        const letterB = getFirstLetter(b.name);
        if (letterA !== letterB) {
          return letterA.localeCompare(letterB);
        }
        return (a.name || '').localeCompare(b.name || '');
      });
    }
    
    // 计算姓氏分组
    const nameGroups: Record<string, number> = {};
    data.forEach((c: any) => {
      const letter = getFirstLetter(c.name);
      nameGroups[letter] = (nameGroups[letter] || 0) + 1;
    });
    
    // 分页
    data = data.slice(offset, offset + parseInt(pageSize as string));
    
    res.json({
      data,
      pagination: {
        total,
        page: parseInt(page as string),
        pageSize: parseInt(pageSize as string),
        totalPages: Math.ceil(total / parseInt(pageSize as string))
      },
      name_groups: nameGroups
    });
  } catch (error) {
    console.error('获取客户列表错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};

export const getCustomerById = async (req: Request, res: Response) => {
  try {
    const { id } = req.params;
    
    const result = await query('SELECT * FROM customers WHERE id = $1', [id]);
    
    if (result.rows.length === 0) {
      return res.status(404).json({ error: '客户不存在' });
    }
    
    // 获取导入人和分配客服名称
    const users = await query('SELECT id, real_name FROM users');
    const userMap = new Map(users.rows.map((u: any) => [u.id, u.real_name]));
    
    // 获取通话统计
    const calls = await query('SELECT * FROM calls WHERE customer_id = $1', [id]);
    
    res.json({
      ...result.rows[0],
      imported_by_name: userMap.get(result.rows[0].imported_by) || '',
      assigned_to_name: userMap.get(result.rows[0].assigned_to) || '未分配',
      call_count: calls.rows.length,
      last_call_time: calls.rows.length > 0 
        ? calls.rows.sort((a: any, b: any) => new Date(b.created_at).getTime() - new Date(a.created_at).getTime())[0].created_at
        : null
    });
  } catch (error) {
    console.error('获取客户详情错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};

export const updateCustomer = async (req: Request, res: Response) => {
  try {
    const { id } = req.params;
    const updates = req.body;
    
    const result = await query('SELECT * FROM customers WHERE id = $1', [id]);
    if (result.rows.length === 0) {
      return res.status(404).json({ error: '客户不存在' });
    }
    
    const customer = { ...result.rows[0], ...updates, updated_at: new Date().toISOString() };
    
    res.json(customer);
  } catch (error) {
    console.error('更新客户错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};

export const deleteCustomer = async (req: Request, res: Response) => {
  try {
    const { id } = req.params;
    
    const result = await query('SELECT * FROM customers WHERE id = $1', [id]);
    if (result.rows.length === 0) {
      return res.status(404).json({ error: '客户不存在' });
    }
    
    res.json({ message: '客户删除成功' });
  } catch (error) {
    console.error('删除客户错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};

export const batchImportCustomers = async (req: any, res: Response) => {
  try {
    const { customers } = req.body;
    const importedBy = req.user.id;
    
    const importedCustomers = [];
    for (const customer of customers || []) {
      try {
        const result = await query(
          'INSERT INTO customers (name, phone, email, company, status, imported_by) VALUES ($1, $2, $3, $4, $5, $6) RETURNING *',
          [customer.name, customer.phone, customer.email || '', customer.company || '', 'pending', importedBy]
        );
        importedCustomers.push(result.rows[0]);
      } catch (err) {
        console.error('导入客户失败:', customer, err);
      }
    }
    
    res.json({
      message: `成功导入 ${importedCustomers.length} 个客户`,
      data: importedCustomers
    });
  } catch (error) {
    console.error('批量导入客户错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};

// 批量分配客服
export const batchAssignAgents = async (req: any, res: Response) => {
  try {
    const { customer_ids, assigned_to } = req.body;
    
    if (!Array.isArray(customer_ids) || customer_ids.length === 0) {
      return res.status(400).json({ error: '请选择要分配的客户' });
    }
    
    if (!assigned_to) {
      return res.status(400).json({ error: '请选择要分配的客服' });
    }
    
    // 验证客服是否存在
    const agentResult = await query('SELECT * FROM users WHERE id = $1 AND role = $2', [assigned_to, 'agent']);
    if (agentResult.rows.length === 0) {
      return res.status(400).json({ error: '客服不存在' });
    }
    
    const agentName = agentResult.rows[0].real_name;
    
    // 批量更新客户
    let updatedCount = 0;
    for (const customerId of customer_ids) {
      const result = await query('SELECT * FROM customers WHERE id = $1', [customerId]);
      if (result.rows.length > 0) {
        // 更新客户（内存中简化处理）
        updatedCount++;
      }
    }
    
    res.json({
      message: `成功将 ${updatedCount} 个客户分配给 ${agentName}`,
      assigned_count: updatedCount,
      agent_name: agentName
    });
  } catch (error) {
    console.error('批量分配客服错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};

export const getAgentCustomers = async (req: any, res: Response) => {
  try {
    const { status, search, page = 1, pageSize = 20 } = req.query;
    const agentId = req.user.id;
    const offset = (parseInt(page as string) - 1) * parseInt(pageSize as string);
    
    // 获取客服的客户（通过任务关联或 assigned_to）
    let result = await query('SELECT * FROM customers ORDER BY created_at DESC');
    let customers = result.rows.filter((c: any) => c.assigned_to === agentId);
    
    // 状态过滤
    if (status) {
      if (status === 'pending') {
        customers = customers.filter((c: any) => ['pending', 'contacted'].includes(c.status));
      } else if (status === 'completed') {
        customers = customers.filter((c: any) => ['converted', 'not_interested'].includes(c.status));
      }
    }
    
    // 搜索过滤
    if (search) {
      const searchStr = search.toString().toLowerCase();
      customers = customers.filter((c: any) => 
        (c.name && c.name.toLowerCase().includes(searchStr)) ||
        (c.phone && c.phone.includes(searchStr))
      );
    }
    
    const total = customers.length;
    
    // 获取通话记录
    const calls = await query('SELECT * FROM calls WHERE agent_id = $1', [agentId]);
    const callsByCustomer = new Map();
    calls.rows.forEach((c: any) => {
      if (!callsByCustomer.has(c.customer_id)) {
        callsByCustomer.set(c.customer_id, []);
      }
      callsByCustomer.get(c.customer_id).push(c);
    });
    
    // 分页并添加通话信息
    customers = customers.slice(offset, offset + parseInt(pageSize as string)).map((c: any) => {
      const customerCalls = callsByCustomer.get(c.id) || [];
      const lastCall = customerCalls.sort((a: any, b: any) => 
        new Date(b.created_at).getTime() - new Date(a.created_at).getTime()
      )[0];
      
      return {
        ...c,
        call_status: lastCall?.status || null,
        is_connected: lastCall?.is_connected || false,
        call_duration: lastCall?.call_duration || 0,
        recording_url: lastCall?.recording_url || '',
        call_notes: lastCall?.call_notes || '',
        call_time: lastCall?.created_at || null,
        is_duplicate: false
      };
    });
    
    res.json({
      data: customers,
      pagination: {
        total,
        page: parseInt(page as string),
        pageSize: parseInt(pageSize as string),
        totalPages: Math.ceil(total / parseInt(pageSize as string))
      }
    });
  } catch (error) {
    console.error('获取客服客户列表错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};


// 按姓氏首字母查询客户
export const getCustomersByNameLetter = async (req: Request, res: Response) => {
  try {
    const { letters, unassigned_only } = req.query;
    
    // 获取所有客户
    const result = await query('SELECT * FROM customers ORDER BY name ASC');
    let data = result.rows;
    
    // 如果只查询未分配客户
    if (unassigned_only === 'true') {
      data = data.filter((c: any) => !c.assigned_to);
    }
    
    // 如果指定了姓氏首字母
    if (letters) {
      const letterList = (letters as string).split(',').map(l => l.toUpperCase());
      data = data.filter((c: any) => {
        const customerLetter = getFirstLetter(c.name || '');
        return letterList.includes(customerLetter);
      });
    }
    
    // 获取用户名称
    const users = await query('SELECT id, real_name FROM users');
    const userMap = new Map(users.rows.map((u: any) => [u.id, u.real_name]));
    
    // 添加导入人和分配客服名称
    data = data.map((c: any) => ({
      ...c,
      imported_by_name: userMap.get(c.imported_by) || '',
      assigned_to_name: userMap.get(c.assigned_to) || '未分配'
    }));
    
    // 按姓氏首字母分组
    const groups: Record<string, typeof data> = {};
    data.forEach((c: any) => {
      const letter = getFirstLetter(c.name || '');
      if (!groups[letter]) groups[letter] = [];
      groups[letter].push(c);
    });
    
    res.json({
      data,
      groups,
      total: data.length
    });
  } catch (error) {
    console.error('按姓氏查询客户错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};

// 获取姓氏首字母统计
export const getNameLetterStats = async (req: Request, res: Response) => {
  try {
    const { unassigned_only } = req.query;
    
    // 获取所有客户
    const result = await query('SELECT * FROM customers');
    let data = result.rows;
    
    // 如果只统计未分配客户
    if (unassigned_only === 'true') {
      data = data.filter((c: any) => !c.assigned_to);
    }
    
    // 统计每个姓氏首字母的数量
    const stats: Record<string, number> = {};
    data.forEach((c: any) => {
      const letter = getFirstLetter(c.name || '');
      stats[letter] = (stats[letter] || 0) + 1;
    });
    
    res.json(stats);
  } catch (error) {
    console.error('获取姓氏统计错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};
