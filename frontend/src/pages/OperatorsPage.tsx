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
    <div style={{ maxWidth: 800, margin: "0 auto", padding: 24 }}>
      <header style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
        <h1>Operaterji</h1>
        <div>
          <Link to="/">← Nadzorna plošča</Link>
          <button style={{ marginLeft: 16 }} onClick={() => setShowForm(!showForm)}>
            + Dodaj operaterja
          </button>
        </div>
      </header>

      {showForm && (
        <CreateOperatorForm
          onCreated={() => {
            setShowForm(false);
            load();
          }}
        />
      )}

      {resetTarget && <ResetPasswordForm id={resetTarget} onDone={() => setResetTarget(null)} />}

      {loading && <p>Nalagam...</p>}

      <table style={{ width: "100%", borderCollapse: "collapse" }}>
        <thead>
          <tr>
            {["E-naslov", "Ime", "Stanje", "Dejanja"].map((h) => (
              <th key={h} style={{ textAlign: "left", padding: 8, borderBottom: "1px solid #ccc" }}>
                {h}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {operators.map((op) => (
            <tr key={op.id}>
              <td style={{ padding: 8 }}>{op.email}</td>
              <td style={{ padding: 8 }}>{op.name}</td>
              <td style={{ padding: 8 }}>{op.status}</td>
              <td style={{ padding: 8 }}>
                {op.id !== self?.id && op.status === "ACTIVE" && (
                  <>
                    <button onClick={() => setResetTarget(op.id)} style={{ marginRight: 8 }}>
                      Ponastavi geslo
                    </button>
                    <button onClick={() => handleDeactivate(op.id)} style={{ color: "red" }}>
                      Deaktiviraj
                    </button>
                  </>
                )}
                {op.id === self?.id && <span style={{ color: "#888" }}>Jaz</span>}
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
    <form
      onSubmit={handleSubmit}
      style={{ border: "1px solid #ccc", borderRadius: 8, padding: 16, marginBottom: 16 }}
    >
      <h3>Nov operater</h3>
      {error && <p style={{ color: "red" }}>{error}</p>}
      {[
        { name: "email", type: "email", label: "E-naslov" },
        { name: "name", type: "text", label: "Ime" },
        { name: "password", type: "password", label: "Geslo (min. 12 znakov)" },
      ].map((f) => (
        <div key={f.name} style={{ marginBottom: 8 }}>
          <label>{f.label}: </label>
          <input name={f.name} type={f.type} required minLength={f.name === "password" ? 12 : 1} />
        </div>
      ))}
      <button type="submit">Ustvari</button>
      <button type="button" onClick={onCreated} style={{ marginLeft: 8 }}>
        Prekliči
      </button>
    </form>
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
    <form
      onSubmit={handleSubmit}
      style={{ border: "1px solid #ccc", borderRadius: 8, padding: 16, marginBottom: 16 }}
    >
      <h3>Ponastavitev gesla</h3>
      {error && <p style={{ color: "red" }}>{error}</p>}
      <div style={{ marginBottom: 8 }}>
        <label>Novo geslo (min. 12 znakov): </label>
        <input name="newPassword" type="password" required minLength={12} />
      </div>
      <button type="submit">Ponastavi</button>
      <button type="button" onClick={onDone} style={{ marginLeft: 8 }}>
        Prekliči
      </button>
    </form>
  );
}
