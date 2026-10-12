import { registerRootComponent } from "expo";
import { createElement } from "react";
import { App } from "./src/App";

// Production entry: real configuration, secure store and system-browser sign-in (no injected test doubles).
registerRootComponent(() => createElement(App));
