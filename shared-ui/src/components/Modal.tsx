import type React from "react";
import { useEffect } from "react";

export interface ModalProps {
	/** Whether the modal is visible */
	isOpen: boolean;
	/** Called when the overlay is clicked, Escape is pressed, or the close button is used */
	onClose: () => void;
	/** Modal heading. Pass a string or a node (e.g. icon + text) */
	title?: React.ReactNode;
	/** Accent color for the heading and content border (CSS value or token) */
	accentColor?: string;
	/** Max width of the content panel in px */
	maxWidth?: number;
	/** Optional footer node, typically action buttons */
	actions?: React.ReactNode;
	/** Hide the default close (X) button in the header */
	hideCloseButton?: boolean;
	/** Stacking context; defaults to 1000 */
	zIndex?: number;
	/** Disable closing when the overlay backdrop is clicked */
	disableBackdropClose?: boolean;
	children?: React.ReactNode;
}

/**
 * Unified modal shell — animated overlay + content panel with header and footer.
 * Fixes the previously-undefined `fade-in-overlay` / `scale-up-cert` animations
 * (now defined in @swish/shared-ui/tokens).
 *
 * Consolidates from:
 * - frontend-customer/CustomerApp.tsx (cert-modal / substitution prompt)
 * - frontend-host/App.tsx (Swiss Loyalty Certificate Desk)
 *
 * @example
 * ```tsx
 * <Modal
 *   isOpen={certModalOpen}
 *   onClose={() => setCertModalOpen(false)}
 *   title={<><Sparkles size={18} /> Certificate Desk</>}
 *   accentColor="var(--color-business)"
 *   actions={<button onClick={handleDownload}>Download</button>}
 * >
 *   <canvas ref={canvasRef} width="560" height="360" />
 * </Modal>
 * ```
 */
export const Modal: React.FC<ModalProps> = ({
	isOpen,
	onClose,
	title,
	accentColor = "var(--accent)",
	maxWidth = 620,
	actions,
	hideCloseButton = false,
	zIndex = 1000,
	disableBackdropClose = false,
	children,
}) => {
	useEffect(() => {
		if (!isOpen) return;
		const onKey = (e: KeyboardEvent) => {
			if (e.key === "Escape") onClose();
		};
		window.addEventListener("keydown", onKey);
		return () => window.removeEventListener("keydown", onKey);
	}, [isOpen, onClose]);

	if (!isOpen) return null;

	return (
		<div
			className="swish-modal-overlay"
			style={{ zIndex }}
			onClick={disableBackdropClose ? undefined : onClose}
		>
			<div
				className="swish-modal-content"
				style={{ borderColor: accentColor, maxWidth }}
				onClick={(e) => e.stopPropagation()}
			>
				{(title || !hideCloseButton) && (
					<div className="swish-modal-header">
						<h4 className="swish-modal-title" style={{ color: accentColor }}>
							{title}
						</h4>
						{!hideCloseButton && (
							<button
								type="button"
								aria-label="Close"
								className="swish-modal-close"
								onClick={onClose}
							>
								×
							</button>
						)}
					</div>
				)}

				{children}

				{actions && <div className="swish-modal-actions">{actions}</div>}
			</div>
		</div>
	);
};
