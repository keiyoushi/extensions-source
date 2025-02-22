package eu.kanade.tachiyomi.extension.en.aisha

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import rx.Observable

class Aisha : Madara(
    "Aisha",
    "https://aisha.manhuaen.com",
    "en",
) {
    override val supportsLatest = false
    override val useNewChapterEndpoint = true

    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> {
        return fetchPopularManga(page)
    }

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        val manga = SManga.create().apply {
            setUrlWithoutDomain("$baseUrl/manhua/aisha/")
            title = "Aisha"
            thumbnail_url = "$baseUrl/wp-content/uploads/2022/10/cover.jpg.webp"
        }

        return Observable.just(MangasPage(listOf(manga), false))
    }

    override fun getFilterList(): FilterList {
        return FilterList()
    }
}
