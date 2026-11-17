package eu.kanade.tachiyomi.extension.tr.seitoon

import keiyoushi.utils.parseAs
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.util.Base64
import kotlin.time.Clock

const val ASSET_TOKEN_HEADER = "x-reader-asset-token"

// Page tokens last five minutes: a page asked for later gets a fresh one from its chapter's manifest
class AssetTokenInterceptor : Interceptor {

    private var latestManifest: Pair<String, Map<String, String>>? = null

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val token = request.header(ASSET_TOKEN_HEADER) ?: return chain.proceed(request)
        if (!token.isExpired()) {
            val response = chain.proceed(request)
            if (response.code != 403) return response
            response.close()
        }
        // A page the manifest no longer lists keeps the server's error
        val freshToken = freshToken(chain, request) ?: return chain.proceed(request)
        return chain.proceed(request.newBuilder().header(ASSET_TOKEN_HEADER, freshToken).build())
    }

    private fun freshToken(chain: Interceptor.Chain, request: Request): String? = synchronized(this) {
        // api/v1/reader/chapters/<chapter id>/assets/<asset id>/image
        val chapterId = request.url.pathSegments[4]
        val manifestUrl = request.url.newBuilder()
            .encodedPath("/api/v1/reader/chapters")
            .addPathSegment(chapterId)
            .addPathSegment("manifest")
            .query(null)
            .build()
        val imageUrl = request.url.toString()
        latestManifest?.takeIf { it.first == chapterId }?.second?.get(imageUrl)?.takeUnless { it.isExpired() }?.let { return it }

        val manifestRequest = request.newBuilder()
            .url(manifestUrl)
            .removeHeader(ASSET_TOKEN_HEADER)
            .header("Accept", "application/json")
            .build()
        val tokens = chain.proceed(manifestRequest).parseAs<ManifestResponse>().data.manifest.pages
            .associate { manifestUrl.resolve(it.imageUrl)!!.toString() to it.deliveryToken }
        latestManifest = chapterId to tokens
        tokens[imageUrl]
    }

    private fun String.isExpired(): Boolean {
        val payload = Base64.getUrlDecoder().decode(substringBefore('.')).decodeToString()
        return payload.parseAs<TokenDto>().exp * 1000 - EXPIRY_MARGIN_MILLIS < Clock.System.now().toEpochMilliseconds()
    }

    companion object {
        private const val EXPIRY_MARGIN_MILLIS = 15_000L
    }
}
