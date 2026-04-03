package keiyoushi.lib.publus

import android.util.Base64
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Page
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

/**
 * @param pages The list of pages.
 * @param keys The decryption keys (k1, k2, k3) obtained from the [Decoder].
 * @param contentUrl The content base URL obtained from the source's content API response.
 */
fun generatePages(
    pages: List<PublusPage>,
    keys: List<IntArray>,
    contentUrl: String,
): List<Page> {
    val k1 = keys[0].toList()
    val k2 = keys[1].toList()
    val k3 = keys[2].toList()

    return pages.map { p ->
        val filename = PublusImage.generateFilename(p.filename, keys, p.no)
        val urlBuilder = (contentUrl + filename).toHttpUrl().newBuilder()

        p.hti?.let { urlBuilder.addQueryParameter("hti", it) }
        p.cfg?.let { urlBuilder.addQueryParameter("cfg", it) }
        p.bid?.let { urlBuilder.addQueryParameter("bid", it) }
        p.uuid?.let { urlBuilder.addQueryParameter("uuid", it) }
        p.pfCd?.let { urlBuilder.addQueryParameter("pfCd", it) }
        p.policy?.let { urlBuilder.addQueryParameter("Policy", it) }
        p.signature?.let { urlBuilder.addQueryParameter("Signature", it) }
        p.keyPairId?.let { urlBuilder.addQueryParameter("Key-Pair-Id", it) }

        val imgUrl = urlBuilder.build().toString()

        val fragmentData = PublusFragment(
            file = p.filename,
            no = p.no,
            ns = p.ns,
            ps = p.ps,
            rs = p.rs,
            bw = p.blockWidth,
            bh = p.blockHeight,
            cw = p.width,
            ch = p.height,
            k1 = k1,
            k2 = k2,
            k3 = k3,
            extra = p.extra,
            s = p.scrambled,
        )

        val fragmentJson = fragmentData.toJsonString()
        val fragment = Base64.encodeToString(fragmentJson.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP)

        Page(p.index, imageUrl = "$imgUrl#$fragment")
    }
}

/**
 * Generates pages for a configuration where only images are scrambled and no decryption keys are involved.
 * @param pages The list of pages.
 * @param contentUrl The content base URL obtained from the source's content API response.
 */
fun generatePagesNoKeys(
    pages: List<PublusPage>,
    contentUrl: String,
): List<Page> = pages.map { p ->
    val filename = "${p.filename}/${p.no}.jpeg"
    val urlBuilder = contentUrl.toHttpUrl().newBuilder()
        .addPathSegments(filename)

    p.hti?.let { urlBuilder.addQueryParameter("hti", it) }
    p.cfg?.let { urlBuilder.addQueryParameter("cfg", it) }
    p.bid?.let { urlBuilder.addQueryParameter("bid", it) }
    p.uuid?.let { urlBuilder.addQueryParameter("uuid", it) }
    p.pfCd?.let { urlBuilder.addQueryParameter("pfCd", it) }
    p.policy?.let { urlBuilder.addQueryParameter("Policy", it) }
    p.signature?.let { urlBuilder.addQueryParameter("Signature", it) }
    p.keyPairId?.let { urlBuilder.addQueryParameter("Key-Pair-Id", it) }

    val imgUrl = urlBuilder.build().toString()

    val fragmentData = PublusFragment(
        file = p.filename,
        no = p.no,
        extra = p.extra,
        s = p.scrambled,
    )

    val fragmentJson = fragmentData.toJsonString()
    val fragment = Base64.encodeToString(fragmentJson.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP)

    Page(p.index, imageUrl = "$imgUrl#$fragment")
}

/**
 * Fetches and parses the page list from a Publus content URL.
 * Automatically detects config pack format.
 * @param contentUrl The content base URL obtained from the source's content API response.
 * @param headers The [Headers] to use for requests.
 * @param client The [OkHttpClient] to use for requests.
 */
fun fetchPages(
    contentUrl: String,
    headers: Headers,
    client: OkHttpClient,
): List<Page> {
    val configBody = client.newCall(GET(contentUrl + "configuration_pack.json", headers))
        .execute()
        .use { it.body.string() }

    val encodedPack = runCatching { configBody.parseAs<ConfigPack>() }.getOrNull()
    val rootJson: Map<String, JsonElement>
    val decoderKeys: List<IntArray>

    if (encodedPack != null) {
        val result = Decoder(encodedPack.data).decode()
        rootJson = result.json.parseAs()
        decoderKeys = result.keys
    } else {
        rootJson = configBody.parseAs()
        decoderKeys = emptyList()
    }

    val container = (rootJson["configuration"] ?: throw Exception("Configuration not found in decrypted JSON"))
        .parseAs<PublusConfiguration>()

    val pages = container.contents.map { entry ->
        val pageJson = rootJson[entry.file] ?: throw Exception("Page config not found for ${entry.file}")
        val details = pageJson.toString().parseAs<PublusPageConfig>().fileLinkInfo.pageLinkInfoList[0].page

        PublusPage(
            index = entry.index,
            filename = entry.file,
            no = details.no,
            ns = details.ns,
            ps = details.ps,
            rs = details.rs,
            blockWidth = details.blockWidth,
            blockHeight = details.blockHeight,
            dummyWidth = details.dummyWidth,
            width = details.size.width,
            height = details.size.height,
            scrambled = details.dummyWidth != null,
        )
    }

    return if (decoderKeys.isNotEmpty()) {
        generatePages(pages, decoderKeys, contentUrl)
    } else {
        generatePagesNoKeys(pages, contentUrl)
    }
}
