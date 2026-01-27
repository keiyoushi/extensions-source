package eu.kanade.tachiyomi.extension.fr.rimuscans

import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.SimpleDateFormat
import java.util.Locale

open class RimuScans : HttpSource() {
    override val name = "Rimu Scans"
    override val baseUrl = "https://rimuscans.com"
    override val lang = "fr"
    override val supportsLatest = true
    override val versionId = 2

    override val client = network.cloudflareClient

    val json = Injekt.get<Json>()

    // override fun headersBuilder() = super.headersBuilder().add("Referer", "$baseUrl/")

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/api/manga?page=$page&limit=50&sortBy=views", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val parsedResponse = response.parseAs<MangaListResponse>()
        return MangasPage(
            parsedResponse.toSMangaList(baseUrl),
            parsedResponse.pagination.hasNextPage,
        )
    }

    // ================================ Recent ================================
    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/api/manga?page=$page&limit=50&sortBy=latest", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // =============================== Search ==============================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        throw UnsupportedOperationException("Search is not supported for this source.")
    }

    override fun searchMangaParse(response: Response): MangasPage {
        throw UnsupportedOperationException("Search is not supported for this source.")
    }

    // =========================== Manga Details ===========================
    override fun mangaDetailsParse(response: Response): SManga {
        return response.asJsoup().let { document ->
            SManga.create().apply {
                title = document.selectFirst("h1")?.text() ?: throw Exception("Title not found")
                url = document.location()

                thumbnail_url =
                    document.selectFirst("div.relative.group img[srcset]")?.parseSrcset()
                Log.d("RimuScans", "Thumbnail URL: $thumbnail_url")

                author = document.selectFirst("div:contains(Auteur) span.text-white.font-semibold")
                    ?.text()
                if (author == "N/A") author = null
                artist = document.selectFirst("div:contains(Artiste) span.text-white.font-semibold")
                    ?.text()
                if (artist == "N/A") artist = null

                description = document.selectFirst("div:has(h2:contains(Synopsis)) p")?.text()

                genre = document.select("span.genre-badge").joinToString(", ") { it.text() }

                status = document.selectFirst("span.rounded-full.bg-gradient-to-r.from-yellow-500")
                    ?.text()?.let {
                        when {
                            it.contains("En cours") -> SManga.ONGOING
                            it.contains("Terminé") -> SManga.COMPLETED
                            it.contains("En pause") -> SManga.ON_HIATUS
                            else -> SManga.UNKNOWN
                        }
                    } ?: SManga.UNKNOWN
            }
        }
    }

    // ========================= Chapter List ==========================
    override fun chapterListParse(response: Response): List<SChapter> {
        throw UnsupportedOperationException("TODO")
    }

    // Recursively searches a JSON tree for a JsonObject containing a specific key.
    private fun findObjectWithKey(
        element: kotlinx.serialization.json.JsonElement,
        key: String,
        containDoMatch: Boolean = false,
    ): kotlinx.serialization.json.JsonObject? {
        Log.d("RimuScans", "Searching for key '$key' in element: $element")
        return when (element) {
            is kotlinx.serialization.json.JsonObject -> {
                if (element.containsKey(key)) return element
                element.values.firstNotNullOfOrNull { findObjectWithKey(it, key) }
            }

            is kotlinx.serialization.json.JsonArray -> {
                element.firstNotNullOfOrNull { findObjectWithKey(it, key) }
            }

            is String -> {
                if (containDoMatch && element.contains(key)) {
                    Log.d("RimuScans", "Found matching string: $element")
                    return kotlinx.serialization.json.JsonObject(
                        mapOf(key to kotlinx.serialization.json.JsonPrimitive(element)),
                    )
                }
                null
            }

            else -> null
        }
    }

    // ========================== Page List =============================

    override fun pageListParse(response: Response): List<Page> {
        throw UnsupportedOperationException("Not defined yet.")
    }

    override fun imageUrlParse(response: Response): String =
        throw UnsupportedOperationException("Not used.")

    // ========================== Utils =============================

    private fun Element.parseSrcset(): String {
        val srcset = this.attr("srcset")
        srcset.split(",").lastOrNull()?.trim()?.split(" ")?.firstOrNull()?.let { path ->
            Log.d("RimuScans", "Parsed srcset path: $path")
            return if (path.isAbsoluteUrl()) path else "$baseUrl$path"
        }
        throw Exception("Failed to parse srcset attribute.")
    }

    private fun String.parseDate(): Long = runCatching {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).parse(this)?.time ?: 0L
    }.getOrNull() ?: 0L

    fun String.isAbsoluteUrl(): Boolean {
        return this.matches(Regex("^(?:[a-z+]+:)?//", RegexOption.IGNORE_CASE))
    }
}
