import { brand } from "@matbakhy/design-tokens";
import { fireEvent, render, screen } from "@testing-library/react-native";
import { App } from "./App";

describe("customer-mobile shell", () => {
  it("starts in Arabic and switches to English", async () => {
    await render(<App />);
    expect(screen.getByRole("header")).toHaveTextContent(brand.name.ar);
    await fireEvent.press(screen.getByRole("button"));
    expect(screen.getByRole("header")).toHaveTextContent(brand.name.en);
  });
});
