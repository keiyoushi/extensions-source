package keiyoushi.lib.publus

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.network.get
import keiyoushi.utils.parseAs
import keiyoushi.utils.stringOrNull
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
 * @param auth Optional signed-request parameters appended to the config and image URLs.
 * @param extra Optional per-page session data, embedded into each page's fragment.
 * @param hashFilenames when false, image paths use the plain `pageId/no.jpeg` instead of the keyed
 * hash, for sources that scramble images and encrypt the config but serve images at unhashed paths.
 */
suspend fun OkHttpClient.fetchPages(
    contentUrl: String,
    headers: Headers,
    auth: PublusAuth? = null,
    extra: Map<String, String>? = null,
    hashFilenames: Boolean = true,
): List<Page> {
    val baseUrl = contentUrl.toHttpUrl()
    val configUrl = baseUrl.newBuilder()
        .addPathSegment("configuration_pack.json")
        .also { auth?.applyTo(it) }
        .build()

    val root = get(configUrl, headers).parseAs<JsonObject>()
    val decoded = root["data"]?.stringOrNull?.let { Decoder(it).decode() }
    val config = decoded?.json?.parseAs<JsonObject>() ?: root
    val keys = decoded?.keys.orEmpty()

    val contents = (config["configuration"] ?: throw Exception("Configuration not found in decrypted JSON"))
        .parseAs<PublusConfiguration>().contents
    val keyLists = keys.map { it.toList() }
    val filenameKeys = if (hashFilenames) keys else emptyList()

    return contents.map { entry ->
        val page = (config[entry.file] ?: throw Exception("Page config not found for ${entry.file}"))
            .parseAs<PublusPageConfig>().fileLinkInfo.pageLinkInfoList.first().page

        val imageUrl = baseUrl.newBuilder()
            .addPathSegments(PublusImage.generateFilename(entry.file, filenameKeys, page.no))
            .also { auth?.applyTo(it) }
            .build()

        val fragment = PublusFragment(
            file = entry.file,
            no = page.no,
            ns = page.ns,
            ps = page.ps,
            rs = page.rs,
            bw = page.blockWidth,
            bh = page.blockHeight,
            cw = page.size.width,
            ch = page.size.height,
            k1 = keyLists.getOrNull(0).orEmpty(),
            k2 = keyLists.getOrNull(1).orEmpty(),
            k3 = keyLists.getOrNull(2).orEmpty(),
            extra = extra,
            s = page.dummyWidth != null,
        ).toJsonString()

        Page(entry.index, imageUrl = "$imageUrl#$fragment")
    }
}

/**
 * Fetches and parses the page list from a Publus content URL, automatically retrieving the
 * headers from the current [HttpSource] context receiver.
 *
 * @param contentUrl The content base URL obtained from the source's content API response.
 * @param auth Optional signed-request parameters appended to the config and image URLs.
 * @param extra Optional per-page session data, embedded into each page's fragment.
 * @param hashFilenames when false, image paths use the plain `pageId/no.jpeg` instead of the keyed
 * hash, for sources that scramble images and encrypt the config but serve images at unhashed paths.
 */
context(source: HttpSource)
suspend fun OkHttpClient.fetchPages(
    contentUrl: String,
    auth: PublusAuth? = null,
    extra: Map<String, String>? = null,
    hashFilenames: Boolean = true,
): List<Page> = fetchPages(contentUrl, source.headers, auth, extra, hashFilenames)
