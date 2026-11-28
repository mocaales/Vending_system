import { fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import CategoriesPage from "./CategoriesPage";
import { fakeGateway, gatewayError, page } from "../test/fakeGateway";

const DAIRY = { id: "cat-1", name: "Mlečni izdelki", description: "Hlajeno" };

const renderPage = () =>
  render(
    <MemoryRouter>
      <CategoriesPage />
    </MemoryRouter>,
  );

const fillAndSubmit = (name: string, button: string) => {
  fireEvent.change(screen.getByLabelText("Ime"), { target: { value: name } });
  fireEvent.click(screen.getByRole("button", { name: button }));
};

afterEach(() => vi.unstubAllGlobals());

describe("CategoriesPage", () => {
  it("lists categories and creates a new one", async () => {
    const sent = fakeGateway({
      "GET /api/v1/categories": [200, page([DAIRY])],
      "POST /api/v1/categories": [201, { id: "cat-2", name: "Meso", description: null }],
    });
    renderPage();
    expect(await screen.findByText("Mlečni izdelki")).toBeInTheDocument();

    fillAndSubmit("Meso", "Dodaj");

    await screen.findByText("Mlečni izdelki"); // ponovno naložen seznam
    expect(sent).toContainEqual({
      method: "POST",
      path: "/api/v1/categories",
      body: { name: "Meso" },
    });
  });

  it("shows CATEGORY_NAME_TAKEN in Slovenian", async () => {
    fakeGateway({
      "GET /api/v1/categories": [200, page([DAIRY])],
      "POST /api/v1/categories": [409, gatewayError("CATEGORY_NAME_TAKEN")],
    });
    renderPage();
    await screen.findByText("Mlečni izdelki");

    fillAndSubmit("mlečni izdelki", "Dodaj");

    expect(await screen.findByText("Kategorija s tem imenom že obstaja.")).toBeInTheDocument();
  });

  it("edits a category with PUT", async () => {
    const sent = fakeGateway({
      "GET /api/v1/categories": [200, page([DAIRY])],
      "PUT /api/v1/categories/cat-1": [200, { ...DAIRY, name: "Mleko" }],
    });
    renderPage();
    fireEvent.click(await screen.findByRole("button", { name: "Uredi" }));
    expect(screen.getByLabelText("Ime")).toHaveValue("Mlečni izdelki");

    fillAndSubmit("Mleko", "Shrani");

    await screen.findByRole("heading", { name: "Nova kategorija" });
    expect(sent).toContainEqual({
      method: "PUT",
      path: "/api/v1/categories/cat-1",
      body: { name: "Mleko", description: "Hlajeno" },
    });
  });

  it("shows CATEGORY_IN_USE when deleting a used category", async () => {
    vi.spyOn(window, "confirm").mockReturnValue(true);
    fakeGateway({
      "GET /api/v1/categories": [200, page([DAIRY])],
      "DELETE /api/v1/categories/cat-1": [409, gatewayError("CATEGORY_IN_USE")],
    });
    renderPage();

    fireEvent.click(await screen.findByRole("button", { name: "Izbriši" }));

    expect(await screen.findByText(/ker jo uporablja izdelek/)).toBeInTheDocument();
  });
});
