# Phase 1 — Claude Design Upload Runbook

**Status:** bundle prepared & clean; blocked only on interactive auth.

The upload to claude.ai/design requires `/design-login`, which only works in an
**interactive terminal** (not a headless/agent session). Everything else is ready.

## What's prepared

- `.design-sync/config.json` — 9 components, 5 groups, design-token registry
- `.design-sync/conventions.md` — styling guide for the Design agent
- `ds-bundle/components/<Group>/<Name>.d.ts` + `.prompt.md` — per-component source spec (the inputs)
- `ds-bundle/_ds_bundle.css`, `styles.css`, `README.md` — token/style payload
- Sync-conflict cruft (`* 2.*`) removed so the upload reads a clean tree.

### Components (9)
| Group | Components |
|---|---|
| Forms & Input | ConnectionConfig |
| Payment & Commerce | CheckoutPanel, CreditCardMockup |
| Status & Timeline | OrderTimeline, StatusIndicator |
| Notifications | NotificationInbox |
| Dashboard | RetailerOnboarding, SensorProvisioning, SandboxLogs |

## Steps (run in an interactive `claude` terminal)

1. **Authorize:** run `/design-login` and complete the browser flow.
2. **Run the sync skill:** `/design-sync` — it will:
   - generate preview HTML per component (with `@dsCard group="…"` markers) from the
     `.d.ts` + `.prompt.md` specs — this is the step that's missing from the current bundle;
   - build `_ds_manifest.json` and run the render self-check;
   - `list_projects` → pick the target (or create **"Swish B2B Design System"**);
   - `finalize_plan` with `localDir = ds-bundle/` and the writes globbed to the
     generated previews + `_ds_bundle.css`;
   - `write_files` the bundle (≤256 per call), incrementally — never wholesale replace.
3. **Verify:** open the project's Design System pane; confirm 9 cards render in 5 groups.

## Notes

- The current `ds-bundle/` holds the **source spec** (`.d.ts`/`.prompt.md`), not the rendered
  preview HTML. Don't raw-`write_files` the bundle as-is — let `/design-sync` generate previews
  first, otherwise the Design System pane has no cards to render.
- If working on claude.ai/code (web), the alternative to `/design-login` is Claude Design's
  **"Send to Claude Code Web"**, which seeds the project into the workspace directly.
