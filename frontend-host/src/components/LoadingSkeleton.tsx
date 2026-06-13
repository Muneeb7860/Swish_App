import React from "react";

export const ProductCardSkeleton: React.FC = () => {
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
};

interface ProductGridSkeletonProps {
	count?: number;
}

export const ProductGridSkeleton: React.FC<ProductGridSkeletonProps> = ({
	count = 8,
}) => {
	return (
		<div className="products-grid">
			{Array.from({ length: count }).map((_, i) => (
				<ProductCardSkeleton key={i} />
			))}
		</div>
	);
};

interface TableRowsSkeletonProps {
	rows?: number;
	cols?: number;
}

export const TableRowsSkeleton: React.FC<TableRowsSkeletonProps> = ({
	rows = 5,
	cols = 4,
}) => {
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
};

export const GenericCardSkeleton: React.FC = () => {
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
};
