package eu.kanade.tachiyomi.multisrc.natsuid

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MultipartBody
import okhttp3.Response
import org.jsoup.Jsoup
import kotlin.random.Random
import kotlin.time.Instant

abstract class NatsuId : KeiSource() {

    private var nonce: String? = null

    private suspend fun getNonce(): String {
        if (nonce == null) {
            val url = "$baseUrl/wp-admin/admin-ajax.php?type=search_form&action=get_nonce"
            client.get(url).use { response ->
                nonce = Jsoup.parseBodyFragment(response.body.string())
                    .selectFirst("input[name=search_nonce]")
                    ?.attr("value")
                    ?.takeIf { it.isNotBlank() }
            }
        }
        return nonce ?: throw Exception("Unable to get nonce")
    }

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", SortFilter.popular)

    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchMangaList(page, "", SortFilter.latest)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/wp-admin/admin-ajax.php?action=advanced_search"
        val body = MultipartBody.Builder().apply {
            setType(MultipartBody.FORM)
            addFormDataPart("nonce", getNonce())
            filters.firstInstanceOrNull<GenreInclusion>()?.selected.also {
                addFormDataPart("inclusion", it ?: "OR")
            }
            filters.firstInstanceOrNull<GenreExclusion>()?.selected.also {
                addFormDataPart("exclusion", it ?: "OR")
            }
            addFormDataPart("page", page.toString())
            val genres = filters.firstInstanceOrNull<GenreFilter>()
            genres?.included.orEmpty().also {
                addFormDataPart("genre", it.toJsonString())
            }
            genres?.excluded.orEmpty().also {
                addFormDataPart("genre_exclude", it.toJsonString())
            }
            addFormDataPart("author", "[]")
            addFormDataPart("artist", "[]")
            val isProject = filters.firstInstanceOrNull<ProjectFilter>()
            addFormDataPart("project", if (isProject?.state == true) "1" else "0")
            filters.firstInstanceOrNull<TypeFilter>()?.checked.orEmpty().also {
                addFormDataPart("type", it.toJsonString())
            }
            filters.firstInstanceOrNull<StatusFilter>()?.checked.orEmpty().also {
                addFormDataPart("status", it.toJsonString())
            }
            val sort = filters.firstInstance<SortFilter>()
            addFormDataPart("order", if (sort.isAscending) "asc" else "desc")
            addFormDataPart("orderby", sort.sort)
            addFormDataPart("query", query.trim())
        }.build()

        val response = client.post(url, body)
        val document = Jsoup.parseBodyFragment(response.body.string(), baseUrl)
        response.close()

        val slugs = document.select("div > a[href*=/manga/]:has(> img)").map {
            it.absUrl("href").toHttpUrl().pathSegments[1]
        }.ifEmpty {
            return MangasPage(emptyList(), false)
        }

        val detailsUrl = "$baseUrl/wp-json/wp/v2/manga".toHttpUrl().newBuilder().apply {
            slugs.forEach { slug ->
                addQueryParameter("slug[]", slug)
            }
            addQueryParameter("per_page", "${slugs.size + 1}")
            addQueryParameter("_embed", null)
        }.build()

        val details = client.get(detailsUrl).parseAs<List<Manga>>(transform = ::transformJsonResponse)
            .filterNot { manga ->
                manga.embedded.getTerms("type").contains("Novel")
            }
            .associateBy { it.slug }

        val mangas = slugs.mapNotNull { slug ->
            details[slug]?.toSManga()
        }

        val hasNextPage = document.selectFirst("button:has(svg)") != null

        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (
            url.host == baseUrl.toHttpUrl().host &&
            url.pathSegments.size >= 2 &&
            url.pathSegments[0] == "manga"
        ) {
            val slug = url.pathSegments[1]
            val mangaUrl = "$baseUrl/wp-json/wp/v2/manga".toHttpUrl().newBuilder()
                .addQueryParameter("slug[]", slug)
                .addQueryParameter("_embed", null)
                .build()

            val mangas = client.get(mangaUrl).parseAs<List<Manga>>(transform = ::transformJsonResponse)

            if (mangas.isEmpty()) return null
            if (mangas[0].embedded.getTerms("type").contains("Novel")) return null

            return mangas[0].toSManga()
        }

        return null
    }

    private fun mangaId(manga: SManga): String? = when {
        manga.url.startsWith("{") -> manga.url.parseAs<MangaUrl>().id.toString()
        manga.url.toIntOrNull() != null -> manga.url
        else -> null
    }

    private fun mangaSlug(manga: SManga): String? = manga.memo ?: if (manga.url.startsWith("{")) {
        manga.url.parseAs<MangaUrl>().slug
    } else {
        null
    }

    override fun getMangaUrl(manga: SManga): String {
        val slug = mangaSlug(manga) ?: "$baseUrl${manga.url}".toHttpUrl().pathSegments[1]
        return "$baseUrl/manga/$slug/"
    }

    protected open fun chapterListPage(mangaId: String): Int = Random.nextInt(99, 9999)

    private suspend fun fetchChapterList(mangaId: String): List<SChapter> {
        val url = "$baseUrl/wp-admin/admin-ajax.php".toHttpUrl().newBuilder()
            .addQueryParameter("manga_id", mangaId)
            .addQueryParameter("page", chapterListPage(mangaId).toString())
            .addQueryParameter("action", "chapter_list")
            .build()

        return client.get(url).use { response ->
            chapterParse(response)
        }
    }

    protected open val chapterListSelector = "div a:has(time)"
    protected open val chapterNameSelector = "span"
    protected open val chapterDateSelector = "time"
    protected open val chapterDateAttribute = "datetime"

    private fun chapterParse(response: Response): List<SChapter> {
        val document = Jsoup.parseBodyFragment(response.body.string(), baseUrl)

        return document.select(chapterListSelector).map {
            SChapter.create().apply {
                setUrlWithoutDomain(it.absUrl("href"))
                name = it.selectFirst(chapterNameSelector)!!.ownText()
                date_upload = it.selectFirst(chapterDateSelector)?.attr(chapterDateAttribute)
                    ?.let { Instant.parseOrNull(it)?.toEpochMilliseconds() } ?: 0L
            }
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val id = mangaId(manga)

        if (id != null) {
            return coroutineScope {
                val detailsDeferred = async {
                    client.get("$baseUrl/wp-json/wp/v2/manga/$id?_embed#true")
                        .parseAs<Manga>(transform = ::transformJsonResponse)
                }
                val chaptersDeferred = if (fetchChapters) {
                    async { fetchChapterList(id) }
                } else {
                    null
                }

                val mangaData = detailsDeferred.await()
                val sChapters = chaptersDeferred?.await() ?: chapters

                SMangaUpdate(mangaData.toSManga(), sChapters)
            }
        }

        val doc = client.get(getMangaUrl(manga)).use { it.asJsoup() }
        val resolvedId = doc.selectFirst("#gallery-list")!!.attr("hx-get")
            .substringAfter("manga_id=").substringBefore("&")

        val mangaData = client.get("$baseUrl/wp-json/wp/v2/manga/$resolvedId?_embed#true")
            .parseAs<Manga>(transform = ::transformJsonResponse)

        val sChapters = if (fetchChapters) {
            fetchChapterList(resolvedId)
        } else {
            chapters
        }

        return SMangaUpdate(mangaData.toSManga(), sChapters)
    }

    protected open val pageListSelector = "main .relative section > img"

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter)).use { response ->
        response.asJsoup()
            .select(pageListSelector).mapIndexed { idx, img ->
                Page(idx, imageUrl = img.absUrl("src"))
            }
    }

    override val supportsFilterFetching: Boolean = true

    override suspend fun fetchFilterData(): JsonElement {
        val url = "$baseUrl/wp-json/wp/v2/genre?per_page=100&page=1&orderby=count&order=desc"
        return client.get(url).parseAs<JsonElement>(transform = ::transformJsonResponse)
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val filters = mutableListOf<Filter<*>>(
            SortFilter(),
            TypeFilter(),
            StatusFilter(),
            ProjectFilter(),
        )

        if (data != null) {
            val terms = data.parseAs<List<Term>>()
            filters.add(GenreFilter(terms.map { it.name to it.slug }))
            filters.add(GenreInclusion())
            filters.add(GenreExclusion())
        }

        return FilterList(filters)
    }

    protected open fun transformJsonResponse(responseBody: String): String = responseBody
}
