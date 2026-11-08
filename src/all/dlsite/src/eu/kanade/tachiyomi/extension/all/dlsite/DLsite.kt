package eu.kanade.tachiyomi.extension.all.dlsite

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.Jsoup
import java.net.URLEncoder

@Source
abstract class DLsite : KeiSource() {

    private val language get() = LANGUAGES.getValue(lang)

    override fun OkHttpClient.Builder.configureClient() = rateLimit(2) { it.host == baseUrl.toHttpUrl().host }

    override suspend fun getPopularManga(page: Int) = search(page, "", FilterList())

    override suspend fun getLatestUpdates(page: Int) = search(page, "", FilterList(SortFilter(SORTS.indexOfFirst { it.second == "release_d" })))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList) = search(page, query, filters)

    // The lists are the site's search, through the endpoint its pages load results from
    private suspend fun search(page: Int, query: String, filters: FilterList): MangasPage {
        val section = filters.firstInstanceOrNull<SectionFilter>()?.value ?: "maniax"
        val types = filters.firstInstanceOrNull<TypeFilter>()?.selected.orEmpty().ifEmpty { listOf("comic", "illust") }
        val genres = filters.firstInstanceOrNull<GenreFilter>()?.selected.orEmpty()
        val url = "$baseUrl/$section/fsr/ajax/=".toHttpUrl().newBuilder().apply {
            fun param(name: String, value: String) = addPathSegment(name).addPathSegment(value)
            param("language", "jp")
            // Like the site, spaces are written "+" in the keyword: it answers "%20" with a 403
            if (query.isNotBlank()) addPathSegment("keyword").addEncodedPathSegment(URLEncoder.encode(query.trim(), "UTF-8"))
            types.forEachIndexed { index, type -> param("work_type_category[$index]", type) }
            // The source's language: works available in it
            if (language.options.size > 1) param("options_and_or", "or")
            language.options.forEachIndexed { index, option -> param("options[$index]", option) }
            if (genres.isNotEmpty()) param("genre_and_or", "and")
            genres.forEachIndexed { index, genre -> param("genre[$index]", genre) }
            if (filters.firstInstanceOrNull<FreeFilter>()?.state == true) param("is_free", "1")
            param("order[0]", filters.firstInstanceOrNull<SortFilter>()?.value ?: "trend")
            param("per_page", PAGE_SIZE.toString())
            param("page", page.toString())
            param("show_type", "1")
            addQueryParameter("locale", language.locale)
        }.build()
        return searchMangaParse(client.get(url, Fetch.XHR))
    }

    // The results come as HTML, in a table for the Japanese locale and in a grid for the others
    private fun searchMangaParse(response: Response): MangasPage {
        val result = response.parseAs<SearchDto>()
        val document = Jsoup.parseBodyFragment(result.searchResult, baseUrl)
        val mangas = document.select("[data-list_item_product_id]").mapNotNull { item ->
            val link = item.selectFirst(".work_name a[href]") ?: return@mapNotNull null
            SManga.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                title = link.attr("title").ifBlank { link.text() }
                thumbnail_url = item.getElementsByTag("thumb-with-ng-filter-block").firstOrNull()
                    ?.attr(":thumb-candidates")
                    ?.let(::coverUrl)
                author = item.select(".author a").joinToString { it.text() }.ifEmpty { item.selectFirst(".maker_name a")?.text() }
            }
        }
        return MangasPage(mangas, result.pageInfo.lastIndice < result.pageInfo.count)
    }

    // The list's thumbnail candidates, a WebP and a JPEG, can be resized copies of the main image: it has the same path
    // under /modpub/, without the size
    private fun coverUrl(candidates: String): String? {
        val urls = CANDIDATE_REGEX.findAll(candidates).map { it.groupValues[1] }.toList()
        val url = urls.firstOrNull { it.endsWith(".jpg") } ?: urls.firstOrNull() ?: return null
        val fullSize = url.replace("/resize/", "/modpub/").replace(SIZE_SUFFIX_REGEX, "")
        return if (fullSize.startsWith("//")) "https:$fullSize" else fullSize
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val match = PRODUCT_PATH_REGEX.find(url.encodedPath) ?: return null
        val product = fetchProduct(match.groupValues[1], match.groupValues[2]) ?: return null
        return product.toSManga().takeIf { product.workType in READABLE_TYPES }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val match = PRODUCT_PATH_REGEX.find(manga.url)!!
        val product = fetchProduct(match.groupValues[1], match.groupValues[2]) ?: throw Exception("This work isn't on sale anymore")
        return SMangaUpdate(product.toSManga(), listOf(product.toSChapter()))
    }

    // The work's data from the site's product API, in the source's language when the site has a translation
    private suspend fun fetchProduct(section: String, workno: String): ProductDto? {
        val url = "$baseUrl/$section/api/=/product.json".toHttpUrl().newBuilder()
            .addQueryParameter("workno", workno)
            .addQueryParameter("locale", language.locale)
            .build()
        return client.get(url, Fetch.XHR).parseAs<List<ProductDto>>().firstOrNull()
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = throw Exception("Reading isn't implemented yet")

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/maniax/genre/list?locale=${language.locale}", Fetch.DOCUMENT).asJsoup()
        .select("a[href*=/works/genre/=/genre/]")
        .map { GenreOption(it.text().replace(COUNT_REGEX, ""), it.attr("href").substringAfterLast('/')) }
        .distinctBy { it.id }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<List<GenreOption>>().orEmpty()
        return FilterList(
            buildList {
                add(SectionFilter())
                add(TypeFilter())
                add(SortFilter())
                add(FreeFilter())
                if (genres.isNotEmpty()) add(GenreFilter(genres))
            },
        )
    }

    private class Language(val locale: String, val options: List<String>)

    companion object {
        private const val PAGE_SIZE = 30

        // The site's locale for each source, and the language options of the works it lists
        private val LANGUAGES = mapOf(
            // Japanese works, and the ones that need no language
            "ja" to Language("ja_JP", listOf("JPN", "NM")),
            "en" to Language("en_US", listOf("ENG")),
            "zh" to Language("zh_CN", listOf("CHI_HANS")),
            "zh-Hant" to Language("zh_TW", listOf("CHI_HANT")),
            "ko" to Language("ko_KR", listOf("KO_KR")),
            "es" to Language("es_ES", listOf("SPA")),
            "fr" to Language("fr_FR", listOf("FRE")),
            "id" to Language("id_ID", listOf("IND")),
            "th" to Language("th_TH", listOf("THA")),
            "vi" to Language("vi_VN", listOf("VIE")),
            "pt-BR" to Language("pt_BR", listOf("POR")),
        )

        // Manga, CG and illustrations, webtoons
        private val READABLE_TYPES = setOf("MNG", "ICG", "WBT")

        private val PRODUCT_PATH_REGEX = Regex("""^/([a-z-]+?)(?:-touch)?/(?:work|announce)/=/product_id/([A-Z]{2}\d+)""")
        private val CANDIDATE_REGEX = Regex("""'([^']+)'""")
        private val SIZE_SUFFIX_REGEX = Regex("""_\d+x\d+(?=\.\w+$)""")
        private val COUNT_REGEX = Regex("""\s*\([\d,.]+\)$""")
    }
}
