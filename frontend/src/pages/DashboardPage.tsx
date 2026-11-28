import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { operatorViewsApi, DashboardOut } from "../api/client";
import { useAuth } from "../auth/AuthContext";

const SectionStatus = ({ status }: { status: string }) => {
  const cls =
    status === "OK" ? "section-ok" : status === "STALE" ? "section-stale" : "section-unavailable";
  return <span className={`section-status ${cls}`}>{status}</span>;
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
    <div className="page">
      <div className="page-header">
        <h1>Nadzorna plošča</h1>
        <nav>
          <span style={{ color: "var(--text-secondary)", fontSize: "0.875rem" }}>
            {operator?.email}
          </span>
          <Link to="/map">Zemljevid</Link>
          <Link to="/machines">Avtomati</Link>
          <Link to="/products">Izdelki</Link>
          <Link to="/inventory">Zaloga</Link>
          <Link to="/operators">Operaterji</Link>
          <Link to="/closings">Zaključki</Link>
          <button className="ghost" onClick={logout}>
            Odjava
          </button>
        </nav>
      </div>

      {loading && <p style={{ color: "var(--text-secondary)" }}>Nalagam...</p>}

      {data && (
        <div className="grid-3">
          {[
            {
              title: "Avtomati",
              section: data.machines,
              value: data.machines.data ? `${data.machines.data.total} skupaj` : null,
            },
            {
              title: "Aktivni alarmi",
              section: data.alarms,
              value: data.alarms.data ? `${data.alarms.data.total} aktivnih` : null,
            },
            {
              title: "Današnja prodaja",
              section: data.sales,
              value: data.sales.data ? "Podatki na voljo" : null,
            },
          ].map(({ title, section, value }) => (
            <div key={title} className="card">
              <h2 style={{ marginBottom: 12 }}>
                {title}
                <SectionStatus status={section.status} />
              </h2>
              {value ? (
                <p style={{ fontSize: "1.25rem", fontWeight: 600 }}>{value}</p>
              ) : (
                <p style={{ color: "var(--text-secondary)", fontSize: "0.875rem" }}>
                  Storitev ni dosegljiva
                </p>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
