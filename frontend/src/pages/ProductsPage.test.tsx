import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import ProductsPage from "./ProductsPage";
import { fakeGateway, gatewayError, page, Routes } from "../test/fakeGateway";

const DAIRY = { id: "cat-1", name: "Mlečni izdelki", description: null };
const MILK = {
  id: "prod-1",
  name: "Mleko 1 l",
  categoryId: "cat-1",
  categoryName: "Mlečni izdelki",
  referencePrice: "1.20",
  currency: "EUR",
  weightGrams: 1030,
  allergens: ["mleko"],
  imageUrl: "https://placehold.co/400x300",
  status: "ACTIVE",
};
const SLOT = { machineId: "m1", slot: "A1", productId: "prod-1", quantity: 5 };

const routes: Routes = {
  "GET /api/v1/categories": [200, page([DAIRY])],
  "GET /api/v1/products": [200, page([MILK])],
};

const renderPage = () =>
  render(
    <MemoryRouter>
      <ProductsPage />
    </MemoryRouter>,
  );

const type = (label: string, value: string) =>
  fireEvent.change(screen.getByLabelText(label), { target: { value } });

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe("ProductsPage", () => {
  it("lists active products and filters archived ones", async () => {
    const sent = fakeGateway({ ...routes });
    renderPage();
    expect(await screen.findByText("Mleko 1 l")).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText("Stanje"), { target: { value: "ARCHIVED" } });

    await waitFor(() =>
      expect(sent.map((r) => r.path)).toContain("/api/v1/products?status=ARCHIVED&limit=200"),
    );
  });

  it("creates a product with a chosen category", async () => {
    const sent = fakeGateway({ ...routes, "POST /api/v1/products": [201, MILK] });
    renderPage();
    fireEvent.click(await screen.findByRole("button", { name: "+ Dodaj izdelek" }));

    type("Naziv", "Jogurt");
    type("Kategorija", "cat-1");
    type("Cena (EUR)", "0.90");
    type("Teža (g)", "180");
    type("Alergeni (ločeni z vejico)", "mleko, laktoza");
    type("URL slike", "https://placehold.co/400x300");
    fireEvent.click(screen.getByRole("button", { name: "Ustvari" }));

    await waitFor(() => expect(sent.map((r) => r.method)).toContain("POST"));
    expect(sent.find((r) => r.method === "POST")?.body).toEqual({
      name: "Jogurt",
      categoryId: "cat-1",
      referencePrice: "0.90",
      currency: "EUR",
      weightGrams: 180,
      allergens: ["mleko", "laktoza"],
      imageUrl: "https://placehold.co/400x300",
    });
  });

  it("shows PRODUCT_ARCHIVED in Slovenian when editing", async () => {
    fakeGateway({
      ...routes,
      "PUT /api/v1/products/prod-1": [409, gatewayError("PRODUCT_ARCHIVED")],
    });
    renderPage();
    fireEvent.click(await screen.findByRole("button", { name: "Uredi" }));
    expect(screen.getByLabelText("Kategorija")).toHaveValue("cat-1");

    fireEvent.click(screen.getByRole("button", { name: "Shrani" }));

    expect(await screen.findByText(/Izdelek je arhiviran/)).toBeInTheDocument();
  });

  it("warns with the number of slots before archiving (B6)", async () => {
    const confirm = vi.spyOn(window, "confirm").mockReturnValue(true);
    const sent = fakeGateway({
      ...routes,
      "GET /api/v1/inventory/product/prod-1": [200, page([SLOT, { ...SLOT, slot: "B2" }])],
      "DELETE /api/v1/products/prod-1": [204, null],
    });
    renderPage();

    fireEvent.click(await screen.findByRole("button", { name: "Arhiviraj" }));

    await waitFor(() => expect(sent.map((r) => r.method)).toContain("DELETE"));
    expect(confirm.mock.calls[0][0]).toContain("Število predalov z izdelkom: 2.");
  });

  it("does not archive when the operator cancels", async () => {
    vi.spyOn(window, "confirm").mockReturnValue(false);
    const sent = fakeGateway({
      ...routes,
      "GET /api/v1/inventory/product/prod-1": [200, page([])],
    });
    renderPage();

    fireEvent.click(await screen.findByRole("button", { name: "Arhiviraj" }));

    await waitFor(() => expect(window.confirm).toHaveBeenCalled());
    expect(sent.map((r) => r.method)).not.toContain("DELETE");
  });

  it("shows PRODUCT_HAS_OPEN_RECONCILIATION in Slovenian", async () => {
    vi.spyOn(window, "confirm").mockReturnValue(true);
    fakeGateway({
      ...routes,
      "GET /api/v1/inventory/product/prod-1": [200, page([])],
      "DELETE /api/v1/products/prod-1": [409, gatewayError("PRODUCT_HAS_OPEN_RECONCILIATION")],
    });
    renderPage();

    fireEvent.click(await screen.findByRole("button", { name: "Arhiviraj" }));

    expect(await screen.findByText(/odprto uskladitev/)).toBeInTheDocument();
  });
});
