export const serverURL = import.meta.env.VITE_SERVER_URL || ""

// extension-backend is not proxied by this webapp's nginx (unlike diagrams-backend,
// which production routes at the same host via Traefik) - it isn't deployed
// anywhere yet, so this always needs an explicit absolute URL.
export const extensionServerURL = import.meta.env.VITE_EXTENSION_SERVER_URL || ""

export const serverWSSUrl =
  import.meta.env.VITE_SERVER_URL_WSS ||
  `${location.protocol === "https:" ? "wss:" : "ws:"}//${location.host}/ws`

export const bugReportURL =
  "https://github.com/ls1intum/Apollon/issues/new?labels=bug&template=bug-report.md"
