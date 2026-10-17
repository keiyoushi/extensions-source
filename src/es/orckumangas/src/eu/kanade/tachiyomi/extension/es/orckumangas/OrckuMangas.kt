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
import org.jsoup.nodes.Element
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

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get("$baseUrl/ranking?page=$page").asJsoup()
        val mangas = document.select("ol li.border-t a[href*=ficha?id=]").mapNotNull { element ->
            parseMangaCard(element)
        }.distinctBy { it.url }
        val hasNextPage = document.select("nav[aria-label=Páginas del ranking] a:containsOwn(Siguiente)").isNotEmpty()
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        if (page > 1) return MangasPage(emptyList(), false)
        val document = client.get(baseUrl).asJsoup()
        val mangas = document.select("#latestGrid div.reveal a[href*=ficha?id=]").mapNotNull { element ->
            val title = element.selectFirst("span.line-clamp-2")?.text()?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            SManga.create().apply {
                this.title = title
                setUrlWithoutDomain(element.attr("abs:href"))
                thumbnail_url = element.selectFirst("img")?.attr("abs:src")
            }
        }.distinctBy { it.url }
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
        val urlBuilder = "$baseUrl/biblioteca".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
        if (query.isNotBlank()) {
            urlBuilder.addQueryParameter("search", query)
        } else {
            filters.forEach { filter ->
                when (filter) {
                    is GenreFilter -> urlBuilder.addQueryParameter("genre", filter.selected)
                    is TypeFilter -> urlBuilder.addQueryParameter("type", filter.selected)
                    is StatusFilter -> urlBuilder.addQueryParameter("status", filter.selected)
                    else -> {}
                }
            }
        }
        val document = client.get(urlBuilder.build()).asJsoup()
        val mangas = document.select("ul.grid li a[href*=ficha?id=]").mapNotNull { element ->
            parseMangaCard(element)
        }.distinctBy { it.url }
        val hasNextPage = document.select("nav[aria-label*=Página] a:containsOwn(Siguiente)").isNotEmpty()
        return MangasPage(mangas, hasNextPage)
    }

    private fun parseMangaCard(element: Element): SManga? {
        val title = element.selectFirst("span.truncate")?.text()?.takeIf { it.isNotBlank() }
            ?: element.selectFirst("span.font-bold")?.text()?.takeIf { it.isNotBlank() }
            ?: return null
        return SManga.create().apply {
            this.title = title
            setUrlWithoutDomain(element.attr("abs:href"))
            thumbnail_url = element.selectFirst("img")?.attr("abs:src")
        }
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
        return SManga.create().apply {
            url = manga.url
            title = document.selectFirst("main#contenido h1")?.text() ?: manga.title
            thumbnail_url = document.selectFirst("section[data-ficha] img")?.attr("abs:src")
            val info = document.selectFirst("#panel-info dl")
            author = info?.select("div:has(dt:containsOwn(Autor)) dd")?.text()
            artist = info?.select("div:has(dt:containsOwn(Artista)) dd")?.text()
            status = info?.select("div:has(dt:containsOwn(Estado)) dd")?.text().parseStatus()
            genre = document.select("a[href*=biblioteca?genre=]").joinToString { it.text() }
            initialized = true
        }
    }

    private suspend fun getChapters(manga: SManga): List<SChapter> = coroutineScope {
        val base = "$baseUrl${manga.url}".toHttpUrl().newBuilder()
            .setQueryParameter("order", "desc")
            .setQueryParameter("page", "1")
            .build()

        val document = client.get(base).asJsoup()
        val pages = document.select("nav[aria-label=Páginas de capítulos] a[href*=page=]").mapNotNull {
            runCatching { it.attr("abs:href").toHttpUrl().queryParameter("page")?.toIntOrNull() }.getOrNull()
        }.maxOrNull() ?: 1

        parseChapters(document) + (2..pages).map { page ->
            async {
                parseChapters(
                    client.get(
                        base.newBuilder()
                            .setQueryParameter("page", page.toString())
                            .build(),
                    ).asJsoup(),
                )
            }
        }.awaitAll().flatten()
    }

    private fun parseChapters(document: Document): List<SChapter> {
        return document.select("ul#chList li[id^=ch-] a[href*=capitulo?id=]").mapNotNull {
            val name = it.selectFirst("span.font-semibold")?.text()?.takeIf { s -> s.isNotBlank() }
                ?: return@mapNotNull null
            SChapter.create().apply {
                this.name = name
                setUrlWithoutDomain(it.attr("abs:href"))
                date_upload = it.selectFirst("span.text-ink-dim")?.text().parseDate()
            }
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        return document.select("div.chapter-images img, main img[src*=uploads]").mapIndexedNotNull { index, element ->
            val src = element.attr("abs:src").takeIf { it.isNotBlank() } ?: return@mapIndexedNotNull null
            Page(index, imageUrl = src)
        }
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData() = client.get("$baseUrl/biblioteca").asJsoup()
        .select("select#genero option")
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
