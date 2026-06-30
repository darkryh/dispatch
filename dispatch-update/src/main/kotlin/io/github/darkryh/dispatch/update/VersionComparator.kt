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
        val aParts = normalize(a)
        val bParts = normalize(b)

        if (aParts.isEmpty() || bParts.isEmpty()) {
            return a.compareTo(b)
        }

        val max = maxOf(aParts.size, bParts.size)
        for (i in 0 until max) {
            val left = aParts.getOrElse(i) { 0 }
            val right = bParts.getOrElse(i) { 0 }
            if (left != right) return left.compareTo(right)
        }
        return 0
    }

    private fun normalize(version: String): List<Int> {
        val core =
            version
                .trim()
                .removePrefix("v")
                .removePrefix("V")
                .split("-", "+")
                .firstOrNull()
                .orEmpty()
        return core
            .split(".")
            .mapNotNull { it.toIntOrNull() }
    }
}
