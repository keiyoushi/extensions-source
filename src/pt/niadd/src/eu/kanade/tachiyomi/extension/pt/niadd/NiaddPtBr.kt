package eu.kanade.tachiyomi.extension.pt.niadd

import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.FilterList
import rx.Observable
import okhttp3.Request
import okhttp3.Response

class NiaddPtBr : HttpSource() {

    override val name = "Niadd PT-BR"
    override val baseUrl = "https://br.niadd.com"
    override val lang = "pt-BR"
    override val supportsLatest = false

    // Popular mangás - retorna vazio
    override fun fetchPopularManga(page: Int): Observable<MangasPage> =
        Observable.just(MangasPage(emptyList(), false))

    // Busca de mangás - retorna vazio
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> =
        Observable.just(MangasPage(emptyList(), false))

    // Detalhes do mangá - apenas retorna o SManga original
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> =
        Observable.just(manga)

    // Lista de capítulos - retorna vazia
    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> =
        Observable.just(emptyList())

    // Lista de páginas - retorna vazia
    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> =
        Observable.just(emptyList())

    // Métodos obrigatórios de parse (não usados ainda)
    override fun popularMangaRequest(page: Int): Request = throw NotImplementedError()
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw NotImplementedError()
    override fun mangaDetailsParse(response: Response): SManga = throw NotImplementedError()
    override fun chapterListParse(response: Response): List<SChapter> = throw NotImplementedError()
    override fun pageListParse(response: Response): List<Page> = throw NotImplementedError()
}
