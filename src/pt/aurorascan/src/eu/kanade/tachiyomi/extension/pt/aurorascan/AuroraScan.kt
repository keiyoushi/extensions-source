package eu.kanade.tachiyomi.extension.pt.aurorascan

import eu.kanade.tachiyomi.lib.textinterceptor.TextInterceptorHelper
import eu.kanade.tachiyomi.multisrc.greenshit.ChapterPagesDto
import eu.kanade.tachiyomi.multisrc.greenshit.GreenShit
import eu.kanade.tachiyomi.multisrc.greenshit.MangaDto
import eu.kanade.tachiyomi.multisrc.greenshit.ResultDto
import eu.kanade.tachiyomi.multisrc.greenshit.toSlug
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

class AuroraScan : GreenShit(
    "Aurora Scan",
    "https://www.serenitytoons.win/",
    "pt-BR",
) {
    override val apiUrl = "https://api.sussytoons.wtf"
    override val cdnUrl = "https://cdn.sussytoons.site"
    override val useWidthInThumbnail = false
    override val defaultOrderBy = "data"
    override val targetAudience = TargetAudience.Shoujo
    override val popularGenreId = "1"
    override val latestGenreId = "1"
    override val popularType = "periodo"
    override val popularTypeValue = "geral"
    override val latestEndpoint = "novos-capitulos"
    override val includeSlugInUrl = true

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    override fun headersBuilder() = super.headersBuilder()
        .set("scan-id", "serenitytoons.win")

    override val client: OkHttpClient = super.client.newBuilder()
        .connectTimeout(1, TimeUnit.MINUTES)
        .readTimeout(1, TimeUnit.MINUTES)
        .build()

    override fun getFilterList(): FilterList = FilterList()

    private fun auroraMangasParse(response: Response): MangasPage {
        val dto = response.parseAs<ResultDto<List<MangaDto>>>(json)
        val mangas = dto.results.map { it.toSManga() }
        return MangasPage(mangas, dto.hasNextPage())
    }

    override fun popularMangaParse(response: Response) = auroraMangasParse(response)
    override fun latestUpdatesParse(response: Response) = auroraMangasParse(response)
    override fun searchMangaParse(response: Response) = auroraMangasParse(response)

    override fun popularMangaRequest(page: Int) =
        GET("$apiUrl/obras/ranking?periodo=geral&limite=30&gen_id=1&pagina=$page", headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val jsonElement = json.parseToJsonElement(response.body.string())
        val mangaDto = if (jsonElement is JsonObject && jsonElement.containsKey("resultado")) {
            json.decodeFromJsonElement<MangaDto>(jsonElement["resultado"]!!)
        } else {
            json.decodeFromJsonElement<MangaDto>(jsonElement)
        }
        return mangaDto.toSManga()
    }

    override fun pageListParse(response: Response): List<Page> {
        val jsonElement = json.parseToJsonElement(response.body.string())
        val chapterDto = if (jsonElement is JsonObject && jsonElement.containsKey("resultado")) {
            json.decodeFromJsonElement<ChapterPagesDto>(jsonElement["resultado"]!!)
        } else {
            json.decodeFromJsonElement<ChapterPagesDto>(jsonElement)
        }

        if (chapterDto.type == "TEXTO") {
            val content = chapterDto.text.orEmpty().ifBlank { "Capítulo em texto." }
            val textUrl = TextInterceptorHelper.createUrl(chapterDto.name, content)
            return listOf(Page(0, imageUrl = textUrl))
        }

        return chapterDto.pages.mapIndexed { i, page ->
            val path = page.path!!
            val normalizedPath = if (path.startsWith('/')) path else "/$path"
            val imageUrl = "$cdnUrl$normalizedPath/${page.src}"
            Page(i, imageUrl = imageUrl)
        }
    }

    private fun MangaDto.toSManga(): SManga = SManga.create().apply {
        title = this@toSManga.name ?: "Unknown"
        val finalUrl = if (includeSlugInUrl && id != null) {
            val finalSlug = slug?.takeIf { it.isNotEmpty() } ?: name?.toSlug() ?: "unknown"
            "/obra/$id/$finalSlug"
        } else {
            "/obra/${id?.toString() ?: slug ?: "unknown"}"
        }
        url = finalUrl
        thumbnail_url = thumbnail?.let {
            if (it.startsWith("http")) it else "$cdnUrl/scans/4/obras/$id/$it"
        }
        genre = genres.joinToString { it.value }
        description = this@toSManga.description?.let { Jsoup.parseBodyFragment(it).text() }
        status = this@toSManga.status?.toStatus() ?: SManga.UNKNOWN
        initialized = true
    }
}
