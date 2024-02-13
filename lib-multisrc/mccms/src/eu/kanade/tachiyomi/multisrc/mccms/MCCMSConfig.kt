package eu.kanade.tachiyomi.multisrc.mccms

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.Response
import org.jsoup.select.Evaluator

const val PAGE_SIZE = 30

val pcHeaders = Headers.headersOf("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/121.0")

fun String.removePathPrefix() = removePrefix("/index.php")

open class MCCMSConfig(
    hasCategoryPage: Boolean = true,
    val textSearchOnlyPageOne: Boolean = false,
    val useMobilePageList: Boolean = false,
    private val lazyLoadImageAttr: String = "data-original",
) {
    val genreData = GenreData(hasCategoryPage)

    fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        return if (useMobilePageList) {
            val container = document.selectFirst(Evaluator.Class("comic-list"))!!
            container.select(Evaluator.Tag("img")).mapIndexed { i, img ->
                Page(i, imageUrl = img.attr("src"))
            }
        } else {
            document.select("img[$lazyLoadImageAttr]").mapIndexed { i, img ->
                Page(i, imageUrl = img.attr(lazyLoadImageAttr))
            }
        }
    }
}
