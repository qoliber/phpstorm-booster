# PhpStorm Booster

A PhpStorm/IntelliJ plugin with tools for **better local development practices** — focused on taming indexing and cache bloat on large PHP/Magento projects.

> **Status: `0.1.0` — concept release.** Built to validate the idea on real projects. Expect rough edges; see [Caveats](#caveats).

Compatible with **PhpStorm 2025.3** (build `253.*`).

---

## Why

On large Magento/PHP codebases, PhpStorm's indexing causes two recurring pains:

1. **Huge generated/vendor folders** (`var`, `generated`, `pub/static`, `node_modules`, …) get indexed on every project, slowing everything down.
2. **Index caches grow without bound** — repeatedly removing and re-adding `vendor/` (e.g. `composer install`) leaves dead index records behind. The global cache (`~/.cache/JetBrains/PhpStorm…/index`) can balloon to many GB and eventually destabilize the IDE.

PhpStorm Booster bundles two feature areas to address these.

---

## Features

### 1. Index Blocker

Globally exclude folders from indexing across **all** projects — configure once, applies everywhere.

- Settings → **Tools → Index Blocker**
- Three match forms per entry:
  - **Exact name** — `var` matches any folder named `var` at any depth
  - **Prefix wildcard** — `Test*` matches `Test`, `Tests`, `TestUtils`, …
  - **Relative path** — `pub/static` matches that path under a content root
- Default block list: `node_modules`, `var`, `generated`, `dev`, `pub/static`, `Test*`
- Matched folders show the standard "excluded" mark and are skipped from indexing/search.
- Live refresh when settings change or matching folders appear.

### 2. Cache Health

Visibility into and control over PhpStorm's cache/index storage.

- **Main-toolbar widget** showing the live index cache size; click for actions.
- **Quick Trim** — clears safe-to-reclaim caches (logs immediately; embedded-browser/AI caches scheduled for next restart, since the running IDE locks them).
- **Invalidate Caches & Restart** — one click, delegates to the native PhpStorm action.
- **Size monitor** — warns when the index cache exceeds a threshold (default **8 GB**), once per session.
- **Enforce disabling of downloadable Shared Indexes** on every startup (default on).
- Settings → **Tools → Cache Health**.

---

## Install

### From a release zip
1. Download `phpstorm-booster-<version>.zip` (from a [CI build artifact](#ci) or a release).
2. PhpStorm → **Settings → Plugins** → ⚙ → **Install Plugin from Disk…** → pick the zip.
3. Restart if prompted.

### Build from source
Requires a JDK/JBR 21.
```bash
./gradlew buildPlugin
# → build/distributions/phpstorm-booster-<version>.zip
```
Other useful tasks:
```bash
./gradlew test          # unit + light integration tests
./gradlew verifyPlugin   # JetBrains plugin compatibility check
./gradlew runIde         # launch a sandbox PhpStorm with the plugin
```

---

## CI

Every push and pull request builds the plugin and uploads the zip as a workflow artifact — see [`.github/workflows/build.yml`](.github/workflows/build.yml). Download the artifact from the **Actions** tab of a completed run.

---

## Caveats

This is a `0.1.0` concept; known limitations:

- **Disabling downloadable Shared Indexes may not shrink the cache** and can make local indexing do *more* work. It's included because it's a deliberate user preference, not because it reduces `index/` size. You can turn it off in Cache Health settings.
- **Cache Health Quick Trim** clears logs/JCEF/AI caches, not the main `index/` storage. To reclaim `index/` itself, use **Invalidate Caches & Restart** (rebuilds a compacted index for active projects only).
- Built against the PhpStorm 2023.3 SDK originally, retargeted to 2025.3. Two internal-API usages (`AppLifecycleListener.appStarted`, delegating to the native invalidate action) produce verifier warnings — non-fatal, slated for cleanup.
- The plugin uses only platform APIs; the code works in any IntelliJ-based IDE, though it's published for PhpStorm.

---

## License

Copyright © Qoliber. All rights reserved (license TBD).
