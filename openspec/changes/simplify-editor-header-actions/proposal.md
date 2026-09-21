# Proposal

## Why

The diagram editor's header currently duplicates or exposes controls that no longer belong there. `gate-project-diagrams` (in progress, 18/21 tasks done) already gave project diagrams their own dedicated sharing UI on `ProjectDetailPage` (`SHARE_PROJECT` → `ShareProjectModal`), so the editor's standalone "Share" button — which creates an unrelated ad-hoc public copy via `useShareableDiagram`/`DiagramApiClient` — is redundant surface area inside the diagram-editing context. Similarly, "New Diagram" duplicates an entry point already available from Home and the project view, and a full "Help" menu (About, Releases, GitHub, legal links, "How does this Editor Work?") is more chrome than the editor needs. Trimming these three controls focuses the editor header on actions specific to the open diagram: File (Import/Export), Save a local copy, and Version history.

## What Changes

- **BREAKING**: The editor header (desktop actions island and the narrow-viewport mobile actions pill) no longer shows a "Share" control. Opening the ad-hoc share flow (`SHARE` modal) is no longer reachable from inside the diagram editor.
- **BREAKING**: The editor's File menu (`FileMenuItems`, shared by the desktop dropdown and the mobile File menu) no longer includes "New Diagram". Creating a new diagram remains reachable from Home and from a project's detail page, just not from inside an already-open editor.
- **BREAKING**: The editor header no longer shows a "Help" control (desktop `HelpMenu` and the mobile "Help" dropdown are removed from the editor chrome). The shared Help/legal menu (`HomeHelpMenu`/`HelpMenuItems`) and its `"editor"` variant, including "How does this Editor Work?" and the dev-only "Open Playground" entry, are left in place but become unreachable from the UI for now — a deliberate, explicitly out-of-scope cleanup for a later change.
- Home's own Help control (`ChromeSubHeader`, `HomeHeaderRow`) and `ProjectDetailPage`'s "Share" button are untouched — this change only removes duplicated/unneeded controls from the editor's own header.

## Capabilities

### New Capabilities
- `editor-chrome`: Defines which actions the diagram editor's header (desktop island and mobile pill) exposes, and which adjacent-surface actions (ad-hoc sharing, new-diagram creation, Help/legal) it deliberately does not expose because they are already served elsewhere.

### Modified Capabilities
(none — `diagram-creation` and `projects` do not currently make claims about the editor header being an entry point for creation or sharing, so no existing requirements change)

## Impact

- **webapp**: `EditorHeader.tsx` (`HeaderActionsIsland` loses the Share button and `HelpMenu`), `MobileIslands.tsx` (`MobileActionsPill` loses the Share `IconButton` and the "mobile-help" `MobileMenuButton`), `FileMenu.tsx` (`FileMenuItems` loses the "New Diagram" item and its `handleNewDiagram`/`useModalContext` usage becomes unnecessary there). Associated `.stories.tsx` files and any snapshot/story fixtures referencing these controls.
- No backend impact. No change to `ShareModal`, `useShareableDiagram`, `sharedDiagramLinks`/`sharedDiagramStorage`, `HomeHelpMenu`, `HowToUseModal`, or any Home/`ProjectDetailPage` surface.
