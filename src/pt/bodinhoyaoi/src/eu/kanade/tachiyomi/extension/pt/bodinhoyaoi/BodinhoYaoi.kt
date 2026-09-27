package eu.kanade.tachiyomi.extension.pt.bodinhoyaoi

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.source.KeiSource

@Source
class BodinhoYaoi(
    override val lang: String = "pt-BR",
    override val id: Long = 8912408912841208912L,
) : KeiSource() {

    override val baseUrl: String = "https://bodinhoyaoi.com"
    override val name: String = "Bodinho Yaoi"

    override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(emptyList(), false)

    override suspend fun getLatestUpdates(page: Int): MangasPage = MangasPage(emptyList(), false)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = MangasPage(emptyList(), false)

    override suspend fun getPageList(chapter: SChapter): List<Page> = emptyList()

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = SMangaUpdate(manga, chapters)
}
