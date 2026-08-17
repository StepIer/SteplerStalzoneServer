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
| Base URL     | `http://localhost:8080` locally; see [Deployed environment](#deployed-environment) |
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

## Deployed environment

The server deploys to Render's free tier as a container. Ask for the current
`https://<service>.onrender.com` base URL — it is not recorded here, because it is assigned
at service-creation time.

Two things about that environment will affect your client code:

**Cold starts.** A free instance **spins down after 15 minutes of inactivity**, and the
first request after that takes **roughly a minute** while the container restarts. This is
the single most likely thing to break a client integration:

- Set request timeouts to at least 90 seconds, or the first call of a session fails.
- Do not treat one slow or timed-out request as the server being down. Retry once with a
  longer timeout before surfacing an error to the user.
- A loading state that tolerates a ~60s first response is worth building early.

**HTTPS and a different origin.** The deployed URL is `https://`, and it is a different
origin from your local dev server, so a browser client that works against
`http://localhost:8080` will hit CORS when pointed at Render. See
[Browser clients must be allowlisted](#browser-clients-must-be-allowlisted) — the deployed
allowlist is set through the `CORS_ALLOWED_HOSTS` environment variable, so adding your
origin needs no code change or redeploy. Just ask.

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

Append new entries below using the template above, newest last, and leave answered ones in
place with the outcome noted — the history is useful context for whoever picks this up next.

---

### REQ-1 — Take over clan-war stat recording entirely (architecture, not an endpoint)

**Status:** proposed · **Blocking?** No client work is blocked. This request exists because
the *client* is the wrong place for this job and we want to delete our implementation of it.

**Heads up on shape:** this one does not fit the template, and that is the point. The client
is not asking for an endpoint — it is asking the server to run an unattended job and write
to a database the client already reads. Most of what follows is the contract the server must
satisfy, not an API. The HTTP surface we do want is small, and it is REQ-2/REQ-3 below.

#### What the client needs to do — and why it can't

The client is an Android app (Kotlin/Compose) for one STALCRAFT clan. Its core job is to
record every clan member's clan-war performance: snapshot the roster's cumulative
kills/deaths/assists/grenades at each round's start, snapshot again after it ends, and store
the difference as that round's result.

It does this today with a foreground service, and the model is broken by construction:

- **Somebody has to press a button** before the first round each evening. If nobody does,
  that entire evening is lost with no trace.
- **The process must survive ~2.5 hours** of Android battery management, app switching, and
  OEM process killers. When it dies mid-evening, the rounds after that point are gone.
- Waking the app on a schedule instead would need `SCHEDULE_EXACT_ALARM`, which is denied by
  default on modern Android, needs re-arming after reboot, and gets killed anyway.

None of these are fixable on a phone. Clan wars happen at fixed times on fixed weekdays;
recording them belongs on something that is always awake. That is you.

#### Design decision we'd like you to adopt: snapshots are the primitive

Do **not** port the client's start/stop "recording session" model. It only exists because a
phone is awake intermittently. Instead:

```
at each scheduled boundary → snapshot the whole roster's cumulative stats, store it
a round's result          → subtract the snapshot bounding its start from the one bounding its close
```

Why this matters, concretely:

- **Restart-safe for free.** Each snapshot is an independent, idempotent write. A redeploy at
  21:12 strands nothing — there is no half-open session to recover. The client currently
  needs a whole `findOpen()` recovery path to clean up after its own crashes; you won't.
- **The settle window stops being a guess.** The client waits 4 minutes after a round's end
  before its closing snapshot, because measuring one real clan war showed ~6% of kills land
  after the round window closes. With a stored snapshot series you can re-derive a round with
  any settle value, from data, instead of trusting that one measurement forever.
- **A wrong schedule becomes recoverable.** Today, if round times shift and nobody updates
  the app, the evening is lost. With snapshots you fix the schedule and re-derive.

So: an append-only snapshot collection is the truth, and the recordings the client reads are
a **projection** of it. Please keep them as two separate collections.

#### Hard constraint: the roster read needs a *user* token

This is the one thing that will shape your auth design, so it is worth stating up front.
Verified against the EAPI docs:

| Call | Token required |
|---|---|
| `GET /{region}/clans/{clanId}/members` — the roster | **user** access token, app token will not do |
| `GET /{region}/character/by-name/{name}/profile` — the stats | app access token is fine |
| `GET /{region}/characters` — a user's characters | **user** access token |

- Base URL `https://eapi.stalcraft.net` (demo, no registration needed: `https://dapi.stalcraft.net`).
- OAuth: authorize `https://exbo.net/oauth/authorize`, token `https://exbo.net/oauth/token`,
  user info `https://exbo.net/oauth/user`. Send `Authorization: Bearer <token>`.
- **App token:** `grant_type=client_credentials` with client id/secret. No refresh token is
  issued; re-fetch on expiry.
- **User token:** `grant_type=authorization_code` → access + refresh token. Both app and user
  tokens report `expires_in: 31536000` — **one year** — so this is not a busy refresh loop.

The awkward consequence: you need a user token belonging to *an actual member of the clan*,
and obtaining the initial one requires an interactive browser consent that a headless server
cannot perform. Our proposal, unless you have a better one:

1. The Android app already implements the full authorization-code flow. We run it once and
   hand you the resulting **refresh token** via an admin endpoint (see REQ-3).
2. You store it, refresh proactively well before expiry, and **alert loudly if refresh ever
   fails** — that is the single failure that silently blinds the roster read.
3. Two hardening asks: accept **more than one** bootstrapped member's token, so one person
   leaving the clan doesn't stop recording; and **cache the last-known roster**, so a failed
   roster read degrades to "record the members we knew about" instead of losing the evening.

Clan resolution should be dynamic rather than configured: call `GET /{region}/characters` for
the bootstrapped user, take the first character that has a clan, and use its `clanId` +
`region`. Regions the client queries, in order: `ru`, `eu`, `na`, `sea`, `nea`.

#### The stat ids

A profile response carries a `stats` array of `{id, value}` where **`value` is a string** and
must be parsed to a number. The four metrics that matter:

| Metric | Stat id |
|---|---|
| Kills | `kil` |
| Deaths | `dea` |
| Assists | `ast` |
| Grenades thrown | `gre-thr` |

These are cumulative career totals, which is why everything is a subtraction of two
snapshots. Missing id → treat as `0`.

#### The schedule (current client defaults — port these, don't invent)

A schedule is a time zone plus **blocks**. A block is one kind of clan war with its own round
windows and its own weekdays. **A day belongs to at most one block**, so "what happens
tonight" always has a single answer.

```
zone: Europe/Vilnius

block id "tournament", name "Tournament"        block id "bases", name "Bases"
  round 1  21:05–21:25                            round 1  20:05–20:25
  round 2  21:30–21:50                            round 2  20:30–20:50
  round 3  21:55–22:15                            round 3  20:55–21:15
  roundsPerDay: Mon 0, Tue 0, Wed 0,              round 4  21:20–21:40
                Thu 3, Fri 3, Sat 3               roundsPerDay: Sun 4
```

Semantics worth copying exactly, because they are load-bearing:

- `roundsPerDay` holds **only** the days a block covers; a day set to `0` is a day it does not
  play. `roundCountOn(day)` is coerced into `0..rounds.size`.
- `roundsOn(day)` = the block's rounds sorted by number, `take(roundCountOn(day))`.
- Blocks are matched by **`id`**, never by name or list position, so renaming or reordering a
  block never loses its times.
- `SETTLE_AFTER_ROUND = 4 minutes` — how long past a round's end to wait before the closing
  snapshot.
- `SETTLE_GUARD_BEFORE_NEXT_ROUND = 30 seconds` — the closing snapshot is pulled back to at
  least this far before the next round opens. Crediting the next round's activity to this one
  is a worse error than missing the tail.
- **A round whose start has already passed must be skipped, not recorded.** Both snapshots
  would land after the fighting, so every delta would be zero — indistinguishable from a
  round nobody scored in. Recording that is worse than recording nothing.

All round times are wall-clock in the configured zone. Run the container in **UTC** and do
the zone math explicitly (`ZonedDateTime.of(date, time, zone)`), the way the client does.

#### The actual contract: Firestore document shapes

**Important and slightly unusual:** the client reads recordings **straight out of Cloud
Firestore**, not over HTTP from you. So the real integration contract here is a *database
schema*, and if you match it, the existing Android recordings list and detail screens keep
working with **zero client changes**. That compatibility is what makes this shippable in
phases instead of as one big bang.

Firebase project: the same one the app uses (`google-services.json` on our side; you'll want
an Admin SDK service account). Write with the **Admin SDK**, which bypasses security rules —
that is intended, and it lets us tighten the rules so clients become read-only.

Collection **`cwRecordings`**, one document per round — the shape the client already parses:

| Field | Type | Notes |
|---|---|---|
| `description` | string | Client displays it verbatim. Use `"<Block name> · Round <n>"`, e.g. `"Tournament · Round 1"`. Max 200 chars. |
| `status` | string | `"recording"` while open, `"completed"` once closed. **An unknown or missing value makes the client skip the document entirely.** |
| `recordedBy` | string | In-game name of who recorded it. Use something clearly non-human, e.g. `"auto"`. Blank is tolerated. |
| `clanId` | string | |
| `clanTag` | string | Shown in the list. |
| `region` | string | |
| `memberCount` | number | Roster size at the time. |
| `startedAt` | timestamp | **Ordering and paging key.** Client orders by this, descending. |
| `stoppedAt` | timestamp | |
| `before` | array | Opening snapshot, in roster order. |
| `after` | array | Closing snapshot. |
| `deltas` | array | `after − before`. |

Each entry in `before` / `after` / `deltas`:

```json
{ "name": "Strelok", "kills": 1234, "deaths": 567, "assists": 89, "grenades": 42 }
```

Rules the client relies on:

- **Only include a member in `deltas` if they appear in *both* snapshots.** A member whose
  profile read failed on either side must be *absent*, not zero — "played and scored nothing"
  and "we failed to measure them" must stay distinguishable. This is the single most important
  data-quality rule in the whole feature.
- `before`/`after`/`deltas` are read as ordered lists and keyed by `name`, so keep roster
  order consistent across the three.
- You can write a finished round in **one** write with `status: "completed"` — the client's
  two-phase open-then-close dance is an artifact of its own crash-recovery needs, not
  something you need to reproduce.
- **Document ids: please make them deterministic**, e.g. `2026-08-20_tournament_r1`. That makes
  every write idempotent and re-runs safe. The client never parses ids (it uses auto-ids today
  and orders by `startedAt`), so this is free.

Collection **`statSnapshots`** (new, yours, client does not read it yet). Suggested shape —
push back if you'd rather model it differently, this one is not settled:

```json
{
  "capturedAt": "<server timestamp>",
  "clanId": "…", "region": "ru",
  "blockId": "tournament", "round": 1, "boundary": "open",
  "members": [ { "name": "Strelok", "kills": 1234, "deaths": 567, "assists": 89, "grenades": 42 } ],
  "missed": ["Bandit", "Sidorovich"]
}
```

`missed` is the fix for a real bug in the client: it drops a member on a single transient
profile failure and the resulting gap is **invisible**. Recording who you failed to read makes
data quality auditable. Please don't inherit that bug.

#### Being a good API citizen

There are **no documented rate limits** for the EAPI, which means don't discover them the hard
way. A roster is ~50 members, so boundaries-only is ~50 × 2 × 7 ≈ 700 profile reads per
evening. Asks:

- Bound the fan-out (a `Semaphore(6)`-ish limit), and use exponential backoff with jitter on
  429/5xx.
- Retry a failed profile read at least once before declaring it missed. The client retries
  once on its export path but **not** on the recording path, which is backwards.
- **Start with boundaries only** — it reproduces current behavior exactly, so we can diff your
  output against known-good phone recordings from the same evening. Densify to a periodic poll
  (e.g. every 2 minutes through the window) only once the API is shown to tolerate it. The
  snapshot schema doesn't change when you do.

#### Non-goals — please don't build these

- **No recordings API for the client.** It reads Firestore directly and that is fine.
- **No auth, pagination, or CORS work** on our behalf. The app is not a browser client.
- **No push to the app.** Firestore snapshot listeners already give it live updates.

**Open questions for you** (we have opinions, not decisions):

1. **Hosting.** An in-process scheduler needs a process that doesn't sleep, which rules out
   scale-to-zero. We lean toward a small always-on container (Hetzner/Fly, ~€4/mo). Do you
   want to own that call?
2. **Where the schedule lives.** See REQ-3 — Firestore as shared source of truth, or your
   `application.yaml` with the client read-only?
3. **Alerting channel.** It is unattended, so it must tell us when it breaks. A Telegram bot
   is the cheapest thing that works for a clan. Preference?

---

### REQ-2 — "Did tonight actually record?" status endpoint

**What the client needs to do:** once you own recording, the CW tab stops being a control and
becomes a status view. A clan officer needs to answer one question at a glance: *is recording
healthy, and did tonight's rounds land?* Firestore tells them what was recorded, but not what
was **supposed** to be recorded and failed — that gap is the whole point of this endpoint.

**Proposed endpoint:** `GET /status`

**Success response:**

```json
{
  "recorder": "healthy",
  "serverTime": "2026-08-20T19:41:07Z",
  "zone": "Europe/Vilnius",
  "eapiSession": { "valid": true, "member": "Strelok", "expiresAt": "2027-06-01T10:22:00Z" },
  "clan": { "clanId": "…", "tag": "STPL", "region": "ru", "memberCount": 48 },
  "today": {
    "date": "2026-08-20",
    "blockId": "tournament",
    "blockName": "Tournament",
    "rounds": [
      { "round": 1, "start": "21:05", "end": "21:25", "state": "recorded",   "recordingId": "2026-08-20_tournament_r1", "membersRecorded": 47, "membersMissed": 1 },
      { "round": 2, "start": "21:30", "end": "21:50", "state": "inProgress", "recordingId": null, "membersRecorded": null, "membersMissed": null },
      { "round": 3, "start": "21:55", "end": "22:15", "state": "scheduled",  "recordingId": null, "membersRecorded": null, "membersMissed": null }
    ]
  },
  "lastSnapshotAt": "2026-08-20T18:29:03Z",
  "warnings": ["Round 1: 1 member could not be read (Bandit)"]
}
```

`recorder` is the one field we'd branch on: `"healthy"` / `"degraded"` / `"down"`. Round
`state`: `"scheduled"` / `"inProgress"` / `"recorded"` / `"skipped"` / `"failed"`. A day with
no clan wars returns `today.blockId: null` and an empty `rounds` array — please don't 404 that,
"nothing scheduled tonight" is a successful answer.

**Error cases:** `500` only if you genuinely can't answer. If the EAPI session is broken,
that is `recorder: "down"` with `eapiSession.valid: false` and a `200` — the client wants to
*display* that, not catch it.

**Called when:** on opening the CW tab, and on manual refresh. Not polled. Cheap is nice but
not critical; serve it from your own state, don't fan out to the EAPI to answer it.

**Blocking?** No — nice to have, and it can land after REQ-1 works. Without it the app can
still show recordings from Firestore; it just can't distinguish "no clan war tonight" from
"the recorder is dead".

---

### REQ-3 — Admin surface: EAPI session bootstrap, schedule, manual re-derive

**What the client needs to do:** three operational things that have nowhere to live otherwise.

**a) Hand you the EAPI user token.** The Android app runs the interactive OAuth flow you
can't, then delivers the refresh token to you once. Guessing at the shape:

`POST /admin/eapi/session` → `{ "refreshToken": "…" }` → `{ "member": "Strelok", "clanTag": "STPL", "expiresAt": "…" }`

Verify it works (resolve the character + clan) before storing, and reject a token whose
account isn't in a clan — failing at bootstrap is far better than failing silently at 21:05.
Supporting several stored sessions covers the "that member left the clan" case.

**b) Edit the schedule.** Round times do shift, and the app already has a working schedule
editor UI (time pickers, per-weekday round counts). Two options and we don't have a strong
preference — your call:

- **Firestore `config/cwSchedule` as shared source of truth.** You read it (ideally watch it,
  so an edit applies without a redeploy); the app reads it for display. Writes go through you,
  since the client has no Firebase Auth and world-writable config is not acceptable.
- **Your `application.yaml`,** client read-only via `GET /schedule`. Simpler, but every time
  change becomes a redeploy.

Either way we'd like `GET /schedule` returning the config, and `PUT /schedule` accepting an
edited one, in roughly the client's existing model:

```json
{
  "zone": "Europe/Vilnius",
  "blocks": [
    { "id": "tournament", "name": "Tournament",
      "rounds": [ { "number": 1, "start": "21:05", "end": "21:25" } ],
      "roundsPerDay": { "MONDAY": 0, "THURSDAY": 3, "FRIDAY": 3, "SATURDAY": 3 } }
  ]
}
```

Validation we'd like enforced server-side rather than trusted from the client: `end` after
`start`, no overlapping rounds within a block, **no weekday claimed by two blocks**, and
`roundsPerDay` counts within `0..rounds.size`.

**c) Re-derive.** The payoff of the snapshot model — recompute recordings from stored snapshots
after fixing a schedule or a settle value, without touching the EAPI:

`POST /admin/rederive` → `{ "date": "2026-08-20" }` → `{ "recordingsWritten": 3 }`

**Error cases:** `400` on a schedule that fails validation, with the reason in the standard
`{"error":"…"}` envelope — the app will surface it directly to the user, so make the message
human-readable. `401`/`403` on a bad admin credential.

**Auth:** these are admin-only and currently the server has no auth at all. A single static
bearer token in config is enough for now; we'll keep it out of the app's committed sources
the same way the EXBO client secret already is (read from a gitignored `local.properties`
into `BuildConfig`). Say the word if you'd rather these were CLI/curl-only for now and not
exposed to the app at all — that's a perfectly good phase-1 answer.

**Called when:** (a) once, at setup. (b) rarely, when times change. (c) manually, after a fix.

**Blocking?** (a) is **blocking for REQ-1** — you cannot read the roster without it, so it is
the first thing to build. (b) and (c) are follow-ups.

---

### Suggested order of work

1. **REQ-3(a)** — session bootstrap. Nothing else can read a roster without it.
2. **REQ-1 snapshots** — scheduler + roster read + profile fan-out + `statSnapshots` writes.
   Run this in parallel with the phone for a few evenings and **diff the results**; that is
   the correctness proof, and it costs nothing but patience.
3. **REQ-1 derivation** — write `cwRecordings`. The app starts showing your data with no
   changes on our side.
4. **REQ-2** — status endpoint. Then we delete the foreground service from the app.
5. **REQ-3(b/c)** — schedule + re-derive.

### One note on shared code

The schedule model, the stat ids, and the settle constants exist in the client today as pure
Kotlin on `java.time`, with no Android dependencies. Two independent implementations of
`roundsOn(day)` drifting apart is a genuinely likely failure, and a silent one. If you'd find
it useful, we can extract them into a plain-JVM Gradle module published for both repos to
depend on — say so and we'll do that work on our side. Until then, treat the tables in REQ-1
as the specification and tell us if you find them ambiguous.
