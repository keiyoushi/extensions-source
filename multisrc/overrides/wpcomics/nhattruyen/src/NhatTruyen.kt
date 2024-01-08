package eu.kanade.tachiyomi.extension.vi.nhattruyen

import eu.kanade.tachiyomi.multisrc.wpcomics.WPComics
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import okhttp3.Headers
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Locale

class NhatTruyen : WPComics("NhatTruyen", "https://nhattruyenmax.com", "vi", SimpleDateFormat("dd/MM/yy", Locale.getDefault()), null) {
    override fun headersBuilder(): Headers.Builder = Headers.Builder()
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET("$baseUrl/the-loai?keyword=$query&page=$page", headers)
    override fun imageRequest(page: Page): Request = GET(page.imageUrl!!, headersBuilder().add("Referer", baseUrl).build())
}
