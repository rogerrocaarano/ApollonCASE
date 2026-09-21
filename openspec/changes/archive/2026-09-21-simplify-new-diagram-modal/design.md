# Design

## Context

`NewDiagramModal.tsx` currently renders a two-tab UI (`scratch` type picker across 13 `UMLDiagramType`s, `template` picker across 5 design-pattern JSON templates) plus a name field, and is opened via `openModal("NEW_DIAGRAM", ...)` from three call sites: `HomePage.tsx`, `ProjectDetailPage.tsx` (with `projectId`), and `FileMenu.tsx`. It already defaults to `UMLDiagramType.ClassDiagram` on the `scratch` tab, and creation forks on whether `projectId` is set: project-scoped creation calls `ProjectsApiClient.createDiagram(projectId, model)` and navigates to `/projects/$projectId/diagrams/$diagramId`; local creation calls `usePersistenceModelStore`'s `createModelByTitleAndType` and navigates to `/local/$id`. See proposal.md - Why for the motivation to simplify this down to name-only, class-diagram-only creation.

## Goals / Non-Goals

**Goals:**
- Replace the modal shown at all three entry points with a name-only flow that always creates a Class Diagram.
- Reuse the existing creation logic (project-scoped vs. local) and shared `HomeDialog*` primitives so the new modal matches today's visual language (see `NewProjectModal.tsx` for the equivalent single-field pattern already in the codebase).

**Non-Goals:**
- Removing or altering `NewDiagramModal.tsx`, its template tab, or any template asset/util (`TemplateThumbnail.tsx`, `templateModels.ts`, `templateThumbnails.ts`, `webapp/assets/diagramTemplates/*.json`). They stay registered and unused, for when the product scope grows beyond class diagrams.
- Changing anything about how a diagram is persisted, versioned, or collaborated on once created — only the creation entry UI changes.
- Changing the `projects` capability's permission rules for who may create a diagram.

## Decisions

**New component instead of modifying `NewDiagramModal.tsx` in place.**
A confirmed product decision (see proposal.md) is to keep the existing multi-type/template modal intact for future use. Stripping it down in place and later re-expanding it would be more disruptive than adding a small, separate component now. New file: `webapp/src/components/modals/NewClassDiagramModal.tsx`.

**New modal registry key `NEW_CLASS_DIAGRAM`, added alongside the existing `NEW_DIAGRAM`.**
`ModalTypes.ts`'s `ModalName` union gains `"NEW_CLASS_DIAGRAM"`. `ModalWrapper.tsx`'s `MODAL_COMPONENTS`/`MODAL_TITLES` maps gain an entry (title: `"New Diagram"`, matching current user-facing copy). The variant logic (`name === "NEW_DIAGRAM" ? "home-wide" : "home-compact"`) needs no change: `NEW_CLASS_DIAGRAM` falls into the `"home-compact"` branch already used by `NEW_PROJECT`, which fits a single-field dialog. `NEW_DIAGRAM` itself, and its `"home-wide"` sizing, stay exactly as-is for `NewDiagramModal`.

**Props stay `{ projectId?: string }`, mirroring `NewDiagramModal`.**
This is the minimum needed to fork project-scoped vs. local creation, and keeps the three call sites' `openModal(...)` calls structurally identical (only the modal name changes).

**Name is required.**
Confirm is disabled until the trimmed name is non-empty, matching `NewProjectModal`'s `isValid` pattern. This is a deliberate behavior change from `NewDiagramModal` (which allows an empty name and creates an "Untitled" diagram) — confirmed with the user as the desired behavior for the simplified flow.

**Class-diagram icon is reused, not redrawn.**
`getDiagramTypeIcon(UMLDiagramType.ClassDiagram, className)` from `diagramTypeMeta.tsx` already renders the class-diagram glyph and accepts a size override (as `NewDiagramModal` does for its option tiles). The new modal calls it directly with a fixed, non-button wrapper (e.g. a plain `<div>`) — no `onClick`, no `aria-pressed` — so it reads as decoration, matching the "shown, not chosen" spec requirement.

**Call sites are repointed immediately, not feature-flagged.**
`HomePage.tsx`, `ProjectDetailPage.tsx`, and `FileMenu.tsx` change their `openModal("NEW_DIAGRAM", ...)` call to `openModal("NEW_CLASS_DIAGRAM", ...)` with the same props they already pass (`{ dialogVariant: "home" }` or `{ projectId }`). `NewDiagramModal` becomes reachable only by a future call site change, not by any UI in this change.

## Risks / Trade-offs

- **Two near-duplicate modals coexist long-term** (`NewDiagramModal` orphaned, `NewClassDiagramModal` live) → Accepted trade-off per the explicit product decision to keep the original for future multi-type support; the alternative (deleting and later reconstructing it) is strictly more work if/when scope grows.
- **`ModalName` union grows without ever removing `"NEW_DIAGRAM"`** → Low risk: it's one extra union member and registry row, consistent with how `NewProjectModal`/`RenameProjectModal` already coexist as separate modals.
