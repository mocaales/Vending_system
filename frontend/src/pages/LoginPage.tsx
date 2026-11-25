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
    <main style={{ maxWidth: 360, margin: "80px auto", padding: "0 16px" }}>
      <h1>Prijava</h1>
      <form onSubmit={handleSubmit}>
        <div>
          <label htmlFor="email">E-naslov</label>
          <br />
          <input
            id="email"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
            autoComplete="email"
            style={{ width: "100%", marginBottom: 12 }}
          />
        </div>
        <div>
          <label htmlFor="password">Geslo</label>
          <br />
          <input
            id="password"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
            autoComplete="current-password"
            style={{ width: "100%", marginBottom: 12 }}
          />
        </div>
        {error && (
          <p role="alert" style={{ color: "red" }}>
            {error}
          </p>
        )}
        <button type="submit" disabled={loading} style={{ width: "100%" }}>
          {loading ? "Prijavljam..." : "Prijava"}
        </button>
      </form>
    </main>
  );
}
