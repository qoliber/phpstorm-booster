package com.qoliber.phpstorm.indexblocker

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class IndexBlockerExcludePolicyTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        IndexBlockerSettings.getInstance().enabled = true
    }

    override fun tearDown() {
        try {
            IndexBlockerSettings.getInstance().enabled = true
            IndexBlockerSettings.getInstance().blockedEntries = listOf()
        } finally {
            super.tearDown()
        }
    }

    private fun policy(): IndexBlockerExcludePolicy = IndexBlockerExcludePolicy(project)

    private fun makeDirs(vararg relativePaths: String) {
        // Each marker file forces creation of its parent directory chain under
        // the light fixture's content root.
        for (path in relativePaths) {
            myFixture.addFileToProject("$path/.keep", "")
        }
    }

    fun `test exact name match at any depth`() {
        makeDirs("var", "src/foo/var", "src/foo/bar")
        IndexBlockerSettings.getInstance().blockedEntries = listOf("var")

        val urls = policy().excludeUrlsForProject.toList()

        assertTrue("expected top-level var, got $urls", urls.any { it.endsWith("/var") })
        assertTrue("expected nested var, got $urls", urls.any { it.endsWith("/src/foo/var") })
        assertEquals(2, urls.size)
    }

    fun `test prefix wildcard match`() {
        makeDirs("Test", "Tests", "TestUtils", "MyTest")
        IndexBlockerSettings.getInstance().blockedEntries = listOf("Test*")

        val urls = policy().excludeUrlsForProject.toList()

        assertEquals(3, urls.size)
        assertTrue(urls.any { it.endsWith("/Test") })
        assertTrue(urls.any { it.endsWith("/Tests") })
        assertTrue(urls.any { it.endsWith("/TestUtils") })
        assertFalse(urls.any { it.endsWith("/MyTest") })
    }

    fun `test relative path match`() {
        makeDirs("pub/static", "pub/static/sub", "src/pub/static", "static")
        IndexBlockerSettings.getInstance().blockedEntries = listOf("pub/static")

        val urls = policy().excludeUrlsForProject.toList()

        assertEquals(1, urls.size)
        assertTrue(urls.single().endsWith("/pub/static"))
    }

    fun `test disabled flag suppresses all matches`() {
        makeDirs("var")
        IndexBlockerSettings.getInstance().blockedEntries = listOf("var")
        IndexBlockerSettings.getInstance().enabled = false

        assertEquals(0, policy().excludeUrlsForProject.size)
    }

    fun `test empty block list returns nothing`() {
        makeDirs("var")
        IndexBlockerSettings.getInstance().blockedEntries = listOf()

        assertEquals(0, policy().excludeUrlsForProject.size)
    }
}
