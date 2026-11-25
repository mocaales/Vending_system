import { useEffect, useState, FormEvent } from "react";
import { useParams, Link } from "react-router-dom";
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

interface Thresholds {
  maxTempC: string;
  resetTempC: string;
  consecutiveReadings: number;
  doorOpenMaxSeconds: number;
  noDataMaxSeconds: number;
  isDefault?: boolean;
}

export default function ThresholdsPage() {
  const { id } = useParams<{ id: string }>();
  const [thresholds, setThresholds] = useState<Thresholds | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);

  const load = () => {
    if (!id) return;
    req<Thresholds>("GET", `/api/v1/thresholds/${id}`)
      .then(setThresholds)
      .catch(() => setError("Napaka pri nalaganju."))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load();
  }, [id]);

  const handleSubmit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const fd = new FormData(e.currentTarget);
    setError(null);
    setSuccess(false);
    try {
      await req("PUT", `/api/v1/thresholds/${id}`, {
        maxTempC: fd.get("maxTempC"),
        resetTempC: fd.get("resetTempC"),
        consecutiveReadings: parseInt(fd.get("consecutiveReadings") as string),
        doorOpenMaxSeconds: parseInt(fd.get("doorOpenMaxSeconds") as string),
        noDataMaxSeconds: parseInt(fd.get("noDataMaxSeconds") as string),
      });
      setSuccess(true);
      load();
    } catch (err) {
      setError(err instanceof GatewayError ? getErrorMessage(err.body.code) : "Napaka.");
    }
  };

  const handleReset = async () => {
    if (!confirm("Ponastavi na privzete vrednosti?")) return;
    try {
      await req("DELETE", `/api/v1/thresholds/${id}`);
      load();
    } catch (err) {
      setError(err instanceof GatewayError ? getErrorMessage(err.body.code) : "Napaka.");
    }
  };

  if (loading) return <p>Nalagam...</p>;

  return (
    <div style={{ maxWidth: 500, margin: "0 auto", padding: 24 }}>
      <Link to={`/machines/${id}`}>← Nazaj</Link>
      <h1>Pragovi alarmov</h1>
      {thresholds?.isDefault && <p style={{ color: "#888" }}>Privzete vrednosti</p>}
      {error && <p style={{ color: "red" }}>{error}</p>}
      {success && <p style={{ color: "green" }}>Pragovi so bili shranjeni.</p>}

      {thresholds && (
        <form onSubmit={handleSubmit}>
          {[
            { name: "maxTempC", label: "Maks. temperatura (°C)", value: thresholds.maxTempC },
            { name: "resetTempC", label: "Reset temperatura (°C)", value: thresholds.resetTempC },
            {
              name: "consecutiveReadings",
              label: "Zaporednih meritev za alarm",
              value: String(thresholds.consecutiveReadings),
            },
            {
              name: "doorOpenMaxSeconds",
              label: "Maks. odprtost vrat (s)",
              value: String(thresholds.doorOpenMaxSeconds),
            },
            {
              name: "noDataMaxSeconds",
              label: "Brez meritev (s)",
              value: String(thresholds.noDataMaxSeconds),
            },
          ].map((f) => (
            <div key={f.name} style={{ marginBottom: 12 }}>
              <label>{f.label}: </label>
              <input name={f.name} defaultValue={f.value} required style={{ width: 120 }} />
            </div>
          ))}
          <button type="submit">Shrani</button>
          <button type="button" onClick={handleReset} style={{ marginLeft: 8, color: "red" }}>
            Ponastavi
          </button>
        </form>
      )}
    </div>
  );
}
