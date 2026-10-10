package eu.kanade.tachiyomi.extension.pt.hentaikai

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Element

@Source
abstract class HentaiKai : KeiSource() {

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = if (page == 1) "$baseUrl/mangas-hentai/" else "$baseUrl/mangas-hentai/page/$page/"
        return typeList(url, page, "mangas-hentai")
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = if (page == 1) "$baseUrl/hentais/" else "$baseUrl/hentais/page/$page/"
        return typeList(url, page, "hentais")
    }

    private suspend fun typeList(url: String, page: Int, typePath: String): MangasPage {
        val document = client.get(url).asJsoup()
        val mangas = document.select("a:has(div.box-info-serie)").map(::listingParse)
        val hasNextPage = document.selectFirst("a[href*=\"/$typePath/page/${page + 1}/\"]") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun listingParse(element: Element): SManga = SManga.create().apply {
        url = element.attr("abs:href").toHttpUrl().encodedPath.removePrefix("/")
        val rawTitle = element.attr("title").ifBlank {
            element.selectFirst("img")?.attr("alt").orEmpty()
        }.trim()
        check(rawTitle.isNotBlank()) { "Empty title for entry: $url" }
        title = rawTitle
        thumbnail_url = element.selectFirst("img")?.let {
            it.attr("data-lazy-src").ifEmpty { it.attr("src") }
        }?.takeUnless { it.startsWith("data:") }?.ifEmpty { null }
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (page > 1) return MangasPage(emptyList(), false)
        val type = filters.firstInstanceOrNull<TypeFilter>()?.toUriPart() ?: ""
        val base = if (type.isBlank()) baseUrl else "$baseUrl/$type"
        val url = base.toHttpUrl().newBuilder()
            .apply { if (query.isNotBlank()) addQueryParameter("s", query) }
            .build()
        val document = client.get(url).asJsoup()
        return MangasPage(document.select("a:has(div.box-info-serie)").map(::listingParse), false)
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        Filter.Header("Content type"),
        TypeFilter(),
    )

    private class TypeFilter :
        Filter.Select<String>(
            "Type",
            arrayOf(
                "All",
                "Hentais",
                "Mangás Hentai",
                "Doujinshis",
                "HQs Hentai",
                "Hentai 3D",
                "Imagens Hentai",
                "One-shots",
                "Animes Hentai",
                "Coloridos",
                "Exclusivo",
            ),
        ) {
        fun toUriPart(): String = when (state) {
            1 -> "hentais"
            2 -> "mangas-hentai"
            3 -> "doujinshis"
            4 -> "hqs-hentai"
            5 -> "hentai-3d"
            6 -> "imagens-hentai"
            7 -> "one-shots"
            8 -> "animes-hentai"
            9 -> "cor/colorido"
            10 -> "exclusivo"
            else -> ""
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get("$baseUrl/${manga.url}").asJsoup()

        val updatedManga = manga.apply {
            val rawTitle = document.selectFirst("h1")?.text()?.trim().orEmpty()
            check(rawTitle.isNotBlank()) { "Empty title for ${manga.url}" }
            title = rawTitle
            thumbnail_url = document.select("li img[data-src]")
                .firstOrNull()
                ?.attr("data-src")?.ifEmpty { null }
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        }

        val updatedChapters = listOf(
            SChapter.create().apply {
                url = manga.url
                name = "Oneshot"
            },
        )

        return SMangaUpdate(updatedManga, updatedChapters)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get("$baseUrl/${chapter.url}").asJsoup()
        .select("li img[data-src]")
        .map { it.attr("data-src").ifEmpty { it.attr("src") } }
        .filter { it.isNotBlank() && !it.startsWith("data:") }
        .distinct()
        .mapIndexed { index, url -> Page(index, imageUrl = url) }
}
