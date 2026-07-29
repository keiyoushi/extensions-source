package eu.kanade.tachiyomi.extension.id.kiryuu

import eu.kanade.tachiyomi.multisrc.natsuid.Manga
import eu.kanade.tachiyomi.multisrc.natsuid.NatsuId
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Source
abstract class Kiryuu : NatsuId() {

    override fun OkHttpClient.Builder.configureClient() = rateLimit(4)

    override fun chapterListPage(mangaId: String): Int = 1

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = if (page == 1) "$baseUrl/latest/" else "$baseUrl/latest/page/$page/"

        val document = client.get(url).use { it.asJsoup() }

        val slugs = document.select("a[color=primary]").mapNotNull {
            val href = it.absUrl("href")
            if (href.isNotBlank()) {
                href.toHttpUrl().pathSegments.getOrNull(1)
            } else {
                null
            }
        }.distinct()

        if (slugs.isEmpty()) return MangasPage(emptyList(), false)

        val detailsUrl = "$baseUrl/wp-json/wp/v2/manga".toHttpUrl().newBuilder().apply {
            slugs.forEach { slug ->
                addQueryParameter("slug[]", slug)
            }
            addQueryParameter("per_page", "${slugs.size + 1}")
            addQueryParameter("_embed", null)
        }.build()

        val details = client.get(detailsUrl).parseAs<List<Manga>>(transform = ::transformJsonResponse)
            .associateBy { it.slug }

        val mangas = slugs.mapNotNull { slug ->
            details[slug]?.toSManga()
        }

        val hasNextPage = slugs.size >= 24

        return MangasPage(mangas, hasNextPage)
    }
}
