# Proposal

## Why

The product's scope, at this stage, is limited to class diagrams. The current "New Diagram" modal (`NewDiagramModal`) still exposes 13 diagram types across two tabs, plus a "Use template" tab with 5 design-pattern templates — complexity that no longer matches what the product supports. Users creating a diagram today have to pick a type and dismiss template options that don't apply to them, when the only decision that matters is the diagram's name.

## What Changes

- Add a new, simplified "New Diagram" modal that asks only for a diagram name and creates a Class Diagram — no type picker, no template tab. The name is required (creation is disabled until non-empty).
- The modal shows the existing class-diagram icon (reused from `diagramTypeMeta.tsx`) next to the name field as a fixed, non-interactive indicator of what's being created.
- Repoint the three existing entry points (`HomePage`, `ProjectDetailPage`, `FileMenu`) to open the new simplified modal instead of the current one.
- The new modal reuses today's creation paths: project-scoped creation via `ProjectsApiClient.createDiagram` (see `gate-project-diagrams`), and local (IndexedDB) creation via `usePersistenceModelStore`.
- Keep the existing `NewDiagramModal` (multi-type picker + templates) in the codebase, registered but unused by any call site, so it's available again if the product later supports more diagram types. Its templates tab, `TemplateThumbnail`, `templateModels.ts`, `templateThumbnails.ts`, and the template JSON assets are all left as-is — nothing is deleted.

## Capabilities

### New Capabilities
- `diagram-creation`: What the webapp's diagram-creation UI must do — restricting creation, at this stage, to naming a Class Diagram, for both local and project-scoped creation.

### Modified Capabilities
(none — `projects`' existing requirements around who may create a diagram are about permission, not the modal's shape, and are unaffected)

## Impact

- New: `webapp/src/components/modals/NewClassDiagramModal.tsx` (+ its test file).
- Modified: `webapp/src/types/ModalTypes.ts` (new `NEW_CLASS_DIAGRAM` modal name), `webapp/src/wrappers/ModalWrapper.tsx` (registry + title), `webapp/src/components/modals/index.ts` (export), `webapp/src/pages/HomePage.tsx`, `webapp/src/pages/ProjectDetailPage.tsx` (+ its test), `webapp/src/components/navbar/FileMenu.tsx`.
- Untouched: `NewDiagramModal.tsx` and everything template-related (kept registered under `NEW_DIAGRAM` for future use).
