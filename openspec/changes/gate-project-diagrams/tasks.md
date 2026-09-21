# Tasks

## 1. extension-backend: proxy the diagram-content REST surface

- [x] 1.1 Extend `DiagramsBackendClient` with `createDiagram(model)`, `getDiagram(redisId)`, `putDiagram(redisId, body, ifMatch?)`, mirroring `diagrams-backend`'s `POST/GET/PUT /api/diagrams(/:id)`; extend `DiagramsBackendClientTest` (currently only covers `deleteDiagram`) to cover each, including a 404-on-get and an `If-Match` conflict case, run against the existing `MockRestServiceServer` setup.
- [x] 1.2 Extend `DiagramsBackendClient` with `listVersions`, `createVersion`, `getVersion`, `restoreVersion`, `renameVersion`, `deleteVersion`, mirroring `diagrams-backend`'s `/api/diagrams/:diagramId/versions...` endpoints; add corresponding `DiagramsBackendClientTest` cases for each.
- [x] 1.3 Add project-scoped diagram-content endpoints to `ProjectsController` (`GET/PUT` body, and the six version operations) under `/api/v1/projects/{projectId}/diagrams/{diagramId}/...`, each resolving the diagram via `ProjectsService.getOwnedProjectOrThrow` + a project-scoped `Diagram` lookup before delegating to `DiagramsBackendClient`; verify with `ProjectsControllerTest` cases for an owner succeeding and a non-owner getting rejected on each new endpoint.
- [x] 1.4 Change diagram creation: replace `LinkDiagramRequest { redisId }` with a request carrying the diagram model; `ProjectsService.linkDiagramToProject` (or a renamed equivalent) calls `DiagramsBackendClient.createDiagram` itself and persists the resulting `redisId`; update `ProjectsControllerTest`/`DiagramDtos` accordingly and verify the old redisId-linking behavior is gone (no code path accepts a client-supplied `redisId` anymore).
- [x] 1.5 Remove `redisId` from `DiagramResponse`'s serialized JSON (keep it on the entity); update `ProjectsControllerTest` assertions that check diagram list/get responses to assert `redisId` is absent from the body.
- [x] 1.6 Resolve each project diagram's title/type/`updatedAt` server-side when listing a project's diagrams (via `DiagramsBackendClient.getDiagram`), so the response carries display metadata without the browser needing a second call; verify with a `ProjectsControllerTest` case asserting the enriched fields on `GET /api/v1/projects/{id}/diagrams`.

## 2. extension-backend: WebSocket ticket + relay

- [x] 2.1 Add `spring-boot-starter-websocket` to `build.gradle.kts`; verify the project still builds (`./gradlew build`).
- [x] 2.2 Add `POST /api/v1/projects/{projectId}/diagrams/{diagramId}/ws-ticket`: owner-only (via `getOwnedProjectOrThrow`), mints a random single-use token with a short TTL, stored in an in-memory map keyed by token to the diagram's `redisId`; verify with a controller test that a non-owner is rejected and an owner receives a token.
- [x] 2.3 Implement the WS relay endpoint: accept a connection carrying `?ticket=...`, consume the ticket (reject if missing/expired/already used), re-check project ownership, then open an upstream `java.net.http.WebSocket` client connection to `diagrams-backend`'s WS using the resolved `redisId` and relay frames verbatim in both directions until either side closes; verify with an integration test that a valid ticket connects and relays a message end-to-end (mocking or running `diagrams-backend`'s WS), and that an invalid/reused/expired ticket is rejected without opening an upstream connection.
- [x] 2.4 Verify ticket single-use and TTL expiry explicitly: a test that reconnects with the same ticket twice and asserts the second attempt is rejected, and a test (using a controllable clock or short TTL) that an expired ticket is rejected.

## 3. webapp: diagram + version gateway abstraction for project diagrams

- [x] 3.1 Extract the `diagrams-backend`-calling parts of the editor lifecycle (`ApollonShared`'s body fetch/save, `useDiagramSeed`, `createDiagramAutosaver`, `WebSocketManager` construction) behind a small gateway interface with a `"remote"` implementation (today's direct `DiagramApiClient`/`WebSocketManager` calls, unchanged behavior) and verify existing `ApollonShared.test.tsx`/`ApollonShared.collab.test.tsx` still pass unmodified against the `"remote"` path.
- [x] 3.2 Add a `"project"` implementation of that gateway: body fetch/save via the new `extension-backend` endpoints, and a WS connect path that first fetches a ticket over authenticated REST, then opens the relay WebSocket with it; add unit tests analogous to the existing `DiagramApiClient`/`WebSocketManager` test coverage.
- [x] 3.3 Add a `"project"` `RepositoryKind` to `webapp/src/services/versionRepository` (alongside `local`/`remote`), backed by the new `extension-backend` version endpoints; verify with tests mirroring `RemoteVersionRepository`'s existing test coverage.
- [x] 3.4 Add a new route `webapp/src/routes/projects.$projectId.diagrams.$diagramId.tsx` rendering the (now gateway-parameterized) shared editor component with both the diagram gateway and `VersionRepositoryProvider` set to `"project"`, with no `?view=` param/mode (per design.md decision 5: project diagrams always open live and editable); verify the route renders and loads a diagram body end-to-end in a test using a mocked `"project"` gateway.

## 4. webapp: point project-diagram UI at the new route

- [x] 4.1 Update `NewDiagramModal`'s `createDiagramInProject` to call the single new `extension-backend` create-and-link endpoint (task 1.4) instead of creating in `diagrams-backend` then linking, and navigate to the new project-diagram route (task 3.4) using the returned `extension-backend` diagram id; update `NewDiagramModal.test.tsx` accordingly.
- [x] 4.2 Update `ProjectDiagramCard` to link to the new project-diagram route using `diagram.diagramId` (the `extension-backend` id) instead of `sharedDiagramRoute(diagram.id)` (the `redisId`); update its test/story files.
- [x] 4.3 Update `ProjectDetailPage.loadDiagrams` to read title/type/`updatedAt` from `extension-backend`'s enriched project-diagrams response (task 1.6) instead of calling `DiagramApiClient.fetchStoredDiagram(row.redisId)` directly; remove the now-unused direct `diagrams-backend` call for this path and update its tests.

## 5. End-to-end verification

- [ ] 5.1 Manually verify: create a diagram inside a project, confirm the browser never receives a `redisId` (inspect network responses), edit it, reload, confirm changes persisted; open the same diagram in two browser sessions as the owner and confirm live collaboration works over the new relay.
- [ ] 5.2 Manually verify: an existing project diagram created before this change opens correctly through the new route with no data migration needed, and its old `/shared/$diagramId` link no longer works as an in-app navigation target.
- [ ] 5.3 Manually verify rejection paths: a second authenticated user (not the project owner) gets rejected calling the project-diagram REST endpoints directly, cannot obtain a ws-ticket for that diagram, and a WS connection attempt with no/invalid ticket is refused.
- [x] 5.4 Run the full test suites (`extension-backend` and `webapp`) and confirm everything passes.
