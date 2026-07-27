package eu.kanade.tachiyomi.extension.pt.mangalivre

/**
 * Values the site rotates every so often. The surrounding algorithm has been stable, so they
 * are read back from the site script instead of being pinned to a release of this extension.
 */
class PowParameters(
    val cookieName: String,
    val seedXor: Long,
    val multiplier: Long,
    val increment: Long,
    val size: Int,
    val pointCount: Int,
    val depth: Double,
    val divisor: Double,
    val fnvOffset: Long,
    val fnvPrime: Long,
) {
    companion object {
        val DEFAULT = PowParameters(
            cookieName = "toon_h",
            seedXor = 1431655765L,
            multiplier = 1664525L,
            increment = 1013904223L,
            size = 72,
            pointCount = 125,
            depth = 2.45,
            divisor = 2.1,
            fnvOffset = 2166136261L,
            fnvPrime = 16777619L,
        )

        fun parse(script: String): PowParameters {
            val seed = SEED_REGEX.find(script)
            val generator = GENERATOR_REGEX.find(script)
            val size = SIZE_REGEX.find(script)
            val projection = PROJECTION_REGEX.find(script)
            val hash = HASH_REGEX.find(script)

            return PowParameters(
                cookieName = COOKIE_REGEX.find(script)?.groupValues?.get(1) ?: DEFAULT.cookieName,
                seedXor = seed?.groupValues?.get(1)?.toLongOrNull() ?: 0L,
                multiplier = generator?.groupValues?.get(1)?.toLong() ?: DEFAULT.multiplier,
                increment = generator?.groupValues?.get(2)?.toLong() ?: DEFAULT.increment,
                size = size?.groupValues?.get(1)?.toInt() ?: DEFAULT.size,
                pointCount = POINTS_REGEX.find(script)?.groupValues?.get(1)?.toInt()
                    ?: DEFAULT.pointCount,
                depth = projection?.groupValues?.get(1)?.toDouble() ?: DEFAULT.depth,
                divisor = projection?.groupValues?.get(2)?.toDouble() ?: DEFAULT.divisor,
                fnvOffset = hash?.groupValues?.get(1)?.toLong() ?: DEFAULT.fnvOffset,
                fnvPrime = hash?.groupValues?.get(2)?.toLong() ?: DEFAULT.fnvPrime,
            )
        }

        private val COOKIE_REGEX = Regex("""pow:[^}]{0,40}\}[^`]{0,140}`([A-Za-z0-9_]+)=\$\{""")
        private val SEED_REGEX = Regex("""this\.seed=\(?\w+(?:\^(\d+))?\)?>>>0""")
        private val GENERATOR_REGEX = Regex("""Math\.imul\(this\.seed,(\d+)\)\+(\d+)>>>0""")
        private val SIZE_REGEX = Regex("""=new \w+\(\w+\),\w+=(\d+),\w+=\d+,\w+=new Uint8Array""")
        private val POINTS_REGEX = Regex("""for\(let \w+=0;\w+<(\d+);\w+\+\+\)\w+\.push\(\{x:""")
        private val PROJECTION_REGEX =
            Regex("""\w+=([\d.]+),\w+=Math\.floor\(\(\w+\.x/\(\w+\.z\+\w+\)\+1\)\*\(\w+/([\d.]+)\)\)""")
        private val HASH_REGEX =
            Regex("""\w+=(\d+);for\(let \w+=0;\w+<\w+\.length;\w+\+\+\)\w+\^=\w+\[\w+\],\w+=Math\.imul\(\w+,(\d+)\)""")
    }
}
