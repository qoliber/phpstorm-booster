package com.qoliber.phpstorm.indexblocker.cachehealth

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
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
                val index = CacheLocations.indexReportDirs().sumOf { dirSize(it) }
                val total = perDir.values.sum()
                last = CacheSizes(index, total, perDir, Instant.now())
                listeners.forEach { it.run() }
            } catch (t: Throwable) {
                LOG.warn("Cache size computation failed", t)
            }
        }
    }

    companion object {
        private val LOG = Logger.getInstance(CacheSizeService::class.java)

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
                LOG.warn("Failed walking $dir", t)
            }
            return total
        }
    }
}
