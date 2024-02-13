package eu.kanade.tachiyomi.extension.en.templescan

import eu.kanade.tachiyomi.multisrc.heancms.Genre
import eu.kanade.tachiyomi.multisrc.heancms.HeanCms
import eu.kanade.tachiyomi.network.interceptor.rateLimit

class TempleScan : HeanCms("Temple Scan", "https://templescan.net", "en") {

    override val versionId = 3

    override val client = super.client.newBuilder()
        .rateLimit(1)
        .build()

    override val useNewQueryEndpoint = true
    override val coverPath = ""
    override val mangaSubDirectory = "comic"

    override fun getGenreList() = listOf(
        Genre("Drama", 1),
        Genre("Josei", 2),
        Genre("Romance", 3),
        Genre("Girls Love", 4),
        Genre("Reincarnation", 5),
        Genre("Fantasia", 6),
        Genre("Ecchi", 7),
        Genre("Adventure", 8),
        Genre("Boys Love", 9),
        Genre("School Life", 10),
        Genre("Action", 11),
        Genre("Military", 13),
        Genre("Comedy", 14),
        Genre("Apocalypse", 15),
    )
}
