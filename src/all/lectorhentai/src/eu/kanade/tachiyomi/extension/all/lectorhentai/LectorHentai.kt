package eu.kanade.tachiyomi.extension.all.lectorhentai

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.annotation.Source
import keiyoushi.lib.unpacker.Unpacker
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParseDateTime
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Source
abstract class LectorHentai : MangaThemesia() {

    private val siteLang get() = LANGUAGES.getValue(lang)

    override val latestFilter get() = FilterList(OrderByFilter("", orderByFilterOptions, "latest"))

    override fun searchMangaUrl(page: Int, query: String) = baseUrl.toHttpUrl().newBuilder().apply {
        addPathSegments("tipo/all")
        if (query.isNotEmpty()) addQueryParameter("s", query)
        addQueryParameter("lenguaje", siteLang)
        addQueryParameter("page", page.toString())
    }

    // The site takes the type in the path
    override fun searchMangaUrl(page: Int, query: String, filters: FilterList) = super.searchMangaUrl(page, query, filters).apply {
        filters.firstInstanceOrNull<TypeFilter>()?.let { setPathSegment(1, it.selectedValue()).removeAllQueryParameters("type") }
    }

    // Some title attributes start with a space
    override fun searchMangaFromElement(element: Element) = super.searchMangaFromElement(element).apply {
        title = element.selectFirst(".tt")!!.text()
    }

    // Every language source gets the links, and only the one of the gallery's language keeps them
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val segments = url.pathSegments
        if (url.host != baseUrl.toHttpUrl().host || segments.size < 3 || segments[0] !in listOf("manga", "read")) return null
        val document = client.get("$baseUrl/manga/${segments[1]}/${segments[2]}", Fetch.DOCUMENT).asJsoup()
        if (document.selectFirst(".imptdt:contains(Idioma) i")?.text() != siteLang) return null
        return mangaDetailsParse(document).apply { initialized = true }
    }

    override val seriesGenreSelector = ".wd-full:has(b:contains(Generos)) a, .wd-full:has(b:contains(Tags)) a"

    override fun mangaDetailsParse(document: Document) = super.mangaDetailsParse(document).apply {
        artist = document.select(".wd-full:has(b:contains(Artista)) a").joinToString { it.text() }.ifEmpty { null }
        author = artist
        val rating = document.selectFirst("[class^=resultMedia]")?.text()?.let(RATING_REGEX::find)?.groupValues
        description = MarkdownDescription.Builder()
            .rating(rating?.get(1)?.toDoubleOrNull(), max = 5, votes = rating?.get(2)?.toIntOrNull())
            .info(document.selectFirst(".imptdt:contains(Tipo) a")?.text(), document.selectFirst(".imptdt:contains(Sección) a")?.text())
            .field("Revistas", document.select(".wd-full:has(b:contains(Revistas)) a").map { it.text() })
            .build()
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
    }

    override fun chapterListParse(document: Document) = listOf(
        SChapter.create().apply {
            setUrlWithoutDomain(document.selectFirst("a.leer")!!.absUrl("href"))
            name = "Chapter"
            date_upload = DATE_FORMAT.tryParseDateTime(document.selectFirst(".fmed:contains(Publicado) time")?.attr("datetime"), ZONE)
        },
    )

    // The reader decrypts its page list with a key given next to it, in a packed script
    override fun pageListParse(document: Document): List<Page> {
        val script = document.select("script").map { it.data() }.first { "eval(function(p,a,c,k,e,d)" in it && "ReaderBlob" in it }
        val blob = Unpacker.unpack(script).substringAfter("ReaderBlob.start(").substringBefore(");").parseAs<ReaderBlobDto>()
        return blob.decrypt().parseAs<ReaderPagesDto>().pages.mapIndexed { i, url -> Page(i, imageUrl = url) }
    }

    override val typeFilterOptions = arrayOf(
        intl["type_filter_option_all"] to "all",
        "Hentai" to "hentai",
        "Yaoi" to "yaoi",
        "Yuri" to "yuri",
    )

    override suspend fun fetchFilterData() = parseGenres(client.get("$baseUrl/tipo/all", Fetch.DOCUMENT).asJsoup()).toJsonElement()

    // The site has no author, year or status filter
    override fun getFilterList(data: JsonElement?) = FilterList(
        super.getFilterList(data).filterNot { it is AuthorFilter || it is YearFilter || it is StatusFilter },
    )

    private companion object {
        // The site's own names, the Russian one with a Latin P
        val LANGUAGES = mapOf("es" to "Español", "en" to "Ingles", "pt-BR" to "Português", "fr" to "Français", "ru" to "Pусский")
        val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val ZONE: ZoneId = ZoneId.of("Europe/Madrid")
        val RATING_REGEX = Regex("""([\d.]+)/5 \((\d+)""")
    }
}
