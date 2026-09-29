# Security

## Overview
NoteTaker is an OAuth2 **resource server**. Every request (except public URIs) must carry a
valid `Authorization: Bearer <JWT>`. JWTs are accepted from **multiple trusted issuers**,
selected per-request by the token's `iss` claim using Spring Security's
`JwtIssuerAuthenticationManagerResolver`.

### Trusted issuers
1. **FIT-UI OKTA issuer** — production/user traffic. Real users authenticate through the UI
   (same source the worklist service consumes) and present an OKTA JWT. The caller's identity
   is the **`employeeNumber`** claim, used as the note `ownerId`.
2. **Self / bypass issuer** — a second trusted issuer we control so we can mint JWTs and run
   integration tests **without** calling OKTA.

Configured under `notetaker.security.token-issuers.*`:
```yaml
notetaker:
  security:
    user-id-claim: employeeNumber
    token-issuers:
      fit-ui-issuer:
        url: https://purpleid-test.oktapreview.com/oauth2/ausXXXXXXXX
        audience: <app-audience>
        keys-uri: /v1/keys
      test-issuer:
        url: https://notetaker-test-issuer.local
        keys-uri: /v1/keys
    jwks:
      key-cache-refresh-minutes: 240
      outage-protection-minutes: 1440
```

## JWKS / "keychain" caching (SMS pattern)
Each issuer gets a `NimbusJwtDecoder` backed by a **cached** `JWKSource`
(`com.nimbusds.jose.jwk.source.JWKSourceBuilder`) so signing keys are **not** re-fetched or
re-validated on every request:
- `.cache(ttl, refreshTimeout)` — keys cached for `key-cache-refresh-minutes`.
- `.refreshAheadCache(true)` — keys refreshed *before* expiry (no request-time stalls).
- `.retrying(true)` and `.outageTolerant(outageTtl)` — stale keys served for
  `outage-protection-minutes` if the issuer is briefly unreachable.

> Note: like SMS, this caches **JWKS public keys** (the material used to *validate* tokens),
> not raw bearer tokens. A validated-token cache (e.g. Caffeine keyed by a token hash with a
> short TTL) can be added later if profiling shows validation is hot.

Decoders validate: signature (RS256/384/512), `iss` (issuer), `exp`/`nbf` (timestamps), and
`aud` (audience) when configured.

## Filter chains
- **Order 1** (`local` / `component-test` only): permit `/h2-console/**`, disable CSRF and
  frame options so the console renders.
- **Order 2** (default): CORS; `public-uris` permitted; everything else `authenticated()`;
  `oauth2ResourceServer` with the multi-issuer `authenticationManagerResolver`;
  authentication failures routed through the `GlobalExceptionHandler` (→ `ErrorResponse`, 401).

## Testing with the bypass issuer
Integration tests start an in-process `MockWebServer` that serves a JWKS document for the
`test-issuer`, wire its URL via `@DynamicPropertySource`, and mint RS256-signed JWTs
(carrying an `employeeNumber` claim) with the matching private key. This exercises the real
decode/validate/caching path end-to-end without OKTA. Unit-level controller tests may instead
use `spring-security-test`'s `jwt()` post-processor.
