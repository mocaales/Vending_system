import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import MovementsPage from "./MovementsPage";
import { fakeGateway, page } from "../test/fakeGateway";

const SALE = {
  id: "mv1",
  machineId: "m1",
  slot: "A1",
  type: "SALE",
  productId: "p1",
  batchCode: "L1",
  quantity: 1,
  saleId: "s1",
  refillId: null,
  occurredAt: "2026-11-28T08:30:00Z",
};

const renderPage = () => {
  const sent = fakeGateway({
    "GET /api/v1/machines": [200, page([{ id: "m1", name: "MB FERI", status: "ACTIVE" }])],
    "GET /api/v1/products?status=ACTIVE&limit=200": [200, page([{ id: "p1", name: "Mleko" }])],
    "GET /api/v1/products?status=ARCHIVED&limit=200": [200, page([])],
    "GET /api/v1/inventory/movements": [200, page([SALE])],
  });
  render(
    <MemoryRouter>
      <MovementsPage />
    </MemoryRouter>,
  );
  return sent;
};

afterEach(() => vi.unstubAllGlobals());

describe("MovementsPage", () => {
  it("lists movements with names and Slovenian types", async () => {
    renderPage();

    const cell = await screen.findByRole("cell", { name: "Prodaja" });
    expect(cell.closest("tr")).toHaveTextContent("MB FERI");
    expect(cell.closest("tr")).toHaveTextContent("Mleko");
  });

  it("sends the chosen filters", async () => {
    const sent = renderPage();
    await screen.findByRole("option", { name: "MB FERI" });

    fireEvent.change(screen.getByLabelText("Avtomat"), { target: { value: "m1" } });
    fireEvent.change(screen.getByLabelText("Vrsta"), { target: { value: "REFILL" } });
    fireEvent.change(screen.getByLabelText("Od"), { target: { value: "2026-11-01" } });
    fireEvent.click(screen.getByRole("button", { name: "Prikaži" }));

    await waitFor(() => {
      const last = sent.filter((r) => r.path.startsWith("/api/v1/inventory/movements")).at(-1)!;
      const params = new URL(last.path, "http://ui").searchParams;
      expect(params.get("machineId")).toBe("m1");
      expect(params.get("type")).toBe("REFILL");
      expect(params.get("from")).toBe(new Date("2026-11-01T00:00:00").toISOString());
      expect(params.has("productId")).toBe(false);
    });
  });
});
