// A bare "" (no VITE_SERVER_URL set) resolves requests as relative to the
// page's own origin. That only ever worked because production Traefik used
// to route diagrams-backend at the webapp's own host under /api - as of
// containerize-production-stack, diagrams-backend is private (no public
// route at all), so that same-origin fallback no longer reaches anything in
// production. See that change's proposal.md for why this is accepted, not
// fixed, here.
export const serverURL = import.meta.env.VITE_SERVER_URL || ""

// extension-backend IS now deployed and Traefik-routed at the webapp's own
// host, under the same /api prefix diagrams-backend used to occupy (see
// containerize-production-stack) - but this still always needs an explicit
// absolute URL rather than falling back to the page's origin like serverURL
// above once did, since that fallback behavior was never added here.
export const extensionServerURL = import.meta.env.VITE_EXTENSION_SERVER_URL || ""

// Same same-origin caveat as serverURL above: this only reaches
// diagrams-backend's collaboration relay in production when something else
// routes /ws there, which containerize-production-stack's Traefik config no
// longer does (that prefix now belongs to extension-backend's /ws/diagrams).
export const serverWSSUrl =
  import.meta.env.VITE_SERVER_URL_WSS ||
  `${location.protocol === "https:" ? "wss:" : "ws:"}//${location.host}/ws`

// extension-backend's collaboration relay (see gate-project-diagrams). Derived
// from extensionServerURL the same way serverWSSUrl derives from the page
// origin — swap the http(s) prefix for ws(s). extensionServerURL is always an
// explicit absolute URL (see its comment above), so there's no bare-origin
// fallback to compute here.
export const extensionWssURL = extensionServerURL
  ? extensionServerURL.replace(/^http/, "ws")
  : ""

export const bugReportURL =
  "https://github.com/ls1intum/Apollon/issues/new?labels=bug&template=bug-report.md"
