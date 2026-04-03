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
      '蔡': 'C', '曹': 'C', '岑': 'C', '常': 'C', '陈': 'C', '程': 'C', '池': 'C', '褚': 'C', '楚': 'C', '崔': 'C',
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
    const { search, status, assigned_to, sort_by = 'created_at', page = 1, pageSize = 20, name_letter } = req.query;
    const pageNum = parseInt(page as string);
    const sizeNum = parseInt(pageSize as string);
    const offset = (pageNum - 1) * sizeNum;
    
    // 获取当前用户信息
    const currentUser = req.user;
    const userResult = await query('SELECT role, data_access_type FROM users WHERE id = $1', [currentUser.id]);
    const userRole = userResult.rows[0]?.role;
    const dataAccessType = userResult.rows[0]?.data_access_type || 'mock';
    
    // 构建WHERE条件
    const whereConditions: string[] = [];
    const params: any[] = [];
    
    // 根据用户权限过滤数据
    if (userRole !== 'admin') {
      whereConditions.push(`(data_source = $${params.length + 1} OR data_source IS NULL)`);
      params.push(dataAccessType);
    }
    
    // 搜索过滤
    if (search) {
      whereConditions.push(`(LOWER(name) LIKE $${params.length + 1} OR phone LIKE $${params.length + 1})`);
      params.push(`%${search.toString().toLowerCase()}%`);
    }
    
    // 状态过滤
    if (status) {
      whereConditions.push(`status = $${params.length + 1}`);
      params.push(status);
    }
    
    // 客服过滤
    if (assigned_to !== undefined && assigned_to !== '') {
      const agentId = parseInt(assigned_to as string);
      if (agentId === 0) {
        whereConditions.push(`(assigned_to IS NULL OR assigned_to = 0)`);
      } else {
        whereConditions.push(`assigned_to = $${params.length + 1}`);
        params.push(agentId);
      }
    }
    
    // 姓氏首字母过滤
    if (name_letter) {
      const letters = (name_letter as string).split(',');
      const letterConditions = letters.map((_, idx) => {
        const pinyinMap: Record<string, string[]> = {
          'A': ['阿', '艾', '安'],
          'B': ['白', '班', '包', '鲍', '毕', '边', '卞'],
          'C': ['蔡', '曹', '岑', '常', '陈', '程', '池', '褚', '楚', '崔'],
          'D': ['戴', '邓', '丁', '董', '杜', '段'],
          'F': ['樊', '范', '方', '费', '冯', '符', '傅', '富'],
          'G': ['高', '葛', '耿', '龚', '顾', '管', '郭'],
          'H': ['韩', '郝', '何', '贺', '侯', '胡', '花', '华', '黄', '霍'],
          'J': ['姬', '纪', '季', '贾', '简', '江', '姜', '蒋', '金', '靳', '景', '静'],
          'K': ['康', '柯', '孔'],
          'L': ['赖', '兰', '雷', '黎', '李', '梁', '林', '刘', '柳', '龙', '卢', '鲁', '陆', '路', '罗', '吕'],
          'M': ['马', '毛', '茅', '梅', '孟', '米', '苗', '闵', '莫', '穆'],
          'N': ['倪', '宁', '牛'],
          'O': ['欧', '区'],
          'P': ['潘', '庞', '裴', '彭', '皮', '朴'],
          'Q': ['齐', '钱', '乔', '秦', '邱', '裘', '曲'],
          'R': ['冉', '任', '荣', '阮'],
          'S': ['沙', '邵', '沈', '盛', '施', '石', '史', '舒', '宋', '苏', '孙', '索'],
          'T': ['汤', '唐', '陶', '田', '童'],
          'W': ['万', '汪', '王', '韦', '卫', '魏', '温', '文', '翁', '巫', '吴', '伍', '武'],
          'X': ['席', '夏', '项', '萧', '谢', '辛', '邢', '熊', '徐', '许', '薛'],
          'Y': ['严', '颜', '杨', '叶', '易', '殷', '尹', '应', '尤', '于', '余', '俞', '虞', '袁', '岳', '云'],
          'Z': ['藏', '曾', '翟', '詹', '张', '章', '赵', '郑', '钟', '周', '朱', '诸', '祝', '庄']
        };
        const chars = pinyinMap[letters[idx]] || [];
        if (chars.length > 0) {
          const charConditions = chars.map((_, charIdx) => 
            `SUBSTR(name, 1, 1) = $${params.length + idx + charIdx + 1}`
          ).join(' OR ');
          params.push(...chars);
          return `(${charConditions})`;
        }
        return `UPPER(SUBSTR(name, 1, 1)) = $${params.length + idx + 1}`;
      }).filter(Boolean);
      
      if (letterConditions.length > 0) {
        whereConditions.push(`(${letterConditions.join(' OR ')})`);
        // 添加英文字母参数
        letters.forEach(letter => {
          const pinyinMap: Record<string, string[]> = {
            'A': ['阿', '艾', '安'], 'B': ['白', '班', '包', '鲍', '毕', '边', '卞'],
            'C': ['蔡', '曹', '岑', '常', '陈', '程', '池', '褚', '楚', '崔'],
            'D': ['戴', '邓', '丁', '董', '杜', '段'],
            'F': ['樊', '范', '方', '费', '冯', '符', '傅', '富'],
            'G': ['高', '葛', '耿', '龚', '顾', '管', '郭'],
            'H': ['韩', '郝', '何', '贺', '侯', '胡', '花', '华', '黄', '霍'],
            'J': ['姬', '纪', '季', '贾', '简', '江', '姜', '蒋', '金', '靳', '景', '静'],
            'K': ['康', '柯', '孔'],
            'L': ['赖', '兰', '雷', '黎', '李', '梁', '林', '刘', '柳', '龙', '卢', '鲁', '陆', '路', '罗', '吕'],
            'M': ['马', '毛', '茅', '梅', '孟', '米', '苗', '闵', '莫', '穆'],
            'N': ['倪', '宁', '牛'], 'O': ['欧', '区'],
            'P': ['潘', '庞', '裴', '彭', '皮', '朴'],
            'Q': ['齐', '钱', '乔', '秦', '邱', '裘', '曲'],
            'R': ['冉', '任', '荣', '阮'],
            'S': ['沙', '邵', '沈', '盛', '施', '石', '史', '舒', '宋', '苏', '孙', '索'],
            'T': ['汤', '唐', '陶', '田', '童'],
            'W': ['万', '汪', '王', '韦', '卫', '魏', '温', '文', '翁', '巫', '吴', '伍', '武'],
            'X': ['席', '夏', '项', '萧', '谢', '辛', '邢', '熊', '徐', '许', '薛'],
            'Y': ['严', '颜', '杨', '叶', '易', '殷', '尹', '应', '尤', '于', '余', '俞', '虞', '袁', '岳', '云'],
            'Z': ['藏', '曾', '翟', '詹', '张', '章', '赵', '郑', '钟', '周', '朱', '诸', '祝', '庄']
          };
          if (!pinyinMap[letter]) {
            params.push(letter);
          }
        });
      }
    }
    
    const whereClause = whereConditions.length > 0 ? `WHERE ${whereConditions.join(' AND ')}` : '';
    
    // 获取总数（使用COUNT查询）
    const countResult = await query(`SELECT COUNT(*) as total FROM customers ${whereClause}`, params);
    const total = parseInt(countResult.rows[0].total);
    
    // 构建排序
    let orderBy = 'ORDER BY created_at DESC';
    if (sort_by === 'name') {
      orderBy = 'ORDER BY name ASC';
    }
    
    // 获取分页数据（使用LIMIT/OFFSET）
    const queryParams = [...params, sizeNum, offset];
    const dataResult = await query(
      `SELECT * FROM customers ${whereClause} ${orderBy} LIMIT $${params.length + 1} OFFSET $${params.length + 2}`,
      queryParams
    );
    let data = dataResult.rows;
    
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
    
    // 计算姓氏分组（使用子查询优化）
    const nameGroupsResult = await query(
      `SELECT 
        CASE 
          WHEN name IS NULL OR name = '' THEN '#'
          WHEN SUBSTR(name, 1, 1) BETWEEN '一' AND '龥' THEN
            CASE SUBSTR(name, 1, 1)
              WHEN '阿' THEN 'A' WHEN '艾' THEN 'A' WHEN '安' THEN 'A'
              WHEN '白' THEN 'B' WHEN '班' THEN 'B' WHEN '包' THEN 'B' WHEN '鲍' THEN 'B' WHEN '毕' THEN 'B' WHEN '边' THEN 'B'
              WHEN '蔡' THEN 'C' WHEN '曹' THEN 'C' WHEN '陈' THEN 'C' WHEN '程' THEN 'C' WHEN '崔' THEN 'C'
              WHEN '戴' THEN 'D' WHEN '邓' THEN 'D' WHEN '丁' THEN 'D' WHEN '董' THEN 'D' WHEN '杜' THEN 'D'
              WHEN '范' THEN 'F' WHEN '方' THEN 'F' WHEN '冯' THEN 'F' WHEN '傅' THEN 'F'
              WHEN '高' THEN 'G' WHEN '葛' THEN 'G' WHEN '郭' THEN 'G'
              WHEN '韩' THEN 'H' WHEN '何' THEN 'H' WHEN '贺' THEN 'H' WHEN '胡' THEN 'H' WHEN '黄' THEN 'H'
              WHEN '贾' THEN 'J' WHEN '江' THEN 'J' WHEN '姜' THEN 'J' WHEN '蒋' THEN 'J' WHEN '金' THEN 'J'
              WHEN '康' THEN 'K' WHEN '孔' THEN 'K'
              WHEN '赖' THEN 'L' WHEN '兰' THEN 'L' WHEN '雷' THEN 'L' WHEN '李' THEN 'L' WHEN '梁' THEN 'L'
              WHEN '林' THEN 'L' WHEN '刘' THEN 'L' WHEN '龙' THEN 'L' WHEN '卢' THEN 'L' WHEN '陆' THEN 'L' WHEN '罗' THEN 'L'
              WHEN '马' THEN 'M' WHEN '毛' THEN 'M' WHEN '孟' THEN 'M' WHEN '莫' THEN 'M'
              WHEN '潘' THEN 'P' WHEN '彭' THEN 'P'
              WHEN '钱' THEN 'Q' WHEN '秦' THEN 'Q' WHEN '邱' THEN 'Q'
              WHEN '任' THEN 'R'
              WHEN '沈' THEN 'S' WHEN '史' THEN 'S' WHEN '宋' THEN 'S' WHEN '苏' THEN 'S' WHEN '孙' THEN 'S'
              WHEN '汤' THEN 'T' WHEN '唐' THEN 'T' WHEN '陶' THEN 'T' WHEN '田' THEN 'T'
              WHEN '万' THEN 'W' WHEN '汪' THEN 'W' WHEN '王' THEN 'W' WHEN '韦' THEN 'W' WHEN '魏' THEN 'W'
              WHEN '吴' THEN 'W' WHEN '武' THEN 'W'
              WHEN '夏' THEN 'X' WHEN '肖' THEN 'X' WHEN '谢' THEN 'X' WHEN '徐' THEN 'X' WHEN '许' THEN 'X'
              WHEN '严' THEN 'Y' WHEN '杨' THEN 'Y' WHEN '叶' THEN 'Y' WHEN '易' THEN 'Y' WHEN '殷' THEN 'Y'
              WHEN '于' THEN 'Y' WHEN '余' THEN 'Y' WHEN '袁' THEN 'Y'
              WHEN '曾' THEN 'Z' WHEN '张' THEN 'Z' WHEN '章' THEN 'Z' WHEN '赵' THEN 'Z' WHEN '郑' THEN 'Z'
              WHEN '周' THEN 'Z' WHEN '朱' THEN 'Z'
              ELSE '#'
            END
          ELSE UPPER(SUBSTR(name, 1, 1))
        END as letter,
        COUNT(*) as count
       FROM customers ${whereClause}
       GROUP BY letter`,
      params
    );
    
    const nameGroups: Record<string, number> = {};
    nameGroupsResult.rows.forEach((row: any) => {
      nameGroups[row.letter] = parseInt(row.count);
    });
    
    res.json({
      data,
      total,
      page: pageNum,
      page_size: sizeNum,
      total_pages: Math.ceil(total / sizeNum),
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
    
    const customer = result.rows[0];
    
    // 获取导入人和分配客服信息
    const users = await query('SELECT id, username, real_name, role, phone, email, status, data_access_type FROM users');
    const userMap = new Map(users.rows.map((u: any) => [u.id, u]));
    
    // 获取通话统计
    const calls = await query('SELECT * FROM calls WHERE customer_id = $1', [id]);
    
    // 构建分配客服对象
    let assignedAgent = null;
    if (customer.assigned_to) {
      const agent = userMap.get(customer.assigned_to);
      if (agent) {
        assignedAgent = {
          id: agent.id,
          username: agent.username,
          real_name: agent.real_name,
          role: agent.role,
          phone: agent.phone,
          email: agent.email,
          status: agent.status,
          data_access_type: agent.data_access_type
        };
      }
    }
    
    res.json({
      ...customer,
      imported_by_name: userMap.get(customer.imported_by)?.real_name || '',
      assigned_to_name: assignedAgent?.real_name || '未分配',
      assigned_agent: assignedAgent,
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

// 手动创建客户
export const createCustomer = async (req: any, res: Response) => {
  try {
    const { name, phone, email, company, address, notes, status, assigned_to } = req.body;
    const createdBy = req.user.id;
    
    // 验证必填字段
    if (!name || !name.trim()) {
      return res.status(400).json({ error: '客户姓名不能为空' });
    }
    if (!phone || !phone.trim()) {
      return res.status(400).json({ error: '电话号码不能为空' });
    }
    
    // 检查电话是否已存在
    const existingCustomer = await query('SELECT id FROM customers WHERE phone = $1', [phone.trim()]);
    if (existingCustomer.rows.length > 0) {
      return res.status(400).json({ error: '该电话号码已存在' });
    }
    
    // 获取当前用户的数据权限类型
    const userResult = await query('SELECT role, data_access_type FROM users WHERE id = $1', [createdBy]);
    const dataAccessType = userResult.rows[0]?.data_access_type || 'real';
    
    // 插入客户数据
    const result = await query(
      `INSERT INTO customers (name, phone, email, company, address, notes, status, assigned_to, imported_by, data_source, created_at, updated_at)
       VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, datetime('now'), datetime('now'))
       RETURNING *`,
      [
        name.trim(),
        phone.trim(),
        email?.trim() || null,
        company?.trim() || null,
        address?.trim() || null,
        notes?.trim() || null,
        status || 'pending',
        assigned_to || null,
        createdBy,
        dataAccessType
      ]
    );
    
    // 获取分配客服名称
    const users = await query('SELECT id, real_name FROM users');
    const userMap = new Map(users.rows.map((u: any) => [u.id, u.real_name]));
    
    const newCustomer = {
      ...result.rows[0],
      imported_by_name: userMap.get(createdBy) || '',
      assigned_to_name: assigned_to ? userMap.get(assigned_to) || '未分配' : '未分配'
    };
    
    res.status(201).json({
      message: '客户创建成功',
      data: newCustomer
    });
  } catch (error) {
    console.error('创建客户错误:', error);
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
    
    // 构建更新字段
    const allowedFields = ['name', 'phone', 'email', 'company', 'address', 'notes', 'status', 'priority', 'assigned_to', 'remark'];
    const updateFields: string[] = [];
    const updateValues: any[] = [];
    let paramIndex = 1;
    
    for (const [key, value] of Object.entries(updates)) {
      if (allowedFields.includes(key)) {
        updateFields.push(`${key} = $${paramIndex}`);
        updateValues.push(value);
        paramIndex++;
      }
    }
    
    if (updateFields.length === 0) {
      return res.status(400).json({ error: '没有有效的更新字段' });
    }
    
    // 添加更新时间
    updateFields.push(`updated_at = datetime('now')`);
    
    // 添加 WHERE 条件的 ID 参数
    updateValues.push(parseInt(id as string));
    
    // 执行更新
    const updateResult = await query(
      `UPDATE customers SET ${updateFields.join(', ')} WHERE id = $${paramIndex}`,
      updateValues
    );
    
    // 获取更新后的数据
    const updatedCustomer = await query('SELECT * FROM customers WHERE id = $1', [id]);
    
    // 获取关联的用户名称
    const users = await query('SELECT id, real_name FROM users');
    const userMap = new Map(users.rows.map((u: any) => [u.id, u.real_name]));
    
    const customer = {
      ...updatedCustomer.rows[0],
      imported_by_name: userMap.get(updatedCustomer.rows[0].imported_by) || '',
      assigned_to_name: updatedCustomer.rows[0].assigned_to ? userMap.get(updatedCustomer.rows[0].assigned_to) || '未分配' : '未分配'
    };
    
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
    
    // 真正删除客户
    await query('DELETE FROM customers WHERE id = $1', [id]);
    
    res.json({ message: '客户删除成功', deleted_id: parseInt(id as string) });
  } catch (error) {
    console.error('删除客户错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};

export const batchImportCustomers = async (req: any, res: Response) => {
  try {
    const { customers, data_source = 'mock', assigned_to } = req.body;
    const importedBy = req.user.id;
    
    // 获取管理员的数据权限类型（只有管理员可以导入数据）
    const userResult = await query('SELECT role, data_access_type FROM users WHERE id = $1', [importedBy]);
    const userRole = userResult.rows[0]?.role;
    
    if (userRole !== 'admin') {
      return res.status(403).json({ error: '只有管理员可以导入客户数据' });
    }
    
    // 解析 assigned_to 参数
    let assignedToId: number | null = null;
    if (assigned_to) {
      assignedToId = parseInt(assigned_to as string);
      if (isNaN(assignedToId)) {
        return res.status(400).json({ error: 'assigned_to 参数必须是有效的用户ID' });
      }
      
      // 验证客服是否存在
      const agentResult = await query('SELECT id, real_name, role FROM users WHERE id = $1', [assignedToId]);
      if (agentResult.rows.length === 0) {
        return res.status(400).json({ error: '指定的客服不存在' });
      }
      if (agentResult.rows[0].role !== 'agent') {
        return res.status(400).json({ error: '指定的用户不是客服角色' });
      }
      console.log(`[批量导入] 将分配给客服: ${agentResult.rows[0].real_name} (ID=${assignedToId})`);
    }
    
    // 管理员导入的数据标记为 real，或者根据传入的 data_source 参数
    const finalDataSource = data_source || 'real';
    
    const importedCustomers = [];
    for (const customer of customers || []) {
      try {
        const result = await query(
          `INSERT INTO customers (name, phone, email, company, status, imported_by, data_source, assigned_to, created_at, updated_at)
           VALUES ($1, $2, $3, $4, $5, $6, $7, $8, datetime('now'), datetime('now'))
           RETURNING *`,
          [customer.name, customer.phone, customer.email || '', customer.company || '', 'pending', importedBy, finalDataSource, assignedToId]
        );
        importedCustomers.push(result.rows[0]);
      } catch (err) {
        console.error('导入客户失败:', customer, err);
      }
    }
    
    res.json({
      message: `成功导入 ${importedCustomers.length} 个客户`,
      data: importedCustomers,
      assigned_to: assignedToId
    });
  } catch (error) {
    console.error('批量导入客户错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};

// 批量分配客服
export const batchAssignAgents = async (req: any, res: Response) => {
  console.log('[批量分配] 开始处理分配请求:', {
    body: req.body,
    user: req.user?.id,
    timestamp: new Date().toISOString()
  });
  
  try {
    const { customer_ids, assigned_to } = req.body;
    
    if (!Array.isArray(customer_ids) || customer_ids.length === 0) {
      console.log('[批量分配] 失败: 未选择客户');
      return res.status(400).json({ error: '请选择要分配的客户' });
    }
    
    if (!assigned_to) {
      console.log('[批量分配] 失败: 未选择客服');
      return res.status(400).json({ error: '请选择要分配的客服' });
    }
    
    // 验证客服是否存在
    console.log(`[批量分配] 验证客服是否存在: ID=${assigned_to}`);
    const agentResult = await query('SELECT * FROM users WHERE id = $1 AND role = $2', [assigned_to, 'agent']);
    if (agentResult.rows.length === 0) {
      console.log(`[批量分配] 失败: 客服ID=${assigned_to} 不存在或不是客服角色`);
      return res.status(400).json({ 
        error: '客服不存在', 
        detail: `ID=${assigned_to} 的用户不存在或不是客服角色` 
      });
    }
    
    const agentName = agentResult.rows[0].real_name;
    const agentDataAccessType = agentResult.rows[0].data_access_type || 'mock';
    console.log(`[批量分配] 找到客服: ${agentName} (ID=${assigned_to}), 数据权限: ${agentDataAccessType}`);
    
    // 批量更新客户
    let updatedCount = 0;
    let failedCustomers: number[] = [];
    
    for (const customerId of customer_ids) {
      console.log(`[批量分配] 处理客户ID=${customerId}`);
      const result = await query('SELECT * FROM customers WHERE id = $1', [customerId]);
      if (result.rows.length > 0) {
        const customer = result.rows[0];
        console.log(`[批量分配] 找到客户: ${customer.name} (ID=${customerId}), 原分配: ${customer.assigned_to_name || '未分配'}`);
        
        try {
          // 更新客户分配（同时更新 assigned_to 和 data_source，确保客服能看到）
          // data_source 必须与客服的 data_access_type 匹配
          await query(
            'UPDATE customers SET assigned_to = $1, data_source = $2, updated_at = CURRENT_TIMESTAMP WHERE id = $3',
            [assigned_to, agentDataAccessType, customerId]
          );
          console.log(`[批量分配] 成功更新客户 ${customerId}, data_source=${agentDataAccessType}`);
          updatedCount++;
        } catch (updateError) {
          console.error(`[批量分配] 更新客户 ${customerId} 失败:`, updateError);
          failedCustomers.push(customerId);
        }
      } else {
        console.log(`[批量分配] 客户ID=${customerId} 不存在`);
        failedCustomers.push(customerId);
      }
    }
    
    console.log(`[批量分配] 完成: 成功=${updatedCount}, 失败=${failedCustomers.length}`);
    
    res.json({
      message: `成功将 ${updatedCount} 个客户分配给 ${agentName}`,
      assigned_count: updatedCount,
      failed_count: failedCustomers.length,
      failed_ids: failedCustomers,
      agent_name: agentName,
      agent_id: assigned_to
    });
  } catch (error) {
    console.error('[批量分配] 服务器错误:', error);
    res.status(500).json({ 
      error: '服务器错误', 
      detail: error instanceof Error ? error.message : '未知错误',
      timestamp: new Date().toISOString()
    });
  }
};

export const getAgentCustomers = async (req: any, res: Response) => {
  try {
    const { status, search, page = 1, pageSize = 20, include_task_customers = true } = req.query;
    const agentId = req.user.id;
    const pageNum = parseInt(page as string);
    const sizeNum = parseInt(pageSize as string);
    const offset = (pageNum - 1) * sizeNum;
    
    // 获取当前客服的数据权限
    const userResult = await query('SELECT data_source FROM users WHERE id = $1', [agentId]);
    const dataAccessType = userResult.rows[0]?.data_source || 'mock';
    
    // 构建查询 - 使用UNION合并直接分配的客户和任务分配的客户
    let querySql = '';
    const params: any[] = [agentId, dataAccessType];
    
    if (include_task_customers !== 'false') {
      // 包含任务分配的客户
      querySql = `
        SELECT DISTINCT c.*, 
          CASE WHEN tc.customer_id IS NOT NULL THEN 'task' ELSE 'direct' END as source_type,
          tc.task_id,
          t.title as task_title
        FROM customers c
        LEFT JOIN task_customers tc ON c.id = tc.customer_id AND tc.status = 'pending'
        LEFT JOIN tasks t ON tc.task_id = t.id AND t.assigned_to = $1
        WHERE (c.assigned_to = $1 AND (c.data_source = $2 OR c.data_source IS NULL))
           OR (t.assigned_to = $1 AND tc.status = 'pending')
      `;
    } else {
      // 只包含直接分配的客户
      querySql = `
        SELECT c.*, 'direct' as source_type, NULL as task_id, NULL as task_title
        FROM customers c
        WHERE c.assigned_to = $1 AND (c.data_source = $2 OR c.data_source IS NULL)
      `;
    }
    
    // 添加状态过滤
    if (status) {
      if (status === 'pending') {
        querySql += ` AND c.status IN ('pending', 'contacted', 'new')`;
      } else if (status === 'completed') {
        querySql += ` AND c.status IN ('converted', 'not_interested')`;
      } else {
        params.push(status);
        querySql += ` AND c.status = $${params.length}`;
      }
    }
    
    // 添加搜索过滤
    if (search) {
      params.push(`%${search.toString().toLowerCase()}%`);
      querySql += ` AND (LOWER(c.name) LIKE $${params.length} OR c.phone LIKE $${params.length})`;
    }
    
    // 获取总数
    const countResult = await query(`SELECT COUNT(*) as total FROM (${querySql}) as subquery`, params);
    const total = parseInt(countResult.rows[0].total);
    
    // 添加排序和分页
    querySql += ` ORDER BY c.created_at DESC LIMIT $${params.length + 1} OFFSET $${params.length + 2}`;
    params.push(sizeNum, offset);
    
    const result = await query(querySql, params);
    let customers = result.rows;
    
    // 获取通话记录
    const calls = await query('SELECT * FROM calls WHERE agent_id = $1', [agentId]);
    const callsByCustomer = new Map();
    calls.rows.forEach((c: any) => {
      if (!callsByCustomer.has(c.customer_id)) {
        callsByCustomer.set(c.customer_id, []);
      }
      callsByCustomer.get(c.customer_id).push(c);
    });
    
    // 添加通话信息
    customers = customers.map((c: any) => {
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
        page: pageNum,
        pageSize: sizeNum,
        totalPages: Math.ceil(total / sizeNum)
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
