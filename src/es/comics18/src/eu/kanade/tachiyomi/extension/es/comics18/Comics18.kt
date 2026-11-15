package eu.kanade.tachiyomi.extension.es.comics18

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import kotlin.time.Instant

@Source
abstract class Comics18 : KeiSource() {

    override val supportsLatest = false

    override suspend fun getPopularManga(page: Int): MangasPage = mangaListParse(client.get(listUrl("", page), Fetch.DOCUMENT).asJsoup())

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val category = filters.firstInstanceOrNull<CategoryFilter>()?.selectedPath()
        val url = when {
            query.isNotBlank() -> listUrl("", page).toHttpUrl().newBuilder().addQueryParameter("s", query).build().toString()
            category != null -> listUrl("$category/", page)
            else -> listUrl("", page)
        }
        return mangaListParse(client.get(url, Fetch.DOCUMENT).asJsoup())
    }

    private fun listUrl(path: String, page: Int) = if (page > 1) "$baseUrl/${path}page/$page/" else "$baseUrl/$path"

    // Only the home and category pages mark VIP comics (Patreon only, a preview for everyone else) with a class
    private fun mangaListParse(document: Document): MangasPage {
        val mangas = document.select("article:not(.tag-vip)").mapNotNull { article ->
            val link = article.selectFirst("a[href]") ?: return@mapNotNull null
            SManga.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                title = article.selectFirst("h3")?.text() ?: return@mapNotNull null
                thumbnail_url = article.selectFirst("img")?.absUrl("src")
            }
        }
        return MangasPage(mangas, document.selectFirst("a.next.page-numbers") != null)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        if (document.selectFirst(".entry-content p > img") == null) return null
        return mangaDetailsParse(document).apply { setUrlWithoutDomain(url.toString()) }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        val chapter = SChapter.create().apply {
            url = manga.url
            name = if (document.isVipPreview()) "Vista previa (VIP)" else "Capítulo único"
            date_upload = Instant.tryParse(document.selectFirst("meta[property='article:published_time']")?.attr("content"))
        }
        return SMangaUpdate(mangaDetailsParse(document).apply { url = manga.url }, listOf(chapter))
    }

    private fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.selectFirst("h1.title")!!.text()
        author = ARTIST_REGEX.find(title)?.groupValues?.get(1)
        thumbnail_url = document.selectFirst("meta[property='og:image']")?.attr("content")
        genre = document.select(".st-post-tags a").eachText().joinToString()
        if (document.isVipPreview()) description = "Cómic VIP: solo se muestra la vista previa gratuita."
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
    }

    private fun Document.isVipPreview() = selectFirst(".swpm-partial-protection-msg") != null

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        .select(".entry-content p > img")
        .mapIndexed { index, img -> Page(index, imageUrl = img.absUrl("src")) }

    override fun getFilterList(data: JsonElement?) = getFilters()

    companion object {
        private val ARTIST_REGEX = Regex("""\[([^\]]+)]\s*$""")
    }
}
