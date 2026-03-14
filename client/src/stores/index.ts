import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { User, AgentConfig } from '../types';

interface AuthState {
  token: string | null;
  user: User | null;
  isAuthenticated: boolean;
  hasHydrated: boolean;  // 新增：标记是否已从 localStorage 恢复
  setAuth: (token: string, user: User) => void;
  clearAuth: () => void;
  updateUser: (user: Partial<User>) => void;
  setHasHydrated: (state: boolean) => void;  // 新增：设置恢复状态
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      token: null,
      user: null,
      isAuthenticated: false,
      hasHydrated: false,  // 初始为 false
      setAuth: (token, user) => set({ token, user, isAuthenticated: true }),
      clearAuth: () => {
        localStorage.removeItem('token');
        localStorage.removeItem('user');
        set({ token: null, user: null, isAuthenticated: false });
      },
      updateUser: (userData) => set((state) => ({
        user: state.user ? { ...state.user, ...userData } : null
      })),
      setHasHydrated: (state) => set({ hasHydrated: state })
    }),
    {
      name: 'auth-storage',
      onRehydrateStorage: () => (state) => {
        // 恢复完成后设置 hasHydrated 为 true
        state?.setHasHydrated(true);
      }
    }
  )
);

interface AgentConfigState {
  config: AgentConfig | null;
  setConfig: (config: AgentConfig) => void;
}

export const useAgentConfigStore = create<AgentConfigState>()(
  persist(
    (set) => ({
      config: null,
      setConfig: (config) => set({ config })
    }),
    {
      name: 'agent-config-storage'
    }
  )
);

interface AutoDialState {
  isAutoDialing: boolean;
  currentCustomer: any | null;
  dialStatus: 'idle' | 'dialing' | 'connected' | 'ended';
  setAutoDialing: (isAutoDialing: boolean) => void;
  setCurrentCustomer: (customer: any | null) => void;
  setDialStatus: (status: 'idle' | 'dialing' | 'connected' | 'ended') => void;
  reset: () => void;
}

export const useAutoDialStore = create<AutoDialState>((set) => ({
  isAutoDialing: false,
  currentCustomer: null,
  dialStatus: 'idle',
  setAutoDialing: (isAutoDialing) => set({ isAutoDialing }),
  setCurrentCustomer: (currentCustomer) => set({ currentCustomer }),
  setDialStatus: (dialStatus) => set({ dialStatus }),
  reset: () => set({ isAutoDialing: false, currentCustomer: null, dialStatus: 'idle' })
}));
