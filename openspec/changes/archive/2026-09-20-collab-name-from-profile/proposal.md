# Proposal

## Why

Opening any project diagram lands the webapp on `/shared/:id?view=COLLABORATE` (the route `sharedDiagramRoute()` defaults to), which today always prompts for a throwaway "collaboration name" (`COLLABORATE_NAME` modal, backed by `sessionStorage`) — a holdover from before Keycloak login existed, when there was no other way to know who was editing. Now that `add-user-profile` gives every user a `displayName`, asking for a name they already have on file is redundant friction on the single most common path through the app (opening a diagram).

## What Changes

- `ApollonShared.tsx`'s collaboration entry point no longer opens `COLLABORATE_NAME` / reads or writes `sessionStorage["apollon-collab-name"]`. Instead:
  - If the authenticated user already has a `displayName`, that name (and its derived cursor color) is used immediately — no modal, no interruption.
  - If the user has no `displayName` yet, the existing **Edit profile** modal (`EDIT_PROFILE`, from `add-user-profile`) opens in its place, gating entry to the collaborative session until a name is saved. Closing it without saving returns to `/`, exactly as the old `COLLABORATE_NAME` modal's cancel path did.
- `EditProfileModal` gains a `requireDisplayName` mode: when set, "Save" is disabled until the display name field is non-empty. This mode is opt-in per call site — opening it from the Help menu (renaming your profile at will) is unaffected and stays fully optional.
- A small `useCurrentUser()` hook is extracted (the `useQuery(["extension-backend", "me"], ...)` currently inlined in `HomeHelpMenu.tsx`) so both `HomeHelpMenu` and `ApollonShared` read the same cached identity instead of duplicating the query.
- **BREAKING** (internal only, no user-facing regression): the collaboration display name is no longer request-scoped/anonymous-friendly via `sessionStorage` — it now requires an authenticated user with a profile. This has no practical effect today because `AuthGate` already requires login for this route and every other one except `/imprint`/`/privacy`.

## Capabilities

### New Capabilities
_None._

### Modified Capabilities
- `identity`: adds a requirement that a user's collaboration identity (the name and color other participants see) comes from their own profile, not a per-session prompt.

## Impact

- **`webapp`**: `ApollonShared.tsx` (removes the `COLLABORATE_NAME` open + `sessionStorage` read/write, adds the `displayName`-or-`EDIT_PROFILE` branch); `EditProfileModal.tsx` (new `requireDisplayName` prop); new `useCurrentUser()` hook (new file, e.g. `webapp/src/hooks/useCurrentUser.ts`), consumed by `HomeHelpMenu.tsx` and `ApollonShared.tsx`.
- Not touched: `extension-backend` (this is purely a client-side presence/cursor-label concept — see `add-projects`'s design.md on why collaboration state was never routed through the backend), `diagrams-backend`, and `ShareDashboardModal.tsx`/`COLLABORATE_NAME`'s other call site (already unreachable dead code since `add-projects` replaced the Home/local-diagram flow — left alone, not in scope here).
