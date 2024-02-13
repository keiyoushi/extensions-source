package eu.kanade.tachiyomi.extension.en.omegascans

import eu.kanade.tachiyomi.multisrc.heancms.Genre
import eu.kanade.tachiyomi.multisrc.heancms.HeanCms
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import okhttp3.HttpUrl.Companion.toHttpUrl

class OmegaScans : HeanCms("Omega Scans", "https://omegascans.org", "en") {

    override val client = super.client.newBuilder()
        .rateLimitHost(apiUrl.toHttpUrl(), 1)
        .build()

    override val useNewQueryEndpoint = true

    // Site changed from MangaThemesia to HeanCms.
    override val versionId = 2

    override val coverPath = ""

    override fun getGenreList() = listOf(
        Genre("Romance", 1),
        Genre("Drama", 2),
        Genre("Fantasy", 3),
        Genre("Hardcore", 4),
        Genre("SM", 5),
        Genre("Harem", 8),
        Genre("Hypnosis", 9),
        Genre("Novel Adaptation", 10),
        Genre("Netori", 11),
        Genre("Netorare", 12),
        Genre("Isekai", 13),
        Genre("Yuri", 14),
        Genre("MILF", 16),
        Genre("Office", 17),
        Genre("Short Story", 18),
        Genre("Comedy", 19),
        Genre("Campus", 20),
        Genre("Crime", 21),
        Genre("Revenge", 22),
        Genre("Supernatural", 23),
        Genre("Action", 24),
        Genre("Military", 25),
        Genre("Ability", 26),
        Genre("Cohabitation", 27),
        Genre("Training", 28),
    )
}
