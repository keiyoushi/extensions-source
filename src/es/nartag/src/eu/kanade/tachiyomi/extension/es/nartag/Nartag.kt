package eu.kanade.tachiyomi.extension.es.nartag

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.Calendar

class Nartag : ParsedHttpSource() {

    override val name = "Traducciones Amistosas"

    override val baseUrl = "https://visortraduccionesamistosas.com"

    override val lang = "es"

    override val supportsLatest = true

    override val client: OkHttpClient = network.client.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 2)
        .build()

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("Referer", baseUrl)

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/biblioteca?page=$page", headers)

    override fun popularMangaSelector(): String = "div.manga div.manga__item"

    override fun popularMangaNextPageSelector(): String = "nav.paginator a[rel=next]"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.select("a.manga__link").attr("href"))
        title = element.select("a.manga__link").text()
        thumbnail_url = element.selectFirst("figure.manga__image > img")?.imgAttr()
    }

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/actualizaciones?page=$page", headers)

    override fun latestUpdatesSelector(): String = popularMangaSelector()

    override fun latestUpdatesNextPageSelector(): String = popularMangaNextPageSelector()

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/biblioteca".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())

        if (query.isNotEmpty()) {
            url.addQueryParameter("s", query)
        }

        filters.forEach { filter ->
            when (filter) {
                is TypeFilter -> {
                    if (filter.state != 0) {
                        url.addQueryParameter("type", filter.toUriPart())
                    }
                }
                is DemographicFilter -> {
                    if (filter.state != 0) {
                        url.addQueryParameter("demography", filter.toUriPart())
                    }
                }
                is StatusFilter -> {
                    if (filter.state != 0) {
                        url.addQueryParameter("bookstatus", filter.toUriPart())
                    }
                }
                is CategoryFilter -> {
                    val includeArray = mutableListOf<String>()
                    val excludeArray = mutableListOf<String>()
                    filter.state.forEach { content ->
                        when (content.state) {
                            Filter.TriState.STATE_INCLUDE -> includeArray.add(content.value)
                            Filter.TriState.STATE_EXCLUDE -> excludeArray.add(content.value)
                        }
                    }
                    if (includeArray.isNotEmpty()) {
                        url.addQueryParameter("categories", includeArray.joinToString(","))
                    }
                    if (excludeArray.isNotEmpty()) {
                        url.addQueryParameter("excategories", excludeArray.joinToString(","))
                    }
                }

                else -> {}
            }
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaSelector(): String = popularMangaSelector()

    override fun searchMangaNextPageSelector(): String = popularMangaNextPageSelector()

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        with(document.selectFirst("section.manga__card")!!) {
            title = select("div.manga__title > h2").text()
            thumbnail_url = selectFirst("figure.manga__cover > img")?.imgAttr()
            genre = select("div.category__item > a").joinToString { it.text() }
            status = select("div.manga__status span.status__name").text().toStatus()
            description = select("div.manga__description > p").text()
        }
    }

    override fun chapterListSelector(): String = "section.manga__chapters div.chapter__item"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        setUrlWithoutDomain(element.select("div.chapter__actions a").attr("href"))
        name = element.select(".chapter__title").text()
        date_upload = parseRelativeDate(element.select("span.chapter__date").text())
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("div.view__content > div.reader__item > img").mapIndexed { i, element ->
            Page(i, "", element.imgAttr())
        }
    }

    private fun String.toStatus(): Int = when (this) {
        "En emisión", "Ongoing" -> SManga.ONGOING
        "Finalizado" -> SManga.COMPLETED
        "Publishing finished" -> SManga.PUBLISHING_FINISHED
        "En pausa" -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    private fun Element.imgAttr(): String = when {
        hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
        hasAttr("data-src") -> attr("abs:data-src")
        else -> attr("abs:src")
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()

    override fun getFilterList(): FilterList = FilterList(
        TypeFilter(),
        DemographicFilter(),
        StatusFilter(),
        CategoryFilter("Categorías", getCategoryList()),
    )

    private class TypeFilter : UriPartFilter(
        "Tipo",
        arrayOf(
            Pair("<Seleccionar>", ""),
            Pair("Manga", "manga"),
            Pair("Manhua", "manhua"),
            Pair("Manhwa", "manhwa"),
            Pair("One Shot", "one-shot"),
            Pair("Doujinshi", "doujinshi"),
        ),
    )

    private class DemographicFilter : UriPartFilter(
        "Demografía",
        arrayOf(
            Pair("<Seleccionar>", ""),
            Pair("Shounen", "shonen"),
            Pair("Shoujo", "shojo"),
            Pair("Seinen", "seinen"),
            Pair("Josei", "josei"),
            Pair("Kodomo", "kodomo"),
        ),
    )

    private class StatusFilter : UriPartFilter(
        "Estado",
        arrayOf(
            Pair("<Seleccionar>", ""),
            Pair("Desconocido", "desconocido"),
            Pair("Ongoing", "ongoing"),
            Pair("Finalizado", "finalizado"),
            Pair("Publishing finished", "publishing-finished"),
            Pair("En emisión", "en-emisi-n"),
            Pair("En pausa", "en-pausa"),
        ),
    )

    class Category(title: String, val value: String) : Filter.TriState(title)
    class CategoryFilter(title: String, categories: List<Category>) : Filter.Group<Category>(title, categories)

    private fun getCategoryList() = listOf(
        Category("Acción", "accion"),
        Category("Animación", "animacion"),
        Category("Apocalíptico", "apocaliptico"),
        Category("Artes Marciales", "artes-marciales"),
        Category("Aventura", "aventura"),
        Category("Boys Love", "boys-love"),
        Category("Ciberpunk", "ciberpunk"),
        Category("Ciencia Ficción", "ciencia-ficcion"),
        Category("Comedia", "comedia"),
        Category("Crimen", "crimen"),
        Category("Demonios", "demonios"),
        Category("Deporte", "deporte"),
        Category("Drama", "drama"),
        Category("Ecchi", "ecchi"),
        Category("Extranjero", "extranjero"),
        Category("Familia", "familia"),
        Category("Fantasia", "fantasia"),
        Category("Género Bender", "genero-bender"),
        Category("Girls Love", "girls-love"),
        Category("Gore", "gore"),
        Category("Guerra", "guerra"),
        Category("Harem", "harem"),
        Category("Historia", "historia"),
        Category("Horror", "horror"),
        Category("Magia", "magia"),
        Category("Mecha", "mecha"),
        Category("Militar", "militar"),
        Category("Misterio", "misterio"),
        Category("Murim", "murim"),
        Category("Musica", "musica"),
        Category("Niños", "ninos"),
        Category("Oeste", "oeste"),
        Category("Parodia", "parodia"),
        Category("Policiaco", "policiaco"),
        Category("Psicológico", "psicologico"),
        Category("Realidad", "realidad"),
        Category("Realidad Virtual", "realidad-virtual"),
        Category("Recuentos de la vida", "recuentos-de-la-vida"),
        Category("Reencarnacion", "reencarnacion"),
        Category("Regresion", "regresion"),
        Category("Romance", "romance"),
        Category("Samurái", "samurai"),
        Category("Sobrenatural", "sobrenatural"),
        Category("Superpoderes", "superpoderes"),
        Category("Telenovela", "telenovela"),
        Category("Thriller", "thriller"),
        Category("Tragedia", "tragedia"),
        Category("Vampiros", "vampiros"),
        Category("Vida Escolar", "vida-escolar"),
    )

    private fun parseRelativeDate(date: String): Long {
        val number = Regex("""(\d+)""").find(date)?.value?.toIntOrNull() ?: return 0
        val cal = Calendar.getInstance()

        return when {
            WordSet("segundo").anyWordIn(date) -> cal.apply { add(Calendar.SECOND, -number) }.timeInMillis
            WordSet("minuto").anyWordIn(date) -> cal.apply { add(Calendar.MINUTE, -number) }.timeInMillis
            WordSet("hora").anyWordIn(date) -> cal.apply { add(Calendar.HOUR, -number) }.timeInMillis
            WordSet("día", "dia").anyWordIn(date) -> cal.apply { add(Calendar.DAY_OF_MONTH, -number) }.timeInMillis
            WordSet("semana").anyWordIn(date) -> cal.apply { add(Calendar.DAY_OF_MONTH, -number * 7) }.timeInMillis
            WordSet("mes").anyWordIn(date) -> cal.apply { add(Calendar.MONTH, -number) }.timeInMillis
            WordSet("año").anyWordIn(date) -> cal.apply { add(Calendar.YEAR, -number) }.timeInMillis
            else -> 0
        }
    }

    class WordSet(private vararg val words: String) {
        fun anyWordIn(dateString: String): Boolean = words.any { dateString.contains(it, ignoreCase = true) }
    }

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }
}
