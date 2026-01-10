package eu.kanade.tachiyomi.extension.ja.piccoma

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservable
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable

class Piccoma : HttpSource() {
    override val name = "Piccoma"
    override val baseUrl = "https://piccoma.com"
    override val lang = "ja"
    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(ImageInterceptor())
        .build()

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/web/ranking/K/P/0", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("section.PCM-productRanking li > a").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                title = element.selectFirst(".PCM-rankingProduct_title p")!!.text()
                element.selectFirst("img.js_lazy")?.absUrl("data-original")?.let { thumbnail_url = it }
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
        val mangas = document.select("li a:has(div.PCOM-prdList_info)").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                title = element.selectFirst(".PCOM-prdList_title span")!!.text()
                element.selectFirst("img")?.absUrl("src")?.let { thumbnail_url = it }
            }
        }
        val hasNextPage = document.selectFirst("#js_nextPage") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotEmpty()) {
            val url = "$baseUrl/web/search/result_ajax/list".toHttpUrl().newBuilder()
                .addQueryParameter("word", query)
                .addQueryParameter("page", page.toString())
                .addQueryParameter("tab_type", "T")
                .build()
            return GET(url, super.headers.newBuilder().add("x-requested-with", "XMLHttpRequest").build())
        }
        val rankingPath = filters.firstInstance<RankingFilter>().toUriPart()
        return GET("$baseUrl/web/ranking/$rankingPath", headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        if (response.request.url.pathSegments.contains("ranking")) {
            return popularMangaParse(response)
        }

        val result = response.parseAs<SearchResponseDto>()
        val mangas = result.data.products.map {
            SManga.create().apply {
                url = "/web/product/${it.id}"
                title = it.title
                thumbnail_url = "https:${it.img}"
            }
        }
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
            document.selectFirst("img.PCM-productThum_img")?.absUrl("src")?.let { thumbnail_url = it }
            status = when {
                statusText?.contains("連載中") == true -> SManga.ONGOING
                statusText?.contains("完結") == true -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }
    }

    override fun chapterListRequest(manga: SManga): Request {
        return GET("$baseUrl${manga.url}/episodes?etype=E", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val episodes = document.selectFirst("ul#js_episodeList")!!
        val mangaTitle = document.selectFirst(".PCM-headTitle_name")?.text()

        return episodes.select("li").map {
            val link = it.selectFirst("a")!!
            val productId = link.attr("data-product_id")
            val episodeId = link.attr("data-episode_id")
            val titleElement = it.selectFirst("div.PCM-epList_title h2")?.text()
            val statusElement = it.selectFirst("div.PCM-epList_status")

            val isPoint = statusElement?.selectFirst(".PCM-epList_status_point") != null
            val isWaitFree = statusElement?.selectFirst(".PCM-epList_status_waitfree") != null
            val isZeroPlus = statusElement?.selectFirst(".PCM-epList_status_zeroPlus") != null

            val icon = when {
                isPoint -> "🔒 "
                isWaitFree || isZeroPlus -> "➡️ "
                else -> ""
            }

            var chapterName = titleElement
            if (mangaTitle != null) {
                chapterName = chapterName?.replace(mangaTitle, "")?.trim()
            }

            SChapter.create().apply {
                url = "/web/viewer/$productId/$episodeId"
                name = "$icon$chapterName"
            }
        }.reversed()
    }

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        return client.newCall(pageListRequest(chapter))
            .asObservable()
            .map { response ->
                if (!response.isSuccessful) throw Exception("HTTP error ${response.code}")
                pageListParse(response)
            }
            .onErrorResumeNext {
                val message = when {
                    chapter.name.startsWith("🔒") -> "Log in via WebView and purchase this chapter to read."
                    chapter.name.startsWith("➡️") -> "Log in via WebView and ensure your charge is full to read this chapter."
                    else -> "PData not found"
                }
                Observable.error(Exception(message, it.cause))
            }
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val script = document.selectFirst("script:containsData(var _pdata_)")!!.data()

        val pDataJson = script.substringAfter("var _pdata_ =")
            .substringBefore("var _rcm_")
            .trim()
            .removeSuffix(";")
            .replace(TITLE_REGEX, "")
            .replace("'", "\"")
            .replace(TRAILING_COMMA_REGEX, "$1")

        val pData = pDataJson.parseAs<PDataDto>()
        val images = pData.img ?: pData.contents ?: emptyList()

        return images.filter { it.path.isNotEmpty() }.mapIndexed { i, img ->
            val fixedUrl = img.path.let { "https:${img.path}" }

            val pageUrl = if (pData.isScrambled) {
                fixedUrl.toHttpUrl().newBuilder()
                    .fragment("scrambled")
                    .build()
                    .toString()
            } else {
                fixedUrl
            }
            Page(i, imageUrl = pageUrl)
        }
    }

    override fun getFilterList() = FilterList(
        Filter.Header("NOTE: Search query will ignore genre filter"),
        RankingFilter(),
    )

    private open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    private class RankingFilter : UriPartFilter(
        "ランキング",
        arrayOf(
            Pair("(マンガ) 総合", "K/P/0"),
            Pair("(マンガ) ファンタジー", "K/P/2"),
            Pair("(マンガ) 恋愛", "K/P/1"),
            Pair("(マンガ) アクション", "K/P/5"),
            Pair("(マンガ) ドラマ", "K/P/3"),
            Pair("(マンガ) ホラー・ミステリー", "K/P/7"),
            Pair("(マンガ) 裏社会・アングラ", "K/P/9"),
            Pair("(マンガ) スポーツ", "K/P/6"),
            Pair("(マンガ) グルメ", "K/P/10"),
            Pair("(マンガ) 日常", "K/P/4"),
            Pair("(マンガ) 雑誌", "K/P/16"),
            Pair("(マンガ) TL", "K/P/13"),
            Pair("(マンガ) BL", "K/P/14"),
            Pair("(Smartoon) All", "S/P/0"),
            Pair("(Smartoon) ファンタジー", "S/P/2"),
            Pair("(Smartoon) 恋愛", "S/P/1"),
            Pair("(Smartoon) アクション", "S/P/5"),
            Pair("(Smartoon) ドラマ", "S/P/3"),
            Pair("(Smartoon) ホラー・ミステリー", "S/P/7"),
            Pair("(Smartoon) 裏社会・アングラ", "S/P/9"),
            Pair("(Smartoon) スポーツ", "S/P/6"),
            Pair("(Smartoon) グルメ", "S/P/10"),
            Pair("(Smartoon) 日常", "S/P/4"),
            Pair("(Smartoon) TL", "S/P/13"),
            Pair("(Smartoon) BL", "S/P/14"),
            Pair("(ノベル) 総合", "N/P/0"),
            Pair("(ノベル) ファンタジー", "N/P/2"),
            Pair("(ノベル) 恋愛", "N/P/1"),
            Pair("(ノベル) ドラマ", "N/P/3"),
            Pair("(ノベル) ホラー・ミステリー", "N/P/7"),
            Pair("(ノベル) TL", "N/P/13"),
            Pair("(ノベル) BL", "N/P/14"),
        ),
    )

    companion object {
        private val TITLE_REGEX = Regex("['\"]?title['\"]?\\s*:\\s*'.*?',?")
        private val TRAILING_COMMA_REGEX = Regex(",\\s*([}\\]])")
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
