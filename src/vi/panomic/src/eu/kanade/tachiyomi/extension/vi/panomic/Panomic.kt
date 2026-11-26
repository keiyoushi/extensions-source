package eu.kanade.tachiyomi.extension.vi.panomic

import eu.kanade.tachiyomi.multisrc.koi.Koi
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import keiyoushi.annotation.Source
import keiyoushi.network.addCookie
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient

@Source
abstract class Panomic : Koi() {

    override val mangaPath = "truyen"

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(3)
        .addCookie {
            listOf("a3_site_passcode_token" to "0d9678e234bc5f4234834a559f1c0157b3d933222d4b147ae2d6e1ac58a1dae0")
        }

    // Novels share the manga path but their slug always starts with "novel-"
    private fun MangasPage.withoutNovels() = MangasPage(
        mangas.filterNot { "/$mangaPath/novel-" in it.url },
        hasNextPage,
    )

    override suspend fun getPopularManga(page: Int): MangasPage = super.getPopularManga(page).withoutNovels()

    override suspend fun getLatestUpdates(page: Int): MangasPage = super.getLatestUpdates(page).withoutNovels()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = super.getSearchMangaList(page, query, filters).withoutNovels()
}
