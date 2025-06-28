package eu.kanade.tachiyomi.extension.all.pixiv

import android.net.Uri

interface PixivTargetCompanion<out T : PixivTarget> {
    val SEARCH_PREFIX: String
    fun fromSearchQuery(query: String): T? {
        if (!query.startsWith(SEARCH_PREFIX)) return null
        val id = query.removePrefix(SEARCH_PREFIX)
        if (!id.matches("\\d+".toRegex())) return null
        return fromSearchQueryId(id)
    }

    fun fromSearchQueryId(id: String): T?
}

sealed class PixivTarget {
    companion object {
        val BASE_URI = Uri.parse("https://www.pixiv.net")

        fun fromSearchQuery(query: String) =
            sequenceOf<PixivTargetCompanion<*>>(User, Series, Illustration)
                .firstNotNullOfOrNull { it.fromSearchQuery(query) }

        fun fromUri(uri: String) = fromUri(Uri.parse(uri))
        fun fromUri(uri: Uri): PixivTarget? {
            // if an absolute domain is specified, check if it matches. Tolerate relative urls as-is.
            if (!(
                uri.scheme in listOf(null, "http", "https") &&
                    uri.host?.let { "pixiv.net" == it.removePrefix("www.") } != false
                )
            ) {
                return null
            }

            var pathSegments = uri.pathSegments ?: return null

            if (KNOWN_LOCALES.contains(pathSegments[0])) {
                pathSegments = pathSegments.subList(1, pathSegments.size)
            }
            if (pathSegments.size < 2) return null

            with(pathSegments[0]) {
                return when {
                    equals("artworks") -> Illustration(pathSegments[1])
                    equals("users") -> User(pathSegments[1])
                    equals("user") &&
                        (pathSegments.size >= 4 && pathSegments[2].equals("series")) ->
                        Series(pathSegments[3], pathSegments[1])

                    else -> null
                }
            }
        }
    }

    abstract fun toUri(): Uri

    abstract fun toSearchQuery(): String

    data class User(val userId: String) : PixivTarget() {
        companion object : PixivTargetCompanion<User> {
            override val SEARCH_PREFIX = "user:"
            override fun fromSearchQueryId(id: String) = User(id)
        }

        override fun toUri(): Uri =
            BASE_URI.buildUpon().appendPath("users").appendPath(userId).build()

        override fun toSearchQuery(): String = SEARCH_PREFIX + userId
    }

    data class Illustration(val illustId: String) : PixivTarget() {
        companion object : PixivTargetCompanion<Illustration> {
            override val SEARCH_PREFIX = "aid:"
            override fun fromSearchQueryId(id: String) = Illustration(id)
        }

        override fun toUri(): Uri =
            BASE_URI.buildUpon().appendPath("artworks").appendPath(illustId).build()

        override fun toSearchQuery(): String = SEARCH_PREFIX + illustId
    }

    data class Series(val seriesId: String, val authorUserId: String? = null) : PixivTarget() {
        companion object : PixivTargetCompanion<Series> {
            override val SEARCH_PREFIX = "sid:"
            override fun fromSearchQueryId(id: String) = Series(id)
        }

        override fun toUri(): Uri {
            if (authorUserId == null) {
                TODO("Not yet implemented")
            }
            return BASE_URI.buildUpon().appendPath("user").appendPath(authorUserId)
                .appendPath("series").appendPath(seriesId).build()
        }

        override fun toSearchQuery(): String = SEARCH_PREFIX + seriesId
    }
}
