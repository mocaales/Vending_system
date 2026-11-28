import { useEffect, useState, FormEvent } from "react";
import { Link } from "react-router-dom";
import {
  GatewayError,
  inventoryApi,
  MachineWithSlots,
  machinesApi,
  MovementOut,
  MovementType,
  ProductOut,
  productsApi,
} from "../api/client";
import { getErrorMessage } from "../api/errors";
import { Field } from "../components/Field";

const errorText = (err: unknown) =>
  err instanceof GatewayError ? getErrorMessage(err.body.code) : "Napaka.";

const TYPES: Record<MovementType, string> = {
  SET: "Nastavitev",
  REFILL: "Polnitev",
  REMOVAL: "Odstranitev",
  SALE: "Prodaja",
};

// Polja obrazca -> poizvedba; datuma od/do pokrijeta cel lokalni dan.
const toFilters = (fd: FormData) => {
  const filters: Record<string, string> = {};
  for (const name of ["machineId", "type", "productId"]) {
    const value = String(fd.get(name));
    if (value) filters[name] = value;
  }
  const from = String(fd.get("from"));
  const to = String(fd.get("to"));
  if (from) filters.from = new Date(`${from}T00:00:00`).toISOString();
  if (to) filters.to = new Date(`${to}T23:59:59`).toISOString();
  return filters;
};

export default function MovementsPage() {
  const [machines, setMachines] = useState<MachineWithSlots[]>([]);
  const [products, setProducts] = useState<ProductOut[]>([]);
  const [filters, setFilters] = useState<Record<string, string>>({});
  const [movements, setMovements] = useState<MovementOut[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    machinesApi
      .list()
      .then((res) => setMachines(res.items as MachineWithSlots[]))
      .catch((err) => setError(errorText(err)));
    Promise.all([productsApi.list("ACTIVE"), productsApi.list("ARCHIVED")])
      .then(([active, archived]) => setProducts([...active.items, ...archived.items]))
      .catch((err) => setError(errorText(err)));
  }, []);

  useEffect(() => {
    setError(null);
    inventoryApi
      .movements(filters)
      .then((res) => setMovements(res.items))
      .catch((err) => setError(errorText(err)));
  }, [filters]);

  const handleFilter = (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setFilters(toFilters(new FormData(e.currentTarget)));
  };

  const machineName = (id: string) => machines.find((m) => m.id === id)?.name ?? id;
  const productName = (id: string) => products.find((p) => p.id === id)?.name ?? id;

  return (
    <div className="page">
      <div className="page-header">
        <h1>Premiki zaloge</h1>
        <nav>
          <Link to="/">← Nadzorna plošča</Link>
          <Link to="/inventory">Zaloga</Link>
        </nav>
      </div>

      <form className="card" style={{ marginBottom: 24 }} onSubmit={handleFilter}>
        <div style={{ display: "grid", gridTemplateColumns: "repeat(5, 1fr)", gap: 12 }}>
          <div className="form-group" style={{ margin: 0 }}>
            <label htmlFor="machineId">Avtomat</label>
            <select id="machineId" name="machineId">
              <option value="">Vsi</option>
              {machines.map((m) => (
                <option key={m.id} value={m.id}>
                  {m.name}
                </option>
              ))}
            </select>
          </div>
          <div className="form-group" style={{ margin: 0 }}>
            <label htmlFor="type">Vrsta</label>
            <select id="type" name="type">
              <option value="">Vse</option>
              {Object.entries(TYPES).map(([type, label]) => (
                <option key={type} value={type}>
                  {label}
                </option>
              ))}
            </select>
          </div>
          <div className="form-group" style={{ margin: 0 }}>
            <label htmlFor="productId">Izdelek</label>
            <select id="productId" name="productId">
              <option value="">Vsi</option>
              {products.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name}
                </option>
              ))}
            </select>
          </div>
          <Field name="from" label="Od" type="date" />
          <Field name="to" label="Do" type="date" />
        </div>
        <button type="submit" className="primary" style={{ marginTop: 16 }}>
          Prikaži
        </button>
      </form>

      {error && <div className="alert alert-error">{error}</div>}

      <table>
        <thead>
          <tr>
            <th>Čas</th>
            <th>Avtomat</th>
            <th>Predal</th>
            <th>Vrsta</th>
            <th>Izdelek</th>
            <th>Serija</th>
            <th>Količina</th>
          </tr>
        </thead>
        <tbody>
          {movements.map((m) => (
            <tr key={m.id}>
              <td>{new Date(m.occurredAt).toLocaleString("sl-SI")}</td>
              <td>{machineName(m.machineId)}</td>
              <td>{m.slot}</td>
              <td>{TYPES[m.type]}</td>
              <td>{productName(m.productId)}</td>
              <td>{m.batchCode}</td>
              <td>{m.quantity}</td>
            </tr>
          ))}
        </tbody>
      </table>
      {movements.length === 0 && (
        <p style={{ color: "var(--text-secondary)", marginTop: 12 }}>Ni premikov.</p>
      )}
    </div>
  );
}
