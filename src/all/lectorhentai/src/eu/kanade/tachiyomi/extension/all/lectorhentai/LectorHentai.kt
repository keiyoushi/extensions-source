package eu.kanade.tachiyomi.extension.all.lectorhentai

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.annotation.Source
import keiyoushi.lib.unpacker.Unpacker
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParseDateTime
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Source
abstract class LectorHentai : KeiSource() {

    private val siteLang get() = LANGUAGES.getValue(lang)

    override suspend fun getPopularManga(page: Int): MangasPage = mangaList(listUrl(page, "all", "popular"))

    override suspend fun getLatestUpdates(page: Int): MangasPage = mangaList(listUrl(page, "all", "latest"))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = listUrl(page, filters.firstInstance<TypeFilter>().value, filters.firstInstance<SortFilter>().value).newBuilder()
            .addQueryParameter("s", query.trim())
            .apply { filters.firstInstance<GenreFilter>().state.filter { it.state }.forEach { addQueryParameter("genre[]", it.name) } }
            .build()
        return mangaList(url)
    }

    // Every language source gets the links, and only the one of the gallery's language keeps them
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val segments = url.pathSegments
        if (url.host != baseUrl.toHttpUrl().host || segments.size < 3 || segments[0] !in listOf("manga", "read")) return null
        val manga = SManga.create().apply { this.url = "/manga/${segments[1]}/${segments[2]}" }
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        if (document.selectFirst(".imptdt:contains(Idioma) i")?.text() != siteLang) return null
        return document.toSManga().apply {
            this.url = manga.url
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
        val chapter = SChapter.create().apply {
            setUrlWithoutDomain(document.selectFirst("a.leer")!!.absUrl("href"))
            name = "Chapter"
            date_upload = DATE_FORMAT.tryParseDateTime(document.selectFirst(".fmed:contains(Publicado) time")?.attr("datetime"), ZONE)
        }
        return SMangaUpdate(document.toSManga().apply { url = manga.url }, listOf(chapter))
    }

    // The reader decrypts its page list with a key given next to it, in a packed script
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        val script = document.select("script").map { it.data() }.first { "eval(function(p,a,c,k,e,d)" in it && "ReaderBlob" in it }
        val blob = Unpacker.unpack(script).substringAfter("ReaderBlob.start(").substringBefore(");").parseAs<ReaderBlobDto>()
        return blob.decrypt().parseAs<ReaderPagesDto>().pages.mapIndexed { i, url -> Page(i, imageUrl = url) }
    }

    override fun getFilterList(data: JsonElement?): FilterList = getFilters()

    private fun listUrl(page: Int, type: String, order: String): HttpUrl = baseUrl.toHttpUrl().newBuilder()
        .addPathSegment("tipo")
        .addPathSegment(type)
        .addQueryParameter("lenguaje", siteLang)
        .addQueryParameter("order", order)
        .addQueryParameter("page", page.toString())
        .build()

    private suspend fun mangaList(url: HttpUrl): MangasPage {
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select(".listupd .bsx > a").map { link ->
            SManga.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                title = link.selectFirst(".tt")!!.text()
                thumbnail_url = link.selectFirst("img")?.absUrl("src")
            }
        }
        return MangasPage(mangas, document.selectFirst(".hpage a.r") != null)
    }

    private fun Document.toSManga() = SManga.create().apply {
        title = info("Título")!!.text()
        thumbnail_url = selectFirst(".thumb img")?.absUrl("src")
        artist = info("Artista")?.select("a")?.joinToString { it.text() }?.ifEmpty { null }
        author = artist
        genre = listOfNotNull(info("Generos"), info("Tags")).flatMap { it.select("a") }.joinToString { it.text() }.ifEmpty { null }
        val rating = selectFirst("[class^=resultMedia]")?.text()?.let(RATING_REGEX::find)?.groupValues
        description = MarkdownDescription.Builder()
            .rating(rating?.get(1)?.toDoubleOrNull(), max = 5, votes = rating?.get(2)?.toIntOrNull())
            .info(selectFirst(".imptdt:contains(Tipo) a")?.text(), selectFirst(".imptdt:contains(Sección) a")?.text())
            .field("Revistas", info("Revistas")?.select("a")?.map { it.text() }.orEmpty())
            .build()
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
    }

    private fun Document.info(label: String) = selectFirst(".infox .wd-full:has(b:contains($label)) > span")

    private companion object {
        // The site's own names, the Russian one with a Latin P
        val LANGUAGES = mapOf("es" to "Español", "en" to "Ingles", "pt-BR" to "Português", "fr" to "Français", "ru" to "Pусский")
        val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val ZONE: ZoneId = ZoneId.of("Europe/Madrid")
        val RATING_REGEX = Regex("""([\d.]+)/5 \((\d+)""")
    }
}
