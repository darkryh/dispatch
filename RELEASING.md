# Releasing Dispatch

CI/CD lives in `.github/workflows/`. This is the operator runbook.

## How a release happens

1. **Tag and push** a version tag — the version is the tag without the leading `v`:
   ```bash
   git tag v1.0.0-beta02
   git push origin v1.0.0-beta02
   ```
2. `release.yml` then, in order:
   - resolves the version from the tag and **fails fast** if that version already exists on Maven Central (coordinates are immutable);
   - runs the full gate (`./gradlew check verifyModuleBoundaries`) stamped with `-PVERSION_NAME=<version>`;
   - **publishes + auto-releases** all 15 `io.github.darkryh.dispatch:dispatch-*` modules to Maven Central
     (`publishToMavenCentral -PautoRelease=true`) — signed, straight to live;
   - creates a **GitHub Release** (`--generate-notes`, `--prerelease` for any `-`-qualified version);
   - explicitly dispatches `initializr-pages.yml` (`gh workflow run`), which **redeploys the Pages
     site** — a `GITHUB_TOKEN`-created release never fires the `published` event for other
     workflows (GitHub's recursion guard), so the redeploy is kicked directly.

   The `maven-central` Environment gates the publish job with a **required reviewer** — so even with
   auto-release, a human approves the run before anything ships.

   You can also run it manually: **Actions → Release → Run workflow**, entering the version.

## Versioning

- The root build version is `(-PVERSION_NAME) ?: "1.0.0-beta03-SNAPSHOT"`.
- Local/dev builds and `publishToMavenLocal` are therefore **SNAPSHOT** and never collide with a release.
- Only the release pipeline passes a concrete `-PVERSION_NAME`.

## Auto-release vs staged

- CI passes `-PautoRelease=true` → the deployment is released to Central automatically.
- **Any other** `publishToMavenCentral` (local, manual) defaults to `USER_MANAGED` — a *staged*
  deployment you review and release in the [Central Portal](https://central.sonatype.com). A dev
  machine can never auto-ship.

## Required repository secrets (Settings → Secrets → Actions, or the `maven-central` Environment)

| Secret | Maps to | What it is |
|---|---|---|
| `MAVEN_CENTRAL_USERNAME` | `ORG_GRADLE_PROJECT_mavenCentralUsername` | Central Portal **user token** name |
| `MAVEN_CENTRAL_PASSWORD` | `ORG_GRADLE_PROJECT_mavenCentralPassword` | Central Portal user token secret |
| `SIGNING_IN_MEMORY_KEY` | `ORG_GRADLE_PROJECT_signingInMemoryKey` | ASCII-armored PGP secret key (`gpg --armor --export-secret-keys`) |
| `SIGNING_IN_MEMORY_KEY_ID` | `ORG_GRADLE_PROJECT_signingInMemoryKeyId` | 8-char key id (optional) |
| `SIGNING_IN_MEMORY_KEY_PASSWORD` | `ORG_GRADLE_PROJECT_signingInMemoryKeyPassword` | PGP key passphrase |

## Public-API changes (the `apiCheck` gate)

Adding/removing public API fails `apiCheck` until you regenerate the frozen baseline:
```bash
./gradlew :dispatch-<module>:apiDump   # or ./gradlew apiDump for all
git add **/api/*.api
```

## GitHub Pages

`initializr-pages.yml` deploys the initializr site on `initializr/**` changes, on every release, and on
demand — **but only once repo Settings → Pages → Source = "GitHub Actions"**. It is currently
"Deploy from a branch" (`gh-pages`, the manual stopgap). Flip the source and retire `gh-pages` once
Actions minutes are available. Branch-based manual update meanwhile:
`./gradlew -p initializr wasmJsBrowserDistribution` then push the dist to `gh-pages`.

## PTY end-to-end tests

`terminalE2eTest` is **not** wired into `check` (kept fast + portable). CI runs it as its own gating
job; run it locally with `./gradlew :dispatch-sample:terminalE2eTest` (or `./gradlew check
-PrunTerminalE2e`). Grid navigation is computed from `CatalogDestination` so a layout change can't
silently break it.
