# Cache Health Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Cache Health feature area to the plugin: a main-toolbar cache-size widget, quick cache trim, one-click invalidate-and-restart, a size-threshold monitor, and enforced disabling of downloadable Shared Indexes.

**Architecture:** Ten Kotlin classes in a new `cachehealth` subpackage. A `CacheSizeService` (app) computes cache-dir sizes off-EDT using paths resolved by `CacheLocations` (via `PathManager`). A toolbar widget displays the size and opens a popup of actions. Trim/invalidate are actions; a monitor posts threshold notifications; a startup activity enforces shared-index disabling. Settings persist via `CacheHealthSettings`.

**Tech Stack:** Kotlin (JVM 17, JBR 21 toolchain), Gradle 9.5.1, IntelliJ Platform Gradle Plugin 2.x, PhpStorm 2025.3 SDK. JUnit 4 + `BasePlatformTestCase`.

**Reference spec:** `docs/superpowers/specs/2026-05-22-cache-health-design.md`

**Important — version-sensitive APIs:** Task 1 retargets the build to PhpStorm 2025.3 and resolves five SDK specifics (toolbar group id, shared-index lever, startup hook, scheduled-delete API, invalidate action id), recording them in `docs/superpowers/notes/cache-health-sdk-findings.md`. Later tasks reference those findings; where this plan gives a concrete best-known value (e.g. `MainToolbarRight`), the implementer must confirm it against the findings and adjust if the SDK differs.

---

## File Structure

| Path | Responsibility |
| --- | --- |
| `gradle.properties` (modify) | Bump `platformVersion=2025.3`, `pluginSinceBuild=253`. |
| `docs/superpowers/notes/cache-health-sdk-findings.md` (create) | Recorded answers to the five SDK unknowns. |
| `cachehealth/SizeFormat.kt` | Pure bytes→human-readable formatting. |
| `cachehealth/CacheLocations.kt` | Resolve cache paths via `PathManager`; list report dirs + trim targets. |
| `cachehealth/CacheSizeService.kt` | App service: off-EDT size computation, caching, listeners. |
| `cachehealth/CacheHealthSettings.kt` | App `PersistentStateComponent` for toggles/threshold. |
| `cachehealth/CacheSizeMonitor.kt` | Pure `shouldWarn` + startup/periodic check + notification. |
| `cachehealth/TrimCachesAction.kt` | Pure trim-target selection + the trim action. |
| `cachehealth/InvalidateAndRestartAction.kt` | Delegates to native `InvalidateCaches` action. |
| `cachehealth/SharedIndexDisabler.kt` | Startup activity enforcing shared-index disabling. |
| `cachehealth/CacheHealthToolbarWidget.kt` | Main-toolbar custom-component action + popup. |
| `cachehealth/CacheHealthConfigurable.kt` | Settings page under Tools. |
| `META-INF/plugin.xml` (modify) | Register widget action, configurable, notification group, startup activities. |
| `src/test/.../cachehealth/*.kt` | Unit + light integration tests. |

---

## Task 1: Retarget to 2025.3 and pin SDK specifics

**Files:**
- Modify: `gradle.properties`
- Create: `docs/superpowers/notes/cache-health-sdk-findings.md`

- [ ] **Step 1: Bump the platform version and since-build**

Edit `gradle.properties`: set `platformVersion=2025.3` and `pluginSinceBuild=253` (leave `pluginUntilBuild=253.*`).

- [ ] **Step 2: Resolve the 2025.3 SDK and confirm it builds**

Run:
```bash
./gradlew --no-daemon clean compileKotlin --console=plain
```
Expected: BUILD SUCCESSFUL (first run downloads the 2025.3 SDK; allow several minutes). If existing v1 code fails to compile against 2025.3 (e.g. `DirectoryIndexExcludePolicy` moved again), record the corrected import in the findings file and fix it minimally.

- [ ] **Step 3: Find the main-toolbar action group id**

Locate the SDK jars and search for candidate group ids:
```bash
SDK=$(find ~/.gradle/caches -type d -name "*PhpStorm*2025.3*" 2>/dev/null | head -1)
echo "SDK: $SDK"
grep -rl "MainToolbarRight\|RightToolbarSideGroup\|MainToolbarNewUI" "$SDK"/lib/*.jar 2>/dev/null | head
```
The conventional right-side group is `MainToolbarRight`. Confirm it exists in this SDK; record the exact id used to place a custom widget.

- [ ] **Step 4: Determine the shared-index disabling lever**

Search for the registry key and the bundled plugin ids:
```bash
# registry keys mentioning shared indexes
unzip -p "$SDK"/lib/app.jar misc/registry.properties 2>/dev/null | grep -i "shared.*index" || true
# bundled shared-index plugin ids
find "$SDK" -path "*indexing-shared*" -name plugin.xml 2>/dev/null -exec grep -h "<id>" {} \;
```
Record either: (a) the registry key that gates downloadable shared indexes (preferred — no restart), or (b) the bundled plugin id(s) to disable (fallback — needs restart). Task 9 implements whichever is recorded as authoritative.

- [ ] **Step 5: Confirm startup hook, scheduled-delete API, invalidate action id**

```bash
# startup activity / app lifecycle
grep -rl "ApplicationActivity\|AppLifecycleListener" "$SDK"/lib/*.jar 2>/dev/null | head
# scheduled deletion
grep -rl "StartupActionScriptManager" "$SDK"/lib/*.jar 2>/dev/null | head
# invalidate caches action id
unzip -p "$SDK"/lib/app.jar META-INF/IdeMainActions.xml 2>/dev/null | grep -i "InvalidateCaches" || \
  grep -rl "InvalidateCaches" "$SDK"/lib/*.jar 2>/dev/null | head
```
Record: the app startup hook to use (prefer `AppLifecycleListener.appStarted`), confirmation that `com.intellij.ide.startup.StartupActionScriptManager` with a `DeleteCommand` exists, and that the action id `InvalidateCaches` is registered.

- [ ] **Step 6: Write the findings file**

Create `docs/superpowers/notes/cache-health-sdk-findings.md` with a short table: each of the five items and the confirmed value/decision. This is the source of truth for later tasks.

- [ ] **Step 7: Commit**

```bash
git add gradle.properties docs/superpowers/notes/cache-health-sdk-findings.md src/main/kotlin
git commit -m "chore: retarget to PhpStorm 2025.3 and pin cache-health SDK specifics"
```

---

## Task 2: `SizeFormat` (TDD)

**Files:**
- Create: `src/main/kotlin/com/qoliber/phpstorm/indexblocker/cachehealth/SizeFormat.kt`
- Create: `src/test/kotlin/com/qoliber/phpstorm/indexblocker/cachehealth/SizeFormatTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.qoliber.phpstorm.indexblocker.cachehealth

import org.junit.Assert.assertEquals
import org.junit.Test

class SizeFormatTest {
    @Test fun `zero bytes`() { assertEquals("0 B", SizeFormat.humanReadable(0)) }
    @Test fun `bytes`() { assertEquals("512 B", SizeFormat.humanReadable(512)) }
    @Test fun `kilobytes rounded`() { assertEquals("1.5 KB", SizeFormat.humanReadable(1536)) }
    @Test fun `megabytes`() { assertEquals("2.0 MB", SizeFormat.humanReadable(2L * 1024 * 1024)) }
    @Test fun `gigabytes one decimal`() {
        assertEquals("6.1 GB", SizeFormat.humanReadable((6.1 * 1024 * 1024 * 1024).toLong()))
    }
    @Test fun `negative treated as zero`() { assertEquals("0 B", SizeFormat.humanReadable(-5)) }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests SizeFormatTest --console=plain`
Expected: compilation failure — `SizeFormat` undefined.

- [ ] **Step 3: Implement**

```kotlin
package com.qoliber.phpstorm.indexblocker.cachehealth

object SizeFormat {
    private val units = arrayOf("B", "KB", "MB", "GB", "TB")

    fun humanReadable(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        var value = bytes.toDouble()
        var unit = 0
        while (value >= 1024 && unit < units.size - 1) {
            value /= 1024
            unit++
        }
        return if (unit == 0) "${bytes} B" else String.format("%.1f %s", value, units[unit])
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew test --tests SizeFormatTest --console=plain`
Expected: PASS — 6 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/qoliber/phpstorm/indexblocker/cachehealth/SizeFormat.kt \
        src/test/kotlin/com/qoliber/phpstorm/indexblocker/cachehealth/SizeFormatTest.kt
git commit -m "feat: add SizeFormat human-readable byte formatting"
```

---

## Task 3: `CacheLocations`

**Files:**
- Create: `src/main/kotlin/com/qoliber/phpstorm/indexblocker/cachehealth/CacheLocations.kt`
- Create: `src/test/kotlin/com/qoliber/phpstorm/indexblocker/cachehealth/CacheLocationsTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.qoliber.phpstorm.indexblocker.cachehealth

import com.intellij.openapi.application.PathManager
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Path

class CacheLocationsTest {
    @Test fun `index report dirs are under system dir`() {
        val sys = PathManager.getSystemDir()
        for (p in CacheLocations.indexReportDirs()) {
            assertTrue("$p not under $sys", p.startsWith(sys))
        }
    }

    @Test fun `trim targets respect settings`() {
        val all = CacheLocations.trimTargets(trimLog = true, trimJcef = true, trimFullLine = true)
        val names = all.map { it.path.fileName.toString() }.toSet()
        assertTrue(names.contains("jcef_cache"))
        assertTrue(names.contains("full-line"))

        val none = CacheLocations.trimTargets(trimLog = false, trimJcef = false, trimFullLine = false)
        assertTrue(none.isEmpty())
    }

    @Test fun `jcef is scheduled on restart, log is live`() {
        val targets = CacheLocations.trimTargets(trimLog = true, trimJcef = true, trimFullLine = false)
        val jcef = targets.single { it.path.fileName.toString() == "jcef_cache" }
        assertTrue(jcef.timing == TrimTiming.ON_RESTART)
        val log: Path = CacheLocations.logDir()
        assertTrue(targets.any { it.path == log && it.timing == TrimTiming.LIVE })
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests CacheLocationsTest --console=plain`
Expected: compilation failure — `CacheLocations`, `TrimTiming`, `TrimTarget` undefined.

- [ ] **Step 3: Implement**

```kotlin
package com.qoliber.phpstorm.indexblocker.cachehealth

import com.intellij.openapi.application.PathManager
import java.nio.file.Path

enum class TrimTiming { LIVE, ON_RESTART }

data class TrimTarget(val path: Path, val timing: TrimTiming)

object CacheLocations {
    fun systemDir(): Path = PathManager.getSystemDir()
    fun indexDir(): Path = systemDir().resolve("index")
    fun cachesDir(): Path = systemDir().resolve("caches")
    fun logDir(): Path = PathManager.getLogDir()
    fun jcefCacheDir(): Path = systemDir().resolve("jcef_cache")
    fun fullLineDir(): Path = systemDir().resolve("full-line")

    /** Dirs counted toward the reported index size. */
    fun indexReportDirs(): List<Path> = listOf(indexDir(), cachesDir())

    fun trimTargets(trimLog: Boolean, trimJcef: Boolean, trimFullLine: Boolean): List<TrimTarget> {
        val out = mutableListOf<TrimTarget>()
        if (trimLog) out.add(TrimTarget(logDir(), TrimTiming.LIVE))
        if (trimJcef) out.add(TrimTarget(jcefCacheDir(), TrimTiming.ON_RESTART))
        if (trimFullLine) out.add(TrimTarget(fullLineDir(), TrimTiming.ON_RESTART))
        return out
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew test --tests CacheLocationsTest --console=plain`
Expected: PASS — 3 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/qoliber/phpstorm/indexblocker/cachehealth/CacheLocations.kt \
        src/test/kotlin/com/qoliber/phpstorm/indexblocker/cachehealth/CacheLocationsTest.kt
git commit -m "feat: add CacheLocations path resolution and trim targets"
```

---

## Task 4: `CacheSizeService` (TDD)

**Files:**
- Create: `src/main/kotlin/com/qoliber/phpstorm/indexblocker/cachehealth/CacheSizeService.kt`
- Create: `src/test/kotlin/com/qoliber/phpstorm/indexblocker/cachehealth/CacheSizeServiceTest.kt`

The service exposes a pure helper `dirSize(Path)` (testable directly over a temp tree) plus app-service caching/listeners.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.qoliber.phpstorm.indexblocker.cachehealth

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

class CacheSizeServiceTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun `sums file sizes recursively`() {
        val root = tmp.newFolder("root").toPath()
        Files.write(root.resolve("a.bin"), ByteArray(100))
        val sub = Files.createDirectory(root.resolve("sub"))
        Files.write(sub.resolve("b.bin"), ByteArray(250))
        assertEquals(350L, CacheSizeService.dirSize(root))
    }

    @Test fun `missing dir is zero`() {
        val missing = tmp.root.toPath().resolve("nope")
        assertEquals(0L, CacheSizeService.dirSize(missing))
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests CacheSizeServiceTest --console=plain`
Expected: compilation failure — `CacheSizeService.dirSize` undefined.

- [ ] **Step 3: Implement**

```kotlin
package com.qoliber.phpstorm.indexblocker.cachehealth

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.concurrency.AppExecutorUtil
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

data class CacheSizes(
    val indexBytes: Long,
    val totalBytes: Long,
    val perDir: Map<String, Long>,
    val computedAt: Instant,
)

@Service(Service.Level.APP)
class CacheSizeService {

    @Volatile private var last: CacheSizes? = null
    private val listeners = CopyOnWriteArrayList<Runnable>()

    fun lastResult(): CacheSizes? = last

    fun addListener(l: Runnable) { listeners.add(l) }

    fun refreshAsync() {
        AppExecutorUtil.getAppExecutorService().execute {
            try {
                val perDir = linkedMapOf(
                    "index" to dirSize(CacheLocations.indexDir()),
                    "caches" to dirSize(CacheLocations.cachesDir()),
                    "log" to dirSize(CacheLocations.logDir()),
                    "jcef_cache" to dirSize(CacheLocations.jcefCacheDir()),
                    "full-line" to dirSize(CacheLocations.fullLineDir()),
                )
                val index = CacheLocations.indexReportDirs()
                    .sumOf { dirSize(it) }
                val total = perDir.values.sum()
                last = CacheSizes(index, total, perDir, Instant.now())
                listeners.forEach { it.run() }
            } catch (t: Throwable) {
                thisLogger().warn("Cache size computation failed", t)
            }
        }
    }

    companion object {
        @JvmStatic
        fun getInstance(): CacheSizeService =
            ApplicationManager.getApplication().getService(CacheSizeService::class.java)

        fun dirSize(dir: Path): Long {
            if (!Files.exists(dir)) return 0L
            var total = 0L
            try {
                Files.walk(dir).use { stream ->
                    stream.forEach { p ->
                        try {
                            if (Files.isRegularFile(p)) total += Files.size(p)
                        } catch (_: Exception) { /* skip unreadable entry */ }
                    }
                }
            } catch (t: Throwable) {
                thisLogger().warn("Failed walking $dir", t)
            }
            return total
        }
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew test --tests CacheSizeServiceTest --console=plain`
Expected: PASS — 2 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/qoliber/phpstorm/indexblocker/cachehealth/CacheSizeService.kt \
        src/test/kotlin/com/qoliber/phpstorm/indexblocker/cachehealth/CacheSizeServiceTest.kt
git commit -m "feat: add CacheSizeService with off-EDT size computation"
```

---

## Task 5: `CacheHealthSettings`

**Files:**
- Create: `src/main/kotlin/com/qoliber/phpstorm/indexblocker/cachehealth/CacheHealthSettings.kt`

- [ ] **Step 1: Implement**

```kotlin
package com.qoliber.phpstorm.indexblocker.cachehealth

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

@Service(Service.Level.APP)
@State(name = "CacheHealth", storages = [Storage("cacheHealth.xml")])
class CacheHealthSettings : PersistentStateComponent<CacheHealthSettings.State> {

    class State {
        @JvmField var enforceDisableSharedIndexes: Boolean = true
        @JvmField var monitorEnabled: Boolean = true
        @JvmField var thresholdGb: Int = 8
        @JvmField var trimLog: Boolean = true
        @JvmField var trimJcef: Boolean = true
        @JvmField var trimFullLine: Boolean = false
    }

    private var state = State()
    override fun getState(): State = state
    override fun loadState(loaded: State) { XmlSerializerUtil.copyBean(loaded, state) }

    var enforceDisableSharedIndexes: Boolean
        get() = state.enforceDisableSharedIndexes
        set(v) { state.enforceDisableSharedIndexes = v }
    var monitorEnabled: Boolean
        get() = state.monitorEnabled
        set(v) { state.monitorEnabled = v }
    var thresholdGb: Int
        get() = state.thresholdGb
        set(v) { state.thresholdGb = v }
    var trimLog: Boolean
        get() = state.trimLog
        set(v) { state.trimLog = v }
    var trimJcef: Boolean
        get() = state.trimJcef
        set(v) { state.trimJcef = v }
    var trimFullLine: Boolean
        get() = state.trimFullLine
        set(v) { state.trimFullLine = v }

    fun thresholdBytes(): Long = thresholdGb.toLong() * 1024 * 1024 * 1024

    companion object {
        @JvmStatic
        fun getInstance(): CacheHealthSettings =
            ApplicationManager.getApplication().getService(CacheHealthSettings::class.java)
    }
}
```

- [ ] **Step 2: Verify compile**

Run: `./gradlew compileKotlin --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/com/qoliber/phpstorm/indexblocker/cachehealth/CacheHealthSettings.kt
git commit -m "feat: add CacheHealthSettings persistent service"
```

---

## Task 6: `CacheSizeMonitor` (TDD for shouldWarn)

**Files:**
- Create: `src/main/kotlin/com/qoliber/phpstorm/indexblocker/cachehealth/CacheSizeMonitor.kt`
- Create: `src/test/kotlin/com/qoliber/phpstorm/indexblocker/cachehealth/CacheSizeMonitorTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.qoliber.phpstorm.indexblocker.cachehealth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CacheSizeMonitorTest {
    private val threshold = 8L * 1024 * 1024 * 1024

    @Test fun `below threshold does not warn`() {
        assertFalse(CacheSizeMonitor.shouldWarn(threshold - 1, threshold, monitorEnabled = true, alreadyWarned = false))
    }
    @Test fun `above threshold warns once`() {
        assertTrue(CacheSizeMonitor.shouldWarn(threshold + 1, threshold, monitorEnabled = true, alreadyWarned = false))
    }
    @Test fun `above threshold but already warned does not warn`() {
        assertFalse(CacheSizeMonitor.shouldWarn(threshold + 1, threshold, monitorEnabled = true, alreadyWarned = true))
    }
    @Test fun `disabled monitor never warns`() {
        assertFalse(CacheSizeMonitor.shouldWarn(threshold + 1, threshold, monitorEnabled = false, alreadyWarned = false))
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests CacheSizeMonitorTest --console=plain`
Expected: compilation failure — `CacheSizeMonitor.shouldWarn` undefined.

- [ ] **Step 3: Implement**

`CacheSizeMonitor` exposes the pure `shouldWarn` plus a `check()` that posts a notification. The startup/periodic scheduling is wired in Task 12 via the startup hook recorded in Task 1's findings.

```kotlin
package com.qoliber.phpstorm.indexblocker.cachehealth

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionManager
import java.util.concurrent.atomic.AtomicBoolean

object CacheSizeMonitor {

    private val warnedThisSession = AtomicBoolean(false)

    fun shouldWarn(indexBytes: Long, thresholdBytes: Long, monitorEnabled: Boolean, alreadyWarned: Boolean): Boolean =
        monitorEnabled && !alreadyWarned && indexBytes > thresholdBytes

    /** Recompute size and post a notification if warranted. Safe to call repeatedly. */
    fun check() {
        val settings = CacheHealthSettings.getInstance()
        val service = CacheSizeService.getInstance()
        service.addListener {
            val sizes = service.lastResult() ?: return@addListener
            if (shouldWarn(sizes.indexBytes, settings.thresholdBytes(), settings.monitorEnabled, warnedThisSession.get())) {
                warnedThisSession.set(true)
                notify(sizes.indexBytes)
            }
        }
        service.refreshAsync()
    }

    private fun notify(indexBytes: Long) {
        val group = NotificationGroupManager.getInstance().getNotificationGroup("Cache Health")
        val n = group.createNotification(
            "PhpStorm index cache is large",
            "Index storage is ${SizeFormat.humanReadable(indexBytes)}. Consider trimming or invalidating caches.",
            NotificationType.WARNING,
        )
        n.addAction(ActionManager.getInstance().getAction("com.qoliber.indexblocker.cachehealth.Trim"))
        n.addAction(ActionManager.getInstance().getAction("com.qoliber.indexblocker.cachehealth.Invalidate"))
        n.notify(null)
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew test --tests CacheSizeMonitorTest --console=plain`
Expected: PASS — 4 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/qoliber/phpstorm/indexblocker/cachehealth/CacheSizeMonitor.kt \
        src/test/kotlin/com/qoliber/phpstorm/indexblocker/cachehealth/CacheSizeMonitorTest.kt
git commit -m "feat: add CacheSizeMonitor threshold logic and notification"
```

---

## Task 7: `TrimCachesAction` (TDD for selection)

**Files:**
- Create: `src/main/kotlin/com/qoliber/phpstorm/indexblocker/cachehealth/TrimCachesAction.kt`
- Create: `src/test/kotlin/com/qoliber/phpstorm/indexblocker/cachehealth/TrimTargetSelectionTest.kt`

The selection logic lives in `CacheLocations.trimTargets` (Task 3, already tested). This task adds a separate pure split of targets by timing, plus the action.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.qoliber.phpstorm.indexblocker.cachehealth

import org.junit.Assert.assertEquals
import org.junit.Test

class TrimTargetSelectionTest {
    @Test fun `splits live and on-restart targets`() {
        val targets = CacheLocations.trimTargets(trimLog = true, trimJcef = true, trimFullLine = true)
        val (live, onRestart) = TrimCachesAction.partition(targets)
        assertEquals(1, live.size)            // log
        assertEquals(2, onRestart.size)       // jcef + full-line
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests TrimTargetSelectionTest --console=plain`
Expected: compilation failure — `TrimCachesAction.partition` undefined.

- [ ] **Step 3: Implement**

The deletion uses `StartupActionScriptManager` for ON_RESTART targets — confirm the `DeleteCommand` API against Task 1 findings before relying on it.

```kotlin
package com.qoliber.phpstorm.indexblocker.cachehealth

import com.intellij.ide.startup.StartupActionScriptManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.io.NioFiles
import java.nio.file.Files

class TrimCachesAction : AnAction("Trim Caches") {

    override fun actionPerformed(e: AnActionEvent) {
        val s = CacheHealthSettings.getInstance()
        val targets = CacheLocations.trimTargets(s.trimLog, s.trimJcef, s.trimFullLine)
        if (targets.isEmpty()) {
            Messages.showInfoMessage("No trim targets selected in Cache Health settings.", "Trim Caches")
            return
        }
        val (live, onRestart) = partition(targets)
        val liveBytes = live.sumOf { CacheSizeService.dirSize(it.path) }
        val restartBytes = onRestart.sumOf { CacheSizeService.dirSize(it.path) }
        val ok = Messages.showYesNoDialog(
            "Free now: ${SizeFormat.humanReadable(liveBytes)}\n" +
                "Free after restart: ${SizeFormat.humanReadable(restartBytes)}\n\nProceed?",
            "Trim Caches", Messages.getQuestionIcon(),
        ) == Messages.YES
        if (!ok) return

        for (t in live) {
            try {
                if (Files.exists(t.path)) NioFiles.deleteRecursively(t.path)
            } catch (ex: Throwable) {
                thisLogger().warn("Live trim failed for ${t.path}", ex)
            }
        }
        for (t in onRestart) {
            try {
                StartupActionScriptManager.addActionCommands(
                    listOf(StartupActionScriptManager.DeleteCommand(t.path.toFile()))
                )
            } catch (ex: Throwable) {
                thisLogger().warn("Scheduling restart-trim failed for ${t.path}", ex)
            }
        }
        CacheSizeService.getInstance().refreshAsync()
    }

    companion object {
        fun partition(targets: List<TrimTarget>): Pair<List<TrimTarget>, List<TrimTarget>> =
            targets.partition { it.timing == TrimTiming.LIVE }
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew test --tests TrimTargetSelectionTest --console=plain`
Expected: PASS — 1 test.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/qoliber/phpstorm/indexblocker/cachehealth/TrimCachesAction.kt \
        src/test/kotlin/com/qoliber/phpstorm/indexblocker/cachehealth/TrimTargetSelectionTest.kt
git commit -m "feat: add TrimCachesAction with hybrid live/on-restart deletion"
```

---

## Task 8: `InvalidateAndRestartAction`

**Files:**
- Create: `src/main/kotlin/com/qoliber/phpstorm/indexblocker/cachehealth/InvalidateAndRestartAction.kt`

- [ ] **Step 1: Implement**

Delegates to the native action id `InvalidateCaches` (confirmed in Task 1 findings).

```kotlin
package com.qoliber.phpstorm.indexblocker.cachehealth

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages

class InvalidateAndRestartAction : AnAction("Invalidate Caches and Restart") {

    override fun actionPerformed(e: AnActionEvent) {
        val native = ActionManager.getInstance().getAction("InvalidateCaches")
        if (native != null) {
            native.actionPerformed(e)
        } else {
            Messages.showErrorDialog(
                "The Invalidate Caches action is unavailable in this IDE build.",
                "Invalidate Caches",
            )
        }
    }
}
```

- [ ] **Step 2: Verify compile**

Run: `./gradlew compileKotlin --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/com/qoliber/phpstorm/indexblocker/cachehealth/InvalidateAndRestartAction.kt
git commit -m "feat: add InvalidateAndRestartAction delegating to native action"
```

---

## Task 9: `SharedIndexDisabler`

**Files:**
- Create: `src/main/kotlin/com/qoliber/phpstorm/indexblocker/cachehealth/SharedIndexDisabler.kt`

Implement the lever recorded as authoritative in Task 1 findings. This plan codes the **registry-flag** path (preferred, no restart); if Task 1 determined the registry key does not gate the feature, replace the body with the plugin-disable fallback documented in the findings.

- [ ] **Step 1: Implement (registry-flag path)**

Replace `SHARED_INDEX_REGISTRY_KEY` with the exact key confirmed in Task 1.

```kotlin
package com.qoliber.phpstorm.indexblocker.cachehealth

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.registry.Registry

object SharedIndexDisabler {

    private const val SHARED_INDEX_REGISTRY_KEY = "shared.indexes.download"

    /** Force downloadable shared indexes off when enforcement is enabled. Idempotent. */
    fun enforce() {
        if (!CacheHealthSettings.getInstance().enforceDisableSharedIndexes) return
        try {
            val value = Registry.get(SHARED_INDEX_REGISTRY_KEY)
            if (value.asBoolean()) {
                value.setValue(false)
                thisLogger().info("Disabled downloadable shared indexes via $SHARED_INDEX_REGISTRY_KEY")
            }
        } catch (t: Throwable) {
            thisLogger().warn("Failed to enforce shared-index disabling", t)
        }
    }
}
```

- [ ] **Step 2: Verify compile**

Run: `./gradlew compileKotlin --console=plain`
Expected: BUILD SUCCESSFUL. (If `Registry.get(key)` throws for an unknown key at runtime, the try/catch contains it; Task 13 smoke test confirms the key is real.)

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/com/qoliber/phpstorm/indexblocker/cachehealth/SharedIndexDisabler.kt
git commit -m "feat: add SharedIndexDisabler enforcing shared-index off at startup"
```

---

## Task 10: `CacheHealthToolbarWidget`

**Files:**
- Create: `src/main/kotlin/com/qoliber/phpstorm/indexblocker/cachehealth/CacheHealthToolbarWidget.kt`

- [ ] **Step 1: Implement**

A `CustomComponentAction` showing the index size; click opens a popup of actions. Registration into the toolbar group happens in Task 12.

```kotlin
package com.qoliber.phpstorm.indexblocker.cachehealth

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CustomComponentAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction.ACTION_BUTTON_PROPERTY_KEY
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.util.ui.JBUI
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JLabel

class CacheHealthToolbarWidget : DumbAwareAction(), CustomComponentAction {

    override fun actionPerformed(e: AnActionEvent) { /* handled by component click */ }

    override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
        val label = JLabel("Index …").apply {
            border = JBUI.Borders.empty(0, 8)
            toolTipText = "PhpStorm index cache size — click for actions"
        }
        val service = CacheSizeService.getInstance()
        val repaint = Runnable {
            val sizes = service.lastResult()
            if (sizes != null) label.text = "Index ${SizeFormat.humanReadable(sizes.indexBytes)}"
        }
        service.addListener { com.intellij.util.ui.UIUtil.invokeLaterIfNeeded(repaint) }
        service.refreshAsync()
        label.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(ev: MouseEvent) = showPopup(label)
        })
        return label
    }

    private fun showPopup(anchor: JComponent) {
        val group = DefaultActionGroup().apply {
            add(ActionManager.getInstance().getAction("com.qoliber.indexblocker.cachehealth.Trim"))
            add(ActionManager.getInstance().getAction("com.qoliber.indexblocker.cachehealth.Invalidate"))
        }
        JBPopupFactory.getInstance()
            .createActionGroupPopup("Cache Health", group, com.intellij.openapi.actionSystem.DataContext.EMPTY_CONTEXT,
                JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true)
            .showUnderneathOf(anchor)
    }
}
```

- [ ] **Step 2: Verify compile**

Run: `./gradlew compileKotlin --console=plain`
Expected: BUILD SUCCESSFUL. If `ACTION_BUTTON_PROPERTY_KEY` import is unused/incorrect for this SDK, remove it; the widget does not require it.

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/com/qoliber/phpstorm/indexblocker/cachehealth/CacheHealthToolbarWidget.kt
git commit -m "feat: add CacheHealthToolbarWidget showing index size with action popup"
```

---

## Task 11: `CacheHealthConfigurable`

**Files:**
- Create: `src/main/kotlin/com/qoliber/phpstorm/indexblocker/cachehealth/CacheHealthConfigurable.kt`

- [ ] **Step 1: Implement (Kotlin UI DSL)**

```kotlin
package com.qoliber.phpstorm.indexblocker.cachehealth

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel

class CacheHealthConfigurable : BoundConfigurable("Cache Health") {

    override fun createPanel(): DialogPanel {
        val s = CacheHealthSettings.getInstance()
        return panel {
            group("Shared Indexes") {
                row {
                    checkBox("Force downloadable shared indexes off on startup")
                        .bindSelected({ s.enforceDisableSharedIndexes }, { s.enforceDisableSharedIndexes = it })
                }
            }
            group("Cache Size Monitor") {
                row {
                    checkBox("Warn when index cache exceeds threshold")
                        .bindSelected({ s.monitorEnabled }, { s.monitorEnabled = it })
                }
                row("Threshold (GB):") {
                    intTextField(0..1024)
                        .bindIntText({ s.thresholdGb }, { s.thresholdGb = it })
                }
            }
            group("Quick Trim Targets") {
                row { checkBox("Logs").bindSelected({ s.trimLog }, { s.trimLog = it }) }
                row { checkBox("Embedded browser cache (jcef_cache)").bindSelected({ s.trimJcef }, { s.trimJcef = it }) }
                row { checkBox("Full Line AI cache").bindSelected({ s.trimFullLine }, { s.trimFullLine = it }) }
            }
        }
    }

    override fun apply() {
        super.apply()
        SharedIndexDisabler.enforce()
    }
}
```

- [ ] **Step 2: Verify compile**

Run: `./gradlew compileKotlin --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/com/qoliber/phpstorm/indexblocker/cachehealth/CacheHealthConfigurable.kt
git commit -m "feat: add CacheHealthConfigurable settings page"
```

---

## Task 12: plugin.xml wiring

**Files:**
- Modify: `src/main/resources/META-INF/plugin.xml`

Use the toolbar group id and startup hook recorded in Task 1 findings. The block below uses `MainToolbarRight` and `AppLifecycleListener`; adjust to the confirmed values if they differ.

- [ ] **Step 1: Add actions, configurable, notification group, listener**

Inside the existing `<extensions defaultExtensionNs="com.intellij">` block, add:

```xml
        <applicationConfigurable
            groupId="tools"
            id="com.qoliber.indexblocker.cachehealth.settings"
            displayName="Cache Health"
            instance="com.qoliber.phpstorm.indexblocker.cachehealth.CacheHealthConfigurable"/>

        <notificationGroup id="Cache Health" displayType="BALLOON"/>

        <applicationService
            serviceImplementation="com.qoliber.phpstorm.indexblocker.cachehealth.CacheSizeService"/>
        <applicationService
            serviceImplementation="com.qoliber.phpstorm.indexblocker.cachehealth.CacheHealthSettings"/>
```

Add an `<applicationListener>` for startup enforcement + monitor:

```xml
        <applicationListener
            class="com.qoliber.phpstorm.indexblocker.cachehealth.CacheHealthStartup"
            topic="com.intellij.ide.AppLifecycleListener"/>
```

Add the actions section (sibling to `<extensions>`):

```xml
    <actions>
        <action id="com.qoliber.indexblocker.cachehealth.Trim"
                class="com.qoliber.phpstorm.indexblocker.cachehealth.TrimCachesAction"
                text="Trim Caches"
                description="Delete safe-to-clear PhpStorm caches"/>
        <action id="com.qoliber.indexblocker.cachehealth.Invalidate"
                class="com.qoliber.phpstorm.indexblocker.cachehealth.InvalidateAndRestartAction"
                text="Invalidate Caches and Restart"
                description="Invalidate caches and restart the IDE"/>
        <action id="com.qoliber.indexblocker.cachehealth.Widget"
                class="com.qoliber.phpstorm.indexblocker.cachehealth.CacheHealthToolbarWidget"
                text="Cache Health">
            <add-to-group group-id="MainToolbarRight" anchor="first"/>
        </action>
    </actions>
```

- [ ] **Step 2: Create the `CacheHealthStartup` listener**

Create `src/main/kotlin/com/qoliber/phpstorm/indexblocker/cachehealth/CacheHealthStartup.kt`:

```kotlin
package com.qoliber.phpstorm.indexblocker.cachehealth

import com.intellij.ide.AppLifecycleListener

class CacheHealthStartup : AppLifecycleListener {
    override fun appStarted() {
        SharedIndexDisabler.enforce()
        CacheSizeMonitor.check()
    }
}
```

- [ ] **Step 3: Verify compile + tests + package**

Run:
```bash
./gradlew test buildPlugin --console=plain
```
Expected: BUILD SUCCESSFUL; all tests green (16 new cache-health tests + the existing 15 = 31); zip produced.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/META-INF/plugin.xml \
        src/main/kotlin/com/qoliber/phpstorm/indexblocker/cachehealth/CacheHealthStartup.kt
git commit -m "feat: wire cache-health actions, settings, listener, and toolbar widget"
```

---

## Task 13: Verify + smoke test + package

**Files:** none — verification only.

- [ ] **Step 1: Run the plugin verifier**

Run:
```bash
./gradlew verifyPlugin --console=plain
```
Expected: BUILD SUCCESSFUL with no compatibility errors against build 253.

- [ ] **Step 2: Launch the sandbox IDE**

Run:
```bash
./gradlew runIde
```

- [ ] **Step 3: Smoke-test (manual, in the sandbox IDE)**

1. Confirm the **Index NN GB** widget appears in the main toolbar and shows a real size after a few seconds.
2. Click it → popup shows Trim Caches and Invalidate & Restart.
3. Open **Settings → Tools → Cache Health**; toggle options; set threshold to `0` and Apply.
4. Trigger `CacheSizeMonitor` (reopen a project or wait for the periodic check) → a "index cache is large" notification appears with action buttons.
5. Run **Trim Caches** → confirm the dialog reports now/after-restart figures.
6. Verify **shared indexes**: with enforce on, restart the sandbox IDE and confirm in Settings (or the registry) that downloadable Shared Indexes are off.

Quit the sandbox IDE.

- [ ] **Step 4: Build the distributable zip**

Run:
```bash
./gradlew buildPlugin
ls -lh build/distributions/
```
Expected: `phpstorm-index-blocker-1.0.0.zip` (or bumped version) present.

- [ ] **Step 5: Commit any smoke-test fixes (if needed) and tag**

```bash
git status
# if clean:
git tag v1.1.0
git log --oneline | head -20
```

---

## Done criteria

- ✅ Build retargeted to PhpStorm 2025.3 (since/until 253); compiles + verifier passes.
- ✅ All tests pass: existing 15 + new cache-health unit/integration tests.
- ✅ Toolbar widget shows live index size and opens an action popup.
- ✅ Quick trim deletes safe dirs live and schedules locked dirs for restart.
- ✅ Invalidate & Restart delegates to the native action.
- ✅ Size monitor notifies above threshold (default 8 GB), once per session.
- ✅ Shared indexes are forced off on startup when enforcement is enabled (default on).
- ✅ Settings page under Tools → Cache Health controls all toggles.
