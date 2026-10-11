import tokens from "@matbakhy/design-tokens/tokens.json" with { type: "json" };
import react from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";

export default defineConfig({
  plugins: [
    react(),
    { name: "brand-title", transformIndexHtml: (html: string) => html.replace("%BRAND_TITLE%", tokens.brand.name.ar) },
  ],
  server: { port: 5173, strictPort: true },
  test: { environment: "jsdom" },
});
