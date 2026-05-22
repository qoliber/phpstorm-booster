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

        val patterns = settings.blockedEntries.mapNotNull(PatternMatcher::parse)
        if (patterns.isEmpty()) return emptyArray()

        val urls = mutableListOf<String>()
        for (contentRoot in ProjectRootManager.getInstance(project).contentRoots) {
            collectMatches(contentRoot, "", patterns, urls)
        }
        return urls.toTypedArray()
    }

    override fun getExcludeRootsForModule(rootModel: ModuleRootModel): Array<VirtualFilePointer> =
        emptyArray()

    private fun collectMatches(
        dir: VirtualFile,
        relativePath: String,
        patterns: List<PatternMatcher.Pattern>,
        out: MutableList<String>,
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
