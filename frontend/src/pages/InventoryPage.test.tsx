import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import InventoryPage from "./InventoryPage";
import { fakeGateway, gatewayError, page, Routes, SentRequest } from "../test/fakeGateway";

const MACHINE = {
  id: "m1",
  name: "MB FERI",
  status: "ACTIVE",
  slots: [{ slotCode: "A1" }, { slotCode: "A2" }, { slotCode: "B1" }],
};
const MILK = { id: "p1", name: "Mleko 1 l", status: "ACTIVE" };
const OLD = { id: "p2", name: "Stari jogurt", status: "ARCHIVED" };
const A1 = {
  machineId: "m1",
  slot: "A1",
  productId: "p1",
  batchCode: "L1",
  expiryDate: "2026-12-10",
  quantity: 5,
  lowStockThreshold: 2,
};
const A2 = { ...A1, slot: "A2", productId: null, batchCode: null, expiryDate: null, quantity: 0 };

const routes: Routes = {
  "GET /api/v1/machines": [200, page([MACHINE])],
  "GET /api/v1/products?status=ACTIVE&limit=200": [200, page([MILK])],
  "GET /api/v1/products?status=ARCHIVED&limit=200": [200, page([OLD])],
  "GET /api/v1/inventory/m1": [200, page([A1, A2])],
};

const openMachine = async (extra: Routes = {}) => {
  const sent = fakeGateway({ ...routes, ...extra });
  render(
    <MemoryRouter>
      <InventoryPage />
    </MemoryRouter>,
  );
  await screen.findByRole("option", { name: "MB FERI" });
  fireEvent.change(screen.getByLabelText("Avtomat"), { target: { value: "m1" } });
  await screen.findByText("Mleko 1 l");
  return sent;
};

const row = (slot: string) => screen.getByRole("cell", { name: slot }).closest("tr")!;
const clickIn = (slot: string, button: string) =>
  fireEvent.click([...row(slot).querySelectorAll("button")].find((b) => b.textContent === button)!);
const type = (label: string, value: string) =>
  fireEvent.change(screen.getByLabelText(label), { target: { value } });
const sentTo = (sent: SentRequest[], method: string, path: string) =>
  sent.filter((r) => r.method === method && r.path === path);

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe("InventoryPage", () => {
  it("shows every machine slot, also empty and not configured", async () => {
    await openMachine();

    expect(row("A1")).toHaveTextContent("Mleko 1 l");
    expect(row("A1")).toHaveTextContent("L1");
    expect(row("A2")).toHaveTextContent("prazen");
    expect(row("B1")).toHaveTextContent("ni nastavljen");
  });

  it("sets a slot with PUT and shows SLOT_NOT_EMPTY", async () => {
    const sent = await openMachine({
      "PUT /api/v1/inventory/m1/B1": [409, gatewayError("SLOT_NOT_EMPTY")],
    });
    clickIn("B1", "Nastavi");

    type("Izdelek", "p1");
    type("Serija", "L2");
    type("Rok uporabe", "2026-12-20");
    type("Količina", "8");
    type("Prag nizke zaloge", "3");
    fireEvent.click(screen.getByRole("button", { name: "Shrani" }));

    expect(await screen.findByText(/Predal najprej izpraznite/)).toBeInTheDocument();
    expect(sentTo(sent, "PUT", "/api/v1/inventory/m1/B1")[0].body).toEqual({
      productId: "p1",
      batchCode: "L2",
      expiryDate: "2026-12-20",
      quantity: 8,
      lowStockThreshold: 3,
    });
  });

  it("refills with the same refillId when sent again (SLOT_HAS_OTHER_BATCH)", async () => {
    const sent = await openMachine({
      "POST /api/v1/inventory/refill": [409, gatewayError("SLOT_HAS_OTHER_BATCH")],
    });
    clickIn("A1", "Napolni");
    expect(screen.getByLabelText("Serija")).toHaveValue("L1"); // privzeto trenutna serija

    type("Dodana količina", "4");
    fireEvent.click(screen.getByRole("button", { name: "Shrani" }));
    expect(await screen.findByText(/V predalu je druga serija/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Shrani" }));

    await waitFor(() => expect(sentTo(sent, "POST", "/api/v1/inventory/refill")).toHaveLength(2));
    const [first, second] = sentTo(sent, "POST", "/api/v1/inventory/refill").map(
      (r) => r.body as { refillId: string },
    );
    expect(first).toMatchObject({ machineId: "m1", slot: "A1", productId: "p1", quantity: 4 });
    expect(second.refillId).toBe(first.refillId);
  });

  it("changes the low stock threshold", async () => {
    const sent = await openMachine({ "PUT /api/v1/inventory/m1/A2/threshold": [200, A2] });
    clickIn("A2", "Prag");

    type("Prag nizke zaloge", "5");
    fireEvent.click(screen.getByRole("button", { name: "Shrani" }));

    await waitFor(() =>
      expect(sentTo(sent, "PUT", "/api/v1/inventory/m1/A2/threshold")[0]?.body).toEqual({
        lowStockThreshold: 5,
      }),
    );
  });

  it("empties a slot after confirmation", async () => {
    vi.spyOn(window, "confirm").mockReturnValue(true);
    const sent = await openMachine({ "DELETE /api/v1/inventory/m1/A1": [204, null] });

    clickIn("A1", "Izprazni");

    await waitFor(() => expect(sentTo(sent, "DELETE", "/api/v1/inventory/m1/A1")).toHaveLength(1));
  });

  it("removes expired stock and lists removed slots", async () => {
    vi.spyOn(window, "confirm").mockReturnValue(true);
    await openMachine({
      "DELETE /api/v1/inventory/expired": [
        200,
        { removed: [{ slot: "A1", productId: "p2", batchCode: "L0", quantity: 3 }] },
      ],
    });

    fireEvent.click(screen.getByRole("button", { name: "Odpiši potekle" }));

    expect(await screen.findByText("Odpisano: A1 (Stari jogurt, 3 kos)")).toBeInTheDocument();
  });
});
