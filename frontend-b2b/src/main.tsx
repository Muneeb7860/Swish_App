import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import "@swish/shared-ui/tokens";
import "./index.css";
import App from "./App";

createRoot(document.getElementById("root") as HTMLElement).render(
	<StrictMode>
		<App />
	</StrictMode>,
);
