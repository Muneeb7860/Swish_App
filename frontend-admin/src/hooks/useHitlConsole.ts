// Phase 8B — admin HITL console state: auth token, live queue, and resolve actions.
import { useCallback, useEffect, useState } from "react";
import {
	adjustHitl,
	login as apiLogin,
	fetchHitlQueue,
	type HitlTicket,
	mapItemToTicket,
	resolveHitl,
} from "../api/governance";

const TOKEN_KEY = "swish_admin_token";

export interface HitlConsole {
	authed: boolean;
	queue: HitlTicket[];
	loading: boolean;
	error: string | null;
	login: (email: string, password: string) => Promise<void>;
	loginWithToken: (token: string) => void;
	logout: () => void;
	refresh: () => Promise<void>;
	approve: (ticket: HitlTicket) => Promise<void>;
	voidTicket: (ticket: HitlTicket) => Promise<void>;
	adjust: (ticket: HitlTicket, newPrice: number) => Promise<void>;
}

export function useHitlConsole(): HitlConsole {
	const [token, setToken] = useState<string | null>(() =>
		localStorage.getItem(TOKEN_KEY),
	);
	const [queue, setQueue] = useState<HitlTicket[]>([]);
	const [loading, setLoading] = useState(false);
	const [error, setError] = useState<string | null>(null);

	const refresh = useCallback(async () => {
		if (!token) return;
		setLoading(true);
		setError(null);
		try {
			const items = await fetchHitlQueue(token);
			setQueue(items.map(mapItemToTicket));
		} catch (e) {
			const msg = e instanceof Error ? e.message : "Failed to load HITL queue";
			setError(msg);
			// An expired/invalid token surfaces as 401/403 — drop it so the gate reappears.
			if (msg.startsWith("401") || msg.startsWith("403")) {
				localStorage.removeItem(TOKEN_KEY);
				setToken(null);
			}
		} finally {
			setLoading(false);
		}
	}, [token]);

	useEffect(() => {
		if (token) void refresh();
	}, [token, refresh]);

	const loginWithToken = useCallback((t: string) => {
		setError(null);
		localStorage.setItem(TOKEN_KEY, t);
		setToken(t);
	}, []);

	const login = useCallback(
		async (email: string, password: string) => {
			const t = await apiLogin(email, password);
			loginWithToken(t);
		},
		[loginWithToken],
	);

	const logout = useCallback(() => {
		localStorage.removeItem(TOKEN_KEY);
		setToken(null);
		setQueue([]);
	}, []);

	const resolve = useCallback(
		async (ticket: HitlTicket, approve: boolean) => {
			if (!token) return;
			setError(null);
			try {
				await resolveHitl(
					token,
					ticket.id,
					approve,
					"admin-console",
					approve
						? "Approved via supervisor console"
						: "Voided via supervisor console",
				);
				await refresh();
			} catch (e) {
				setError(e instanceof Error ? e.message : "Action failed");
			}
		},
		[token, refresh],
	);

	const adjust = useCallback(
		async (ticket: HitlTicket, newPrice: number) => {
			if (!token) return;
			setError(null);
			try {
				await adjustHitl(
					token,
					ticket.id,
					newPrice,
					"admin-console",
					`Bid adjusted to ${newPrice} CHF via supervisor console`,
				);
				await refresh();
			} catch (e) {
				setError(e instanceof Error ? e.message : "Adjust failed");
			}
		},
		[token, refresh],
	);

	return {
		authed: !!token,
		queue,
		loading,
		error,
		login,
		loginWithToken,
		logout,
		refresh,
		approve: (t) => resolve(t, true),
		voidTicket: (t) => resolve(t, false),
		adjust,
	};
}
