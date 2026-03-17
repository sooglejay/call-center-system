import { Navigate, useLocation } from 'react-router-dom';
import { ReactNode } from 'react';
import type { User } from '../types';

interface PrivateRouteProps {
  children: ReactNode;
  allowedRoles?: string[];
}

// 获取认证状态
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

export default function PrivateRoute({ children, allowedRoles }: PrivateRouteProps) {
  const location = useLocation();
  const { isAuthenticated, user } = getAuth();

  // 未登录，跳转到登录页，记录当前路径
  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location.pathname }} replace />;
  }

  // 角色权限检查
  if (allowedRoles && user && !allowedRoles.includes(user.role)) {
    // 根据角色跳转到对应首页
    const redirectPath = user.role === 'admin' ? '/dashboard' : '/dashboard';
    return <Navigate to={redirectPath} replace />;
  }

  return <>{children}</>;
}

// 导出认证状态获取函数供其他组件使用
export { getAuth };
