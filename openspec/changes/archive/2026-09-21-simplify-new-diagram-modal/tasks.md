# Tasks

## 1. New simplified modal component

- [x] 1.1 Create `webapp/src/components/modals/NewClassDiagramModal.tsx`: a `{ projectId?: string }`-scoped component using `HomeDialogContent`/`HomeDialogField`/`HomeDialogTextInput`/`HomeDialogActions` (mirroring `NewProjectModal.tsx`'s structure) with a single required name field (confirm disabled while the trimmed name is empty) and the class-diagram icon from `getDiagramTypeIcon(UMLDiagramType.ClassDiagram, ...)` shown non-interactively beside/above the field.
- [x] 1.2 Port `NewDiagramModal`'s creation logic for the `UMLDiagramType.ClassDiagram` case only: project-scoped creation via `ProjectsApiClient.createDiagram` + navigate to `/projects/$projectId/diagrams/$diagramId` (with the same loading state / error toast handling), and local creation via `usePersistenceModelStore`'s `createModelByTitleAndType` + navigate to `/local/$id`.
- [x] 1.3 Add `webapp/src/components/modals/NewClassDiagramModal.test.tsx` covering: project-scoped success (creates via `ProjectsApiClient.createDiagram` with `type: "ClassDiagram"` and navigates), project-scoped failure (toast shown, no navigation/close), local creation without `projectId`, and confirm disabled with an empty/whitespace-only name. Verify with `npm test -- NewClassDiagramModal` (or the project's equivalent vitest invocation) passing.
- [x] 1.4 Export the new component from `webapp/src/components/modals/index.ts` and verify the module resolves (build/typecheck succeeds).

## 2. Modal registry wiring

- [x] 2.1 Add `"NEW_CLASS_DIAGRAM"` to the `ModalName` union in `webapp/src/types/ModalTypes.ts`, keeping the existing `"NEW_DIAGRAM"` entry unchanged.
- [x] 2.2 Register `NEW_CLASS_DIAGRAM: NewClassDiagramModal` in `ModalWrapper.tsx`'s `MODAL_COMPONENTS`, add its title (`"New Diagram"`) to `MODAL_TITLES`, and confirm it satisfies the `satisfies Record<ModalName, ...>` check (typecheck passes) without needing a change to the `home-wide`/`home-compact` variant ternary.

## 3. Repoint entry points

- [x] 3.1 Update `webapp/src/pages/HomePage.tsx`'s `openNewDiagram` to call `openModal("NEW_CLASS_DIAGRAM", { dialogVariant: "home" })`.
- [x] 3.2 Update `webapp/src/pages/ProjectDetailPage.tsx`'s `handleNewDiagram` to call `openModal("NEW_CLASS_DIAGRAM", { projectId: id })`; update the matching assertion in `ProjectDetailPage.test.tsx` (currently expecting `"NEW_DIAGRAM"`) and verify the test passes.
- [x] 3.3 Update `webapp/src/components/navbar/FileMenu.tsx`'s `handleNewDiagram` to call `openModal("NEW_CLASS_DIAGRAM", { dialogVariant: "home" })`.

## 4. Verification

- [x] 4.1 Run the webapp test suite and confirm everything passes, including the untouched `NewDiagramModal.test.tsx` (still exercising the original, now-orphaned modal) and the updated `ProjectDetailPage.test.tsx`.
- [x] 4.2 Manually verify in the browser: opening "New Diagram" from the home dashboard, from a project's detail page, and from the editor's File menu each shows the simplified name-only modal with the class-diagram icon, the confirm button is disabled until a name is entered, and confirming creates a Class Diagram (locally, or inside the project when opened from one) and navigates to it.
