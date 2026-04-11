package eu.kanade.tachiyomi.extension.en.infernalvoidscans

import eu.kanade.tachiyomi.multisrc.iken.Iken
import okhttp3.ResponseBody.Companion.toResponseBody

class HiveScans :
    Iken(
        "Hive Scans",
        "en",
        "https://hivetoons.org",
        "https://api.hivetoons.org",
    ) {
    override val versionId = 2

    override val client = super.client.newBuilder()
        .addInterceptor { chain ->
            val request = chain.request()
            val headers = request.headers.newBuilder()
                .set("Cache-Control", "max-age=0")
                .build()

            val response = chain.proceed(request.newBuilder().headers(headers).build())

            if (request.url.encodedPath.endsWith("/api/query")) {
                val body = response.body ?: return@addInterceptor response
                val contentType = body.contentType()
                val bodyString = body.string()

                // The API removed the `isNovel` field from the /api/query endpoint which crashes the parser.
                // We inject it manually right before the `postTitle` property inside the SearchResponse mapping.
                val newBody = if (!bodyString.contains("\"isNovel\"")) {
                    bodyString.replace("\"postTitle\":", "\"isNovel\":false,\"postTitle\":")
                } else {
                    bodyString
                }

                return@addInterceptor response.newBuilder()
                    .body(newBody.toResponseBody(contentType))
                    .build()
            }

            response
        }
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .set("Cache-Control", "max-age=0")
}
