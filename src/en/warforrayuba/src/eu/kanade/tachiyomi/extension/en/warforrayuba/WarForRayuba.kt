package eu.kanade.tachiyomi.extension.en.warforrayuba

import android.os.Build
import eu.kanade.tachiyomi.AppInfo
import eu.kanade.tachiyomi.extension.en.warforrayuba.dto.GithubFileDto
import eu.kanade.tachiyomi.extension.en.warforrayuba.dto.PageDto
import eu.kanade.tachiyomi.extension.en.warforrayuba.dto.RoundDto
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
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.OkHttpClient

@Source
abstract class WarForRayuba : KeiSource() {

    override val supportsLatest = false

    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(4)
    }

    override fun Headers.Builder.configureHeaders() = apply {
        add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:86.0) Gecko/20100101 Firefox/86.0 ")
        set("Referer", baseUrl)
    }

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
        allowSpecialFloatingPointValues = true
        useArrayPolymorphism = true
        prettyPrint = true
    }

    private val cubariHeaders = Headers.Builder().apply {
        add(
            "User-Agent",
            "(Android ${Build.VERSION.RELEASE}; " +
                "${Build.MANUFACTURER} ${Build.MODEL}) " +
                "Tachiyomi/${AppInfo.getVersionName()} " +
                Build.ID,
        )
    }.build()

    override suspend fun getPopularManga(page: Int): MangasPage {
        val response = client.get("https://api.github.com/repos/xrabohrok/WarMap/contents/tools").parseAs<List<GithubFileDto>>()
        val mangaList = response.filter { it.name.endsWith(".json") }.map {
            SManga.create().apply {
                title = it.name
                url = it.downloadUrl
            }
        }
        return MangasPage(mangaList, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = MangasPage(emptyList(), false)

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val roundDto = client.get(manga.url).parseAs<RoundDto>(json)

        val manga = SManga.create().apply {
            title = roundDto.title
            description = roundDto.description
            thumbnail_url = roundDto.cover
            author = roundDto.author
            artist = roundDto.artist
        }

        val chapters = roundDto.chapters.map { (number, chapter) ->
            SChapter.create().apply {
                url = "https://cubari.moe" + chapter.groups.primary
                chapter_number = number.toFloat()
                name = number.toString() + " " + chapter.title
                date_upload = chapter.last_updated
            }
        }.reversed()

        return SMangaUpdate(manga, chapters)
    }

    override fun getMangaUrl(manga: SManga): String = baseUrl

    override fun getChapterUrl(chapter: SChapter): String = chapter.url

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterData = client.get(chapter.url, cubariHeaders).parseAs<List<PageDto>>(json)

        val pageList = chapterData.mapIndexed { index, page ->
            Page(index, page.src.slice(0..page.src.lastIndexOf(".")), page.src)
        }

        return pageList
    }
}
