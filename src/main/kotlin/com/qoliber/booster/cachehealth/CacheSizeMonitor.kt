package com.qoliber.booster.cachehealth

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
        ActionManager.getInstance().getAction("com.qoliber.booster.cachehealth.Trim")?.let { n.addAction(it) }
        ActionManager.getInstance().getAction("com.qoliber.booster.cachehealth.Invalidate")?.let { n.addAction(it) }
        n.notify(null)
    }
}
