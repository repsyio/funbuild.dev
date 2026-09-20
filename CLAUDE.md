# CLAUDE.md

@AGENTS.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

`funbuild` is a firatapp on the single-node K3s cluster at `apps.firat.io`:

- **Backend:** GitHub Actions → Repsy registry → ArgoCD → CNPG database → Traefik ingress → external-dns/Cloudflare, served at `api.funbuild.dev`.
- **Frontend:** the same image pipeline → cfpo (Cloudflare Pages operator) → Cloudflare Pages, served at `funbuild.dev`.

It's a weekly/bi-weekly programming-challenge platform: admins post assignments, members submit
projects against them (title, description, showcase URL, optional git repo URL, optional
free-form tech-stack labels) and vote, and admins can edit/delete any user, assignment or
project. See README.md's "Domain model" section for the full picture.

`README.md` has the layout, local dev and ops commands. Cluster conventions (ingress annotations, CNPG storage class, `CloudflarePage`) are in `repsyio/apps-firat-apps` `CLAUDE.md`.

## Commands

```sh
cd backend && ./mvnw verify                      # controller + CORS tests, no database needed
cd frontend && npm run build && npx ng test --watch=false
docker build -t funbuild:local backend/
docker build -t funbuild-frontend:local frontend/
docker run --rm -v "$PWD/helm:/chart:ro" alpine/helm:3 template funbuild /chart   # helm CLI is not installed locally
```

Angular 22 needs Node ≥ 22.22.3; use `nvm use 24`.

## How it fits together

- **Two images, one tag.**
  - `backend/Dockerfile` builds the API jar image, which serves only `/api` and actuator.
  - `frontend/Dockerfile` builds a `busybox` image holding the Angular build in `/site`.
  - Both are tagged `sha-<short>` from the same commit.
- **Frontend on Cloudflare Pages.**
  - `helm/templates/cloudflarepage.yaml` creates a `CloudflarePage`. cfpo copies `/site` out of the frontend image in a Job in `cfpo-system` (as uid 65532, with `sh`/`cp`) and uploads it to the Pages project `funbuild`.
  - `frontend.projectName` is immutable once the project exists. `pages.dev` names are global, so the project's subdomain may carry a suffix; `status.pagesDevUrl` on the `CloudflarePage` shows it.
  - A failed deploy isn't retried until the `CloudflarePage` spec changes, which normally happens with the next `image.tag`.
  - Don't add an Ingress for `funbuild.dev`: external-dns would claim the record and cfpo reports `DnsConflict`.
- **API URL and CORS.**
  - The production Angular build calls `https://api.funbuild.dev` (`src/environments/environment.ts`). The development build uses `environment.development.ts` (empty base URL, `proxy.conf.json`). Specs build the expected URL from `environment`.
  - `CorsConfig` builds a `CorsConfigurationSource` bean (GET/POST/PUT/DELETE/OPTIONS on `/api/**`) consumed by `SecurityConfig`'s `.cors(...)`, not a `WebMvcConfigurer` — Spring Security's filter chain runs before MVC, so CORS has to be wired there. Origins come from `app.cors.allowed-origins`; the chart sets `APP_CORS_ALLOWED_ORIGINS=https://<frontend.domain>`.
- **Auth.** JWT bearer tokens (`security/JwtService`, `security/JwtAuthFilter`) plus GitHub/Google OAuth2 login (`security/OAuth2LoginSuccessHandler` redirects to `<frontend>/oauth-callback#token=...`). `JwtAuthFilter` must save the authenticated `SecurityContext` through the `SecurityContextRepository` bean (`securityContextRepository.saveContext(...)`), not just `SecurityContextHolder.setContext(...)` — Spring Security 7's `AuthorizationFilter` resolves its deferred `Authentication` supplier from that repository, not from a live re-read of the holder, so a filter that only touches the holder authenticates silently for nothing and every request 401s.
- **Registration has no display name.** `RegisterRequest` is email/password only — `UserService.register` derives a placeholder display name from the email's local part (`defaultDisplayName`). Members change it afterward on `/profile` (`PUT /api/users/me` → `UserController.updateMe`, `UpdateProfileRequest`). Don't reintroduce a `displayName` field on the register form/DTO.
- **Frontend design system.** No UI kit (daisyUI was removed) — `frontend/src/styles.css` defines "Nocturne", a small set of plain CSS component classes (`.btn`, `.card`, `.tag`, `.input`, `.nav`, `.table`, `.vote-pill`, `.orb`, `.avatar-circle`, `.panel`, `.spinner`, …) on Nocturne design tokens, used alongside Tailwind utilities for layout. Dark theme only, no light variant. Extend that stylesheet rather than pulling in a UI kit.
- **Lazy associations and `open-in-view: false`.** Controllers never touch entities directly — `AssignmentService`/`ProjectService` map entity → response DTO themselves, inside a `@Transactional(readOnly = true)` method. Doing the mapping in the controller (or in a non-transactional service method) throws `LazyInitializationException` on any lazy field (`Assignment.createdBy`, `Project.assignment`/`submitter`/`techLabels`, …) once the session's closed — and since that exception surfaces through Tomcat's `/error` dispatch, which isn't in the security allowlist, the client sees a misleading 401 instead of a 500.
- Entities set `createdAt = Instant.now()` themselves in their constructor rather than relying on the column's SQL `DEFAULT now()` — Hibernate doesn't re-read a DB-generated default after `INSERT` unless the column uses `@Generated`, so a `insertable = false` timestamp column comes back `null` on the just-saved entity otherwise.
- Schema is owned by Flyway (`db/migration`); Hibernate runs with `ddl-auto: validate`. Add a new `V<n>__*.sql` to change it, and never edit an applied migration.
- The Helm release name and namespace must stay `funbuild`. The CNPG cluster is `<release>-db`, and the Deployment reads `username`/`password` from the operator-created secret `funbuild-db-app`.
- **Deploy tag bump.**
  - `image.tag` in `helm/values.yaml` is rewritten by the workflow's `deploy` job (`sed` on the line `  tag:`). It is used by both the Deployment and the `CloudflarePage`. Keep that key's indentation and keep it unique; `frontend.image` deliberately has no `tag`.
  - The workflow ignores `helm/**` and `**.md` pushes, so chart-only changes deploy through ArgoCD without a rebuild.
- **Registry credentials.** They are held by:
  - the pull secret `repsy-registry` in namespaces `funbuild` and `cfpo-system`
  - the GitHub secrets `REPSY_USERNAME`/`REPSY_TOKEN`

  Never commit them.
- **Auth credentials.** Hand-created Secret `funbuild-auth` in the `funbuild` namespace (`jwt-secret`, `github-client-id/secret`, `google-client-id/secret`), wired into the Deployment via `helm/values.yaml`'s `auth.secretName`. See README's "GitHub / Google OAuth2 apps" section. Never commit it. Google's client ID/secret are also saved in 1Password (`Repsy` vault, item "funbuild - Google OAuth") for reference; GitHub's are still the placeholder `unconfigured` values in the secret. After patching the secret, `kubectl -n funbuild rollout restart deploy/funbuild` — env vars from a Secret don't hot-reload.
- `server.forward-headers-strategy: framework` is required: Cloudflare terminates TLS and Traefik talks plain HTTP to the pod.
