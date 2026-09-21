# funbuild

A firatapp on the `apps.firat.io` K3s cluster. Admins post weekly/bi-weekly programming
assignments (e.g. "develop a mini racing game"); members submit projects against them and vote.
The homepage lists currently active assignments and the all-time most-voted submissions.

- Frontend: https://funbuild.dev (Cloudflare Pages)
- API: https://api.funbuild.dev (K3s)

| Directory | Contents |
| --- | --- |
| `backend/` | Spring Boot 4, Java 25, Maven, package-per-feature (`user`, `assignment`, `project`, `techlabel`, `vote`, `security`). JWT bearer auth plus GitHub/Google OAuth2 login. Flyway migrations in `src/main/resources/db/migration`. `Dockerfile` builds the API image. |
| `frontend/` | Angular 22 (routed, standalone components), Tailwind CSS 4 with a custom "Nocturne" dark theme (plain CSS component classes in `src/styles.css`, no UI kit). `Dockerfile` builds an image that only holds the built site in `/site`. |
| `helm/` | Chart deployed by ArgoCD: Deployment, Service, Ingress (`api.funbuild.dev`), CNPG `Cluster` `funbuild-db`, `CloudflarePage` `funbuild`. |

## Domain model

Users are `ADMIN` or `MEMBER` (self-registration with email/password, or GitHub/Google OAuth2 —
the first admin is promoted by hand, see below). A new local account starts with a display name
derived from its email's local part; members can change it afterward on `/profile`
(`PUT /api/users/me`). Admins create/edit/delete assignments with a
title, description, start and end date; a member can only submit while the assignment is
`ACTIVE`. A project submission has a title, description, showcase URL, optional git repo URL, and
optional free-form tech-stack labels (any member can type a new one into the picker; it's reused
by everyone afterward). Each member can cast one toggleable vote per project. Admins can also edit
or delete any user, assignment or project.

### Promoting the first admin

Every self-registered or OAuth account starts as `MEMBER`. After the first person registers,
promote them by hand:

```sh
kubectl -n funbuild exec -it funbuild-db-1 -- psql funbuild \
  -c "UPDATE users SET role='ADMIN' WHERE email='you@example.com';"
```

They need to log in again afterward — the role is baked into the JWT at login time.

### GitHub / Google OAuth2 apps

Register an OAuth app with each provider and point the callback URL at the backend, not the
frontend:

- GitHub OAuth App callback: `https://api.funbuild.dev/login/oauth2/code/github`
- Google OAuth client redirect URI: `https://api.funbuild.dev/login/oauth2/code/google`

The resulting client id/secret pairs go into the hand-created `funbuild-auth` Secret (see
Deployment below), not into git. Without them, email/password auth still works fully — the OAuth
buttons just fail upstream at the provider until real credentials are set.

## Local development

Requires Java 25, Node 24 and Docker.

```sh
docker run -d --name funbuild-pg -p 5432:5432 \
  -e POSTGRES_DB=funbuild -e POSTGRES_USER=funbuild -e POSTGRES_PASSWORD=funbuild postgres:18-alpine

cd backend && ./mvnw spring-boot:run        # http://localhost:8080
cd frontend && npm ci && npm start          # http://localhost:4200, proxies /api to :8080
```

The development build calls the API on its own origin (through the proxy). The production build calls `https://api.funbuild.dev` (`frontend/src/environments/environment.ts`). The backend allows cross-origin requests from `APP_CORS_ALLOWED_ORIGINS`, which defaults to `http://localhost:4200`, and redirects OAuth2 logins back to `APP_FRONTEND_URL` (same default). `GITHUB_CLIENT_ID`/`GITHUB_CLIENT_SECRET`/`GOOGLE_CLIENT_ID`/`GOOGLE_CLIENT_SECRET` and `APP_JWT_SECRET` all have local-dev defaults, so OAuth2 login won't work locally unless you set real values, but registration/login and everything else does.

Tests: `./mvnw verify` in `backend`, `npx ng test --watch=false` in `frontend`.
The backend verification lifecycle enforces Java 25 and Maven 3.9.7 or newer, runs
Checkstyle and SpotBugs, writes `target/site/jacoco/jacoco.xml`, and enforces the
current 13% JaCoCo instruction-coverage baseline. The threshold is a ratchet and
must rise as the test suite expands. CI runs this same `verify` command.

### API contract and generated models

The version-controlled OpenAPI source of truth is
[`backend/src/main/openapi/openapi.yaml`](backend/src/main/openapi/openapi.yaml). It covers every
`/api` endpoint, JWT bearer authentication, the OAuth authorization-code flows, request validation,
and both normal and error responses.

`./mvnw verify` validates the contract before generating Java transport models into
`backend/target/generated-sources/openapi`. Those files are compiled as part of the ordinary Maven
build and must never be edited or committed. Because generation always starts from the checked-in
contract, a clean CI build both regenerates the artifacts and fails for an invalid or inconsistent
specification. The existing feature-local request/response records remain deliberately as thin
server-side adapters: they provide the static entity-to-response mapping used by the service layer,
while the generated `dev.funbuild.api.model` package is the transport-model artifact. JPA entities
are not exposed.

The Angular app currently has hand-written TypeScript view models in `frontend/src/app/core/models.ts`.
Frontend generation is intentionally separate: no generated TypeScript client is committed, so its
build stays independent of the backend Maven toolchain. If a typed frontend client is introduced,
it must be generated from the same OpenAPI document.

The repository also contains the pinned `repsy-core` submodule at `core/`. Initialize it after
cloning with `git submodule update --init --recursive`; CI and the backend Dockerfile install its
required Maven modules before building funbuild. The API uses the shared `RestResponse<T>` envelope:
Angular unwraps successful `data` values at its HTTP boundary, while errors retain `type`, `msgId`,
`text`, and `errorCode`.

## Deployment

1. A push to `main` runs `.github/workflows/deploy.yml`. It runs the tests, then builds and pushes `repo.repsy.io/firat/apps/funbuild:sha-<short>` (backend) and `repo.repsy.io/firat/apps/funbuild-frontend:sha-<short>`, both also as `:latest`.
2. The workflow commits the new tag to `helm/values.yaml` (`[skip ci]`). Both images share it.
3. ArgoCD (Application `apps/funbuild.yaml` in `repsyio/apps-firat-apps`) auto-syncs the chart into namespace `funbuild`.
4. external-dns sees the Ingress and creates the proxied Cloudflare CNAME `api.funbuild.dev → apps.firat.io`.
5. The Cloudflare Pages operator (cfpo, Application `apps/cfpo.yaml`) sees the `CloudflarePage`:
   - It runs a deploy Job in `cfpo-system` that copies `/site` out of the frontend image and uploads it to the Pages project `funbuild`.
   - It attaches `funbuild.dev` as the project's custom domain and creates a CNAME to the project's `pages.dev` subdomain.

The workflow needs the repository secrets `REPSY_USERNAME` and `REPSY_TOKEN`.

The cluster pulls images with a `repsy-registry` secret. The one in `funbuild` is for the backend; cfpo's deploy Jobs use their own copy in `cfpo-system` (see `repsyio/apps-firat-infra`). They are created by hand and not stored in git:

```sh
export KUBECONFIG=../apps-firat-infra/.kubeconfig
kubectl create namespace funbuild
kubectl -n funbuild create secret docker-registry repsy-registry \
  --docker-server=repo.repsy.io --docker-username=apps --docker-password=<token>
```

After rotating the Repsy token, update the GitHub secrets and every `repsy-registry` secret.

The app also needs a hand-created `funbuild-auth` Secret in the `funbuild` namespace, referenced
by `helm/values.yaml`'s `auth.secretName` (see `helm/templates/deployment.yaml`):

```sh
kubectl -n funbuild create secret generic funbuild-auth \
  --from-literal=jwt-secret="$(openssl rand -base64 48)" \
  --from-literal=github-client-id=<id> --from-literal=github-client-secret=<secret> \
  --from-literal=google-client-id=<id> --from-literal=google-client-secret=<secret>
```

The GitHub/Google values can be left as placeholders (e.g. `unconfigured`) until those OAuth apps
are registered (see "GitHub / Google OAuth2 apps" above) — email/password auth doesn't need them.

## Operations

```sh
kubectl -n argocd get application funbuild
kubectl -n funbuild get cluster,pods,ingress,cloudflarepage
kubectl -n funbuild logs deploy/funbuild
kubectl -n funbuild exec -it funbuild-db-1 -- psql funbuild
kubectl -n funbuild describe cloudflarepage funbuild   # deploy, domain and DNS events
kubectl -n cfpo-system get jobs                 # frontend deploys
kubectl -n cfpo-system logs deploy/cfpo
```

Dependabot checks Maven, npm, Docker and GitHub Actions dependencies weekly.
