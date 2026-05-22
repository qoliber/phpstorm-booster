# Magento Fallback Exclusion — Design (v0.2)

**Date:** 2026-05-22
**Status:** Approved (brainstorming phase)
**Author:** jwinkler@qoliber.com
**Builds on:** the Index Blocker feature (global manual block list).

## Purpose

Automatically exclude Magento build/generated directories from indexing in projects detected as Magento, without the user having to maintain the manual block list. Complements — does not replace — the existing global manual list.

## Scope

**In scope:**
- Detect whether a content root is a Magento project root.
- For detected Magento roots, additionally exclude a curated, root-anchored set of build dirs, skipping anything the manual list already excludes.
- A single settings toggle (default on).

**Explicitly out of scope (descoped from the original v0.2 idea):**
- Full `.gitignore`-semantics exclusion (JGit-based) — dropped.
- Indexing-cost diagnostics surfacing — dropped.
- Per-project settings storage — the toggle is global; the *effect* is per-project (only Magento roots are affected).

## Decisions

- **Layering:** the Magento preset is a per-project layer applied on top of the unchanged global manual list. The manual list keeps applying to every project.
- **Role of the preset:** a complete, **root-anchored**, auto-activated Magento exclusion set. It earns its keep over the manual defaults by (a) adding dirs the defaults miss (`pub/media`, `setup`), (b) continuing to work if the user customizes/clears the manual list, and (c) only firing on real Magento projects, anchored to the actual Magento root (not any folder that happens to share a name).
- **Detection signal:** a content root is a Magento root iff both `bin/magento` (file) and `app/etc/` (directory) exist directly under it.

## Architecture

The global manual block list and `IndexBlockerSettings` manual entries are unchanged. The per-project `IndexBlockerExcludePolicy` gains one additional step after its existing manual walk.

### `MagentoProjectDetector` (object)

```kotlin
fun magentoRootFor(contentRoot: VirtualFile): VirtualFile?
```
Returns `contentRoot` if `contentRoot/bin/magento` exists and `contentRoot/app/etc` is a directory; otherwise `null`. Cheap VFS existence checks. (v0.2 only checks the content root itself, not nested subdirectories — Magento roots are content roots in practice.)

### `MagentoPreset` (object)

A constant list of root-relative directories to exclude in Magento projects:
```
generated
var
pub/static
pub/media
setup
node_modules
```
Root-anchored: resolved relative to the detected Magento root, so `pub/static` means *that* project's `pub/static`, not a same-named folder elsewhere.

### `IndexBlockerExcludePolicy` (extended)

`getExcludeUrlsForProject()` currently returns manual-pattern matches. Extend it:
1. Compute the existing manual-match URL set (unchanged).
2. If `IndexBlockerSettings.magentoFallbackEnabled` is true, for each content root: if `MagentoProjectDetector.magentoRootFor(root) != null`, for each `MagentoPreset` entry, resolve the directory under the root; if it exists as a directory and its URL is not already in the manual-match set, add it.
3. Return the union.

Deduplication is by exact URL: a preset dir already excluded by a manual pattern is not added twice.

### `IndexBlockerSettings` (extended)

Add one field: `magentoFallbackEnabled: Boolean = true` (with getter/setter, persisted in the existing `indexBlocker.xml`).

### `IndexBlockerConfigurable` (extended)

Add one checkbox below the existing controls: *"Auto-exclude Magento build directories in detected Magento projects."* bound to `magentoFallbackEnabled`. On apply, the existing refresh path re-queries the policy.

## Data flow

```
policy queried
  → manual-pattern matches (existing)
  → if magentoFallbackEnabled:
       for each content root that is a Magento root:
         add existing MagentoPreset dirs (minus manual dups)
  → union returned as excluded URLs
settings change → existing IndexBlockerRefresher → re-query
```

## Error handling

- All VFS lookups are null-safe; a missing `bin/magento`/`app/etc` simply means "not Magento".
- A preset dir that doesn't exist in a given project is silently skipped.
- No new threading; detection runs inside the existing policy query (cheap existence checks).

## Testing

- **`MagentoProjectDetectorTest`** (`BasePlatformTestCase`): a fixture with `bin/magento` + `app/etc/` → detected; missing either → not detected.
- **`IndexBlockerExcludePolicyTest`** (extend): a Magento-shaped fixture (`bin/magento`, `app/etc/`, `generated/`, `var/`, `pub/media/`, `setup/`) with the toggle on → preset dirs excluded; a preset dir also matched by a manual entry appears once (dedup); toggle off → no preset dirs added; non-Magento fixture → no preset dirs added.

## File layout

```
src/main/kotlin/com/qoliber/booster/
├── MagentoProjectDetector.kt   (new)
├── MagentoPreset.kt            (new)
├── IndexBlockerExcludePolicy.kt (modify)
├── IndexBlockerSettings.kt      (modify: add magentoFallbackEnabled)
└── IndexBlockerConfigurable.kt  (modify: add checkbox)
src/test/kotlin/com/qoliber/booster/
├── MagentoProjectDetectorTest.kt (new)
└── IndexBlockerExcludePolicyTest.kt (extend)
```

## Open questions

None.
