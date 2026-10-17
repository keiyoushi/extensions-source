package eu.kanade.tachiyomi.extension.en.snafucomics

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import kotlin.time.Duration.Companion.seconds

@Source
abstract class SnafuComic : KeiSource() {

    override val supportsLatest = false

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(permits = 2, period = 1.seconds)

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/${manga.url}"

    override suspend fun getPopularManga(page: Int): MangasPage {
        if (page > 1) return MangasPage(emptyList(), false)
        return MangasPage(catalogEntries().values.toList(), false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val entries = catalogEntries().values.toList()
        if (query.isBlank()) return MangasPage(entries, false)
        val normalized = query.trim().lowercase()
        return MangasPage(
            entries.filter {
                it.title.lowercase().contains(normalized) ||
                    it.author.orEmpty().lowercase().contains(normalized)
            },
            false,
        )
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val updatedManga = async {
            if (!fetchDetails) return@async manga
            val entry = catalogEntries()[manga.url]
                ?: throw IllegalStateException("Series not found in catalog: ${manga.url}")
            manga.apply {
                title = entry.title
                author = entry.author
                thumbnail_url = entry.thumbnail_url
            }
        }
        val updatedChapters = async {
            if (!fetchChapters) return@async chapters
            parseChapters(manga.url)
        }
        SMangaUpdate(updatedManga.await(), updatedChapters.await())
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = try {
        client.get("$baseUrl${chapter.url}").asJsoup()
            .select("img[src*=\"/comics/\"]")
            .mapIndexed { index, img -> Page(index, imageUrl = img.attr("abs:src")) }
    } catch (_: Exception) {
        emptyList()
    }

    private suspend fun catalogEntries(): Map<String, SManga> = parseCatalog(client.get("$baseUrl/all-comics").asJsoup()).associateBy { it.url }

    private fun parseCatalog(document: Document): List<SManga> = document.select("a[href]").mapNotNull { link ->
        val path = toPath(link.attr("href")) ?: return@mapNotNull null
        if (!path.matches(SERIES_PATH_REGEX)) return@mapNotNull null
        val img = link.selectFirst("img") ?: return@mapNotNull null
        val text = link.text()
        if (!text.contains(" by ")) return@mapNotNull null
        val slug = path.removePrefix("/")
        val title = img.attr("alt").ifBlank { text.substringBefore(" by ").trim() }
        check(title.isNotBlank()) { "Empty title for series entry: $slug" }
        SManga.create().apply {
            url = slug
            this.title = title
            author = text.substringAfter(" by ").trim().ifEmpty { null }
            thumbnail_url = img.attr("abs:src").ifEmpty { null }
        }
    }.distinctBy { it.url }

    private suspend fun parseChapters(slug: String): List<SChapter> {
        val document = client.get("$baseUrl/$slug/archive").asJsoup()
        val heading = document.select("h1, h2, h3, h4")
            .firstOrNull { it.text().equals("Archive", ignoreCase = true) }
        val links = if (heading != null) {
            buildList {
                var sibling = heading.nextElementSibling()
                while (sibling != null && sibling.tagName() !in HEADING_TAGS) {
                    addAll(sibling.select("a[href]"))
                    sibling = sibling.nextElementSibling()
                }
            }
        } else {
            document.select("a[href]")
        }
        val chapterLinks = links.mapNotNull { link ->
            val path = toPath(link.attr("href")) ?: return@mapNotNull null
            if (!path.matches(Regex("^/$slug/.+"))) return@mapNotNull null
            link to path
        }
        if (chapterLinks.isNotEmpty()) {
            return chapterLinks.map { (link, path) ->
                val name = link.text().trim().ifEmpty { path.substringAfterLast("/").ifEmpty { path } }
                SChapter.create().apply {
                    url = path
                    this.name = name
                    chapterNumberRegex.find(name)?.value?.toFloatOrNull()?.let {
                        chapter_number = it
                    }
                }
            }.distinctBy { it.url }
        }
        return document.select("select option[value]").mapNotNull { option ->
            val value = option.attr("value").trim()
            if (value.isEmpty()) return@mapNotNull null
            SChapter.create().apply {
                url = toPath(value) ?: "/$slug/$value"
                name = option.text().trim().ifEmpty { value }
            }
        }.distinctBy { it.url }
    }

    private fun toPath(href: String): String? {
        val trimmed = href.trim()
        return when {
            trimmed.startsWith("/") -> trimmed
            trimmed.startsWith(baseUrl) -> trimmed.removePrefix(baseUrl).ifEmpty { "/" }
            else -> null
        }
    }

    private val chapterNumberRegex = Regex("""\d+(\.\d+)?""")

    companion object {
        private val SERIES_PATH_REGEX = Regex("^/[^/]+$")
        private val HEADING_TAGS = setOf("h1", "h2", "h3", "h4")
    }
}
