import { Request, Response, NextFunction } from 'express';

/**
 * 错误代码枚举
 */
export enum ErrorCode {
  // 通用错误
  UNKNOWN_ERROR = 'UNKNOWN_ERROR',
  VALIDATION_ERROR = 'VALIDATION_ERROR',
  UNAUTHORIZED = 'UNAUTHORIZED',
  FORBIDDEN = 'FORBIDDEN',
  NOT_FOUND = 'NOT_FOUND',
  CONFLICT = 'CONFLICT',
  
  // 认证相关
  INVALID_CREDENTIALS = 'INVALID_CREDENTIALS',
  TOKEN_EXPIRED = 'TOKEN_EXPIRED',
  TOKEN_INVALID = 'TOKEN_INVALID',
  
  // 用户相关
  USER_NOT_FOUND = 'USER_NOT_FOUND',
  USER_ALREADY_EXISTS = 'USER_ALREADY_EXISTS',
  INVALID_ROLE = 'INVALID_ROLE',
  
  // 客户相关
  CUSTOMER_NOT_FOUND = 'CUSTOMER_NOT_FOUND',
  PHONE_ALREADY_EXISTS = 'PHONE_ALREADY_EXISTS',
  
  // 任务相关
  TASK_NOT_FOUND = 'TASK_NOT_FOUND',
  
  // 通话相关
  CALL_NOT_FOUND = 'CALL_NOT_FOUND',
  
  // 数据导入相关
  INVALID_FILE_FORMAT = 'INVALID_FILE_FORMAT',
  IMPORT_FAILED = 'IMPORT_FAILED'
}

/**
 * API 错误类
 */
export class ApiError extends Error {
  public readonly code: ErrorCode;
  public readonly statusCode: number;
  public readonly details?: any;

  constructor(
    message: string,
    code: ErrorCode = ErrorCode.UNKNOWN_ERROR,
    statusCode: number = 500,
    details?: any
  ) {
    super(message);
    this.code = code;
    this.statusCode = statusCode;
    this.details = details;
    
    // 确保原型链正确
    Object.setPrototypeOf(this, ApiError.prototype);
  }

  /**
   * 创建验证错误
   */
  static validationError(message: string, details?: any): ApiError {
    return new ApiError(message, ErrorCode.VALIDATION_ERROR, 400, details);
  }

  /**
   * 创建未授权错误
   */
  static unauthorized(message: string = '未授权访问'): ApiError {
    return new ApiError(message, ErrorCode.UNAUTHORIZED, 401);
  }

  /**
   * 创建禁止访问错误
   */
  static forbidden(message: string = '禁止访问'): ApiError {
    return new ApiError(message, ErrorCode.FORBIDDEN, 403);
  }

  /**
   * 创建资源未找到错误
   */
  static notFound(resource: string = '资源'): ApiError {
    return new ApiError(`${resource}不存在`, ErrorCode.NOT_FOUND, 404);
  }

  /**
   * 创建冲突错误
   */
  static conflict(message: string): ApiError {
    return new ApiError(message, ErrorCode.CONFLICT, 409);
  }
}

/**
 * 统一错误响应格式
 */
interface ErrorResponse {
  success: false;
  error: string;
  code: ErrorCode;
  details?: any;
  timestamp: string;
  path: string;
}

/**
 * 成功响应格式
 */
interface SuccessResponse<T> {
  success: true;
  data: T;
  message?: string;
  timestamp: string;
}

/**
 * 分页响应格式
 */
interface PaginatedResponse<T> {
  success: true;
  data: T[];
  total: number;
  page: number;
  page_size: number;
  total_pages: number;
  timestamp: string;
}

/**
 * 响应辅助类
 */
export class ResponseHelper {
  /**
   * 成功响应
   */
  static success<T>(res: Response, data: T, message?: string, statusCode: number = 200): void {
    const response: SuccessResponse<T> = {
      success: true,
      data,
      message,
      timestamp: new Date().toISOString()
    };
    res.status(statusCode).json(response);
  }

  /**
   * 分页响应
   */
  static paginated<T>(
    res: Response,
    data: T[],
    total: number,
    page: number,
    pageSize: number
  ): void {
    const response: PaginatedResponse<T> = {
      success: true,
      data,
      total,
      page,
      page_size: pageSize,
      total_pages: Math.ceil(total / pageSize),
      timestamp: new Date().toISOString()
    };
    res.json(response);
  }

  /**
   * 创建成功响应 (201)
   */
  static created<T>(res: Response, data: T, message?: string): void {
    this.success(res, data, message, 201);
  }

  /**
   * 无内容响应 (204)
   */
  static noContent(res: Response): void {
    res.status(204).send();
  }

  /**
   * 错误响应
   */
  static error(
    res: Response,
    error: ApiError | Error,
    path: string
  ): void {
    let statusCode = 500;
    let code = ErrorCode.UNKNOWN_ERROR;
    let message = '服务器内部错误';
    let details = undefined;

    if (error instanceof ApiError) {
      statusCode = error.statusCode;
      code = error.code;
      message = error.message;
      details = error.details;
    } else {
      // 记录未知错误
      console.error('Unhandled error:', error);
    }

    const response: ErrorResponse = {
      success: false,
      error: message,
      code,
      details,
      timestamp: new Date().toISOString(),
      path
    };

    res.status(statusCode).json(response);
  }
}

/**
 * 错误处理中间件
 */
export const errorHandler = (
  err: Error | ApiError,
  req: Request,
  res: Response,
  next: NextFunction
): void => {
  ResponseHelper.error(res, err, req.path);
};

/**
 * 异步处理器包装函数
 * 自动捕获异步错误并传递给错误处理中间件
 */
export const asyncHandler = (
  fn: (req: Request, res: Response, next: NextFunction) => Promise<void>
) => {
  return (req: Request, res: Response, next: NextFunction) => {
    Promise.resolve(fn(req, res, next)).catch(next);
  };
};

/**
 * 404 处理中间件
 */
export const notFoundHandler = (req: Request, res: Response): void => {
  ResponseHelper.error(
    res,
    ApiError.notFound(`路由 ${req.method} ${req.path}`),
    req.path
  );
};
