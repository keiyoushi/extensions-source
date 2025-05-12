package eu.kanade.tachiyomi.extension.pt.sussyscan

import eu.kanade.tachiyomi.multisrc.greenshit.ChapterPageDto
import eu.kanade.tachiyomi.multisrc.greenshit.GreenShit
import eu.kanade.tachiyomi.multisrc.greenshit.MangaDto
import eu.kanade.tachiyomi.multisrc.greenshit.ResultDto
import eu.kanade.tachiyomi.multisrc.greenshit.WrapperChapterDto
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.parseAs
import okhttp3.Request
import okhttp3.Response

class SussyToons : GreenShit(
    "Sussy Toons",
    "https://www.sussytoons.wtf",
    "pt-BR",
) {
    override val id = 6963507464339951166

    override val versionId = 2

    override fun popularMangaRequest(page: Int): Request =
        GET("$apiUrl/obras/top5", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val mangas = response.parseAs<ResultDto<List<MangaDto>>>().toSMangaList()
        return MangasPage(mangas, hasNextPage = false)
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl${manga.url}"

    override fun mangaDetailsRequest(manga: SManga): Request {
        val pathSegment = manga.url.substringBeforeLast("/").replace("obra", "obras")
        return GET("$apiUrl$pathSegment", headers)
    }

    override fun mangaDetailsParse(response: Response) =
        response.parseAs<ResultDto<MangaDto>>().results.toSManga()

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl${chapter.url}"

    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> =
        response.parseAs<ResultDto<WrapperChapterDto>>().toSChapterList()

    override fun pageListRequest(chapter: SChapter): Request {
        val pathSegment = chapter.url.replace("capitulo", "capitulo-app")
        return GET("$apiUrl$pathSegment", headers)
    }

    override fun pageListParse(response: Response): List<Page> =
        response.parseAs<ResultDto<ChapterPageDto>>().toPageList()
}
