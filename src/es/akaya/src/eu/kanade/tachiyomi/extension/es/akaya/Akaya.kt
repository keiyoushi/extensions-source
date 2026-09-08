package eu.kanade.tachiyomi.extension.es.akaya

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.jsoup.Jsoup
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class Akaya : HttpSource() {
    private val baseUrlHost by lazy { baseUrl.toHttpUrl().host }

    override val supportsLatest = true

    override val client = network.client.newBuilder()
        .addInterceptor { chain ->
            val request = chain.request()

            if (!request.url.toString().startsWith("$baseUrl/serie")) {
                return@addInterceptor chain.proceed(request)
            }

            val response = chain.proceed(request)

            if (response.request.url.toString().removeSuffix("/") == baseUrl) {
                response.close()
                throw IOException("Esta serie no se encuentra disponible")
            }

            response
        }
        .rateLimit(1, 1.seconds) { it.host == baseUrlHost }
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/collection/bd90cb43-9bf2-4759-b8cc-c9e66a526bc6?page=$page", headers)

    override fun popularMangaParse(response: Response) = parseMangaList(response)

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/explorer/all?page=$page", headers)

    override fun latestUpdatesParse(response: Response) = parseMangaList(response)

    override fun searchMangaRequest(
        page: Int,
        query: String,
        filters: FilterList,
    ): Request {
        if (query.isNotEmpty()) {
            return livewireSearchRequest(query)
        }

        val selectedGenres = filters
            .filterIsInstance<GenreFilter>()
            .firstOrNull()
            ?.state
            ?.filter { it.state }
            .orEmpty()

        if (selectedGenres.isNotEmpty()) {
            return livewireGenreRequest(
                genres = selectedGenres,
                page = page,
            )
        }

        return GET("$baseUrl/explorer/all?page=$page", headers)
    }

    private fun livewireSearchRequest(query: String): Request {
        val homeResponse = client.newCall(GET(baseUrl, headers)).execute()
        val homeDocument = homeResponse.use { it.asJsoup() }

        val component = homeDocument
            .select("*")
            .firstOrNull { element ->
                element.attr("wire:name") == "home.input-search"
            }
            ?: throw IOException("No se encontró el buscador de Akaya")

        val snapshot = component.attr("wire:snapshot")

        if (snapshot.isBlank()) {
            throw IOException("El buscador no tiene snapshot Livewire")
        }

        val token = homeDocument
            .selectFirst("meta[name=csrf-token]")
            ?.attr("content")
            .orEmpty()

        val payload = buildJsonObject {
            put("_token", token)

            putJsonArray("components") {
                add(
                    buildJsonObject {
                        put("snapshot", snapshot)

                        putJsonObject("updates") {
                            put("search", query)
                        }

                        putJsonArray("calls") {
                            add(
                                buildJsonObject {
                                    put("method", "\$commit")
                                    putJsonArray("params") {}

                                    putJsonObject("metadata") {
                                        put("type", "model.live")
                                    }
                                },
                            )
                        }
                    },
                )
            }
        }

        return Request.Builder()
            .url("$baseUrl/livewire-c4e82cae/update")
            .headers(headers)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("X-Livewire", "1")
            .header("Origin", baseUrl)
            .header("Referer", "$baseUrl/")
            .post(
                Json.encodeToString(payload)
                    .toRequestBody("application/json".toMediaType()),
            )
            .build()
    }

    private fun livewireGenreRequest(
        genres: List<Genre>,
        page: Int,
    ): Request {
        val explorerUrl = "$baseUrl/explorer/all?page=$page"

        val explorerResponse = client
            .newCall(GET(explorerUrl, headers))
            .execute()

        val explorerDocument = explorerResponse.use { it.asJsoup() }

        val component = explorerDocument
            .select("[wire:snapshot]")
            .firstOrNull { element ->
                val content = element.outerHtml()

                content.contains("toggleGenres") ||
                    content.contains("Acción", ignoreCase = true)
            }
            ?: throw IOException("No se encontró el filtro de géneros de Akaya")

        val snapshot = component.attr("wire:snapshot")

        if (snapshot.isBlank()) {
            throw IOException("El filtro de géneros no tiene snapshot Livewire")
        }

        val token = explorerDocument
            .selectFirst("meta[name=csrf-token]")
            ?.attr("content")
            .orEmpty()

        val payload = buildJsonObject {
            put("_token", token)

            putJsonArray("components") {
                add(
                    buildJsonObject {
                        put("snapshot", snapshot)
                        putJsonObject("updates") {}

                        putJsonArray("calls") {
                            genres.forEach { genre ->
                                add(
                                    buildJsonObject {
                                        put("method", "toggleGenres")

                                        putJsonArray("params") {
                                            add(JsonPrimitive(genre.id))
                                        }

                                        putJsonObject("metadata") {}
                                    },
                                )
                            }
                        }
                    },
                )
            }
        }

        return Request.Builder()
            .url("$baseUrl/livewire-c4e82cae/update")
            .headers(headers)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("X-Livewire", "true")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Origin", baseUrl)
            .header("Referer", explorerUrl)
            .post(
                Json.encodeToString(payload)
                    .toRequestBody("application/json".toMediaType()),
            )
            .build()
    }

    override fun searchMangaParse(response: Response): MangasPage {
        if (!response.request.url.toString().contains("/livewire-c4e82cae/update")) {
            return parseMangaList(response)
        }

        val json = response.parseAs<JsonObject>()

        val html = json["components"]
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("effects")
            ?.jsonObject
            ?.get("html")
            ?.jsonPrimitive
            ?.content
            .orEmpty()

        if (html.isBlank()) {
            return MangasPage(emptyList(), false)
        }

        val document = Jsoup.parse(html, baseUrl)

        val mangas = document
            .select("a[href*=\"/serie/\"]")
            .mapNotNull { link ->
                val card = link.closest("div[role=link]") ?: link.parent()
                val image = card?.selectFirst("img")

                val titleCandidates = listOf(
                    card?.selectFirst("h1, h2, h3, h4")?.text(),
                    card?.selectFirst("[class*=\"title\"], [class*=\"name\"]")?.text(),
                    link.attr("aria-label"),
                    link.attr("title"),
                    link.text(),
                    image?.attr("alt"),
                    card?.text(),
                )

                val title = titleCandidates
                    .asSequence()
                    .flatMap { value ->
                        value
                            .orEmpty()
                            .split("\n")
                            .asSequence()
                    }
                    .map { it.trim() }
                    .firstOrNull { candidate ->
                        candidate.isNotBlank() &&
                            !candidate.equals("Leer", ignoreCase = true) &&
                            !candidate.equals("card image", ignoreCase = true)
                    }
                    .orEmpty()

                if (title.isBlank()) {
                    return@mapNotNull null
                }

                val cleanTitle = title
                    .replace(Regex("\\s+series\\s*$", RegexOption.IGNORE_CASE), "")
                    .trim()

                if (cleanTitle.isBlank()) {
                    return@mapNotNull null
                }

                SManga.create().apply {
                    setUrlWithoutDomain(link.attr("href"))
                    this.title = cleanTitle
                    thumbnail_url = image?.absUrl("src")
                }
            }
            .distinctBy { it.url }

        val hasNextPage = document
            .select("nav[aria-label='Pagination Navigation'] button")
            .any { it.attr("wire:click").contains("nextPage") }

        return MangasPage(mangas, hasNextPage)
    }

    private fun parseMangaList(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document
            .select(
                "div[role=link]:has(img[src*=\"api.akayamedia.com/content/\"])",
            )
            .mapNotNull { element ->
                val link = element.selectFirst("a[href*=\"/serie/\"]")
                    ?: return@mapNotNull null

                val image = element.selectFirst(
                    "img[src*=\"api.akayamedia.com/content/\"]",
                ) ?: return@mapNotNull null

                SManga.create().apply {
                    setUrlWithoutDomain(link.attr("href"))
                    title = image.attr("alt").ifBlank {
                        element.selectFirst("h1")?.text().orEmpty()
                    }
                    thumbnail_url = image.attr("abs:src")
                }
            }

        val hasNextPage = document
            .select("nav[aria-label='Pagination Navigation'] button")
            .any { it.attr("wire:click").contains("nextPage") }

        return MangasPage(mangas, hasNextPage)
    }

    override fun getFilterList() = FilterList(
        Filter.Header("Los filtros se ignorarán al hacer una búsqueda por texto"),
        Filter.Separator(),
        OrderFilter(),
        GenreFilter(),
    )

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        val header = document.selectFirst("header.masthead > div.container > div.row")

        val statusText = document
            .selectFirst("span.text-sm.whitespace-nowrap")
            ?.text()
            ?.trim()
            .orEmpty()

        val genres = document
            .select("span")
            .firstOrNull { it.text().trim() == "Géneros" }
            ?.parent()
            ?.parent()
            ?.select("ul li span")
            ?.eachText()
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.distinct()
            .orEmpty()

        val authors = document
            .select("a[href*=\"/user/\"] .truncate")
            .eachText()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        return SManga.create().apply {
            title = header
                ?.selectFirst(".serie-head-title")
                ?.text()
                .orEmpty()

            author = authors.joinToString(", ")

            genre = genres.joinToString(", ")

            status = when {
                statusText.contains("finalizada", ignoreCase = true) ->
                    SManga.COMPLETED

                statusText.contains("cancelada", ignoreCase = true) ->
                    SManga.CANCELLED

                else ->
                    SManga.ONGOING
            }

            description = document
                .select("p.text-gray-500.text-sm")
                .firstOrNull()
                ?.text()
                ?.trim()
                .orEmpty()

            thumbnail_url = document
                .selectFirst("meta[property=og:image]")
                ?.attr("content")
                ?.replace("/chapters/", "/content/")
        }
    }

    override fun chapterListRequest(manga: SManga): Request = GET(baseUrl + manga.url + "?order_direction=desc", headers)

    private fun parseChapters(document: org.jsoup.nodes.Document): List<SChapter> {
        return document
            .select("#chapters-container a[href*=\"/chapter/\"]")
            .mapNotNull { link ->
                val url = link.attr("href")
                if (url.isBlank()) return@mapNotNull null

                val chapterName = link.text()
                    .trim()
                    .ifBlank {
                        link.parent()?.text()?.trim().orEmpty()
                    }

                if (chapterName.isBlank()) return@mapNotNull null

                val chapterElement = link.parent()
                val date = chapterElement
                    ?.selectFirst("span.text-gray-300.text-sm")
                    ?.text()
                    ?.trim()

                SChapter.create().apply {
                    setUrlWithoutDomain(url)
                    name = chapterName
                    date_upload = dateFormat.tryParse(date)
                }
            }
    }

    private fun livewirePageRequest(
        snapshot: String,
        token: String,
        page: Int,
        referer: String,
    ): Request {
        val payload = buildJsonObject {
            put("_token", token)
            putJsonArray("components") {
                add(
                    buildJsonObject {
                        put("snapshot", snapshot)
                        putJsonObject("updates") {}
                        putJsonArray("calls") {
                            add(
                                buildJsonObject {
                                    put("method", "gotoPage")
                                    putJsonArray("params") {
                                        add(JsonPrimitive(page))
                                        add(JsonPrimitive("page"))
                                    }
                                    putJsonObject("metadata") {}
                                },
                            )
                        }
                    },
                )
            }
        }

        return Request.Builder()
            .url("$baseUrl/livewire-c4e82cae/update")
            .headers(headers)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("X-Livewire", "true")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Referer", referer)
            .header("Origin", baseUrl)
            .post(
                Json.encodeToString(payload)
                    .toRequestBody("application/json".toMediaType()),
            )
            .build()
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chapters = mutableListOf<SChapter>()

        fun formatChapters(): List<SChapter> {
            val uniqueChapters = chapters
                .distinctBy { it.url }
                .reversed()

            return uniqueChapters
                .mapIndexed { index, chapter ->
                    chapter.apply {
                        name = "Cap ${uniqueChapters.size - index}"
                    }
                }
        }

        chapters += parseChapters(document)

        val snapshotElement = document
            .select("*")
            .firstOrNull {
                it.hasAttr("wire:snapshot") &&
                    it.attr("wire:snapshot").contains("serie.index")
            }

        var snapshot = snapshotElement?.attr("wire:snapshot")
            ?: return formatChapters()

        val token = document
            .selectFirst("meta[name=csrf-token]")
            ?.attr("content")
            .orEmpty()

        if (token.isEmpty()) {
            return formatChapters()
        }

        var page = 2

        while (true) {
            try {
                val pageResponse = client
                    .newCall(
                        livewirePageRequest(
                            snapshot = snapshot,
                            token = token,
                            page = page,
                            referer = response.request.url.toString().substringBefore("?"),
                        ),
                    )
                    .execute()

                var shouldStop = false

                pageResponse.use {
                    if (!it.isSuccessful) {
                        shouldStop = true
                        return@use
                    }

                    val responseBody = it.body.string()
                    val livewire = responseBody.parseAs<LivewireResponseDto>()

                    val component = livewire.components.firstOrNull()
                    if (component == null) {
                        shouldStop = true
                        return@use
                    }

                    val html = component.effects?.html
                    if (html == null) {
                        shouldStop = true
                        return@use
                    }

                    val pageDocument = org.jsoup.Jsoup.parse(html)
                    val pageChapters = parseChapters(pageDocument)

                    val previousChapterCount = chapters.size
                    chapters += pageChapters

                    if (pageChapters.isEmpty() || chapters.size == previousChapterCount) {
                        shouldStop = true
                        return@use
                    }

                    snapshot = component.snapshot ?: run {
                        shouldStop = true
                        return@use
                    }
                }

                if (shouldStop) {
                    break
                }

                page++
            } catch (_: Exception) {
                break
            }
        }

        return formatChapters()
    }

    override fun pageListRequest(chapter: SChapter): Request {
        if (chapter.url.substringAfterLast("#") == "lock") {
            throw Exception("Capítulo bloqueado")
        }
        return super.pageListRequest(chapter)
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        val imageUrls = document.select("img").mapNotNull { image ->
            listOf(
                image.attr("abs:src"),
                image.attr("abs:data-src"),
                image.attr("abs:data-original"),
                image.attr("abs:data-lazy-src"),
            ).firstOrNull { url ->
                url.isNotBlank() &&
                    (
                        url.contains("api.akayamedia.com") ||
                            url.contains("/chapters/")
                        )
            }
        }

        if (imageUrls.isNotEmpty()) {
            return imageUrls.distinct().mapIndexed { index, imageUrl ->
                Page(index, imageUrl = imageUrl)
            }
        }

        val scriptContent = document
            .select("script")
            .firstOrNull { it.data().contains("chapterData") }
            ?.data()
            .orEmpty()

        if (scriptContent.isNotBlank()) {
            try {
                val jsonString = scriptContent
                    .substringAfter("var chapterData =")
                    .substringBefore(";")
                    .trim()

                val chapterData = jsonString.parseAs<ChapterDataDto>()

                return chapterData.sortedImages.mapIndexed { index, image ->
                    Page(
                        index,
                        imageUrl = "https://api.akayamedia.com/chapters/${image.image}",
                    )
                }
            } catch (_: Exception) {
                // No se encontraron imágenes en chapterData.
            }
        }

        return emptyList()
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    companion object {
        private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("es"))
    }
}
