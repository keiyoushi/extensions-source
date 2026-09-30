package eu.kanade.tachiyomi.extension.all.holonometria

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.tryParseDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class Holonometria : KeiSource() {

    private val langPath: String get() = if (lang == "ja") "" else "$lang/"

    override val supportsLatest = false

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get("$baseUrl/${langPath}alt/holonometria/manga/").asJsoup()

        val mangas = document.select(".manga__item").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
                title = element.select(".manga__title").text()
                thumbnail_url = element.selectFirst("img")?.attr("abs:src")
            }
        }

        return MangasPage(mangas, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val search = query.trim()
        val entries = getPopularManga(page).mangas.filter { it.title.contains(search, true) }

        return MangasPage(entries, false)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()

        val updatedManga = manga.apply {
            title = document.select(".alt-nav__met-sub-link.is-current").text()
            thumbnail_url = document.select(".manga-detail__thumb img").attr("abs:src")
            description = document.select(".manga-detail__caption").text()

            val info = document.select(".manga-detail__person").html().split("<br>")

            author = info.firstOrNull { desc -> MANGA.any { desc.contains(it, true) } }
                ?.substringAfter("：")
                ?.substringAfter(":")
                ?.trim()
                ?.replace("&amp;", "&")

            artist = info.firstOrNull { desc -> SCRIPT.any { desc.contains(it, true) } }
                ?.substringAfter("：")
                ?.substringAfter(":")
                ?.trim()
                ?.replace("&amp;", "&")
        }

        val updatedChapters = document.select(".manga-detail__list .manga-detail__list-item").map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
                name = element.select(".manga-detail__list-title").text()
                date_upload = dateFormat.tryParseDate(element.selectFirst(".manga-detail__list-date")?.text())
            }
        }.reversed()

        return SMangaUpdate(updatedManga, updatedChapters)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()

        return document.select(".manga-detail__swiper-wrapper img").mapIndexed { idx, img ->
            Page(idx, imageUrl = img.attr("abs:src"))
        }.reversed()
    }

    companion object {
        private val MANGA = listOf("manga", "gambar", "漫画")
        private val SCRIPT = listOf("script", "naskah", "脚本")

        private val dateFormat = DateTimeFormatter.ofPattern("[yyyy.M.d][d.M.yyyy]", Locale.ENGLISH)
    }
}
