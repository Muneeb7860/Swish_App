import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { Modal } from "../Modal";

describe("Modal", () => {
	describe("visibility", () => {
		it("renders nothing when isOpen is false", () => {
			const { container } = render(
				<Modal isOpen={false} onClose={() => {}}>
					<p>Body</p>
				</Modal>,
			);
			expect(container).toBeEmptyDOMElement();
			expect(screen.queryByText("Body")).not.toBeInTheDocument();
		});

		it("renders overlay, content and children when open", () => {
			const { container } = render(
				<Modal isOpen onClose={() => {}}>
					<p>Body content</p>
				</Modal>,
			);
			expect(
				container.querySelector(".swish-modal-overlay"),
			).toBeInTheDocument();
			expect(
				container.querySelector(".swish-modal-content"),
			).toBeInTheDocument();
			expect(screen.getByText("Body content")).toBeInTheDocument();
		});
	});

	describe("header & title", () => {
		it("renders the title when provided", () => {
			render(
				<Modal isOpen onClose={() => {}} title="Certificate Desk">
					<p>x</p>
				</Modal>,
			);
			expect(screen.getByText("Certificate Desk")).toBeInTheDocument();
		});

		it("shows a close button by default", () => {
			render(
				<Modal isOpen onClose={() => {}} title="T">
					<p>x</p>
				</Modal>,
			);
			expect(screen.getByRole("button", { name: "Close" })).toBeInTheDocument();
		});

		it("hides the close button when hideCloseButton is set", () => {
			render(
				<Modal isOpen onClose={() => {}} title="T" hideCloseButton>
					<p>x</p>
				</Modal>,
			);
			expect(
				screen.queryByRole("button", { name: "Close" }),
			).not.toBeInTheDocument();
		});
	});

	describe("closing", () => {
		it("calls onClose when the close button is clicked", () => {
			const onClose = vi.fn();
			render(
				<Modal isOpen onClose={onClose} title="T">
					<p>x</p>
				</Modal>,
			);
			fireEvent.click(screen.getByRole("button", { name: "Close" }));
			expect(onClose).toHaveBeenCalledTimes(1);
		});

		it("calls onClose when the backdrop overlay is clicked", () => {
			const onClose = vi.fn();
			const { container } = render(
				<Modal isOpen onClose={onClose}>
					<p>x</p>
				</Modal>,
			);
			const overlay = container.querySelector(".swish-modal-overlay");
			expect(overlay).not.toBeNull();
			if (overlay) fireEvent.click(overlay);
			expect(onClose).toHaveBeenCalledTimes(1);
		});

		it("does NOT close when the content panel is clicked", () => {
			const onClose = vi.fn();
			const { container } = render(
				<Modal isOpen onClose={onClose}>
					<p>x</p>
				</Modal>,
			);
			const content = container.querySelector(".swish-modal-content");
			expect(content).not.toBeNull();
			if (content) fireEvent.click(content);
			expect(onClose).not.toHaveBeenCalled();
		});

		it("does NOT close on backdrop click when disableBackdropClose is set", () => {
			const onClose = vi.fn();
			const { container } = render(
				<Modal isOpen onClose={onClose} disableBackdropClose>
					<p>x</p>
				</Modal>,
			);
			const overlay = container.querySelector(".swish-modal-overlay");
			expect(overlay).not.toBeNull();
			if (overlay) fireEvent.click(overlay);
			expect(onClose).not.toHaveBeenCalled();
		});

		it("calls onClose when Escape is pressed", () => {
			const onClose = vi.fn();
			render(
				<Modal isOpen onClose={onClose}>
					<p>x</p>
				</Modal>,
			);
			fireEvent.keyDown(window, { key: "Escape" });
			expect(onClose).toHaveBeenCalledTimes(1);
		});

		it("does not listen for Escape once closed", () => {
			const onClose = vi.fn();
			const { rerender } = render(
				<Modal isOpen onClose={onClose}>
					<p>x</p>
				</Modal>,
			);
			rerender(
				<Modal isOpen={false} onClose={onClose}>
					<p>x</p>
				</Modal>,
			);
			fireEvent.keyDown(window, { key: "Escape" });
			expect(onClose).not.toHaveBeenCalled();
		});
	});

	describe("actions & styling", () => {
		it("renders the actions footer when provided", () => {
			render(
				<Modal
					isOpen
					onClose={() => {}}
					actions={<button type="button">Download</button>}
				>
					<p>x</p>
				</Modal>,
			);
			expect(
				screen.getByRole("button", { name: "Download" }),
			).toBeInTheDocument();
		});

		it("applies accentColor and maxWidth to the content panel", () => {
			const { container } = render(
				<Modal
					isOpen
					onClose={() => {}}
					accentColor="rgb(1, 2, 3)"
					maxWidth={420}
				>
					<p>x</p>
				</Modal>,
			);
			const content = container.querySelector(
				".swish-modal-content",
			) as HTMLElement;
			expect(content.style.borderColor).toBe("rgb(1, 2, 3)");
			expect(content.style.maxWidth).toBe("420px");
		});
	});
});
