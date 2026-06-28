import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import {
	GenericCardSkeleton,
	ProductCardSkeleton,
	ProductGridSkeleton,
	Skeleton,
	TableRowsSkeleton,
} from "../Skeleton";

describe("Skeleton Component", () => {
	describe("product-card variant", () => {
		it("renders product card skeleton", () => {
			const { container } = render(<Skeleton variant="product-card" />);
			expect(container.querySelector(".product-card")).toBeInTheDocument();
			expect(container.querySelector(".skeleton-image")).toBeInTheDocument();
		});

		it("applies glass-card class", () => {
			const { container } = render(<Skeleton variant="product-card" />);
			expect(container.querySelector(".glass-card")).toBeInTheDocument();
		});
	});

	describe("product-grid variant", () => {
		it("renders grid with default count (8)", () => {
			const { container } = render(<Skeleton variant="product-grid" />);
			const cards = container.querySelectorAll(".product-card");
			expect(cards).toHaveLength(8);
		});

		it("renders grid with custom count", () => {
			const { container } = render(
				<Skeleton variant="product-grid" count={5} />,
			);
			const cards = container.querySelectorAll(".product-card");
			expect(cards).toHaveLength(5);
		});

		it("applies products-grid class", () => {
			const { container } = render(<Skeleton variant="product-grid" />);
			expect(container.querySelector(".products-grid")).toBeInTheDocument();
		});
	});

	describe("table-rows variant", () => {
		it("renders table with default rows (5)", () => {
			const { container } = render(<Skeleton variant="table-rows" />);
			const rows = container.querySelectorAll(".skeleton-table-row");
			expect(rows).toHaveLength(5);
		});

		it("renders correct number of columns per row", () => {
			const { container } = render(
				<Skeleton variant="table-rows" rows={2} cols={3} />,
			);
			const rows = container.querySelectorAll(".skeleton-table-row");
			expect(rows).toHaveLength(2);
			// each row holds `cols` shimmer cells
			expect(rows[0].querySelectorAll(".skeleton-shimmer")).toHaveLength(3);
		});
	});

	describe("generic-card variant", () => {
		it("renders generic card skeleton", () => {
			const { container } = render(<Skeleton variant="generic-card" />);
			expect(container.querySelector(".glass-card")).toBeInTheDocument();
		});

		it("applies correct flexbox styling", () => {
			const { container } = render(<Skeleton variant="generic-card" />);
			const card = container.querySelector(".glass-card");
			expect(card).toHaveStyle("display: flex");
			expect(card).toHaveStyle("flexDirection: column");
		});
	});

	describe("Convenience exports", () => {
		it("ProductCardSkeleton renders product card", () => {
			const { container } = render(<ProductCardSkeleton />);
			expect(container.querySelector(".product-card")).toBeInTheDocument();
		});

		it("ProductGridSkeleton renders product grid", () => {
			const { container } = render(<ProductGridSkeleton count={4} />);
			const cards = container.querySelectorAll(".product-card");
			expect(cards).toHaveLength(4);
		});

		it("TableRowsSkeleton renders table rows", () => {
			const { container } = render(<TableRowsSkeleton rows={3} cols={4} />);
			expect(container.querySelector("div[style*='flex']")).toBeInTheDocument();
		});

		it("GenericCardSkeleton renders generic card", () => {
			const { container } = render(<GenericCardSkeleton />);
			expect(container.querySelector(".glass-card")).toBeInTheDocument();
		});
	});

	describe("Accessibility", () => {
		it("has proper semantic structure", () => {
			const { container } = render(<Skeleton variant="generic-card" />);
			const element = container.querySelector(".glass-card");
			expect(element).toBeTruthy();
		});

		it("applies shimmer animation class", () => {
			const { container } = render(<Skeleton variant="product-card" />);
			const shimmer = container.querySelector(".skeleton-shimmer");
			expect(shimmer).toBeInTheDocument();
		});
	});
});
