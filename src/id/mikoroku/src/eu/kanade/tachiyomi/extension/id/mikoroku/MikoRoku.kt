package eu.kanade.tachiyomi.extension.id.mikoroku

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup

@Source
abstract class MikoRoku : KeiSource() {

    override suspend fun getPopularManga(page: Int): MangasPage = fetchCatalog()
        .sortedByDescending { it.rating }
        .toMangasPage()

    override suspend fun getLatestUpdates(page: Int): MangasPage = fetchCatalog().toMangasPage()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val normalizedQuery = query.normalize()
        if (normalizedQuery.isEmpty() && query.isNotBlank()) return MangasPage(emptyList(), false)

        return fetchCatalog()
            .filter { entry ->
                entry.title.normalize().contains(normalizedQuery) ||
                    entry.altTitle.split(";").any { it.normalize().contains(normalizedQuery) }
            }
            .toMangasPage()
    }

    private fun List<CatalogEntry>.toMangasPage() = MangasPage(map { it.toSManga() }, false)

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host.removePrefix("www.")) return null
        val slug = url.queryParameter("slug") ?: return null

        return buildManga(slug, fetchFirestoreDoc(slug)).apply { initialized = true }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val slug = "$baseUrl${manga.url}".toHttpUrl().queryParameter("slug")
            ?: throw Exception("Invalid manga URL: ${manga.url}")

        val doc = fetchFirestoreDoc(slug)
        val details = buildManga(slug, doc)

        return SMangaUpdate(manga = details, chapters = fetchChapterList(slug, doc, details.title))
    }

    private suspend fun buildManga(slug: String, doc: FirestoreMap?): SManga {
        val fields = doc?.fields.orEmpty()
        val entry = fetchCatalog().find { it.slug == slug }
        return SManga.create().apply {
            url = "/detail.html?slug=$slug"
            title = fields.getString("title").nonBlank()
                ?: entry?.title
                ?: throw Exception("Manga tidak ditemukan")
            author = fields.getString("author").nonBlank() ?: entry?.author.nonBlank()
            artist = fields.getString("artist").nonBlank() ?: entry?.artist.nonBlank()
            description = fields.getString("desc").nonBlank()?.let { Jsoup.parseBodyFragment(it).text() }
                ?: entry?.desc.nonBlank()
            genre = fields.getStringList("genres").takeIf { it.isNotEmpty() }?.joinToString()
                ?: entry?.genres?.takeIf { it.isNotEmpty() }?.joinToString()
            status = (fields.getString("status") ?: entry?.status).toMangaStatus()
            thumbnail_url = fields.getString("img").nonBlank() ?: entry?.img.nonBlank()
        }
    }

    private fun String?.toMangaStatus(): Int = when (this?.lowercase()) {
        "ongoing" -> SManga.ONGOING
        "completed" -> SManga.COMPLETED
        "hiatus" -> SManga.ON_HIATUS
        "dropped", "cancelled" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    private suspend fun fetchChapterList(slug: String, doc: FirestoreMap?, title: String): List<SChapter> {
        val subcollectionChapters = fetchChaptersSubcollection(slug)
        val fieldChapters = doc?.toChapterInfos().orEmpty()
            .sortedWith(compareByDescending<ChapterInfo> { it.number }.thenByDescending { it.order })
            .map { it.toSChapter(slug) }
        val firestoreChapters = (subcollectionChapters + fieldChapters)
            .distinctBy { it.name.chapterNumber() }

        val blogger1 = searchBlogger(
            "https://www.mikodrive.my.id/feeds/posts/default",
            title,
            500,
        ).mapNotNull { it.toSChapter(slug, title) }
        val blogger2 = searchBlogger(
            "https://www.yomidays.my.id/feeds/posts/default",
            title,
            500,
        ).mapNotNull { it.toSChapter(slug, title) }
        val seen = firestoreChapters.mapTo(mutableSetOf()) { it.name.chapterNumber() }
        val merged = firestoreChapters.toMutableList()
        for (ch in blogger1 + blogger2) {
            if (seen.add(ch.name.chapterNumber())) merged.add(ch)
        }
        return merged.sortedByDescending { it.name.chapterNumber() }
    }

    private suspend fun fetchChaptersSubcollection(slug: String): List<SChapter> {
        val url = "https://firestore.googleapis.com/v1/projects/mikoroku/databases/(default)/documents/manga/$slug/chapters?pageSize=500"

        val response = client.get(url, ensureSuccess = false)
        if (response.code != 200) {
            response.close()
            return emptyList()
        }
        return response.parseAs<FirestoreListResponse>().documents.mapNotNull { doc ->
            val fields = doc.fields
            if (fields.getBoolean("isDraft") == true) return@mapNotNull null
            val title = fields.getString("title")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val key = doc.name.substringAfterLast("/")
            SChapter.create().apply {
                name = title
                date_upload = fields.getLong("date") ?: 0L
                this.url = "/manga/$slug/chapter/$key"
            }
        }
    }

    private fun FirestoreMap.toChapterInfos(): List<ChapterInfo> = fields
        .filterKeys { it.startsWith("chapters.") }
        .mapNotNull { (key, value) ->
            val chapter = value.mapValue?.fields ?: return@mapNotNull null
            if (chapter.getBoolean("isDraft") == true) return@mapNotNull null

            ChapterInfo(
                key = key.removePrefix("chapters."),
                title = chapter.getString("title").nonBlank() ?: return@mapNotNull null,
                date = chapter.getLong("date") ?: 0L,
                order = chapter.getLong("order") ?: 0L,
            )
        }

    private class ChapterInfo(
        val key: String,
        val title: String,
        val date: Long,
        val order: Long,
    ) {
        val number = title.chapterNumber()

        fun toSChapter(slug: String) = SChapter.create().apply {
            name = title
            date_upload = date
            url = "/manga/$slug/chapter/$key"
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        POST_URL_REGEX.find(chapter.url)?.let { match ->
            return fetchBloggerPostPages(match.groupValues[2])
        }

        val (slug, key) = CHAPTER_URL_REGEX.find(chapter.url)?.destructured
            ?: throw Exception("Unsupported chapter URL: ${chapter.url}")

        fetchFirestorePages(slug, key)?.let { return it }

        val title = fetchCatalog().find { it.slug == slug }?.title ?: return emptyList()
        return fetchBloggerChapterPages(title, chapter.name)
    }

    private suspend fun fetchFirestorePages(slug: String, key: String): List<Page>? {
        val subcollectionImages = run {
            val url = "https://firestore.googleapis.com/v1/projects/mikoroku/databases/(default)/documents/manga/$slug/chapters/$key"
            val response = client.get(url, ensureSuccess = false)
            if (response.code != 200) {
                response.close()
                null
            } else {
                response.parseAs<FirestoreDoc>().fields.getStringList("images").takeIf { it.isNotEmpty() }
            }
        }
        if (!subcollectionImages.isNullOrEmpty()) {
            return subcollectionImages.toPages()
        }

        val fieldImages = fetchFirestoreDoc(slug)
            ?.fields?.get("chapters.$key")
            ?.mapValue?.fields
            ?.getStringList("images")
            .orEmpty()
        if (fieldImages.isEmpty()) return null

        return fieldImages.toPages()
    }

    private suspend fun fetchBloggerPostPages(postId: String): List<Page> {
        val url = "https://www.mikodrive.my.id/feeds/posts/default/$postId".toHttpUrl().newBuilder()
            .addQueryParameter("alt", "json")
            .build()

        return client.get(url).parseAs<BloggerEntryResponse>().entry.imageUrls().toPages()
    }

    private suspend fun fetchBloggerChapterPages(mangaTitle: String, chapterName: String): List<Page> {
        val label = chapterName.chapterLabel() ?: return emptyList()
        val number = label.chapterNumber()

        return searchBlogger("https://www.mikodrive.my.id/feeds/posts/default", "$mangaTitle $label", 5)
            .firstOrNull { it.chapterLabel(mangaTitle)?.chapterNumber() == number }
            ?.imageUrls()
            .orEmpty()
            .toPages()
    }

    private fun List<String>.toPages(): List<Page> = mapIndexed { index, url -> Page(index, imageUrl = url) }

    private suspend fun fetchCatalog(): List<CatalogEntry> {
        val catalog = client.get("https://raw.githubusercontent.com/moemaomao/mymangadata/main/all-manga.json").parseAs<List<CatalogEntry>>()
        return catalog
    }

    private suspend fun fetchFirestoreDoc(slug: String): FirestoreMap? {
        val url = "https://firestore.googleapis.com/v1/projects/mikoroku/databases/(default)/documents/manga/$slug"

        val response = client.get(url, ensureSuccess = false)
        if (response.code != 200) {
            response.close()
            return null
        }
        return response.parseAs<FirestoreMap>()
    }

    private suspend fun searchBlogger(feedUrl: String, query: String, maxResults: Int): List<BloggerEntry> {
        val url = feedUrl.toHttpUrl().newBuilder()
            .addQueryParameter("alt", "json")
            .addQueryParameter("max-results", maxResults.toString())
            .addQueryParameter("q", query)
            .build()

        val entries = client.get(url).parseAs<BloggerFeedResponse>().feed.entry
        return entries
    }

    private fun String.normalize(): String = lowercase().filter { it.isLetterOrDigit() }

    private fun String?.nonBlank(): String? = this?.takeIf { it.isNotBlank() }

    companion object {
        private val CHAPTER_URL_REGEX = """/manga/([^/]+)/chapter/([^/]+)""".toRegex()
        private val POST_URL_REGEX = """/manga/([^/]+)/post/([^/]+)""".toRegex()
    }
}
