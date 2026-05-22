package com.qoliber.booster.cachehealth

import com.intellij.ide.startup.StartupActionScriptManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
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
                LOG.warn("Live trim failed for ${t.path}", ex)
            }
        }
        for (t in onRestart) {
            try {
                StartupActionScriptManager.addActionCommand(
                    StartupActionScriptManager.DeleteCommand(t.path)
                )
            } catch (ex: Throwable) {
                LOG.warn("Scheduling restart-trim failed for ${t.path}", ex)
            }
        }
        CacheSizeService.getInstance().refreshAsync()
    }

    companion object {
        private val LOG = Logger.getInstance(TrimCachesAction::class.java)

        fun partition(targets: List<TrimTarget>): Pair<List<TrimTarget>, List<TrimTarget>> =
            targets.partition { it.timing == TrimTiming.LIVE }
    }
}
