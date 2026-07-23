package eu.kanade.tachiyomi.extension.it.juinjutsuteamreader

import eu.kanade.tachiyomi.multisrc.foolslide.FoolSlide
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Source
abstract class JuinJutsuTeamReader : FoolSlide() {
    override val supportsLatest = false

    private val seenMangaUrls = mutableSetOf<String>()

    private val numberOnlyRegex = Regex("""\d+""")

    private val chapterNumberRegex = Regex("""/(\d+(?:\.\d+)?)/(?:\d+/)?$""")

    override suspend fun getPopularManga(page: Int): MangasPage {
        if (page == 1) seenMangaUrls.clear()
        val request = GET("$baseUrl/latest/$page/", headers)
        val document = client.newCall(request).await().asJsoup()
        val mangas = document.select(popularMangaSelector()).map { popularMangaFromElement(it) }
        val newMangas = mangas.filter { seenMangaUrls.add(it.url) }
        val hasNextPage = newMangas.isNotEmpty() && popularMangaNextPageSelector().let { selector -> document.select(selector).first() != null }
        return MangasPage(newMangas, hasNextPage)
    }

    override fun popularMangaNextPageSelector() = "div.next a"

    override fun chapterListSelector() = "div.group_comic div.element"

    override fun chapterFromElement(element: Element): SChapter = super.chapterFromElement(element).apply {
        chapterNumberRegex.find(url)?.groupValues?.get(1)?.toFloatOrNull()?.let {
            chapter_number = it
        }
    }

    override suspend fun fetchMangaUpdate(manga: SManga, chapters: List<SChapter>, fetchDetails: Boolean, fetchChapters: Boolean): SMangaUpdate {
        val document: Document? = if (fetchDetails || fetchChapters) {
            val request = allowAdult(GET(baseUrl + manga.url, headers)).newBuilder()
                .removeHeader("If-Modified-Since")
                .removeHeader("If-None-Match")
                .build()
            client.newCall(request).await().asJsoup()
        } else {
            null
        }

        val sManga = if (fetchDetails) {
            mangaDetailsParse(document!!).apply { url = manga.url }
        } else {
            manga
        }

        val sChapters = if (fetchChapters) {
            document!!.select(chapterListSelector())
                .map { chapterFromElement(it) }
                .filter { !it.name.trim().matches(numberOnlyRegex) }
        } else {
            chapters
        }

        return SMangaUpdate(sManga, sChapters)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val request = allowAdult(GET(baseUrl + chapter.url, headers)).newBuilder()
            .removeHeader("If-Modified-Since")
            .removeHeader("If-None-Match")
            .build()
        val document = client.newCall(request).await().asJsoup()
        return pageListParse(document)
    }
}
