package eu.kanade.tachiyomi.extension.es.ragnascans

import android.util.Base64
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getStringOrNull
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class RagnaScans : KeiSource() {

    // Limit to 1 request per second to prevent HTTP 429 on parallel manga update fetches
    override fun OkHttpClient.Builder.configureClient() = rateLimit(1) {
        it.host == baseUrl.toHttpUrl().host &&
            when (it.pathSegments.firstOrNull()) {
                "manga", "manga.php" -> true
                else -> false
            }
    }

    private val dateFormat = DateTimeFormatter.ofPattern("dd MMMM, yyyy", Locale.forLanguageTag("es")).withZone(ZoneId.of("UTC"))

    // ============================== Popular ==============================
    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get("$baseUrl/directorio.php?page=$page&orden=vistas&q=").asJsoup()
        return parseMangaList(document)
    }

    // ============================== Latest ===============================
    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val document = client.get("$baseUrl/directorio.php?page=$page&orden=actualizado&q=").asJsoup()
        return parseMangaList(document)
    }

    private fun parseMangaList(document: Document): MangasPage {
        val mangas = document.select(".mod-grid .mod-card").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                title = element.selectFirst(".mod-card-title")!!.text()
                thumbnail_url = element.selectFirst(".mod-card-cover")?.absUrl("src")
            }
        }
        val hasNextPage = document.selectFirst(".mod-pg-btn:contains(Sig)") != null
        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Search ===============================
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/directorio.php".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            addQueryParameter("q", query)

            filters.firstInstanceOrNull<GenreFilter>()?.state
                ?.filter { it.state }
                ?.forEach { addQueryParameter("generos[]", it.value) }

            filters.firstInstanceOrNull<StatusFilter>()?.state
                ?.filter { it.state }
                ?.forEach { addQueryParameter("estado[]", it.value) }

            filters.firstInstanceOrNull<TypeFilter>()?.state
                ?.filter { it.state }
                ?.forEach { addQueryParameter("tipo[]", it.value) }

            filters.firstInstanceOrNull<SortFilter>()?.let {
                addQueryParameter("orden", it.selectedValue)
            }
        }.build()

        return parseMangaList(client.get(url).asJsoup())
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        if (url.encodedPath != "/manga.php" && url.encodedPathSegments.firstOrNull() != "manga") return null
        if (url.encodedPath == "/manga.php" && url.queryParameter("id") == null) return null
        if (url.encodedPathSegments.firstOrNull() == "manga" && url.encodedPathSegments.getOrNull(1) == null) return null

        val document = client.get(url).asJsoup()
        val mangaId = document
            .selectFirst("[data-manga-id]")
            ?.attrOrNull("data-manga-id")
            ?: throw Exception("Failed to extract manga ID")

        return parseMangaDetails(document).apply {
            this.url = "/manga.php?id=$mangaId"
            initialized = true
        }
    }

    // ============================== Details ==============================
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        return SMangaUpdate(parseMangaDetails(document), parseChapterList(document))
    }

    override fun getMangaUrl(manga: SManga): String {
        // manga.php 301s to slug, memo skips request
        return manga.memo.getStringOrNull("slug")?.let { "$baseUrl/manga/$it" } ?: super.getMangaUrl(manga)
    }

    private fun parseMangaDetails(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("h1")!!.text()
        thumbnail_url = document.selectFirst(".cover-wrapper img")?.absUrl("src")

        val infoWrap = document.select(".flex.flex-wrap.items-center.gap-x-3")
        author = infoWrap.selectFirst("span:contains(Autor:)")?.text()?.substringAfter("Autor:")?.trim()
        artist = infoWrap.selectFirst("span:contains(Ilustrador:)")?.text()?.substringAfter("Ilustrador:")?.trim()

        description = document.selectFirst("#sinopsisWrapper p")?.text()

        val metaRows = document.select(".meta-table .meta-row")

        genre = metaRows.find { it.selectFirst(".meta-label")?.text()?.lowercase()?.contains("género") == true }
            ?.select(".meta-value a")?.joinToString { it.text() }

        val statusText = metaRows.find { it.selectFirst(".meta-label")?.text()?.lowercase()?.contains("estado") == true }
            ?.selectFirst(".meta-value")?.text()?.lowercase()

        status = when (statusText) {
            "emision", "en emisión", "en emision" -> SManga.ONGOING
            "finalizado" -> SManga.COMPLETED
            "hiatus", "pausado" -> SManga.ON_HIATUS
            "cancelado" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }

        // Memo slug to skip 301
        document.location()
            .toHttpUrlOrNull()
            ?.encodedPathSegments
            ?.takeIf { it.firstOrNull() == "manga" }
            ?.getOrNull(1)
            ?.let { slug -> memo = buildJsonObject { put("slug", slug) } }
    }

    // ============================= Chapters ==============================
    private fun parseChapterList(document: Document): List<SChapter> {
        return document.select("#chaptersContainer .chapter-item").mapNotNull { element ->
            // Filter out premium/locked chapters
            if (element.hasClass("locked-neon") || element.selectFirst(".ph-lock-key") != null) {
                return@mapNotNull null
            }

            SChapter.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                name = element.selectFirst(".chapter-item-title h4")!!.text().removeSuffix(".00")
                date_upload = dateFormat.tryParseDate(element.selectFirst(".chapter-item-date")?.text())
            }
        }
    }

    // =============================== Pages ===============================
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        return document.select("#pagesContainer .page-container img").mapIndexedNotNull { index, img ->
            val dataVerify = img.attr("data-verify")

            if (dataVerify.isNotEmpty()) {
                try {
                    val decodedBytes = Base64.decode(dataVerify, Base64.DEFAULT)
                    val reversedUrl = String(decodedBytes).reversed()
                    val imageUrl = when {
                        reversedUrl.startsWith("http") -> reversedUrl
                        reversedUrl.startsWith("//") -> "https:$reversedUrl"
                        else -> baseUrl + reversedUrl
                    }
                    Page(index, imageUrl = imageUrl)
                } catch (_: Exception) {
                    null
                }
            } else {
                val src = img.absUrl("src")
                if (src.isNotEmpty() && !src.startsWith("data:image")) {
                    Page(index, imageUrl = src)
                } else {
                    null
                }
            }
        }
    }

    // ============================== Filters ==============================
    override fun getFilterList(data: JsonElement?) = FilterList(
        GenreFilter(),
        StatusFilter(),
        TypeFilter(),
        Filter.Separator(),
        SortFilter(),
    )
}
