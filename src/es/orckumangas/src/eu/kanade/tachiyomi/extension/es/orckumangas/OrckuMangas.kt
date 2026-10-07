package eu.kanade.tachiyomi.extension.es.orckumangas

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.addCookie
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParseDate
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import java.time.format.DateTimeFormatter
import java.util.Calendar
import kotlin.time.Duration.Companion.seconds

@Source
abstract class OrckuMangas : KeiSource() {
    private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    override fun OkHttpClient.Builder.configureClient() = apply {
        addCookie("orcku_mayor_edad" to "1")
        rateLimit(3, 1.seconds)
    }

    override suspend fun getPopularManga(page: Int) = parseSearch(client.get("$baseUrl/ranking.php?page=$page").asJsoup())

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val document = client.get("$baseUrl/index.php?filter_chapters=1&type=").asJsoup()
        val mangas = document.select("div > a.block").map { element ->
            SManga.create().apply {
                title = element.selectFirst("h3")!!.ownText()
                setUrlWithoutDomain(element.attr("abs:href"))
                thumbnail_url = element.selectFirst("img")?.attr("abs:src")
            }
        }
        return MangasPage(mangas, false)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        url.queryParameter("id") ?: return null
        return getDetails(
            SManga.create().apply {
                setUrlWithoutDomain(url.toString())
            },
        )
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = if (query.isNotBlank()) {
            "$baseUrl/buscador.php".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("page", page.toString())
        } else {
            "$baseUrl/biblioteca.php".toHttpUrl().newBuilder().apply {
                addQueryParameter("page", page.toString())
                filters.forEach { filter ->
                    when (filter) {
                        is GenreFilter -> addQueryParameter("genre", filter.selected)
                        is TypeFilter -> addQueryParameter("type", filter.selected)
                        is StatusFilter -> addQueryParameter("status", filter.selected)
                        else -> {}
                    }
                }
            }
        }
        return parseSearch(client.get(url.build()).asJsoup())
    }

    private fun parseSearch(document: Document): MangasPage {
        val mangas = document.select("div.card > a").map { element ->
            SManga.create().apply {
                title = element.selectFirst("h3")!!.ownText()
                setUrlWithoutDomain(element.attr("abs:href"))
                thumbnail_url = element.selectFirst("img")?.attr("abs:src")
            }
        }
        val hasNextPage = document.selectFirst("div.flex > a:containsOwn(Siguiente)") != null
        return MangasPage(mangas, hasNextPage)
    }

    override val supportRelatedMangasBySearch = true

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ) = coroutineScope {
        val details = async { if (fetchDetails) getDetails(manga) else manga }
        val chapters = async { if (fetchChapters) getChapters(manga) else chapters }
        SMangaUpdate(details.await(), chapters.await())
    }

    private suspend fun getDetails(manga: SManga): SManga {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        return document.selectFirst("main div.card:has(h1)")!!.let { element ->
            SManga.create().apply {
                url = manga.url
                title = element.selectFirst("h1")!!.text()
                thumbnail_url = element.selectFirst("img")?.attr("abs:src")
                author = element.selectFirst("div:has(> span:containsOwn(Autor))")?.ownText()
                artist = element.selectFirst("div:has(> span:containsOwn(Artista))")?.ownText()
                status = element.selectFirst("div:has(> span:containsOwn(Estado))")?.ownText().parseStatus()
                genre = element.select("a[href*=genre]").joinToString { it.text() }
                description = element.selectFirst("p")?.text()
                initialized = true
            }
        }
    }

    private suspend fun getChapters(manga: SManga): List<SChapter> = coroutineScope {
        val url = "$baseUrl${manga.url}".toHttpUrl().newBuilder()
            .setQueryParameter("order", "desc")
            .setQueryParameter("page", "1")
            .build()

        val document = client.get(url).asJsoup()
        val pages = document.select("div > a[href*=page=]").lastOrNull()
            ?.attr("abs:href")?.toHttpUrl()
            ?.queryParameter("page")?.toInt() ?: 1

        parseChapters(document) + (2..pages).map { page ->
            async {
                parseChapters(
                    client.get(
                        url.newBuilder()
                            .setQueryParameter("page", page.toString())
                            .build(),
                    ).asJsoup(),
                )
            }
        }.awaitAll().flatten()
    }

    private fun parseChapters(document: Document) = document.select(".cap-grid > a.cap-card").map {
        SChapter.create().apply {
            name = it.selectFirst(".cap-num")!!.ownText()
            setUrlWithoutDomain(it.attr("abs:href"))
            date_upload = it.selectFirst(".cap-date")?.text().parseDate()
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        return document.select("div.chapter-images img").mapIndexed { index, element ->
            Page(index, imageUrl = element.attr("abs:src"))
        }
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData() = client.get("$baseUrl/biblioteca").asJsoup()
        .select("select[name=genre] option")
        .associate { it.text().trim() to it.attr("value") }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?) = FilterList(
        buildList {
            add(Filter.Header("Los filtros son ignorados si se realiza una búsqueda por texto"))
            val genres = data?.parseAs<Map<String, String>>().orEmpty()
            if (genres.isNotEmpty()) add(GenreFilter(genres.toList()))
            add(TypeFilter())
            add(StatusFilter())
        },
    )

    private fun String?.parseStatus(): Int = when (this?.lowercase()) {
        "ongoing" -> SManga.ONGOING
        "completed" -> SManga.COMPLETED
        "hiatus" -> SManga.ON_HIATUS
        "cancelled" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    private val nonDigitRegex = Regex("""\D""")

    private fun String?.parseDate(): Long {
        if (isNullOrEmpty() || !contains("hace", ignoreCase = true)) {
            return dateFormat.tryParseDate(this)
        }

        val number = replace(nonDigitRegex, "").toIntOrNull() ?: return 0L
        val calendar = Calendar.getInstance()

        when {
            contains("segundo") -> calendar.add(Calendar.SECOND, -number)
            contains("minuto") -> calendar.add(Calendar.MINUTE, -number)
            contains("hora") -> calendar.add(Calendar.HOUR, -number)
            contains("día") || contains("dia") -> calendar.add(Calendar.DAY_OF_YEAR, -number)
            contains("semana") -> calendar.add(Calendar.WEEK_OF_YEAR, -number)
            contains("mes") -> calendar.add(Calendar.MONTH, -number)
            contains("año") -> calendar.add(Calendar.YEAR, -number)
        }

        return calendar.timeInMillis
    }
}
