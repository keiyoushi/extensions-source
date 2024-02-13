package eu.kanade.tachiyomi.extension.id.nekomik

import app.cash.quickjs.QuickJs
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Page
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document

class Nekomik : MangaThemesia("Nekomik", "https://nekomik.me", "id") {

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(4)
        .build()

    override val hasProjectPage = true

    override fun pageListParse(document: Document): List<Page> {
        val obfuscatedJs = document.selectFirst("script:containsData(fromCharCode)")?.data()
            ?: return super.pageListParse(document)

        val data = QuickJs.create().use { context ->
            context.evaluate(
                """
                ts_reader = { run: function(...args) { whatever = args[0] } };
                $obfuscatedJs;
                JSON.stringify(whatever);
                """.trimIndent(),
            ) as String
        }

        val tsReader = json.decodeFromString<TSReader>(data)
        val imageUrls = tsReader.sources.firstOrNull()?.images ?: return emptyList()
        return imageUrls.mapIndexed { index, imageUrl -> Page(index, document.location(), imageUrl) }
    }

    @Serializable
    data class TSReader(
        val sources: List<ReaderImageSource>,
    )

    @Serializable
    data class ReaderImageSource(
        val source: String,
        val images: List<String>,
    )
}
