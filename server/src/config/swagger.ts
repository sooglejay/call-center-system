import swaggerJsdoc from 'swagger-jsdoc';

const options: swaggerJsdoc.Options = {
  definition: {
    openapi: '3.0.0',
    info: {
      title: '呼叫中心系统 API',
      version: '1.0.0',
      description: '呼叫中心系统后端 API 文档，支持 Web 管理后台和 Android 移动端',
      contact: {
        name: 'API Support',
        email: 'support@example.com'
      }
    },
    servers: [
      {
        url: '/api',
        description: '当前服务器'
      }
    ],
    components: {
      securitySchemes: {
        bearerAuth: {
          type: 'http',
          scheme: 'bearer',
          bearerFormat: 'JWT'
        }
      },
      schemas: {
        // 用户相关
        User: {
          type: 'object',
          properties: {
            id: { type: 'integer', example: 1 },
            username: { type: 'string', example: 'agent001' },
            real_name: { type: 'string', example: '张三' },
            role: { type: 'string', enum: ['admin', 'agent'], example: 'agent' },
            phone: { type: 'string', example: '13800138000' },
            email: { type: 'string', example: 'agent@example.com' },
            status: { type: 'string', enum: ['active', 'inactive'], example: 'active' },
            data_access_type: { type: 'string', example: 'real' },
            created_at: { type: 'string', format: 'date-time' }
          }
        },
        // 客户相关
        Customer: {
          type: 'object',
          properties: {
            id: { type: 'integer', example: 1 },
            name: { type: 'string', example: '李四' },
            phone: { type: 'string', example: '13900139000' },
            email: { type: 'string', example: 'lisi@example.com' },
            company: { type: 'string', example: '测试公司' },
            address: { type: 'string', example: '北京市朝阳区' },
            notes: { type: 'string', example: 'VIP客户' },
            status: { type: 'string', enum: ['pending', 'contacted', 'converted', 'not_interested', 'completed'], example: 'pending' },
            priority: { type: 'integer', example: 1 },
            assigned_to: { type: 'integer', example: 1, nullable: true },
            assigned_to_name: { type: 'string', example: '张三' },
            assigned_agent: { $ref: '#/components/schemas/User' },
            created_at: { type: 'string', format: 'date-time' },
            updated_at: { type: 'string', format: 'date-time' }
          }
        },
        // 通话记录
        CallRecord: {
          type: 'object',
          properties: {
            id: { type: 'integer', example: 1 },
            customer_id: { type: 'integer', example: 1 },
            customer: { $ref: '#/components/schemas/Customer' },
            agent_id: { type: 'integer', example: 1 },
            agent: { $ref: '#/components/schemas/User' },
            phone: { type: 'string', example: '13900139000' },
            direction: { type: 'string', enum: ['inbound', 'outbound'], example: 'outbound' },
            status: { type: 'string', enum: ['pending', 'calling', 'connected', 'completed', 'failed', 'no_answer', 'busy'], example: 'completed' },
            duration: { type: 'integer', example: 120, description: '通话时长(秒)' },
            notes: { type: 'string', example: '客户有意向' },
            recording: { type: 'string', example: 'https://example.com/recording.mp3' },
            created_at: { type: 'string', format: 'date-time' }
          }
        },
        // 任务
        Task: {
          type: 'object',
          properties: {
            id: { type: 'integer', example: 1 },
            title: { type: 'string', example: '本周回访任务' },
            description: { type: 'string', example: '回访上周意向客户' },
            status: { type: 'string', enum: ['pending', 'in_progress', 'completed', 'cancelled'], example: 'pending' },
            priority: { type: 'integer', example: 1 },
            assigned_to: { type: 'integer', example: 1 },
            assigned_agent: { $ref: '#/components/schemas/User' },
            due_date: { type: 'string', format: 'date' },
            created_at: { type: 'string', format: 'date-time' },
            updated_at: { type: 'string', format: 'date-time' }
          }
        },
        // 统计数据
        Stats: {
          type: 'object',
          properties: {
            total_calls: { type: 'integer', example: 100 },
            successful_calls: { type: 'integer', example: 60 },
            connected_calls: { type: 'integer', example: 60 },
            failed_calls: { type: 'integer', example: 40 },
            total_duration: { type: 'integer', example: 3600, description: '总通话时长(秒)' },
            avg_duration: { type: 'integer', example: 36, description: '平均通话时长(秒)' },
            connection_rate: { type: 'number', example: 0.6, description: '接通率' },
            pending_customers: { type: 'integer', example: 50 },
            completed_customers: { type: 'integer', example: 30 }
          }
        },
        // 仪表盘统计
        DashboardStats: {
          type: 'object',
          properties: {
            total_customers: { type: 'integer', example: 1000 },
            total_calls: { type: 'integer', example: 500 },
            connection_rate: { type: 'number', example: 0.65 },
            active_agents: { type: 'integer', example: 10 },
            today_calls: { type: 'integer', example: 50 },
            today_duration: { type: 'integer', example: 1800 },
            today_success_rate: { type: 'number', example: 0.7 },
            pending_customers: { type: 'integer', example: 100 },
            total_agents: { type: 'integer', example: 15 },
            agent_ranking: {
              type: 'array',
              items: { $ref: '#/components/schemas/AgentRanking' }
            },
            recent_calls: {
              type: 'array',
              items: { $ref: '#/components/schemas/CallRecord' }
            },
            trend: {
              type: 'array',
              items: {
                type: 'object',
                properties: {
                  date: { type: 'string', format: 'date' },
                  total_calls: { type: 'integer' },
                  connected_calls: { type: 'integer' }
                }
              }
            }
          }
        },
        // 客服排行
        AgentRanking: {
          type: 'object',
          properties: {
            agent_id: { type: 'integer', example: 1 },
            agent_name: { type: 'string', example: '张三' },
            total_calls: { type: 'integer', example: 100 },
            successful_calls: { type: 'integer', example: 60 },
            total_duration: { type: 'integer', example: 3600 },
            success_rate: { type: 'number', example: 0.6 }
          }
        },
        // 分页响应
        PaginatedResponse: {
          type: 'object',
          properties: {
            data: { type: 'array', items: {} },
            total: { type: 'integer', example: 100 },
            page: { type: 'integer', example: 1 },
            page_size: { type: 'integer', example: 20 },
            total_pages: { type: 'integer', example: 5 }
          }
        },
        // 错误响应
        Error: {
          type: 'object',
          properties: {
            error: { type: 'string', example: '错误信息' },
            code: { type: 'string', example: 'VALIDATION_ERROR' },
            details: { type: 'object', example: {} }
          }
        },
        // 登录请求
        LoginRequest: {
          type: 'object',
          required: ['username', 'password'],
          properties: {
            username: { type: 'string', example: 'admin' },
            password: { type: 'string', example: 'password123' }
          }
        },
        // 登录响应
        LoginResponse: {
          type: 'object',
          properties: {
            token: { type: 'string', example: 'eyJhbGciOiJIUzI1NiIs...' },
            user: { $ref: '#/components/schemas/User' }
          }
        },
        // 创建用户请求
        CreateUserRequest: {
          type: 'object',
          required: ['username', 'password', 'real_name'],
          properties: {
            username: { type: 'string', example: 'agent001' },
            password: { type: 'string', example: 'password123' },
            real_name: { type: 'string', example: '张三' },
            role: { type: 'string', enum: ['admin', 'agent'], example: 'agent' },
            phone: { type: 'string', example: '13800138000' },
            email: { type: 'string', example: 'agent@example.com' },
            data_access_type: { type: 'string', example: 'real' }
          }
        },
        // 批量分配请求
        BatchAssignRequest: {
          type: 'object',
          required: ['customer_ids', 'assigned_to'],
          properties: {
            customer_ids: { 
              type: 'array', 
              items: { type: 'integer' },
              example: [1, 2, 3]
            },
            assigned_to: { type: 'integer', example: 1 }
          }
        }
      }
    },
    tags: [
      { name: 'Auth', description: '认证相关接口' },
      { name: 'Users', description: '用户管理接口' },
      { name: 'Customers', description: '客户管理接口' },
      { name: 'Calls', description: '通话记录接口' },
      { name: 'Tasks', description: '任务管理接口' },
      { name: 'Stats', description: '统计数据接口' },
      { name: 'DataImport', description: '数据导入接口' }
    ]
  },
  apis: ['./src/routes/*.ts', './src/docs/*.yaml']
};

export const swaggerSpec = swaggerJsdoc(options);
