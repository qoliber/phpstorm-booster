# Magento Fallback Exclusion Implementation Plan (v0.2)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** In projects detected as Magento, automatically exclude a curated, root-anchored set of build directories from indexing — on top of the unchanged global manual block list — gated by a default-on toggle.

**Architecture:** Add `MagentoProjectDetector` (is this content root a Magento root?) and `MagentoPreset` (the curated dir list). Extend `IndexBlockerExcludePolicy` to union the existing manual matches with the preset dirs of detected Magento roots, deduped by URL. Add one persisted setting and one settings checkbox.

**Tech Stack:** Kotlin, IntelliJ Platform 2025.3 SDK, JUnit 4 + `BasePlatformTestCase`. Package `com.qoliber.booster`.

**Reference spec:** `docs/superpowers/specs/2026-05-22-magento-fallback-design.md`

---

## File Structure

| Path | Responsibility |
| --- | --- |
| `src/main/kotlin/com/qoliber/booster/MagentoPreset.kt` | Constant list of Magento build dirs to exclude. |
| `src/main/kotlin/com/qoliber/booster/MagentoProjectDetector.kt` | Detect a Magento root (`bin/magento` + `app/etc/`). |
| `src/main/kotlin/com/qoliber/booster/IndexBlockerSettings.kt` (modify) | Add `magentoFallbackEnabled` (default true). |
| `src/main/kotlin/com/qoliber/booster/IndexBlockerExcludePolicy.kt` (modify) | Union manual matches + Magento preset dirs, deduped. |
| `src/main/kotlin/com/qoliber/booster/IndexBlockerConfigurable.kt` (modify) | Add the toggle checkbox. |
| `src/test/kotlin/com/qoliber/booster/MagentoProjectDetectorTest.kt` | Detector tests. |
| `src/test/kotlin/com/qoliber/booster/IndexBlockerExcludePolicyTest.kt` (modify) | Add Magento-fallback cases. |

---

## Task 1: `MagentoPreset` + `MagentoProjectDetector` (TDD)

**Files:**
- Create: `src/main/kotlin/com/qoliber/booster/MagentoPreset.kt`
- Create: `src/main/kotlin/com/qoliber/booster/MagentoProjectDetector.kt`
- Create: `src/test/kotlin/com/qoliber/booster/MagentoProjectDetectorTest.kt`

- [ ] **Step 1: Write the failing detector test**

Create `src/test/kotlin/com/qoliber/booster/MagentoProjectDetectorTest.kt`:

```kotlin
package com.qoliber.booster

import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class MagentoProjectDetectorTest : BasePlatformTestCase() {

    private fun contentRoot() = ProjectRootManager.getInstance(project).contentRoots[0]

    fun `test detects magento root when bin magento and app etc exist`() {
        myFixture.addFileToProject("bin/magento", "#!/usr/bin/env php")
        myFixture.addFileToProject("app/etc/env.php", "<?php")
        assertEquals(contentRoot(), MagentoProjectDetector.magentoRootFor(contentRoot()))
    }

    fun `test not magento without bin magento`() {
        myFixture.addFileToProject("app/etc/env.php", "<?php")
        assertNull(MagentoProjectDetector.magentoRootFor(contentRoot()))
    }

    fun `test not magento without app etc`() {
        myFixture.addFileToProject("bin/magento", "x")
        assertNull(MagentoProjectDetector.magentoRootFor(contentRoot()))
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests MagentoProjectDetectorTest --console=plain`
Expected: compilation failure — `MagentoProjectDetector` undefined.

- [ ] **Step 3: Implement `MagentoPreset`**

Create `src/main/kotlin/com/qoliber/booster/MagentoPreset.kt`:

```kotlin
package com.qoliber.booster

object MagentoPreset {
    /** Root-relative build/generated directories to exclude in Magento projects. */
    val dirs: List<String> = listOf(
        "generated",
        "var",
        "pub/static",
        "pub/media",
        "setup",
        "node_modules",
    )
}
```

- [ ] **Step 4: Implement `MagentoProjectDetector`**

Create `src/main/kotlin/com/qoliber/booster/MagentoProjectDetector.kt`:

```kotlin
package com.qoliber.booster

import com.intellij.openapi.vfs.VirtualFile

object MagentoProjectDetector {

    /** Returns [contentRoot] if it looks like a Magento root (has bin/magento and app/etc), else null. */
    fun magentoRootFor(contentRoot: VirtualFile): VirtualFile? {
        if (!contentRoot.isDirectory) return null
        val binMagento = contentRoot.findFileByRelativePath("bin/magento")
        val appEtc = contentRoot.findFileByRelativePath("app/etc")
        val ok = binMagento != null && !binMagento.isDirectory &&
            appEtc != null && appEtc.isDirectory
        return if (ok) contentRoot else null
    }
}
```

- [ ] **Step 5: Run to verify it passes**

Run: `./gradlew test --tests MagentoProjectDetectorTest --console=plain`
Expected: PASS — 3 tests.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/qoliber/booster/MagentoPreset.kt \
        src/main/kotlin/com/qoliber/booster/MagentoProjectDetector.kt \
        src/test/kotlin/com/qoliber/booster/MagentoProjectDetectorTest.kt
git commit -m "feat: add MagentoProjectDetector and MagentoPreset"
```

---

## Task 2: Add `magentoFallbackEnabled` to `IndexBlockerSettings`

**Files:**
- Modify: `src/main/kotlin/com/qoliber/booster/IndexBlockerSettings.kt`

- [ ] **Step 1: Add the state field**

In the `State` class, after the `blockedEntries` field, add:

```kotlin
        @JvmField var magentoFallbackEnabled: Boolean = true
```

- [ ] **Step 2: Add the accessor property**

After the existing `blockedEntries` property (before the `companion object`), add:

```kotlin
    var magentoFallbackEnabled: Boolean
        get() = state.magentoFallbackEnabled
        set(value) { state.magentoFallbackEnabled = value }
```

- [ ] **Step 3: Verify compile**

Run: `./gradlew compileKotlin --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/com/qoliber/booster/IndexBlockerSettings.kt
git commit -m "feat: add magentoFallbackEnabled setting"
```

---

## Task 3: Extend `IndexBlockerExcludePolicy` (TDD)

**Files:**
- Modify: `src/main/kotlin/com/qoliber/booster/IndexBlockerExcludePolicy.kt`
- Modify: `src/test/kotlin/com/qoliber/booster/IndexBlockerExcludePolicyTest.kt`

- [ ] **Step 1: Add Magento cases + reset to the test**

In `src/test/kotlin/com/qoliber/booster/IndexBlockerExcludePolicyTest.kt`, update `tearDown` to also reset the new flag, and add four test methods.

Replace the existing `tearDown` with:

```kotlin
    override fun tearDown() {
        try {
            IndexBlockerSettings.getInstance().enabled = true
            IndexBlockerSettings.getInstance().blockedEntries = listOf()
            IndexBlockerSettings.getInstance().magentoFallbackEnabled = true
        } finally {
            super.tearDown()
        }
    }
```

Add these methods to the class:

```kotlin
    private fun makeMagentoRoot() {
        myFixture.addFileToProject("bin/magento", "x")
        myFixture.addFileToProject("app/etc/env.php", "<?php")
    }

    fun `test magento fallback excludes preset dirs`() {
        makeMagentoRoot()
        makeDirs("generated", "var", "pub/static", "pub/media", "setup", "node_modules", "app/code/Vendor/Module")
        IndexBlockerSettings.getInstance().blockedEntries = listOf()
        IndexBlockerSettings.getInstance().magentoFallbackEnabled = true

        val urls = policy().excludeUrlsForProject.toList()

        assertTrue("generated, got $urls", urls.any { it.endsWith("/generated") })
        assertTrue("pub/media, got $urls", urls.any { it.endsWith("/pub/media") })
        assertTrue("setup, got $urls", urls.any { it.endsWith("/setup") })
        assertFalse("must not exclude app/code", urls.any { it.endsWith("/app/code/Vendor/Module") })
    }

    fun `test magento fallback dedups with manual list`() {
        makeMagentoRoot()
        makeDirs("generated", "var")
        IndexBlockerSettings.getInstance().blockedEntries = listOf("var")
        IndexBlockerSettings.getInstance().magentoFallbackEnabled = true

        val urls = policy().excludeUrlsForProject.toList()

        assertEquals("var excluded exactly once", 1, urls.count { it.endsWith("/var") })
        assertTrue(urls.any { it.endsWith("/generated") })
    }

    fun `test magento fallback off adds nothing`() {
        makeMagentoRoot()
        makeDirs("generated", "var")
        IndexBlockerSettings.getInstance().blockedEntries = listOf()
        IndexBlockerSettings.getInstance().magentoFallbackEnabled = false

        assertEquals(0, policy().excludeUrlsForProject.size)
    }

    fun `test non-magento project gets no preset`() {
        makeDirs("generated", "var")
        IndexBlockerSettings.getInstance().blockedEntries = listOf()
        IndexBlockerSettings.getInstance().magentoFallbackEnabled = true

        assertEquals(0, policy().excludeUrlsForProject.size)
    }
```

- [ ] **Step 2: Run to verify the new cases fail**

Run: `./gradlew test --tests IndexBlockerExcludePolicyTest --console=plain`
Expected: FAIL — the new `magento fallback excludes preset dirs` / `dedups` cases fail (current policy ignores Magento; with an empty manual list it returns nothing). The off/non-magento cases pass already.

- [ ] **Step 3: Replace the policy implementation**

Replace the entire contents of `src/main/kotlin/com/qoliber/booster/IndexBlockerExcludePolicy.kt`:

```kotlin
package com.qoliber.booster

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootModel
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.impl.DirectoryIndexExcludePolicy
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.pointers.VirtualFilePointer

class IndexBlockerExcludePolicy(private val project: Project) : DirectoryIndexExcludePolicy {

    override fun getExcludeUrlsForProject(): Array<String> {
        val settings = IndexBlockerSettings.getInstance()
        if (!settings.enabled) return emptyArray()

        // LinkedHashSet gives stable order plus automatic dedup by URL
        // (so a preset dir already matched by a manual pattern appears once).
        val urls = LinkedHashSet<String>()
        val contentRoots = ProjectRootManager.getInstance(project).contentRoots

        val patterns = settings.blockedEntries.mapNotNull(PatternMatcher::parse)
        if (patterns.isNotEmpty()) {
            for (contentRoot in contentRoots) {
                collectMatches(contentRoot, "", patterns, urls)
            }
        }

        if (settings.magentoFallbackEnabled) {
            for (contentRoot in contentRoots) {
                val magentoRoot = MagentoProjectDetector.magentoRootFor(contentRoot) ?: continue
                for (rel in MagentoPreset.dirs) {
                    val dir = magentoRoot.findFileByRelativePath(rel) ?: continue
                    if (dir.isDirectory) urls.add(dir.url)
                }
            }
        }

        return urls.toTypedArray()
    }

    override fun getExcludeRootsForModule(rootModel: ModuleRootModel): Array<VirtualFilePointer> =
        emptyArray()

    private fun collectMatches(
        dir: VirtualFile,
        relativePath: String,
        patterns: List<PatternMatcher.Pattern>,
        out: MutableCollection<String>,
    ) {
        if (!dir.isDirectory) return
        for (child in dir.children) {
            if (!child.isDirectory) continue
            val childRel = if (relativePath.isEmpty()) child.name else "$relativePath/${child.name}"
            val matched = patterns.any { PatternMatcher.matches(it, child.name, childRel) }
            if (matched) {
                out.add(child.url)
                // Do not descend — entire subtree is excluded.
            } else {
                collectMatches(child, childRel, patterns, out)
            }
        }
    }
}
```

- [ ] **Step 4: Run to verify all pass**

Run: `./gradlew test --tests IndexBlockerExcludePolicyTest --console=plain`
Expected: PASS — 9 tests (5 original + 4 new).

- [ ] **Step 5: Run the full suite**

Run: `./gradlew test --console=plain`
Expected: PASS — full suite green (existing cache-health + index-blocker + the new Magento tests).

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/qoliber/booster/IndexBlockerExcludePolicy.kt \
        src/test/kotlin/com/qoliber/booster/IndexBlockerExcludePolicyTest.kt
git commit -m "feat: apply Magento preset exclusions in detected Magento projects"
```

---

## Task 4: Add the toggle to `IndexBlockerConfigurable`

**Files:**
- Modify: `src/main/kotlin/com/qoliber/booster/IndexBlockerConfigurable.kt`

The configurable is a hand-built Swing panel with a `JBCheckBox` field named `enabledCheckbox`, a `JBList` of entries, `createComponent()`, `isModified()`, `apply()`, and `reset()`. Read the current file first, then make these four additions.

- [ ] **Step 1: Read the current file**

Run: open `src/main/kotlin/com/qoliber/booster/IndexBlockerConfigurable.kt` and confirm it has `enabledCheckbox` plus `createComponent`/`isModified`/`apply`/`reset`.

- [ ] **Step 2: Add the checkbox field**

Next to the existing `private val enabledCheckbox = JBCheckBox("Enable Index Blocker")` declaration, add:

```kotlin
    private val magentoCheckbox =
        JBCheckBox("Auto-exclude Magento build directories in detected Magento projects")
```

- [ ] **Step 3: Place the checkbox in the UI**

In `createComponent()`, the existing code adds `enabledCheckbox` to the panel's `BorderLayout.NORTH`. Replace that single add with a small vertical panel holding both checkboxes, so the Magento toggle appears under the enable toggle:

```kotlin
        val topPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(enabledCheckbox)
            add(magentoCheckbox)
        }
        add(topPanel, BorderLayout.NORTH)
```

Add the imports `javax.swing.BoxLayout` and `javax.swing.JPanel` if not already present.

- [ ] **Step 4: Wire isModified / apply / reset**

In `isModified()`, add (before the final entries comparison `return`):

```kotlin
        if (magentoCheckbox.isSelected != s.magentoFallbackEnabled) return true
```
(where `s` is the `IndexBlockerSettings.getInstance()` already referenced in that method; if the method uses a different local name, match it.)

In `apply()`, after the existing `s.enabled = enabledCheckbox.isSelected` line, add:

```kotlin
        s.magentoFallbackEnabled = magentoCheckbox.isSelected
```

In `reset()`, after the existing `enabledCheckbox.isSelected = s.enabled` line, add:

```kotlin
        magentoCheckbox.isSelected = s.magentoFallbackEnabled
```

- [ ] **Step 5: Verify compile + tests + package**

Run:
```bash
./gradlew test buildPlugin --console=plain
```
Expected: BUILD SUCCESSFUL; full suite green; zip produced.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/qoliber/booster/IndexBlockerConfigurable.kt
git commit -m "feat: add Magento fallback toggle to Index Blocker settings"
```

---

## Task 5: Verify + package

**Files:** none — verification only.

- [ ] **Step 1: Plugin verifier**

Run: `./gradlew verifyPlugin --console=plain`
Expected: BUILD SUCCESSFUL (the two pre-existing internal-API warnings remain; no new errors).

- [ ] **Step 2: Confirm the zip**

Run: `ls -lh build/distributions/*.zip`
Expected: `phpstorm-booster-0.1.1.zip` present (version bump to 0.2.0 / release is a separate step the controller handles).

- [ ] **Step 3 (optional manual smoke):**

`./gradlew runIde`, open a Magento project (with `bin/magento` + `app/etc/`), confirm `generated`/`var`/`pub/static`/`pub/media`/`setup`/`node_modules` show the excluded mark; toggle the new checkbox in Settings → Tools → Index Blocker off → Apply → marks for preset-only dirs disappear.

---

## Done criteria

- ✅ `MagentoProjectDetector` correctly identifies Magento roots (3 unit tests).
- ✅ Policy excludes the root-anchored preset dirs in detected Magento projects, deduped against the manual list, and adds nothing when the toggle is off or the project isn't Magento (4 tests).
- ✅ Manual list behavior unchanged (5 original tests still pass).
- ✅ Settings toggle persists and is wired into the configurable.
- ✅ `verifyPlugin` passes; plugin packages.
