package eu.kanade.tachiyomi.extension.fr.poseidonscans

import android.content.SharedPreferences
import android.util.Log
import androidx.preference.CheckBoxPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.getValue

class PoseidonScans :
    HttpSource(),
    ConfigurableSource {

    override val name = "Poseidon Scans"
    override val baseUrl = "https://poseidon-scans.co"
    override val lang = "fr"
    override val supportsLatest = true
    override val versionId = 2

    override val client = network.cloudflareClient

    private val preferences: SharedPreferences by getPreferencesLazy()

    private fun String.toAbsoluteUrl(): String = if (this.startsWith("http")) this else baseUrl + this

    private fun String.toApiCoverUrl(): String {
        if (this.startsWith("http")) return this
        if (this.contains("storage/covers/")) return "$baseUrl/api/covers/${this.substringAfter("storage/covers/")}"
        if (this.startsWith("/api/covers/")) return baseUrl + this
        if (this.startsWith("/")) return baseUrl + this
        return "$baseUrl/api/covers/$this"
    }

    private val isoDateFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    // found /manga/all too

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/api/manga/lastchapters?limit=16&page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val apiResponse = try {
            response.parseAs<LatestApiResponse>()
        } catch (e: Exception) {
            return MangasPage(emptyList(), false)
        }

        val mangas = apiResponse.data.mapNotNull { apiManga ->
            if (apiManga.slug.isBlank()) {
                return@mapNotNull null
            }
            SManga.create().apply {
                title = apiManga.title
                url = "/serie/${apiManga.slug}"
                thumbnail_url = apiManga.coverImage?.takeIf { it.isNotBlank() }?.toApiCoverUrl()
            }
        }
        val hasNextPage = mangas.size == 16
        return MangasPage(mangas, hasNextPage)
    }

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/api/manga/popular?limit=16&page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val apiResponse = try {
            response.parseAs<LatestApiResponse>()
        } catch (e: Exception) {
            return MangasPage(emptyList(), false)
        }

        val mangas = apiResponse.data.mapNotNull { apiManga ->
            if (apiManga.slug.isBlank()) {
                return@mapNotNull null
            }
            SManga.create().apply {
                title = apiManga.title
                url = "/serie/${apiManga.slug}"
                thumbnail_url = apiManga.coverImage?.takeIf { it.isNotBlank() }?.toApiCoverUrl()
            }
        }
        val hasNextPage = mangas.size == 16
        return MangasPage(mangas, hasNextPage)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val mangaDto = response.extractNextJs<MangaDetailsData>()
            ?: throw Exception("Cant scape data from nextjs")

        return SManga.create().apply {
            title = mangaDto.title
            thumbnail_url = "$baseUrl/api/covers/${mangaDto.slug}.webp"
            author = mangaDto.author?.takeIf { it.isNotBlank() }
            artist = mangaDto.artist?.takeIf { it.isNotBlank() }

            val genresList = mangaDto.categories.map { it.name.trim() }.filter { it.isNotBlank() }.toMutableList()
            genre = genresList.joinToString(", ") { genreName ->
                genreName.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.FRENCH) else it.toString() }
            }

            status = parseStatus(mangaDto.status)

            description = mangaDto.description
            setUrlWithoutDomain("/serie/${mangaDto.slug}")
        }
    }

    private fun parseStatus(statusString: String?): Int = when (statusString?.trim()?.lowercase(Locale.FRENCH)) {
        "en cours" -> SManga.ONGOING
        "terminé" -> SManga.COMPLETED
        "en pause", "hiatus" -> SManga.ON_HIATUS
        "annulé", "abandonné" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val mangaDto = response.extractNextJs<MangaDetailsData>()
            ?: throw Exception("Cant scape data from nextjs")
        val showPremium = preferences.getBoolean(
            SHOW_PREMIUM_KEY,
            SHOW_PREMIUM_DEFAULT,
        )
        return mangaDto.chapters
            .mapNotNull { ch ->
                // If chapter is premium, check if premium period has expired
                if (ch.isPremium == true && !showPremium) {
                    ch.premiumUntil?.let { premiumUntilString ->
                        val premiumUntilDate = parseIsoDate(premiumUntilString)
                        if (premiumUntilDate > 0) {
                            // Exclude if premium period is still active
                            if (System.currentTimeMillis() <= premiumUntilDate) {
                                return@mapNotNull null
                            }
                        } else {
                            // If we can't parse the premium until date, exclude the chapter for safety
                            return@mapNotNull null
                        }
                    } ?: return@mapNotNull null // If premiumUntil is null but isPremium is true, exclude
                }
                val chapterNumberString = ch.number.toString().removeSuffix(".0")
                SChapter.create().apply {
                    val isVolume = ch.isVolume == true || (
                        ch.number == ch.number.toInt().toFloat() &&
                            ch.title?.lowercase()?.contains("volume") == true
                        )

                    val baseName = if (isVolume) {
                        "Volume $chapterNumberString"
                    } else {
                        "Chapitre $chapterNumberString"
                    }

                    if (ch.isPremium == true) {
                        val splittedDate = formatTimestamp(parseIsoDate(ch.premiumUntil)).split(" ")
                        scanlator = buildString {
                            append("Free the ")
                            append(splittedDate[0])
                            append(" ")
                            append(splittedDate[1])
                            append(" at ")
                            append(splittedDate[2])
                        }
                    }

                    name = ch.title?.trim()?.takeIf { it.isNotBlank() }
                        ?.let { title ->
                            buildString {
                                if (ch.isPremium == true) append("🔒 ")
                                append("$baseName - $title")
                            }
                        }
                        ?: buildString {
                            if (ch.isPremium == true) append("🔒 ")
                            append(baseName)
                        }
                    setUrlWithoutDomain("/serie/${mangaDto.slug}/chapter/$chapterNumberString")
                    date_upload = parseIsoDate(ch.createdAt)
                    chapter_number = ch.number
                }
            }
            .sortedByDescending { it.chapter_number }
    }

    fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd MMMM HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    private fun parseIsoDate(dateString: String?): Long {
        if (dateString.isNullOrBlank()) return 0L
        val cleanedDateString = if (dateString.startsWith("\"\$D")) {
            dateString.removePrefix("\"\$D").removeSuffix("\"")
        } else if (dateString.startsWith("\$D")) {
            dateString.removePrefix("\$D")
        } else if (dateString.startsWith("\"") && dateString.endsWith("\"") && dateString.length > 2) {
            dateString.substring(1, dateString.length - 1)
        } else {
            dateString
        }
        return isoDateFormatter.tryParse(cleanedDateString)
    }

    override fun pageListParse(response: Response): List<Page> {
        val pageDataDto = response.extractNextJs<PageData>()
            ?: throw Exception("Cant scape data from nextjs")
        if (pageDataDto.currentChapter.isPremium) {
            if (pageDataDto.sessionStatus == "unauthenticated") {
                throw Exception("This chapter is premium. Please connect via the webview to view.")
            }
            if (!pageDataDto.isPremiumUser) {
                throw Exception("This chapter is premium. You are not a premium user.")
            }
        }

        val imagesListJson = pageDataDto.initialData.images
        val imagesDataList = try {
            imagesListJson.toString().parseAs<List<PageImageUrlData>>()
        } catch (e: Exception) {
            throw Exception("Error parsing image list: ${e.message}. JSON: $imagesListJson")
        }

        return imagesDataList.map { pageDto ->
            Page(
                index = pageDto.order,
                imageUrl = pageDto.originalUrl.toAbsoluteUrl(),
            )
        }.sortedBy { it.index }
    }

    override fun imageRequest(page: Page): Request {
        val refererUrl = page.url
        val imageHeaders = headersBuilder()
            .set("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
            .set("Referer", if (refererUrl.isNotBlank()) refererUrl else "$baseUrl/")
            .build()
        return GET(page.imageUrl!!, imageHeaders)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("series")
            if (query.isNotBlank()) {
                addQueryParameter("search", query)
            }
            if (page > 1) {
                addQueryParameter("page", page.toString())
            }
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select("div.grid a.block.group").mapNotNull { element ->
            try {
                val url = element.attr("href").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val title = element.selectFirst("h2")?.text()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null

                val thumbnailUrlPath = element.selectFirst("img[alt]")
                    ?.attr("srcset")
                    ?.substringBefore(" ")
                    ?.let {
                        URLDecoder.decode(it, "UTF-8")
                            .substringAfter("url=")
                            .substringBefore("&")
                    }

                SManga.create().apply {
                    this.setUrlWithoutDomain(url)
                    this.title = title
                    this.thumbnail_url = thumbnailUrlPath?.takeIf { it.isNotBlank() }?.toApiCoverUrl()
                }
            } catch (e: Exception) {
                Log.e("PoseidonScans", "Error parsing manga from HTML element", e)
                null
            }
        }

        val hasNextPage = document.select("nav[aria-label=Pagination] a:contains(Suivant)").isNotEmpty()

        return MangasPage(mangas, hasNextPage)
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not used.")
    override fun getFilterList(): FilterList = FilterList()

    // ========================== Preference =============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        CheckBoxPreference(screen.context).apply {
            key = SHOW_PREMIUM_KEY
            title = "Show premium chapters"
            summary = "Show paid chapters (identified by 🔒) in the list."
            setDefaultValue(SHOW_PREMIUM_DEFAULT)
        }.also(screen::addPreference)
    }

    companion object {
        private const val SHOW_PREMIUM_KEY = "show_premium_chapters"
        private const val SHOW_PREMIUM_DEFAULT = false
    }
}
