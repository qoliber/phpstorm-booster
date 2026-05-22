package com.qoliber.booster.cachehealth

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

class CacheSizeServiceTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun `sums file sizes recursively`() {
        val root = tmp.newFolder("root").toPath()
        Files.write(root.resolve("a.bin"), ByteArray(100))
        val sub = Files.createDirectory(root.resolve("sub"))
        Files.write(sub.resolve("b.bin"), ByteArray(250))
        assertEquals(350L, CacheSizeService.dirSize(root))
    }

    @Test fun `missing dir is zero`() {
        val missing = tmp.root.toPath().resolve("nope")
        assertEquals(0L, CacheSizeService.dirSize(missing))
    }
}
