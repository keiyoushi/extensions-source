package eu.kanade.tachiyomi.extension.tr.webtoonoku

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import kotlin.time.Instant

@Source
abstract class WebtoonOku : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(2)
    }

    override suspend fun getPopularManga(page: Int): MangasPage = listPage(page, "popular")

    override suspend fun getLatestUpdates(page: Int): MangasPage = listPage(page, "latest")

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            return listPage(page, "latest") { addQueryParameter("s", query.trim()) }
        }
        return listPage(page, filters.firstInstance<OrderFilter>().value) {
            filters.filterIsInstance<UriPartGroup>().forEach { group ->
                group.selected.forEach { addQueryParameter(group.param, it) }
            }
        }
    }

    private suspend fun listPage(
        page: Int,
        order: String,
        block: HttpUrl.Builder.() -> Unit = {},
    ): MangasPage {
        val listUrl = "$baseUrl/manhwa/".toHttpUrl().newBuilder().apply {
            if (page > 1) addPathSegments("page/$page/")
            addQueryParameter("order", order)
            block()
        }.build()
        val document = client.get(listUrl).asJsoup()
        val mangas = document.select("article.manga-item").map { element ->
            SManga.create().apply {
                val link = element.selectFirst(".manga-title a")!!
                url = link.absUrl("href").toHttpUrl().pathSegments[1]
                title = link.text()
                thumbnail_url = element.selectFirst(".manga-thumb img")?.absUrl("data-src")
            }
        }
        return MangasPage(mangas, document.selectFirst("a.next.page-numbers") != null)
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/manhwa/${manga.url}/"

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        val slug = when {
            segments.size == 2 && segments[0] == "manhwa" -> segments[1]
            // chapter pages live at the site root as <slug>-chapter-<n>
            segments.size == 1 && "-chapter-" in segments[0] -> segments[0].substringBeforeLast("-chapter-")
            else -> return null
        }
        val document = client.get("$baseUrl/manhwa/$slug/").asJsoup()
        return mangaDetails(document).apply { this.url = slug }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        return SMangaUpdate(mangaDetails(document), chapterList(document))
    }

    private fun mangaDetails(document: Document) = SManga.create().apply {
        title = document.selectFirst("h1.manga-title-main")!!.text()
        thumbnail_url = document.selectFirst(".manga-cover-img")?.absUrl("data-src")
        author = document.infoValue("Yazar")
        artist = document.infoValue("Sanatçı")
        genre = document.select(".manga-genres a").joinToString { it.text() }
        description = document.selectFirst(".synopsis-content")?.wholeText()?.trim()
        status = when (document.infoValue("Durum")?.lowercase()) {
            "devam ediyor" -> SManga.ONGOING
            "tamamlandı" -> SManga.COMPLETED
            "ara verildi" -> SManga.ON_HIATUS
            "bırakıldı" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }

    // the site prints "-" for unknown author/artist
    private fun Document.infoValue(label: String) = selectFirst(".info-item:has(.info-label:containsOwn($label)) .info-value")?.text()?.takeUnless { it == "-" }

    private fun chapterList(document: Document) = document.select("a.chapter-box")
        .map { element ->
            SChapter.create().apply {
                url = element.absUrl("href").toHttpUrl().pathSegments[0]
                name = element.selectFirst(".chapter-num")!!.ownText()
                chapter_number = element.attr("data-chapter").toFloatOrNull() ?: -1f
                date_upload = Instant.tryParse(element.selectFirst(".chapter-date")?.attr("data-time-iso"))
            }
        }
        .distinctBy { it.url }

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/${chapter.url}/"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        return document.select("#readerImages img[data-src]").mapIndexed { index, img ->
            Page(index, imageUrl = img.absUrl("data-src"))
        }
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("Filtreler yalnızca arama metni boşken uygulanır"),
        OrderFilter(),
        StatusFilter(),
        TypeFilter(),
        GenreFilter(),
    )
}
