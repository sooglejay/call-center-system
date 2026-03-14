import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { User, AgentConfig } from '../types';

interface AuthState {
  token: string | null;
  user: User | null;
  isAuthenticated: boolean;
  setAuth: (token: string, user: User) => void;
  clearAuth: () => void;
  updateUser: (user: Partial<User>) => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      token: null,
      user: null,
      isAuthenticated: false,
      setAuth: (token, user) => {
        console.log('[AuthStore] setAuth called');
        set({ token, user, isAuthenticated: true });
      },
      clearAuth: () => {
        localStorage.removeItem('token');
        localStorage.removeItem('user');
        set({ token: null, user: null, isAuthenticated: false });
      },
      updateUser: (userData) => set((state) => ({
        user: state.user ? { ...state.user, ...userData } : null
      }))
    }),
    {
      name: 'auth-storage'
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
