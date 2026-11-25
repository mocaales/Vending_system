import { createContext, useContext, useState, useCallback, ReactNode } from "react";
import { authApi, OperatorOut } from "../api/client";

interface AuthState {
  operator: OperatorOut | null;
  isAuthenticated: boolean;
  login: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthState | null>(null);

export const AuthProvider = ({ children }: { children: ReactNode }) => {
  const [operator, setOperator] = useState<OperatorOut | null>(() => {
    const token = sessionStorage.getItem("accessToken");
    return token
      ? ({ id: "", email: "", name: "", status: "ACTIVE", createdAt: "" } as OperatorOut)
      : null;
  });

  const login = useCallback(async (email: string, password: string) => {
    const token = await authApi.login(email, password);
    sessionStorage.setItem("accessToken", token.accessToken);
    const me = await authApi.me();
    setOperator(me);
  }, []);

  const logout = useCallback(async () => {
    try {
      await authApi.logout();
    } finally {
      sessionStorage.removeItem("accessToken");
      setOperator(null);
    }
  }, []);

  return (
    <AuthContext.Provider value={{ operator, isAuthenticated: operator !== null, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within AuthProvider");
  return ctx;
};
