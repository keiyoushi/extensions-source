package keiyoushi.lib.publus

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Page
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import kotlinx.serialization.json.JsonObject
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

/**
 * Fetches and parses the page list from a Publus content URL, handling both
 * the encrypted config-pack format and a plain configuration JSON.
 *
 * @param contentUrl The content base URL obtained from the source's content API response.
 * @param headers The [Headers] to use for requests.
 * @param client The [OkHttpClient] to use for requests.
 * @param auth Optional signed-request parameters appended to the config and image URLs.
 * @param extra Optional per-page session data, embedded into each page's fragment.
 * @param hashFilenames when false, image paths use the plain `pageId/no.jpeg` instead of the keyed
 * hash, for sources that scramble images and encrypt the config but serve images at unhashed paths.
 */
fun fetchPages(
    contentUrl: String,
    headers: Headers,
    client: OkHttpClient,
    auth: PublusAuth? = null,
    extra: Map<String, String>? = null,
    hashFilenames: Boolean = true,
): List<Page> {
    val configUrl = contentUrl.toHttpUrl().newBuilder()
        .addPathSegment("configuration_pack.json")
        .also { auth?.applyTo(it) }
        .build()

    val root = client.newCall(GET(configUrl, headers))
        .execute()
        .use { it.body.string() }
        .parseAs<JsonObject>()

    val pack = runCatching { root.parseAs<ConfigPack>() }.getOrNull()
    val (config, keys) = if (pack != null) {
        val decoded = Decoder(pack.data).decode()
        decoded.json.parseAs<JsonObject>() to decoded.keys
    } else {
        root to emptyList()
    }

    val container = (config["configuration"] ?: throw Exception("Configuration not found in decrypted JSON"))
        .parseAs<PublusConfiguration>()

    val pages = container.contents.map {
        val pageJson = config[it.file] ?: throw Exception("Page config not found for ${it.file}")
        val page = pageJson.parseAs<PublusPageConfig>().fileLinkInfo.pageLinkInfoList.first().page

        PublusPage(
            index = it.index,
            filename = it.file,
            no = page.no,
            ns = page.ns,
            ps = page.ps,
            rs = page.rs,
            blockWidth = page.blockWidth,
            blockHeight = page.blockHeight,
            width = page.size.width,
            height = page.size.height,
            scrambled = page.dummyWidth != null,
        )
    }

    return buildPages(pages, contentUrl, keys, auth, extra, hashFilenames)
}

private class PublusPage(
    val index: Int,
    val filename: String,
    val no: Int,
    val ns: Long,
    val ps: Long,
    val rs: Long,
    val blockWidth: Int,
    val blockHeight: Int,
    val width: Int,
    val height: Int,
    val scrambled: Boolean,
)

/**
 * Builds the final [Page] list, appending signed-request parameters to each image URL and encoding
 * the per-page unscramble metadata into the URL fragment.
 *
 * An empty [keys] list selects the image-scramble-only variant, where the filename
 * is the plain `pageId/no.jpeg` and the fragment omits the key material.
 *
 * @see fetchPages
 */
private fun buildPages(
    pages: List<PublusPage>,
    contentUrl: String,
    keys: List<IntArray>,
    auth: PublusAuth?,
    extra: Map<String, String>?,
    hashFilenames: Boolean,
): List<Page> {
    val keyLists = keys.takeIf { it.isNotEmpty() }
        ?.let { Triple(it[0].toList(), it[1].toList(), it[2].toList()) }
    val filenameKeys = if (hashFilenames) keys else emptyList()

    return pages.map { p ->
        val filename = PublusImage.generateFilename(p.filename, filenameKeys, p.no)
        val imageUrl = contentUrl.toHttpUrl().newBuilder()
            .addPathSegments(filename)
            .also { auth?.applyTo(it) }
            .build()

        val fragment = if (keyLists == null) {
            PublusFragment(
                file = p.filename,
                no = p.no,
                cw = p.width,
                ch = p.height,
                extra = extra,
                s = p.scrambled,
            )
        } else {
            PublusFragment(
                file = p.filename,
                no = p.no,
                ns = p.ns,
                ps = p.ps,
                rs = p.rs,
                bw = p.blockWidth,
                bh = p.blockHeight,
                cw = p.width,
                ch = p.height,
                k1 = keyLists.first,
                k2 = keyLists.second,
                k3 = keyLists.third,
                extra = extra,
                s = p.scrambled,
            )
        }.toJsonString()

        Page(p.index, imageUrl = "$imageUrl#$fragment")
    }
}
