package eu.kanade.tachiyomi.extension.en.infernalvoidscans

import eu.kanade.tachiyomi.multisrc.iken.Iken
import eu.kanade.tachiyomi.source.model.Page
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Response
import uy.kohesive.injekt.injectLazy

class HiveScans : Iken(
    "Hive Scans",
    "en",
    "https://hivetoon.com",
) {

    private val json by injectLazy<Json>()

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

    private val pageRegex = Regex("""\\"images\\":(\[.*?]).*?nextChapter""")

    @Serializable
    class PageDTO(
        val url: String,
    )

    override fun pageListParse(response: Response): List<Page> {
        val pageDataArray = pageRegex.find(response.body.string())?.destructured?.component1()?.replace("\\", "") ?: return listOf()
        return json.decodeFromString<List<PageDTO>>(pageDataArray).mapIndexed { idx, page ->
            Page(idx, imageUrl = page.url)
        }
    }

    override fun headersBuilder() = super.headersBuilder()
        .set("Cache-Control", "max-age=0")
}
