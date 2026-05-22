package com.qoliber.booster.cachehealth

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
