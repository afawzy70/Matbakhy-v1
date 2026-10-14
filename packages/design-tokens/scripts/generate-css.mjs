// Writes src/generated/tokens.css from tokens.json. The test suite fails when the file is stale.
import { readFileSync, writeFileSync } from "node:fs";
import { renderCss } from "../src/css.ts";

const tokens = JSON.parse(readFileSync(new URL("../tokens.json", import.meta.url), "utf8"));
writeFileSync(new URL("../src/generated/tokens.css", import.meta.url), renderCss(tokens));
console.log("wrote src/generated/tokens.css");
