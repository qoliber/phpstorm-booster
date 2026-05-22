package com.qoliber.booster

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.RootsChangeRescanningInfo
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent

@Service(Service.Level.PROJECT)
class IndexBlockerRefresher(private val project: Project) {

    fun refresh() {
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            ApplicationManager.getApplication().runWriteAction {
                ProjectRootManagerEx.getInstanceEx(project)
                    .makeRootsChange(EmptyRunnable.INSTANCE, RootsChangeRescanningInfo.TOTAL_RESCAN)
            }
        }
    }

    companion object {
        @JvmStatic
        fun refreshAllOpenProjects() {
            for (p in ProjectManager.getInstance().openProjects) {
                p.getService(IndexBlockerRefresher::class.java).refresh()
            }
        }
    }
}

internal class IndexBlockerVfsListener : AsyncFileListener {

    private val log = Logger.getInstance(IndexBlockerVfsListener::class.java)

    override fun prepareChange(events: List<VFileEvent>): AsyncFileListener.ChangeApplier? {
        val settings = IndexBlockerSettings.getInstance()
        if (!settings.enabled) return null
        val patterns = settings.blockedEntries.mapNotNull(PatternMatcher::parse)
        if (patterns.isEmpty()) return null

        val relevant = events.any { evt -> isRelevant(evt, patterns) }
        if (!relevant) return null

        return object : AsyncFileListener.ChangeApplier {
            override fun afterVfsChange() {
                try {
                    IndexBlockerRefresher.refreshAllOpenProjects()
                } catch (t: Throwable) {
                    log.warn("Failed to refresh after VFS change", t)
                }
            }
        }
    }

    private fun isRelevant(event: VFileEvent, patterns: List<PatternMatcher.Pattern>): Boolean {
        val name = when (event) {
            is VFileCreateEvent -> event.childName
            is VFileDeleteEvent -> event.file.name
            else -> return false
        }
        // Cheap name-only check: prefix/exact match by name, or last segment of relative path.
        return patterns.any { p ->
            when (p) {
                is PatternMatcher.Pattern.ExactName -> p.name == name
                is PatternMatcher.Pattern.Prefix -> p.prefix.isNotEmpty() && name.startsWith(p.prefix)
                is PatternMatcher.Pattern.RelativePath -> p.path.substringAfterLast('/') == name
            }
        }
    }
}
