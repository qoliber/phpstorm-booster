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
