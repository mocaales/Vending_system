import { useEffect, useState } from "react";
import { useParams, Link } from "react-router-dom";
import { operatorViewsApi, MachineOverviewOut } from "../api/client";
import { GatewayError } from "../api/client";

const SectionStatus = ({ status }: { status: string }) => {
  const color = status === "OK" ? "green" : status === "STALE" ? "orange" : "red";
  return <span style={{ color, marginLeft: 8, fontSize: 12 }}>[{status}]</span>;
};

const Section = ({
  title,
  status,
  children,
}: {
  title: string;
  status: string;
  children: React.ReactNode;
}) => (
  <div style={{ border: "1px solid #ccc", borderRadius: 8, padding: 16, marginBottom: 16 }}>
    <h2>
      {title}
      <SectionStatus status={status} />
    </h2>
    {status === "UNAVAILABLE" ? <p style={{ color: "red" }}>Ni dosegljivo</p> : children}
  </div>
);

export default function MachineOverviewPage() {
  const { id } = useParams<{ id: string }>();
  const [data, setData] = useState<MachineOverviewOut | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!id) return;
    operatorViewsApi
      .machineOverview(id)
      .then(setData)
      .catch((err) => {
        if (err instanceof GatewayError && err.status === 404) {
          setError("Avtomat ni najden.");
        } else {
          setError("Napaka pri nalaganju podatkov.");
        }
      })
      .finally(() => setLoading(false));
  }, [id]);

  if (loading) return <p>Nalagam...</p>;
  if (error) return <p style={{ color: "red" }}>{error}</p>;
  if (!data) return null;

  const machine = data.machine.data as { name?: string; status?: string } | null;

  return (
    <div style={{ maxWidth: 900, margin: "0 auto", padding: 24 }}>
      <Link to="/machines">← Nazaj</Link>
      <Link to={`/machines/${id}/thresholds`} style={{ marginLeft: 16 }}>
        Pragovi alarmov
      </Link>
      <h1>
        {machine?.name ?? "Avtomat"}
        <SectionStatus status={data.machine.status} />
      </h1>

      <Section title="Stanje" status={data.monitoring.status}>
        <pre style={{ fontSize: 12 }}>{JSON.stringify(data.monitoring.data, null, 2)}</pre>
      </Section>

      <Section title="Pragovi" status={data.thresholds.status}>
        <pre style={{ fontSize: 12 }}>{JSON.stringify(data.thresholds.data, null, 2)}</pre>
      </Section>

      <Section title="Alarmi" status={data.alarms.status}>
        {data.alarms.data?.items.length === 0 ? (
          <p>Ni aktivnih alarmov.</p>
        ) : (
          <pre style={{ fontSize: 12 }}>{JSON.stringify(data.alarms.data, null, 2)}</pre>
        )}
      </Section>

      <Section title="Zaloga" status={data.inventory.status}>
        {data.inventory.data?.items.length === 0 ? (
          <p>Ni zalog.</p>
        ) : (
          <pre style={{ fontSize: 12 }}>{JSON.stringify(data.inventory.data, null, 2)}</pre>
        )}
      </Section>
    </div>
  );
}
