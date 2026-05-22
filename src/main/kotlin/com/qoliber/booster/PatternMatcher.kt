package com.qoliber.booster

object PatternMatcher {

    sealed class Pattern {
        data class ExactName(val name: String) : Pattern()
        data class Prefix(val prefix: String) : Pattern()
        data class RelativePath(val path: String) : Pattern()
    }

    fun parse(entry: String): Pattern? {
        val trimmed = entry.trim()
        if (trimmed.isEmpty()) return null
        return when {
            trimmed.contains('/') -> {
                val normalized = trimmed.trimEnd('/')
                if (normalized.isEmpty()) null else Pattern.RelativePath(normalized)
            }
            trimmed == "*" -> null
            trimmed.endsWith('*') -> Pattern.Prefix(trimmed.dropLast(1))
            else -> Pattern.ExactName(trimmed)
        }
    }

    fun matches(pattern: Pattern, folderName: String, relativePath: String): Boolean = when (pattern) {
        is Pattern.ExactName -> folderName == pattern.name
        is Pattern.Prefix -> pattern.prefix.isNotEmpty() && folderName.startsWith(pattern.prefix)
        is Pattern.RelativePath -> relativePath == pattern.path
    }
}
