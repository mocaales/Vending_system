import { render, screen } from "@testing-library/react";
import App from "./App";
import { getErrorMessage } from "./api/errors";

describe("App", () => {
  it("redirects unauthenticated user to login", () => {
    sessionStorage.clear();
    render(<App />);
    expect(screen.getByRole("heading", { name: /vending system/i })).toBeInTheDocument();
  });
});

describe("getErrorMessage", () => {
  it("maps INVALID_CREDENTIALS to Slovenian", () => {
    expect(getErrorMessage("INVALID_CREDENTIALS")).toBe("Napačen e-naslov ali geslo.");
  });

  it("maps RATE_LIMITED to Slovenian", () => {
    expect(getErrorMessage("RATE_LIMITED")).toMatch(/preveč/i);
  });

  it("returns fallback for unknown code", () => {
    expect(getErrorMessage("UNKNOWN_CODE")).toContain("UNKNOWN_CODE");
  });
});
