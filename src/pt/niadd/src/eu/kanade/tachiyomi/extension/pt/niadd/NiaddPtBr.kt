package eu.kanade.tachiyomi.extension.pt.niadd

import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.Request
import org.jsoup.nodes.Document
import rx.Observable

class NiaddPtBr : HttpSource() {
    override val name = "Niadd PT-BR"
    override val baseUrl = "https://br.niadd.com"
    override val lang = "pt"
    override val supportsLatest = false

    // retorna uma request de busca, mesmo que vazia
    override fun fetchPopularManga(page: Int) = Observable.just(emptyList<SManga>())
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList) = Observable.just(emptyList<SManga>())
    override fun fetchMangaDetails(manga: SManga) = Observable.just(manga)
    override fun fetchChapterList(manga: SManga) = Observable.just(emptyList())
    override fun fetchPageList(chapter: SChapter) = Observable.just(emptyList())
}
