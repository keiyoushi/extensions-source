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
 * @param imageUrlPrefix The content URL for the images.
 */
fun generatePages(
    pages: List<PublusPage>,
    keys: List<IntArray>,
    imageUrlPrefix: String,
): List<Page> {
    val k1 = keys[0].toList()
    val k2 = keys[1].toList()
    val k3 = keys[2].toList()

    return pages.map { p ->
        val filename = PublusImage.generateFilename(p.filename, keys, p.no)
        val urlBuilder = (imageUrlPrefix + filename).toHttpUrl().newBuilder()

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
 * @param imageUrlPrefix The content URL for the images.
 * @param fileExtension The file extension, default ".jpeg".
 */
fun generatePagesNoKeys(
    pages: List<PublusPage>,
    imageUrlPrefix: String,
    fileExtension: String = ".jpeg",
): List<Page> = pages.map { p ->
    val filename = "${p.filename}/${p.no}$fileExtension"
    val urlBuilder = imageUrlPrefix.toHttpUrl().newBuilder()
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
 * Fetches and parses the full page list from a Publus content URL, automatically detecting
 * the configuration pack format and selecting the correct rendering path.
 *
 * **Config pack formats detected:**
 * - `{"data":"<encrypted>"}` — Decoder is run; keys drive URL generation (e.g. ComicBoost)
 * - Raw JSON body — parsed directly, no Decoder (e.g. DMM)
 *
 * **Rendering path selected by result:**
 * - Decoder keys present → [generatePages] (key-hashed URLs for all pages)
 * - No Decoder keys → [generatePagesNoKeys] (plain `No.jpeg` URLs for all pages)
 *
 * Scrambling is determined **per page**: pages where `DummyWidth` is present in the JSON
 * are unscrambled by the interceptor; pages where it is absent are passed through as-is.
 *
 * @param contentUrl The content base URL obtained from the source's c.php response.
 * @param headers The HTTP headers to use for requests.
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

    // Two possible config pack formats:
    //   1. {"data":"<encrypted>"} → ConfigPack wrapper, data needs Decoder (e.g. ComicBoost)
    //   2. {... raw json ...}     → plain JSON body, no Decoder needed (e.g. DMM)
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
