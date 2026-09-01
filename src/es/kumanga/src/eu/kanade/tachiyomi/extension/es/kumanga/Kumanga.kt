package eu.kanade.tachiyomi.extension.es.kumanga

import android.util.Base64
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.lib.randomua.addRandomUAPreference
import keiyoushi.lib.randomua.setRandomUserAgent
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.injectLazy
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

class Kumanga :
    HttpSource(),
    ConfigurableSource {

    override val name = "Kumanga"

    override val baseUrl = "https://www.kumanga.com"

    private val apiUrl = "$baseUrl/backend/ajax/searchengine2.php"

    override val lang = "es"

    override val supportsLatest = true

    private val json: Json by injectLazy()

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(2, 1, TimeUnit.SECONDS)
        .build()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .setRandomUserAgent()

    override fun popularMangaRequest(page: Int): Request {
        val form = FormBody.Builder()
            .add("page", page.toString())
            .add("perPage", CONTENT_PER_PAGE.toString())
            .add("retrieveCategories", "true")
            .add("retrieveAuthors", "true")
            .add("contentType", "manga")
            .build()

        return POST(apiUrl, headers, form)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val dto = json.decodeFromString<KumangaSearchResponseDto>(response.body.string())
        val mangas = dto.contents.map { it.toSManga(baseUrl) }
        return MangasPage(mangas, dto.contents.size >= CONTENT_PER_PAGE)
    }

    override fun latestUpdatesRequest(page: Int): Request = GET(baseUrl, headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("a[href*='manga/']")
            .filter { element ->
                val href = element.attr("href")
                href.contains("/manga/") && !href.contains("/capitulo/") && !href.contains("/c/") && !href.contains("/leer/") && !href.contains("mangalist")
            }
            .distinctBy { it.attr("href") }
            .mapNotNull { element ->
                val rawHref = element.attr("href")
                val href = if (rawHref.startsWith("http")) {
                    rawHref.removePrefix(baseUrl)
                } else if (rawHref.startsWith("/")) {
                    rawHref
                } else {
                    "/$rawHref"
                }
                val title = element.selectFirst("img")?.attr("alt")?.trim()
                    ?: element.text().trim()
                if (title.isEmpty()) return@mapNotNull null
                val id = href.substringAfter("/manga/").substringBefore("/")
                SManga.create().apply {
                    this.url = href
                    this.title = title
                    this.thumbnail_url = id.toIntOrNull()?.let { "https://static.kumanga.com/manga/${it / 1000}/$it.jpg" }
                }
            }

        return MangasPage(mangas, false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val form = FormBody.Builder()
            .add("page", page.toString())
            .add("perPage", CONTENT_PER_PAGE.toString())
            .add("retrieveCategories", "true")
            .add("retrieveAuthors", "true")
            .add("contentType", "manga")

        if (query.isNotBlank()) {
            form.add("keywords", query)
        }

        filters.forEach { filter ->
            when (filter) {
                is TypeFilter -> {
                    filter.state
                        .filter { it.state }
                        .forEach { form.add("type_filter[]", it.id) }
                }
                is StatusFilter -> {
                    if (filter.state != 0) {
                        form.add("status_filter[]", filter.toUriPart())
                    }
                }
                is GenreFilter -> {
                    filter.state
                        .filter { it.state }
                        .forEach { form.add("category_filter[]", it.id) }
                }
                else -> {}
            }
        }

        return POST(apiUrl, headers, form.build())
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun mangaDetailsRequest(manga: SManga): Request = GET(baseUrl + manga.url, headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("h1")?.text()?.trim()
                ?: document.selectFirst("meta[property='og:title']")?.attr("content")?.removeSuffix(" - KuManga")?.trim()
                ?: ""
            thumbnail_url = document.selectFirst("img.lazy-loaded, img[data-src*='/manga/'], img[src*='/manga/']")?.let {
                it.attr("abs:data-src").ifEmpty { it.attr("abs:src") }
            } ?: document.selectFirst("meta[property='og:image']")?.attr("content")
            description = document.selectFirst("meta[property='og:description']")?.attr("content")
                ?: document.selectFirst(".panel-body p, .description")?.text()?.trim()
            genre = document.select("a[href*='categories='], a[href*='genre=']")
                .joinToString { it.text().trim() }
            val bodyText = document.text()
            status = when {
                bodyText.contains("Activo", ignoreCase = true) -> SManga.ONGOING
                bodyText.contains("Finalizado", ignoreCase = true) -> SManga.COMPLETED
                bodyText.contains("Inconcluso", ignoreCase = true) -> SManga.ON_HIATUS
                else -> SManga.UNKNOWN
            }
            initialized = true
        }
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}"

    override fun chapterListRequest(manga: SManga): Request = GET(baseUrl + manga.url, headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chapterList = mutableListOf<SChapter>()
        val mangaUrl = response.request.url.encodedPath

        document.select("a[href*='/capitulo/'], a[href*='/manga/c/']").forEach { el ->
            val href = el.attr("href")
            val chNum = href.substringAfterLast("/").trim()
            val ch = SChapter.create().apply {
                url = if (href.startsWith("http")) href.removePrefix(baseUrl) else href
                name = "Capítulo $chNum"
                chapter_number = chNum.toFloatOrNull() ?: -1f
            }
            chapterList.add(ch)
        }

        val scriptContent = document.selectFirst("script:containsData(OTHER_CHAPTERS)")?.data()
        if (scriptContent != null) {
            val jsonStr = scriptContent.substringAfter("let OTHER_CHAPTERS =").substringBefore(";").trim()
            runCatching {
                val otherChapters = json.decodeFromString<List<KumangaOtherChapterDto>>(jsonStr)
                otherChapters.forEach { item ->
                    val num = item.NumCap ?: return@forEach
                    val ch = SChapter.create().apply {
                        url = "$mangaUrl/capitulo/$num"
                        name = "Capítulo $num"
                        chapter_number = num.toFloatOrNull() ?: -1f
                    }
                    chapterList.add(ch)
                }
            }
        }

        return chapterList.distinctBy { it.url }.sortedByDescending { it.chapter_number }
    }

    override fun pageListRequest(chapter: SChapter): Request = GET(baseUrl + chapter.url, headers)

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        val readerLink = document.selectFirst("a#leer[href], a[href*='manga/leer/']")?.attr("href")
        val finalDoc = if (readerLink != null) {
            val readerUrl = if (readerLink.startsWith("http")) readerLink else "$baseUrl/$readerLink".replace("//", "/")
            client.newCall(GET(readerUrl, headers)).execute().asJsoup()
        } else {
            document
        }

        val form = finalDoc.selectFirst("form#myForm[action]")
        val targetDoc = if (form != null) {
            val url = form.attr("action")
            val bodyBuilder = FormBody.Builder()
            form.select("input").forEach { input ->
                bodyBuilder.add(input.attr("name"), input.attr("value"))
            }
            client.newCall(POST(url, headers, bodyBuilder.build())).execute().asJsoup()
        } else {
            finalDoc
        }

        val rawPUrl = targetDoc.selectFirst("script:containsData(pUrl=)")?.data()
            ?.substringAfter("pUrl=")
            ?.substringBefore(";")
            ?.trim()
            ?.removeSurrounding("'", "\"")

        if (!rawPUrl.isNullOrEmpty()) {
            val decoded = decodeBase64(decodeBase64(rawPUrl).reversed().drop(10).dropLast(10))
            val imageList = json.decodeFromString<List<KumangaImageDto>>(decoded)
            return imageList.mapIndexedNotNull { index, dto ->
                val imgUrl = dto.imgURL ?: return@mapIndexedNotNull null
                val fullUrl = if (imgUrl.startsWith("http")) imgUrl else "$baseUrl/$imgUrl".replace("\\", "")
                Page(index, imageUrl = fullUrl)
            }
        }

        val images = targetDoc.select("img.km-img, #img-api-1, div.reading-content img, .page-break img")
        return images.mapIndexed { index, img ->
            val src = img.attr("abs:data-src").ifEmpty { img.attr("abs:src") }
            Page(index, imageUrl = src)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    private fun decodeBase64(encoded: String): String = Base64.decode(encoded, Base64.DEFAULT).toString(Charset.forName("UTF-8"))

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        screen.addRandomUAPreference()
    }

    override fun getFilterList() = FilterList(
        TypeFilter(getTypeList()),
        Filter.Separator(),
        StatusFilter(),
        Filter.Separator(),
        GenreFilter(getGenreList()),
    )

    private class Type(name: String, val id: String) : Filter.CheckBox(name)
    private class TypeFilter(types: List<Type>) : Filter.Group<Type>("Tipo", types)

    private class StatusFilter :
        UriPartFilter(
            "Estado",
            arrayOf(
                Pair("Todos", ""),
                Pair("Activo", "1"),
                Pair("Finalizado", "2"),
                Pair("Inconcluso", "3"),
            ),
        )

    private class Genre(name: String, val id: String) : Filter.CheckBox(name)
    private class GenreFilter(genres: List<Genre>) : Filter.Group<Genre>("Género", genres)

    open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    private fun getTypeList() = listOf(
        Type("Manga", "1"),
        Type("Manhwa", "2"),
        Type("Manhua", "3"),
        Type("One shot", "4"),
        Type("Doujinshi", "5"),
    )

    private fun getGenreList() = listOf(
        Genre("Acción", "1"),
        Genre("Artes marciales", "2"),
        Genre("Automóviles", "3"),
        Genre("Aventura", "4"),
        Genre("Boys Love", "49"),
        Genre("Ciencia Ficción", "5"),
        Genre("Comedia", "6"),
        Genre("Demonios", "7"),
        Genre("Deportes", "8"),
        Genre("Doujinshi", "9"),
        Genre("Drama", "10"),
        Genre("Ecchi", "11"),
        Genre("Espacio exterior", "12"),
        Genre("Fantasía", "13"),
        Genre("Gender bender", "14"),
        Genre("Girls Love", "50"),
        Genre("Gore", "46"),
        Genre("Harem", "15"),
        Genre("Hentai", "16"),
        Genre("Histórico", "17"),
        Genre("Horror", "18"),
        Genre("Isekai", "51"),
        Genre("Josei", "19"),
        Genre("Juegos", "20"),
        Genre("Locura", "21"),
        Genre("Magia", "22"),
        Genre("Mecha", "23"),
        Genre("Militar", "24"),
        Genre("Misterio", "25"),
        Genre("Música", "26"),
        Genre("Niños", "27"),
        Genre("Parodia", "28"),
        Genre("Policía", "29"),
        Genre("Psicológico", "30"),
        Genre("Recuentos de la vida", "31"),
        Genre("Reencarnación", "52"),
        Genre("Romance", "32"),
        Genre("Samurai", "33"),
        Genre("Seinen", "34"),
        Genre("Shoujo", "35"),
        Genre("Shoujo Ai", "36"),
        Genre("Shounen", "37"),
        Genre("Shounen Ai", "38"),
        Genre("Sobrenatural", "39"),
        Genre("Súperpoderes", "41"),
        Genre("Supervivencia", "53"),
        Genre("Suspenso", "40"),
        Genre("Terror", "47"),
        Genre("Tragedia", "48"),
        Genre("Vampiros", "42"),
        Genre("Vida escolar", "43"),
        Genre("Yaoi", "44"),
        Genre("Yuri", "45"),
    )

    companion object {
        private const val CONTENT_PER_PAGE = 24
    }
}
