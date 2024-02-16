package eu.kanade.tachiyomi.lib.speedbinb

import eu.kanade.tachiyomi.lib.textinterceptor.TextInterceptorHelper
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * SpeedBinb is a reader for various Japanese manga sites.
 *
 * Versions (`SpeedBinb.VERSION` in DevTools console):
 * - Minimum version tested: `1.6650.0001`
 * - Maximum version tested: `1.6930.1101`
 *
 * These versions are only for reference purposes, and does not reflect the actual range
 * of versions this class can scrape.
 */
class SpeedBinbReader(
    private val client: OkHttpClient,
    private val headers: Headers,
    private val json: Json,
    private val highQualityMode: Boolean = false,
) {
    private val isInterceptorAdded by lazy {
        client.interceptors.filterIsInstance<SpeedBinbInterceptor>().isNotEmpty()
    }

    fun pageListParse(response: Response): List<Page> =
        pageListParse(response.asJsoup())

    fun pageListParse(document: Document): List<Page> {
        // We throw here instead of in the `init {}` block because extensions that fail
        // to load just mysteriously disappears from the extension list, no errors no nothing.
        if (!isInterceptorAdded) {
            throw Exception("SpeedBinbInterceptor was not added to the client.")
        }

        val readerUrl = document.location().toHttpUrl()
        val content = document.selectFirst("#content")!!

        if (!content.hasAttr("data-ptbinb")) {
            return content.select("[data-ptimg]").mapIndexed { i, it ->
                Page(i, imageUrl = it.absUrl("data-ptimg"))
            }
        }

        val cid = content.attr("data-ptbinb-cid")
            .ifEmpty { readerUrl.queryParameter("cid") }
            ?: throw Exception("Could not find chapter ID")
        val sharedKey = generateSharedKey(cid)
        val contentInfoUrl = content.absUrl("data-ptbinb").toHttpUrl().newBuilder()
            .copyKeyParametersFrom(readerUrl)
            .setQueryParameter("cid", cid)
            .setQueryParameter("k", sharedKey)
            .setQueryParameter("dmytime", System.currentTimeMillis().toString())
            .build()
        val contentInfo = client.newCall(GET(contentInfoUrl, headers)).execute().parseAs<BibContentInfo>()

        if (contentInfo.result != 1) {
            throw Exception("Failed to execute bibGetCntntInfo API.")
        }

        if (contentInfo.items.isEmpty()) {
            throw Exception("There is no item.")
        }

        val contentItem = contentInfo.items[0]
        val ctbl = json.decodeFromString<List<String>>(decodeScrambleTable(cid, sharedKey, contentItem.ctbl))
        val ptbl = json.decodeFromString<List<String>>(decodeScrambleTable(cid, sharedKey, contentItem.ptbl))
        val sbcUrl = contentItem.getSbcUrl(readerUrl, cid)
        val sbcData = client.newCall(GET(sbcUrl, headers)).execute().body.string().let {
            val raw = if (contentItem.serverType == ServerType.DIRECT) {
                it.substringAfter("DataGet_Content(").substringBeforeLast(")")
            } else {
                it
            }

            json.decodeFromString<SBCContent>(raw)
        }

        if (sbcData.result != 1) {
            throw Exception("Failed to fetch content")
        }

        val isSingleQuality = sbcData.imageClass == "singlequality"
        val ttx = Jsoup.parseBodyFragment(sbcData.ttx, document.location())
        val pageBaseUrl = when (contentItem.serverType) {
            ServerType.DIRECT, ServerType.REST -> contentItem.contentServer
            ServerType.SBC -> sbcUrl.replaceFirst("/sbcGetCntnt.php", "/sbcGetImg.php")
            else -> throw UnsupportedOperationException("Unsupported ServerType value ${contentItem.serverType}")
        }.toHttpUrl()
        val pages = ttx.select("t-case:first-of-type t-img").mapIndexed { i, it ->
            val src = it.attr("src")
            val keyPair = determineKeyPair(src, ptbl, ctbl)
            val fragment = "ptbinb,${keyPair.first},${keyPair.second}"
            val imageUrl = pageBaseUrl.newBuilder()
                .buildImageUrl(
                    readerUrl,
                    src,
                    contentItem,
                    isSingleQuality,
                    highQualityMode,
                )
                .fragment(fragment)
                .toString()

            Page(i, imageUrl = imageUrl)
        }.toMutableList()

        // This is probably the silliest use of TextInterceptor ever.
        //
        // If chapter purchases are enabled, and there's a link to purchase the current chapter,
        // we add in the purchase URL as the last page.
        val buyIconPosition = document.selectFirst("script:containsData(Config.LoginBuyIconPosition)")
            ?.data()
            ?.substringAfter("Config.LoginBuyIconPosition=")
            ?.substringBefore(";")
            ?.trim()
            ?: "-1"
        val enableBuying = buyIconPosition != "-1"

        if (enableBuying && contentItem.viewMode != ViewMode.COMMERCIAL && !contentItem.shopUrl.isNullOrEmpty()) {
            pages.add(
                Page(pages.size, imageUrl = TextInterceptorHelper.createUrl("", "購入： ${contentItem.shopUrl}")),
            )
        }

        return pages
    }

    private inline fun <reified T> Response.parseAs(): T =
        json.decodeFromString(body.string())
}

private fun HttpUrl.Builder.buildImageUrl(
    readerUrl: HttpUrl,
    src: String,
    contentItem: BibContentItem,
    isSingleQuality: Boolean,
    highQualityMode: Boolean,
) = apply {
    when (contentItem.serverType) {
        ServerType.DIRECT -> {
            val filename = when {
                isSingleQuality -> "M.jpg"
                highQualityMode -> "M_H.jpg"
                else -> "M_L.jpg"
            }

            addPathSegments(src)
            addPathSegment(filename)
            contentItem.contentDate?.let { setQueryParameter("dmytime", it) }
        }
        ServerType.REST -> {
            addPathSegment("img")
            addPathSegments(src)
            if (!isSingleQuality && !highQualityMode) {
                setQueryParameter("q", "1")
            }

            contentItem.contentDate?.let { setQueryParameter("dmytime", it) }
            copyKeyParametersFrom(readerUrl)
        }
        ServerType.SBC -> {
            setQueryParameter("src", src)
            contentItem.requestToken?.let { setQueryParameter("p", it) }

            if (!isSingleQuality) {
                setQueryParameter("q", if (highQualityMode) "0" else "1")
            }

            setQueryParameter("vm", contentItem.viewMode.toString())
            contentItem.contentDate?.let { setQueryParameter("dmytime", it) }
            copyKeyParametersFrom(readerUrl)
        }
        else -> throw UnsupportedOperationException("Unsupported ServerType value ${contentItem.serverType}")
    }
}

internal fun HttpUrl.Builder.copyKeyParametersFrom(url: HttpUrl): HttpUrl.Builder {
    for (i in 0..9) {
        url.queryParameter("u$i")?.let {
            setQueryParameter("u$i", it)
        }
    }

    return this
}
