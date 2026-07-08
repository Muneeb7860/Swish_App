/// <reference types="vite/client" />
/// <reference types="vitest/globals" />

// Side-effect CSS imports (e.g. `import "./tokens.css"`) have no type info;
// declare them so `tsc --noEmit` accepts them — here and in the shared-ui
// sources this package re-exports (file:../shared-ui ships raw .ts).
declare module "*.css";
