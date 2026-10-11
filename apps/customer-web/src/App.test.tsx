import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { App } from "./App";

afterEach(cleanup);

describe("customer-web shell", () => {
  it("starts in Arabic right-to-left", () => {
    render(<App />);
    expect(screen.getByRole("heading", { level: 1 }).textContent).toBe("مطبخي");
    expect(document.documentElement.dir).toBe("rtl");
    expect(document.documentElement.lang).toBe("ar");
  });

  it("switches to English left-to-right", () => {
    render(<App />);
    fireEvent.click(screen.getByRole("button"));
    expect(screen.getByRole("heading", { level: 1 }).textContent).toBe("Customer web shell");
    expect(document.documentElement.dir).toBe("ltr");
  });
});
