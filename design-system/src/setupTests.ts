// Vitest global setup — registers @testing-library/jest-dom matchers
// (toBeInTheDocument, toHaveValue, etc.) and auto-cleans the DOM between tests.
import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterEach } from "vitest";

afterEach(() => {
	cleanup();
});
