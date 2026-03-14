import { BrowserRouter, Routes, Route, Navigate, useLocation } from 'react-router-dom';
import { useAuthStore } from './stores';
import LoginPage from './pages/login';
import AdminLayout from './pages/admin/Layout';
import AgentLayout from './pages/agent/Layout';
import Dashboard from './pages/admin/Dashboard';
import UserManagement from './pages/admin/UserManagement';
import CustomerManagement from './pages/admin/CustomerManagement';
import TaskManagement from './pages/admin/TaskManagement';
import Stats from './pages/admin/Stats';
import SystemConfig from './pages/admin/SystemConfig';
import AgentDashboard from './pages/agent/Dashboard';
import CallList from './pages/agent/CallList';
import CommunicationRecords from './pages/agent/CommunicationRecords';
import MyStats from './pages/agent/MyStats';
import Settings from './pages/agent/Settings';

// 检查是否已登录（直接读 localStorage，不依赖 zustand）
const checkAuth = () => {
  const token = localStorage.getItem('token');
  const userStr = localStorage.getItem('user');
  return { isAuthenticated: !!token, user: userStr ? JSON.parse(userStr) : null };
};

function AppContent() {
  const location = useLocation();
  const { isAuthenticated, user } = checkAuth();
  
  console.log('[App] 当前路径:', location.pathname);
  console.log('[App] 是否已登录:', isAuthenticated);
  console.log('[App] 用户角色:', user?.role);

  return (
    <Routes>
      {/* 登录路由 */}
      <Route 
        path="/login" 
        element={isAuthenticated ? <Navigate to="/" replace /> : <LoginPage />} 
      />
      
      {/* 已登录路由 */}
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
          <Route path="*" element={<Navigate to="/" replace />} />
        </>
      ) : (
        <Route path="*" element={<Navigate to="/login" replace />} />
      )}
    </Routes>
  );
}

function App() {
  return (
    <BrowserRouter>
      <AppContent />
    </BrowserRouter>
  );
}

export default App;
