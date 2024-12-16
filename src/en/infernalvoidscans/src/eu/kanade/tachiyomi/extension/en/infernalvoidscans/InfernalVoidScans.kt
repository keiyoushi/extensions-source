package eu.kanade.tachiyomi.extension.en.infernalvoidscans

import eu.kanade.tachiyomi.multisrc.iken.Iken
import eu.kanade.tachiyomi.multisrc.iken.SearchResponse
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Response
import uy.kohesive.injekt.injectLazy

class InfernalVoidScans : Iken(
    "Infernal Void Scans",
    "en",
    "https://hivetoon.com",
) {
    override val client = super.client.newBuilder()
        .addInterceptor { chain ->
            val request = chain.request()
            val headers = request.headers.newBuilder()
                .set("Cache-Control", "max-age=0")
                .build()
            chain.proceed(request.newBuilder().headers(headers).build())
        }
        .build()

    private val json by injectLazy<Json>()

    override fun headersBuilder() = super.headersBuilder()
        .set("Cache-Control", "max-age=0")

    private val titleCache by lazy {
        val response = client.newCall(GET("$baseUrl/api/query?perPage=9999", headers)).execute()
        val data = json.decodeFromString<SearchResponse>(response.body.string())
        data.posts
            .filterNot { it.isNovel }
            .associateBy { it.slug }
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val entries = document.select(".group a").mapNotNull {
            titleCache[it.absUrl("href").substringAfter("series/")]?.toSManga()
        }

        return MangasPage(entries, false)
    }
}
