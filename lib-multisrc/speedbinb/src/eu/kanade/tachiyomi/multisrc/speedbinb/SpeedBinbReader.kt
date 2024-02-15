package eu.kanade.tachiyomi.multisrc.speedbinb

import eu.kanade.tachiyomi.lib.textinterceptor.TextInterceptor
import eu.kanade.tachiyomi.lib.textinterceptor.TextInterceptorHelper
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import uy.kohesive.injekt.injectLazy
import kotlin.math.ceil

/**
 * SpeedBinb is a reader for various Japanese manga sites. As it is **only** a reader,
 * parsing of entries is left to the child class.
 */
abstract class SpeedBinbReader(
    private val highQualityMode: Boolean = false,
) : HttpSource() {

    protected val json: Json by injectLazy()

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(TextInterceptor())
        .addInterceptor(PtBinbInterceptor(json))
        .build()

    override fun pageListParse(response: Response): List<Page> =
        pageListParse(response, response.asJsoup())

    protected open fun pageListParse(response: Response, document: Document): List<Page> {
        val responseUrl = response.request.url
        val content = document.selectFirst("#content")!!

        // PtImg scrambling
        if (!content.hasAttr("data-ptbinb")) {
            return content.select("[data-ptimg]").mapIndexed { i, it ->
                Page(i, imageUrl = it.attr("abs:data-ptimg"))
            }
        }

        val cid = responseUrl.queryParameter("cid") ?: content.attr("data-ptbinb-cid")

        if (cid.isEmpty()) {
            throw Exception("Could not find chapter ID")
        }

        val sharedKey = generateSharedKey(cid)
        val contentInfoUrl = content.attr("abs:data-ptbinb").toHttpUrl().newBuilder().apply {
            copyKeyParametersFrom(responseUrl)
            addQueryParameter("cid", cid)
            addQueryParameter("k", sharedKey)
            addQueryParameter("dmytime", System.currentTimeMillis().toString())
        }.build()
        val contentInfo = client.newCall(GET(contentInfoUrl, headers)).execute().parseAs<BibContentInfo>()

        if (contentInfo.result != 1) {
            throw Exception("Failed to execute bibGetCntntInfo API.")
        }

        if (contentInfo.items.isEmpty()) {
            throw Exception("There is no item.")
        }

        val contentInfoItem = contentInfo.items[0]
        val ctbl = json.decodeFromString<List<String>>(decodeScrambleTable(cid, sharedKey, contentInfoItem.ctbl))
        val ptbl = json.decodeFromString<List<String>>(decodeScrambleTable(cid, sharedKey, contentInfoItem.ptbl))

        val sbcUrl = when (contentInfoItem.serverType) {
            BibContentItem.SERVER_TYPE_DIRECT -> "${contentInfoItem.contentServer.removeSuffix("/")}/content.js"
            BibContentItem.SERVER_TYPE_REST -> "${contentInfoItem.contentServer.removeSuffix("/")}/content"
            BibContentItem.SERVER_TYPE_SBC -> baseUrl.toHttpUrl().newBuilder().apply {
                addPathSegments(contentInfoItem.contentServer)
                addPathSegment("sbcGetCntnt.php")
                addQueryParameter("cid", cid)

                contentInfoItem.requestToken?.let {
                    addQueryParameter("p", it)
                }

                addQueryParameter("q", "1")
                addQueryParameter("vm", contentInfoItem.viewMode.toString())
                addQueryParameter("dmytime", contentInfoItem.contentDate ?: System.currentTimeMillis().toString())
                copyKeyParametersFrom(responseUrl)
            }.toString()
            else -> throw UnsupportedOperationException("Unsupported ServerType value ${contentInfoItem.serverType}")
        }
        val sbcRawData = client.newCall(GET(sbcUrl, headers)).execute().body.string().let {
            if (contentInfoItem.serverType == BibContentItem.SERVER_TYPE_DIRECT) {
                it.substring(16, it.lastIndex)
            } else {
                it
            }
        }
        val sbcData = json.decodeFromString<SBCContent>(sbcRawData)

        if (sbcData.result != 1) {
            throw Exception("Failed to fetch content")
        }

        val ttx = Jsoup.parseBodyFragment(sbcData.ttx, responseUrl.toString())
        val singleQuality = sbcData.imageClass == "singlequality"
        val pageBaseUrl = when (contentInfoItem.serverType) {
            BibContentItem.SERVER_TYPE_DIRECT, BibContentItem.SERVER_TYPE_REST -> contentInfoItem.contentServer.toHttpUrl()
            BibContentItem.SERVER_TYPE_SBC -> sbcUrl.toHttpUrl()
            else -> throw UnsupportedOperationException("Unsupported ServerType value ${contentInfoItem.serverType}")
        }

        val pages = ttx.select("t-case:first-of-type t-img")
            .mapIndexed { i, it ->
                val src = it.attr("src")
                val keyPair = determineKeyPair(src, ptbl, ctbl)
                val fragment = "ptbinb,${keyPair.first},${keyPair.second}"
                val imageUrl = pageBaseUrl.newBuilder()
                    .buildImageUrl(
                        pageBaseUrl,
                        responseUrl,
                        contentInfoItem,
                        src,
                        fragment,
                        singleQuality,
                        highQualityMode,
                    )
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

        if (enableBuying && contentInfoItem.viewMode != 1 && !contentInfoItem.shopUrl.isNullOrEmpty()) {
            pages.add(
                Page(pages.size, imageUrl = TextInterceptorHelper.createUrl(name, "購入： ${contentInfoItem.shopUrl}")),
            )
        }

        return pages
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    private inline fun <reified T> Response.parseAs(): T =
        json.decodeFromString(body.string())
}

internal const val URLSAFE_BASE64_LOOKUP = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

private fun HttpUrl.Builder.buildImageUrl(
    originalUrl: HttpUrl,
    chapterUrl: HttpUrl,
    contentInfoItem: BibContentItem,
    src: String,
    fragment: String,
    singleQuality: Boolean,
    highQualityMode: Boolean,
) = when (contentInfoItem.serverType) {
    BibContentItem.SERVER_TYPE_DIRECT -> {
        val filename = if (singleQuality) {
            "M.jpg"
        } else if (highQualityMode) {
            "M_H.jpg"
        } else {
            "M_L.jpg"
        }

        addPathSegments(src)
        addPathSegment(filename)
        contentInfoItem.contentDate?.let { addQueryParameter("dmytime", it) }
        fragment(fragment)
    }
    BibContentItem.SERVER_TYPE_REST -> {
        addPathSegment("img")
        addPathSegments(src)

        if (!singleQuality && !highQualityMode) {
            addQueryParameter("q", "1")
        }

        contentInfoItem.contentDate?.let { addQueryParameter("dmytime", it) }
        copyKeyParametersFrom(chapterUrl)
        fragment(fragment)
    }
    BibContentItem.SERVER_TYPE_SBC -> {
        setPathSegment(originalUrl.pathSize - 1, "sbcGetImg.php")
        addQueryParameter("src", src)
        contentInfoItem.requestToken?.let { addQueryParameter("p", it) }

        if (!singleQuality) {
            addQueryParameter("q", if (highQualityMode) "0" else "1")
        }

        addQueryParameter("vm", contentInfoItem.viewMode.toString())
        contentInfoItem.contentDate?.let { addQueryParameter("dmytime", it) }
        copyKeyParametersFrom(chapterUrl)
        fragment(fragment)
    }
    else -> throw UnsupportedOperationException("Unsupported ServerType value ${contentInfoItem.serverType}")
}

private fun determineKeyPair(src: String?, ptbl: List<String>, ctbl: List<String>): Pair<String, String> {
    val i = mutableListOf(0, 0)

    if (src != null) {
        val filename = src.substringAfterLast("/")

        for (e in filename.indices) {
            i[e % 2] = i[e % 2] + filename[e].code
        }

        i[0] = i[0] % 8
        i[1] = i[1] % 8
    }

    return Pair(ptbl[i[0]], ctbl[i[1]])
}

private fun decodeScrambleTable(cid: String, sharedKey: String, table: String): String {
    val r = "$cid:$sharedKey"
    var e = r.toCharArray()
        .map { it.code }
        .reduceIndexed { index, acc, i -> acc + (i shl index % 16) } and 2147483647

    if (e == 0) {
        e = 0x12345678
    }

    return buildString(table.length) {
        for (s in table.indices) {
            e = e ushr 1 xor (1210056708 and -(1 and e))
            append(((table[s].code - 32 + e) % 94 + 32).toChar())
        }
    }
}

private fun generateSharedKey(cid: String): String {
    val randomChars = randomChars(16)
    val cidRepeatCount = ceil(16F / cid.length).toInt()
    val unk1 = buildString(cid.length * cidRepeatCount) {
        for (i in 0 until cidRepeatCount) {
            append(cid)
        }
    }
    val unk2 = unk1.substring(0, 16)
    val unk3 = unk1.substring(unk1.length - 16, unk1.length)
    var s = 0
    var h = 0
    var u = 0

    return buildString(randomChars.length * 2) {
        for (i in randomChars.indices) {
            s = s xor randomChars[i].code
            h = h xor unk2[i].code
            u = u xor unk3[i].code

            append(randomChars[i])
            append(URLSAFE_BASE64_LOOKUP[(s + h + u) and 63])
        }
    }
}

private fun randomChars(length: Int) = buildString(length) {
    for (i in 0 until length) {
        append(URLSAFE_BASE64_LOOKUP.random())
    }
}

private fun HttpUrl.Builder.copyKeyParametersFrom(url: HttpUrl) {
    for (i in 0..9) {
        url.queryParameter("u$i")?.let {
            setQueryParameter("u$i", it)
        }
    }
}
