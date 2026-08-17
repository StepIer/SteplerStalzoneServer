# Client Integration Guide

**Audience:** whoever builds the client for this server — including a Claude Code session
working in the client repo.

This file is two things at once:

1. The **API contract** as it exists today, so the client can be built against facts
   rather than guesses.
2. A **channel for requesting server changes** — see [Requesting a change](#requesting-a-change).

Everything documented here was verified against a running build. If something below
disagrees with the server's actual behavior, the server is right and this file is a bug —
report it the same way you would request a change.

---

## Server at a glance

| Property     | Value                                              |
|--------------|----------------------------------------------------|
| Stack        | Kotlin 2.4, Ktor 3.5, Netty engine, JVM 21         |
| Base URL     | `http://localhost:8080` when run locally           |
| Port         | `$PORT` if set, otherwise `8080`                   |
| Payload      | JSON (`application/json`) for all structured data  |
| Auth         | **None.** Every endpoint is public.                |
| Persistence  | **None.** Nothing survives a restart.              |
| Repository   | https://github.com/StepIer/SteplerStalzoneServer   |

## Running the server locally

```sh
./gradlew run
```

Ready when you see:

```
INFO  Application - Responding at http://0.0.0.0:8080
```

Or run the self-contained jar, which is how it is deployed:

```sh
./gradlew build
PORT=9090 java -jar build/libs/SteplerStalzoneServer-all.jar
```

Every request is logged server-side as one line — `GET /health -> 200 OK`. If you are
debugging an integration, that log is the fastest way to see whether your request even
arrived and what path it actually hit.

---

## Endpoints

Only two exist so far. This is a young server; expect to request most of what you need.

### `GET /`

A liveness stub returning plain text, not JSON.

```sh
curl -i http://localhost:8080/
```

```
HTTP/1.1 200 OK
Content-Type: text/plain; charset=UTF-8

Hello, World!
```

### `GET /health`

```sh
curl -i http://localhost:8080/health
```

```
HTTP/1.1 200 OK
Content-Type: application/json

{"status":"UP"}
```

| Field    | Type     | Notes                                    |
|----------|----------|------------------------------------------|
| `status` | `string` | `"UP"` whenever the server can respond   |

It reports only that the process is serving traffic. It does **not** check downstream
dependencies, because there are none yet. Do not treat it as a readiness signal for
anything beyond "the port is open".

---

## Conventions to code against

**Errors share one envelope.** Any failure — expected or not — returns:

```json
{ "error": "human readable message" }
```

So a single client-side error type can parse every failure. Statuses in use:

| Status | When                                  | Body                                |
|--------|---------------------------------------|-------------------------------------|
| `404`  | No route matches the path             | `{"error":"Resource not found"}`    |
| `500`  | Unhandled server-side exception       | `{"error":"Internal server error"}` |
| `400`  | Malformed request — see note          | `{"error":"<reason>"}`              |

`400` handling is wired but currently **unreachable**: no endpoint accepts a request body or
typed parameters yet, so nothing can be malformed. It will start firing as soon as the first
input-accepting endpoint lands. `404` and `500` are live today.

**A `500` body is deliberately uninformative.** The real exception and stack trace are
logged on the server and never sent to you. If you hit a `500`, the message in your hands
will not diagnose it — ask for the server log, or report the request that triggered it.

**Unknown request fields are tolerated.** The JSON parser is configured with
`ignoreUnknownKeys`, so sending a field the server does not model yet is not an error —
it is silently ignored. Useful, but it also means **a typo'd field name fails silently**
rather than loudly. If a value seems not to take effect, suspect the spelling first.

**Response fields with default values are always present.** `encodeDefaults` is on, so you
will not see a field vanish from a response just because it holds its default.

---

## Browser clients must be allowlisted

If the client runs in a browser, cross-origin requests are **rejected until your origin is
explicitly allowed** — a preflight from an unknown origin gets `403`. This is intentional,
not a misconfiguration.

To get unblocked, tell us your origin as `host[:port]` with no scheme (e.g.
`localhost:3000`) and it gets added to `cors.allowedHosts` in `application.yaml`.

Allowed once configured: `GET POST PUT PATCH DELETE`, headers `Authorization` and
`Content-Type`, and credentials. Note that a successful preflight advertises only
`DELETE, OPTIONS, PATCH, PUT` in `Access-Control-Allow-Methods` — `GET` and `POST` are
absent because browsers always permit them. That is correct behavior, not a gap.

Not a browser client (mobile, CLI, another service, a game client)? CORS does not apply to
you at all. Ignore this section.

---

## Do not assume these exist

Assuming any of these will cost you a rewrite. None are implemented:

- **Authentication or authorization** of any kind — no tokens, no sessions, no users.
- **A database.** No data is stored; nothing persists across a restart.
- **WebSockets or SSE.** HTTP request/response only, no server push.
- **API versioning.** Paths have no `/v1` prefix, so a breaking change breaks you directly.
  If you need version stability, request it explicitly.
- **Pagination, filtering, or sorting** conventions. Nothing returns a collection yet, so
  the shape is still open — say what you want and it can be designed around your needs.
- **Rate limiting, retries, or idempotency keys.** Retrying a mutating request is not
  guaranteed safe once mutating endpoints exist.

---

## Requesting a change

Copy the template, fill it in, and get it to the server side by whichever route fits:

- Paste it into the Claude Code session working in this repo, **or**
- Open an issue on the repo, **or**
- Append it under [Open requests](#open-requests) and commit — useful for batching several
  requests, or when you want the ask recorded rather than answered immediately.

The template exists because the same three things are always missing from an informal ask:
concrete JSON, the error cases, and whether the client is blocked.

````markdown
### <short title>

**What the client needs to do:** <the user-facing behavior driving this, not the endpoint
you think you want. If the server can serve the goal a better way, that is worth knowing.>

**Proposed endpoint:** `<METHOD> /<path>`

**Request** (omit for GET):
```json
{ "field": "example value" }
```

**Success response:**
```json
{ "field": "example value" }
```

**Error cases:** <what should happen on invalid input, missing resource, conflict — and
which status code the client wants to branch on>

**Called when:** <on app start / on user action / polled every N seconds — this drives
whether it needs caching, pagination, or rate limits>

**Blocking?** <yes, client work is stopped / no, have a workaround / nice to have>
````

Two things that genuinely help:

- **Send real example JSON, not prose descriptions of it.** A concrete payload settles field
  names, nesting, and nullability in one pass instead of three.
- **Say if you are guessing.** A request marked "not sure this is the right shape" gets a
  design discussion; one that reads as settled gets built as written.

If a proposal conflicts with something already built, or would paint the API into a corner,
expect a counter-proposal rather than silent compliance.

## Open requests

None yet. Append new entries below using the template above, newest last, and leave
answered ones in place with the outcome noted — the history is useful context for whoever
picks this up next.
