package eu.kanade.tachiyomi.extension.it.juinjutsuteamreader

import eu.kanade.tachiyomi.multisrc.foolslide.FoolSlide
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element

class JuinJutsuTeamReader : FoolSlide("Juin Jutsu Team Reader", "https://www.juinjutsureader.ovh", "it") {
    override val supportsLatest = false

    private val seenMangaUrls = mutableSetOf<String>()

    private val chapterNumberRegex = Regex("""/(\d+(?:\.\d+)?)/(?:\d+/)?$""")

    override fun popularMangaRequest(page: Int): Request {
        if (page == 1) seenMangaUrls.clear()
        return GET("$baseUrl/latest/$page/", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val mp = super.popularMangaParse(response)
        val newMangas = mp.mangas.filter { seenMangaUrls.add(it.url) }
        val hasNextPage = newMangas.isNotEmpty() && mp.hasNextPage
        return MangasPage(newMangas, hasNextPage)
    }

    override fun popularMangaNextPageSelector() = "div.next a"

    override fun chapterListSelector() = "div.group_comic div.element"

    override fun chapterFromElement(element: Element): SChapter = super.chapterFromElement(element).apply {
        chapterNumberRegex.find(url)?.groupValues?.get(1)?.toFloatOrNull()?.let {
            chapter_number = it
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> = super.chapterListParse(response).filter { chapter ->
        !chapter.name.trim().matches(Regex("""\d+"""))
    }

    override fun chapterListRequest(manga: SManga): Request = super.chapterListRequest(manga).newBuilder()
        .removeHeader("If-Modified-Since")
        .removeHeader("If-None-Match")
        .build()

    override fun pageListRequest(chapter: SChapter): Request = super.pageListRequest(chapter).newBuilder()
        .removeHeader("If-Modified-Since")
        .removeHeader("If-None-Match")
        .build()
}
