# SteplerStalzoneServer

This project was created using the [Ktor Project Generator](https://start.ktor.io).

Here are some useful links to get you started:

* [Ktor Documentation](https://ktor.io/docs/home.html)
* [Ktor GitHub page](https://github.com/ktorio/ktor)
* [Ktor Slack chat](https://app.slack.com/client/T09229ZC6/C0A974TJ9). [Request an invite](https://surveys.jetbrains.com/s3/kotlin-slack-sign-up).

## Features

Here's a list of features included in this project:

| Name               | Description                                                     |
|--------------------|-----------------------------------------------------------------|
| Netty engine       | Serves HTTP via `EngineMain`, entry point `com.stepler.MainKt`  |
| Routing            | Endpoints declared in `Routing.kt`                              |
| YAML config        | `application.yaml`; port comes from `$PORT`, defaulting to 8080 |
| ContentNegotiation | JSON in/out via kotlinx.serialization                           |
| StatusPages        | Uniform JSON error bodies; internals never reach the client     |
| CallLogging        | One `METHOD path -> status` line per request                    |
| CORS               | Closed by default; opt in via `cors.allowedHosts`               |
| Logback            | Console logging at `INFO`                                       |

## Building & Running

To build or run the project, use one of the following tasks:

| Task              | Description       |
|-------------------|-------------------|
| `./gradlew test`  | Run the tests     |
| `./gradlew build` | Build the project |
| `./gradlew run`   | Run the server    |

If the server starts successfully, you'll see the following output:

```
2024-12-04 14:32:45.584 [main] INFO  Application - Application started in 0.303 seconds.
2024-12-04 14:32:45.682 [main] INFO  Application - Responding at http://0.0.0.0:8080
```

## Deploying

Deploys to [Render](https://render.com) from this repo — free tier, no CLI and no card
required. `render.yaml` and `Dockerfile` are the whole configuration.

1. Push to `main` (Render builds from GitHub).
2. In the Render dashboard: **New → Blueprint**, pick this repo. It reads `render.yaml`.
3. It prompts for `CORS_ALLOWED_HOSTS`. Leave blank to keep cross-origin access closed,
   or enter your client's origin as `host[:port]` — e.g. `localhost:3000`.
4. **Apply**. First build takes a few minutes (Gradle downloads dependencies); later
   builds reuse the cached dependency layer.

Verify with `curl https://<your-service>.onrender.com/health` → `{"status":"UP"}`.

The container is portable — the same `Dockerfile` runs on Fly.io, Railway, or Cloud Run if
you outgrow Render. Nothing here is Render-specific except `render.yaml`.

### Free tier caveat

A free service **spins down after 15 minutes of inactivity**, and the next request takes
roughly a minute while it restarts. Fine for development; set generous client timeouts, and
move to a paid instance before anyone depends on it. The free tier also has 750 instance
hours per workspace per month.

### Running the container locally

```sh
docker build -t stalzone-server .
docker run --rm -e PORT=8080 -p 8080:8080 stalzone-server
```

## Building a client against this server

[CLIENT.md](CLIENT.md) is the API contract for client developers: endpoint shapes, the error
envelope, CORS setup, what does *not* exist yet, and a template for requesting server
changes. Hand that file to whoever (or whatever) writes the client.

## Endpoints

| Method | Path      | Response                                |
|--------|-----------|-----------------------------------------|
| `GET`  | `/`       | `Hello, World!` as `text/plain`         |
| `GET`  | `/health` | `{"status":"UP"}` as `application/json` |

Unknown paths and unhandled exceptions return a JSON `{"error":"..."}` body.

## Configuration

Settings live in `src/main/resources/application.yaml`.

| Key                    | Default              | Meaning                                                                  |
|------------------------|----------------------|--------------------------------------------------------------------------|
| `ktor.deployment.port` | `$PORT`, else `8080` | Listen port                                                              |
| `cors.allowedHosts`    | `[]`                 | Browser origins allowed to call the API, as `host[:port]` with no scheme |

Cross-origin requests are rejected until you list an origin. To let a frontend on
`http://localhost:3000` call the API:

```yaml
cors:
  allowedHosts: [ "localhost:3000" ]
```
