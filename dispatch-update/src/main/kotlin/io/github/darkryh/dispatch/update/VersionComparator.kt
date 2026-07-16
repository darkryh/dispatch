package io.github.darkryh.dispatch.update

object VersionComparator {
    fun isNewer(
        latest: String,
        current: String,
    ): Boolean = compare(latest, current) > 0

    fun compare(
        a: String,
        b: String,
    ): Int {
        val aVersion = parse(a)
        val bVersion = parse(b)

        if (aVersion.core.isEmpty() || bVersion.core.isEmpty()) {
            return a.compareTo(b)
        }

        val max = maxOf(aVersion.core.size, bVersion.core.size)
        for (i in 0 until max) {
            val left = aVersion.core.getOrElse(i) { 0 }
            val right = bVersion.core.getOrElse(i) { 0 }
            if (left != right) return left.compareTo(right)
        }
        return comparePreRelease(aVersion.preRelease, bVersion.preRelease)
    }

    private data class ParsedVersion(
        val core: List<Int>,
        val preRelease: String?,
    )

    private fun parse(version: String): ParsedVersion {
        // Build metadata (+...) never affects precedence; strip it before splitting off the
        // pre-release qualifier so "1.0.0-beta02+build7" parses the same as "1.0.0-beta02".
        val noMeta =
            version
                .trim()
                .removePrefix("v")
                .removePrefix("V")
                .substringBefore('+')
        val core = noMeta.substringBefore('-')
        val preRelease =
            noMeta
                .substringAfter('-', missingDelimiterValue = "")
                .ifEmpty { null }
        return ParsedVersion(
            core = core.split(".").mapNotNull { it.toIntOrNull() },
            preRelease = preRelease,
        )
    }

    /**
     * Semver-style pre-release precedence. Discarding the qualifier entirely (the previous
     * behavior) made same-core prereleases compare equal, so an app on 1.0.0-beta01 was never
     * told 1.0.0-beta02 existed.
     *
     * Rules:
     * - A version without a qualifier is newer than the same core with one (1.0.0 > 1.0.0-rc1).
     * - Qualifiers compare as alternating digit/non-digit runs so numbering is natural:
     *   beta02 > beta01, beta10 > beta2, and alpha < beta < rc.
     * - Numeric runs sort below alphabetic ones, and a qualifier that extends another is newer
     *   (beta01 < beta01-SNAPSHOT), both per semver precedence.
     */
    private fun comparePreRelease(
        a: String?,
        b: String?,
    ): Int {
        if (a == null && b == null) return 0
        if (a == null) return 1
        if (b == null) return -1
        val aRuns = splitRuns(a)
        val bRuns = splitRuns(b)
        val max = maxOf(aRuns.size, bRuns.size)
        for (i in 0 until max) {
            val left = aRuns.getOrNull(i) ?: return -1
            val right = bRuns.getOrNull(i) ?: return 1
            val leftNum = left.toIntOrNull()
            val rightNum = right.toIntOrNull()
            val comparison =
                when {
                    leftNum != null && rightNum != null -> leftNum.compareTo(rightNum)
                    leftNum != null -> -1
                    rightNum != null -> 1
                    else -> left.compareTo(right, ignoreCase = true)
                }
            if (comparison != 0) return comparison
        }
        return 0
    }

    /** Splits "beta02-SNAPSHOT" into ["beta", "02", "SNAPSHOT"]; '.'/'-' are pure separators. */
    private fun splitRuns(qualifier: String): List<String> {
        val runs = mutableListOf<String>()
        val current = StringBuilder()
        var currentIsDigit = false
        for (char in qualifier) {
            if (char == '.' || char == '-') {
                if (current.isNotEmpty()) {
                    runs += current.toString()
                    current.clear()
                }
                continue
            }
            val isDigit = char.isDigit()
            if (current.isNotEmpty() && isDigit != currentIsDigit) {
                runs += current.toString()
                current.clear()
            }
            currentIsDigit = isDigit
            current.append(char)
        }
        if (current.isNotEmpty()) runs += current.toString()
        return runs
    }
}
