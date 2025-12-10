package eu.kanade.tachiyomi.extension.pt.taosect

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.jsoup.select.Elements
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Locale

class TaoSect : HttpSource() {
    override val name = "TaoSect"
    override val baseUrl = "https://taosect.com/"
    override val lang = "pt-BR"
    override val supportsLatest = false

    override val client by lazy {
        network.cloudflareClient.newBuilder()
            .rateLimit(2)
            .build()
    }

    val readerUrl = "${baseUrl}leitor-online/"
    val apiUrl = "${baseUrl}wp-admin/admin-ajax.php"

    // ===== Popular =====
    override fun popularMangaRequest(page: Int): Request {
        return GET(readerUrl, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select(".container-obras-populares .manga_item").map { element ->
            val imageContainer = element.select(".container_imagem")
            val url = imageContainer.select(".link-img").attr("href")
            val coverImage = REGEX_FOR_IMAGE_ON_STYLE.find(imageContainer.attr("style"))
                ?.groupValues
                ?.get(1)

            MangaDto(
                url = url.split(baseUrl)[1],
                coverImage = coverImage,
            )
        }

        return MangasPage(mangas.map { it.toSManga() }, false)
    }

    // ===== Latest =====
    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response) = throw UnsupportedOperationException()

    // ===== Search =====
    /**
     * Is not possible to know if we will have a next page. This is a workflow to not get
     * 'No Results found'.
     */
    private var lastResult: SManga? = null

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val categoryId = filters.filterIsInstance<CategoryFilter>().firstOrNull()?.selected

        if (page == 1) lastResult = null

        return POST(
            apiUrl,
            headers,
            buildSerializedQuery(
                page = page,
                query = query,
                categoryId = categoryId?.toIntOrNull(),
            ).toRequestBody("application/x-www-form-urlencoded; charset=UTF-8".toMediaType()),
        )
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select(".manga_item").map { element ->
            val imageContainer = element.select(".container_imagem")
            val contentContainer = element.select(".conteudo_manga_item")

            val titleElement = contentContainer.select(".titulo_manga_item")

            val url = titleElement.select("a").attr("href")
            val coverImage = REGEX_FOR_IMAGE_ON_STYLE.find(imageContainer.attr("style"))
                ?.groupValues
                ?.get(1)

            val description = contentContainer.select("p").text().trim()

            MangaDto(
                title = titleElement.text().trim(),
                url = url.split(baseUrl)[1],
                coverImage = coverImage,
                description = description,
            )
        }

        if (mangas.isEmpty()) {
            return MangasPage(if (lastResult != null) listOf(lastResult!!) else emptyList(), false)
        } else {
            val maybeHasNextPage = mangas.size == PAGE_LIMIT

            return MangasPage(
                mangas.map { it.toSManga() }.let { originalList ->
                    if (maybeHasNextPage) {
                        val list = originalList.toMutableList()
                        if (lastResult != null) list.add(0, lastResult!!)
                        lastResult = list.lastOrNull()
                        list.dropLast(1)
                    } else {
                        originalList
                    }
                },
                maybeHasNextPage,
            )
        }
    }

    // ===== Manga Details =====
    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET("$baseUrl${manga.url}", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        return extractMangaDetails(response).toSManga()
    }

    // ===== Chapters =====
    override fun chapterListRequest(manga: SManga): Request {
        return mangaDetailsRequest(manga)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        return extractMangaDetails(response).let { manga ->
            manga.chapters
                ?.map { it.toSChapter(DATE_FORMAT) }
                ?.sortedWith(
                    compareBy<SChapter> { it.chapter_number }
                        .thenByDescending { it.date_upload },
                )
                ?: emptyList()
        }
    }

    // ===== Pages =====
    override fun pageListRequest(chapter: SChapter): Request {
        return GET(chapter.url, headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        val pages = document.select("#leitor_pagina_projeto option").mapIndexed { index, element ->
            PageDto(
                pageNumber = index,
                imageUrl = element.attr("value"),
            )
        }

        return pages.map { it.toSPage() }
    }

    override fun imageUrlParse(response: Response): String {
        throw UnsupportedOperationException("Not used")
    }

    // ====== Utils ======
    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String {
        return "$readerUrl/${chapter.url}"
    }

    override fun getFilterList() = getFilters()

    private fun buildSerializedQuery(
        action: String = "solicita_mais_projetos_pesquisa",
        page: Int,
        postType: String = "projetos",
        orderBy: String = "date",
        order: String = "ASC",
        query: String,
        categoryId: Int? = null,
    ): String {
        val pageSizeStr = PAGE_LIMIT.toString()

        val entries = mutableListOf<String>()

        // posts per page
        entries += """s:14:"posts_per_page";s:${pageSizeStr.length}:"$pageSizeStr";"""

        // paged (always 1. idk what is it)
        entries += """s:5:"paged";i:1;"""

        // post type
        entries += """s:9:"post_type";s:${postType.length}:"$postType";"""

        // order by
        entries += """s:7:"orderby";s:${orderBy.length}:"$orderBy";"""

        // order
        entries += """s:5:"order";s:${order.length}:"$order";"""

        // s (search)
        entries += """s:1:"s";s:${query.length}:"$query";"""

        // tax_query (ONLY IF categoryId != null)
        if (categoryId != null) {
            val taxonomy = "generos"
            val field = "term_id"

            val taxQuery = buildString {
                append("""s:9:"tax_query";a:1:{i:0;a:4:{""")
                append("""s:8:"taxonomy";s:${taxonomy.length}:"$taxonomy";""")
                append("""s:5:"field";s:${field.length}:"$field";""")
                append("""s:5:"terms";a:1:{i:0;i:$categoryId;}""")
                append("""s:8:"operator";s:2:"IN";""")
                append("}}")
            }

            entries += taxQuery
        }

        val serialized = buildString {
            append("a:${entries.size}:{")
            entries.forEach { append(it) }
            append("}")
        }

        val encodedOnce = URLEncoder.encode(serialized, StandardCharsets.UTF_8.toString())
        val argsEncoded = URLEncoder.encode(encodedOnce, StandardCharsets.UTF_8.toString())

        return buildString {
            append("action=").append(action)
            append("&pagina=").append(page)
            append("&args=").append(argsEncoded)
        }
    }

    private fun extractMangaDetails(response: Response): MangaDto {
        val document = response.asJsoup()

        val project = document.select(".cabelho-projeto").first()!!
        val projectImageContainer = project.select(".imagens-projeto")
        val projectContentContainer = project.select(".detalhes-projeto")

        val titleElement = projectContentContainer.select(".titulo-projeto")
        val url = document.select("link[rel='canonical']").attr("href")

        val projectTableContainer = projectContentContainer.select(".tabela-projeto")

        val chapters = document.select(".tabela-volumes tr").map { chapterElement ->
            val tdLeft = chapterElement.select("td[align='left']")
            val tdRight = chapterElement.select("td[align='right']")

            ChapterDto(
                url = tdLeft.select("a").attr("href"),
                title = tdLeft.select("a").text().trim(),
                createdAt = tdRight.select("i")
                    .firstOrNull()
                    ?.text()
                    ?.replace("(", "")
                    ?.replace(")", ""),
            )
        }

        return MangaDto(
            title = titleElement.text().trim(),
            url = url.split(baseUrl)[1],
            coverImage = projectImageContainer.select("img").firstOrNull()?.attr("src"),
            description = projectTableContainer.select(".tabela-projeto-conteudo p").text(),
            chapters = chapters,
            author = projectTableContainer.findTableValueByLabel("Roteiro"),
            artist = projectTableContainer.findTableValueByLabel("Arte"),
            genre = projectTableContainer.select("link_genero").firstOrNull()?.text(),
            status = projectTableContainer.findTableValueByLabel("Situação no País de Origem"),
        )
    }

    fun Elements.findTableValueByLabel(label: String): String? {
        return this
            .select("tr")
            .firstOrNull { tr ->
                tr.selectFirst("td strong")?.text()?.trim() == label
            }
            ?.select("td")
            ?.getOrNull(1)
            ?.text()
            ?.trim()
    }

    companion object {
        private const val PAGE_LIMIT = 10

        private val DATE_FORMAT by lazy {
            SimpleDateFormat("dd/MM/yyyy", Locale.ROOT)
        }

        private val REGEX_FOR_IMAGE_ON_STYLE = Regex("""url\(([^)]+)\)""")
    }
}
