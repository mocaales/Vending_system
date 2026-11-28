import { useEffect, useState, FormEvent } from "react";
import { Link } from "react-router-dom";
import {
  GatewayError,
  inventoryApi,
  MachineWithSlots,
  machinesApi,
  ProductOut,
  productsApi,
  RemovedOut,
  SlotOut,
} from "../api/client";
import { getErrorMessage } from "../api/errors";
import { Field } from "../components/Field";

const errorText = (err: unknown) =>
  err instanceof GatewayError ? getErrorMessage(err.body.code) : "Napaka.";

// Kaj operater dela s predalom: nastavitev (PUT), polnitev (POST refill) ali prag.
type Kind = "set" | "refill" | "threshold";
interface Action {
  slot: string;
  kind: Kind;
}

export default function InventoryPage() {
  const [machines, setMachines] = useState<MachineWithSlots[]>([]);
  const [products, setProducts] = useState<ProductOut[]>([]);
  const [machineId, setMachineId] = useState("");
  const [stock, setStock] = useState<SlotOut[]>([]);
  const [action, setAction] = useState<Action | null>(null);
  const [removed, setRemoved] = useState<RemovedOut[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    machinesApi
      .list()
      .then((res) =>
        setMachines((res.items as MachineWithSlots[]).filter((m) => m.status !== "RETIRED")),
      )
      .catch((err) => setError(errorText(err)));
    // arhivirani so potrebni za nazive: zaloga arhiviranega izdelka se še prodaja
    Promise.all([productsApi.list("ACTIVE"), productsApi.list("ARCHIVED")])
      .then(([active, archived]) => setProducts([...active.items, ...archived.items]))
      .catch((err) => setError(errorText(err)));
  }, []);

  const loadStock = () =>
    inventoryApi
      .machine(machineId)
      .then((res) => setStock(res.items))
      .catch((err) => setError(errorText(err)));

  useEffect(() => {
    setAction(null);
    setRemoved(null);
    setError(null);
    if (machineId) loadStock();
  }, [machineId]);

  const machine = machines.find((m) => m.id === machineId);
  const productName = (id: string | null) => products.find((p) => p.id === id)?.name ?? id;

  const handleEmpty = async (slot: string) => {
    if (!confirm(`Izpraznim predal ${slot}?`)) return;
    try {
      await inventoryApi.empty(machineId, slot);
      loadStock();
    } catch (err) {
      setError(errorText(err));
    }
  };

  const handleExpired = async () => {
    if (!confirm("Odpišem poteklo zalogo v vseh predalih avtomata?")) return;
    try {
      const res = await inventoryApi.removeExpired(machineId);
      setRemoved(res.removed);
      loadStock();
    } catch (err) {
      setError(errorText(err));
    }
  };

  return (
    <div className="page">
      <div className="page-header">
        <h1>Zaloga</h1>
        <nav>
          <Link to="/">← Nadzorna plošča</Link>
          <Link to="/inventory/movements">Premiki</Link>
        </nav>
      </div>

      <div className="flex-row" style={{ marginBottom: 16 }}>
        <select
          aria-label="Avtomat"
          value={machineId}
          onChange={(e) => setMachineId(e.target.value)}
        >
          <option value="">Izberi avtomat</option>
          {machines.map((m) => (
            <option key={m.id} value={m.id}>
              {m.name}
            </option>
          ))}
        </select>
        {machine && (
          <button className="danger" onClick={handleExpired}>
            Odpiši potekle
          </button>
        )}
      </div>

      {error && <div className="alert alert-error">{error}</div>}

      {removed && (
        <div className="alert alert-success">
          {removed.length === 0
            ? "Potekle zaloge ni."
            : `Odpisano: ${removed
                .map((r) => `${r.slot} (${productName(r.productId)}, ${r.quantity} kos)`)
                .join(", ")}`}
        </div>
      )}

      {machine && action && (
        <SlotForm
          key={`${action.slot}-${action.kind}`}
          machineId={machine.id}
          action={action}
          current={stock.find((s) => s.slot === action.slot)}
          products={products.filter((p) => p.status === "ACTIVE")}
          onDone={() => {
            setAction(null);
            loadStock();
          }}
          onCancel={() => setAction(null)}
        />
      )}

      {machine && (
        <table>
          <thead>
            <tr>
              <th>Predal</th>
              <th>Izdelek</th>
              <th>Serija</th>
              <th>Rok uporabe</th>
              <th>Količina</th>
              <th>Prag</th>
              <th>Dejanja</th>
            </tr>
          </thead>
          <tbody>
            {machine.slots.map(({ slotCode }) => {
              const slot = stock.find((s) => s.slot === slotCode); // brez zapisa: ni nastavljen
              const filled = Boolean(slot?.productId);
              return (
                <tr key={slotCode}>
                  <td>{slotCode}</td>
                  <td>
                    {filled ? productName(slot!.productId) : slot ? "prazen" : "ni nastavljen"}
                  </td>
                  <td>{slot?.batchCode}</td>
                  <td>{slot?.expiryDate}</td>
                  <td>{slot?.quantity ?? 0}</td>
                  <td>{slot?.lowStockThreshold}</td>
                  <td>
                    <div className="flex-row">
                      <button onClick={() => setAction({ slot: slotCode, kind: "set" })}>
                        Nastavi
                      </button>
                      {filled && (
                        <button onClick={() => setAction({ slot: slotCode, kind: "refill" })}>
                          Napolni
                        </button>
                      )}
                      {slot && (
                        <button onClick={() => setAction({ slot: slotCode, kind: "threshold" })}>
                          Prag
                        </button>
                      )}
                      {filled && (
                        <button className="danger" onClick={() => handleEmpty(slotCode)}>
                          Izprazni
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      )}
    </div>
  );
}

const TITLES: Record<Kind, string> = {
  set: "Nastavitev predala",
  refill: "Polnitev predala",
  threshold: "Prag nizke zaloge predala",
};

function SlotForm({
  machineId,
  action,
  current,
  products,
  onDone,
  onCancel,
}: {
  machineId: string;
  action: Action;
  current?: SlotOut;
  products: ProductOut[];
  onDone: () => void;
  onCancel: () => void;
}) {
  const [error, setError] = useState<string | null>(null);
  // en refillId na obrazec: ponovno pošiljanje iste polnitve ne podvoji količine
  const [refillId] = useState(() => crypto.randomUUID());
  const { slot, kind } = action;

  const handleSubmit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const fd = new FormData(e.currentTarget);
    const stock = {
      productId: String(fd.get("productId")),
      batchCode: String(fd.get("batchCode")),
      expiryDate: String(fd.get("expiryDate")),
      quantity: Number(fd.get("quantity")),
    };
    const threshold = Number(fd.get("lowStockThreshold"));
    try {
      if (kind === "set")
        await inventoryApi.setSlot(machineId, slot, { ...stock, lowStockThreshold: threshold });
      else if (kind === "refill")
        await inventoryApi.refill({ refillId, machineId, slot, ...stock });
      else await inventoryApi.setThreshold(machineId, slot, threshold);
      onDone();
    } catch (err) {
      setError(errorText(err)); // npr. SLOT_NOT_EMPTY, SLOT_HAS_OTHER_BATCH
    }
  };

  return (
    <div className="card" style={{ marginBottom: 24 }}>
      <h2 style={{ marginBottom: 16 }}>
        {TITLES[kind]} {slot}
      </h2>
      {error && <div className="alert alert-error">{error}</div>}
      <form onSubmit={handleSubmit}>
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
          {kind !== "threshold" && (
            <>
              <div className="form-group" style={{ margin: 0 }}>
                <label htmlFor="productId">Izdelek</label>
                <select
                  id="productId"
                  name="productId"
                  defaultValue={current?.productId ?? ""}
                  required
                >
                  <option value="">Izberi izdelek</option>
                  {products.map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.name}
                    </option>
                  ))}
                </select>
              </div>
              <Field
                name="batchCode"
                label="Serija"
                maxLength={40}
                defaultValue={current?.batchCode ?? ""}
                required
              />
              <Field
                name="expiryDate"
                label="Rok uporabe"
                type="date"
                defaultValue={current?.expiryDate ?? ""}
                required
              />
              <Field
                name="quantity"
                label={kind === "refill" ? "Dodana količina" : "Količina"}
                type="number"
                min={1}
                max={1000}
                defaultValue={kind === "set" ? current?.quantity || "" : ""}
                required
              />
            </>
          )}
          {kind !== "refill" && (
            <Field
              name="lowStockThreshold"
              label="Prag nizke zaloge"
              type="number"
              min={0}
              max={1000}
              defaultValue={current?.lowStockThreshold ?? 2}
              required
            />
          )}
        </div>
        <div className="flex-row" style={{ marginTop: 16 }}>
          <button type="submit" className="primary">
            Shrani
          </button>
          <button type="button" className="ghost" onClick={onCancel}>
            Prekliči
          </button>
        </div>
      </form>
    </div>
  );
}
