package eu.kanade.tachiyomi.extension.id.yuramanga

import eu.kanade.tachiyomi.multisrc.zmanga.ZManga
import java.io.IOException
import java.text.SimpleDateFormat

class YuraManga : ZManga(
    "YuraManga",
    "https://www.yuramanga.my.id",
    "id",
    SimpleDateFormat("dd/MM/yyyy"),
) {
    // Moved from Madara to ZManga
    override val versionId = 3

    override val client = super.client.newBuilder()
        .addInterceptor { chain ->
            val response = chain.proceed(chain.request())
            if (response.request.url.pathSegments.contains("login")) {
                throw IOException("Please log in to the WebView to continue")
            }
            response
        }
        .build()
}
