import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { AuthPortal, Modal, ProductCardSkeleton, Skeleton } from "../index";

// design-system re-exports its components from @swish/shared-ui. This smoke test
// verifies the re-export wiring resolves and the components render — the full
// behavioural coverage lives in shared-ui's own suite.
describe("design-system re-exports from @swish/shared-ui", () => {
	it("exposes the expected component identities", () => {
		expect(typeof AuthPortal).toBe("function");
		expect(typeof Skeleton).toBe("function");
		expect(typeof Modal).toBe("function");
		expect(typeof ProductCardSkeleton).toBe("function");
	});

	it("renders a re-exported Skeleton", () => {
		const { container } = render(<Skeleton variant="generic-card" />);
		expect(container.querySelector(".glass-card")).toBeInTheDocument();
	});

	it("renders a re-exported AuthPortal", () => {
		const { getByText } = render(
			<AuthPortal role="customer" onAuthSuccess={() => {}} />,
		);
		expect(getByText("Customer Login")).toBeInTheDocument();
	});
});
