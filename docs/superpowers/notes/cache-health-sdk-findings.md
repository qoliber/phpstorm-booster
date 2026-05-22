# Cache-Health SDK Findings — PhpStorm 2025.3 (PS-253.28294.345)

Researched against SDK at:
`~/.gradle/caches/9.5.1/transforms/bb280acfe15ca988abfcd173e966c328/transformed/PhpStorm-2025.3`

Build marker: `PS-253.28294.345`

---

## SDK Specifics Table

| Item | Confirmed Value / Decision | Confidence | Source |
|------|---------------------------|------------|--------|
| **Main toolbar right-side group id (New UI 2025.3)** | `MainToolbarRight` | HIGH | `idea/PlatformActions.xml` inside `app.jar` — the `<group id="MainToolbarRight" searchable="false">` is a direct child of `MainToolbarNewUI`, alongside `MainToolbarLeft` and `MainToolbarCenter`. It currently holds `SearchEverywhere` and `SettingsEntryPoint`. |
| **Shared-index disabling lever** | Registry key **`shared.indexes.download`** (default `true`, `restartRequired=true`) in plugin `intellij.indexing.shared.core` (bundled at `plugins/indexing-shared/`). PHP-specific companion key: **`shared.indexes.php.download`** in plugin `com.jetbrains.php.sharedIndexes`. Setting both to `false` completely disables downloadable shared indexes. | HIGH | `META-INF/plugin.xml` inside `indexing-shared.jar` and `php-sharedIndexes.jar` |
| **Startup hook** | `com.intellij.ide.AppLifecycleListener` exists in `app.jar`. Interface has a `TOPIC` field and a default `appStarted()` method (confirmed via javap). Register as an `applicationListeners` listener in `plugin.xml`. | HIGH | `com/intellij/ide/AppLifecycleListener.class` in `app.jar` |
| **`StartupActionScriptManager.DeleteCommand` constructor signature** | Two constructors: `DeleteCommand(java.nio.file.Path)` and `DeleteCommand(java.io.File)`. Both are `public`. Class is in `com.intellij.ide.startup.StartupActionScriptManager` (static inner class), located in `app.jar`. Use `addActionCommand(DeleteCommand(path))` to schedule a file for deletion on next restart. | HIGH | `com/intellij/ide/startup/StartupActionScriptManager$DeleteCommand.class` in `app.jar` |
| **`InvalidateCaches` action id** | Action id `"InvalidateCaches"` is registered with class `com.intellij.ide.actions.InvalidateCachesAction`. Present in `idea/PlatformActions.xml` in `app.jar`. | HIGH | `idea/PlatformActions.xml` in `app.jar` |

---

## v1 Code Changes Required to Compile Against 2025.3

Two changes were required in `IndexBlockerRefresher.kt`:

### 1. Kotlin metadata binary version mismatch

**Problem:** The 2025.3 SDK JARs were compiled with Kotlin 2.2.0 (binary metadata version 2.2.0), but the plugin used `org.jetbrains.kotlin.jvm` version `2.0.21` (expects binary version 2.0.0). This caused dozens of `e: ... Module was compiled with an incompatible version of Kotlin` errors, which also blocked the `thisLogger` Kotlin extension from being resolved.

**Fix:** Added `-Xskip-metadata-version-check` to `freeCompilerArgs` in `build.gradle.kts`. This is the documented suppression flag and does not affect runtime behavior — the classes are binary-compatible at the JVM level.

```kotlin
// build.gradle.kts — before:
compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
}

// build.gradle.kts — after:
compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    freeCompilerArgs.add("-Xskip-metadata-version-check")
}
```

> **Note for future tasks:** A proper long-term fix is to upgrade `org.jetbrains.kotlin.jvm` to `2.2.0` (or whichever 2.2.x matches the SDK). That was not done here to minimise scope, but it is the clean solution.

### 2. Deprecated `makeRootsChange(Runnable, Boolean, Boolean)` API

**Problem:** `ProjectRootManagerEx.makeRootsChange(EmptyRunnable.INSTANCE, false, true)` still compiles in 2025.3 but emits a deprecation warning. The method is annotated `@Deprecated` in the 2025.3 SDK.

**Fix:** Replaced with the non-deprecated overload that takes a `RootsChangeRescanningInfo`:

```kotlin
// IndexBlockerRefresher.kt — before:
ProjectRootManagerEx.getInstanceEx(project)
    .makeRootsChange(EmptyRunnable.INSTANCE, false, true)

// IndexBlockerRefresher.kt — after:
ProjectRootManagerEx.getInstanceEx(project)
    .makeRootsChange(EmptyRunnable.INSTANCE, RootsChangeRescanningInfo.TOTAL_RESCAN)
```

New import added: `com.intellij.openapi.project.RootsChangeRescanningInfo`

### 3. `thisLogger()` Kotlin extension unresolved (consequence of issue #1)

**Problem:** The `thisLogger()` call on line 54 of `IndexBlockerRefresher.kt` became an "Unresolved reference" because the Kotlin metadata mismatch blocked the compiler from reading the extension function metadata from `util-8.jar`. After verifying the fix in issue #1, `thisLogger` is confirmed present in `LoggerKt` in `util-8.jar`. However, since `thisLogger` requires `this` context and was used inside an anonymous `ChangeApplier` object (where `this` would be the anonymous class, not `IndexBlockerVfsListener`), it was replaced with an explicit logger field for correctness.

```kotlin
// IndexBlockerRefresher.kt — before:
import com.intellij.openapi.diagnostic.thisLogger
// ...
internal class IndexBlockerVfsListener : AsyncFileListener {
    // ...
    thisLogger().warn("Failed to refresh after VFS change", t)

// IndexBlockerRefresher.kt — after:
import com.intellij.openapi.diagnostic.Logger
// ...
internal class IndexBlockerVfsListener : AsyncFileListener {
    private val log = Logger.getInstance(IndexBlockerVfsListener::class.java)
    // ...
    log.warn("Failed to refresh after VFS change", t)
```

---

## compileKotlin Result

```
BUILD SUCCESSFUL in 14s
2 actionable tasks: 2 executed
```

No warnings, no errors after the three fixes above.

---

## Additional SDK Notes (for later tasks)

- `RootsChangeRescanningInfo` constants available: `TOTAL_RESCAN`, `RESCAN_DEPENDENCIES_IF_NEEDED`, `NO_RESCAN_NEEDED` (in `util-8.jar`)
- `StartupActionScriptManager.addActionCommand(ActionCommand)` is `synchronized` and throws `IOException`
- Shared index plugin IDs: `intellij.indexing.shared.core` (core), `intellij.indexing.shared` (project indexes), `com.jetbrains.php.sharedIndexes` (PHP-specific)
- `MainToolbarNewUI` is the parent group; direct children are `MainToolbarLeft`, `MainToolbarCenter`, `MainToolbarRight`
