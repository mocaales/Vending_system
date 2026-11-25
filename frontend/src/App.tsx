const apiUrl = import.meta.env.VITE_API_URL ?? "";

export default function App() {
  return (
    <main>
      <h1>Vending System</h1>
      {apiUrl && <p data-testid="api-url">{apiUrl}</p>}
    </main>
  );
}
