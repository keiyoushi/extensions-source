package eu.kanade.tachiyomi.extension.all.manga18fx

import eu.kanade.tachiyomi.multisrc.yiimadara.YiiMadara
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.utils.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class Manga18fx : YiiMadara() {
    override val directoryPath: String? = null
    override val completedPath: String? = null
    override val rawPath = "manhwa-raw"

    override val sortOptions = listOf(
        "Default" to "",
        "Newest" to "latest",
        "Rating" to "rating",
        "Most views" to "views",
    )

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$baseUrl/hot-manga".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .build()
        return mangaListParse(client.get(url).asJsoup())
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = mangaListParse(client.get(listUrl("page", page).build()).asJsoup())

    // Legacy Madara chapter URLs are absolute and end with ?style=list
    override fun getChapterUrl(chapter: SChapter): String = if (chapter.url.startsWith("http")) chapter.url else super.getChapterUrl(chapter)
}
