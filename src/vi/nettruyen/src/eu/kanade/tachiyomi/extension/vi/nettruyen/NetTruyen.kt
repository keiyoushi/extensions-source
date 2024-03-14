package eu.kanade.tachiyomi.extension.vi.nettruyen

import eu.kanade.tachiyomi.multisrc.wpcomics.WPComics
import eu.kanade.tachiyomi.source.model.MangasPage
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

class NetTruyen : WPComics(
    "NetTruyen",
    "https://www.nettruyenee.com",
    "vi",
    SimpleDateFormat("dd/MM/yy", Locale.getDefault()),
    null,
) {
    override fun String.replaceSearchPath() = replace("/$searchPath?status=2&", "/truyen-full?")

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
