package eu.kanade.tachiyomi.extension.es.akaya

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class Akaya : KeiSource() {
    private val baseUrlHost by lazy { baseUrl.toHttpUrl().host }

    override val supportsLatest = true

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor { chain ->
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
    }.rateLimit(1, 1.seconds) { it.host == baseUrlHost }

    override fun Headers.Builder.configureHeaders(): Headers.Builder = set("Referer", "$baseUrl/")

    override suspend fun getPopularManga(page: Int): MangasPage = parseMangaList(
        client.get(
            "$baseUrl/collection/bd90cb43-9bf2-4759-b8cc-c9e66a526bc6?page=$page",
        ),
    )

    override suspend fun getLatestUpdates(page: Int): MangasPage = parseMangaList(
        client.get("$baseUrl/explorer/all?page=$page"),
    )

    override suspend fun getSearchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage {
        if (query.isNotEmpty()) {
            return parseLivewireMangaList(
                livewireSearch(query),
            )
        }

        val selectedGenres = filters
            .filterIsInstance<GenreFilter>()
            .firstOrNull()
            ?.state
            ?.filter { it.state }
            .orEmpty()

        if (selectedGenres.isNotEmpty()) {
            return parseLivewireMangaList(
                livewireGenreSearch(
                    genres = selectedGenres,
                    page = page,
                ),
            )
        }

        return parseMangaList(
            client.get("$baseUrl/explorer/all?page=$page"),
        )
    }

    private suspend fun livewireSearch(query: String): Response {
        val homeDocument = client
            .get(baseUrl)
            .asJsoup()

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

        val requestHeaders = headersBuilder()
            .set("Accept", "application/json")
            .set("X-Livewire", "1")
            .set("Origin", baseUrl)
            .set("Referer", "$baseUrl/")
            .build()

        return client.post(
            "$baseUrl/livewire-c4e82cae/update",
            requestHeaders,
            payload.toJsonRequestBody(),
        )
    }

    private suspend fun livewireGenreSearch(
        genres: List<Genre>,
        page: Int,
    ): Response {
        val explorerUrl = "$baseUrl/explorer/all?page=$page"

        val explorerDocument = client
            .get(explorerUrl)
            .asJsoup()

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

        val requestHeaders = headersBuilder()
            .set("Accept", "application/json")
            .set("X-Livewire", "true")
            .set("X-Requested-With", "XMLHttpRequest")
            .set("Origin", baseUrl)
            .set("Referer", explorerUrl)
            .build()

        return client.post(
            "$baseUrl/livewire-c4e82cae/update",
            requestHeaders,
            payload.toJsonRequestBody(),
        )
    }

    private fun parseLivewireMangaList(response: Response): MangasPage {
        if (!response.request.url.toString().contains("/livewire-c4e82cae/update")) {
            return parseMangaList(response)
        }

        val livewire = response.parseAs<LivewireResponseDto>()

        val html = livewire.components
            .firstOrNull()
            ?.effects
            ?.html
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

    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("Los filtros se ignorarán al hacer una búsqueda por texto"),
        Filter.Separator(),
        OrderFilter(),
        GenreFilter(),
    )

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client
            .get(getMangaUrl(manga))
            .asJsoup()

        val updatedManga = parseMangaDetails(document)
        val updatedChapters = parseChaptersWithPagination(document)

        return SMangaUpdate(
            updatedManga,
            updatedChapters,
        )
    }

    private fun parseMangaDetails(document: Document): SManga {
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

    private suspend fun parseChaptersWithPagination(
        document: Document,
    ): List<SChapter> {
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
                            referer = document.location().substringBefore("?"),
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

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        if (chapter.url.substringAfterLast("#") == "lock") {
            throw Exception("Capítulo bloqueado")
        }

        val document = client
            .get(getChapterUrl(chapter))
            .asJsoup()

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

    companion object {
        private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("es"))
    }
}
