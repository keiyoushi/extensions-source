package eu.kanade.tachiyomi.extension.ja.nicomanga

import eu.kanade.tachiyomi.source.model.Filter
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
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.jsonInstance
import keiyoushi.utils.parseAs
import keiyoushi.utils.textOrNull
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import org.jsoup.Jsoup

@Source
abstract class Nicomanga : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(2)
    }

    // ============================== Popular ==============================

    override suspend fun getPopularManga(page: Int): MangasPage = fetchMangaListPage("$baseUrl/manga-list.html?p=$page&pr=popular")

    // ============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = fetchMangaListPage("$baseUrl/manga-list.html?p=$page&pr=new")

    // ============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val author = filters.firstInstanceOrNull<AuthorFilter>()?.state?.trim()

        // The site splits name (n) and author (a) search into separate parameters
        // (ANDing them yields "No Manga Found"), and the global search only fills
        // the plain query - so when the name search comes back empty, retry the
        // query as an author search.
        if (query.isNotBlank() && author.isNullOrBlank() && page == 1) {
            val byName = searchPage(page, query, null, filters)
            if (byName.mangas.isNotEmpty()) return byName

            val byAuthor = searchPage(page, null, query, filters)
            if (byAuthor.mangas.isNotEmpty()) return byAuthor

            return byName
        }

        return searchPage(page, query, author, filters)
    }

    private suspend fun searchPage(page: Int, name: String?, author: String?, filters: FilterList): MangasPage {
        val url = "$baseUrl/manga-list.html".toHttpUrl().newBuilder()
            .addQueryParameter("p", page.toString())

        if (!name.isNullOrBlank()) {
            url.addQueryParameter("n", name.trim())
        }

        if (!author.isNullOrBlank()) {
            url.addQueryParameter("a", author.trim())
        }

        filters.firstInstanceOrNull<SortFilter>()?.state?.let { state ->
            val sortables = arrayOf("last_update", "views", "post", "name")
            val prs = arrayOf("all", "popular", "new", "az")

            url.addQueryParameter("s", sortables[state.index])
            url.addQueryParameter("pr", prs[state.index])
            url.addQueryParameter("st", if (state.ascending) "ASC" else "DESC")
        }

        val genres = filters.firstInstanceOrNull<GenreList>()?.state
            ?.filter { it.state }
            ?.map { it.id }
            ?: emptyList()

        if (genres.isNotEmpty()) {
            url.addQueryParameter("g", genres.joinToString(","))
            val logicFilter = filters.firstInstanceOrNull<MatchingLogic>()
            url.addQueryParameter("gm", (logicFilter?.state ?: 1).toString())
        }

        return fetchMangaListPage(url.build().toString())
    }

    // ========================== Details & chapters ========================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val payload = decodePayload(client.get(baseUrl + manga.url).body.string())
        val mangaDto = payload.manga!!

        val details = SManga.create().apply {
            title = mangaDto.n
            thumbnail_url = mangaDto.c
            author = mangaDto.authorsList.joinToString { it.n }
            artist = mangaDto.artists?.ifEmpty { null } ?: mangaDto.authorsList.firstOrNull()?.n
            genre = mangaDto.genresList.joinToString { it.n }
            status = when (mangaDto.statusText?.lowercase()) {
                "on going", "ongoing" -> SManga.ONGOING
                "completed" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
            description = buildString {
                mangaDto.description
                    ?.takeIf { it.isNotBlank() }
                    ?.let { it.htmlToText() }
                    ?.takeIf { !it.equals("Updating", ignoreCase = true) }
                    ?.let { append(it) }
                mangaDto.otherName?.takeIf { it.isNotBlank() }?.let {
                    if (isNotEmpty()) append("\n\n")
                    append(it.trim())
                }
            }
        }

        val chapterList = payload.chaptersList.orEmpty().map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.ur.orEmpty())
                name = element.n?.takeIf { it.isNotBlank() } ?: "Chapter ${element.chapter.orEmpty()}"
                date_upload = parseDate(element.t)
            }
        }

        return SMangaUpdate(details, chapterList)
    }

    // =============================== Pages ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val images = decodePayload(client.get(baseUrl + chapter.url).body.string()).images.orEmpty()

        return images.mapIndexed { i, url ->
            Page(i, imageUrl = url)
        }
    }

    // ============================= Utilities =============================

    /**
     * Popular, latest, and search all render into the same manga list grid.
     */
    private suspend fun fetchMangaListPage(url: String): MangasPage {
        val document = client.get(url).asJsoup()

        val mangas = document.select(".manga-grid .manga-card").map { element ->
            val titleElement = element.selectFirst(".manga-title")!!

            SManga.create().apply {
                title = titleElement.text()
                // The .manga-title contains the manga detail link, while .manga-cover links to the latest chapter
                setUrlWithoutDomain(titleElement.attr("abs:href"))
                thumbnail_url = element.selectFirst("img.manga-img")?.let { img ->
                    img.attrOrNull("abs:data-src") ?: img.attrOrNull("abs:src")
                }
            }
        }

        val hasNextPage = document.select(".custom-pagination .page-link.next:not(.disabled)").isNotEmpty()
        return MangasPage(mangas, hasNextPage)
    }

    /**
     * The site renders details, chapters, and page data inside an obfuscated RSC text stream.
     * Each character encodes one byte: (codePoint - 19968) xor key[index % key.length].
     * The result is a UTF-8 JSON document.
     */
    private fun decodePayload(body: String): Payload {
        val streamMarker = Regex("""([0-9a-z]+):T[0-9a-f]+,""")

        for (match in streamMarker.findAll(body)) {
            // The payload blob lives right after the marker (usually in the next script tag).
            val windowEnd = minOf(body.length, match.range.last + 1 + 600)
            var start = match.range.last + 1
            var found = false
            while (start < windowEnd) {
                if (body.codePointAt(start) >= 0x3000) {
                    found = true
                    break
                }
                start++
            }
            if (!found) continue

            val end = body.indexOf("</script>", start)
            if (end == -1) continue
            val decoded = decodeXorStream(body.substring(start, end))
            if (!decoded.trimStart().startsWith("{")) continue

            val jsonEnd = runCatching { findJsonObjectEnd(decoded) }.getOrNull() ?: continue
            runCatching { jsonInstance.decodeFromString(Payload.serializer(), decoded.substring(0, jsonEnd)) }
                .getOrNull()
                ?.takeIf { it.manga != null }
                ?.let { return it }
        }

        throw Exception("Encoded site payload not found")
    }

    private fun decodeXorStream(encoded: String): String {
        val key = PAYLOAD_KEY
        val bytes = ByteArray(encoded.length)
        for (i in encoded.indices) {
            bytes[i] = (((encoded.codePointAt(i) - 19968) xor key[i % key.length].code) and 0xFF).toByte()
        }
        return String(bytes, Charsets.UTF_8)
    }

    private fun findJsonObjectEnd(text: String): Int {
        var depth = 0
        var inString = false
        var escaped = false
        for (i in text.indices) {
            val c = text[i]
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
                continue
            }
            when {
                c == '"' -> inString = true
                c == '{' -> depth++
                c == '}' -> {
                    depth--
                    if (depth == 0) return i + 1
                }
            }
        }
        throw Exception("Malformed encoded site payload")
    }

    /**
     * Some payloads wrap the description in HTML (<p> paragraphs, <div class=sContent>,
     * <br> line breaks) while others are plain text - convert either to plain text,
     * keeping paragraphs separated.
     */
    private fun String.htmlToText(): String {
        val document = Jsoup.parseBodyFragment(this)
        document.select("br").append("\n")
        document.select("p, div").append("\n\n")

        return document.wholeText()
            .lines()
            .map { it.trim() }
            .joinToString("\n")
            .replace(Regex("\\n{3,}"), "\n\n")
    }

    /**
     * The payload only carries relative time text for chapters, e.g. "7 days ago"
     * (note the site also sends "2 year ago"). Returns 0 when unparseable.
     */
    private fun parseDate(text: String?): Long {
        val match = relativeDateRegex.find(text?.trim() ?: return 0L) ?: return 0L
        val amount = match.groupValues[1].toLongOrNull() ?: return 0L
        // Week/month/year are date-based units that Instant arithmetic rejects, so
        // convert to seconds directly (month/year use their average durations).
        val unitSeconds = when (match.groupValues[2]) {
            "second" -> 1L
            "minute" -> 60L
            "hour" -> 3600L
            "day" -> 86400L
            "week" -> 604800L
            "month" -> 2629746L
            "year" -> 31556952L
            else -> return 0L
        }
        return System.currentTimeMillis() - amount * unitSeconds * 1000
    }

    // ============================== Filters ==============================

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = GenresPayload(
        client.get("$baseUrl/manga-list.html").asJsoup()
            .select("a.genre-tag")
            .mapNotNull { element ->
                val id = element.attr("abs:href").toHttpUrlOrNull()?.queryParameter("g")
                val name = element.textOrNull()
                if (id == null || name == null) return@mapNotNull null
                NGenre(id = id, name = name)
            },
    ).toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<GenresPayload>()
            ?.genres
            ?.map { Genre(it.name, it.id) }
            .orEmpty()

        return FilterList(
            AuthorFilter(),
            SortFilter(),
            MatchingLogic(),
            Filter.Separator(),
            GenreList(genres),
        )
    }

    // ============================= Companion =============================

    private companion object {
        const val PAYLOAD_KEY = "NicoMangaX2"

        val relativeDateRegex = Regex("""(\d+)\s+(second|minute|hour|day|week|month|year)s?\s+ago""")
    }
}
