# Design

## Context

See `proposal.md` for motivation. Relevant existing state:

- `ApollonShared.tsx` (`/shared/$diagramId`) gates both showing a name prompt and mounting the editor on `collaborationUser` (`{ name, color }`), lazily initialized from `sessionStorage.getItem("apollon-collab-name")` via `readStoredCollabUser()`. When absent, a `useEffect` opens `COLLABORATE_NAME` with `initialName: randomCollabName()`; on confirm it writes `sessionStorage` and sets `collaborationUser`; on close it navigates to `/`. `collaborationUser` is passed straight into `ApollonEditor`'s `collaboration.user` option (Yjs awareness — presence, cursors, selection highlights) and nowhere else; it never reaches `extension-backend`.
- `add-user-profile` already added `displayName`/`email` to `User`, `GET`/`PATCH /api/v1/me`, and `EditProfileModal.tsx` (currently: no required fields, `HomeDialogActions`'s `confirmDisabled` is never set).
- `HomeHelpMenu.tsx` is the only current caller of `useQuery(["extension-backend", "me"], ExtensionApiClient.me, { enabled: auth.isAuthenticated, staleTime: 5 * 60 * 1000, retry: false })`.
- `collabColorFromName(name: string): string` (from `@tumaet/apollon`) is a pure hash-to-palette-index function — feeding it a `displayName` instead of a random name works unchanged.

## Goals / Non-Goals

**Goals:**
- Remove the redundant per-session name prompt for any user who already has a `displayName`.
- Reuse `EditProfileModal` (not a new modal) as the "no name yet" path, with a mode that makes the field actually required for this one entry point.

**Non-Goals:**
- Live-updating an already-open collaboration session's displayed name if the user edits their profile mid-session (from the Help menu, in another tab, etc.) — the name is captured once on entry, exactly like today's random name is.
- Touching `ShareDashboardModal.tsx` / its own `COLLABORATE_NAME` call site — already unreachable since `add-projects` (see that change's proposal), not revived or cleaned up here.
- Any backend change. Collaboration identity stays a client-side Yjs-awareness concept.
- Removing `COLLABORATE_NAME`, `randomCollabName`, or `apollon-collab-name` handling elsewhere — only `ApollonShared.tsx`'s usage is replaced.

## Decisions

### `EditProfileModal` gets an opt-in `requireDisplayName` prop, not a separate modal
Duplicating the modal for "same two fields, but name mandatory" would immediately drift from the Help-menu version. A boolean prop that disables `HomeDialogActions`' `confirmLabel` button until `displayName.trim()` is non-empty (the same `confirmDisabled` mechanism `RenameProjectModal`/`NewProjectModal` already use for their own required fields) keeps one component, one test surface, and zero behavior change for the existing Help-menu call site (prop defaults to `false`).

**Alternative considered**: validate server-side only (reject blank on `PATCH`) and let the modal close/reopen on failure. Rejected: `PATCH /api/v1/me` intentionally has no such constraint (per `add-user-profile`'s design — the general profile is optional), and overloading it here would make the Help-menu path suddenly reject a legitimate "clear my name" edit.

### `ApollonShared.tsx` branches on `currentUser.displayName`, replacing the `sessionStorage` state machine
```
currentUser query pending -> not ready yet (same "wait" behavior the old
                              sessionStorage-read lazy-init effectively had
                              on first load)
displayName set           -> collaborationUser = { name: displayName,
                              color: collabColorFromName(displayName) }
displayName empty         -> open EDIT_PROFILE with requireDisplayName
                              onUpdated -> collaborationUser from the saved name
                              onClose   -> navigate("/")
```
This mirrors the existing `needsCollabName` / prompt-effect / editor-mount-gate structure one-for-one, just swapping the data source and the modal. `sessionStorage["apollon-collab-name"]` is no longer read or written by this file.

`collaborationUser` is `useMemo`'d on the trimmed `displayName` string, not recomputed as a fresh object literal every render: the pre-existing editor-mount effect depends on `collaborationUser`'s *identity* (`[..., collaborationUser, ...]`), and that effect tears down and recreates the `ApollonEditor` + `WebSocketManager` connection on every dependency change. A fresh object each render — found while implementing this, via a heap-exhaustion test failure — re-triggered that effect continuously, since the old code's `collaborationUser` was `useState`-backed (stable by construction) and nothing here previously had to think about the object staying referentially stable across renders.

### Extract `useCurrentUser()` instead of inlining a second `useQuery(["extension-backend", "me"], ...)`
`ApollonShared.tsx` needs the same cached identity `HomeHelpMenu.tsx` already fetches. A one-line hook (`webapp/src/hooks/useCurrentUser.ts`) wrapping the existing query config means both call sites share one cache entry and one place to change the query's shape later (e.g. if `staleTime` or error handling needs to evolve). `HomeHelpMenu.tsx` is updated to use it too, so the config exists in exactly one place.

## Risks / Trade-offs

- **[Risk]** A user who dismisses the profile-completion prompt without saving is bounced to `/` — same as today's `COLLABORATE_NAME` cancel path, so no new failure mode, but worth naming since it's now tied to profile completion rather than a throwaway name.
- **[Trade-off]** If `currentUser` is still loading when the route mounts (cold cache, no prior `HomeHelpMenu` render this session), there's a brief extra wait before the editor can mount, where there was none before (the old `sessionStorage` read was synchronous). Acceptable: it's a one-time network round trip already happening elsewhere in the app, and the editor was already gated on the diagram-seed fetch, which is comparably slow.
- **[Risk, found during implementation]** `useCurrentUser()` has `retry: false` and no error UI in this file. If `GET /api/v1/me` fails outright, `currentUser` stays `undefined` forever, so `needsCollabName`'s gate never resolves either way — the collaborative view hangs on its loading overlay indefinitely instead of surfacing an error, whereas today's `sessionStorage`-based flow had no dependency on `extension-backend` being reachable at all. Accepted as-is: by the time this route renders, `extension-backend` is already required for `AuthGate`/every other authenticated feature, so its outage isn't a failure mode unique to this change — but it's a pre-existing gap (no generic "extension-backend unreachable" UI anywhere yet) this change now also depends on, worth a follow-up rather than solving here.
