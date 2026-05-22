package com.qoliber.phpstorm.indexblocker.cachehealth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CacheSizeMonitorTest {
    private val threshold = 8L * 1024 * 1024 * 1024

    @Test fun `below threshold does not warn`() {
        assertFalse(CacheSizeMonitor.shouldWarn(threshold - 1, threshold, monitorEnabled = true, alreadyWarned = false))
    }
    @Test fun `above threshold warns once`() {
        assertTrue(CacheSizeMonitor.shouldWarn(threshold + 1, threshold, monitorEnabled = true, alreadyWarned = false))
    }
    @Test fun `above threshold but already warned does not warn`() {
        assertFalse(CacheSizeMonitor.shouldWarn(threshold + 1, threshold, monitorEnabled = true, alreadyWarned = true))
    }
    @Test fun `disabled monitor never warns`() {
        assertFalse(CacheSizeMonitor.shouldWarn(threshold + 1, threshold, monitorEnabled = false, alreadyWarned = false))
    }
}
