import { useEffect, useState, FormEvent } from "react";
import { Link } from "react-router-dom";
import { GatewayError } from "../api/client";
import { getErrorMessage } from "../api/errors";

const apiUrl = () => import.meta.env.VITE_API_URL ?? "";
const getToken = () => sessionStorage.getItem("accessToken");

const req = async <T,>(method: string, path: string, body?: unknown): Promise<T> => {
  const res = await fetch(`${apiUrl()}${path}`, {
    method,
    headers: {
      "content-type": "application/json",
      authorization: `Bearer ${getToken() ?? ""}`,
    },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
  if (res.status === 204) return undefined as T;
  const data = await res.json();
  if (!res.ok) throw new GatewayError(res.status, data);
  return data as T;
};

interface Closing {
  id: string;
  date: string;
  machineId?: string;
  status: string;
  note?: string;
  totals: { currency: string; salesCount: number; turnover: string }[];
  openReconciliations: number;
  createdAt: string;
}

export default function ClosingsPage() {
  const [closings, setClosings] = useState<Closing[]>([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);

  const load = () => {
    setLoading(true);
    req<{ items: Closing[] }>("GET", "/api/v1/sales/closings")
      .then((res) => setClosings(res.items))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load();
  }, []);

  const handleConfirm = async (id: string) => {
    try {
      await req("PUT", `/api/v1/sales/closings/${id}`, { status: "CONFIRMED" });
      load();
    } catch (err) {
      alert(err instanceof GatewayError ? getErrorMessage(err.body.code) : "Napaka.");
    }
  };

  const handleDelete = async (id: string) => {
    if (!confirm("Izbrisati osnutek?")) return;
    try {
      await req("DELETE", `/api/v1/sales/closings/${id}`);
      load();
    } catch (err) {
      alert(err instanceof GatewayError ? getErrorMessage(err.body.code) : "Napaka.");
    }
  };

  return (
    <div style={{ maxWidth: 900, margin: "0 auto", padding: 24 }}>
      <header style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
        <h1>Dnevni zaključki</h1>
        <div>
          <Link to="/">← Nadzorna plošča</Link>
          <button style={{ marginLeft: 16 }} onClick={() => setShowForm(!showForm)}>
            + Nov zaključek
          </button>
        </div>
      </header>

      {showForm && (
        <CreateClosingForm
          onCreated={() => {
            setShowForm(false);
            load();
          }}
        />
      )}

      {loading && <p>Nalagam...</p>}

      <table style={{ width: "100%", borderCollapse: "collapse" }}>
        <thead>
          <tr>
            {["Datum", "Stanje", "Prodaje", "Promet", "Uskladitve", "Dejanja"].map((h) => (
              <th key={h} style={{ textAlign: "left", padding: 8, borderBottom: "1px solid #ccc" }}>
                {h}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {closings.map((c) => (
            <tr key={c.id}>
              <td style={{ padding: 8 }}>{c.date}</td>
              <td style={{ padding: 8 }}>{c.status}</td>
              <td style={{ padding: 8 }}>{c.totals[0]?.salesCount ?? 0}</td>
              <td style={{ padding: 8 }}>
                {c.totals[0] ? `${c.totals[0].turnover} ${c.totals[0].currency}` : "—"}
              </td>
              <td style={{ padding: 8 }}>{c.openReconciliations}</td>
              <td style={{ padding: 8 }}>
                {c.status === "DRAFT" && (
                  <>
                    <button onClick={() => handleConfirm(c.id)} style={{ marginRight: 8 }}>
                      Potrdi
                    </button>
                    <button onClick={() => handleDelete(c.id)} style={{ color: "red" }}>
                      Izbriši
                    </button>
                  </>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function CreateClosingForm({ onCreated }: { onCreated: () => void }) {
  const [error, setError] = useState<string | null>(null);

  const yesterday = () => {
    const d = new Date();
    d.setUTCDate(d.getUTCDate() - 1);
    return d.toISOString().slice(0, 10);
  };

  const handleSubmit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const fd = new FormData(e.currentTarget);
    try {
      await req("POST", "/api/v1/sales/closings", {
        date: fd.get("date"),
        note: fd.get("note") || undefined,
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
      <h3>Nov zaključek</h3>
      {error && <p style={{ color: "red" }}>{error}</p>}
      <div style={{ marginBottom: 8 }}>
        <label>Datum: </label>
        <input name="date" type="date" defaultValue={yesterday()} required />
      </div>
      <div style={{ marginBottom: 8 }}>
        <label>Opomba: </label>
        <input name="note" type="text" style={{ width: 200 }} />
      </div>
      <button type="submit">Ustvari</button>
      <button type="button" onClick={onCreated} style={{ marginLeft: 8 }}>
        Prekliči
      </button>
    </form>
  );
}
