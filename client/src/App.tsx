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
import AgentDashboard from './pages/agent/Dashboard';
import CallList from './pages/agent/CallList';
import CommunicationRecords from './pages/agent/CommunicationRecords';
import MyStats from './pages/agent/MyStats';
import Settings from './pages/agent/Settings';
import PrivateRoute, { getAuth } from './components/PrivateRoute';

// 获取基础路径（子路径部署时使用）
const getBasePath = (): string => {
  return import.meta.env.VITE_BASE_PATH || '';
};

function App() {
  const { isAuthenticated } = getAuth();
  const basePath = getBasePath();

  return (
    <BrowserRouter basename={basePath || undefined}>
      <Routes>
        {/* 登录页 - 已登录则跳转到首页 */}
        <Route 
          path="/login" 
          element={
            isAuthenticated ? 
              <Navigate to="/dashboard" replace /> : 
              <LoginPage />
          } 
        />
        
        {/* 管理员路由 */}
        <Route 
          path="/" 
          element={
            <PrivateRoute allowedRoles={['admin']}>
              <AdminLayout />
            </PrivateRoute>
          }
        >
          <Route index element={<Navigate to="/dashboard" replace />} />
          <Route path="dashboard" element={<Dashboard />} />
          <Route path="users" element={<UserManagement />} />
          <Route path="customers" element={<CustomerManagement />} />
          <Route path="tasks" element={<TaskManagement />} />
          <Route path="stats" element={<Stats />} />
          <Route path="config" element={<SystemConfig />} />
          <Route path="twilio-test" element={<TwilioTest />} />
        </Route>

        {/* 客服路由 */}
        <Route 
          path="/" 
          element={
            <PrivateRoute allowedRoles={['agent']}>
              <AgentLayout />
            </PrivateRoute>
          }
        >
          <Route index element={<Navigate to="/dashboard" replace />} />
          <Route path="dashboard" element={<AgentDashboard />} />
          <Route path="calls" element={<CallList />} />
          <Route path="communication" element={<CommunicationRecords />} />
          <Route path="stats" element={<MyStats />} />
          <Route path="settings" element={<Settings />} />
        </Route>
        
        {/* 404 - 根据登录状态跳转 */}
        <Route 
          path="*" 
          element={
            isAuthenticated ? 
              <Navigate to="/" replace /> : 
              <Navigate to="/login" replace />
          } 
        />
      </Routes>
    </BrowserRouter>
  );
}

export default App;
