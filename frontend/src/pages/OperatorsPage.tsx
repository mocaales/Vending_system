import { useEffect, useState, FormEvent } from "react";
import { Link } from "react-router-dom";
import { operatorsApi, OperatorOut, GatewayError } from "../api/client";
import { getErrorMessage } from "../api/errors";
import { useAuth } from "../auth/AuthContext";

export default function OperatorsPage() {
  const { operator: self } = useAuth();
  const [operators, setOperators] = useState<OperatorOut[]>([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [resetTarget, setResetTarget] = useState<string | null>(null);

  const load = () => {
    setLoading(true);
    operatorsApi
      .list()
      .then((res) => setOperators(res.items))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load();
  }, []);

  const handleDeactivate = async (id: string) => {
    if (!confirm("Deaktivirati operaterja?")) return;
    try {
      await operatorsApi.deactivate(id);
      load();
    } catch (err) {
      alert(err instanceof GatewayError ? getErrorMessage(err.body.code) : "Napaka.");
    }
  };

  return (
    <div className="page">
      <div className="page-header">
        <h1>Operaterji</h1>
        <nav>
          <Link to="/">← Nadzorna plošča</Link>
          <button className="primary" onClick={() => setShowForm(!showForm)}>
            + Dodaj operaterja
          </button>
        </nav>
      </div>

      {showForm && (
        <CreateOperatorForm
          onCreated={() => {
            setShowForm(false);
            load();
          }}
        />
      )}
      {resetTarget && <ResetPasswordForm id={resetTarget} onDone={() => setResetTarget(null)} />}
      {loading && <p style={{ color: "var(--text-secondary)" }}>Nalagam...</p>}

      <table>
        <thead>
          <tr>
            <th>E-naslov</th>
            <th>Ime</th>
            <th>Stanje</th>
            <th>Dejanja</th>
          </tr>
        </thead>
        <tbody>
          {operators.map((op) => (
            <tr key={op.id}>
              <td>{op.email}</td>
              <td>{op.name}</td>
              <td>
                <span
                  className={`badge ${op.status === "ACTIVE" ? "badge-active" : "badge-inactive"}`}
                >
                  {op.status === "ACTIVE" ? "Aktiven" : "Deaktiviran"}
                </span>
              </td>
              <td>
                <div className="flex-row">
                  {op.id !== self?.id && op.status === "ACTIVE" && (
                    <>
                      <button onClick={() => setResetTarget(op.id)}>Ponastavi geslo</button>
                      <button className="danger" onClick={() => handleDeactivate(op.id)}>
                        Deaktiviraj
                      </button>
                    </>
                  )}
                  {op.id === self?.id && (
                    <span style={{ color: "var(--text-secondary)", fontSize: "0.85rem" }}>Jaz</span>
                  )}
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function CreateOperatorForm({ onCreated }: { onCreated: () => void }) {
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const fd = new FormData(e.currentTarget);
    try {
      await operatorsApi.create({
        email: fd.get("email") as string,
        name: fd.get("name") as string,
        password: fd.get("password") as string,
      });
      onCreated();
    } catch (err) {
      setError(err instanceof GatewayError ? getErrorMessage(err.body.code) : "Napaka.");
    }
  };

  return (
    <div className="card" style={{ marginBottom: 24 }}>
      <h2 style={{ marginBottom: 16 }}>Nov operater</h2>
      {error && <div className="alert alert-error">{error}</div>}
      <form onSubmit={handleSubmit}>
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 12 }}>
          {[
            { name: "email", type: "email", label: "E-naslov" },
            { name: "name", type: "text", label: "Ime" },
            { name: "password", type: "password", label: "Geslo (min. 12 znakov)" },
          ].map((f) => (
            <div key={f.name} className="form-group" style={{ margin: 0 }}>
              <label>{f.label}</label>
              <input
                name={f.name}
                type={f.type}
                required
                minLength={f.name === "password" ? 12 : 1}
              />
            </div>
          ))}
        </div>
        <div className="flex-row" style={{ marginTop: 16 }}>
          <button type="submit" className="primary">
            Ustvari
          </button>
          <button type="button" className="ghost" onClick={onCreated}>
            Prekliči
          </button>
        </div>
      </form>
    </div>
  );
}

function ResetPasswordForm({ id, onDone }: { id: string; onDone: () => void }) {
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const fd = new FormData(e.currentTarget);
    try {
      await operatorsApi.resetPassword(id, fd.get("newPassword") as string);
      alert("Geslo je bilo ponastavljeno.");
      onDone();
    } catch (err) {
      setError(err instanceof GatewayError ? getErrorMessage(err.body.code) : "Napaka.");
    }
  };

  return (
    <div className="card" style={{ marginBottom: 24 }}>
      <h2 style={{ marginBottom: 16 }}>Ponastavitev gesla</h2>
      {error && <div className="alert alert-error">{error}</div>}
      <form onSubmit={handleSubmit}>
        <div className="form-group">
          <label>Novo geslo (min. 12 znakov)</label>
          <input
            name="newPassword"
            type="password"
            required
            minLength={12}
            style={{ maxWidth: 300 }}
          />
        </div>
        <div className="flex-row">
          <button type="submit" className="primary">
            Ponastavi
          </button>
          <button type="button" className="ghost" onClick={onDone}>
            Prekliči
          </button>
        </div>
      </form>
    </div>
  );
}
