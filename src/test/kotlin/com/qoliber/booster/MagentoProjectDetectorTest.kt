package com.qoliber.booster

import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class MagentoProjectDetectorTest : BasePlatformTestCase() {

    private fun contentRoot() = ProjectRootManager.getInstance(project).contentRoots[0]

    fun `test detects magento root when bin magento and app etc exist`() {
        myFixture.addFileToProject("bin/magento", "#!/usr/bin/env php")
        myFixture.addFileToProject("app/etc/env.php", "<?php")
        assertEquals(contentRoot(), MagentoProjectDetector.magentoRootFor(contentRoot()))
    }

    fun `test not magento without bin magento`() {
        myFixture.addFileToProject("app/etc/env.php", "<?php")
        assertNull(MagentoProjectDetector.magentoRootFor(contentRoot()))
    }

    fun `test not magento without app etc`() {
        myFixture.addFileToProject("bin/magento", "x")
        assertNull(MagentoProjectDetector.magentoRootFor(contentRoot()))
    }
}
