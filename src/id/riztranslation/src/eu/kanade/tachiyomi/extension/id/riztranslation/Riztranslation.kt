package eu.kanade.tachiyomi.extension.id.riztranslation

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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class Riztranslation : KeiSource() {

    private val apiUrl = "https://uefnaojxivvxeamljskn.supabase.co/rest/v1"

    private val apiHeaders: Headers
        get() = headers.newBuilder()
            .add("apikey", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InVlZm5hb2p4aXZ2eGVhbWxqc2tuIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NDc3MTU5MjksImV4cCI6MjA2MzI5MTkyOX0._lEBN5puTvATwtYodg4zbcoTwg0ss3j2BebD8WoHt9A")
            .build()

    // ========================= Popular =========================
    override suspend fun getPopularManga(page: Int): MangasPage {
        val offset = (page - 1) * 20
        return client.get("$apiUrl/Book?select=id,judul,cover&type=not.ilike.*novel*&order=id.desc&offset=$offset&limit=20", apiHeaders)
            .parseAs<List<BookDto>>()
            .toMangasPage()
    }

    private fun List<BookDto>.toMangasPage() = MangasPage(map { it.toSManga() }, size == 20)

    // ========================= Latest =========================
    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val offset = (page - 1) * 30
        val chapters = client.get("$apiUrl/Chapter?select=bookId,Book!inner(id,judul,cover)&Book.type=not.ilike.*novel*&order=created_at.desc&offset=$offset&limit=30", apiHeaders)
            .parseAs<List<LatestChapterDto>>()
        val hasNextPage = chapters.size == 30

        val mangaList = chapters.mapNotNull { it.book }.distinctBy { it.id }.map { it.toSManga() }
        return MangasPage(mangaList, hasNextPage)
    }

    // ========================= Search =========================
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val typeIndex = url.pathSegments.indexOfFirst { it == "detail" || it == "view" }
        if (typeIndex == -1 || typeIndex + 1 >= url.pathSize) return null
        val id = url.pathSegments[typeIndex + 1]

        return client.get("$apiUrl/Book?select=id,judul,cover&type=not.ilike.*novel*&id=eq.$id", apiHeaders)
            .parseAs<List<BookDto>>()
            .firstOrNull()
            ?.toSManga()
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val offset = (page - 1) * 20
        val url = "$apiUrl/Book".toHttpUrl().newBuilder()

        val selects = mutableListOf("id", "judul", "cover")
        var typeFilter = "not.ilike.*novel*"
        var sortColumn = "updated_at.desc"

        val selectedGenres = mutableListOf<String>()

        for (filter in filters) {
            when (filter) {
                is TypeFilter -> {
                    typeFilter = when (filter.state) {
                        1 -> "eq.Manga"
                        2 -> "eq.Web Manga"
                        else -> "not.ilike.*novel*"
                    }
                }
                is StatusFilter -> {
                    when (filter.state) {
                        1 -> url.addQueryParameter("status", "eq.ongoing")
                        2 -> url.addQueryParameter("status", "ilike.*complete*")
                        3 -> url.addQueryParameter("status", "eq.oneshot")
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
            url.addQueryParameter("Genre.id", "in.(${selectedGenres.joinToString(",")})")
        }

        if (query.isNotEmpty()) {
            url.addQueryParameter("judul", "ilike.*$query*")
        }

        url.addQueryParameter("select", selects.joinToString(","))
        url.addQueryParameter("type", typeFilter)
        url.addQueryParameter("order", sortColumn)
        url.addQueryParameter("offset", offset.toString())
        url.addQueryParameter("limit", "20")

        return client.get(url.build(), apiHeaders).parseAs<List<BookDto>>().toMangasPage()
    }

    // ========================= Filters =========================
    override fun getFilterList(data: JsonElement?) = FilterList(
        HasChapterFilter(),
        TypeFilter(),
        StatusFilter(),
        SortFilter(),
        GenreFilter(),
    )

    // ========================= Details =========================
    override fun getMangaUrl(manga: SManga): String = "$baseUrl/detail/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = if (fetchDetails) {
            async {
                val books = client.get("$apiUrl/Book?select=*%2Cgenres%3A_BookGenre%28genre%3AGenre%28*%29%29&type=not.ilike.*novel*&id=eq.${manga.url}", apiHeaders)
                    .parseAs<List<BookDto>>()
                if (books.isEmpty()) throw Exception("Manga not found")
                books.first().toSMangaDetails().apply { url = manga.url }
            }
        } else {
            null
        }

        val chapterList = if (fetchChapters) {
            async {
                client.get("$apiUrl/Chapter?select=id,bookId,chapter,nama,created_at&bookId=eq.${manga.url}&order=chapter.desc", apiHeaders)
                    .parseAs<List<ChapterDto>>()
                    .map { it.toSChapter() }
            }
        } else {
            null
        }

        SMangaUpdate(details?.await() ?: manga, chapterList?.await() ?: chapters)
    }

    // ========================= Chapters =========================
    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/view/${chapter.url}"

    // ========================= Pages =========================
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val id = chapter.url.substringAfterLast("/")
        val chapters = client.get("$apiUrl/Chapter?select=id,bookId,isigambar&id=eq.$id", apiHeaders)
            .parseAs<List<ChapterDto>>()
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
}
