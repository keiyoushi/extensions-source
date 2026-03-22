package eu.kanade.tachiyomi.extension.id.riztranslation

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.parseAs
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale

class Riztranslation : HttpSource() {

    override val name = "Riztranslation"

    override val baseUrl = "https://riztranslation.pages.dev"

    private val apiUrl = "https://uefnaojxivvxeamljskn.supabase.co/rest/v1"

    override val lang = "id"

    override val supportsLatest = true

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.ROOT)
    private val dateFormatNoMs = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT)

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("apikey", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InVlZm5hb2p4aXZ2eGVhbWxqc2tuIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NDc3MTU5MjksImV4cCI6MjA2MzI5MTkyOX0._lEBN5puTvATwtYodg4zbcoTwg0ss3j2BebD8WoHt9A")

    // ========================= Popular =========================
    override fun popularMangaRequest(page: Int): Request {
        val offset = (page - 1) * 20
        return GET("$apiUrl/Book?select=id,judul,cover&type=not.ilike.*novel*&order=id.desc&offset=$offset&limit=20", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val books = response.parseAs<List<BookDto>>()
        val hasNextPage = books.size == 20

        val mangaList = books.mapNotNull { book ->
            val bookTitle = book.judul ?: return@mapNotNull null
            SManga.create().apply {
                url = "/detail/${book.id}"
                title = bookTitle
                thumbnail_url = book.cover
            }
        }
        return MangasPage(mangaList, hasNextPage)
    }

    // ========================= Latest =========================
    override fun latestUpdatesRequest(page: Int): Request {
        val offset = (page - 1) * 30
        return GET("$apiUrl/Chapter?select=bookId,Book!inner(id,judul,cover)&Book.type=not.ilike.*novel*&order=created_at.desc&offset=$offset&limit=30", headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val chapters = response.parseAs<List<LatestChapterDto>>()
        val hasNextPage = chapters.size == 30

        val mangaList = chapters.mapNotNull { it.book }.distinctBy { it.id }.mapNotNull { book ->
            if (book.judul == null) return@mapNotNull null
            SManga.create().apply {
                url = "/detail/${book.id}"
                title = book.judul!!
                thumbnail_url = book.cover
            }
        }
        return MangasPage(mangaList, hasNextPage)
    }

    // ========================= Search =========================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.startsWith(PREFIX_ID_SEARCH)) {
            val id = query.removePrefix(PREFIX_ID_SEARCH)
            return GET("$apiUrl/Book?select=id,judul,cover&type=not.ilike.*novel*&id=eq.$id", headers)
        } else if (query.contains("/detail/") || query.contains("/view/")) {
            val segments = query.split("/")
            val typeIndex = segments.indexOfFirst { it == "detail" || it == "view" }
            if (typeIndex != -1 && typeIndex + 1 < segments.size) {
                val id = segments[typeIndex + 1]
                return GET("$apiUrl/Book?select=id,judul,cover&type=not.ilike.*novel*&id=eq.$id", headers)
            }
        }
        val offset = (page - 1) * 20
        val selects = mutableListOf("id", "judul", "cover")
        val queries = mutableListOf<String>()

        var typeFilter = "not.ilike.*novel*"
        var sortColumn = "updated_at.desc"

        val selectedGenres = mutableListOf<String>()

        for (filter in filters) {
            when (filter) {
                is TypeFilter -> {
                    typeFilter = when (filter.state) {
                        1 -> "eq.Manga"
                        2 -> "eq.Web%20Manga"
                        else -> "not.ilike.*novel*"
                    }
                }
                is StatusFilter -> {
                    when (filter.state) {
                        1 -> queries.add("status=eq.ongoing")
                        2 -> queries.add("status=ilike.*complete*")
                        3 -> queries.add("status=eq.oneshot")
                    }
                }
                is SortFilter -> {
                    val isAsc = filter.state?.ascending == true
                    val direction = if (isAsc) "asc" else "desc"
                    sortColumn = when (filter.state?.index) {
                        0 -> "updated_at.$direction"
                        1 -> "created_at.$direction"
                        2 -> "judul.$direction"
                        else -> "updated_at.desc"
                    }
                }
                is HasChapterFilter -> {
                    if (filter.state) {
                        selects.add("Chapter!inner()")
                    }
                }
                is GenreFilter -> {
                    filter.state.filter { it.state }.forEach {
                        selectedGenres.add(it.id)
                    }
                }
                else -> {}
            }
        }

        if (selectedGenres.isNotEmpty()) {
            selects.add("Genre!inner(id)")
            queries.add("Genre.id=in.(${selectedGenres.joinToString(",")})")
        }

        if (query.isNotEmpty()) {
            queries.add("judul=ilike.*${URLEncoder.encode(query, "UTF-8")}*")
        }

        queries.add("type=$typeFilter")
        queries.add("order=$sortColumn")
        queries.add("offset=$offset")
        queries.add("limit=20")

        val url = "$apiUrl/Book?select=${selects.joinToString(",")}&${queries.joinToString("&")}"

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // ========================= Filters =========================
    override fun getFilterList() = FilterList(
        HasChapterFilter(),
        TypeFilter(),
        StatusFilter(),
        SortFilter(),
        GenreFilter(),
    )

    // ========================= Details =========================
    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url

    override fun mangaDetailsRequest(manga: SManga): Request {
        val id = manga.url.substringAfterLast("/")
        return GET("$apiUrl/Book?select=*%2Cgenres%3A_BookGenre%28genre%3AGenre%28*%29%29&type=not.ilike.*novel*&id=eq.$id", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val books = response.parseAs<List<BookDto>>()
        if (books.isEmpty()) throw Exception("Manga not found")
        val book = books.first()

        val bookTitle = book.judul ?: throw Exception("Manga not found")

        return SManga.create().apply {
            title = bookTitle
            thumbnail_url = book.cover
            author = book.author
            artist = book.artist
            description = book.synopsis
            status = parseStatus(book.status)
            genre = book.genres?.mapNotNull { it.genre?.nama }?.joinToString()
        }
    }

    private fun parseStatus(status: String?) = when (status?.lowercase()) {
        "completed", "complete", "oneshot" -> SManga.COMPLETED
        "ongoing" -> SManga.ONGOING
        else -> SManga.UNKNOWN
    }

    // ========================= Chapters =========================
    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url

    override fun chapterListRequest(manga: SManga): Request {
        val id = manga.url.substringAfterLast("/")
        return GET("$apiUrl/Chapter?select=id,bookId,chapter,nama,created_at&bookId=eq.$id&order=chapter.desc", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapters = response.parseAs<List<ChapterDto>>()
        return chapters.map { ch ->
            SChapter.create().apply {
                url = "/view/${ch.bookId}/${ch.id}"
                name = buildString {
                    val chapNum = ch.chapter?.toString()?.removeSuffix(".0")
                    if (chapNum != null) {
                        append("Chapter $chapNum")
                    }
                    if (!ch.nama.isNullOrBlank()) {
                        if (isNotEmpty()) append(" - ")
                        append(ch.nama)
                    }
                }
                chapter_number = ch.chapter ?: -1f
                date_upload = parseDate(ch.createdAt)
            }
        }
    }

    private fun parseDate(dateStr: String?): Long {
        if (dateStr == null) return 0L
        return runCatching { dateFormat.parse(dateStr)?.time }
            .getOrNull() ?: runCatching { dateFormatNoMs.parse(dateStr)?.time }
            .getOrNull() ?: 0L
    }

    // ========================= Pages =========================
    override fun pageListRequest(chapter: SChapter): Request {
        val id = chapter.url.substringAfterLast("/")
        return GET("$apiUrl/Chapter?select=isigambar&id=eq.$id", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val chapters = response.parseAs<List<ChapterDto>>()
        if (chapters.isEmpty()) throw Exception("Chapter not found")
        val isigambar = chapters.first().isigambar

        if (isigambar.isNullOrBlank()) return emptyList()

        val images = try {
            isigambar.parseAs<List<String>>()
        } catch (e: Exception) {
            emptyList()
        }

        return images.mapIndexed { i, url ->
            Page(i, "", url)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    companion object {
        const val PREFIX_ID_SEARCH = "id:"
    }
}
