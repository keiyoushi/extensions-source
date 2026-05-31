package eu.kanade.tachiyomi.extension.ja.piccoma

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
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable

class Piccoma :
    HttpSource(),
    ConfigurableSource {
    override val name = "Piccoma"
    override val baseUrl = "https://piccoma.com"
    override val lang = "ja"
    override val supportsLatest = true

    private val preferences by getPreferencesLazy()
    private val xHeaders = headersBuilder()
        .set("X-Requested-With", "XMLHttpRequest")
        .build()

    override val client = network.client.newBuilder()
        .addInterceptor(ImageInterceptor())
        .build()

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/web/ranking/K/P/0", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("section.PCM-productRanking li > a").map {
            SManga.create().apply {
                setUrlWithoutDomain(it.absUrl("href"))
                title = it.selectFirst(".PCM-rankingProduct_title p")!!.text()
                it.selectFirst("img.js_lazy")?.absUrl("data-original")?.toHttpUrl()?.newBuilder()?.setPathSegment(4, "cover_x3")?.toString()?.let { cover -> thumbnail_url = cover }
            }
        }
        return MangasPage(mangas, false)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/web/weekday/product/list".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("li a:has(div.PCOM-prdList_info)").map {
            SManga.create().apply {
                setUrlWithoutDomain(it.absUrl("href"))
                title = it.selectFirst(".PCOM-prdList_title span")!!.text()
                it.selectFirst("img")?.absUrl("src")?.toHttpUrl()?.newBuilder()?.setPathSegment(4, "cover_x3")?.toString()?.let { cover -> thumbnail_url = cover }
            }
        }
        val hasNextPage = document.selectFirst("#js_nextPage") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val url = "$baseUrl/web/search/result_ajax/list".toHttpUrl().newBuilder()
                .addQueryParameter("word", query)
                .addQueryParameter("page", page.toString())
                .addQueryParameter("tab_type", "T")
                .build()
            return GET(url, xHeaders)
        }
        val rankingPath = filters.firstInstance<RankingFilter>().value
        return GET("$baseUrl/web/ranking/$rankingPath", headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        if (response.request.url.pathSegments[1] == "ranking") {
            return popularMangaParse(response)
        }

        val result = response.parseAs<SearchResponseDto>()
        val mangas = result.data.products
            .filter { it.isAudio != 1 && it.isAnime != 1 }
            .map { it.toSManga() }
        val currentPage = response.request.url.queryParameter("page")!!.toInt()
        val hasNextPage = currentPage < result.data.totalPage
        return MangasPage(mangas, hasNextPage)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val statusText = document.selectFirst("ul.PCM-productStatus")?.text()

        return SManga.create().apply {
            title = document.selectFirst("h1.PCM-productTitle")!!.text()
            author = document.select("ul.PCM-productAuthor li a").joinToString { it.text() }
            genre = document.select("ul.PCM-productGenre li a, .PCM-productDesc_tagList li a").joinToString { it.text() }
            description = document.selectFirst("div.PCM-productDesc > p")?.text()
            document.selectFirst("img.PCM-productThum_img")?.absUrl("src")?.toHttpUrl()?.newBuilder()?.setPathSegment(4, "cover_x3")?.toString()?.let { thumbnail_url = it }
            status = when {
                statusText?.contains("連載中") == true -> SManga.ONGOING
                statusText?.contains("完結") == true -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        val chapterDocument = client.newCall(GET("$baseUrl${manga.url}/episodes?etype=E", headers)).execute().asJsoup()
        val chapterMangaTitle = chapterDocument.selectFirst(".PCM-headTitle_name")?.text()

        val chapterList = chapterDocument.selectFirst("ul#js_episodeList")?.select("li").orEmpty().mapNotNull {
            val link = it.selectFirst("a") ?: return@mapNotNull null
            val productId = link.attr("data-product_id")
            val episodeId = link.attr("data-episode_id")
            val titleElement = it.selectFirst("div.PCM-epList_title h2")?.text()
            val statusElement = it.selectFirst("div.PCM-epList_status")

            val isPoint = statusElement?.selectFirst(".PCM-epList_status_point") != null
            val isWaitFree = statusElement?.selectFirst(".PCM-epList_status_waitfree") != null
            val isZeroPlus = statusElement?.selectFirst(".PCM-epList_status_zeroPlus") != null

            if (hideLocked && (isPoint || isWaitFree || isZeroPlus)) return@mapNotNull null

            val icon = when {
                isPoint -> "🔒 "
                isWaitFree || isZeroPlus -> "➡️ "
                else -> ""
            }

            val chapterName = titleElement?.stripMangaTitle(chapterMangaTitle)

            SChapter.create().apply {
                url = "/web/viewer/$productId/$episodeId"
                name = "$icon$chapterName"
            }
        }

        val volumeDocument = client.newCall(GET("$baseUrl${manga.url}/episodes?etype=V", headers)).execute().asJsoup()
        val volumeMangaTitle = volumeDocument.selectFirst(".PCM-headTitle_name")?.text()

        val volumeList = volumeDocument.selectFirst("ul#js_volumeList")?.select("li").orEmpty().mapNotNull {
            val freeBtn = it.selectFirst(".PCM-prdVol_freeBtn")
            val buyBtn = it.selectFirst(".PCM-prdVol_buyBtn")
            val trialBtn = it.selectFirst(".PCM-prdVol_trialBtn")

            if (hideLocked && freeBtn == null && (buyBtn != null || trialBtn != null)) return@mapNotNull null
            val btn = (freeBtn ?: trialBtn ?: buyBtn ?: it.selectFirst("[data-episode_id]")) ?: return@mapNotNull null
            val icon = when {
                freeBtn != null -> ""
                trialBtn != null -> "🔒 (Preview) "
                buyBtn != null -> "🔒 "
                else -> ""
            }

            val productId = btn.attr("data-product_id")
            val episodeId = btn.attr("data-episode_id")
            val titleElement = it.selectFirst("div.PCM-prdVol_title h2")?.text()
            val chapterName = titleElement?.stripMangaTitle(volumeMangaTitle)

            SChapter.create().apply {
                url = "/web/viewer/$productId/$episodeId"
                name = "$icon$chapterName"
            }
        }

        return Observable.just((volumeList + chapterList).reversed())
    }

    private fun String.stripMangaTitle(mangaTitle: String?) = if (mangaTitle != null) replace(mangaTitle, "").trim() else this

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val script = document.selectFirst("script:containsData(var _pdata_)")?.data()
            ?: throw Exception("Log in via Webview and purchase this product to read.")

        val pDataJson = script.substringAfter("var _pdata_ =")
            .substringBefore("var _rcm_")
            .trim()
            .removeSuffix(";")
            .replace(TITLE_REGEX, "")
            .replace(UNQUOTED_KEY_REGEX, "$1\"$2\":")
            .replace("'", "\"")
            .replace(TRAILING_COMMA_REGEX, "$1")

        val pData = pDataJson.parseAs<PDataDto>()
        val images = pData.img ?: pData.contents ?: emptyList()
        val scrambled = if (pData.isScrambled) "#scrambled" else ""

        return images.filter { it.path.isNotEmpty() }.mapIndexed { i, img ->
            Page(i, imageUrl = "https:${img.path}$scrambled")
        }
    }

    override fun getFilterList() = FilterList(
        RankingFilter(),
    )

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_LOCKED_PREF_KEY
            title = "Hide Locked Chapters"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    companion object {
        private val TITLE_REGEX = Regex("""['"]?title['"]?\s*:\s*['"].*?['"],?""")
        private val UNQUOTED_KEY_REGEX = Regex("""([{,]\s*)([a-zA-Z0-9_]+)\s*:""")
        private val TRAILING_COMMA_REGEX = Regex(""",\s*([}\]])""")
        private const val HIDE_LOCKED_PREF_KEY = "hide_locked"
    }

    override fun chapterListRequest(manga: SManga): Request = throw UnsupportedOperationException()
    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()
    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
