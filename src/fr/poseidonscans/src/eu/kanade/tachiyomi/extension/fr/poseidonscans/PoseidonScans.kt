package eu.kanade.tachiyomi.extension.fr.poseidonscans

import android.content.SharedPreferences
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
import keiyoushi.utils.extractNextJsRsc
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PoseidonScans :
    HttpSource(),
    ConfigurableSource {

    override val name = "Poseidon Scans"
    override val baseUrl = "https://poseidon-scans.net"
    override val lang = "fr"
    override val supportsLatest = true
    override val versionId = 2

    override val client = network.cloudflareClient

    val rscHeaders = headersBuilder().add("RSC", "1").build()

    private val preferences: SharedPreferences by getPreferencesLazy()

    private fun String.toAbsoluteUrl(): String = if (this.startsWith("http")) this else baseUrl + this

    private fun String.toApiCoverUrl(): String {
        if (this.startsWith("http")) return this
        if (this.contains("storage/covers/")) return "$baseUrl/api/covers/${this.substringAfter("storage/covers/")}"
        if (this.startsWith("/api/covers/")) return baseUrl + this
        if (this.startsWith("/")) return baseUrl + this
        return "$baseUrl/api/covers/$this"
    }

    // found /manga/all too

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/api/manga/lastchapters?limit=16&page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val apiResponse = response.parseAs<LatestApiResponse>()

        val mangas = apiResponse.data.map { apiManga ->
            SManga.create().apply {
                title = apiManga.title
                url = "/serie/${apiManga.slug}"
                thumbnail_url = apiManga.slug.toApiCoverUrl() + ".webp"
            }
        }
        val hasNextPage = mangas.size == 16
        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, rscHeaders)

    override fun popularMangaParse(response: Response): MangasPage {
        val mangaDtos = response.extractNextJs<List<PopularMangaData>>() ?: throw Exception("Cant scape data from Next.js")

        val mangas = mangaDtos.map { mangaDto ->
            SManga.create().apply {
                title = mangaDto.title
                url = "/serie/${mangaDto.slug}"
                thumbnail_url = mangaDto.slug.toApiCoverUrl() + ".webp"
            }
        }
        return MangasPage(mangas, false)
    }

    // =========================== Manga Details ============================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val mangaDto = document.extractNextJs<MangaDetailsData>() ?: throw Exception("Cant scape data from Next.js")

        return SManga.create().apply {
            title = mangaDto.title
            thumbnail_url = "$baseUrl/api/covers/${mangaDto.slug}.webp"
            author = mangaDto.author
            artist = mangaDto.artist

            genre = mangaDto.categories.mapNotNull { it.name.trim().takeIf { name -> name.isNotBlank() } }.joinToString(", ") {
                it.replaceFirstChar { char -> char.titlecase(Locale.FRENCH) }
            }

            status = parseStatus(mangaDto.status)

            description = mangaDto.description.trim().takeIf { it.isNotEmpty() }

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

    // ============================== Chapters ==============================

    override fun chapterListRequest(manga: SManga): Request = GET(baseUrl + manga.url, rscHeaders)

    override fun chapterListParse(response: Response): List<SChapter> {
        val url = response.request.url
        val rscBody = response.body.string()
        val chapters = chapterListRsc(rscBody)
        if (chapters.isNotEmpty()) return chapters

        // RSC data can be partial on first load; retry with cache-busting
        val retryUrl = url.newBuilder().addQueryParameter("_", System.currentTimeMillis().toString()).build()
        val retryRequest = response.request.newBuilder().url(retryUrl).header("Cache-Control", "no-cache").build()
        val retryResponse = client.newCall(retryRequest).execute()
        return chapterListRsc(retryResponse.body.string())
    }

    fun chapterListRsc(rscBody: String): List<SChapter> {
        val mangaPageDto = rscBody.extractNextJsRsc<MangaPageDetailsData>() ?: throw Exception("Cant scape data from Next.js")

        val showPremium = preferences.getBoolean(
            SHOW_PREMIUM_KEY,
            SHOW_PREMIUM_DEFAULT,
        )
        return mangaPageDto.manga.chapters.mapNotNull { ch ->
            val isLocked = ch.isPremium == true && mangaPageDto.isPremiumUser != true

            if (isLocked && !showPremium) {
                val premiumUntilDate = ch.premiumUntil?.time ?: 0L
                if (System.currentTimeMillis() <= premiumUntilDate) return@mapNotNull null
            }
            SChapter.create().apply {
                val chapterNumberString = ch.number.toString().removeSuffix(".0")
                val isVolume = ch.isVolume == true || (ch.number % 1 == 0f && ch.title?.contains("volume", ignoreCase = true) == true)

                val baseName = if (isVolume) {
                    "Volume $chapterNumberString"
                } else {
                    "Chapitre $chapterNumberString"
                }
                val title = ch.title?.trim()?.takeIf { it.isNotBlank() }

                name = buildString {
                    if (isLocked) append("🔒 ")

                    append(
                        if (title != null) {
                            "$baseName - $title"
                        } else {
                            baseName
                        },
                    )

                    if (isLocked) {
                        val dateParts = formatTimestamp(
                            ch.premiumUntil?.time ?: 0L,
                        ).split(" ")
                        // formatTimestamp gives: [dd, MMMM, HH:mm]
                        append(
                            " - Free the ${dateParts.take(2).joinToString(" ")} at ${dateParts.getOrNull(2) ?: ""}",
                        )
                    }
                }.trim()
                setUrlWithoutDomain(
                    "/serie/${mangaPageDto.manga.slug}/chapter/$chapterNumberString",
                )
                date_upload = ch.createdAt.time
                chapter_number = ch.number
            }
        }.sortedByDescending { it.chapter_number }
    }

    fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd MMMM HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    // =============================== Pages ================================

    override fun pageListRequest(chapter: SChapter): Request = GET(baseUrl + chapter.url, rscHeaders)

    override fun pageListParse(response: Response): List<Page> {
        val pageDataDto = response.extractNextJs<PageData>() ?: throw Exception("Cant scape data from Next.js")
        if (pageDataDto.currentChapter.isPremium) {
            if (pageDataDto.sessionStatus == "unauthenticated") {
                throw Exception("This chapter is premium. Please connect via the WebView to view.")
            }
            if (!pageDataDto.isPremiumUser) {
                throw Exception("This chapter is premium. You are not a premium user.")
            }
        }
        return pageDataDto.initialData.images.map { pageDto ->
            Page(
                index = pageDto.order,
                imageUrl = pageDto.originalUrl.toAbsoluteUrl(),
            )
        }.sortedBy { it.index }
    }

    override fun imageRequest(page: Page): Request {
        val refererUrl = page.url
        val imageHeaders = headersBuilder().set(
            "Accept",
            "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8",
        ).set("Referer", refererUrl.ifBlank { "$baseUrl/" }).build()
        return GET(page.imageUrl!!, imageHeaders)
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // =============================== Search ===============================

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

        val mangas = document.select("div.grid a.block.group").map { element ->
            val url = element.attr("href")
            val title = element.selectFirst("h2")?.text()!!

            val thumbnailUrlPath = element.selectFirst("img[alt]")?.attr("srcset")?.substringBefore(" ")?.let {
                URLDecoder.decode(it, "UTF-8").substringAfter("url=").substringBefore("&")
            }

            SManga.create().apply {
                this.setUrlWithoutDomain(url)
                this.title = title
                this.thumbnail_url = thumbnailUrlPath?.takeIf { it.isNotBlank() }?.toApiCoverUrl()
            }
        }

        val hasNextPage = document.select("nav[aria-label=Pagination] a:contains(Suivant)").isNotEmpty()

        return MangasPage(mangas, hasNextPage)
    }

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
