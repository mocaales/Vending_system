import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { operatorViewsApi, DashboardOut } from "../api/client";
import { useAuth } from "../auth/AuthContext";

const SectionStatus = ({ status }: { status: string }) => {
  const color = status === "OK" ? "green" : status === "STALE" ? "orange" : "red";
  return <span style={{ color, marginLeft: 8, fontSize: 12 }}>[{status}]</span>;
};

export default function DashboardPage() {
  const { logout, operator } = useAuth();
  const [data, setData] = useState<DashboardOut | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    operatorViewsApi
      .dashboard()
      .then(setData)
      .finally(() => setLoading(false));
  }, []);

  return (
    <div style={{ maxWidth: 900, margin: "0 auto", padding: 24 }}>
      <header style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
        <h1>Nadzorna plošča</h1>
        <div>
          <span style={{ marginRight: 16 }}>{operator?.email}</span>
          <Link to="/map" style={{ marginRight: 16 }}>
            Javni zemljevid
          </Link>
          <Link to="/machines" style={{ marginRight: 16 }}>
            Avtomati
          </Link>
          <Link to="/operators" style={{ marginRight: 16 }}>
            Operaterji
          </Link>
          <Link to="/closings" style={{ marginRight: 16 }}>
            Zaključki
          </Link>
          <button onClick={logout}>Odjava</button>
        </div>
      </header>

      {loading && <p>Nalagam...</p>}

      {data && (
        <div style={{ display: "grid", gridTemplateColumns: "repeat(3, 1fr)", gap: 16 }}>
          <div style={{ border: "1px solid #ccc", borderRadius: 8, padding: 16 }}>
            <h2>
              Avtomati
              <SectionStatus status={data.machines.status} />
            </h2>
            {data.machines.data ? (
              <p>Skupaj: {data.machines.data.total}</p>
            ) : (
              <p style={{ color: "red" }}>Ni dosegljivo</p>
            )}
          </div>

          <div style={{ border: "1px solid #ccc", borderRadius: 8, padding: 16 }}>
            <h2>
              Aktivni alarmi
              <SectionStatus status={data.alarms.status} />
            </h2>
            {data.alarms.data ? (
              <p>Skupaj: {data.alarms.data.total}</p>
            ) : (
              <p style={{ color: "red" }}>Ni dosegljivo</p>
            )}
          </div>

          <div style={{ border: "1px solid #ccc", borderRadius: 8, padding: 16 }}>
            <h2>
              Današnja prodaja
              <SectionStatus status={data.sales.status} />
            </h2>
            {data.sales.data ? (
              <p>Podatki na voljo</p>
            ) : (
              <p style={{ color: "red" }}>Ni dosegljivo</p>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
