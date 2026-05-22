package com.qoliber.phpstorm.indexblocker.cachehealth

import org.junit.Assert.assertEquals
import org.junit.Test

class TrimTargetSelectionTest {
    @Test fun `splits live and on-restart targets`() {
        val targets = CacheLocations.trimTargets(trimLog = true, trimJcef = true, trimFullLine = true)
        val (live, onRestart) = TrimCachesAction.partition(targets)
        assertEquals(1, live.size)            // log
        assertEquals(2, onRestart.size)       // jcef + full-line
    }
}
