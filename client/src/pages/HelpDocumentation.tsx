import { Card, Tabs, Typography, Steps, Alert, Divider } from 'antd';
import {
  UserOutlined,
  TeamOutlined,
  PhoneOutlined,
  FileTextOutlined,
  BarChartOutlined,
  SettingOutlined,
  DashboardOutlined,
  CustomerServiceOutlined,
  QuestionCircleOutlined
} from '@ant-design/icons';

const { Title, Paragraph, Text } = Typography;

export default function HelpDocumentation() {
  return (
    <div style={{ maxWidth: 1200, margin: '0 auto' }}>
      <Title level={2}>
        <QuestionCircleOutlined style={{ marginRight: 12 }} />
        系统使用说明
      </Title>
      <Paragraph type="secondary">
        欢迎使用智能呼叫中心系统！本文档将帮助您快速了解系统的使用方法。请根据您的角色选择对应的说明查看。
      </Paragraph>

      <Divider />

      <Tabs
        type="card"
        items={[
          {
            key: 'admin',
            label: (
              <span>
                <TeamOutlined style={{ marginRight: 8 }} />
                管理员使用指南
              </span>
            ),
            children: <AdminGuide />,
          },
          {
            key: 'agent',
            label: (
              <span>
                <CustomerServiceOutlined style={{ marginRight: 8 }} />
                客服坐席使用指南
              </span>
            ),
            children: <AgentGuide />,
          },
        ]}
      />
    </div>
  );
}

// ==================== 管理员使用指南 ====================
function AdminGuide() {
  return (
    <div>
      <Alert
        message="管理员角色说明"
        description="管理员负责系统的整体管理，包括人员管理、客户数据管理、任务分配和系统监控等工作。"
        type="info"
        showIcon
        style={{ marginBottom: 24 }}
      />

      <Card title="一、系统登录" style={{ marginBottom: 24 }}>
        <Steps
          direction="vertical"
          items={[
            {
              title: '访问登录页面',
              description: '打开浏览器，输入系统地址访问登录页面。',
            },
            {
              title: '输入账号信息',
              description: (
                <div>
                  <p>输入管理员账号和密码：</p>
                  <ul>
                    <li>默认管理员账号：<Text code>admin</Text></li>
                    <li>默认密码：<Text code>admin123</Text></li>
                  </ul>
                </div>
              ),
            },
            {
              title: '进入管理后台',
              description: '登录成功后，系统将自动跳转到管理员仪表盘页面。',
            },
          ]}
        />
      </Card>

      <Card title="二、仪表板功能" style={{ marginBottom: 24 }}>
        <Paragraph>
          <DashboardOutlined style={{ marginRight: 8 }} />
          <strong>仪表板</strong>是管理员的日常工作入口，提供以下功能：
        </Paragraph>
        <ul>
          <li>查看今日通话统计、客户总数、接通率等关键指标</li>
          <li>查看客服人员的在线状态和工作情况</li>
          <li>查看最近通话记录和待处理任务</li>
          <li>快速导航到各个管理模块</li>
        </ul>
      </Card>

      <Card title="三、人员管理" style={{ marginBottom: 24 }}>
        <Paragraph>
          <TeamOutlined style={{ marginRight: 8 }} />
          <strong>人员管理</strong>模块用于管理系统中的客服人员：
        </Paragraph>
        <Steps
          direction="vertical"
          size="small"
          items={[
            {
              title: '添加客服',
              description: '点击"添加人员"按钮，填写客服的基本信息（姓名、账号、密码等），选择角色为"客服"。',
            },
            {
              title: '编辑信息',
              description: '在列表中找到需要修改的客服，点击"编辑"按钮修改其信息。',
            },
            {
              title: '重置密码',
              description: '如客服忘记密码，可点击"重置密码"为其设置新密码。',
            },
            {
              title: '启用/禁用',
              description: '可对客服账号进行启用或禁用操作，禁用后该客服将无法登录系统。',
            },
          ]}
        />
      </Card>

      <Card title="四、客户管理" style={{ marginBottom: 24 }}>
        <Paragraph>
          <UserOutlined style={{ marginRight: 8 }} />
          <strong>客户管理</strong>模块用于管理客户数据：
        </Paragraph>
        <ul>
          <li>
            <strong>导入客户</strong>：支持批量导入客户数据，可通过Excel文件导入
          </li>
          <li>
            <strong>添加客户</strong>：手动添加单个客户，填写姓名、电话、公司等基本信息
          </li>
          <li>
            <strong>分配客服</strong>：将客户分配给指定的客服人员进行跟进
          </li>
          <li>
            <strong>客户状态</strong>：查看客户的拨打状态（待拨打、已接通、未接通等）
          </li>
          <li>
            <strong>搜索筛选</strong>：支持按姓名、电话、状态等条件搜索客户
          </li>
        </ul>
      </Card>

      <Card title="五、任务分配" style={{ marginBottom: 24 }}>
        <Paragraph>
          <FileTextOutlined style={{ marginRight: 8 }} />
          <strong>任务分配</strong>模块用于创建和管理工作任务：
        </Paragraph>
        <Steps
          direction="vertical"
          size="small"
          items={[
            {
              title: '创建任务',
              description: '点击"新建任务"，填写任务标题、描述，选择执行客服。',
            },
            {
              title: '分配客户',
              description: '为任务选择需要拨打的客户，可按姓氏首字母筛选或搜索选择。',
            },
            {
              title: '设置优先级',
              description: '设置任务优先级（普通、高、紧急），帮助客服合理安排工作。',
            },
            {
              title: '跟踪进度',
              description: '在任务列表中查看任务完成进度，包括已拨打客户数和完成率。',
            },
          ]}
        />
      </Card>

      <Card title="六、监控统计" style={{ marginBottom: 24 }}>
        <Paragraph>
          <BarChartOutlined style={{ marginRight: 8 }} />
          <strong>监控统计</strong>模块提供全面的数据统计功能：
        </Paragraph>
        <ul>
          <li>
            <strong>整体统计</strong>：查看系统整体的通话数据、接通率、通话时长等
          </li>
          <li>
            <strong>客服排名</strong>：查看客服人员的工作排名，包括通话数量、时长等
          </li>
          <li>
            <strong>趋势分析</strong>：查看通话量的趋势变化，支持按日、周、月统计
          </li>
          <li>
            <strong>数据导出</strong>：支持导出统计数据用于进一步分析
          </li>
        </ul>
      </Card>

      <Card title="七、系统配置" style={{ marginBottom: 24 }}>
        <Paragraph>
          <SettingOutlined style={{ marginRight: 8 }} />
          <strong>系统配置</strong>模块用于配置系统参数：
        </Paragraph>
        <ul>
          <li>
            <strong>Twilio配置</strong>：配置Twilio电话服务的账号信息
          </li>
          <li>
            <strong>拨号设置</strong>：设置默认拨号间隔、超时时间等参数
          </li>
          <li>
            <strong>数据权限</strong>：配置客服的数据访问权限范围
          </li>
        </ul>
      </Card>

      <Card title="八、常见问题">
        <ul>
          <li>
            <strong>Q: 如何查看客服的工作情况？</strong>
            <br />
            A: 在"监控统计"页面可以查看各客服的通话记录和统计数据。
          </li>
          <li style={{ marginTop: 12 }}>
            <strong>Q: 客户数据如何导入？</strong>
            <br />
            A: 在"客户管理"页面点击"导入"按钮，按照模板格式上传Excel文件即可。
          </li>
          <li style={{ marginTop: 12 }}>
            <strong>Q: 任务可以分配给多个客服吗？</strong>
            <br />
            A: 每个任务只能分配给一个客服，但可以为不同客服创建相同客户的任务。
          </li>
        </ul>
      </Card>
    </div>
  );
}

// ==================== 客服坐席使用指南 ====================
function AgentGuide() {
  return (
    <div>
      <Alert
        message="客服坐席角色说明"
        description="客服坐席是系统的日常使用人员，主要负责拨打电话、跟进客户、记录通话信息等工作。"
        type="info"
        showIcon
        style={{ marginBottom: 24 }}
      />

      <Card title="一、系统登录" style={{ marginBottom: 24 }}>
        <Steps
          direction="vertical"
          items={[
            {
              title: '访问登录页面',
              description: '打开浏览器，输入系统地址访问登录页面。',
            },
            {
              title: '输入账号信息',
              description: (
                <div>
                  <p>输入您的客服账号和密码：</p>
                  <ul>
                    <li>账号：由管理员分配</li>
                    <li>默认密码示例：<Text code>agent123</Text></li>
                  </ul>
                </div>
              ),
            },
            {
              title: '进入工作台',
              description: '登录成功后，系统将自动跳转到客服工作台页面。',
            },
          ]}
        />
      </Card>

      <Card title="二、工作台功能" style={{ marginBottom: 24 }}>
        <Paragraph>
          <DashboardOutlined style={{ marginRight: 8 }} />
          <strong>工作台</strong>是客服的日常工作入口，提供以下功能：
        </Paragraph>
        <ul>
          <li>查看今日待拨打客户列表</li>
          <li>查看我的任务和进度</li>
          <li>查看今日通话统计数据</li>
          <li>快速开始拨打电话</li>
        </ul>
      </Card>

      <Card title="三、拨打电话" style={{ marginBottom: 24 }}>
        <Paragraph>
          <PhoneOutlined style={{ marginRight: 8 }} />
          系统提供<strong>两种拨号方式</strong>：
        </Paragraph>

        <Title level={5}>1. 手动拨号</Title>
        <Steps
          direction="vertical"
          size="small"
          items={[
            {
              title: '选择客户',
              description: '在"电话列表"页面，点击客户右侧的电话图标进行拨打。',
            },
            {
              title: '记录结果',
              description: '通话结束后，在弹出的对话框中选择通话结果（接通、未接通、关机、拒接等）。',
            },
            {
              title: '添加备注',
              description: '填写通话备注，记录客户的需求和反馈信息。',
            },
          ]}
        />

        <Divider />

        <Title level={5}>2. 自动拨号</Title>
        <Steps
          direction="vertical"
          size="small"
          items={[
            {
              title: '启动自动拨号',
              description: '在"电话列表"页面，点击"自动拨号"按钮，设置拨号间隔后启动。',
            },
            {
              title: '自动拨打',
              description: '系统会按照列表顺序自动拨打客户电话，您只需接听并沟通即可。',
            },
            {
              title: '记录结果',
              description: '每个电话结束后，系统会暂停等待您记录通话结果。',
            },
            {
              title: '继续下一个',
              description: '记录完成后，系统自动拨打下一个客户。',
            },
          ]}
        />
      </Card>

      <Card title="四、客户列表" style={{ marginBottom: 24 }}>
        <Paragraph>
          <UserOutlined style={{ marginRight: 8 }} />
          <strong>电话列表</strong>页面显示分配给您的所有客户：
        </Paragraph>
        <ul>
          <li>
            <strong>查看客户信息</strong>：点击客户姓名可查看详细信息（电话、公司、地址等）
          </li>
          <li>
            <strong>搜索客户</strong>：支持按姓名、电话搜索客户
          </li>
          <li>
            <strong>筛选状态</strong>：可按拨打状态筛选（待拨打、已接通、未接通）
          </li>
          <li>
            <strong>排序</strong>：支持按优先级、创建时间等排序
          </li>
        </ul>
      </Card>

      <Card title="五、通信记录" style={{ marginBottom: 24 }}>
        <Paragraph>
          在<strong>通信记录</strong>页面，您可以：
        </Paragraph>
        <ul>
          <li>查看所有通话历史记录</li>
          <li>查看通话时长和通话结果</li>
          <li>查看和编辑通话备注</li>
          <li>按日期筛选通话记录</li>
          <li>回放通话录音（如已开启录音功能）</li>
        </ul>
      </Card>

      <Card title="六、我的业绩" style={{ marginBottom: 24 }}>
        <Paragraph>
          <BarChartOutlined style={{ marginRight: 8 }} />
          <strong>我的业绩</strong>页面展示您的工作数据：
        </Paragraph>
        <ul>
          <li>
            <strong>今日统计</strong>：今日通话次数、通话时长、接通率
          </li>
          <li>
            <strong>本周统计</strong>：本周累计通话数据
          </li>
          <li>
            <strong>本月统计</strong>：本月累计通话数据
          </li>
          <li>
            <strong>排名情况</strong>：您在团队中的排名位置
          </li>
        </ul>
      </Card>

      <Card title="七、拨号设置" style={{ marginBottom: 24 }}>
        <Paragraph>
          <SettingOutlined style={{ marginRight: 8 }} />
          在<strong>拨号设置</strong>页面，您可以：
        </Paragraph>
        <ul>
          <li>
            <strong>设置拨号间隔</strong>：设置自动拨号时两个电话之间的等待时间
          </li>
          <li>
            <strong>设置超时时间</strong>：设置无人接听时的等待时间
          </li>
          <li>
            <strong>开启/关闭录音</strong>：设置是否自动录制通话
          </li>
        </ul>
      </Card>

      <Card title="八、任务处理" style={{ marginBottom: 24 }}>
        <Paragraph>
          <FileTextOutlined style={{ marginRight: 8 }} />
          管理员分配的任务会显示在工作台：
        </Paragraph>
        <Steps
          direction="vertical"
          size="small"
          items={[
            {
              title: '查看任务',
              description: '在工作台查看分配给我的任务列表和进度。',
            },
            {
              title: '执行任务',
              description: '点击任务进入详情，查看需要拨打的客户列表。',
            },
            {
              title: '完成任务',
              description: '拨打完所有客户后，任务自动标记为完成。',
            },
          ]}
        />
      </Card>

      <Card title="九、常见问题">
        <ul>
          <li>
            <strong>Q: 电话拨打失败怎么办？</strong>
            <br />
            A: 请检查网络连接和Twilio配置，或联系管理员检查系统设置。
          </li>
          <li style={{ marginTop: 12 }}>
            <strong>Q: 如何查看我的工作量？</strong>
            <br />
            A: 在"我的业绩"页面可以查看今日、本周、本月的通话统计数据。
          </li>
          <li style={{ marginTop: 12 }}>
            <strong>Q: 通话结果选错了可以修改吗？</strong>
            <br />
            A: 可以在"通信记录"页面找到对应记录进行编辑。
          </li>
          <li style={{ marginTop: 12 }}>
            <strong>Q: 自动拨号可以暂停吗？</strong>
            <br />
            A: 可以，在自动拨号过程中点击"停止"按钮即可暂停。
          </li>
        </ul>
      </Card>
    </div>
  );
}
