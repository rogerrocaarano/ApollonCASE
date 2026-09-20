// Keycloak realm (OIDC issuer) and public client id the webapp authenticates
// against. Both are non-secret: the client is public (Standard Flow + PKCE,
// no client secret) - see openspec/changes/add-keycloak-auth/design.md.
export const keycloakAuthority = import.meta.env.VITE_KEYCLOAK_AUTHORITY || ""

export const keycloakClientId = import.meta.env.VITE_KEYCLOAK_CLIENT_ID || ""
