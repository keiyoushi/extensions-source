package eu.kanade.tachiyomi.extension.es.kumanga

import android.util.Base64
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.textOrNull
import keiyoushi.utils.xor
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document

@Source
abstract class Kumanga : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(2)

    override suspend fun getPopularManga(page: Int) = getSearchMangaList(page, "", FilterList())

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val document = client.get(baseUrl, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select(".update_item h4 a").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                title = element.text()
                thumbnail_url = coverUrl(url.toMangaId())
            }
        }.distinctBy { it.url }
        return MangasPage(mangas, false)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("mangalist")
            filters.filterIsInstance<CheckBoxGroup>().forEach { group ->
                val values = group.state.filter { it.state }.joinToString("-") { it.value }
                if (values.isNotEmpty()) addQueryParameter(group.param, values)
            }
            if (query.isNotBlank()) addQueryParameter("keywords", query.trim())
            addQueryParameter("page", page.toString())
        }.build()
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select("li.km-li-crd").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.attr("onclick").substringAfter("'").substringBefore("'"))
                title = element.selectFirst(".km-title-p-card")!!.text()
                thumbnail_url = element.selectFirst("img.km-img-crd")?.absUrl("src")
            }
        }
        val hasNextPage = document.selectFirst(".section-nav-pagination a[href]:has(.fa-chevron-right)") != null
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host.removePrefix("www.")) return null
        if (url.pathSegments.firstOrNull() != "manga") return null
        val segment = url.pathSegments.getOrNull(1) ?: return null
        val mangaUrl = when {
            segment == "c" || segment == "leer" ->
                client.get(url, Fetch.DOCUMENT).asJsoup().selectFirst("a[href~=manga/\\d+/]")?.absUrl("href") ?: return null
            segment.toIntOrNull() != null -> "$baseUrl/manga/$segment"
            else -> return null
        }
        val document = client.get(mangaUrl, Fetch.DOCUMENT).asJsoup()
        return parseDetails(document).apply {
            setUrlWithoutDomain(document.selectFirst("meta[property=og:url]")!!.attr("content"))
            initialized = true
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        val details = parseDetails(document).apply { url = manga.url }
        return SMangaUpdate(details, parseChapters(document, manga.url.toMangaId()))
    }

    private fun parseDetails(document: Document) = SManga.create().apply {
        title = document.selectFirst("h1.media-name__main")!!.ownText()
        thumbnail_url = document.selectFirst("img.lazy-loaded")?.absUrl("data-src")
        description = buildString {
            document.selectFirst("#idesc")?.textOrNull()?.takeIf { it != "Sin descripción." }?.let(::append)
            document.selectFirst(".media-name__alt")?.textOrNull()?.let {
                if (isNotEmpty()) append("\n\n")
                append("Nombres alternativos: $it")
            }
        }.ifEmpty { null }
        genre = (listOfNotNull(document.infoValue("Tipo")) + document.select(".media-cats a").map { it.text() })
            .joinToString().ifEmpty { null }
        status = when (document.infoValue("Estado")?.lowercase()) {
            "en emisión" -> SManga.ONGOING
            "finalizado" -> SManga.COMPLETED
            "abandonado" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }

    private fun Document.infoValue(title: String) = selectFirst(".media-info-list__item:has(.media-info-list__title:contains($title)) .media-info-list__value")?.text()

    private fun parseChapters(document: Document, mangaId: Int): List<SChapter> {
        val listed = document.select(".media-chapters-list a.media-chapter__link").map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                name = buildString {
                    append(element.selectFirst(".media-chapter__name strong")!!.text())
                    element.selectFirst(".media-chapter__name .no_mobil")?.textOrNull()?.let { append(": ", it) }
                }
            }
        }
        val others = document.selectFirst("script:containsData(OTHER_CHAPTERS)")?.data()
            ?.let { OTHER_CHAPTERS_REGEX.find(it)!!.groupValues[1].parseAs<List<OtherChapterDto>>() }
            .orEmpty()
            .map { it.toSChapter(mangaId) }
        return listed + others
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val readerUrl = if (chapter.url.startsWith("/manga/leer/")) {
            getChapterUrl(chapter)
        } else {
            val upload = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
                .selectFirst("a#leer, a.readButton")!!.absUrl("href").toHttpUrl()
            // Upload pages (/manga/c/<id>) and the reader (/manga/leer/<id>) share the upload id
            baseUrl.toHttpUrl().newBuilder()
                .addPathSegments("manga/leer")
                .addPathSegment(upload.pathSegments.last())
                .build()
                .toString()
        }
        val document = client.get(readerUrl, Fetch.DOCUMENT).asJsoup()
        // Decoy page lists are declared inside blocks, out of the reader's scope
        val scripts = document.select("script").joinToString("\n") { it.data().withoutBlocks() }
        val name = RPICS_REGEX.find(scripts)!!.groupValues[1]
        val encoded = STRING_CONST_REGEX.findAll(scripts).first { it.groupValues[1] == name }.groupValues[2]
        val paths = Base64.decode(encoded, Base64.DEFAULT).xor(PAGES_KEY).decodeToString().parseAs<List<String>>()
        val base = readerUrl.toHttpUrl()
        return paths.mapIndexed { index, path -> Page(index, imageUrl = base.resolve(path)!!.toString()) }
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        TypeFilter(),
        StatusFilter(),
        GenreFilter(),
    )

    private fun String.toMangaId() = "$baseUrl$this".toHttpUrl().pathSegments[1].toInt()

    private tailrec fun String.withoutBlocks(): String {
        val outer = BLOCK_REGEX.replace(this, "")
        return if (outer == this) this else outer.withoutBlocks()
    }

    companion object {
        // XOR key from the site's kx.reader.js
        private val PAGES_KEY = "Jr54VwepF4La".toByteArray()
        private val OTHER_CHAPTERS_REGEX = Regex("""OTHER_CHAPTERS\s*=\s*(\[.*])\s*;""", RegexOption.DOT_MATCHES_ALL)
        private val RPICS_REGEX = Regex("""const\s+rpics\s*=\s*(\w+)\s*;""")
        private val STRING_CONST_REGEX = Regex("""const\s+(\w+)\s*=\s*"([^"]*)"""")
        private val BLOCK_REGEX = Regex("""\{[^{}]*\}""")
    }
}
