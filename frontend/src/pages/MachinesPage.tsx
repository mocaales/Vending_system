import { useEffect, useState, FormEvent } from "react";
import { Link } from "react-router-dom";
import { machinesApi } from "../api/client";
import { GatewayError } from "../api/client";
import { getErrorMessage } from "../api/errors";

interface Machine {
  id: string;
  name: string;
  code: string;
  address: string;
  status: string;
}

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
        const msg =
          err instanceof GatewayError ? getErrorMessage(err.body.code) : "Napaka pri nalaganju.";
        setError(msg);
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
    <div style={{ maxWidth: 900, margin: "0 auto", padding: 24 }}>
      <header style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
        <h1>Avtomati</h1>
        <div>
          <Link to="/">← Nadzorna plošča</Link>
          <button style={{ marginLeft: 16 }} onClick={() => setShowForm(!showForm)}>
            + Dodaj avtomat
          </button>
        </div>
      </header>

      {showForm && (
        <CreateMachineForm
          onCreated={() => {
            setShowForm(false);
            load();
          }}
        />
      )}

      {loading && <p>Nalagam...</p>}
      {error && <p style={{ color: "red" }}>{error}</p>}

      <table style={{ width: "100%", borderCollapse: "collapse" }}>
        <thead>
          <tr>
            <th style={{ textAlign: "left", padding: 8, borderBottom: "1px solid #ccc" }}>Ime</th>
            <th style={{ textAlign: "left", padding: 8, borderBottom: "1px solid #ccc" }}>
              Naslov
            </th>
            <th style={{ textAlign: "left", padding: 8, borderBottom: "1px solid #ccc" }}>
              Stanje
            </th>
            <th style={{ padding: 8, borderBottom: "1px solid #ccc" }}>Dejanja</th>
          </tr>
        </thead>
        <tbody>
          {machines.map((m) => (
            <tr key={m.id}>
              <td style={{ padding: 8 }}>
                <Link to={`/machines/${m.id}`}>{m.name}</Link>
              </td>
              <td style={{ padding: 8 }}>{m.address}</td>
              <td style={{ padding: 8 }}>{m.status}</td>
              <td style={{ padding: 8 }}>
                {m.status === "ACTIVE" && (
                  <button onClick={() => handleStatus(m.id, "SERVICE")} style={{ marginRight: 8 }}>
                    V servis
                  </button>
                )}
                {m.status === "SERVICE" && (
                  <button onClick={() => handleStatus(m.id, "ACTIVE")} style={{ marginRight: 8 }}>
                    Aktiviraj
                  </button>
                )}
                {m.status !== "RETIRED" && (
                  <button onClick={() => handleRetire(m.id)} style={{ color: "red" }}>
                    Umakni
                  </button>
                )}
                <Link to={`/machines/${m.id}/keys`} style={{ marginLeft: 8 }}>
                  Ključi
                </Link>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function CreateMachineForm({ onCreated }: { onCreated: () => void }) {
  const [formError, setFormError] = useState<string | null>(null);

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
      setFormError(err instanceof GatewayError ? getErrorMessage(err.body.code) : "Napaka.");
    }
  };

  return (
    <form
      onSubmit={handleSubmit}
      style={{ border: "1px solid #ccc", borderRadius: 8, padding: 16, marginBottom: 16 }}
    >
      <h3>Nov avtomat</h3>
      {formError && <p style={{ color: "red" }}>{formError}</p>}
      {["code", "name", "address", "latitude", "longitude"].map((field) => (
        <div key={field} style={{ marginBottom: 8 }}>
          <label>{field}: </label>
          <input name={field} required style={{ width: 200 }} />
        </div>
      ))}
      <button type="submit">Ustvari</button>
      <button type="button" onClick={() => onCreated()} style={{ marginLeft: 8 }}>
        Prekliči
      </button>
    </form>
  );
}
