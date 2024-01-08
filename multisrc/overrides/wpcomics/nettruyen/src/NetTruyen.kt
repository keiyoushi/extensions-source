package eu.kanade.tachiyomi.extension.vi.nettruyen

import eu.kanade.tachiyomi.multisrc.wpcomics.WPComics
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import okhttp3.Headers
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Locale

class NetTruyen : WPComics("NetTruyen", "https://www.nettruyenbing.com", "vi", SimpleDateFormat("dd/MM/yy", Locale.getDefault()), null) {
    override fun headersBuilder(): Headers.Builder = Headers.Builder()
    override fun imageRequest(page: Page): Request = GET(page.imageUrl!!, headersBuilder().add("Referer", baseUrl).build())
    override fun getFilterList(): FilterList {
        return FilterList(
            StatusFilter(getStatusList()),
            GenreFilter(getGenreList()),
        )
    }
}
