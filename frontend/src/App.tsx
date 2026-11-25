import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import { AuthProvider } from "./auth/AuthContext";
import { ProtectedRoute } from "./components/ProtectedRoute";
import LoginPage from "./pages/LoginPage";
import MapPage from "./pages/MapPage";
import DashboardPage from "./pages/DashboardPage";
import MachineOverviewPage from "./pages/MachineOverviewPage";
import MachinesPage from "./pages/MachinesPage";
import MachineKeysPage from "./pages/MachineKeysPage";

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/map" element={<MapPage />} />
          <Route
            path="/"
            element={
              <ProtectedRoute>
                <DashboardPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/machines"
            element={
              <ProtectedRoute>
                <MachinesPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/machines/:id"
            element={
              <ProtectedRoute>
                <MachineOverviewPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/machines/:id/keys"
            element={
              <ProtectedRoute>
                <MachineKeysPage />
              </ProtectedRoute>
            }
          />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}
