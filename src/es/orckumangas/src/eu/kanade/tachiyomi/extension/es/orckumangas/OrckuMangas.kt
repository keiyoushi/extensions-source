package eu.kanade.tachiyomi.extension.es.orckumangas

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.addCookie
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.textOrNull
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

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(SortFilter()))

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val document = client.get(baseUrl, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select("#latestGrid div.reveal a[href*=ficha?id=]").map { element ->
            SManga.create().apply {
                title = element.selectFirst("span.line-clamp-2")!!.text()
                setUrlWithoutDomain(element.attr("abs:href"))
                thumbnail_url = element.selectFirst("img")?.attr("abs:src")
            }
        }.distinctBy { it.url }
        return MangasPage(mangas, false)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.encodedPath != "/ficha") return null
        val id = url.queryParameter("id")?.toIntOrNull() ?: return null
        val manga = SManga.create().apply { this.url = "/ficha?id=$id" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/biblioteca".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            if (query.isNotBlank()) addQueryParameter("search", query)
            filters.forEach { filter ->
                when (filter) {
                    is SortFilter -> addQueryParameter("sort", filter.selected)
                    is GenreFilter -> addQueryParameter("genre", filter.selected)
                    is TypeFilter -> addQueryParameter("type", filter.selected)
                    is StatusFilter -> addQueryParameter("status", filter.selected)
                    else -> {}
                }
            }
        }.build()
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select("ul.grid li a[href*=ficha?id=]").map { element ->
            SManga.create().apply {
                title = element.selectFirst("span.truncate")!!.text()
                setUrlWithoutDomain(element.attr("abs:href"))
                thumbnail_url = element.selectFirst("img")?.attr("abs:src")
            }
        }
        val hasNextPage = document.selectFirst("a[rel=next]") != null
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
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        return manga.apply {
            title = document.selectFirst("main#contenido h1")!!.text()
            thumbnail_url = document.selectFirst("section[data-ficha] img")?.attr("abs:src")
            val info = document.selectFirst("#panel-info dl")
            author = info?.selectFirst("div:has(dt:containsOwn(Autor)) dd")?.textOrNull()
            artist = info?.selectFirst("div:has(dt:containsOwn(Artista)) dd")?.textOrNull()
            status = info?.selectFirst("div:has(dt:containsOwn(Estado)) dd")?.text().parseStatus()
            description = document.selectFirst("#syn")?.wholeText()?.trim()?.ifEmpty { null }
            genre = buildList {
                info?.selectFirst("div:has(dt:containsOwn(Tipo)) dd")?.textOrNull()?.let(::add)
                document.select("a[href*=biblioteca?genre=]").mapTo(this) { it.text() }
            }.joinToString().ifEmpty { null }
        }
    }

    private suspend fun getChapters(manga: SManga): List<SChapter> = coroutineScope {
        val base = getMangaUrl(manga).toHttpUrl().newBuilder()
            .setQueryParameter("order", "desc")
            .setQueryParameter("page", "1")
            .build()

        val document = client.get(base, Fetch.DOCUMENT).asJsoup()
        val pages = document.select("nav[aria-label=Páginas de capítulos] a[href*=page=]").mapNotNull {
            it.attr("abs:href").toHttpUrl().queryParameter("page")?.toIntOrNull()
        }.maxOrNull() ?: 1

        parseChapters(document) + (2..pages).map { page ->
            async {
                parseChapters(
                    client.get(
                        base.newBuilder()
                            .setQueryParameter("page", page.toString())
                            .build(),
                        Fetch.DOCUMENT,
                    ).asJsoup(),
                )
            }
        }.awaitAll().flatten()
    }

    private fun parseChapters(document: Document): List<SChapter> = document.select("ul#chList li[id^=ch-] a[href*=capitulo?id=]").map {
        SChapter.create().apply {
            name = it.selectFirst("span.font-semibold")!!.text()
            setUrlWithoutDomain(it.attr("abs:href"))
            val info = it.selectFirst("span.text-ink-dim")?.text()
            scanlator = info?.substringBefore(" · ")
            date_upload = info?.substringAfter(" · ").parseDate()
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        return document.select("main img[data-pagina]").mapIndexed { index, element ->
            Page(index, imageUrl = element.attr("abs:src"))
        }
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData() = client.get("$baseUrl/biblioteca", Fetch.DOCUMENT).asJsoup()
        .select("select#genero option")
        .associate { it.text() to it.attr("value") }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?) = FilterList(
        buildList {
            add(SortFilter())
            val genres = data?.parseAs<Map<String, String>>().orEmpty()
            if (genres.isNotEmpty()) add(GenreFilter(genres.toList()))
            add(TypeFilter())
            add(StatusFilter())
        },
    )

    private fun String?.parseStatus(): Int = when (this?.lowercase()) {
        "en emisión", "ongoing" -> SManga.ONGOING
        "finalizado", "completed" -> SManga.COMPLETED
        "en pausa", "hiatus" -> SManga.ON_HIATUS
        "cancelado", "cancelled" -> SManga.CANCELLED
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
