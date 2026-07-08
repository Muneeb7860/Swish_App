import { Modal } from "@swish/shared-ui";
import * as Lucide from "lucide-react";
import { useCallback, useEffect, useState } from "react";

/**
 * Self-contained cookie / privacy consent flow for the customer app.
 *
 * There was no pre-existing consent system in the platform, so this is the
 * single source of truth: it owns the banner (first visit), the preferences
 * dialog (reuses the shared `Modal`), persistence (localStorage), and the
 * always-available "Revisit Consent" entry point.
 *
 * The stored decision survives page refresh and navigation. Whenever it
 * changes, a `swish:consent-changed` CustomEvent is dispatched so the rest of
 * the application can react to the updated consent state.
 */

const STORAGE_KEY = "swish.consent.v1";
const CONSENT_EVENT = "swish:consent-changed";

export interface ConsentState {
	necessary: true;
	analytics: boolean;
	marketing: boolean;
	personalization: boolean;
	updatedAt: string;
}

type OptionalCategory = "analytics" | "marketing" | "personalization";

const CATEGORIES: {
	key: keyof ConsentState;
	label: string;
	description: string;
	locked?: boolean;
}[] = [
	{
		key: "necessary",
		label: "Strictly necessary",
		description:
			"Required for the app to work — sign-in, cart, checkout and security. Always on.",
		locked: true,
	},
	{
		key: "analytics",
		label: "Analytics",
		description:
			"Anonymous usage metrics that help us understand what to improve.",
	},
	{
		key: "marketing",
		label: "Marketing",
		description:
			"Lets us show promotions and measure the campaigns you engage with.",
	},
	{
		key: "personalization",
		label: "Personalization",
		description: "Tailors recommendations and offers to your shopping habits.",
	},
];

function makeState(choices: Record<OptionalCategory, boolean>): ConsentState {
	return {
		necessary: true,
		analytics: choices.analytics,
		marketing: choices.marketing,
		personalization: choices.personalization,
		updatedAt: new Date().toISOString(),
	};
}

/** Read the persisted consent, or `null` if the user has not decided yet. */
export function readConsent(): ConsentState | null {
	try {
		const raw = localStorage.getItem(STORAGE_KEY);
		if (!raw) return null;
		const parsed = JSON.parse(raw) as Partial<ConsentState>;
		return {
			necessary: true,
			analytics: !!parsed.analytics,
			marketing: !!parsed.marketing,
			personalization: !!parsed.personalization,
			updatedAt: parsed.updatedAt ?? new Date().toISOString(),
		};
	} catch {
		return null;
	}
}

const ALL_ON: Record<OptionalCategory, boolean> = {
	analytics: true,
	marketing: true,
	personalization: true,
};
const ALL_OFF: Record<OptionalCategory, boolean> = {
	analytics: false,
	marketing: false,
	personalization: false,
};

export default function ConsentManager() {
	const [consent, setConsent] = useState<ConsentState | null>(null);
	const [hydrated, setHydrated] = useState(false);
	const [prefsOpen, setPrefsOpen] = useState(false);
	// Draft toggles while the preferences dialog is open.
	const [draft, setDraft] =
		useState<Record<OptionalCategory, boolean>>(ALL_OFF);

	// Hydrate from storage once on mount so behaviour survives refresh/navigation.
	useEffect(() => {
		setConsent(readConsent());
		setHydrated(true);
	}, []);

	const persist = useCallback((next: ConsentState) => {
		try {
			localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
		} catch {
			/* storage may be unavailable (private mode) — keep in-memory state */
		}
		setConsent(next);
		window.dispatchEvent(
			new CustomEvent<ConsentState>(CONSENT_EVENT, { detail: next }),
		);
	}, []);

	const openPreferences = useCallback(() => {
		const current = consent ?? readConsent();
		setDraft(
			current
				? {
						analytics: current.analytics,
						marketing: current.marketing,
						personalization: current.personalization,
					}
				: ALL_OFF,
		);
		setPrefsOpen(true);
	}, [consent]);

	const acceptAll = useCallback(() => persist(makeState(ALL_ON)), [persist]);
	const rejectAll = useCallback(() => persist(makeState(ALL_OFF)), [persist]);
	const savePreferences = useCallback(() => {
		persist(makeState(draft));
		setPrefsOpen(false);
	}, [draft, persist]);

	if (!hydrated) return null;

	const showBanner = consent === null && !prefsOpen;

	return (
		<>
			{/* First-visit consent banner */}
			{showBanner && (
				<div
					className="consent-banner"
					role="dialog"
					aria-label="Cookie consent"
				>
					<div className="consent-banner__body">
						<Lucide.Cookie size={20} className="consent-banner__icon" />
						<div>
							<strong className="consent-banner__title">
								We value your privacy
							</strong>
							<p className="consent-banner__text">
								We use cookies to keep the app running, measure usage, and
								personalize your experience. Choose what you're comfortable with
								— you can change this anytime.
							</p>
						</div>
					</div>
					<div className="consent-banner__actions">
						<button
							type="button"
							className="btn-secondary-glow"
							onClick={rejectAll}
						>
							Reject all
						</button>
						<button
							type="button"
							className="btn-secondary-glow"
							onClick={openPreferences}
						>
							Customize
						</button>
						<button
							type="button"
							className="btn-primary-glow"
							style={{ flexGrow: 0, padding: "0.5rem 1rem" }}
							onClick={acceptAll}
						>
							Accept all
						</button>
					</div>
				</div>
			)}

			{/* Always-available entry point to reopen preferences */}
			{consent !== null && (
				<button
					type="button"
					className="consent-revisit-btn"
					onClick={openPreferences}
					aria-haspopup="dialog"
				>
					<Lucide.ShieldCheck size={15} />
					Revisit Consent
				</button>
			)}

			{/* Preferences dialog — reuses the shared Modal shell */}
			<Modal
				isOpen={prefsOpen}
				onClose={() => setPrefsOpen(false)}
				title={
					<>
						<Lucide.Sliders size={18} /> Consent Preferences
					</>
				}
				accentColor="var(--color-customer)"
				maxWidth={520}
				actions={
					<>
						<button
							type="button"
							className="btn-secondary-glow"
							onClick={() => setDraft(ALL_OFF)}
						>
							Reject all
						</button>
						<button
							type="button"
							className="btn-secondary-glow"
							onClick={() => setDraft(ALL_ON)}
						>
							Accept all
						</button>
						<button
							type="button"
							className="btn-primary-glow"
							style={{ flexGrow: 0, padding: "0.5rem 1.25rem" }}
							onClick={savePreferences}
						>
							Save preferences
						</button>
					</>
				}
			>
				<div className="consent-prefs">
					{CATEGORIES.map((cat) => {
						const checked = cat.locked || draft[cat.key as OptionalCategory];
						return (
							<div key={cat.key} className="consent-prefs__row">
								<div className="consent-prefs__meta">
									<span className="consent-prefs__label">{cat.label}</span>
									<span className="consent-prefs__desc">{cat.description}</span>
								</div>
								<button
									type="button"
									role="switch"
									aria-checked={checked}
									aria-label={cat.label}
									disabled={cat.locked}
									className={`consent-toggle${checked ? " on" : ""}${cat.locked ? " locked" : ""}`}
									onClick={() =>
										!cat.locked &&
										setDraft((d) => ({
											...d,
											[cat.key]: !d[cat.key as OptionalCategory],
										}))
									}
								>
									<span className="consent-toggle__knob" />
								</button>
							</div>
						);
					})}
					{consent && (
						<p className="consent-prefs__stamp">
							Last updated {new Date(consent.updatedAt).toLocaleString()}
						</p>
					)}
				</div>
			</Modal>
		</>
	);
}
