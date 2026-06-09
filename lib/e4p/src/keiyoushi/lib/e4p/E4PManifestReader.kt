package keiyoushi.lib.e4p

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Page
import keiyoushi.utils.parseAsProto
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.OkHttpClient

class E4PManifestReader(private val client: OkHttpClient, private val requestHeaders: Headers) {
    private val decoder = E4PDecoder()

    fun extractPagesFromEncryptedManifest(manifestUrl: HttpUrl): List<Page> {
        val manifestResponse = client.newCall(GET(manifestUrl, requestHeaders)).execute()
        val ticketBytes = manifestResponse.parseAsProto<E4PQSTicket>()
        val decoded = decoder.decodeManifestFull(ticketBytes)
        val pub = decoded.pub
        val manifestQueryNames = manifestUrl.queryParameterNames

        val consumerIdStr = (ticketBytes.consumer + "0".repeat(32)).substring(0, 32)
        val consumerId = consumerIdStr.toByteArray(Charsets.US_ASCII)

        return pub.spine.mapIndexedNotNull { index, link ->
            val bestVariant = findBestVariant(link.variants) ?: return@mapIndexedNotNull null

            val resolved = manifestUrl.resolve(bestVariant.link) ?: return@mapIndexedNotNull null
            val withAuth = resolved.newBuilder().apply {
                manifestQueryNames.forEach { name ->
                    manifestUrl.queryParameter(name)?.let { setQueryParameter(name, it) }
                }
            }.build()

            val drm = bestVariant.image?.drm
            val iv = drm?.iv
            val finalUrl = if (drm?.version == EdrmVersion.XEBP && iv != null && iv.size == 32 &&
                decoded.pbexSeed != null && decoded.pbexSeed.size == 48
            ) {
                val xebpFragment = listOf(
                    withAuth.fragment.orEmpty(),
                    XebpDecoder.hex(iv),
                    ticketBytes.contentId,
                    XebpDecoder.hex(consumerId),
                    XebpDecoder.hex(decoded.pbexSeed),
                ).joinToString("\n")
                withAuth.newBuilder().fragment(xebpFragment).build()
            } else {
                withAuth
            }

            Page(index, imageUrl = finalUrl.toString())
        }
    }

    fun extractPagesFromUnencryptedManifest(manifestUrl: HttpUrl): List<Page> {
        val manifestResponse = client.newCall(GET(manifestUrl, requestHeaders)).execute()
        val pub = manifestResponse.parseAsProto<ProtoPub>()
        val manifestQueryNames = manifestUrl.queryParameterNames

        return pub.spine.mapIndexedNotNull { index, link ->
            val bestVariant = findBestVariant(link.variants) ?: return@mapIndexedNotNull null

            val resolved = manifestUrl.resolve(bestVariant.link) ?: return@mapIndexedNotNull null
            val withAuth = resolved.newBuilder().apply {
                manifestQueryNames.forEach { name ->
                    manifestUrl.queryParameter(name)?.let { setQueryParameter(name, it) }
                }
            }.build()

            Page(index, imageUrl = withAuth.toString())
        }
    }

    private fun findBestVariant(variants: List<Variant>): Variant? = variants.filter { it.image != null }.maxByOrNull {
        imageHeightRegex.find(it.link)?.groups?.get(1)?.value?.toIntOrNull() ?: 0
    }

    companion object {
        private val imageHeightRegex = """^h(\d+)/""".toRegex()
    }
}
