package keiyoushi.lib.speedbinb

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.lib.textinterceptor.TextInterceptorHelper
import keiyoushi.network.get
import keiyoushi.utils.parseAs
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * Fetches the page list of a SpeedBinb reader.
 * The pages are descrambled by [SpeedBinbInterceptor], which must be added to the client.
 *
 * Versions (`SpeedBinb.VERSION` in DevTools console):
 * - Minimum version tested: `1.6650.0001`
 * - Maximum version tested: `1.7070.1001`
 *
 * These versions are only for reference purposes, and does not reflect the actual range
 * of versions this can scrape.
 *
 * @param document The loaded reader page.
 * @param headers The [Headers] to use for requests.
 * @param highQualityMode Whether to request the high quality images when the reader offers both.
 * @return The pages, or an empty list if the reader refuses to serve the content.
 */
suspend fun OkHttpClient.fetchPages(
    document: Document,
    headers: Headers,
    highQualityMode: Boolean = true,
): List<Page> {
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

    val contentInfoUrl = content.absUrl("data-ptbinb").toHttpUrl().newBuilder()
        .copyKeyParametersFrom(readerUrl)
        .build()

    val buyIconPosition = document.selectFirst("script:containsData(Config.LoginBuyIconPosition)")
        ?.data()
        ?.substringAfter("Config.LoginBuyIconPosition=")
        ?.substringBefore(";")
        ?.trim()
        ?: "-1"

    return fetchPages(contentInfoUrl, cid, headers, highQualityMode, enableBuying = buyIconPosition != "-1")
}

/**
 * Fetches the page list of a SpeedBinb reader page, automatically retrieving the headers from
 * the current [HttpSource] context receiver.
 *
 * @param document The loaded reader page.
 * @param highQualityMode Whether to request the high quality images when the reader offers both.
 * @return The pages, or an empty list if the reader refuses to serve the content.
 * @see fetchPages
 */
context(source: HttpSource)
suspend fun OkHttpClient.fetchPages(
    document: Document,
    highQualityMode: Boolean = true,
): List<Page> = fetchPages(document, source.headers, highQualityMode)

/**
 * Fetches the page list of SpeedBinb content straight from its `bibGetCntntInfo` API, for readers
 * whose API URL and content ID are known without loading the reader page.
 *
 * @param contentInfoUrl The `bibGetCntntInfo` API URL.
 * @param cid The content ID.
 * @param headers The [Headers] to use for requests.
 * @param highQualityMode Whether to request the high quality images when the reader offers both.
 * @param enableBuying Whether to add the purchase URL of trial content as the last page.
 * @return The pages, or an empty list if the reader refuses to serve the content.
 */
suspend fun OkHttpClient.fetchPages(
    contentInfoUrl: HttpUrl,
    cid: String,
    headers: Headers,
    highQualityMode: Boolean = true,
    enableBuying: Boolean = false,
): List<Page> {
    val sharedKey = generateSharedKey(cid)
    val url = contentInfoUrl.newBuilder()
        .setQueryParameter("cid", cid)
        .setQueryParameter("k", sharedKey)
        .setQueryParameter("dmytime", System.currentTimeMillis().toString())
        .build()

    val contentInfo = get(url, headers).parseAs<BibContentInfo>()

    if (contentInfo.result != 1) {
        return emptyList()
    }

    val contentItem = contentInfo.items.first().parseAs<BibContentItem>()
    val ctbl = decodeScrambleTable(cid, sharedKey, contentItem.ctbl).parseAs<List<String>>()
    val ptbl = decodeScrambleTable(cid, sharedKey, contentItem.ptbl).parseAs<List<String>>()
    val sbcUrl = contentItem.getSbcUrl(contentInfoUrl, cid)
    val sbcResponse = get(sbcUrl, headers)
    val sbcData = if (contentItem.serverType == ServerType.DIRECT) {
        sbcResponse.parseAs<SBCContent> { it.substringAfter("DataGet_Content(").substringBeforeLast(")") }
    } else {
        sbcResponse.parseAs<SBCContent>()
    }

    if (sbcData.result != 1) {
        throw Exception("Failed to fetch content")
    }

    val isSingleQuality = sbcData.imageClass == "singlequality"
    val pageBaseUrl = when (contentItem.serverType) {
        ServerType.DIRECT, ServerType.REST -> contentItem.contentServer
        ServerType.SBC -> sbcUrl.replaceFirst("/sbcGetCntnt.php", "/sbcGetImg.php")
        else -> throw UnsupportedOperationException("Unsupported ServerType value ${contentItem.serverType}")
    }.toHttpUrl()

    // The ttx never closes its tags. After 128 pages, Jsoup before 1.23.2 stops matching </t-case>,
    // causing the <t-nocase> fallback to be nested inside it, so only parse the first case.
    val firstCase = sbcData.ttx.substringAfter("<t-case", "").substringAfter(">").substringBefore("</t-case>")
    val pages = Jsoup.parseBodyFragment(firstCase).select("t-img").mapIndexed { i, it ->
        val src = it.attr("src")
        val keyPair = determineKeyPair(src, ptbl, ctbl)
        val imageUrl = pageBaseUrl.newBuilder()
            .buildImageUrl(
                contentInfoUrl,
                src,
                contentItem,
                isSingleQuality,
                highQualityMode,
            )
            .fragment("ptbinb,${keyPair.first},${keyPair.second}")
            .toString()

        Page(i, imageUrl = imageUrl)
    }

    // This is probably the silliest use of TextInterceptor ever.
    //
    // If chapter purchases are enabled, and there's a link to purchase the current chapter,
    // we add in the purchase URL as the last page.
    if (enableBuying && contentItem.viewMode != ViewMode.COMMERCIAL && !contentItem.shopUrl.isNullOrEmpty()) {
        return pages + Page(pages.size, imageUrl = TextInterceptorHelper.createUrl("", "購入： ${contentItem.shopUrl}"))
    }

    return pages
}

/**
 * Fetches the page list of SpeedBinb content straight from its `bibGetCntntInfo` API,
 * automatically retrieving the headers from the current [HttpSource] context receiver.
 *
 * @param contentInfoUrl The `bibGetCntntInfo` API URL, including the reader's `u0`-`u9` parameters.
 * @param cid The content ID.
 * @param highQualityMode Whether to request the high quality images when the reader offers both.
 * @param enableBuying Whether to add the purchase URL of trial content as the last page.
 * @return The pages, or an empty list if the reader refuses to serve the content.
 * @see fetchPages
 */
context(source: HttpSource)
suspend fun OkHttpClient.fetchPages(
    contentInfoUrl: HttpUrl,
    cid: String,
    highQualityMode: Boolean = true,
    enableBuying: Boolean = false,
): List<Page> = fetchPages(contentInfoUrl, cid, source.headers, highQualityMode, enableBuying)

private fun HttpUrl.Builder.buildImageUrl(
    contentInfoUrl: HttpUrl,
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
            copyKeyParametersFrom(contentInfoUrl)
        }
        ServerType.SBC -> {
            setQueryParameter("src", src)
            contentItem.requestToken?.let { setQueryParameter("p", it) }

            if (!isSingleQuality) {
                val isTrial = contentItem.viewMode == ViewMode.NON_MEMBER_TRIAL || contentItem.viewMode == ViewMode.MEMBER_TRIAL
                setQueryParameter("q", if (highQualityMode && !isTrial) "0" else "1")
            }

            setQueryParameter("vm", contentItem.viewMode.toString())
            contentItem.contentDate?.let { setQueryParameter("dmytime", it) }
            copyKeyParametersFrom(contentInfoUrl)
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
