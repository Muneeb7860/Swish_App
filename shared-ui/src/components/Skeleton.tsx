import type React from "react";

type SkeletonVariant =
	| "product-card"
	| "product-grid"
	| "table-rows"
	| "generic-card";

export interface SkeletonProps {
	/** Skeleton display variant */
	variant: SkeletonVariant;
	/** Number of items to display (for grids) */
	count?: number;
	/** Number of rows (for table variant) */
	rows?: number;
	/** Number of columns (for table variant) */
	cols?: number;
}

/**
 * Unified loading skeleton component with 4 variants.
 * Displays animated placeholder while data loads.
 *
 * Consolidates from:
 * - frontend-host/LoadingSkeleton.tsx (4 separate components)
 * - Used across: customer, host, admin, rider frontends
 *
 * @example
 * ```tsx
 * <Skeleton variant="product-grid" count={12} />
 * <Skeleton variant="table-rows" rows={10} cols={5} />
 * <Skeleton variant="generic-card" />
 * ```
 */
export const Skeleton: React.FC<SkeletonProps> = ({
	variant,
	count = 8,
	rows = 5,
	cols = 4,
}) => {
	switch (variant) {
		case "product-card":
			return (
				<div className="glass-card product-card" style={{ cursor: "default" }}>
					<div className="skeleton-image skeleton-shimmer" />
					<div className="skeleton-text medium skeleton-shimmer" />
					<div className="product-info-row" style={{ marginTop: "1rem" }}>
						<div
							className="skeleton-text short skeleton-shimmer"
							style={{ margin: 0, height: 16 }}
						/>
						<div
							className="skeleton-shimmer"
							style={{ width: 60, height: 28, borderRadius: 8 }}
						/>
					</div>
				</div>
			);

		case "product-grid":
			return (
				<div className="products-grid">
					{Array.from({ length: count }).map((_, i) => (
						<Skeleton key={i} variant="product-card" />
					))}
				</div>
			);

		case "table-rows":
			return (
				<div
					style={{
						display: "flex",
						flexDirection: "column",
						gap: "1rem",
						width: "100%",
					}}
				>
					{Array.from({ length: rows }).map((_, r) => (
						<div
							key={r}
							style={{
								display: "flex",
								gap: "1rem",
								padding: "1rem",
								background: "rgba(255, 255, 255, 0.02)",
								border: "1px solid rgba(255, 255, 255, 0.05)",
								borderRadius: 12,
								alignItems: "center",
							}}
						>
							{Array.from({ length: cols }).map((_, c) => (
								<div
									key={c}
									className="skeleton-shimmer skeleton-text"
									style={{
										flex: c === 0 ? 2 : 1,
										margin: 0,
										height: 14,
									}}
								/>
							))}
						</div>
					))}
				</div>
			);

		case "generic-card":
			return (
				<div
					className="glass-card"
					style={{
						padding: "1.5rem",
						display: "flex",
						flexDirection: "column",
						gap: "0.8rem",
					}}
				>
					<div
						className="skeleton-text medium skeleton-shimmer"
						style={{ height: 18 }}
					/>
					<div className="skeleton-text skeleton-shimmer" />
					<div className="skeleton-text skeleton-shimmer" />
					<div className="skeleton-text short skeleton-shimmer" />
				</div>
			);

		default:
			return null;
	}
};

// Convenience exports for backward compatibility with frontend-host API
export const ProductCardSkeleton: React.FC = () => (
	<Skeleton variant="product-card" />
);

export const ProductGridSkeleton: React.FC<{ count?: number }> = (props) => (
	<Skeleton variant="product-grid" {...props} />
);

export const TableRowsSkeleton: React.FC<{ rows?: number; cols?: number }> = (
	props,
) => <Skeleton variant="table-rows" {...props} />;

export const GenericCardSkeleton: React.FC = () => (
	<Skeleton variant="generic-card" />
);
