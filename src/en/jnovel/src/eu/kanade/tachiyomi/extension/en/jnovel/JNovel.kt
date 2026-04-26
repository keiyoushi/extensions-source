package eu.kanade.tachiyomi.extension.en.jnovel

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
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
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAsProto
import okhttp3.HttpUrl.Builder
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

class JNovel :
    HttpSource(),
    ConfigurableSource {
    override val name = "J-Novel"
    private val domain = "j-novel.club"
    override val baseUrl = "https://$domain"
    override val lang = "en"
    override val supportsLatest = false

    private val viewerUrl = "https://labs.$domain/embed/v2"
    private val preferences by getPreferencesLazy()
    private val decoder = Decoder()
    private val rscHeaders = headersBuilder()
        .set("rsc", "1")
        .build()

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(ImageInterceptor())
        .addInterceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)
            if (!response.isSuccessful && response.request.url.toString().startsWith(viewerUrl)) {
                throw IOException("Log in via WebView and purchase this chapter to read.")
            }
            response
        }
        .build()

    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/series".toHttpUrl().newBuilder()
            .addQueryParameter("type", "manga")
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, rscHeaders)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.extractNextJs<SeriesResponse>()
        val mangas = result?.seriesList?.series.orEmpty().map { it.toSManga() }
        return MangasPage(mangas, result?.seriesList?.hasNextPage() ?: false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        fun Builder.addFilter(param: String, filter: SelectFilter) = filter.value.takeIf { it.isNotBlank() }?.let { addQueryParameter(param, it) }

        val url = "$baseUrl/series".toHttpUrl().newBuilder().apply {
            addQueryParameter("type", "manga")

            if (query.isNotEmpty()) {
                addQueryParameter("search", query)
            }

            addFilter("sort", filters.firstInstance<SortFilter>())
            addFilter("label", filters.firstInstance<LabelFilter>())
            addFilter("status", filters.firstInstance<StatusFilter>())
            addFilter("rentals", filters.firstInstance<RentalFilter>())
        }.build()
        return GET(url, rscHeaders)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$baseUrl/series/${manga.url}", rscHeaders)

    override fun mangaDetailsParse(response: Response): SManga {
        val result = response.extractNextJs<SeriesDetailsResponse>()
        val creators = result?.volumes?.firstOrNull()?.volume?.creators.orEmpty()
        return requireNotNull(result?.series).toSManga(creators)
    }

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        val result = response.extractNextJs<SeriesDetailsResponse>()
        val title = result?.series?.title
        return result?.volumes.orEmpty().flatMap { volume ->
            val owned = volume.volume?.owned == true
            volume.parts
                .filter { !hideLocked || !it.isLocked(owned) }
                .map { it.toSChapter(title!!, owned) }
        }
            .reversed()
    }

    override fun pageListRequest(chapter: SChapter): Request = GET("$baseUrl/read/${chapter.url}", headers)

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val embedUrl = document.selectFirst("iframe[src^='$viewerUrl']")?.absUrl("src") ?: throw Exception("Log in via WebView and purchase this chapter to read.")
        val embedDocument = client.newCall(GET(embedUrl, headers)).execute().asJsoup()
        val manifestUrlStr = embedDocument.body().absUrl("data-e4p-manifest")
        val manifestUrl = manifestUrlStr.toHttpUrl()
        val manifestResponse = client.newCall(GET(manifestUrlStr, headers)).execute()
        val ticketBytes = manifestResponse.parseAsProto<E4PQSTicket>()
        val decoded = decoder.decodeManifestFull(ticketBytes)
        val pub = decoded.pub
        val manifestQueryNames = manifestUrl.queryParameterNames

        val consumerIdStr = (ticketBytes.consumer + "0".repeat(32)).substring(0, 32)
        val consumerId = consumerIdStr.toByteArray(Charsets.US_ASCII)

        return pub.spine.mapIndexedNotNull { index, link ->
            val h2048 = link.variants.firstOrNull {
                it.link.contains("h2048") && it.image != null
            } ?: return@mapIndexedNotNull null

            val resolved = manifestUrl.resolve(h2048.link) ?: return@mapIndexedNotNull null
            val withAuth = resolved.newBuilder().apply {
                manifestQueryNames.forEach { name ->
                    manifestUrl.queryParameter(name)?.let { setQueryParameter(name, it) }
                }
            }.build()

            val drm = h2048.image?.drm
            val iv = drm?.iv
            val finalUrl = if (drm?.version == EdrmVersion.XEBP && iv != null && iv.size == 32 &&
                decoded.pbexSeed != null && decoded.pbexSeed.size == 48
            ) {
                val xebpFragment = listOf(
                    withAuth.fragment.orEmpty(),
                    XebpDecoder.hex(iv),
                    ticketBytes.contentId,
                    XebpDecoder.hex(consumerId),
                    XebpDecoder.hex(decoded.pbexSeed),
                ).joinToString("\n")
                withAuth.newBuilder().fragment(xebpFragment).build()
            } else {
                withAuth
            }

            Page(index, imageUrl = finalUrl.toString())
        }
    }

    override fun getFilterList() = FilterList(
        SortFilter(),
        LabelFilter(),
        StatusFilter(),
        RentalFilter(),
    )

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_LOCKED_PREF_KEY
            title = "Hide Locked Chapters"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()
    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    companion object {
        private const val HIDE_LOCKED_PREF_KEY = "hide_locked"
    }
}
