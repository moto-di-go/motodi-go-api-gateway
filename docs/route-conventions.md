# GATE-PLAT-001 - Route Conventions

## Public API Prefix

All public REST endpoints exposed by the API Gateway use `/api/v1`.
The gateway owns the external prefix and forwards the path unchanged to the downstream service.

## Downstream Ownership

The following prefixes are routed to `backend-core`:

- `/api/v1/auth/**`
- `/api/v1/profiles/**`
- `/api/v1/kyc/**`
- `/api/v1/drivers/**`
- `/api/v1/urban/**` — recherche urbaine, autocomplete Photon+lieux métier, preview/matching Valhalla.
- `/api/v1/pricing/**`
- `/api/v1/intercity/**`
- `/api/v1/bookings/**` — CORE-BOOK-001: `GET /api/v1/bookings/mine`, `GET /api/v1/bookings/{id}`,
  `POST /api/v1/bookings/{id}/confirm`, `POST /api/v1/bookings/{id}/cancel`. Subscription create/list
  also live under `/api/v1/intercity/announcements/{id}/bookings`. Ride pay remains CORE-PAY-002:
  `POST /api/v1/bookings/{id}/pay`.
- `/api/v1/wallet/**` — CORE-WALLET-001 reads: `GET /api/v1/wallet`, `GET /api/v1/wallet/ledger`. CORE-PAY-001 deposits: `POST /api/v1/wallet/deposits`, `GET /api/v1/wallet/deposits/{id}`.
- `/api/v1/payments/**`
- `/api/v1/nfc/**`
- `/api/v1/trips/**`
- `/api/v1/offline/**`
- `/api/v1/notifications/**`
- `/api/v1/support/**`
- `/api/v1/integrations/mobile-money/**`
- `/api/v1/admin/**`

The `/api/v1/ai/**` prefix is routed to `backend-ai` as a technical route.

No route is exposed directly to PostgreSQL/PostGIS, MinIO, payment providers or push providers.

## Mobile Money Callbacks (GATE-INTEGRATIONS-001)

Provider-specific public callbacks (no JWT):

- `POST /api/v1/integrations/mobile-money/mtn/callback`
- `POST /api/v1/integrations/mobile-money/orange/callback`
- `POST /api/v1/integrations/mobile-money/aggregator/callback`
- `POST /api/v1/integrations/mobile-money/callback` (legacy alias, defaults to aggregator)

Edge checks before forwarding to backend-core:

- `X-Signature` HMAC-SHA256 of the raw body (provider secret)
- Optional source IP / CIDR allowlist per provider
- Redis idempotency (`Idempotency-Key` or body hash) so a valid callback is forwarded once

Wallet credit and deposit initiation are handled by CORE-PAY-001 in backend-core.

## AI Routing And Resilience (GATE-INTEGRATIONS-001)

- `/api/v1/ai/**` is proxied to `backend-ai` with Resilience4j timeout and circuit breaker.
- On AI timeout / open circuit / proxy failure, the gateway returns an exploitable fallback JSON
  (`fallback: true`, empty `recommendations`) from `/__gateway/ai/fallback`.
- Backend-core routes use a separate circuit breaker without a business fallback.

## Correlation Id

Every response includes `X-Correlation-Id`.
If a client sends the header, the gateway preserves it. Otherwise, the gateway generates a UUID and propagates it to the downstream request.

## Health And API Docs

The following operational endpoints are intentionally unauthenticated but are not business API routes:

- `GET /actuator/health`
- `GET /actuator/health/readiness`
- `GET /actuator/health/liveness`
- `GET /actuator/info`

The test and local API documentation entrypoints are versioned:

- `GET /api/v1/docs`
- `GET /api/v1/openapi`

## Ticket Boundaries

`GATE-PLAT-001` implements the secure platform baseline: versioned routes, CORS, security headers, correlation id, health endpoints and API docs.
`GATE-AUTH-001` covers JWT validation, RBAC and rate limiting.
`GATE-INTEGRATIONS-001` covers Mobile Money callback edge verification, AI routing resilience and gateway AI fallback.
