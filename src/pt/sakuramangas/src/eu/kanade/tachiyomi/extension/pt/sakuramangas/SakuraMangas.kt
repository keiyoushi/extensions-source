package eu.kanade.tachiyomi.extension.pt.sakuramangas

import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.util.Calendar
import kotlin.concurrent.thread

class SakuraMangas : HttpSource() {
    override val lang = "pt-BR"

    override val supportsLatest = true

    override val name = "Sakura Mangás"

    override val baseUrl = "https://sakuramangas.org"

    private var genresSet: Set<Genre> = emptySet()
    private var demographyOptions: List<Pair<String, String>> = listOf(
        "Todos" to "",
    )
    private var classificationOptions: List<Pair<String, String>> = listOf(
        "Todos" to "",
    )
    private var orderByOptions: List<Pair<String, String>> = listOf(
        "Lidos" to "3",
    )

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")
        .set("X-Requested-With", "XMLHttpRequest")

    // ================================ Popular =======================================

    override fun popularMangaRequest(page: Int): Request =
        searchMangaRequest(page, "", FilterList())

    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)

    // ================================ Latest =======================================

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/dist/sakura/models/home/home_ultimos.php", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val result = response.parseAs<List<String>>()

        val mangas = result.map {
            val element = Jsoup.parseBodyFragment(it, baseUrl)
            SManga.create().apply {
                title = element.selectFirst(".h5-titulo")!!.text()
                setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
            }
        }

        return MangasPage(mangas, hasNextPage = false)
    }

    // ================================ Search =======================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val form = FormBody.Builder()
            .add("seach", query)
            .add("order", "3")
            .add("offset", ((page - 1) * 15).toString())
            .add("limit", "15")

        val inclGenres = mutableListOf<String>()
        val exclGenres = mutableListOf<String>()

        var demography: String? = null
        var classification: String? = null
        var orderBy: String? = null

        filters.forEach { filter ->
            when (filter) {
                is GenreList -> filter.state.forEach {
                    when (it.state) {
                        Filter.TriState.STATE_INCLUDE -> inclGenres.add(it.id)
                        Filter.TriState.STATE_EXCLUDE -> exclGenres.add(it.id)
                        else -> {}
                    }
                }

                is DemographyFilter -> demography = filter.getValue().ifEmpty { null }
                is ClassificationFilter -> classification = filter.getValue().ifEmpty { null }
                is OrderByFilter -> orderBy = filter.getValue().ifEmpty { null }
                else -> {}
            }
        }

        inclGenres.forEach { form.add("tags[]", it) }
        exclGenres.forEach { form.add("excludeTags[]", it) }

        demography?.let { form.add("demography", it) }
        classification?.let { form.add("classification", it) }
        orderBy?.let { form.add("order", it) }

        return POST("$baseUrl/dist/sakura/models/obras/obras_buscar.php", headers, form.build())
    }

    fun searchMangaFromElement(element: Element) = SManga.create().apply {
        title = element.selectFirst(".h5-titulo")!!.text()
        thumbnail_url = element.selectFirst("img.img-pesquisa")?.absUrl("src")
        description = element.selectFirst(".p-sinopse")?.text()

        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val result = response.parseAs<SakuraMangasResultDto>()
        val seriesList =
            result.asJsoup("$baseUrl/obras/").select(".result-item").map(::searchMangaFromElement)
        return MangasPage(seriesList, result.hasMore)
    }

    // ================================ Details =======================================

    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}"

    private fun mangaDetailsApiRequest(mangaId: String): Request {
        val form = FormBody.Builder()
            .add("manga_id", mangaId)
            .add("dataType", "json")

        return POST("$baseUrl/dist/sakura/models/manga/manga_info.php", headers, form.build())
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val mangaId = document.selectFirst("meta[manga-id]")!!.attr("manga-id")

        return client.newCall(mangaDetailsApiRequest(mangaId)).execute()
            .parseAs<SakuraMangaInfoDto>().toSManga(document.baseUri())
    }

    // ================================ Chapters =======================================

    private fun chapterListApiRequest(mangaId: String, page: Int): Request {
        val form = FormBody.Builder()
            .add("manga_id", mangaId)
            .add("offset", ((page - 1) * 90).toString())
            .add("order", "desc")
            .add("limit", "90")

        return POST("$baseUrl/dist/sakura/models/manga/manga_capitulos.php", headers, form.build())
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val mangaId = document.selectFirst("meta[manga-id]")!!.attr("manga-id")

        var page = 1
        val chapters = mutableListOf<SChapter>()
        do {
            val doc = client.newCall(chapterListApiRequest(mangaId, page++)).execute().asJsoup()

            val chapterGroup = doc.select(".capitulo-item").map(::chapterFromElement).also {
                chapters += it
            }
        } while (chapterGroup.isNotEmpty())

        return chapters
    }

    fun chapterFromElement(element: Element) = SChapter.create().apply {
        name = buildString {
            element.selectFirst(".num-capitulo")
                ?.text()
                ?.let { append(it) }

            element.selectFirst(".cap-titulo")
                ?.text()
                ?.takeIf { it.isNotBlank() }
                ?.let { append(" - $it") }
        }
        scanlator = element.selectFirst(".scan-nome")?.text()
        chapter_number =
            element
                .selectFirst(".num-capitulo")!!
                .attr("data-chapter")
                .toFloatOrNull() ?: 1F
        date_upload = element.selectFirst(".cap-data")?.text()?.toDate() ?: 0L
        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
    }

    // ================================ Pages =======================================

    private fun pageListApiRequest(chapterId: String, token: String): Request {
        val form = FormBody.Builder()
            .add("chapter_id", chapterId)
            .add("token", token)

        return POST(
            "$baseUrl/dist/sakura/models/capitulo/capitulos_read.php",
            headers,
            form.build(),
        )
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        val chapterId = document.selectFirst("meta[chapter-id]")!!.attr("chapter-id")
        val token = document.selectFirst("meta[token]")!!.attr("token")

        val response = client.newCall(pageListApiRequest(chapterId, token)).execute()
            .parseAs<SakuraMangaChapterReadDto>()

        val baseUrl = document.baseUri().trimEnd('/')

        return response.imageUrls.mapIndexed { index, url ->
            Page(
                index,
                imageUrl = "$baseUrl/$url".toHttpUrl().toString(),
            )
        }
    }

    override fun imageUrlParse(response: Response): String = ""

    override fun getFilterList(): FilterList {
        thread {
            fetchFilters()
        }

        return FilterList(
            OrderByFilter("Ordenar por", orderByOptions, "order"),
            DemographyFilter("Demografia", demographyOptions, "demography"),
            ClassificationFilter("Classificação", classificationOptions, "classification"),
            GenreList(
                title = "Gêneros",
                genres = genresSet.toTypedArray(),
            ),
        )
    }

    private fun fetchFilters() {
        if (genresSet.isNotEmpty()) {
            return
        }

        try {
            val document = client
                .newCall(GET("$baseUrl/obras/", headers))
                .execute()
                .asJsoup()

            genresSet = document.select(".genero-badge").map { element ->
                val id = element.attr("data-value")
                Genre(element.ownText(), id)
            }.toSet()

            val demoOpts = document.select("select#demografia-select option").mapNotNull { opt ->
                val value = opt.attr("value").orEmpty()
                val text = opt.text().trim()
                if (text.isEmpty()) null else text to value
            }
            if (demoOpts.isNotEmpty()) demographyOptions = demoOpts

            val classOpts =
                document.select("select#classificacao-select option").mapNotNull { opt ->
                    val value = opt.attr("value").orEmpty()
                    val text = opt.text().trim()
                    if (text.isEmpty()) null else text to value
                }
            if (classOpts.isNotEmpty()) classificationOptions = classOpts

            val orderOptions = document.select("select#ordenar-por option").mapNotNull { opt ->
                val value = opt.attr("value").orEmpty()
                val text = opt.text().trim()
                if (text.isEmpty()) null else text to value
            }
            if (orderOptions.isNotEmpty()) orderByOptions = orderOptions
        } catch (e: Exception) {
            Log.e("SakuraMangas", "failed to fetch genres", e)
        }
    }

    private fun String.toDate(): Long {
        val trimmedDate = this.split(" ")

        if (trimmedDate[0] != "Há") return 0L

        val number = trimmedDate[1].toIntOrNull() ?: return 0L

        val unit = trimmedDate[2]

        val javaUnit = when (unit) {
            "ano", "anos" -> Calendar.YEAR
            "mês", "meses" -> Calendar.MONTH
            "semana", "semanas" -> Calendar.WEEK_OF_MONTH
            "dia", "dias" -> Calendar.DAY_OF_MONTH
            "hora", "horas" -> Calendar.HOUR
            "minuto", "minutos" -> Calendar.MINUTE
            "segundo", "segundos" -> Calendar.SECOND
            else -> return 0L
        }

        val now = Calendar.getInstance()

        now.add(javaUnit, -number)

        return now.timeInMillis
    }
}
