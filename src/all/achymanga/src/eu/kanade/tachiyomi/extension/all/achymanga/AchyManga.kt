package eu.kanade.tachiyomi.extension.all.achymanga

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import org.jsoup.nodes.Document
import java.io.IOException
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

@Source
abstract class AchyManga : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor(::descrambleImage)

    // The site only ranks its Spanish series
    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = if (lang == "es") {
            baseUrl.toHttpUrl().newBuilder().addPathSegment("populares").addQueryParameter("adulto", "0").build()
        } else {
            catalogueUrl(adult = false).build()
        }
        return parseSeriesList(client.get(url, Fetch.DOCUMENT).asJsoup())
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = catalogueUrl(adult = false).addQueryParameter("orden", "actualizadas").build()
        return parseSeriesList(client.get(url, Fetch.DOCUMENT).asJsoup())
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = catalogueUrl(adult = filters.firstInstanceOrNull<AdultFilter>()?.state == true).apply {
            if (query.isNotBlank()) addQueryParameter("q", query.trim())
            filters.filterIsInstance<SelectFilter>().forEach { it.addTo(this) }
        }.build()
        return parseSeriesList(client.get(url, Fetch.DOCUMENT).asJsoup())
    }

    // The +18 series have their own catalogue
    private fun catalogueUrl(adult: Boolean) = baseUrl.toHttpUrl().newBuilder()
        .addPathSegment("catalogo")
        .addQueryParameter("idioma", lang)
        .addQueryParameter("adulto", if (adult) "1" else "0")

    // Every list fits on one page; novels are text, so they are left out
    private fun parseSeriesList(document: Document): MangasPage {
        val mangas = document.select("a[data-series-card]")
            .filter { it.attr("data-tipo") != "novela" && it.attr("data-idioma") == lang }
            .map { card ->
                SManga.create().apply {
                    setUrlWithoutDomain(card.absUrl("href"))
                    title = card.attrOrNull("data-title")!!
                    thumbnail_url = card.attrOrNull("data-cover")
                }
            }
        return MangasPage(mangas, false)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host) return null
        val (section, id, slug) = url.pathSegments.takeIf { it.size >= 3 } ?: return null
        if (section != "manga" || id.isEmpty() || slug.isEmpty()) return null

        val seriesUrl = baseUrl.toHttpUrl().newBuilder().addPathSegment("manga").addPathSegment(id).addPathSegment(slug).build()
        val document = client.get(seriesUrl, Fetch.DOCUMENT).asJsoup()
        if (document.selectFirst("html")!!.attr("lang") != lang || document.isNovel()) return null

        return parseDetails(document).apply {
            setUrlWithoutDomain(document.location())
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
        val chapterList = document.select("#grid-caps a[data-num]").map { link ->
            SChapter.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                name = link.selectFirst("span")!!.text()
                chapter_number = link.attr("data-num").toFloatOrNull() ?: -1f
                date_upload = parseRelativeDate(link.selectFirst("span.muted")?.text())
            }
        }
        return SMangaUpdate(parseDetails(document).apply { url = manga.url }, chapterList)
    }

    private fun parseDetails(document: Document) = SManga.create().apply {
        title = document.selectFirst("h1")!!.text()
        author = document.selectFirst("h1 + p")?.text()
        genre = document.select("a.chip[href*=genero=]").joinToString { it.text() }.ifEmpty { null }
        status = when (document.selectFirst(".chip-active")?.text()?.lowercase()) {
            "en curso", "ongoing" -> SManga.ONGOING
            "finalizada", "completed" -> SManga.COMPLETED
            "en pausa", "hiatus" -> SManga.ON_HIATUS
            "cancelado", "cancelled" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        thumbnail_url = document.selectFirst("main img")?.absUrl("src")
        description = MarkdownDescription.Builder()
            .description(document.selectFirst("#sinopsis"))
            .altTitles(document.selectFirst("h1 ~ p[title]")?.text()?.split(" · ").orEmpty().filterNot { it.equals(title, ignoreCase = true) })
            .build()
    }

    private fun Document.isNovel() = selectFirst(".chip-active")!!.siblingElements().any { it.text().startsWith("Novel") }

    private fun parseRelativeDate(text: String?): Long {
        val (amount, unit) = RELATIVE_DATE_REGEX.find(text ?: return 0L)?.destructured ?: return 0L
        val chronoUnit = when (unit.lowercase().first()) {
            's' -> if (unit.startsWith("sem", ignoreCase = true)) ChronoUnit.WEEKS else ChronoUnit.SECONDS
            'm' -> if (unit.startsWith("mes", ignoreCase = true) || unit.startsWith("mo", ignoreCase = true)) ChronoUnit.MONTHS else ChronoUnit.MINUTES
            'h' -> ChronoUnit.HOURS
            'd' -> ChronoUnit.DAYS
            'w' -> ChronoUnit.WEEKS
            'a', 'y' -> ChronoUnit.YEARS
            else -> return 0L
        }
        return ZonedDateTime.now().minus(amount.toLong(), chronoUnit).toInstant().toEpochMilli()
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        val reader = document.select("script:not([src])").first { it.data().contains(MANIFEST_KEY_REGEX) }.data()
        val key = MANIFEST_KEY_REGEX.find(reader)!!.groupValues[1]
        val permutation = PERMUTATION_REGEX.find(reader)!!.groupValues[1].replace(" ", "")

        return document.selectFirst("#datos-manifiesto-d")!!.data().parseAs<ManifestDto>()
            .pageUrls(key.encodeToByteArray())
            .mapIndexed { i, url ->
                val imageUrl = url.toHttpUrl()
                val scrambled = imageUrl.queryParameter("s") == "1" || imageUrl.encodedPath.endsWith("_s.webp")
                Page(i, imageUrl = if (scrambled) imageUrl.newBuilder().fragment(permutation).build().toString() else url)
            }
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = client.get(catalogueUrl(adult = false).build(), Fetch.DOCUMENT).asJsoup()
        .select("form#filtros select:not([name=idioma])")
        .map { select ->
            SelectDto(
                name = select.attr("name"),
                label = select.attr("aria-label"),
                options = select.select("option").filter { it.attr("value") != "novela" }.map { it.attr("value") to it.text() },
            )
        }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?) = getFilters(data?.parseAs<List<SelectDto>>())

    // The reader cuts scrambled pages into a 4x4 grid of tiles (sizes rounded down to 8 px) and keeps the margins in place
    private fun descrambleImage(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val permutation = request.url.fragment?.split(',')?.map(String::toInt) ?: return chain.proceed(request)

        val response = chain.proceed(request)
        if (!response.isSuccessful) return response

        val image = response.use { BitmapFactory.decodeStream(it.body.byteStream()) }
            ?: throw IOException("Failed to decode image")
        val result = image.copy(Bitmap.Config.ARGB_8888, true)
        val tileWidth = TILE_STEP * (image.width / (GRID * TILE_STEP))
        val tileHeight = TILE_STEP * (image.height / (GRID * TILE_STEP))
        if (tileWidth > 0 && tileHeight > 0) {
            val pixels = IntArray(tileWidth * tileHeight)
            permutation.forEachIndexed { tile, original ->
                image.getPixels(pixels, 0, tileWidth, tile % GRID * tileWidth, tile / GRID * tileHeight, tileWidth, tileHeight)
                result.setPixels(pixels, 0, tileWidth, original % GRID * tileWidth, original / GRID * tileHeight, tileWidth, tileHeight)
            }
        }
        image.recycle()

        val output = Buffer()
        result.compress(Bitmap.CompressFormat.JPEG, 90, output.outputStream())
        result.recycle()
        return response.newBuilder()
            .removeHeader("Content-Length")
            .removeHeader("Content-Type")
            .body(output.asResponseBody(JPEG, output.size))
            .build()
    }

    companion object {
        private val MANIFEST_KEY_REGEX = Regex("""CLAVE_XOR\s*=\s*'([^']+)'""")
        private val PERMUTATION_REGEX = Regex("""PERMUTACION_16\s*=\s*\[([\d,\s]+)]""")
        private val RELATIVE_DATE_REGEX = Regex("""(\d+)\s*(\p{L}+)""")
        private val JPEG = "image/jpeg".toMediaType()
        private const val GRID = 4
        private const val TILE_STEP = 8
    }
}
