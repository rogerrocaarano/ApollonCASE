# Design

## Context

See `proposal.md` - Why. Relevant current state:

- `webapp`'s OIDC scope is already `"openid profile email"` (`auth/oidcConfig.ts`), so `given_name`, `family_name`, and `email` are already present as claims oidc-client-ts exposes on `auth.user.profile` (a typed `UserProfile` bag) — no scope or Keycloak client-mapper change is needed to read them client-side.
- `useCurrentUser()` (`hooks/useCurrentUser.ts`) wraps `ExtensionApiClient.me()` (`GET /api/v1/me`) in a `react-query` cache, consumed today by `HomeHelpMenu.tsx` ("Signed in as …", "Edit profile") and `ApollonShared.tsx` (the collaboration-name gate). Nothing in the webapp reads `currentUser.id` — only `displayName`, `email`, and `keycloakId`, all three of which are also directly available from `auth.user.profile` without a round trip to `extension-backend`.
- `HomeHelpMenu.tsx`'s `HelpMenuItems` renders the shared Help/legal body AND (when `auth.isAuthenticated`) an account block ("Signed in as", "Edit profile", "Sign out") in the same dropdown. It's consumed directly (`HomeHelpMenu`) by `HomeHeaderRow`/`ChromeSubHeader`/`HelpMenu` (editor), and inlined (`HelpMenuItems`) into `MobileIslands.tsx`'s mobile overflow.
- `ApollonShared.tsx` gates entry to a collaborative session on `collaborationUser` (derived from `currentUser.displayName`): if empty, it opens `EDIT_PROFILE` in `requireDisplayName` mode and blocks the editor mount effect (`if (isCollaborationView && !collaborationUser) return`) until a name is saved.
- `ProjectsPage.tsx`/`ProjectDetailPage.tsx` build their own header JSX (`BackNav`, title, action buttons) directly inside `PageShell`'s `header` slot — they don't render `HomeHelpMenu`/`ChromeSubHeader` at all, which is why there's no sign-out control there today.
- The editor's back-to-dashboard control is duplicated in two places, both hardcoded: `EditorHeader.tsx`'s `HeaderBrandIsland` (`<BackNav to="/" label={ALL_DIAGRAMS_LABEL}>`, desktop) and `MobileIslands.tsx`'s `MobileBackPill` (same, mobile). Neither knows which route mounted the editor.
- `useBackTarget.ts` already solves an analogous problem for the *chrome* pages (legal/404): it inspects `readNavFrom(location.state)` — a path stamped into router state by whoever navigated there — to decide the back target. The editor's own back control is a different case: it isn't reached via a stamped `state.from`, it needs to know the *current* route's own params (is this `/projects/$projectId/diagrams/$diagramId`, or something else?). `useDiagramIdFromPath.ts` documents why route-scoped hooks (`useParams()`) can't answer that from the navbar: it renders above the matched route, so `useParams()` returns `{}` at that depth — `useLocation().pathname` is the only router-aware source available there, which is what it parses directly instead. The project detail route itself is `/projects/$id` (param named `id`, in `routes/projects.$id.tsx`) — distinct from the project-diagram route's own `$projectId` param.
- Backend: `User(id, keycloakId, displayName, email)`; `UsersService.updateProfile` writes `displayName`; `UsersController.updateMe` is the only caller. `ProjectPermission`/`ProjectsRepository` key everything off `keycloakId`/`User.id` — never `displayName`. `UsersRepository.findByEmail` (used by `ProjectsService.shareProject`) is the only cross-user lookup, and it's by `email`, not `displayName`.

## Goals / Non-Goals

**Goals:**
- A user's display name is always immediately available, derived from `given_name`/`family_name` on the current OIDC token — no persistence, no editing UI, no blocking modal.
- One `UserMenu` control (name/email + Sign out) shared across every authenticated surface, including the two that lack it today.
- The editor's back control resolves to the right destination for a project diagram without touching the unrelated chrome-page `useBackTarget` logic.

**Non-Goals:**
- No change to the two other `navigate({ to: "/" })` calls in `ApollonShared.tsx` (invalid view type, seed failure) — those are error-recovery redirects, a different concern from the deliberate back-navigation control this change fixes. Left as `/` intentionally.
- No change to `ShareModal`/`ShareDashboardModal` (ad-hoc link sharing) or their own Share button in the editor header.
- No change to how `extension-backend` authorizes anything — `keycloakId` stays the join key, `email` stays the share-by-email lookup key; only `displayName` is removed.
- Not adding the full Help/Theme island to `ProjectsPage`/`ProjectDetailPage` — only `UserMenu`.

## Decisions

### 1. Compute display name (and email, for display) from `auth.user.profile`, not `extension-backend`
Every remaining webapp consumer of "who am I" (`UserMenu`, `ApollonShared`'s collaboration identity) reads `useAuth().user?.profile.given_name` / `.family_name` / `.email` directly (a small `useTokenIdentity()` helper: `{ displayName, email }`, `displayName = [given_name, family_name].filter(Boolean).join(" ")`). This removes the `extension-backend` round trip from the UI entirely — `useCurrentUser()`, `ExtensionApiClient.me()`, and the `CurrentUser` type are deleted from the webapp. `GET /api/v1/me` keeps existing server-side purely as the auth-flow verification endpoint the identity spec already calls out; nothing in the webapp calls it anymore.

**Alternative considered**: keep calling `GET /api/v1/me` for `email` (since it's the backend-synced source of truth) while deriving `displayName` from the token. Rejected — `extension-backend` syncs its `User.email` from the exact same JWT `email` claim on every request, so the two values can never diverge in practice, and keeping the round trip for one field while dropping it for the other adds an inconsistency with no behavioral benefit.

### 2. `UserMenu`: new component, `HelpMenuItems` loses its account block
Extract a `UserMenu.tsx` (mirroring `HomeHelpMenu.tsx`'s structure: a `DropdownMenu` behind a `navbarButtonStyle()` trigger) whose body is exactly today's account block — `DropdownMenuLabel` ("Signed in as `{displayName}` · `{email}`"), `Sign out` (`auth.signoutRedirect()`) — with "Edit profile" removed (nothing left to edit). `HelpMenuItems` drops its trailing `auth.isAuthenticated && (...)` block and the now-unused imports (`useQueryClient`, `useModalContext`'s edit-profile usage, `UserPenIcon`, `CurrentUser`, `useCurrentUser`).

Mounting sites, each placing `UserMenu` next to its existing Help control:
- `HeaderActionsIsland` (`EditorHeader.tsx`, desktop editor)
- `MobileActionsPill` (`MobileIslands.tsx`, mobile editor) — a new icon-only pill entry, same family as the existing Help/Theme icons
- `ChromeSubHeader.tsx` (legal/404)
- `HomeActionsIsland` (`HomeHeaderRow.tsx`, old flat gallery) — kept in sync for consistency, even though it's not on the path this proposal is fixing
- **New**: `ProjectsPage.tsx` and `ProjectDetailPage.tsx` headers — the actual gap this closes

**Alternative considered**: fold "Sign out" into each page's existing action buttons instead of a shared component (e.g., a plain button next to "New project"). Rejected — it would re-implement the same dropdown/label logic per page and drift in wording/order, the exact duplication `HomeHelpMenu`'s docblock already warns against for Help.

### 3. `ApollonShared.tsx`: delete the collaboration-name gate
`collaborationUser` is computed directly from `useTokenIdentity()` (decision 1) instead of `useCurrentUser()`. Since a name is always present, `needsCollabName`, the `hasPromptedRef`-guarded `openModal("EDIT_PROFILE", ...)` effect, and the `isCollaborationView && !collaborationUser` early-return in the editor-mount effect are all deleted — collaboration starts on the same tick the diagram seed resolves, exactly like the non-collaboration path already does.

### 4. Editor back-target: a small route-aware hook, separate from `useBackTarget`
Add `useEditorBackTarget()` (`hooks/useEditorBackTarget.ts`) used by both `HeaderBrandIsland` and `MobileBackPill` in place of the hardcoded `to="/"`. It parses `useLocation().pathname` directly — the same technique `useDiagramIdFromPath` already uses for the identical "navbar renders above the matched route" constraint (see Context) — checking whether the path starts with `/projects/:id/diagrams/...`, and returns:
- `{ to: "/projects/$id", params: { id }, label: "Project diagrams" }` when it does (`$id` matching the project detail route's own param name, not the project-diagram route's `$projectId`),
- `{ to: "/", label: ALL_DIAGRAMS_LABEL }` otherwise (local, shared, playground — today's behavior, unchanged).

`BackNav`'s prop type widens from `BackTarget` to `BackTarget | EditorBackTarget` so it can render either hook's result.

This is intentionally a separate hook from `useBackTarget` (used only by `ChromeSubHeader`/`ErrorPage`/`LegalPage`): that one resolves a *stamped prior location* (`state.from`) for pages reached *after* leaving the editor, which is a different question from "what route am I in right now." Merging them would force one hook to handle both "read state" and "read current route" branches for no shared benefit — they already return a compatible shape and both feed the same `BackNav`, which is all the reuse that's warranted.

**Alternative considered**: use TanStack Router's `useMatches()` instead of parsing the pathname, matching by route id rather than string. Rejected after checking `useDiagramIdFromPath`'s own docblock: it explicitly notes route-scoped hooks return nothing useful at navbar depth, which is why that hook already parses the raw pathname — same constraint, same proven fix, so `useEditorBackTarget` follows it rather than introducing a second technique for the same problem.

**Alternative considered**: pass `projectId` as a prop down through `ApollonShared` → `EditorHeaderRow` from each route. Rejected — `EditorHeaderRow` is mounted once per editor layout, not wired to receive per-route data today, and every other "where am I" decision in this chrome (`useBackTarget`, `useHelpMenu`'s legal provenance) already reads it from the router rather than threading props, so this stays consistent with the existing pattern.

### 5. Backend: remove `displayName` end to end
`User.kt` drops the `displayName` column; `UserDtos.kt` drops `UpdateProfileRequest` and `UserResponse.displayName`; `UsersController.kt` drops `PATCH /api/v1/me`; `UsersService.updateProfile` is deleted (`trackKeycloakUser`, used by every authenticated request, is untouched — it only ever touched `email`). No migration: the user drops and recreates the dev database, same precedent as `project-permissions`.

## Risks / Trade-offs

- **[Risk] A user whose Keycloak `given_name`/`family_name` are blank produces an empty/odd collaboration name** (e.g. a single trailing space if only one is set). → Mitigation: confirmed out of scope — Keycloak is asserted to always populate both (see proposal.md discussion); not defended against in code beyond `.filter(Boolean).join(" ")` degrading gracefully to whatever is present.
- **[Risk] Removing `GET /api/v1/me` from the webapp's own call graph means a regression in `CurrentUserFilter`/JWT validation would no longer surface through the webapp's normal UI flows**, only through project/diagram calls. → Mitigation: `/me` remains and keeps its documented purpose (manual/e2e auth-flow verification); every other authenticated endpoint still exercises `CurrentUserFilter` identically, so this doesn't reduce production coverage, only removes a redundant client call.
- **[Trade-off] `UserMenu` duplicates a small amount of dropdown boilerplate already in `HomeHelpMenu`** (trigger button, tooltip, `DropdownMenu` scaffolding) rather than sharing a generic "icon dropdown" primitive. → Accepted — the existing codebase already has this exact shape repeated per menu (`HomeHelpMenu`, `RefinePopover`'s trigger, `MobileMenuButton`); extracting a shared primitive now is a larger refactor than this change's scope.
