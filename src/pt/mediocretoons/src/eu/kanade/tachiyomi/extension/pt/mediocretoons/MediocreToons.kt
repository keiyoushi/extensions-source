package eu.kanade.tachiyomi.extension.pt.mediocretoons

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.text.Normalizer

class MediocreToons : HttpSource() {

    override val name = "Mediocre Toons"

    override val baseUrl = "https://mediocretoons.site"

    override val lang = "pt-BR"

    override val supportsLatest = true

    private val apiUrl = "https://api.mediocretoons.site"

    private val scanId: Long = 2

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(2)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .set("scan-id", scanId.toString())
        .set("x-app-key", "toons-mediocre-app")

    // ============================== Popular ================================
    override fun popularMangaRequest(page: Int): Request {
        val url = "$apiUrl/obras/ranking".toHttpUrl().newBuilder()
            .addQueryParameter("ordenarPor", "view_geral")
            .addQueryParameter("limite", "100")
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.parseAs<List<MediocreMangaDto>>()
        val mangas = result.map { it.toSManga() }
        return MangasPage(mangas, hasNextPage = false)
    }

    // ============================= Latest Updates ==========================
    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$apiUrl/obras/recentes".toHttpUrl().newBuilder()
            .addQueryParameter("pagina", page.toString())
            .addQueryParameter("limite", "24")
            .addQueryParameter("formato", "5")
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val dto = response.parseAs<MediocreListDto<List<MediocreMangaDto>>>()
        val mangas = dto.data.map { it.toSManga() }
        val hasNext = dto.pagination?.hasNextPage ?: false
        return MangasPage(mangas, hasNextPage = hasNext)
    }

    // =============================== Search ================================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/obras".toHttpUrl().newBuilder()
            .addQueryParameter("limite", "20")
            .addQueryParameter("pagina", page.toString())
            .addQueryParameter("temCapitulo", "true")

        if (query.isNotEmpty()) {
            url.addQueryParameter("string", query)
        }

        filters.forEach { filter ->
            when (filter) {
                is FormatoFilter -> {
                    if (filter.selected.isNotEmpty()) {
                        url.addQueryParameter("formato", filter.selected)
                    }
                }
                is StatusFilter -> {
                    if (filter.selected.isNotEmpty()) {
                        url.addQueryParameter("status", filter.selected)
                    }
                }
                is SortFilter -> {
                    url.addQueryParameter("ordenarPor", filter.selected)
                }
                else -> {}
            }
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val dto = response.parseAs<MediocreListDto<List<MediocreMangaDto>>>()
        val mangas = dto.data.map { it.toSManga() }
        val hasNext = dto.pagination?.hasNextPage ?: false
        return MangasPage(mangas, hasNextPage = hasNext)
    }

    // ============================== Filters ================================
    override fun getFilterList() = FilterList(
        FormatoFilter(),
        StatusFilter(),
        SortFilter(),
    )

    private class FormatoFilter : UriSelectFilter(
        "Formato",
        arrayOf(
            Pair("Todos", ""),
            Pair("Novel", "3"),
            Pair("Shoujo", "4"),
            Pair("Comic", "5"),
            Pair("Yaoi", "8"),
            Pair("Yuri", "9"),
            Pair("Hentai", "10"),
        ),
    )

    private class StatusFilter : UriSelectFilter(
        "Status",
        arrayOf(
            Pair("Todos", ""),
            Pair("Ativo", "1"),
            Pair("Em Andamento", "2"),
            Pair("Cancelada", "3"),
            Pair("Concluído", "4"),
            Pair("Hiato", "6"),
        ),
    )

    private class TagsFilter : Filter.Group<TagCheckBox>(
        "Tags",
        listOf(
            TagCheckBox("Ação", "2"),
            TagCheckBox("Aventura", "3"),
            TagCheckBox("Fantasia", "4"),
            TagCheckBox("Romance", "5"),
            TagCheckBox("Comédia", "6"),
            TagCheckBox("Drama", "7"),
            TagCheckBox("Terror", "8"),
            TagCheckBox("Horror", "9"),
            TagCheckBox("Suspense", "10"),
            TagCheckBox("Histórico", "11"),
            TagCheckBox("Vida escolar", "12"),
            TagCheckBox("Sobrenatural", "13"),
            TagCheckBox("Militar", "14"),
            TagCheckBox("Shounen", "15"),
            TagCheckBox("Shoujo", "16"),
            TagCheckBox("Josei", "17"),
            TagCheckBox("One-shot", "18"),
            TagCheckBox("Isekai", "19"),
            TagCheckBox("Retorno", "20"),
            TagCheckBox("Reencarnação", "21"),
            TagCheckBox("Sistema", "22"),
            TagCheckBox("Cultivo", "23"),
            TagCheckBox("Artes Marciais", "24"),
            TagCheckBox("Dungeon", "25"),
            TagCheckBox("Tragédia", "26"),
            TagCheckBox("Psicológico", "27"),
            TagCheckBox("Culinaria", "28"),
            TagCheckBox("Magia", "29"),
            TagCheckBox("SuperPoder", "30"),
            TagCheckBox("Murim", "31"),
            TagCheckBox("Necromante", "32"),
            TagCheckBox("Apocalipse", "33"),
            TagCheckBox("Seinen", "34"),
            TagCheckBox("Luta", "35"),
            TagCheckBox("máfia", "36"),
            TagCheckBox("Monstros", "37"),
            TagCheckBox("Esportes", "38"),
            TagCheckBox("Demônios", "39"),
            TagCheckBox("Ficção Científica", "40"),
            TagCheckBox("Fatia da Vida/Slice of Life", "41"),
            TagCheckBox("Ecchi", "42"),
            TagCheckBox("Mistério", "43"),
            TagCheckBox("Harém", "44"),
            TagCheckBox("manhua", "45"),
            TagCheckBox("Jogo", "46"),
            TagCheckBox("Regressão", "47"),
            TagCheckBox("+18", "48"),
            TagCheckBox("Oneshot", "49"),
            TagCheckBox("Yuri", "50"),
            TagCheckBox("Crime", "51"),
            TagCheckBox("Policial", "52"),
            TagCheckBox("Viagem no Tempo", "53"),
            TagCheckBox("Moderno", "54"),
        ),
    )

    private class SortFilter : UriSelectFilter(
        "Ordenar Por",
        arrayOf(
            Pair("Mais Recentes", "criada_em_desc"),
            Pair("Mais Populares", "view_geral"),
            Pair("A-Z", "nome"),
        ),
        defaultValue = 0,
    ) private class TagCheckBox(name: String, val value: String) : Filter.CheckBox(name)

    private open class UriSelectFilter(
        displayName: String,
        private val options: Array<Pair<String, String>>,
        defaultValue: Int = 0,
    ) : Filter.Select<String>(
        displayName,
        options.map { it.first }.toTypedArray(),
        defaultValue,
    ) {
        val selected get() = options[state].second
    }

    // ============================ Manga Details ============================
    override fun getMangaUrl(manga: SManga): String {
        val id = manga.url.substringAfter("/obra/").substringBefore('/')
        val slug = manga.title.toSlug()
        return "$baseUrl/obra/$id/$slug"
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        val pathSegment = manga.url.replace("/obra/", "/obras/")
        return GET("$apiUrl$pathSegment", headers)
    }

    override fun mangaDetailsParse(response: Response) =
        response.parseAs<MediocreMangaDto>().toSManga(isDetails = true)

    // ============================== Chapters ===============================
    override fun getChapterUrl(chapter: SChapter) = "$baseUrl${chapter.url}"

    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> =
        response.parseAs<MediocreMangaDto>().chapters.map { it.toSChapter() }
            .distinctBy(SChapter::url)

    // =============================== Pages =================================
    override fun pageListRequest(chapter: SChapter): Request {
        val chapterId = chapter.url.substringAfterLast("/")
        return GET("$apiUrl/capitulos/$chapterId", headers)
    }

    override fun pageListParse(response: Response): List<Page> =
        response.parseAs<MediocreChapterDetailDto>().toPageList()

    override fun imageUrlParse(response: Response): String = ""

    override fun imageUrlRequest(page: Page): Request {
        val imageHeaders = headers.newBuilder()
            .add("Referer", "$baseUrl/")
            .build()
        return GET(page.url, imageHeaders)
    }

    companion object {
        const val CDN_URL = "https://cdn.mediocretoons.site"
    }
}

private fun String.toSlug(): String {
    val noDiacritics = Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
    val slug = noDiacritics.lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
    return if (slug.isEmpty()) this.hashCode().toString() else slug
}
