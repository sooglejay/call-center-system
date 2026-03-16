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
import type { User } from './types';

// 从 localStorage 获取认证状态
const getAuth = (): { isAuthenticated: boolean; user: User | null } => {
  const token = localStorage.getItem('token');
  const userStr = localStorage.getItem('user');
  try {
    const user = userStr ? JSON.parse(userStr) : null;
    return { isAuthenticated: !!token, user };
  } catch {
    return { isAuthenticated: false, user: null };
  }
};

function App() {
  const { isAuthenticated, user } = getAuth();

  return (
    <BrowserRouter>
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
        
        {/* 需要认证的路由 */}
        {isAuthenticated ? (
          <>
            {user?.role === 'admin' ? (
              <Route path="/" element={<AdminLayout />}>
                <Route index element={<Navigate to="/dashboard" replace />} />
                <Route path="dashboard" element={<Dashboard />} />
                <Route path="users" element={<UserManagement />} />
                <Route path="customers" element={<CustomerManagement />} />
                <Route path="tasks" element={<TaskManagement />} />
                <Route path="stats" element={<Stats />} />
                <Route path="config" element={<SystemConfig />} />
                <Route path="twilio-test" element={<TwilioTest />} />
              </Route>
            ) : (
              <Route path="/" element={<AgentLayout />}>
                <Route index element={<Navigate to="/dashboard" replace />} />
                <Route path="dashboard" element={<AgentDashboard />} />
                <Route path="calls" element={<CallList />} />
                <Route path="communication" element={<CommunicationRecords />} />
                <Route path="stats" element={<MyStats />} />
                <Route path="settings" element={<Settings />} />
              </Route>
            )}
          </>
        ) : null}
        
        {/* 未登录时，除 /login 外都跳转到登录页 */}
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
