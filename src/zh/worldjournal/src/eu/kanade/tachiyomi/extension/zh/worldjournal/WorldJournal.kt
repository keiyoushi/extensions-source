package eu.kanade.tachiyomi.extension.zh.worldjournal
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
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import java.time.LocalDate
import java.time.ZoneId

@Serializable
private class WorldJournalPage(
    val page_id: String? = null,
    val page_name: String? = null,
    val image_path: String? = null,
)

@Source
abstract class WorldJournal : KeiSource() {

    private val manga = SManga.create().apply {
        url = "world-journal-ny"
        title = "世界日報－紐約"
        initialized = true
    }

    override suspend fun getPopularManga(page: Int): MangasPage = if (page == 1) {
        MangasPage(listOf(manga), false)
    } else {
        MangasPage(emptyList(), false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = getPopularManga(page)

    override suspend fun getSearchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage = if (
        page == 1 &&
        "世界日報－紐約".contains(query.trim(), ignoreCase = true)
    ) {
        MangasPage(listOf(manga), false)
    } else {
        MangasPage(emptyList(), false)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? = if (
        url.host == baseUrl.removePrefix("https://").removePrefix("http://")
    ) {
        manga
    } else {
        null
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val updatedManga = SManga.create().apply {
            title = "世界日報－紐約"
            description = "世界日報紐約版電子報"
            status = SManga.COMPLETED
        }

        val dateList = (0..6).map { LocalDate.now().minusDays(it.toLong()) }

        val chapterList = dateList.map {
            SChapter.create().apply {
                url = it.toString()
                name = it.toString()
                date_upload = it.atStartOfDay(ZoneId.of("America/New_York")).toInstant().toEpochMilli()
            }
        }

        return SMangaUpdate(
            updatedManga,
            chapterList,
        )
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val date = chapter.url
        val url = "https://www.worldjournal.com/datafeed/wj/ny/NY-$date.json"

        val pages = client.get(url).parseAs<List<WorldJournalPage>>()

        return pages
            .filter {
                it.page_id?.matches(Regex("^[ABCD]\\d{2}$")) == true
            }
            .sortedBy { it.page_id }
            .mapIndexedNotNull { index, item ->
                val pageId = item.page_id ?: return@mapIndexedNotNull null
                val imageUrl = item.image_path?.takeIf { it.isNotBlank() }
                    ?: "https://pgw.worldjournal.com/gw/wj.php?u=NY/${date.replace("-", "")}/NY${date.replace("-", "")}$pageId"

                Page(
                    index,
                    imageUrl = imageUrl,
                )
            }
    }
}
