import { useState, FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { GatewayError } from "../api/client";
import { getErrorMessage } from "../api/errors";

export default function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      await login(email, password);
      navigate("/", { replace: true });
    } catch (err) {
      if (err instanceof GatewayError) {
        setError(getErrorMessage(err.body.code));
      } else {
        setError("Napaka pri povezavi s strežnikom.");
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div
      style={{
        minHeight: "100vh",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        background: "var(--bg)",
      }}
    >
      <div className="card" style={{ width: 360, padding: 32 }}>
        <h1 style={{ marginBottom: 4 }}>Vending System</h1>
        <p style={{ color: "var(--text-secondary)", marginBottom: 24, fontSize: "0.875rem" }}>
          Prijava v operaterski vmesnik
        </p>

        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label htmlFor="email">E-naslov</label>
            <input
              id="email"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
              autoComplete="email"
              placeholder="admin@vending.local"
            />
          </div>

          <div className="form-group">
            <label htmlFor="password">Geslo</label>
            <input
              id="password"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              autoComplete="current-password"
              placeholder="••••••••"
            />
          </div>

          {error && <div className="alert alert-error">{error}</div>}

          <button
            type="submit"
            className="primary"
            disabled={loading}
            style={{ width: "100%", padding: "10px" }}
          >
            {loading ? "Prijavljam..." : "Prijava"}
          </button>
        </form>
      </div>
    </div>
  );
}
