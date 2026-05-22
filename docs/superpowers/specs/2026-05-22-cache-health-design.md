# Cache Health — Design

**Date:** 2026-05-22
**Status:** Approved (brainstorming phase)
**Author:** jwinkler@qoliber.com
**Builds on:** the existing Index Blocker plugin (v1, folder exclusion). This adds a second feature area in the same plugin.

## Purpose

PhpStorm's global index/cache storage grows without bound on this user's machine (measured: 12 GB total under `~/.cache/JetBrains/PhpStorm2025.3`, of which `index/` is 6.1 GB). The main driver is vendor churn: `rm -rf vendor && composer install` re-indexes thousands of files on every cycle, the platform assigns new file IDs even for byte-identical files, and dead index records accumulate until a full rebuild compacts them. The bloat is cumulative across the user's ~40 projects and eventually destabilizes the IDE.

This feature gives the user visibility into cache size and tools to reclaim it, plus enforces the user's explicit preference to keep downloadable Shared Indexes disabled.

## Scope

**In scope:**
1. Live cache-size display in the **main toolbar**.
2. **Quick trim** of safe-to-clear caches (logs, JCEF browser cache, optionally full-line AI cache).
3. **One-click Invalidate & Restart** wrapping the platform's native action.
4. **Size monitor** that warns when `index/` exceeds a threshold (default 8 GB).
5. **Enforce disabling of downloadable Shared Indexes** on every startup.
6. Settings page for thresholds and toggles.

**Out of scope:**
- Selective garbage-collection of dead index records (no public platform API exists).
- The v2 Composer/`.idea` normalizer (tracked separately).
- Cross-IDE distribution tuning beyond PhpStorm.

## Deliberate decisions (with caveats recorded)

- **Disabling downloadable Shared Indexes may not shrink `index/`** and can make local indexing do *more* work. The user was advised of this and explicitly chose to enforce it anyway. It is therefore included as a first-class, default-on feature.
- **Build target moves to PhpStorm 2025.3.** The v1 folder-blocker used only ultra-stable APIs and compiled against 2023.3. This feature uses version-sensitive APIs (main-toolbar widget group, shared-index registry key, scheduled deletion), so the whole plugin will now compile and verify against 2025.3. `platformVersion=2025.3`, `pluginSinceBuild=253`, `pluginUntilBuild=253.*`.
- **Quick trim is hybrid**, not live-delete-everything: safe content (rotated logs) is deleted immediately; locked dirs (`jcef_cache/`, etc.) are scheduled for deletion on next restart via `StartupActionScriptManager`. This avoids corrupting the running session.

## Architecture

All new code lives in a `cachehealth` subpackage:
`src/main/kotlin/com/qoliber/phpstorm/indexblocker/cachehealth/`.

### `CacheLocations` (object)

Resolves all cache paths via `PathManager` — never hardcoded `~/.cache/...`, so it works on any OS/IDE.

- `systemDir: Path` = `PathManager.getSystemDir()`
- `indexDir: Path` = `systemDir/"index"`
- `cachesDir: Path` = `systemDir/"caches"`
- `logDir: Path` = `PathManager.getLogDir()`
- `jcefCacheDir: Path` = `systemDir/"jcef_cache"`
- `fullLineDir: Path` = `systemDir/"full-line"`
- `indexReportDirs(): List<Path>` — dirs counted toward the reported "index" size (`indexDir`, `cachesDir`).
- `trimTargets(settings): List<TrimTarget>` — the dirs eligible for trimming given settings, each tagged `LIVE` (safe to delete now) or `ON_RESTART` (must be scheduled).

### `CacheSizeService` (`@Service(Service.Level.APP)`)

- Computes directory sizes on a background executor (`AppExecutorUtil.getAppExecutorService()`), never on the EDT.
- Caches the last `CacheSizes(indexBytes, totalBytes, perDir: Map<String, Long>, computedAt: Instant)`.
- `refreshAsync()` recomputes and notifies listeners; `lastResult()` returns the cached snapshot (possibly null before first compute).
- `addListener(Runnable)` so the toolbar widget can repaint when new sizes arrive.
- Size walk uses `Files.walk` summing `Files.size`, catching and logging `IOException` per-entry (skips unreadable files, never throws to caller).

### `SizeFormat` (object)

- `humanReadable(bytes: Long): String` → `"6.1 GB"`, `"812 MB"`, `"0 B"`. Pure, unit-tested. Binary units (1024).

### `CacheHealthToolbarWidget`

- An `AnAction` implementing `CustomComponentAction`. Its `createCustomComponent` returns a clickable label showing `"Index ${humanReadable(indexBytes)}"` (or a placeholder until first compute).
- Registered in plugin.xml into the 2025.3 main-toolbar group (exact group id pinned during planning).
- An `Alarm`/timer (e.g. 60 s) calls `CacheSizeService.refreshAsync()` while the component is showing; the service listener repaints the label.
- `actionPerformed` opens a `JBPopup` listing: the per-dir breakdown (read-only) and action buttons → Refresh, Trim caches, Invalidate & Restart, Open Cache Health settings.

### `TrimCachesAction` (`AnAction`)

- Builds the trim target list from `CacheHealthSettings`.
- Shows a confirmation dialog summarizing estimated bytes freed now (LIVE targets) vs after restart (ON_RESTART targets).
- On confirm: for LIVE targets, delete eligible files immediately (`NioFiles.deleteRecursively`, skipping locked/in-use files, logging failures). For ON_RESTART targets, enqueue deletion via `StartupActionScriptManager.addActionCommands(listOf(DeleteCommand(path)))`.
- Refreshes `CacheSizeService` afterward.

### `InvalidateAndRestartAction` (`AnAction`)

- Looks up the platform action via `ActionManager.getInstance().getAction("InvalidateCaches")` and delegates to it (native dialog + restart). If the action id is unavailable in the SDK, report at build time; do not reimplement cache invalidation by hand.

### `CacheSizeMonitor`

- An application-level startup activity (e.g. `AppLifecycleListener.appStarted` or an `ApplicationActivity` equivalent for 2025.3; exact hook pinned in planning).
- Schedules a periodic check (every ~30 min) on the app scheduled executor; also runs once shortly after startup.
- `shouldWarn(indexBytes, thresholdBytes, alreadyWarnedThisSession): Boolean` — pure, unit-tested.
- When it should warn: post a `Notification` in the `Cache Health` `notificationGroup` with actions Trim caches / Invalidate & Restart / Don't warn again (the last sets `monitorEnabled=false`). Warns at most once per IDE session.

### `SharedIndexDisabler`

- Application startup activity. When `CacheHealthSettings.enforceDisableSharedIndexes` is true (default), forces downloadable shared indexes off.
- Primary mechanism: set the platform registry flag governing shared-index downloads to `false` (exact key pinned during planning against the 2025.3 SDK).
- Fallback (only if the registry flag proves insufficient): ensure the bundled shared-index plugins are disabled via `DisabledPluginsState`, prompting a one-time restart. The plan will determine which mechanism the SDK actually honors and implement exactly one.
- Re-applied on every startup so the setting stays enforced even if something re-enables it.

### `CacheHealthSettings` (`@Service(Service.Level.APP)`, `PersistentStateComponent`)

State, stored in `<config>/options/cacheHealth.xml`:
- `enforceDisableSharedIndexes: Boolean = true`
- `monitorEnabled: Boolean = true`
- `thresholdGb: Int = 8`
- `trimLog: Boolean = true`
- `trimJcef: Boolean = true`
- `trimFullLine: Boolean = false`

### `CacheHealthConfigurable` (`Configurable`)

- New `applicationConfigurable` "Cache Health" under the `tools` group.
- Controls: enforce-disable-shared-indexes checkbox, monitor enabled checkbox, threshold (GB) spinner, three trim-target checkboxes. Standard `panel { }` Kotlin UI DSL.
- `apply()` writes settings; if `enforceDisableSharedIndexes` was just toggled on, invoke `SharedIndexDisabler` immediately.

## plugin.xml additions

- `<action>` registering `CacheHealthToolbarWidget` into the main-toolbar group.
- `<applicationConfigurable groupId="tools" id="...cacheHealth" displayName="Cache Health" instance="...CacheHealthConfigurable"/>`
- `<notificationGroup id="Cache Health" displayType="BALLOON"/>`
- The startup-activity registration for `CacheSizeMonitor` + `SharedIndexDisabler` (the appropriate listener/activity extension for 2025.3).
- `CacheSizeService` and `CacheHealthSettings` auto-register via `@Service`.

## Error handling

- All size walks and deletions catch `IOException`/`Throwable`, log via `thisLogger().warn`, and never propagate into platform call paths.
- Trim never deletes anything outside the resolved `CacheLocations` trim targets (defense against path mistakes): assert each target is under `PathManager.getSystemDir()`/`getLogDir()` before deleting.
- If `PathManager` dirs don't exist (fresh install), sizes report 0 and trim is a no-op.

## Testing

**Unit (pure, no IDE fixture):**
- `SizeFormat.humanReadable`: 0, bytes, KB/MB/GB boundaries, rounding to one decimal.
- `CacheSizeMonitor.shouldWarn`: below threshold → false; above + not warned → true; above + already warned → false; monitor disabled → false.
- Trim-target selection: each settings combination produces the expected target list with correct LIVE/ON_RESTART tags (no real deletion).

**Light integration (`BasePlatformTestCase` or temp-dir based):**
- `CacheSizeService` over a temp directory tree with files of known sizes → asserts summed bytes; unreadable entry is skipped without throwing.
- `CacheLocations` returns paths under the test `PathManager` system/log dirs.

**Manual smoke (sandbox `runIde`, documented in the plan):**
- Toolbar widget shows a size and repaints; popup actions work.
- Trim dialog reports now/after-restart and actually frees space.
- Invalidate & Restart triggers the native flow.
- Threshold notification fires when forced below a tiny threshold.
- After enabling enforce-disable and restarting, downloadable Shared Indexes are off.

## File layout (additions)

```
src/main/kotlin/com/qoliber/phpstorm/indexblocker/cachehealth/
├── CacheLocations.kt
├── CacheSizeService.kt
├── SizeFormat.kt
├── CacheHealthToolbarWidget.kt
├── TrimCachesAction.kt
├── InvalidateAndRestartAction.kt
├── CacheSizeMonitor.kt
├── SharedIndexDisabler.kt
├── CacheHealthSettings.kt
└── CacheHealthConfigurable.kt
src/test/kotlin/com/qoliber/phpstorm/indexblocker/cachehealth/
├── SizeFormatTest.kt
├── CacheSizeMonitorTest.kt
├── TrimTargetSelectionTest.kt
└── CacheSizeServiceTest.kt
```

## Items to pin during planning (require SDK verification)

1. The 2025.3 **main-toolbar action-group id** for placing a custom widget.
2. The **shared-index download registry key** (or confirmation that disabling the bundled plugin is required instead).
3. The correct **application startup-activity hook** in 2025.3 (`AppLifecycleListener` vs `ApplicationActivity`).
4. `StartupActionScriptManager` **DeleteCommand** API shape for scheduled deletion.
5. `InvalidateCaches` **action id** availability.
