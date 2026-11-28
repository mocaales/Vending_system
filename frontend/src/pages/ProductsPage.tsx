import { useEffect, useState, FormEvent, InputHTMLAttributes } from "react";
import { Link } from "react-router-dom";
import {
  categoriesApi,
  CategoryOut,
  GatewayError,
  inventoryApi,
  ProductIn,
  ProductOut,
  productsApi,
} from "../api/client";
import { getErrorMessage } from "../api/errors";

const errorText = (err: unknown) =>
  err instanceof GatewayError ? getErrorMessage(err.body.code) : "Napaka.";

type Status = "ACTIVE" | "ARCHIVED";

export default function ProductsPage() {
  const [products, setProducts] = useState<ProductOut[]>([]);
  const [categories, setCategories] = useState<CategoryOut[]>([]);
  const [status, setStatus] = useState<Status>("ACTIVE");
  const [query, setQuery] = useState("");
  const [editing, setEditing] = useState<ProductOut | "new" | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = () =>
    productsApi
      .list(status, query)
      .then((res) => setProducts(res.items))
      .catch((err) => setError(errorText(err)));

  useEffect(() => {
    load();
  }, [status, query]);

  useEffect(() => {
    categoriesApi
      .list()
      .then((res) => setCategories(res.items))
      .catch((err) => setError(errorText(err)));
  }, []);

  const handleSearch = (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setQuery(String(new FormData(e.currentTarget).get("q")));
  };

  // B6: izdelek na zalogi se sme arhivirati, a UI prej pokaže, v koliko predalih je.
  const handleArchive = async (product: ProductOut) => {
    setError(null);
    try {
      const slots = await inventoryApi.productSlots(product.id);
      const warning =
        slots.total > 0
          ? `Število predalov z izdelkom: ${slots.total}. Zaloga se bo prodajala do praznjenja.\n`
          : "";
      if (!confirm(`${warning}Arhiviram izdelek "${product.name}"?`)) return;
      await productsApi.archive(product.id);
      load();
    } catch (err) {
      setError(errorText(err)); // npr. PRODUCT_HAS_OPEN_RECONCILIATION
    }
  };

  return (
    <div className="page">
      <div className="page-header">
        <h1>Izdelki</h1>
        <nav>
          <Link to="/">← Nadzorna plošča</Link>
          <Link to="/categories">Kategorije</Link>
          <button className="primary" onClick={() => setEditing("new")}>
            + Dodaj izdelek
          </button>
        </nav>
      </div>

      {editing && (
        <ProductForm
          key={editing === "new" ? "new" : editing.id}
          product={editing === "new" ? null : editing}
          categories={categories}
          onSaved={() => {
            setEditing(null);
            load();
          }}
          onCancel={() => setEditing(null)}
        />
      )}

      {error && <div className="alert alert-error">{error}</div>}

      <form className="flex-row" style={{ marginBottom: 16 }} onSubmit={handleSearch}>
        <input name="q" placeholder="Išči po nazivu" aria-label="Iskanje" />
        <button type="submit">Išči</button>
        <select
          aria-label="Stanje"
          value={status}
          onChange={(e) => setStatus(e.target.value as Status)}
        >
          <option value="ACTIVE">Aktivni</option>
          <option value="ARCHIVED">Arhivirani</option>
        </select>
      </form>

      <table>
        <thead>
          <tr>
            <th>Naziv</th>
            <th>Kategorija</th>
            <th>Cena</th>
            <th>Teža</th>
            <th>Alergeni</th>
            <th>Dejanja</th>
          </tr>
        </thead>
        <tbody>
          {products.map((p) => (
            <tr key={p.id}>
              <td>{p.name}</td>
              <td>{p.categoryName}</td>
              <td>
                {p.referencePrice} {p.currency}
              </td>
              <td>{p.weightGrams} g</td>
              <td style={{ color: "var(--text-secondary)" }}>{p.allergens.join(", ")}</td>
              <td>
                {p.status === "ACTIVE" ? (
                  <div className="flex-row">
                    <button onClick={() => setEditing(p)}>Uredi</button>
                    <button className="danger" onClick={() => handleArchive(p)}>
                      Arhiviraj
                    </button>
                  </div>
                ) : (
                  <span className="badge badge-inactive">Arhiviran</span>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function Field({
  name,
  label,
  defaultValue,
  ...input
}: {
  name: string;
  label: string;
  defaultValue?: string | number;
} & InputHTMLAttributes<HTMLInputElement>) {
  return (
    <div className="form-group" style={{ margin: 0 }}>
      <label htmlFor={name}>{label}</label>
      <input id={name} name={name} defaultValue={defaultValue} {...input} />
    </div>
  );
}

function ProductForm({
  product,
  categories,
  onSaved,
  onCancel,
}: {
  product: ProductOut | null;
  categories: CategoryOut[];
  onSaved: () => void;
  onCancel: () => void;
}) {
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const fd = new FormData(e.currentTarget);
    const data: ProductIn = {
      name: String(fd.get("name")),
      categoryId: String(fd.get("categoryId")),
      referencePrice: String(fd.get("referencePrice")),
      currency: "EUR",
      weightGrams: Number(fd.get("weightGrams")),
      allergens: String(fd.get("allergens"))
        .split(",")
        .map((a) => a.trim())
        .filter(Boolean),
      imageUrl: String(fd.get("imageUrl")),
    };
    try {
      if (product) await productsApi.update(product.id, data);
      else await productsApi.create(data);
      onSaved();
    } catch (err) {
      setError(errorText(err)); // npr. PRODUCT_ARCHIVED, REFERENCE_NOT_FOUND
    }
  };

  return (
    <div className="card" style={{ marginBottom: 24 }}>
      <h2 style={{ marginBottom: 16 }}>{product ? "Uredi izdelek" : "Nov izdelek"}</h2>
      {error && <div className="alert alert-error">{error}</div>}
      <form onSubmit={handleSubmit}>
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
          <Field name="name" label="Naziv" maxLength={100} defaultValue={product?.name} required />
          <div className="form-group" style={{ margin: 0 }}>
            <label htmlFor="categoryId">Kategorija</label>
            <select id="categoryId" name="categoryId" defaultValue={product?.categoryId} required>
              <option value="">Izberi kategorijo</option>
              {categories.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
          </div>
          <Field
            name="referencePrice"
            label="Cena (EUR)"
            pattern="\d{1,10}(\.\d{1,2})?"
            placeholder="1.20"
            defaultValue={product?.referencePrice}
            required
          />
          <Field
            name="weightGrams"
            label="Teža (g)"
            type="number"
            min={1}
            max={100000}
            defaultValue={product?.weightGrams}
            required
          />
          <Field
            name="allergens"
            label="Alergeni (ločeni z vejico)"
            defaultValue={product?.allergens.join(", ")}
          />
          <Field
            name="imageUrl"
            label="URL slike"
            type="url"
            maxLength={500}
            defaultValue={product?.imageUrl}
            required
          />
        </div>
        <div className="flex-row" style={{ marginTop: 16 }}>
          <button type="submit" className="primary">
            {product ? "Shrani" : "Ustvari"}
          </button>
          <button type="button" className="ghost" onClick={onCancel}>
            Prekliči
          </button>
        </div>
      </form>
    </div>
  );
}
