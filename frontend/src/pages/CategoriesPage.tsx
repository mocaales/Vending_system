import { useEffect, useState, FormEvent } from "react";
import { Link } from "react-router-dom";
import { categoriesApi, CategoryOut, GatewayError } from "../api/client";
import { getErrorMessage } from "../api/errors";

const errorText = (err: unknown) =>
  err instanceof GatewayError ? getErrorMessage(err.body.code) : "Napaka.";

export default function CategoriesPage() {
  const [categories, setCategories] = useState<CategoryOut[]>([]);
  const [editing, setEditing] = useState<CategoryOut | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = () =>
    categoriesApi
      .list()
      .then((res) => setCategories(res.items))
      .catch((err) => setError(errorText(err)));

  useEffect(() => {
    load();
  }, []);

  const handleDelete = async (category: CategoryOut) => {
    if (!confirm(`Izbrišem kategorijo "${category.name}"?`)) return;
    setError(null);
    try {
      await categoriesApi.remove(category.id);
      load();
    } catch (err) {
      setError(errorText(err)); // npr. CATEGORY_IN_USE
    }
  };

  return (
    <div className="page">
      <div className="page-header">
        <h1>Kategorije</h1>
        <nav>
          <Link to="/">← Nadzorna plošča</Link>
          <Link to="/products">Izdelki</Link>
        </nav>
      </div>

      {/* key: ob menjavi kategorije se obrazec napolni na novo */}
      <CategoryForm
        key={editing?.id ?? "new"}
        category={editing}
        onDone={() => {
          setEditing(null);
          load();
        }}
      />

      {error && <div className="alert alert-error">{error}</div>}

      <table>
        <thead>
          <tr>
            <th>Ime</th>
            <th>Opis</th>
            <th>Dejanja</th>
          </tr>
        </thead>
        <tbody>
          {categories.map((c) => (
            <tr key={c.id}>
              <td>{c.name}</td>
              <td style={{ color: "var(--text-secondary)" }}>{c.description}</td>
              <td>
                <div className="flex-row">
                  <button onClick={() => setEditing(c)}>Uredi</button>
                  <button className="danger" onClick={() => handleDelete(c)}>
                    Izbriši
                  </button>
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function CategoryForm({ category, onDone }: { category: CategoryOut | null; onDone: () => void }) {
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const form = e.currentTarget;
    const fd = new FormData(form);
    const data = {
      name: String(fd.get("name")),
      description: String(fd.get("description")) || undefined,
    };
    try {
      if (category) await categoriesApi.update(category.id, data);
      else await categoriesApi.create(data);
      form.reset();
      onDone();
    } catch (err) {
      setError(errorText(err)); // npr. CATEGORY_NAME_TAKEN
    }
  };

  return (
    <div className="card" style={{ marginBottom: 24 }}>
      <h2 style={{ marginBottom: 16 }}>{category ? "Uredi kategorijo" : "Nova kategorija"}</h2>
      {error && <div className="alert alert-error">{error}</div>}
      <form onSubmit={handleSubmit}>
        <div style={{ display: "grid", gridTemplateColumns: "1fr 2fr", gap: 12 }}>
          <div className="form-group" style={{ margin: 0 }}>
            <label htmlFor="name">Ime</label>
            <input id="name" name="name" maxLength={60} defaultValue={category?.name} required />
          </div>
          <div className="form-group" style={{ margin: 0 }}>
            <label htmlFor="description">Opis</label>
            <input
              id="description"
              name="description"
              maxLength={200}
              defaultValue={category?.description ?? ""}
            />
          </div>
        </div>
        <div className="flex-row" style={{ marginTop: 16 }}>
          <button type="submit" className="primary">
            {category ? "Shrani" : "Dodaj"}
          </button>
          {category && (
            <button type="button" className="ghost" onClick={onDone}>
              Prekliči
            </button>
          )}
        </div>
      </form>
    </div>
  );
}
