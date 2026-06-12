import { useEffect, useRef } from "react";

/**
 * Reusable hook that tracks the mouse position relative to a card element
 * and updates CSS custom properties (--mouse-x, --mouse-y) for interactive glowing borders.
 */
export function useMouseMoveGlow<T extends HTMLElement = HTMLDivElement>() {
	const ref = useRef<T>(null);

	useEffect(() => {
		const element = ref.current;
		if (!element) return;

		const handleMouseMove = (e: MouseEvent) => {
			const rect = element.getBoundingClientRect();
			const x = e.clientX - rect.left;
			const y = e.clientY - rect.top;

			element.style.setProperty("--mouse-x", `${x}px`);
			element.style.setProperty("--mouse-y", `${y}px`);
		};

		element.addEventListener("mousemove", handleMouseMove);
		return () => {
			element.removeEventListener("mousemove", handleMouseMove);
		};
	}, []);

	return ref;
}
