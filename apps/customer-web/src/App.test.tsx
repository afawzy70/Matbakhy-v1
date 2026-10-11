import { brand } from "@matbakhy/design-tokens";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { App, title } from "./App";

afterEach(cleanup);

describe("customer-web shell", () => {
  it("starts in Arabic right-to-left with the token brand name", () => {
    render(<App />);
    expect(screen.getByRole("heading", { level: 1 }).textContent).toBe(title("ar"));
    expect(title("ar")).toContain(brand.name.ar);
    expect(document.documentElement.dir).toBe("rtl");
    expect(document.documentElement.lang).toBe("ar");
    expect(document.title).toBe(title("ar"));
  });

  it("switches to English left-to-right", () => {
    render(<App />);
    fireEvent.click(screen.getByRole("button"));
    expect(screen.getByRole("heading", { level: 1 }).textContent).toBe(title("en"));
    expect(title("en")).toContain(brand.name.en);
    expect(document.documentElement.dir).toBe("ltr");
  });
});
