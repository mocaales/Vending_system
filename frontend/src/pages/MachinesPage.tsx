import { useEffect, useState, FormEvent } from "react";
import { Link } from "react-router-dom";
import { machinesApi, GatewayError } from "../api/client";
import { getErrorMessage } from "../api/errors";

interface Machine {
  id: string;
  name: string;
  code: string;
  address: string;
  status: string;
}

const StatusBadge = ({ status }: { status: string }) => {
  const cls =
    status === "ACTIVE" ? "badge-active" : status === "SERVICE" ? "badge-stale" : "badge-inactive";
  return (
    <span className={`badge ${cls}`}>
      {status === "ACTIVE" ? "Aktiven" : status === "SERVICE" ? "Servis" : "Umaknjen"}
    </span>
  );
};

export default function MachinesPage() {
  const [machines, setMachines] = useState<Machine[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showForm, setShowForm] = useState(false);

  const load = () => {
    setLoading(true);
    machinesApi
      .list()
      .then((res) => setMachines(res.items as Machine[]))
      .catch((err) => {
        setError(err instanceof GatewayError ? getErrorMessage(err.body.code) : "Napaka.");
      })
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load();
  }, []);

  const handleRetire = async (id: string) => {
    if (!confirm("Umikam avtomat. Ste prepričani?")) return;
    try {
      await machinesApi.retire(id);
      load();
    } catch (err) {
      alert(err instanceof GatewayError ? getErrorMessage(err.body.code) : "Napaka.");
    }
  };

  const handleStatus = async (id: string, status: string) => {
    try {
      await machinesApi.setStatus(id, status);
      load();
    } catch (err) {
      alert(err instanceof GatewayError ? getErrorMessage(err.body.code) : "Napaka.");
    }
  };

  return (
    <div className="page">
      <div className="page-header">
        <h1>Avtomati</h1>
        <nav>
          <Link to="/">← Nadzorna plošča</Link>
          <button className="primary" onClick={() => setShowForm(!showForm)}>
            + Dodaj avtomat
          </button>
        </nav>
      </div>

      {showForm && (
        <CreateMachineForm
          onCreated={() => {
            setShowForm(false);
            load();
          }}
        />
      )}

      {error && <div className="alert alert-error">{error}</div>}
      {loading && <p style={{ color: "var(--text-secondary)" }}>Nalagam...</p>}

      <table>
        <thead>
          <tr>
            <th>Ime</th>
            <th>Naslov</th>
            <th>Stanje</th>
            <th>Dejanja</th>
          </tr>
        </thead>
        <tbody>
          {machines.map((m) => (
            <tr key={m.id}>
              <td>
                <Link to={`/machines/${m.id}`}>{m.name}</Link>
              </td>
              <td style={{ color: "var(--text-secondary)" }}>{m.address}</td>
              <td>
                <StatusBadge status={m.status} />
              </td>
              <td>
                <div className="flex-row">
                  {m.status === "ACTIVE" && (
                    <button onClick={() => handleStatus(m.id, "SERVICE")}>V servis</button>
                  )}
                  {m.status === "SERVICE" && (
                    <button className="primary" onClick={() => handleStatus(m.id, "ACTIVE")}>
                      Aktiviraj
                    </button>
                  )}
                  {m.status !== "RETIRED" && (
                    <button className="danger" onClick={() => handleRetire(m.id)}>
                      Umakni
                    </button>
                  )}
                  <Link to={`/machines/${m.id}/keys`}>Ključi</Link>
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function CreateMachineForm({ onCreated }: { onCreated: () => void }) {
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const fd = new FormData(e.currentTarget);
    try {
      await machinesApi.create({
        code: fd.get("code"),
        name: fd.get("name"),
        address: fd.get("address"),
        latitude: parseFloat(fd.get("latitude") as string),
        longitude: parseFloat(fd.get("longitude") as string),
        slots: [{ slotCode: "A1" }],
      });
      onCreated();
    } catch (err) {
      setError(err instanceof GatewayError ? getErrorMessage(err.body.code) : "Napaka.");
    }
  };

  const fields = [
    { name: "code", label: "Oznaka (npr. VM-01)" },
    { name: "name", label: "Ime avtomata" },
    { name: "address", label: "Naslov" },
    { name: "latitude", label: "Zemljepisna širina" },
    { name: "longitude", label: "Zemljepisna dolžina" },
  ];

  return (
    <div className="card" style={{ marginBottom: 24 }}>
      <h2 style={{ marginBottom: 16 }}>Nov avtomat</h2>
      {error && <div className="alert alert-error">{error}</div>}
      <form onSubmit={handleSubmit}>
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
          {fields.map((f) => (
            <div key={f.name} className="form-group" style={{ margin: 0 }}>
              <label>{f.label}</label>
              <input name={f.name} required />
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
