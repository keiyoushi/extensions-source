package eu.kanade.tachiyomi.extension.en.infernalvoidscans

import eu.kanade.tachiyomi.multisrc.iken.Iken
import eu.kanade.tachiyomi.source.model.Page
import keiyoushi.utils.extractNextJs
import okhttp3.Response

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
            chain.proceed(request.newBuilder().headers(headers).build())
        }
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .set("Cache-Control", "max-age=0")

    override fun pageListParse(response: Response): List<Page> = response.extractNextJs<Images>()?.images.orEmpty().mapIndexed { index, element ->
        Page(index, imageUrl = element.url)
    }
}
