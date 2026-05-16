# PhpStorm Index Blocker — Design

**Date:** 2026-05-16
**Status:** Approved (brainstorming phase)
**Author:** jwinkler@qoliber.com

## Purpose

A PhpStorm plugin that excludes a user-configurable list of folders from indexing across **all** projects on the IDE. The block list is global (IDE-wide), not per-project. Matched folders appear with the standard "excluded" mark in the Project view and are skipped during indexing, search, and navigation.

Motivating use case: large generated/build directories (`var`, `generated`, `pub/static`, `node_modules`, `dev`, `Test*` namespaces) slow PhpStorm down on every project. Marking each one excluded by hand in every project is tedious; this plugin does it once, globally.

## Scope

**In scope (v1):**
- Global, IDE-wide block list with a simple matching grammar.
- Settings UI under Settings → Tools → Index Blocker.
- Folder exclusion via the standard `DirectoryIndexExcludePolicy` extension point.
- Live refresh when settings change or matching folders appear/disappear in the VFS.
- Target: PhpStorm builds 233 (2023.3) through 243.* (2024.3).

**Out of scope (v1):**
- Per-project overrides.
- Leading `*` wildcards, regex, or full glob support.
- JetBrains Marketplace submission workflow (manual "Install from Disk" only).
- UI tests for the settings panel.

## Match grammar

Each block-list entry is one of three forms; the form is auto-detected from the entry text. No UI mode switch.

| Form              | Example       | Meaning                                                                |
| ----------------- | ------------- | ---------------------------------------------------------------------- |
| Exact name        | `var`         | Match any folder named exactly `var`, at any depth under a content root. |
| Prefix wildcard   | `Test*`       | Match any folder whose name starts with `Test`, at any depth. Only trailing `*` supported. |
| Relative path     | `pub/static`  | Match the folder at that path relative to a content root.              |

Detection rule: if the entry contains `/`, treat as relative path; else if it ends with `*`, treat as prefix; else treat as exact name. Entries are trimmed and deduped on apply; empty entries rejected.

**Default block list (preseeded on first install):**
```
node_modules
var
generated
dev
pub/static
Test*
```

## Architecture

Four Kotlin classes, all wired through `plugin.xml` extension points.

### `IndexBlockerSettings` (application service)

`@Service(Service.Level.APP)` implementing `PersistentStateComponent<State>`. Holds the global block list. Persisted to `<config>/options/indexBlocker.xml`.

```kotlin
@State(name = "IndexBlocker", storages = [Storage("indexBlocker.xml")])
class IndexBlockerSettings : PersistentStateComponent<IndexBlockerSettings.State> {
    class State {
        var blockedEntries: MutableList<String> = mutableListOf(/* defaults */)
        var enabled: Boolean = true
    }
    // getState / loadState / instance accessor
}
```

### `IndexBlockerConfigurable` (settings UI)

Implements `Configurable`, registered as `applicationConfigurable` under the `tools` group with id `tools.indexBlocker`. Renders:
- An **Enabled** checkbox (master switch).
- A `JBList` of entries edited via `ToolbarDecorator` (Add / Remove / Edit buttons — standard IntelliJ UX).
- A one-line help label: *"Folder names listed here will be excluded from indexing in every project."*

`apply()` writes to `IndexBlockerSettings`, then delegates to `IndexBlockerRefresher` to propagate the change.

### `IndexBlockerExcludePolicy` (per project)

Implements `DirectoryIndexExcludePolicy`. Registered as a project-scoped extension. The platform calls `getExcludeUrlsForProject()` (and/or `getExcludeRootsForProject()` depending on platform version) and we return the URLs of all directories under each module's content roots that match the active block list. Walks each content root with a BFS, depth-unlimited, skipping any subtree once it's already been added (no redundant descent into matched dirs). Result cached per-module; cache invalidated on settings change or on relevant VFS events.

If `enabled` is false, return empty list.

### `IndexBlockerRefresher` (helper)

Two responsibilities:
1. **On settings change:** for each open `Project`, call `ProjectRootManagerEx.getInstanceEx(project).makeRootsChange(EmptyRunnable.INSTANCE, false, true)` inside a write action on EDT. Triggers re-query of the policy and a smart-mode re-index.
2. **On VFS changes:** an `AsyncFileListener` registered at app scope. For folder create/delete events whose name matches any active entry, fire the same roots-change on the affected project only. Filtered tightly to avoid noise.

## Data flow

```
User edits settings → IndexBlockerConfigurable.apply()
  → IndexBlockerSettings.loadState (persists)
  → IndexBlockerRefresher.refreshAll()
    → for each open project: ProjectRootManagerEx.makeRootsChange
      → platform re-queries IndexBlockerExcludePolicy
        → returns matched folder URLs
          → folders shown excluded; re-index runs
```

## Build & distribution

- **Language:** Kotlin (JVM 17 target).
- **Build:** Gradle Kotlin DSL with the **IntelliJ Platform Gradle Plugin 2.x** (`org.jetbrains.intellij.platform`).
- **Target product:** PhpStorm.
- **Compatibility range:** `sinceBuild = 233`, `untilBuild = 243.*` (PhpStorm 2023.3 → 2024.3).
- **Plugin dependencies:** `com.intellij.modules.platform` only. No PhpStorm-specific APIs used, but `plugin.xml` declares PhpStorm as the product so it surfaces correctly in the marketplace later.
- **Distribution v1:** local zip via *Install Plugin from Disk*. Marketplace submission deferred.

## Testing

- **Unit tests** for a pure `PatternMatcher` class that implements the match grammar:
  - exact name matches at depth 0, 1, N
  - prefix `Test*` matches `Test`, `Tests`, `TestUtils`; does not match `MyTest`
  - relative path `pub/static` matches only that path, not nested
  - whitespace trimmed; empty entries rejected; duplicates ignored
- **Light integration test** using `BasePlatformTestCase`:
  - Build a fixture project with directories matching each entry form.
  - Seed `IndexBlockerSettings` with one entry of each kind.
  - Assert `IndexBlockerExcludePolicy` returns the expected set of URLs.
- **No UI tests.** Settings panel verified manually.

## Error handling

- Invalid entries (empty after trim) are dropped silently on apply with no error dialog — the UI prevents adding them in the first place.
- VFS listener exceptions are logged via `thisLogger()` and swallowed; never bubble into the platform.
- Settings load failure (corrupt XML) falls back to defaults; logged at WARN.

## File layout

```
phpstorm-index-blocker/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── src/main/
│   ├── kotlin/com/qoliber/phpstorm/indexblocker/
│   │   ├── IndexBlockerSettings.kt
│   │   ├── IndexBlockerConfigurable.kt
│   │   ├── IndexBlockerExcludePolicy.kt
│   │   ├── IndexBlockerRefresher.kt
│   │   └── PatternMatcher.kt
│   └── resources/META-INF/
│       └── plugin.xml
└── src/test/kotlin/com/qoliber/phpstorm/indexblocker/
    ├── PatternMatcherTest.kt
    └── IndexBlockerExcludePolicyTest.kt
```

## Open questions

None at this stage. All decisions captured above.
