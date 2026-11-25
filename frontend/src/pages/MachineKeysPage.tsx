import { useEffect, useState } from "react";
import { useParams, Link } from "react-router-dom";
import { machinesApi, GatewayError } from "../api/client";
import { getErrorMessage } from "../api/errors";

interface MachineKey {
  keyId: string;
  label?: string;
  hint: string;
  status: string;
  createdAt: string;
}

export default function MachineKeysPage() {
  const { id } = useParams<{ id: string }>();
  const [keys, setKeys] = useState<MachineKey[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = () => {
    if (!id) return;
    setLoading(true);
    machinesApi
      .listKeys(id)
      .then((res) => setKeys((res as { items: MachineKey[] }).items))
      .catch((err) => {
        setError(err instanceof GatewayError ? getErrorMessage(err.body.code) : "Napaka.");
      })
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load();
  }, [id]);

  const handleCreate = async () => {
    const label = prompt("Oznaka ključa (neobvezno):") ?? undefined;
    try {
      const res = await machinesApi.createKey(id!, label);
      const created = res as { machineKey?: string };
      if (created.machineKey) {
        alert(`Nov ključ (shranite — prikazan samo enkrat):\n${created.machineKey}`);
      }
      load();
    } catch (err) {
      alert(err instanceof GatewayError ? getErrorMessage(err.body.code) : "Napaka.");
    }
  };

  const handleRevoke = async (keyId: string) => {
    if (!confirm("Preklicujem ključ. Ste prepričani?")) return;
    try {
      await machinesApi.deleteKey(id!, keyId);
      load();
    } catch (err) {
      alert(err instanceof GatewayError ? getErrorMessage(err.body.code) : "Napaka.");
    }
  };

  return (
    <div style={{ maxWidth: 700, margin: "0 auto", padding: 24 }}>
      <Link to="/machines">← Nazaj</Link>
      <h1>Ključi avtomata</h1>
      {loading && <p>Nalagam...</p>}
      {error && <p style={{ color: "red" }}>{error}</p>}
      <button onClick={handleCreate} style={{ marginBottom: 16 }}>
        + Nov ključ
      </button>
      <table style={{ width: "100%", borderCollapse: "collapse" }}>
        <thead>
          <tr>
            <th style={{ textAlign: "left", padding: 8, borderBottom: "1px solid #ccc" }}>
              Oznaka
            </th>
            <th style={{ textAlign: "left", padding: 8, borderBottom: "1px solid #ccc" }}>Namig</th>
            <th style={{ textAlign: "left", padding: 8, borderBottom: "1px solid #ccc" }}>
              Stanje
            </th>
            <th style={{ padding: 8, borderBottom: "1px solid #ccc" }}>Dejanja</th>
          </tr>
        </thead>
        <tbody>
          {keys.map((k) => (
            <tr key={k.keyId}>
              <td style={{ padding: 8 }}>{k.label ?? "—"}</td>
              <td style={{ padding: 8, fontFamily: "monospace" }}>...{k.hint}</td>
              <td style={{ padding: 8 }}>{k.status}</td>
              <td style={{ padding: 8 }}>
                {k.status === "ACTIVE" && (
                  <button onClick={() => handleRevoke(k.keyId)} style={{ color: "red" }}>
                    Prekliči
                  </button>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
