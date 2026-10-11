import js from "@eslint/js";
import globals from "globals";
import reactHooks from "eslint-plugin-react-hooks";
import tseslint from "typescript-eslint";

export default tseslint.config(
  {
    ignores: [
      "**/node_modules/**",
      "**/dist/**",
      "**/build/**",
      "**/.expo/**",
      "**/coverage/**",
      "backend/**",
      "apps/customer-mobile/android/**",
      "apps/customer-mobile/ios/**",
      "verification/**",
    ],
  },
  js.configs.recommended,
  ...tseslint.configs.strict,
  {
    files: ["**/*.{ts,tsx}"],
    plugins: { "react-hooks": reactHooks },
    languageOptions: { globals: { ...globals.browser } },
    rules: {
      ...reactHooks.configs.recommended.rules,
      "@typescript-eslint/no-explicit-any": "error",
    },
  },
  {
    files: ["**/*.{js,mjs,cjs}", "**/*.config.{ts,js}"],
    languageOptions: { globals: { ...globals.node } },
  },
  {
    // Static browser references for design review (MB-004 prototype and the owner-selected approved direction), loaded
    // with classic script tags.
    files: ["docs/design/prototype/**/*.js", "docs/design/approved/**/*.js"],
    languageOptions: { sourceType: "script", globals: { ...globals.browser } },
  },
  {
    // CommonJS Node scripts: require() is their module system. The design reference checks also pass callbacks that
    // run inside the page (Playwright evaluate, jsdom), hence browser globals there.
    files: ["**/*.cjs"],
    languageOptions: { sourceType: "commonjs" },
    rules: { "@typescript-eslint/no-require-imports": "off" },
  },
  {
    files: ["docs/design/approved/check*.cjs"],
    languageOptions: { globals: { ...globals.node, ...globals.browser } },
  },
);
