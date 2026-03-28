import { Request, Response, NextFunction } from 'express';
import { ApiError, ErrorCode } from './errorHandler';

/**
 * 验证规则类型
 */
type ValidationType = 'string' | 'number' | 'boolean' | 'array' | 'object' | 'email' | 'phone' | 'date';

/**
 * 字段验证规则
 */
interface FieldRule {
  type?: ValidationType;
  required?: boolean;
  minLength?: number;
  maxLength?: number;
  min?: number;
  max?: number;
  pattern?: RegExp;
  enum?: (string | number)[];
  custom?: (value: any, body: any) => boolean | string;
  message?: string;
}

/**
 * 验证规则
 */
interface ValidationRules {
  body?: Record<string, FieldRule>;
  query?: Record<string, FieldRule>;
  params?: Record<string, FieldRule>;
}

/**
 * 验证结果
 */
interface ValidationResult {
  valid: boolean;
  errors: Record<string, string>;
}

/**
 * 类型验证函数
 */
const typeValidators: Record<ValidationType, (value: any) => boolean> = {
  string: (v) => typeof v === 'string',
  number: (v) => typeof v === 'number' && !isNaN(v),
  boolean: (v) => typeof v === 'boolean',
  array: (v) => Array.isArray(v),
  object: (v) => typeof v === 'object' && v !== null && !Array.isArray(v),
  email: (v) => typeof v === 'string' && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v),
  phone: (v) => typeof v === 'string' && /^1[3-9]\d{9}$/.test(v),
  date: (v) => !isNaN(Date.parse(v))
};

/**
 * 验证单个字段
 */
function validateField(
  value: any,
  field: string,
  rule: FieldRule,
  context: any
): string | null {
  // 必填验证
  if (rule.required && (value === undefined || value === null || value === '')) {
    return rule.message || `${field}不能为空`;
  }

  // 非必填且为空，跳过其他验证
  if (value === undefined || value === null || value === '') {
    return null;
  }

  // 类型验证
  if (rule.type) {
    const validator = typeValidators[rule.type];
    if (!validator(value)) {
      return rule.message || `${field}类型不正确，期望 ${rule.type}`;
    }
  }

  // 字符串长度验证
  if (rule.type === 'string' || typeof value === 'string') {
    if (rule.minLength !== undefined && value.length < rule.minLength) {
      return rule.message || `${field}长度不能少于 ${rule.minLength} 个字符`;
    }
    if (rule.maxLength !== undefined && value.length > rule.maxLength) {
      return rule.message || `${field}长度不能超过 ${rule.maxLength} 个字符`;
    }
  }

  // 数字范围验证
  if (rule.type === 'number' || typeof value === 'number') {
    if (rule.min !== undefined && value < rule.min) {
      return rule.message || `${field}不能小于 ${rule.min}`;
    }
    if (rule.max !== undefined && value > rule.max) {
      return rule.message || `${field}不能大于 ${rule.max}`;
    }
  }

  // 数组长度验证
  if (rule.type === 'array' || Array.isArray(value)) {
    if (rule.minLength !== undefined && value.length < rule.minLength) {
      return rule.message || `${field}至少需要 ${rule.minLength} 个元素`;
    }
    if (rule.maxLength !== undefined && value.length > rule.maxLength) {
      return rule.message || `${field}最多只能有 ${rule.maxLength} 个元素`;
    }
  }

  // 枚举值验证
  if (rule.enum && !rule.enum.includes(value)) {
    return rule.message || `${field}必须是以下值之一: ${rule.enum.join(', ')}`;
  }

  // 正则验证
  if (rule.pattern && !rule.pattern.test(String(value))) {
    return rule.message || `${field}格式不正确`;
  }

  // 自定义验证
  if (rule.custom) {
    const result = rule.custom(value, context);
    if (result !== true) {
      return typeof result === 'string' ? result : rule.message || `${field}验证失败`;
    }
  }

  return null;
}

/**
 * 验证对象
 */
function validateObject(
  data: any,
  rules: Record<string, FieldRule>,
  context: any
): ValidationResult {
  const errors: Record<string, string> = {};

  for (const [field, rule] of Object.entries(rules)) {
    const error = validateField(data[field], field, rule, context);
    if (error) {
      errors[field] = error;
    }
  }

  return {
    valid: Object.keys(errors).length === 0,
    errors
  };
}

/**
 * 创建验证中间件
 */
export function validate(rules: ValidationRules) {
  return (req: Request, res: Response, next: NextFunction) => {
    const errors: Record<string, string> = {};

    // 验证 body
    if (rules.body) {
      const result = validateObject(req.body, rules.body, req.body);
      Object.assign(errors, result.errors);
    }

    // 验证 query
    if (rules.query) {
      const result = validateObject(req.query, rules.query, req.query);
      Object.assign(errors, result.errors);
    }

    // 验证 params
    if (rules.params) {
      const result = validateObject(req.params, rules.params, req.params);
      Object.assign(errors, result.errors);
    }

    if (Object.keys(errors).length > 0) {
      throw ApiError.validationError('请求参数验证失败', errors);
    }

    next();
  };
}

/**
 * 常用验证规则
 */
export const commonRules = {
  // 用户名: 3-20位字母数字下划线
  username: (): FieldRule => ({
    required: true,
    type: 'string',
    minLength: 3,
    maxLength: 20,
    pattern: /^[a-zA-Z0-9_]+$/,
    message: '用户名为3-20位字母、数字或下划线'
  }),

  // 密码: 6-50位
  password: (): FieldRule => ({
    required: true,
    type: 'string',
    minLength: 6,
    maxLength: 50,
    message: '密码长度为6-50位'
  }),

  // 姓名: 2-20位
  realName: (): FieldRule => ({
    required: true,
    type: 'string',
    minLength: 2,
    maxLength: 20,
    message: '姓名长度为2-20位'
  }),

  // 手机号
  phone: (): FieldRule => ({
    type: 'phone',
    message: '请输入正确的手机号'
  }),

  // 邮箱
  email: (): FieldRule => ({
    type: 'email',
    message: '请输入正确的邮箱地址'
  }),

  // 角色
  role: (): FieldRule => ({
    required: true,
    enum: ['admin', 'agent'],
    message: '角色必须为 admin 或 agent'
  }),

  // 分页页码
  page: (): FieldRule => ({
    type: 'number',
    min: 1,
    message: '页码必须大于0'
  }),

  // 分页大小
  pageSize: (): FieldRule => ({
    type: 'number',
    min: 1,
    max: 100,
    message: '每页数量必须在1-100之间'
  }),

  // ID
  id: (): FieldRule => ({
    required: true,
    type: 'number',
    min: 1,
    message: 'ID必须为正整数'
  }),

  // 客户姓名
  customerName: (): FieldRule => ({
    required: true,
    type: 'string',
    minLength: 1,
    maxLength: 50,
    message: '客户姓名长度为1-50位'
  }),

  // 客户电话
  customerPhone: (): FieldRule => ({
    required: true,
    type: 'string',
    minLength: 7,
    maxLength: 20,
    message: '电话号码长度为7-20位'
  }),

  // 任务标题
  taskTitle: (): FieldRule => ({
    required: true,
    type: 'string',
    minLength: 1,
    maxLength: 100,
    message: '任务标题长度为1-100位'
  }),

  // 任务状态
  taskStatus: (): FieldRule => ({
    enum: ['pending', 'in_progress', 'completed', 'cancelled'],
    message: '任务状态无效'
  }),

  // 客户状态
  customerStatus: (): FieldRule => ({
    enum: ['pending', 'contacted', 'converted', 'not_interested', 'completed'],
    message: '客户状态无效'
  })
};

/**
 * 常用验证中间件
 */
export const validateLogin = validate({
  body: {
    username: commonRules.username(),
    password: { required: true, type: 'string', minLength: 1 }
  }
});

export const validateCreateUser = validate({
  body: {
    username: commonRules.username(),
    password: commonRules.password(),
    real_name: commonRules.realName(),
    role: commonRules.role(),
    phone: commonRules.phone(),
    email: commonRules.email()
  }
});

export const validateCreateCustomer = validate({
  body: {
    name: commonRules.customerName(),
    phone: commonRules.customerPhone(),
    email: commonRules.email(),
    company: { type: 'string', maxLength: 100 },
    address: { type: 'string', maxLength: 200 },
    notes: { type: 'string', maxLength: 500 },
    status: commonRules.customerStatus(),
    assigned_to: { type: 'number', min: 1 }
  }
});

export const validateCreateTask = validate({
  body: {
    title: commonRules.taskTitle(),
    description: { type: 'string', maxLength: 500 },
    assigned_to: { type: 'number', min: 1 },
    priority: { type: 'number', min: 1, max: 5 },
    due_date: { type: 'date' }
  }
});

export const validatePagination = validate({
  query: {
    page: { type: 'number', min: 1 },
    pageSize: { type: 'number', min: 1, max: 100 }
  }
});

export const validateId = validate({
  params: {
    id: commonRules.id()
  }
});

export const validateBatchAssign = validate({
  body: {
    customer_ids: { 
      required: true, 
      type: 'array',
      minLength: 1,
      custom: (value) => Array.isArray(value) && value.every(id => Number.isInteger(id) && id > 0)
        ? true
        : 'customer_ids 必须是正整数数组'
    },
    assigned_to: commonRules.id()
  }
});
