package eu.kanade.tachiyomi.extension.de.onepiecetube

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

@Source
abstract class OnePieceTube : KeiSource() {

    // The site has no list of updates, only its lists
    override val supportsLatest = false

    override suspend fun getPopularManga(page: Int) = MangasPage(fetchLists(), false)

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList) = MangasPage(fetchLists().filter { it.title.contains(query.trim(), ignoreCase = true) }, false)

    // One entry per list of the site's menu, the spin-off list split by series
    private suspend fun fetchLists(): List<SManga> {
        val document = client.get("$baseUrl/manga/$SPIN_OFF$LIST_SUFFIX", Fetch.DOCUMENT).asJsoup()
        val data = document.pageData<ListDto>()!!
        return document.select("a.dropdown-item[href$=$LIST_SUFFIX]").flatMap { link ->
            val slug = link.absUrl("href").toHttpUrl().pathSegments.last().removeSuffix(LIST_SUFFIX)
            if (slug == SPIN_OFF) {
                data.specials.mapNotNull { special ->
                    val series = data.entries.firstOrNull { it.specialsId == special.id }?.series ?: return@mapNotNull null
                    SManga.create().apply {
                        url = "/manga/$SPIN_OFF/$series"
                        title = special.name.trim()
                    }
                }
            } else {
                listOf(
                    SManga.create().apply {
                        url = "/manga/$slug$LIST_SUFFIX"
                        title = "$SERIES ${link.text()}"
                    },
                )
            }
        }
    }

    // A spin-off series has no page of its own: it is a part of the spin-off list
    override fun getMangaUrl(manga: SManga): String {
        val path = manga.url.split('/')
        if (path.size <= 3) return super.getMangaUrl(manga)
        return baseUrl.toHttpUrl().newBuilder().addPathSegments("manga/${path[2]}$LIST_SUFFIX").build().toString()
    }

    // Details and chapters are on the list page, which also lists entries without pages
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        val data = document.pageData<ListDto>()!!
        val series = manga.url.split('/').getOrNull(3)
        val entries = data.entries.filter { it.isAvailable && (series == null || it.series == series) }
        // The spin-off list describes each series in its own paragraph
        val html = data.category.description?.let { text ->
            if (series == null) text else text.split('●').firstOrNull { "<b>${manga.title}</b>" in it } ?: text
        }
        val details = SManga.create().apply {
            url = manga.url
            title = manga.title
            description = MarkdownDescription.Builder()
                .description(html?.let { Jsoup.parseBodyFragment(it, baseUrl).body() })
                .field("Erstausgabe", data.category.published?.trim())
                .build()
            status = when (document.selectFirst("h6:containsOwn(Status) + p")?.text()) {
                "laufend" -> SManga.ONGOING
                else -> SManga.UNKNOWN
            }
            // First page of the oldest entry: the site has no covers
            thumbnail_url = if (fetchDetails) entries.minByOrNull { it.number }?.let { fetchFirstPage(it.href) } else manga.thumbnail_url
        }
        return SMangaUpdate(details, entries.map { it.toSChapter() })
    }

    private suspend fun fetchFirstPage(url: String): String? = client.get(url, Fetch.DOCUMENT).asJsoup().pageData<ReaderDto>()?.chapter?.pages?.firstOrNull()?.url

    // A chapter link opens its list, the spin-off list its series
    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        val path = url.pathSegments.filter(String::isNotEmpty)
        val knownHost = url.host == baseUrl.toHttpUrl().host || url.host in OLD_HOSTS
        if (!knownHost || path.size < 2 || path[0] != "manga") return MangasPage(emptyList(), false)
        val list = path[1].removeSuffix(LIST_SUFFIX)
        val series = path.getOrNull(2)
        val mangas = fetchLists().filter { manga ->
            val mangaPath = manga.url.split('/')
            if (mangaPath.size > 3) {
                mangaPath[2] == list && (path.size == 2 || mangaPath[3] == series)
            } else {
                mangaPath[2] == "$list$LIST_SUFFIX"
            }
        }
        return MangasPage(mangas, false)
    }

    // An entry that is no longer available only shows a notice
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val reader = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup().pageData<ReaderDto>() ?: return emptyList()
        return reader.chapter.pages.mapIndexed { i, page -> Page(i, imageUrl = page.url) }
    }

    private inline fun <reified T> Document.pageData(): T? = selectFirst("script:containsData(window.__data)")?.data()
        ?.substringAfter('=')
        ?.trim()
        ?.removeSuffix(";")
        ?.parseAs<T>()

    companion object {
        private const val SERIES = "One Piece"
        private const val SPIN_OFF = "spin-off"
        private const val LIST_SUFFIX = "-mangaliste"
        private val OLD_HOSTS = setOf("onepiece-tube.com", "www.onepiece-tube.com")
    }
}
