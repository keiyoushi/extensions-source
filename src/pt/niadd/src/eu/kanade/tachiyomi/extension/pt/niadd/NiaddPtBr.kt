package eu.kanade.tachiyomi.extension.pt.niadd

import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.network.GET
import okhttp3.Response
import okhttp3.Request
import org.jsoup.nodes.Document

class NiaddPtBr : HttpSource() {

    override val name = "Niadd PT-BR"
    override val baseUrl = "https://br.niadd.com"
    override val lang = "pt-BR"
    override val supportsLatest = false

    // Lista de mangás populares - por enquanto retorna vazio
    override fun fetchPopularManga(page: Int) = MangasPage(emptyList(), false)

    // Pesquisa de mangás - por enquanto retorna vazio
    override fun fetchSearchManga(page: Int, query: String, filters: List<Filter>) =
        MangasPage(emptyList(), false)

    // Mangás mais recentes - não suportado
    override fun fetchLatestUpdates(page: Int) = throw UnsupportedOperationException("Not supported")

    // Requerido pelo HttpSource mas ainda vazio
    override fun fetchMangaDetails(manga: SManga) = manga
    override fun fetchChapterList(manga: SManga) = emptyList<Chapter>()
    override fun fetchPageList(chapter: Chapter) = emptyList<Page>()
}
