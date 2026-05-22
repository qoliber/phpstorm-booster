package com.qoliber.phpstorm.indexblocker.cachehealth

import org.junit.Assert.assertEquals
import org.junit.Test

class SizeFormatTest {
    @Test fun `zero bytes`() { assertEquals("0 B", SizeFormat.humanReadable(0)) }
    @Test fun `bytes`() { assertEquals("512 B", SizeFormat.humanReadable(512)) }
    @Test fun `kilobytes rounded`() { assertEquals("1.5 KB", SizeFormat.humanReadable(1536)) }
    @Test fun `megabytes`() { assertEquals("2.0 MB", SizeFormat.humanReadable(2L * 1024 * 1024)) }
    @Test fun `gigabytes one decimal`() {
        assertEquals("6.1 GB", SizeFormat.humanReadable((6.1 * 1024 * 1024 * 1024).toLong()))
    }
    @Test fun `negative treated as zero`() { assertEquals("0 B", SizeFormat.humanReadable(-5)) }
}
