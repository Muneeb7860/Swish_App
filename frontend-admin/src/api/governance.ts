// Phase 8B — governance/HITL API client for the admin console.
// frontend-admin runs standalone (no dev proxy), so we hit the backend by URL.
// The HitlQueueController is @CrossOrigin and SecurityConfig allows localhost:*,
// and the endpoints require an ADMIN JWT obtained via /api/v1/auth/login.

const API_BASE: string =
	(import.meta as any).env?.VITE_API_BASE ?? "http://localhost:8083";

/** Unified HITL item as served by GET /api/governance/hitl (Phase 8A). */
export interface HitlItem {
	id: string; // composite: PA-<id> | AQ-<ticketId>
	source: string; // B2B_PROCUREMENT | AGENT_ESCALATION
	type: string; // b2b_override | agent_escalation | pricing_review
	status: string; // PENDING | APPROVED | REJECTED
	amount: number;
	description: string;
	reference?: string | null;
	createdAt?: string | null;
}

/** Shape the AdminPanel renders. */
export interface HitlTicket {
	id: string;
	type: string;
	amount: number;
	desc: string;
	source?: string;
}

export function mapItemToTicket(item: HitlItem): HitlTicket {
	return {
		id: item.id,
		type: item.type ?? item.source ?? "hitl",
		amount:
			typeof item.amount === "number" ? item.amount : Number(item.amount ?? 0),
		desc: item.description ?? "",
		source: item.source,
	};
}

async function asError(res: Response): Promise<Error> {
	let detail = `${res.status} ${res.statusText}`;
	try {
		const body = await res.json();
		if (body?.message) detail = body.message;
		else if (body?.error) detail = body.error;
	} catch {
		/* non-JSON body */
	}
	return new Error(detail);
}

/** Authenticate and return the JWT. Throws on bad credentials. */
export async function login(email: string, password: string): Promise<string> {
	const res = await fetch(`${API_BASE}/api/v1/auth/login`, {
		method: "POST",
		headers: { "Content-Type": "application/json" },
		body: JSON.stringify({ email, password }),
	});
	if (!res.ok) throw await asError(res);
	const body = await res.json();
	if (!body?.token)
		throw new Error(
			"Login succeeded but no token was returned (MFA required?).",
		);
	return body.token as string;
}

export async function fetchHitlQueue(token: string): Promise<HitlItem[]> {
	const res = await fetch(`${API_BASE}/api/governance/hitl`, {
		headers: { Authorization: `Bearer ${token}` },
	});
	if (!res.ok) throw await asError(res);
	return (await res.json()) as HitlItem[];
}

/** Approve (release) or reject (void) a HITL item by its composite id. */
export async function resolveHitl(
	token: string,
	id: string,
	approve: boolean,
	operator: string,
	reason: string,
): Promise<void> {
	const action = approve ? "approve" : "reject";
	const res = await fetch(
		`${API_BASE}/api/governance/hitl/${encodeURIComponent(id)}/${action}`,
		{
			method: "POST",
			headers: {
				"Content-Type": "application/json",
				Authorization: `Bearer ${token}`,
			},
			body: JSON.stringify({ operator, reason }),
		},
	);
	if (!res.ok) throw await asError(res);
}

/** Adjust bid price for a B2B procurement HITL item (PA-* prefix only). */
export async function adjustHitl(
	token: string,
	id: string,
	newPrice: number,
	operator: string,
	reason: string,
): Promise<void> {
	const res = await fetch(
		`${API_BASE}/api/governance/hitl/${encodeURIComponent(id)}/adjust`,
		{
			method: "POST",
			headers: {
				"Content-Type": "application/json",
				Authorization: `Bearer ${token}`,
			},
			body: JSON.stringify({ newPrice, operator, reason }),
		},
	);
	if (!res.ok) throw await asError(res);
}
