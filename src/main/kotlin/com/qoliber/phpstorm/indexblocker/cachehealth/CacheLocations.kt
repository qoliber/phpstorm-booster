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
