import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import LoginPage from './pages/login';
import AdminLayout from './pages/admin/Layout';
import AgentLayout from './pages/agent/Layout';
import Dashboard from './pages/admin/Dashboard';
import UserManagement from './pages/admin/UserManagement';
import CustomerManagement from './pages/admin/CustomerManagement';
import TaskManagement from './pages/admin/TaskManagement';
import Stats from './pages/admin/Stats';
import SystemConfig from './pages/admin/SystemConfig';
import TwilioTest from './pages/admin/TwilioTest';
import DataPermission from './pages/admin/DataPermission';
import VersionManagement from './pages/admin/VersionManagement';
import AgentDashboard from './pages/agent/Dashboard';
import AgentTaskList from './pages/agent/AgentTaskList';
import AgentTaskExecution from './pages/agent/AgentTaskExecution';
import CallList from './pages/agent/CallList';
import CommunicationRecords from './pages/agent/CommunicationRecords';
import MyStats from './pages/agent/MyStats';
import Settings from './pages/agent/Settings';
import AppDownload from './pages/agent/AppDownload';
import HelpDocumentation from './pages/HelpDocumentation';
import PrivateRoute, { getAuth } from './components/PrivateRoute';

// 获取基础路径（子路径部署时使用）
const getBasePath = (): string => {
  return import.meta.env.VITE_BASE_PATH || '';
};

function App() {
  const { isAuthenticated, user } = getAuth();
  const basePath = getBasePath();
  
  // 根据用户角色确定默认跳转路径
  const getDefaultRedirect = () => {
    if (!isAuthenticated) return '/login';
    return user?.role === 'admin' ? '/admin/dashboard' : '/agent/dashboard';
  };

  return (
    <BrowserRouter basename={basePath || undefined}>
      <Routes>
        {/* 根路径重定向 */}
        <Route 
          path="/" 
          element={<Navigate to={getDefaultRedirect()} replace />} 
        />
        
        {/* 登录页 */}
        <Route 
          path="/login" 
          element={
            isAuthenticated ? 
              <Navigate to={getDefaultRedirect()} replace /> : 
              <LoginPage />
          } 
        />

        {/* 帮助文档 - 公开访问 */}
        <Route path="/help" element={<HelpDocumentation />} />
        
        {/* 管理员路由 - 使用 /admin 前缀 */}
        <Route 
          path="/admin" 
          element={
            <PrivateRoute allowedRoles={['admin']}>
              <AdminLayout />
            </PrivateRoute>
          }
        >
          <Route index element={<Navigate to="dashboard" replace />} />
          <Route path="dashboard" element={<Dashboard />} />
          <Route path="users" element={<UserManagement />} />
          <Route path="customers" element={<CustomerManagement />} />
          <Route path="tasks" element={<TaskManagement />} />
          <Route path="stats" element={<Stats />} />
          <Route path="config" element={<SystemConfig />} />
          <Route path="twilio-test" element={<TwilioTest />} />
          <Route path="data-permission" element={<DataPermission />} />
          <Route path="version" element={<VersionManagement />} />
        </Route>

        {/* 客服路由 - 使用 /agent 前缀 */}
        <Route 
          path="/agent" 
          element={
            <PrivateRoute allowedRoles={['agent']}>
              <AgentLayout />
            </PrivateRoute>
          }
        >
          <Route index element={<Navigate to="dashboard" replace />} />
          <Route path="dashboard" element={<AgentDashboard />} />
          <Route path="tasks" element={<AgentTaskList />} />
          <Route path="tasks/:taskId" element={<AgentTaskExecution />} />
          <Route path="calls" element={<CallList />} />
          <Route path="communication" element={<CommunicationRecords />} />
          <Route path="stats" element={<MyStats />} />
          <Route path="download" element={<AppDownload />} />
          <Route path="settings" element={<Settings />} />
        </Route>
        
        {/* 404 - 根据登录状态跳转 */}
        <Route 
          path="*" 
          element={
            isAuthenticated ? 
              <Navigate to={getDefaultRedirect()} replace /> : 
              <Navigate to="/login" replace />
          } 
        />
      </Routes>
    </BrowserRouter>
  );
}

export default App;
