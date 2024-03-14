package eu.kanade.tachiyomi.extension.vi.nhattruyen

import eu.kanade.tachiyomi.multisrc.wpcomics.WPComics
import eu.kanade.tachiyomi.source.model.MangasPage
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

class NhatTruyen : WPComics("NhatTruyen", "https://nhattruyenup.com", "vi", SimpleDateFormat("dd/MM/yy", Locale.getDefault()), null) {
    override val searchPath = "the-loai"

    /**
     * NetTruyen/NhatTruyen redirect back to catalog page if searching query is not found.
     * That makes both sites always return un-relevant results when searching should return empty.
     */
    override fun searchMangaParse(response: Response): MangasPage {
        if (response.request.url.queryParameter(name = queryParam) == null) {
            return MangasPage(mangas = listOf(), hasNextPage = false)
        }
        return super.searchMangaParse(response)
    }
}
