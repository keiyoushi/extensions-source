package eu.kanade.tachiyomi.extension.es.aiyumanga

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class AiYuManhua : ZeistManga(
    "AiYuManhua",
    "https://aiyumanhua.com",
    "es",
) {
    // Site moved from MangaThemesia to ZeistManga (again)
    override val versionId = 4

    override val client = super.client.newBuilder()
        .rateLimit(2, 1, TimeUnit.SECONDS)
        .build()

    override val popularMangaSelector = "div.PopularPosts article"
    override val popularMangaSelectorTitle = ".post-title a"
    override val popularMangaSelectorUrl = ".post-title a"

    override val hasFilters = false

    override val mangaDetailsSelector = "div.section#main div.widget:has(main)"
    override val mangaDetailsSelectorGenres = "dl > dd > a[rel=tag]"

    override val pageListSelector = "article#reader div.separator a, article#reader"
}
