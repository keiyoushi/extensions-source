package eu.kanade.tachiyomi.extension.ja.piccoma

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Source
abstract class Piccoma :
    KeiSource(),
    ConfigurableSource {
    private val preferences by getPreferencesLazy()
    private val xHeaders get() = headersBuilder()
        .set("X-Requested-With", "XMLHttpRequest")
        .build()

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(ImageInterceptor())

    override suspend fun getPopularManga(page: Int): MangasPage = getRanking("K/P/0")

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val requestUrl = "$baseUrl/web/weekday/product/list".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .build()

        val document = client.get(requestUrl).asJsoup()
        val mangas = document.select("li a:has(div.PCOM-prdList_info)").map {
            SManga.create().apply {
                url = it.absUrl("href").toHttpUrl().pathSegments.last()
                title = it.selectFirst(".PCOM-prdList_title span")!!.text()
                thumbnail_url = it.selectFirst("img")?.absUrl("src")?.toCoverUrl()
            }
        }
        val hasNextPage = document.selectFirst("#js_nextPage") != null
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val ranking = filters.firstInstance<RankingFilter>().value
        if (query.isNotBlank()) {
            val url = "$baseUrl/web/search/result_ajax/list".toHttpUrl().newBuilder()
                .addQueryParameter("word", query)
                .addQueryParameter("page", page.toString())
                .addQueryParameter("tab_type", "T")
                .build()

            val result = client.get(url, xHeaders).parseAs<SearchResponseDto>()
            val mangas = result.data.products
                .filter { it.isAudio != 1 && it.isAnime != 1 }
                .map { it.toSManga() }

            val hasNextPage = page < result.data.totalPage
            return MangasPage(mangas, hasNextPage)
        }

        return getRanking(ranking)
    }

    private suspend fun getRanking(path: String): MangasPage {
        val document = client.get("$baseUrl/web/ranking/$path").asJsoup()
        val mangas = document.select("section.PCM-productRanking li > a").map {
            SManga.create().apply {
                url = it.absUrl("href").toHttpUrl().pathSegments.last()
                title = it.selectFirst(".PCM-rankingProduct_title p")!!.text()
                thumbnail_url = it.selectFirst("img.js_lazy")?.absUrl("data-original")?.toCoverUrl()
            }
        }

        return MangasPage(mangas, false)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = async {
            if (!fetchDetails) return@async manga
            val document = client.get(getMangaUrl(manga)).asJsoup()
            val statusText = document.selectFirst("ul.PCM-productStatus")?.text()

            SManga.create().apply {
                title = document.selectFirst("h1.PCM-productTitle")!!.text()
                author = document.select("ul.PCM-productAuthor li a").joinToString { it.text() }
                genre = document.select("ul.PCM-productGenre li a, .PCM-productDesc_tagList li a").joinToString { it.text() }
                description = document.selectFirst("div.PCM-productDesc > p")?.text()
                thumbnail_url = document.selectFirst("img.PCM-productThum_img")?.absUrl("src")?.toCoverUrl()
                status = when {
                    statusText?.contains("連載中") == true -> SManga.ONGOING
                    statusText?.contains("完結") == true -> SManga.COMPLETED
                    else -> SManga.UNKNOWN
                }
            }
        }
        val chapterList = async {
            if (!fetchChapters) return@async chapters
            val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
            val volumes = async { getVolumes(manga, hideLocked) }
            val episodes = async { getEpisodes(manga, hideLocked) }

            (volumes.await() + episodes.await()).reversed()
        }

        SMangaUpdate(
            details.await(),
            chapterList.await(),
        )
    }

    private suspend fun getEpisodes(manga: SManga, hideLocked: Boolean): List<SChapter> {
        val document = client.get("${getMangaUrl(manga)}/episodes?etype=E").asJsoup()
        val mangaTitle = document.selectFirst(".PCM-headTitle_name")?.text()

        return document.selectFirst("ul#js_episodeList")?.select("li").orEmpty().mapNotNull {
            val link = it.selectFirst("a") ?: return@mapNotNull null
            val status = it.selectFirst("div.PCM-epList_status")

            val isPoint = status?.selectFirst(".PCM-epList_status_point") != null
            val isWaitFree = status?.selectFirst(".PCM-epList_status_waitfree") != null
            val isZeroPlus = status?.selectFirst(".PCM-epList_status_zeroPlus") != null

            if (hideLocked && (isPoint || isWaitFree || isZeroPlus)) return@mapNotNull null

            val icon = when {
                isPoint -> "🔒 "
                isWaitFree || isZeroPlus -> "➡️ "
                else -> ""
            }

            val title = it.selectFirst("div.PCM-epList_title h2")!!.text()
            SChapter.create().apply {
                url = link.attr("data-episode_id")
                name = icon + title.stripTitle(mangaTitle)
                memo = buildJsonObject {
                    put("productId", link.attr("data-product_id"))
                }
            }
        }
    }

    private suspend fun getVolumes(manga: SManga, hideLocked: Boolean): List<SChapter> {
        val document = client.get("${getMangaUrl(manga)}/episodes?etype=V").asJsoup()
        val mangaTitle = document.selectFirst(".PCM-headTitle_name")?.text()

        return document.selectFirst("ul#js_volumeList")?.select("li").orEmpty().mapNotNull {
            val freeBtn = it.selectFirst(".PCM-prdVol_freeBtn")
            val buyBtn = it.selectFirst(".PCM-prdVol_buyBtn")
            val trialBtn = it.selectFirst(".PCM-prdVol_trialBtn")

            if (hideLocked && freeBtn == null && (buyBtn != null || trialBtn != null)) return@mapNotNull null

            val button = freeBtn ?: trialBtn ?: buyBtn ?: it.selectFirst("[data-episode_id]") ?: return@mapNotNull null
            val icon = when {
                freeBtn != null -> ""
                trialBtn != null -> "🔒 (Preview) "
                buyBtn != null -> "🔒 "
                else -> ""
            }

            val title = it.selectFirst("div.PCM-prdVol_title h2")!!.text()
            SChapter.create().apply {
                url = button.attr("data-episode_id")
                name = icon + title.stripTitle(mangaTitle)
                memo = buildJsonObject {
                    put("productId", button.attr("data-product_id"))
                }
            }
        }
    }

    private fun String.stripTitle(mangaTitle: String?): String = if (mangaTitle != null) replace(mangaTitle, "").trim() else this

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
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
        val images = pData.img ?: pData.contents.orEmpty()
        val scrambled = if (pData.isScrambled) "#scrambled" else ""

        return images.filter { it.path.isNotEmpty() }.mapIndexed { i, img ->
            Page(i, imageUrl = "https:${img.path}$scrambled")
        }
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/web/product/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/web/viewer/${chapter.memo["productId"]!!.string}/${chapter.url}"

    override fun getFilterList(data: JsonElement?) = FilterList(
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
}
