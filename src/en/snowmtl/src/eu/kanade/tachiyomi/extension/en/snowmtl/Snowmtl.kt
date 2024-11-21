package eu.kanade.tachiyomi.extension.en.snowmtl

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.absoluteValue

class Snowmtl : ParsedHttpSource() {

    override val name = "Snow Machine Translations"

    override val baseUrl = "https://snowmtl.ru"

    override val lang = "en"

    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(::composedImageIntercept)
        .build()

    private val captions = mutableMapOf<String, PageMapping>()

    // ============================== Popular ===============================

    private val popularFilter = FilterList(SelectionList("", listOf(Option(value = "views", query = "sort_by"))))

    override fun popularMangaRequest(page: Int) = searchMangaRequest(page, "", popularFilter)

    override fun popularMangaSelector() = searchMangaSelector()

    override fun popularMangaFromElement(element: Element) = searchMangaFromElement(element)

    override fun popularMangaNextPageSelector() = searchMangaNextPageSelector()

    // =============================== Latest ===============================

    private val latestFilter = FilterList(SelectionList("", listOf(Option(value = "recent", query = "sort_by"))))

    override fun latestUpdatesRequest(page: Int) = searchMangaRequest(page, "", latestFilter)

    override fun latestUpdatesSelector() = searchMangaSelector()

    override fun latestUpdatesFromElement(element: Element) = searchMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = searchMangaNextPageSelector()

    // =========================== Search ============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())

        if (query.isNotBlank()) {
            url.addQueryParameter("query", query)
        }

        filters.forEach { filter ->
            when (filter) {
                is SelectionList -> {
                    val selected = filter.selected()
                    if (selected.value.isBlank()) {
                        return@forEach
                    }
                    url.addQueryParameter(selected.query, selected.value)
                }
                is GenreList -> {
                    filter.state.filter(GenreCheckBox::state).forEach { genre ->
                        url.addQueryParameter("genres", genre.id)
                    }
                }
                else -> {}
            }
        }

        return GET(url.build(), headers)
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith(PREFIX_SEARCH)) {
            val slug = query.removePrefix(PREFIX_SEARCH)
            return fetchMangaDetails(SManga.create().apply { url = "/comics/$slug" }).map { manga ->
                MangasPage(listOf(manga), false)
            }
        }

        return super.fetchSearchManga(page, query, filters)
    }

    override fun searchMangaSelector() = "section h2 + div > div"

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        title = element.selectFirst("h3")!!.text()
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
    }

    override fun searchMangaNextPageSelector() = "a[href*=search]:contains(Next)"

    // =========================== Manga Details ============================
    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.selectFirst("h1")!!.text()
        description = document.selectFirst("p:has(span:contains(Synopsis))")?.ownText()
        author = document.selectFirst("p:has(span:contains(Author))")?.ownText()
        genre = document.select("h2:contains(Genres) + div span").joinToString { it.text() }
        thumbnail_url = document.selectFirst("img.object-cover")?.absUrl("src")
        document.selectFirst("p:has(span:contains(Status))")?.ownText()?.let {
            status = when (it.lowercase()) {
                "ongoing" -> SManga.ONGOING
                "complete" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }
        setUrlWithoutDomain(document.location())
    }

    // ============================== Chapters ==============================
    override fun chapterListSelector() = "section li"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        element.selectFirst("a")!!.let {
            name = it.ownText()
            setUrlWithoutDomain(it.absUrl("href"))
        }
        date_upload = parseChapterDate(element.selectFirst("span")?.text())
    }

    // =============================== Pages ================================

    override fun pageListParse(document: Document): List<Page> {
        captions.clear()

        val jsonArray = document.selectFirst("div#json-data")?.let {
            Json.parseToJsonElement(it.ownText()).jsonArray
        } ?: throw Exception("Pages not found")

        return jsonArray.mapIndexed { index, jsonElement ->
            val root = jsonElement.jsonObject
            val imageUrl = """https://${root["img_url"]?.jsonPrimitive?.content}"""

            captionsCache(root, imageUrl)

            Page(index, imageUrl = imageUrl)
        }
    }

    private fun captionsCache(root: JsonObject, imageUrl: String) {
        val subs = root["translations"]!!.jsonArray.mapIndexed { index, it ->
            val caption = try {
                val arr = it.jsonArray
                arr[0].jsonArray to arr[arr.size - 1].toContent()
            } catch (_: Exception) {
                val obj = it.jsonObject
                obj["bbox"]!!.jsonArray to obj["text"]!!.toContent()
            }

            val coord = caption.first

            Sub(
                index = index,
                x1 = coord[0].toInt(),
                y1 = coord[1].toInt(),
                x2 = coord[2].toInt(),
                y2 = coord[3].toInt(),
                text = caption.second,
            )
        }

        captions[imageUrl] = PageMapping(url = imageUrl, subs)
    }

    override fun imageUrlParse(document: Document): String = ""

    // ============================= Utilities ==============================

    fun parseChapterDate(date: String?): Long {
        date ?: return 0
        return try { dateFormat.parse(date)!!.time } catch (_: Exception) { parseRelativeDate(date) }
    }

    private fun parseRelativeDate(date: String): Long {
        val number = Regex("""(\d+)""").find(date)?.value?.toIntOrNull() ?: return 0
        val cal = Calendar.getInstance()

        return when {
            date.contains("day") -> cal.apply { add(Calendar.DAY_OF_MONTH, -number) }.timeInMillis
            date.contains("hour") -> cal.apply { add(Calendar.HOUR, -number) }.timeInMillis
            date.contains("minute") -> cal.apply { add(Calendar.MINUTE, -number) }.timeInMillis
            date.contains("second") -> cal.apply { add(Calendar.SECOND, -number) }.timeInMillis
            date.contains("week") -> cal.apply { add(Calendar.DAY_OF_MONTH, -number * 7) }.timeInMillis
            else -> 0
        }
    }

    private fun JsonElement.toInt() = this.jsonPrimitive.content.toInt()

    private fun JsonElement.toContent() = this.jsonPrimitive.content

    val Int.sp: Float get() = this * scaledDensity

    // The Interceptor joins the subtitles and chapters of the manga.
    private fun composedImageIntercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.toString()
        if ((url in captions).not()) {
            return chain.proceed(request)
        }

        val response = chain.proceed(request)

        val bitmap = BitmapFactory.decodeStream(response.body.byteStream())!!
            .copy(Bitmap.Config.ARGB_8888, true)

        val canvas = Canvas(bitmap)
        val defaultTextSize = 22.sp

        val paint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.FILL_AND_STROKE
            textSize = defaultTextSize
            isAntiAlias = true
            typeface = Typeface.SANS_SERIF
        }

        val marginTop = 30
        val marginLeft = 30

        captions[url]?.subs
            ?.filter { it.text.isNotBlank() }
            ?.forEach {
                val lines = it.breakLines(paint)
                val middleStringSize = lines.toList().sortedBy(String::length)[lines.size / 2].length

                val centerY = it.centerY - (lines.size * paint.getCharWidth())
                val centerX = it.centerX - (middleStringSize / 2 * paint.getCharWidth())

                lines.forEachIndexed { index, line ->
                    val y = (paint.textSize * index + centerY + marginTop).absoluteValue
                    val x = (centerX + marginLeft).absoluteValue
                    canvas.drawText(line, 0, line.length, x, y, paint)
                }
            }

        val output = ByteArrayOutputStream()

        val format = when (url.substringAfterLast(".").lowercase()) {
            "png" -> Bitmap.CompressFormat.PNG
            "jpeg", "jpg" -> Bitmap.CompressFormat.JPEG
            else -> Bitmap.CompressFormat.WEBP
        }

        bitmap.compress(format, 100, output)

        val responseBody = output.toByteArray().toResponseBody(MEDIA_TYPE)

        return response.newBuilder()
            .body(responseBody)
            .build()
    }

    data class PageMapping(
        val url: String,
        val subs: List<Sub>,
    )

    data class Sub(
        val index: Int,
        val x1: Int,
        val y1: Int,
        val x2: Int,
        val y2: Int,
        val text: String,
    ) {
        val width get() = x2 - x1
        val height get() = y2 - y1
        val centerY get() = (y2 + y1) / 2f
        val centerX get() = (x2 + x1) / 2f

        fun breakLines(paint: Paint): List<String> {
            val diameter = width / paint.getCharWidth()
            val radius = diameter / 2
            return breakTextIntoLines(text, diameter + radius)
        }

        private fun breakTextIntoLines(text: String, maxLineLength: Float): List<String> {
            val words = text.split(" ")
            val lines = mutableListOf<String>()
            var currentLine = StringBuilder()

            for (word in words) {
                if (currentLine.length + word.length + 1 <= maxLineLength) {
                    if (currentLine.isNotEmpty()) {
                        currentLine.append(" ")
                    }
                    currentLine.append(word)
                } else {
                    lines.add(currentLine.toString())
                    currentLine = StringBuilder(word)
                }
            }

            if (currentLine.isNotEmpty()) {
                lines.add(currentLine.toString())
            }

            return lines
        }
    }

    // =============================== Filters ================================

    override fun getFilterList(): FilterList {
        val filters = mutableListOf<Filter<*>>(
            SelectionList("Sort", sortByList),
            Filter.Separator(),
            GenreList(title = "Genres", genres = genreList),
        )

        return FilterList(filters)
    }

    private var genreList: List<Genre> = listOf(
        Genre("Action"),
        Genre("Adult"),
        Genre("Adventure"),
        Genre("Comedy"),
        Genre("Drama"),
        Genre("Ecchi"),
        Genre("Fantasy"),
        Genre("Gender Bender"),
        Genre("Harem"),
        Genre("Historical"),
        Genre("Horror"),
        Genre("Josei"),
        Genre("Lolicon"),
        Genre("Martial Arts"),
        Genre("Mature"),
        Genre("Mecha"),
        Genre("Mystery"),
        Genre("Psychological"),
        Genre("Romance"),
        Genre("School Life"),
        Genre("Sci-fi"),
        Genre("Seinen"),
        Genre("Shoujo"),
        Genre("Shoujo Ai"),
        Genre("Shounen"),
        Genre("Shounen Ai"),
        Genre("Slice of Life"),
        Genre("Smut"),
        Genre("Sports"),
        Genre("Supernatural"),
        Genre("Tragedy"),
        Genre("Yaoi"),
        Genre("Yuri"),
    )

    private val sortByList = listOf(
        Option("All"),
        Option("Most Views", "views"),
        Option("Most Recent", "recent"),
    ).map { it.copy(query = "sort_by") }

    private data class Option(val name: String = "", val value: String = "", val query: String = "")

    private open class SelectionList(displayName: String, private val vals: List<Option>, state: Int = 0) :
        Filter.Select<String>(displayName, vals.map { it.name }.toTypedArray(), state) {
        fun selected() = vals[state]
    }

    private class GenreList(title: String, genres: List<Genre>) :
        Filter.Group<GenreCheckBox>(title, genres.map { GenreCheckBox(it.name, it.id) })

    class GenreCheckBox(name: String, val id: String = name) : Filter.CheckBox(name)

    class Genre(val name: String, val id: String = name)

    companion object {
        const val scaledDensity = 1.5f
        const val PREFIX_SEARCH = "id:"
        val MEDIA_TYPE = "image/png".toMediaType()
        private val dateFormat: SimpleDateFormat = SimpleDateFormat("dd MMMM yyyy", Locale.US)
    }
}

fun Paint.getCharWidth(): Float {
    val text = "A"
    val fontWidth = FloatArray(1)
    getTextWidths(text.first().toString(), fontWidth)
    return fontWidth.first()
}
