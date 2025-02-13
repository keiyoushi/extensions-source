package eu.kanade.tachiyomi.extension.en.infernalvoidscans

import eu.kanade.tachiyomi.multisrc.iken.Iken
import eu.kanade.tachiyomi.source.model.Page
import okhttp3.Response

class HiveScans : Iken(
    "Hive Scans",
    "en",
    "https://hivetoon.com",
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

    val PageRegex = Regex("""https://storage.\w+.\w+/public//upload/series/(\w+)/(\w+)/(\d+).webp""")

    override fun pageListParse(response: Response): List<Page> {
        return PageRegex.findAll(response.body.string()).mapIndexed { idx, match ->
            Page(idx, imageUrl = match.value)
        }.toList()
    }

    override fun headersBuilder() = super.headersBuilder()
        .set("Cache-Control", "max-age=0")
}
