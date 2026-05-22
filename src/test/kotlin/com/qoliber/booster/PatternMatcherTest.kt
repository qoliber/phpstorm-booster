package com.qoliber.booster

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PatternMatcherTest {

    @Test fun `parse trims whitespace`() {
        assertEquals(PatternMatcher.Pattern.ExactName("var"), PatternMatcher.parse("  var  "))
    }

    @Test fun `parse rejects empty entry`() {
        assertNull(PatternMatcher.parse(""))
        assertNull(PatternMatcher.parse("   "))
    }

    @Test fun `parse detects exact name`() {
        assertEquals(PatternMatcher.Pattern.ExactName("var"), PatternMatcher.parse("var"))
        assertEquals(PatternMatcher.Pattern.ExactName("node_modules"), PatternMatcher.parse("node_modules"))
    }

    @Test fun `parse detects prefix wildcard`() {
        assertEquals(PatternMatcher.Pattern.Prefix("Test"), PatternMatcher.parse("Test*"))
    }

    @Test fun `parse rejects bare star`() {
        assertNull(PatternMatcher.parse("*"))
    }

    @Test fun `parse detects relative path`() {
        assertEquals(PatternMatcher.Pattern.RelativePath("pub/static"), PatternMatcher.parse("pub/static"))
    }

    @Test fun `parse trims trailing slash on relative path`() {
        assertEquals(PatternMatcher.Pattern.RelativePath("pub/static"), PatternMatcher.parse("pub/static/"))
    }

    @Test fun `matches exact name at any depth`() {
        val p = PatternMatcher.Pattern.ExactName("var")
        assertTrue(PatternMatcher.matches(p, "var", "var"))
        assertTrue(PatternMatcher.matches(p, "var", "a/b/var"))
        assertFalse(PatternMatcher.matches(p, "vars", "vars"))
        assertFalse(PatternMatcher.matches(p, "myvar", "myvar"))
    }

    @Test fun `matches prefix anchored at start`() {
        val p = PatternMatcher.Pattern.Prefix("Test")
        assertTrue(PatternMatcher.matches(p, "Test", "a/Test"))
        assertTrue(PatternMatcher.matches(p, "Tests", "a/Tests"))
        assertTrue(PatternMatcher.matches(p, "TestUtils", "a/TestUtils"))
        assertFalse(PatternMatcher.matches(p, "MyTest", "a/MyTest"))
        assertFalse(PatternMatcher.matches(p, "test", "a/test")) // case-sensitive
    }

    @Test fun `matches relative path only at exact location`() {
        val p = PatternMatcher.Pattern.RelativePath("pub/static")
        assertTrue(PatternMatcher.matches(p, "static", "pub/static"))
        assertFalse(PatternMatcher.matches(p, "static", "static"))
        assertFalse(PatternMatcher.matches(p, "static", "a/pub/static"))
        assertFalse(PatternMatcher.matches(p, "static", "pub/static/sub"))
    }
}
